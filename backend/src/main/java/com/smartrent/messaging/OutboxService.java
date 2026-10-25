package com.smartrent.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartrent.config.AppProperties;
import com.smartrent.domain.Booking;
import com.smartrent.domain.EventType;
import com.smartrent.domain.OutboxEvent;
import com.smartrent.dto.Dtos.BookingEventMessage;
import com.smartrent.repo.OutboxRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox, step 1: the event row is written in the SAME database transaction as the booking change
 * (Propagation.MANDATORY enforces that). If the booking transaction rolls back, no event exists; if it commits,
 * the event is guaranteed to be relayed to JMS later by {@link OutboxRelay} (at-least-once delivery).
 */
@Service
public class OutboxService {

    private final OutboxRepository repo;
    private final ObjectMapper mapper;
    private final AppProperties props;

    public OutboxService(OutboxRepository repo, ObjectMapper mapper, AppProperties props) {
        this.repo = repo;
        this.mapper = mapper;
        this.props = props;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(EventType type, Booking b) {
        BookingEventMessage msg = new BookingEventMessage(
                UUID.randomUUID().toString(), type, b.getId(), b.getReference(), b.getUser().getId(), Instant.now(),
                b.getVehicle().getMake() + " " + b.getVehicle().getModel(), b.getPickupDate(), b.getReturnDate(),
                b.getTotalPrice(), props.getCurrency());
        OutboxEvent e = new OutboxEvent();
        e.setEventId(msg.eventId());
        e.setEventType(type);
        e.setBookingId(b.getId());
        try {
            e.setPayload(mapper.writeValueAsString(msg));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize booking event", ex);
        }
        repo.save(e);
    }
}
