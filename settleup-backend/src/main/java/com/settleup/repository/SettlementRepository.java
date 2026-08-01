package com.settleup.repository;

import com.settleup.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findByPublicId(UUID publicId);

    /** Used by the Settlement Worker to detect duplicate message deliveries. */
    boolean existsByIdempotencyKey(UUID idempotencyKey);

    Optional<Settlement> findByIdempotencyKey(UUID idempotencyKey);
}
