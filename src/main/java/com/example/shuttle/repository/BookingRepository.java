package com.example.shuttle.repository;

import com.example.shuttle.domain.Booking;
import com.example.shuttle.domain.BookingState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Waitlist order: requestSeq ASC, then tier DESC, then id ASC
    @Query("""
           select b from Booking b
           where b.tripId = :tripId and b.state = :state
           order by b.requestSeq asc, b.tier desc, b.id asc
           """)
    List<Booking> findOrdered(@Param("tripId") Long tripId,
                              @Param("state") BookingState state);

    List<Booking> findByTripIdAndState(Long tripId, BookingState state);

    long countByTripIdAndState(Long tripId, BookingState state);

    boolean existsByTripIdAndEmployeeIdAndStateNot(Long tripId, String employeeId, BookingState state);
}
