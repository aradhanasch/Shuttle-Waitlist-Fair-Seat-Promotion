package com.example.shuttle.dto;

import com.example.shuttle.domain.Booking;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class Dtos {
    private Dtos() {}

    public record CreateTripRequest(@Min(1) int capacity,
                                    @NotNull Instant checkInCutoff,
                                    @NotEmpty Map<String, Integer> quotas) {}

    public record QuotaRequest(@NotEmpty Map<String, Integer> quotas) {}

    public record CreateBookingRequest(@NotBlank String costCentre,
                                       @Min(1) @Max(50) int partySize,
                                       int tier) {}

    public record BookingView(Long id, Long tripId, String employeeId, String costCentre,
                              int partySize, String state, long requestSeq) {
        public static BookingView of(Booking b) {
            return new BookingView(b.getId(), b.getTripId(), b.getEmployeeId(), b.getCostCentre(),
                    b.getPartySize(), b.getState().name(), b.getRequestSeq());
        }
    }

    public record TripView(Long id, int capacity, int seatsConfirmed, int freeSeats,
                           long waitlistLength, Instant checkInCutoff) {}

    public record WaitlistEntry(int position, Long bookingId, String employeeId,
                                String costCentre, int partySize, int tier, int skipCount) {}

    public record ActionResult(Long id, List<Long> promotedBookingIds) {}

    public record ErrorResponse(String code, String message, Instant timestamp) {}
}
