package com.smartrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.smartrent.domain.*;
import com.smartrent.dto.Dtos.BookingDto;
import com.smartrent.dto.Dtos.BookingRequest;
import com.smartrent.dto.Dtos.ReportRunDto;
import com.smartrent.messaging.OutboxRelay;
import com.smartrent.repo.NotificationRepository;
import com.smartrent.repo.OutboxRepository;
import com.smartrent.service.BookingService;
import com.smartrent.service.ReportService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.jms.core.JmsTemplate;

@SpringBootTest
class MessagingAndBatchTest {

    @Autowired BookingService bookings;
    @Autowired OutboxRelay relay;
    @Autowired OutboxRepository outbox;
    @Autowired NotificationRepository notifications;
    @Autowired JmsTemplate jms;
    @Autowired ReportService reports;
    @Autowired TestSupport support;

    @Test
    void bookingEventTravelsThroughOutboxAndArtemisAndIsStoredAsNotification() {
        Vehicle v = support.newVehicle();
        LocalDate today = LocalDate.now();
        BookingDto b = bookings.create(support.customer(), new BookingRequest(v.getId(), today.plusDays(250), today.plusDays(252), null), null);
        relay.relayPending();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notifications.countByBookingId(b.id())).isEqualTo(1));
        bookings.cancel(b.id(), support.customer(), "test");
        relay.relayPending();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notifications.countByBookingId(b.id())).isEqualTo(2));
        assertThat(outbox.countByStatus(OutboxStatus.FAILED)).isZero();
    }

    @Test
    void duplicateDeliveryOfTheSameEventIsProcessedOnce() {
        Vehicle v = support.newVehicle();
        LocalDate today = LocalDate.now();
        BookingDto b = bookings.create(support.customer(), new BookingRequest(v.getId(), today.plusDays(260), today.plusDays(262), null), null);
        String payload = "{\"eventId\":\"dup-" + b.id() + "\",\"eventType\":\"BOOKING_CONFIRMED\",\"bookingId\":" + b.id()
                + ",\"bookingReference\":\"" + b.reference() + "\",\"userId\":" + support.customer().id()
                + ",\"occurredAt\":\"2030-01-01T00:00:00Z\",\"vehicleLabel\":\"Test Car\",\"pickupDate\":\"" + b.pickupDate()
                + "\",\"returnDate\":\"" + b.returnDate() + "\",\"totalPrice\":100.00,\"currency\":\"INR\"}";
        jms.convertAndSend("smartrent.booking.events", payload);
        jms.convertAndSend("smartrent.booking.events", payload);
        await().atMost(Duration.ofSeconds(20)).until(() -> notifications.existsByEventId("dup-" + b.id()));
        // let the second copy be consumed too, then assert still exactly one
        try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        assertThat(notifications.findAll().stream().filter(n -> ("dup-" + b.id()).equals(n.getEventId())).count()).isEqualTo(1);
    }

    @Test
    void malformedMessageIsDroppedWithoutBreakingTheConsumer() {
        jms.convertAndSend("smartrent.booking.events", "this is not json");
        Vehicle v = support.newVehicle();
        LocalDate today = LocalDate.now();
        BookingDto b = bookings.create(support.customer(), new BookingRequest(v.getId(), today.plusDays(270), today.plusDays(272), null), null);
        relay.relayPending();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(notifications.countByBookingId(b.id())).isEqualTo(1));
    }

    @Test
    void everyReportTypeRunsAsASpringBatchJobAndWritesACsvFile() throws Exception {
        LocalDate from = LocalDate.now().minusDays(30);
        LocalDate to = LocalDate.now().plusDays(300);
        for (ReportType type : ReportType.values()) {
            ReportRunDto run = reports.trigger(type, from, to, "test@example.test");
            assertThat(run.status()).as(type + ": " + run.errorMessage()).isEqualTo(ReportStatus.COMPLETED);
            assertThat(run.downloadable()).isTrue();
            Resource file = reports.file(run.id());
            Path p = file.getFile().toPath();
            List<String> lines = Files.readAllLines(p);
            assertThat(lines).as(type + " has a header").isNotEmpty();
            assertThat(lines.size() - 1).as(type + " row count matches").isEqualTo(run.rowCount().intValue());
        }
        assertThat(reports.history(0, 50).content()).hasSizeGreaterThanOrEqualTo(ReportType.values().length);
    }
}
