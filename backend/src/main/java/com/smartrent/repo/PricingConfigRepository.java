package com.smartrent.repo;

import com.smartrent.domain.PricingConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingConfigRepository extends JpaRepository<PricingConfig, Long> {
}
