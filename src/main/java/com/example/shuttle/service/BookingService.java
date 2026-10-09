package com.example.shuttle.service;

import com.example.shuttle.domain.*;
import com.example.shuttle.exception.BadRequestException;
import com.example.shuttle.exception.NotFoundException;
import com.example.shuttle.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class BookingService {

    private final TripRepository trips;
    private final CostCentreQuotaRepository quotas;
    private final BookingRepository bookings;

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

        // 1. Lock the trip row. Everything below is now serialised per trip.
        Trip trip = trips.findByIdForUpdate(tripId)
                .orElseThrow(() -> new NotFoundException("Trip not found: " + tripId));

        // 2. Assign the sequence inside the lock, so there are no ties.
        long seq = trip.nextSeq();
        Booking booking = new Booking(tripId, employeeId, costCentre,
                partySize, tier, seq, Instant.now());

        CostCentreQuota quota = quotas.findByTripIdAndCostCentre(tripId, costCentre)
                .orElseThrow(() -> new BadRequestException(
                        "No quota configured for cost centre: " + costCentre));

        // 3. Confirm only if seats fit, quota fits, and nobody is already waiting.
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
}