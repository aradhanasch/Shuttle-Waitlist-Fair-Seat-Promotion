package com.example.shuttle.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "cost_centre_quota")
public class CostCentreQuota {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "cost_centre", nullable = false)
    private String costCentre;

    @Column(name = "quota", nullable = false)
    private int quota;

    @Column(name = "seats_used", nullable = false)
    private int seatsUsed;

    protected CostCentreQuota() {}

    public CostCentreQuota(Long tripId, String costCentre, int quota) {
        this.tripId = tripId;
        this.costCentre = costCentre;
        this.quota = quota;
    }

    public int remaining() { return quota - seatsUsed; }

    public void use(int n) {
        if (n > remaining()) throw new IllegalStateException("Quota exceeded");
        seatsUsed += n;
    }

    public void release(int n) {
        if (n > seatsUsed) throw new IllegalStateException("Releasing more than used");
        seatsUsed -= n;
    }

    public void setQuota(int quota) { this.quota = quota; }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public String getCostCentre() { return costCentre; }
    public int getQuota() { return quota; }
    public int getSeatsUsed() { return seatsUsed; }
}