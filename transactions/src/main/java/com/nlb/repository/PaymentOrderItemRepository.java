package com.nlb.repository;

import com.nlb.domain.PaymentOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaymentOrderItemRepository extends JpaRepository<PaymentOrderItem, UUID> {
}