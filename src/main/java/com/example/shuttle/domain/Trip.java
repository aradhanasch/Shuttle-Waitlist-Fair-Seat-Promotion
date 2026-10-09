package com.example.shuttle.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class Trip {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private int capacity;
    private int seatsConfirmed;
    private long nextRequestSeq = 1;
    private Instant checkInCutoff;
    @Version private long version;

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

    public Long getId() { return id; }
    public int getCapacity() { return capacity; }
    public int getSeatsConfirmed() { return seatsConfirmed; }
    public Instant getCheckInCutoff() { return checkInCutoff; }
}
