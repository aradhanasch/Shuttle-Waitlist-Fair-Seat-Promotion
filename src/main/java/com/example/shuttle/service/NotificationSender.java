package com.example.shuttle.service;

import com.example.shuttle.domain.NotificationOutbox;

public interface NotificationSender {
    void send(NotificationOutbox notification);
}
