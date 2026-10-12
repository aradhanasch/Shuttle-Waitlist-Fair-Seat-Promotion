package com.example.shuttle.controller;

import com.example.shuttle.config.Auth;
import com.example.shuttle.domain.Booking;
import com.example.shuttle.domain.Role;
import com.example.shuttle.domain.Trip;
import com.example.shuttle.dto.Dtos.*;
import com.example.shuttle.service.BookingService;
import com.example.shuttle.service.TripService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class TripController {

    private final TripService tripService;
    private final BookingService bookingService;

    public TripController(TripService tripService, BookingService bookingService) {
        this.tripService = tripService;
        this.bookingService = bookingService;
    }

    @PostMapping("/trips")
    public ResponseEntity<TripView> create(
            @RequestHeader(value = "X-User", required = false) String user,
            @RequestHeader(value = "X-Role", required = false) String role,
            @Valid @RequestBody CreateTripRequest req) {
        Auth.of(user, role).require(Role.ADMIN);
        Trip t = tripService.create(req.capacity(), req.checkInCutoff(), req.quotas());
        return ResponseEntity.status(201).body(tripService.availability(t.getId()));
    }

    @PutMapping("/trips/{id}/quotas")
    public ActionResult updateQuotas(
            @PathVariable Long id,
            @RequestHeader(value = "X-User", required = false) String user,
            @RequestHeader(value = "X-Role", required = false) String role,
            @Valid @RequestBody QuotaRequest req) {
        Auth.of(user, role).require(Role.ADMIN);
        return result(id, bookingService.updateQuotas(id, req.quotas()));
    }

    @GetMapping("/trips/{id}")
    public TripView get(
            @PathVariable Long id,
            @RequestHeader(value = "X-User", required = false) String user,
            @RequestHeader(value = "X-Role", required = false) String role) {
        Auth.of(user, role);   // any authenticated role
        return tripService.availability(id);
    }

    @GetMapping("/trips/{id}/waitlist")
    public List<WaitlistEntry> waitlist(
            @PathVariable Long id,
            @RequestHeader(value = "X-User", required = false) String user,
            @RequestHeader(value = "X-Role", required = false) String role) {
        Auth.of(user, role).require(Role.APPROVER, Role.ADMIN);
        return tripService.waitlist(id);
    }

    @PostMapping("/trips/{id}/reclaim")
    public ActionResult reclaim(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestHeader(value = "X-User", required = false) String user,
            @RequestHeader(value = "X-Role", required = false) String role) {
        Auth.of(user, role).require(Role.ADMIN);
        return result(id, bookingService.reclaimNoShows(id, force));
    }

    private ActionResult result(Long id, List<Booking> promoted) {
        return new ActionResult(id, promoted.stream().map(Booking::getId).toList());
    }
}
