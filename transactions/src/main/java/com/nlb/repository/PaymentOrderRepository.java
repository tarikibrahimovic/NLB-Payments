package com.nlb.repository;

import com.nlb.domain.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {
    Optional<PaymentOrder> findByIdempotencyKey(String idempotencyKey);

    List<PaymentOrder> findByInitiatedByUserIdOrderByCreatedAtDesc(UUID initiatedByUserId);

    Optional<PaymentOrder> findByIdAndInitiatedByUserId(UUID id, UUID initiatedByUserId);

}