package com.example.shuttle.service;

import com.example.shuttle.domain.*;
import com.example.shuttle.dto.Dtos.TripView;
import com.example.shuttle.dto.Dtos.WaitlistEntry;
import com.example.shuttle.exception.BadRequestException;
import com.example.shuttle.exception.NotFoundException;
import com.example.shuttle.repository.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TripService {

    private final TripRepository trips;
    private final CostCentreQuotaRepository quotas;
    private final BookingRepository bookings;

    public TripService(TripRepository trips, CostCentreQuotaRepository quotas,
                       BookingRepository bookings) {
        this.trips = trips;
        this.quotas = quotas;
        this.bookings = bookings;
    }

    @Transactional
    public Trip create(int capacity, Instant cutoff, Map<String, Integer> quotaMap) {
        if (capacity < 1) throw new BadRequestException("capacity must be at least 1");
        Trip t = trips.save(new Trip(capacity, cutoff));
        quotaMap.forEach((cc, q) -> {
            if (q == null || q < 0) throw new BadRequestException("Quota must be >= 0 for " + cc);
            quotas.save(new CostCentreQuota(t.getId(), cc, q));
        });
        return t;
    }

    /** Hot read path: cached for a few seconds. The booking path never reads this cache. */
    @Cacheable(cacheNames = "availability", key = "#tripId")
    @Transactional(readOnly = true)
    public TripView availability(Long tripId) {
        Trip t = trips.findById(tripId)
                .orElseThrow(() -> new NotFoundException("Trip not found: " + tripId));
        return new TripView(t.getId(), t.getCapacity(), t.getSeatsConfirmed(), t.freeSeats(),
                bookings.countByTripIdAndState(tripId, BookingState.WAITLISTED),
                t.getCheckInCutoff());
    }

    @Transactional(readOnly = true)
    public List<WaitlistEntry> waitlist(Long tripId) {
        if (!trips.existsById(tripId)) throw new NotFoundException("Trip not found: " + tripId);
        List<WaitlistEntry> out = new ArrayList<>();
        int pos = 1;
        for (Booking b : bookings.findOrdered(tripId, BookingState.WAITLISTED)) {
            out.add(new WaitlistEntry(pos++, b.getId(), b.getEmployeeId(), b.getCostCentre(),
                    b.getPartySize(), b.getTier(), b.getSkipCount()));
        }
        return out;
    }
}
