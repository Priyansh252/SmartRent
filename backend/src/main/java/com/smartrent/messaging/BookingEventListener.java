package com.smartrent.messaging;

import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * The JMS CONSUMER. Runs on the listener container's own threads, completely outside the HTTP request that created
 * the booking. The session is transacted: if {@link NotificationService#handle} throws, the broker redelivers.
 */
@Component
public class BookingEventListener {

    private final NotificationService notifications;

    public BookingEventListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @JmsListener(destination = "${smartrent.jms.events-queue}")
    public void onMessage(String payload) {
        notifications.handle(payload);
    }
}
