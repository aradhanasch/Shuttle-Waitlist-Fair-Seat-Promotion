package com.example.shuttle;

import com.example.shuttle.domain.*;
import com.example.shuttle.repository.*;
import com.example.shuttle.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ConcurrencyTest {

    @Autowired BookingService service;
    @Autowired TripRepository trips;
    @Autowired CostCentreQuotaRepository quotas;
    @Autowired BookingRepository bookings;

    private Trip newTrip(int capacity) {
        Trip t = trips.save(new Trip(capacity, Instant.now().plus(1, ChronoUnit.HOURS)));
        quotas.save(new CostCentreQuota(t.getId(), "CC1", 100));
        return t;
    }

    private void runTogether(List<Callable<Object>> jobs) throws Exception {
        ExecutorService ex = Executors.newFixedThreadPool(jobs.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> fs = new ArrayList<>();
        for (Callable<Object> j : jobs) {
            fs.add(ex.submit(() -> { go.await(); return j.call(); }));
        }
        go.countDown();
        for (Future<Object> f : fs) f.get(30, TimeUnit.SECONDS);
        ex.shutdown();
    }

    @Test
    void lastSeatRaceNeverOverbooks() throws Exception {
        Trip t = newTrip(1);
        List<Callable<Object>> jobs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String emp = "e" + i;
            jobs.add(() -> service.book(t.getId(), emp, "CC1", 1, 0));
        }
        runTogether(jobs);

        assertEquals(1, trips.findById(t.getId()).orElseThrow().getSeatsConfirmed());
        assertEquals(1, bookings.findByTripIdAndState(t.getId(), BookingState.CONFIRMED).size());
        assertEquals(19, bookings.findByTripIdAndState(t.getId(), BookingState.WAITLISTED).size());
    }

    @Test
    void twoSimultaneousCancelsPromoteExactlyTwo() throws Exception {
        Trip t = newTrip(2);
        Booking a = service.book(t.getId(), "a", "CC1", 1, 0);
        Booking b = service.book(t.getId(), "b", "CC1", 1, 0);
        for (int i = 0; i < 3; i++) service.book(t.getId(), "w" + i, "CC1", 1, 0);

        runTogether(List.of(
                () -> service.cancel(a.getId(), "user"),
                () -> service.cancel(b.getId(), "user")));

        List<Booking> confirmed = bookings.findByTripIdAndState(t.getId(), BookingState.CONFIRMED);
        assertEquals(2, confirmed.size());                       // never 3
        assertEquals(2, trips.findById(t.getId()).orElseThrow().getSeatsConfirmed());
        assertEquals(1, bookings.findByTripIdAndState(t.getId(), BookingState.WAITLISTED).size());
    }

    @Test
    void sameBookingCancelledTwiceAtOnceFreesOnlyOneSeat() throws Exception {
        Trip t = newTrip(1);
        Booking a = service.book(t.getId(), "a", "CC1", 1, 0);
        service.book(t.getId(), "w1", "CC1", 1, 0);
        service.book(t.getId(), "w2", "CC1", 1, 0);

        runTogether(List.of(
                () -> service.cancel(a.getId(), "user"),
                () -> service.cancel(a.getId(), "user")));

        assertEquals(1, trips.findById(t.getId()).orElseThrow().getSeatsConfirmed());
        assertEquals(1, bookings.findByTripIdAndState(t.getId(), BookingState.CONFIRMED).size());
    }
}