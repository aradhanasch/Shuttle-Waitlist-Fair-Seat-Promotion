package com.example.shuttle.repository;

import com.example.shuttle.domain.CostCentreQuota;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CostCentreQuotaRepository extends JpaRepository<CostCentreQuota, Long> {
    Optional<CostCentreQuota> findByTripIdAndCostCentre(Long tripId, String costCentre);
}