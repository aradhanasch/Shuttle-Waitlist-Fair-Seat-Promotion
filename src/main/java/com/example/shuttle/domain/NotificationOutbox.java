package com.example.shuttle.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notification_outbox")
public class NotificationOutbox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NotificationOutbox() {}

    public NotificationOutbox(Long bookingId, String employeeId, String message) {
        this.bookingId = bookingId;
        this.employeeId = employeeId;
        this.message = message;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }

    public void markSent() { this.status = "SENT"; }

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public String getEmployeeId() { return employeeId; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
