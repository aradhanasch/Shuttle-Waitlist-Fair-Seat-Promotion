package com.example.shuttle.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "booking")
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    @Column(name = "cost_centre", nullable = false)
    private String costCentre;

    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private BookingState state;

    @Column(name = "request_seq", nullable = false)
    private long requestSeq;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "tier", nullable = false)
    private int tier;

    @Column(name = "skip_count", nullable = false)
    private int skipCount;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "cancel_reason", length = 30)
    private String cancelReason;

    protected Booking() {}

    public Booking(Long tripId, String employeeId, String costCentre,
                   int partySize, int tier, long requestSeq, Instant now) {
        this.tripId = tripId;
        this.employeeId = employeeId;
        this.costCentre = costCentre;
        this.partySize = partySize;
        this.tier = tier;
        this.requestSeq = requestSeq;
        this.requestedAt = now;
        this.state = BookingState.WAITLISTED;
    }

    public void confirm() {
        if (state != BookingState.WAITLISTED) {
            throw new IllegalStateException("Only waitlisted can be confirmed");
        }
        state = BookingState.CONFIRMED;
    }

    public void cancel(String reason) {
        if (state == BookingState.CANCELLED) {
            throw new IllegalStateException("Already cancelled");
        }
        state = BookingState.CANCELLED;
        cancelReason = reason;
    }

    public void checkIn(Instant now) { this.checkedInAt = now; }
    public void recordSkip() { skipCount++; }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public String getEmployeeId() { return employeeId; }
    public String getCostCentre() { return costCentre; }
    public int getPartySize() { return partySize; }
    public BookingState getState() { return state; }
    public long getRequestSeq() { return requestSeq; }
    public Instant getRequestedAt() { return requestedAt; }
    public int getTier() { return tier; }
    public int getSkipCount() { return skipCount; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public String getCancelReason() { return cancelReason; }
}