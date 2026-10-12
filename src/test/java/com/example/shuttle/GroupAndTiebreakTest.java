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
class GroupAndTiebreakTest {

    @Autowired BookingService service;
    @Autowired TripRepository trips;
    @Autowired CostCentreQuotaRepository quotas;
    @Autowired BookingRepository bookings;

    private Trip newTrip(int capacity, int quota) {
        Trip t = trips.save(new Trip(capacity, Instant.now().plus(1, ChronoUnit.HOURS)));
        quotas.save(new CostCentreQuota(t.getId(), "CC1", quota));
        return t;
    }

    /**
     * FIFO beats tier: a later request with a higher tier never jumps the queue.
     * (Equal requestSeq cannot occur on one trip: UNIQUE(trip_id, request_seq) forbids it,
     * so tier/id are only deterministic backups and are not reachable in practice.)
     */
    @Test
    void fifoBeatsTier() {
        Trip t = trips.save(new Trip(1, Instant.now().plus(1, ChronoUnit.HOURS)));
        Instant now = Instant.now();
        Booking first = bookings.save(new Booking(t.getId(), "first", "CC1", 1, 0, 1, now));
        Booking second = bookings.save(new Booking(t.getId(), "second", "CC1", 1, 9, 2, now));

        List<Booking> order = bookings.findOrdered(t.getId(), BookingState.WAITLISTED);
        assertEquals(List.of(first.getId(), second.getId()),
                order.stream().map(Booking::getId).toList());
    }

    @Test
    void groupOfThreeWaitsUntilThreeSeatsAreFree() {
        Trip t = newTrip(3, 10);
        Booking a = service.book(t.getId(), "a", "CC1", 1, 0);
        Booking b = service.book(t.getId(), "b", "CC1", 1, 0);
        Booking c = service.book(t.getId(), "c", "CC1", 1, 0);
        Booking group = service.book(t.getId(), "group", "CC1", 3, 0);
        assertEquals(BookingState.WAITLISTED, group.getState());

        service.cancel(a.getId(), "user");   // 1 free: group must NOT be split
        assertEquals(BookingState.WAITLISTED, bookings.findById(group.getId()).orElseThrow().getState());
        service.cancel(b.getId(), "user");   // 2 free: still not enough
        assertEquals(BookingState.WAITLISTED, bookings.findById(group.getId()).orElseThrow().getState());
        service.cancel(c.getId(), "user");   // 3 free: whole group boards together
        assertEquals(BookingState.CONFIRMED, bookings.findById(group.getId()).orElseThrow().getState());
        assertEquals(3, trips.findById(t.getId()).orElseThrow().getSeatsConfirmed());
    }

    @Test
    void duplicateActiveBookingIsRejected() {
        Trip t = newTrip(5, 5);
        service.book(t.getId(), "dup", "CC1", 1, 0);
        assertThrows(RuntimeException.class, () -> service.book(t.getId(), "dup", "CC1", 1, 0));
    }
}
