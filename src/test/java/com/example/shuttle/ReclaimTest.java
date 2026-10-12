package com.example.shuttle;

import com.example.shuttle.domain.*;
import com.example.shuttle.repository.*;
import com.example.shuttle.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ReclaimTest {

    @Autowired BookingService service;
    @Autowired TripRepository trips;
    @Autowired CostCentreQuotaRepository quotas;
    @Autowired BookingRepository bookings;

    @Test
    void noShowIsReclaimedAndNextPersonPromoted() {
        Trip t = trips.save(new Trip(2, Instant.now().plus(1, ChronoUnit.HOURS)));
        quotas.save(new CostCentreQuota(t.getId(), "CC1", 10));

        Booking present = service.book(t.getId(), "present", "CC1", 1, 0);
        Booking absent = service.book(t.getId(), "absent", "CC1", 1, 0);
        Booking waiting = service.book(t.getId(), "waiting", "CC1", 1, 0);
        service.checkIn(present.getId(), "present");

        List<Booking> promoted = service.reclaimNoShows(t.getId(), true);

        assertEquals(1, promoted.size());
        Booking absentNow = bookings.findById(absent.getId()).orElseThrow();
        assertEquals(BookingState.CANCELLED, absentNow.getState());
        assertEquals("NO_SHOW", absentNow.getCancelReason());
        assertEquals(BookingState.CONFIRMED, bookings.findById(present.getId()).orElseThrow().getState());
        assertEquals(BookingState.CONFIRMED, bookings.findById(waiting.getId()).orElseThrow().getState());
        assertEquals(2, trips.findById(t.getId()).orElseThrow().getSeatsConfirmed());
    }

    @Test
    void reclaimIsIdempotent() {
        Trip t = trips.save(new Trip(1, Instant.now().plus(1, ChronoUnit.HOURS)));
        quotas.save(new CostCentreQuota(t.getId(), "CC1", 10));
        service.book(t.getId(), "a", "CC1", 1, 0);
        service.book(t.getId(), "b", "CC1", 1, 0);

        assertEquals(1, service.reclaimNoShows(t.getId(), true).size());
        assertTrue(service.reclaimNoShows(t.getId(), true).isEmpty());   // second run is a no-op
        assertEquals(1, trips.findById(t.getId()).orElseThrow().getSeatsConfirmed());
    }

    @Test
    void reclaimBeforeCutoffDoesNothingUnlessForced() {
        Trip t = trips.save(new Trip(1, Instant.now().plus(1, ChronoUnit.HOURS)));
        quotas.save(new CostCentreQuota(t.getId(), "CC1", 10));
        service.book(t.getId(), "a", "CC1", 1, 0);
        service.book(t.getId(), "b", "CC1", 1, 0);

        assertTrue(service.reclaimNoShows(t.getId(), false).isEmpty());
        assertEquals(1, trips.findById(t.getId()).orElseThrow().getSeatsConfirmed());
        assertEquals("a", bookings.findByTripIdAndState(t.getId(), BookingState.CONFIRMED)
                .get(0).getEmployeeId());
    }
}
