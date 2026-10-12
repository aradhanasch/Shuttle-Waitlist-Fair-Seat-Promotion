package com.example.shuttle.controller;

import com.example.shuttle.config.Auth;
import com.example.shuttle.domain.Booking;
import com.example.shuttle.domain.BookingState;
import com.example.shuttle.domain.Role;
import com.example.shuttle.dto.Dtos.*;
import com.example.shuttle.exception.ForbiddenException;
import com.example.shuttle.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping("/trips/{tripId}/bookings")
    public ResponseEntity<BookingView> book(
            @PathVariable Long tripId,
            @RequestHeader(value = "X-User", required = false) String user,
            @RequestHeader(value = "X-Role", required = false) String role,
            @Valid @RequestBody CreateBookingRequest req) {
        Auth auth = Auth.of(user, role);
        auth.require(Role.EMPLOYEE);
        Booking b = service.book(tripId, auth.userId(), req.costCentre(), req.partySize(), req.tier());
        // 201 = seated now, 202 = accepted onto the waitlist
        return ResponseEntity.status(b.getState() == BookingState.CONFIRMED ? 201 : 202)
                .body(BookingView.of(b));
    }

    @DeleteMapping("/bookings/{id}")
    public ActionResult cancel(
            @PathVariable Long id,
            @RequestHeader(value = "X-User", required = false) String user,
            @RequestHeader(value = "X-Role", required = false) String role) {
        Auth auth = Auth.of(user, role);
        Booking existing = service.find(id);
        if (auth.role() != Role.ADMIN && !existing.getEmployeeId().equals(auth.userId())) {
            throw new ForbiddenException("Only the owner or an admin can cancel this booking");
        }
        var promoted = service.cancel(id, auth.role() == Role.ADMIN ? "ADMIN" : "USER");
        return new ActionResult(id, promoted.stream().map(Booking::getId).toList());
    }

    @PostMapping("/bookings/{id}/check-in")
    public BookingView checkIn(
            @PathVariable Long id,
            @RequestHeader(value = "X-User", required = false) String user,
            @RequestHeader(value = "X-Role", required = false) String role) {
        Auth auth = Auth.of(user, role);
        auth.require(Role.EMPLOYEE);
        return BookingView.of(service.checkIn(id, auth.userId()));
    }
}
