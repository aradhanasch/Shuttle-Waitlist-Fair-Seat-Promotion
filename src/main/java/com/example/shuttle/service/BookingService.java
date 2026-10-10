package com.example.shuttle.service;

import com.example.shuttle.domain.*;
import com.example.shuttle.exception.BadRequestException;
import com.example.shuttle.exception.NotFoundException;
import com.example.shuttle.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class BookingService {

    private final TripRepository trips;
    private final CostCentreQuotaRepository quotas;
    private final BookingRepository bookings;

    @PersistenceContext
    private EntityManager em;

    public BookingService(TripRepository trips, CostCentreQuotaRepository quotas,
                          BookingRepository bookings) {
        this.trips = trips;
        this.quotas = quotas;
        this.bookings = bookings;
    }

    @Transactional
    public Booking book(Long tripId, String employeeId, String costCentre,
                        int partySize, int tier) {
        if (partySize < 1) throw new BadRequestException("partySize must be at least 1");

        Trip trip = lockTrip(tripId);
        long seq = trip.nextSeq();
        Booking booking = new Booking(tripId, employeeId, costCentre,
                partySize, tier, seq, Instant.now());

        CostCentreQuota quota = quotaFor(tripId, costCentre);

        boolean waitlistEmpty = bookings.findOrdered(tripId, BookingState.WAITLISTED).isEmpty();
        boolean seatsFit = trip.freeSeats() >= partySize;
        boolean quotaFits = quota.remaining() >= partySize;

        if (waitlistEmpty && seatsFit && quotaFits) {
            trip.reserve(partySize);
            quota.use(partySize);
            booking.confirm();
        }
        return bookings.save(booking);
    }

    /**
     * Cancels a booking. If it held seats, releases them and promotes from the waitlist.
     * Returns the bookings promoted as a result (empty if none).
     */
    @Transactional
    public List<Booking> cancel(Long bookingId, String reason) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));

        Trip trip = lockTrip(booking.getTripId());
        em.refresh(booking);   // re-read the real state now that we hold the lock

        if (booking.getState() == BookingState.CANCELLED) {
            return List.of();   // idempotent: second cancel is a no-op
        }

        boolean heldSeats = booking.getState() == BookingState.CONFIRMED;
        booking.cancel(reason);

        if (!heldSeats) {
            return List.of();   // a waitlisted cancel frees nothing
        }

        trip.release(booking.getPartySize());
        quotaFor(trip.getId(), booking.getCostCentre()).release(booking.getPartySize());
        return promote(trip);
    }

    /**
     * Walks the waitlist in order. Promotes each booking whose party fits BOTH the
     * vehicle and its cost-centre quota. A booking that doesn't fit is skipped and
     * keeps its place. Must be called while holding the trip lock.
     */
    List<Booking> promote(Trip trip) {
        List<Booking> promoted = new ArrayList<>();
        for (Booking b : bookings.findOrdered(trip.getId(), BookingState.WAITLISTED)) {
            if (trip.freeSeats() == 0) break;

            CostCentreQuota quota = quotaFor(trip.getId(), b.getCostCentre());
            boolean seatsFit = trip.freeSeats() >= b.getPartySize();
            boolean quotaFits = quota.remaining() >= b.getPartySize();

            if (seatsFit && quotaFits) {
                trip.reserve(b.getPartySize());
                quota.use(b.getPartySize());
                b.confirm();
                promoted.add(b);
            } else {
                b.recordSkip();   // keeps its place in line
            }
        }
        return promoted;
    }

    private Trip lockTrip(Long tripId) {
        return trips.findByIdForUpdate(tripId)
                .orElseThrow(() -> new NotFoundException("Trip not found: " + tripId));
    }

    private CostCentreQuota quotaFor(Long tripId, String costCentre) {
        return quotas.findByTripIdAndCostCentre(tripId, costCentre)
                .orElseThrow(() -> new BadRequestException(
                        "No quota configured for cost centre: " + costCentre));
    }
}