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
class PromotionTest {

    @Autowired BookingService service;
    @Autowired TripRepository trips;
    @Autowired CostCentreQuotaRepository quotas;
    @Autowired BookingRepository bookings;

    private Trip newTrip(int capacity) {
        return trips.save(new Trip(capacity, Instant.now().plus(1, ChronoUnit.HOURS)));
    }

    private void quota(Trip t, String cc, int q) {
        quotas.save(new CostCentreQuota(t.getId(), cc, q));
    }

    private BookingState stateOf(Booking b) {
        return bookings.findById(b.getId()).orElseThrow().getState();
    }

    @Test
    void cancelPromotesTopWaitlistedSingle() {
        Trip t = newTrip(1);
        quota(t, "CC1", 5);
        Booking seated = service.book(t.getId(), "seated", "CC1", 1, 0);
        Booking w1 = service.book(t.getId(), "w1", "CC1", 1, 0);
        Booking w2 = service.book(t.getId(), "w2", "CC1", 1, 0);

        List<Booking> promoted = service.cancel(seated.getId(), "user");

        assertEquals(1, promoted.size());
        assertEquals(BookingState.CONFIRMED, stateOf(w1));
        assertEquals(BookingState.WAITLISTED, stateOf(w2));
    }

    @Test
    void groupOfTwoIsSkippedWhenOnlyOneSeatFrees() {
        Trip t = newTrip(2);
        quota(t, "CC1", 10);
        Booking a = service.book(t.getId(), "a", "CC1", 1, 0);
        Booking b = service.book(t.getId(), "b", "CC1", 1, 0);
        Booking group = service.book(t.getId(), "group", "CC1", 2, 0);
        Booking single = service.book(t.getId(), "single", "CC1", 1, 0);

        service.cancel(a.getId(), "user");   // exactly 1 seat frees

        assertEquals(BookingState.WAITLISTED, stateOf(group));  // skipped, not split
        assertEquals(BookingState.CONFIRMED, stateOf(single));  // next fitting person
        assertEquals(1, bookings.findById(group.getId()).orElseThrow().getSkipCount());
    }

    @Test
    void freedSeatBlockedByCostCentreQuota() {
        Trip t = newTrip(2);
        quota(t, "CC1", 1);
        quota(t, "CC2", 5);
        Booking cc1Seated = service.book(t.getId(), "x", "CC1", 1, 0);
        service.book(t.getId(), "y", "CC2", 1, 0);
        Booking cc1Waiting = service.book(t.getId(), "z", "CC1", 1, 0); // quota full -> waitlist
        Booking cc2Waiting = service.book(t.getId(), "w", "CC2", 1, 0);

        // Free a CC2-less seat: cancel the CC2 booking's seat by cancelling "y"
        Booking y = bookings.findByTripIdAndState(t.getId(), BookingState.CONFIRMED).stream()
                .filter(x -> x.getEmployeeId().equals("y")).findFirst().orElseThrow();
        service.cancel(y.getId(), "user");

        // Seat exists, but CC1 quota is still full, so z is skipped and w (CC2) is promoted
        assertEquals(BookingState.WAITLISTED, stateOf(cc1Waiting));
        assertEquals(BookingState.CONFIRMED, stateOf(cc2Waiting));
        assertEquals(BookingState.CONFIRMED, stateOf(cc1Seated));
    }

    @Test
    void cancellingWaitlistedBookingPromotesNobody() {
        Trip t = newTrip(1);
        quota(t, "CC1", 5);
        service.book(t.getId(), "seated", "CC1", 1, 0);
        Booking w = service.book(t.getId(), "w", "CC1", 1, 0);

        assertTrue(service.cancel(w.getId(), "user").isEmpty());
        assertEquals(BookingState.CANCELLED, stateOf(w));
    }

    @Test
    void cancellingTwiceIsIdempotent() {
        Trip t = newTrip(1);
        quota(t, "CC1", 5);
        Booking seated = service.book(t.getId(), "seated", "CC1", 1, 0);
        service.book(t.getId(), "w1", "CC1", 1, 0);

        service.cancel(seated.getId(), "user");
        assertTrue(service.cancel(seated.getId(), "user").isEmpty()); // no second promotion
        assertEquals(1, trips.findById(t.getId()).orElseThrow().getSeatsConfirmed());
    }
}