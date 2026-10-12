package com.example.shuttle.service;

import com.example.shuttle.domain.*;
import com.example.shuttle.exception.BadRequestException;
import com.example.shuttle.exception.ForbiddenException;
import com.example.shuttle.exception.NotFoundException;
import com.example.shuttle.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final TripRepository trips;
    private final CostCentreQuotaRepository quotas;
    private final BookingRepository bookings;
    private final NotificationOutboxRepository outbox;
    private final MeterRegistry metrics;

    @PersistenceContext
    private EntityManager em;

    public BookingService(TripRepository trips, CostCentreQuotaRepository quotas,
                          BookingRepository bookings, NotificationOutboxRepository outbox,
                          MeterRegistry metrics) {
        this.trips = trips;
        this.quotas = quotas;
        this.bookings = bookings;
        this.outbox = outbox;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public Booking find(Long bookingId) {
        return bookings.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));
    }

    /** Books a seat, or joins the waitlist. Everything runs under the per-trip lock. */
    @Transactional
    @CacheEvict(cacheNames = "availability", allEntries = true)
    public Booking book(Long tripId, String employeeId, String costCentre,
                        int partySize, int tier) {
        if (partySize < 1) throw new BadRequestException("partySize must be at least 1");

        Trip trip = lockTrip(tripId);

        if (bookings.existsByTripIdAndEmployeeIdAndStateNot(tripId, employeeId, BookingState.CANCELLED)) {
            metrics.counter("shuttle.rejections", "reason", "duplicate").increment();
            throw new BadRequestException("Employee already has an active booking on this trip");
        }

        CostCentreQuota quota = quotaFor(tripId, costCentre);
        long seq = trip.nextSeq();   // assigned inside the lock, so no ties
        Booking booking = new Booking(tripId, employeeId, costCentre,
                partySize, tier, seq, Instant.now());

        boolean waitlistEmpty = bookings.findOrdered(tripId, BookingState.WAITLISTED).isEmpty();
        boolean seatsFit = trip.freeSeats() >= partySize;
        boolean quotaFits = quota.remaining() >= partySize;

        if (waitlistEmpty && seatsFit && quotaFits) {
            trip.reserve(partySize);
            quota.use(partySize);
            booking.confirm();
            metrics.counter("shuttle.bookings", "result", "confirmed").increment();
        } else {
            if (seatsFit && !quotaFits) metrics.counter("shuttle.quota_blocks").increment();
            metrics.counter("shuttle.bookings", "result", "waitlisted").increment();
        }
        log.info("event=book tripId={} employeeId={} partySize={} state={}",
                tripId, employeeId, partySize, booking.getState());
        return bookings.save(booking);
    }

    /** Cancels a booking; if it held seats, frees them and promotes from the waitlist. */
    @Transactional
    @CacheEvict(cacheNames = "availability", allEntries = true)
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
        metrics.counter("shuttle.cancellations").increment();
        log.info("event=cancel bookingId={} heldSeats={}", bookingId, heldSeats);

        if (!heldSeats) {
            return List.of();   // a waitlisted cancel frees nothing
        }

        trip.release(booking.getPartySize());
        quotaFor(trip.getId(), booking.getCostCentre()).release(booking.getPartySize());
        return promote(trip);
    }

    @Transactional
    public Booking checkIn(Long bookingId, String userId) {
        Booking b = bookings.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));
        lockTrip(b.getTripId());
        em.refresh(b);
        if (!b.getEmployeeId().equals(userId)) throw new ForbiddenException("Not your booking");
        if (b.getState() != BookingState.CONFIRMED) {
            throw new BadRequestException("Only confirmed bookings can check in");
        }
        b.checkIn(Instant.now());
        return b;
    }

    /**
     * No-show reclaim: after the cut-off, cancel every confirmed booking that never checked in,
     * then promote from the waitlist. A trip is processed once, so re-running is safe.
     */
    @Transactional
    @CacheEvict(cacheNames = "availability", allEntries = true)
    public List<Booking> reclaimNoShows(Long tripId, boolean force) {
        Trip trip = lockTrip(tripId);
        if (trip.isNoShowProcessed()) return List.of();
        if (!force && Instant.now().isBefore(trip.getCheckInCutoff())) return List.of();

        List<Booking> noShows = bookings.findByTripIdAndState(tripId, BookingState.CONFIRMED).stream()
                .filter(b -> b.getCheckedInAt() == null).toList();
        for (Booking b : noShows) {
            b.cancel("NO_SHOW");
            trip.release(b.getPartySize());
            quotaFor(tripId, b.getCostCentre()).release(b.getPartySize());
        }
        trip.markNoShowProcessed();
        metrics.counter("shuttle.no_show_reclaims").increment(noShows.size());
        log.info("event=reclaim tripId={} reclaimed={}", tripId, noShows.size());
        return promote(trip);
    }

    /** Admin changes quotas. Extra quota can unblock waiting people, so we promote. */
    @Transactional
    @CacheEvict(cacheNames = "availability", allEntries = true)
    public List<Booking> updateQuotas(Long tripId, Map<String, Integer> newQuotas) {
        Trip trip = lockTrip(tripId);
        newQuotas.forEach((cc, q) -> {
            if (q == null || q < 0) throw new BadRequestException("Quota must be >= 0 for " + cc);
            CostCentreQuota existing = quotas.findByTripIdAndCostCentre(tripId, cc).orElse(null);
            if (existing == null) {
                quotas.save(new CostCentreQuota(tripId, cc, q));
            } else {
                if (q < existing.getSeatsUsed()) {
                    throw new BadRequestException("Quota for " + cc + " is below seats already used");
                }
                existing.setQuota(q);
            }
        });
        quotas.flush();
        return promote(trip);
    }

    /**
     * Walks the waitlist in order. A booking is promoted only if its WHOLE party fits both
     * the vehicle and its cost-centre quota; otherwise it is skipped and keeps its place.
     * Must be called while holding the trip lock.
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
                // outbox row is written in the SAME transaction as the promotion
                outbox.save(new NotificationOutbox(b.getId(), b.getEmployeeId(),
                        "Your seat on trip " + trip.getId() + " is confirmed"));
                metrics.counter("shuttle.promotions").increment();
                log.info("event=promoted tripId={} bookingId={} employeeId={}",
                        trip.getId(), b.getId(), b.getEmployeeId());
            } else {
                b.recordSkip();
                if (b.getPartySize() > 1 && !seatsFit) metrics.counter("shuttle.group_skips").increment();
                if (seatsFit && !quotaFits) metrics.counter("shuttle.quota_blocks").increment();
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
