package com.smartrent.messaging;

import com.smartrent.config.AppProperties;
import com.smartrent.domain.OutboxEvent;
import com.smartrent.domain.OutboxStatus;
import com.smartrent.repo.OutboxRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Transactional outbox, step 2 (the JMS PRODUCER): polls committed PENDING rows and sends them to the Artemis queue.
 * A row is marked SENT only after the broker accepted the message; failures are retried with a bounded attempt
 * counter (then FAILED, visible to administrators). Delivery is at-least-once, so the consumer is idempotent.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repo;
    private final JmsTemplate jms;
    private final AppProperties props;

    public OutboxRelay(OutboxRepository repo, JmsTemplate jms, AppProperties props) {
        this.repo = repo;
        this.jms = jms;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${smartrent.outbox.poll-interval-ms:1000}",
            initialDelayString = "${smartrent.outbox.poll-interval-ms:1000}")
    public void scheduledRelay() {
        relayPending();
    }

    /** Sends one batch; returns how many messages the broker accepted. Public so tests/admins can trigger it. */
    public synchronized int relayPending() {
        List<OutboxEvent> batch = repo.findByStatusOrderByIdAsc(OutboxStatus.PENDING,
                PageRequest.of(0, props.getOutbox().getBatchSize()));
        int sent = 0;
        for (OutboxEvent e : batch) {
            try {
                jms.convertAndSend(props.getJms().getEventsQueue(), e.getPayload());
                e.setStatus(OutboxStatus.SENT);
                e.setSentAt(Instant.now());
                e.setLastError(null);
                sent++;
            } catch (Exception ex) {
                e.setAttempts(e.getAttempts() + 1);
                e.setLastError(truncate(ex.getClass().getSimpleName() + ": " + ex.getMessage()));
                if (e.getAttempts() >= props.getOutbox().getMaxAttempts()) {
                    e.setStatus(OutboxStatus.FAILED);
                    log.error("Outbox event {} marked FAILED after {} attempts", e.getEventId(), e.getAttempts());
                } else {
                    log.warn("Outbox event {} not delivered (attempt {}): {}", e.getEventId(), e.getAttempts(),
                            ex.getMessage());
                }
            }
            repo.save(e);
        }
        return sent;
    }

    private static String truncate(String s) {
        return s != null && s.length() > 480 ? s.substring(0, 480) : s;
    }
}
