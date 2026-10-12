package com.example.shuttle.service;

import com.example.shuttle.domain.NotificationOutbox;
import com.example.shuttle.repository.NotificationOutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Sends PENDING outbox rows. A failed send stays PENDING and is retried on the next tick. */
@Component
public class NotificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationOutboxRepository outbox;
    private final NotificationSender sender;
    private final MeterRegistry metrics;

    public NotificationDispatcher(NotificationOutboxRepository outbox, NotificationSender sender,
                                  MeterRegistry metrics) {
        this.outbox = outbox;
        this.sender = sender;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void dispatch() {
        for (NotificationOutbox n : outbox.findByStatusOrderByIdAsc("PENDING")) {
            try {
                sender.send(n);
                n.markSent();
                metrics.counter("shuttle.notifications_sent").increment();
            } catch (Exception e) {
                log.warn("event=sms_failed id={} (will retry)", n.getId());
            }
        }
    }
}
