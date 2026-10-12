package com.example.shuttle.service;

import com.example.shuttle.domain.NotificationOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Stub SMS gateway: logs instead of sending. Swap for a real provider implementation. */
@Component
public class LogNotificationSender implements NotificationSender {
    private static final Logger log = LoggerFactory.getLogger(LogNotificationSender.class);

    @Override
    public void send(NotificationOutbox n) {
        log.info("event=sms_sent to={} message=\"{}\"", n.getEmployeeId(), n.getMessage());
    }
}
