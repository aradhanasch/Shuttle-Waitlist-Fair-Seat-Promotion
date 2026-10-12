package com.example.shuttle.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "trip")
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "seats_confirmed", nullable = false)
    private int seatsConfirmed;

    @Column(name = "next_request_seq", nullable = false)
    private long nextRequestSeq = 1;

    @Column(name = "check_in_cutoff", nullable = false)
    private Instant checkInCutoff;

    @Column(name = "no_show_processed", nullable = false)
    private boolean noShowProcessed;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Trip() {}

    public Trip(int capacity, Instant checkInCutoff) {
        this.capacity = capacity;
        this.checkInCutoff = checkInCutoff;
    }

    public int freeSeats() { return capacity - seatsConfirmed; }

    public void reserve(int n) {
        if (n > freeSeats()) throw new IllegalStateException("Not enough free seats");
        seatsConfirmed += n;
    }

    public void release(int n) {
        if (n > seatsConfirmed) throw new IllegalStateException("Releasing more than confirmed");
        seatsConfirmed -= n;
    }

    public long nextSeq() { return nextRequestSeq++; }

    public void markNoShowProcessed() { this.noShowProcessed = true; }

    public Long getId() { return id; }
    public int getCapacity() { return capacity; }
    public int getSeatsConfirmed() { return seatsConfirmed; }
    public Instant getCheckInCutoff() { return checkInCutoff; }
    public boolean isNoShowProcessed() { return noShowProcessed; }
    public long getVersion() { return version; }
}
