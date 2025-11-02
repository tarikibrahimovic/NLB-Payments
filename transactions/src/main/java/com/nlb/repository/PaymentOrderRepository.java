package com.nlb.repository;

import com.nlb.domain.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {

    /**
     * @param idempotencyKey Ključ poslat u Idempotency-Key header-u.
     * @return Optional<PaymentOrder>
     */
    Optional<PaymentOrder> findByIdempotencyKey(String idempotencyKey);
}