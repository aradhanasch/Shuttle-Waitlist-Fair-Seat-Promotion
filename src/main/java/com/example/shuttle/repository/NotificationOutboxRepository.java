package com.example.shuttle.repository;

import com.example.shuttle.domain.NotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {
    List<NotificationOutbox> findByStatusOrderByIdAsc(String status);
}
