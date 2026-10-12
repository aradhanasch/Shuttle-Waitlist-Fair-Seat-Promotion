package com.example.shuttle.service;

import com.example.shuttle.domain.Trip;
import com.example.shuttle.repository.TripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Periodically reclaims no-show seats on trips past their check-in cut-off. */
@Component
public class NoShowScheduler {
    private static final Logger log = LoggerFactory.getLogger(NoShowScheduler.class);

    private final TripRepository trips;
    private final BookingService service;

    public NoShowScheduler(TripRepository trips, BookingService service) {
        this.trips = trips;
        this.service = service;
    }

    @Scheduled(fixedDelay = 30000)
    public void run() {
        for (Trip t : trips.findByCheckInCutoffBeforeAndNoShowProcessedFalse(Instant.now())) {
            try {
                service.reclaimNoShows(t.getId(), false);   // own transaction per trip
            } catch (Exception e) {
                log.error("event=reclaim_failed tripId={}", t.getId(), e);   // retried next tick
            }
        }
    }
}
