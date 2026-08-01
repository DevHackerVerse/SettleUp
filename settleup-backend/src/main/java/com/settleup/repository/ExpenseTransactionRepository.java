package com.settleup.repository;

import com.settleup.entity.ExpenseTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseTransactionRepository extends JpaRepository<ExpenseTransaction, Long> {

    Optional<ExpenseTransaction> findByPublicId(UUID publicId);

    /**
     * Paginated expense list for a group — used by GET /groups/{groupId}/expenses.
     * Spec requires page/size params, default size 20.
     */
    Page<ExpenseTransaction> findByGroupIdOrderByCreatedAtDesc(Long groupId, Pageable pageable);
}
