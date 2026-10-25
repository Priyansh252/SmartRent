package com.smartrent.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartrent.domain.Notification;
import com.smartrent.domain.NotificationStatus;
import com.smartrent.dto.Dtos.BookingEventMessage;
import com.smartrent.dto.Dtos.NotificationDto;
import com.smartrent.dto.Dtos.OutboxDto;
import com.smartrent.dto.Dtos.PageResponse;
import com.smartrent.repo.NotificationRepository;
import com.smartrent.repo.OutboxRepository;
import com.smartrent.service.Mappers;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Turns booking events into persisted notifications (idempotently) and serves the history views. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final NotificationRepository repo;
    private final OutboxRepository outbox;
    private final ObjectMapper mapper;

    public NotificationService(NotificationRepository repo, OutboxRepository outbox, ObjectMapper mapper) {
        this.repo = repo;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    /**
     * Idempotent: the same eventId is processed at most once (existence check + UNIQUE(event_id) as the safety net),
     * so broker redeliveries and relay retries never notify a customer twice.
     * A malformed message can never succeed, so it is logged and dropped instead of being redelivered forever.
     * Any other exception propagates and rolls back, which makes the broker redeliver the message.
     */
    @Transactional
    public void handle(String payload) {
        BookingEventMessage m;
        try {
            m = mapper.readValue(payload, BookingEventMessage.class);
        } catch (JsonProcessingException e) {
            log.error("Discarding malformed booking event message: {}", e.getOriginalMessage());
            return;
        }
        if (m.eventId() == null || m.eventType() == null || m.userId() == null || m.bookingId() == null) {
            log.error("Discarding incomplete booking event message {}", m.eventId());
            return;
        }
        if (repo.existsByEventId(m.eventId())) {
            log.info("Duplicate delivery of event {} ignored", m.eventId());
            return;
        }
        Notification n = new Notification();
        n.setEventId(m.eventId());
        n.setEventType(m.eventType());
        n.setBookingId(m.bookingId());
        n.setUserId(m.userId());
        n.setTitle(title(m));
        n.setMessage(message(m));
        n.setStatus(NotificationStatus.PROCESSED);
        n.setCreatedAt(m.occurredAt() != null ? m.occurredAt() : Instant.now());
        n.setProcessedAt(Instant.now());
        repo.save(n);
        log.info("Processed {} for booking {} (event {})", m.eventType(), m.bookingReference(), m.eventId());
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> mine(Long userId, int page, int size) {
        return PageResponse.of(repo.findByUserIdOrderByProcessedAtDescIdDesc(userId, page(page, size))
                .map(Mappers::toDto));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> all(int page, int size) {
        return PageResponse.of(repo.findAllByOrderByProcessedAtDescIdDesc(page(page, size)).map(Mappers::toDto));
    }

    @Transactional(readOnly = true)
    public PageResponse<OutboxDto> outbox(int page, int size) {
        return PageResponse.of(outbox.findAllByOrderByIdDesc(page(page, size)).map(Mappers::toDto));
    }

    private static PageRequest page(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    private static String title(BookingEventMessage m) {
        return switch (m.eventType()) {
            case BOOKING_CONFIRMED -> "Booking confirmed";
            case BOOKING_CANCELLED -> "Booking cancelled";
            case VEHICLE_PICKED_UP -> "Vehicle picked up";
            case VEHICLE_RETURNED -> "Vehicle returned";
        };
    }

    private static String message(BookingEventMessage m) {
        String dates = m.pickupDate().format(DATE) + " to " + m.returnDate().format(DATE);
        String ref = m.bookingReference();
        String car = m.vehicleLabel();
        return switch (m.eventType()) {
            case BOOKING_CONFIRMED -> "Booking " + ref + " is confirmed: " + car + ", " + dates + ". Agreed total "
                    + m.currency() + " " + m.totalPrice() + ".";
            case BOOKING_CANCELLED -> "Booking " + ref + " (" + car + ", " + dates + ") has been cancelled.";
            case VEHICLE_PICKED_UP -> "You picked up the " + car + " for booking " + ref + ". Return it by "
                    + m.returnDate().format(DATE) + ".";
            case VEHICLE_RETURNED -> "The " + car + " from booking " + ref + " was returned. Thank you for renting with SmartRent.";
        };
    }
}
