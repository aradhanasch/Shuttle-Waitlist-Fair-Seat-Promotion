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
class BookingServiceTest {

    @Autowired BookingService service;
    @Autowired TripRepository trips;
    @Autowired CostCentreQuotaRepository quotas;
    @Autowired BookingRepository bookings;

    private Trip newTrip(int capacity, int quota) {
        Trip t = trips.save(new Trip(capacity, Instant.now().plus(1, ChronoUnit.HOURS)));
        quotas.save(new CostCentreQuota(t.getId(), "CC1", quota));
        return t;
    }

    @Test
    void confirmsWhileSeatsAndQuotaAvailable() {
        Trip t = newTrip(2, 2);
        assertEquals(BookingState.CONFIRMED, service.book(t.getId(), "e1", "CC1", 1, 0).getState());
        assertEquals(BookingState.CONFIRMED, service.book(t.getId(), "e2", "CC1", 1, 0).getState());
    }

    @Test
    void waitlistsWhenBusIsFull() {
        Trip t = newTrip(1, 5);
        service.book(t.getId(), "e1", "CC1", 1, 0);
        assertEquals(BookingState.WAITLISTED, service.book(t.getId(), "e2", "CC1", 1, 0).getState());
    }

    @Test
    void waitlistsWhenQuotaFullEvenIfSeatsExist() {
        Trip t = newTrip(10, 1);   // plenty of seats, quota of 1
        service.book(t.getId(), "e1", "CC1", 1, 0);
        assertEquals(BookingState.WAITLISTED, service.book(t.getId(), "e2", "CC1", 1, 0).getState());
    }

    @Test
    void waitlistOrderIsFifoThenTierThenId() {
        Trip t = newTrip(1, 5);
        service.book(t.getId(), "seated", "CC1", 1, 0);   // takes the seat
        Booking a = service.book(t.getId(), "a", "CC1", 1, 0);
        Booking b = service.book(t.getId(), "b", "CC1", 1, 9); // higher tier, but later
        Booking c = service.book(t.getId(), "c", "CC1", 1, 0);

        List<Booking> order = bookings.findOrdered(t.getId(), BookingState.WAITLISTED);
        assertEquals(List.of(a.getId(), b.getId(), c.getId()),
                order.stream().map(Booking::getId).toList());
        // sequences are strictly increasing, so there are no ties
        assertTrue(order.get(0).getRequestSeq() < order.get(1).getRequestSeq());
        assertTrue(order.get(1).getRequestSeq() < order.get(2).getRequestSeq());
    }

    @Test
    void rejectsBadPartySize() {
        Trip t = newTrip(1, 1);
        assertThrows(RuntimeException.class, () -> service.book(t.getId(), "e1", "CC1", 0, 0));
    }
}