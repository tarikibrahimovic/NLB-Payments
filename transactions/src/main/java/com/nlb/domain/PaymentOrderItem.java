package com.nlb.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "payment_order_items", indexes = {
        @Index(name = "idx_paymentitem_order_key", columnList = "order_key", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentOrderItem extends BaseEntity{

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_order_id", nullable = false)
    private PaymentOrder paymentOrder;

    @Column(name = "destination_account_id", nullable = false)
    private UUID destinationAccountId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentOrderItemStatus status;

    // Jedinstveni ključ izveden iz idempotencyKey + index
    // npr. "key-abc-123#0", "key-abc-123#1"
    @Column(name = "order_key", nullable = false, unique = true, length = 255)
    private String orderKey;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}