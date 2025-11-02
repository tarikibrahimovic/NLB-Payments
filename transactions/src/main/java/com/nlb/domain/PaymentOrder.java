package com.nlb.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "payment_orders", indexes = {
        // Ključni indeks za idempotenciju. Mora biti UNIQUE.
        @Index(name = "idx_paymentorder_idempotency_key", columnList = "idempotency_key", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentOrder extends BaseEntity{

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(name = "initiated_by_user_id", nullable = false)
    private UUID initiatedByUserId; // Korisnik koji je vlasnik JWT tokena

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "total_amount_cents", nullable = false)
    private long totalAmountCents; // Suma svih PaymentOrderItem-a (za brzu proveru salda)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentOrderStatus status;

    // CascadeType.ALL: Ako obrišemo Order, brišu se i svi njegovi Item-i.
    // mappedBy: Označava da PaymentOrderItem ima polje 'paymentOrder' koje upravlja vezom.
    @OneToMany(mappedBy = "paymentOrder", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<PaymentOrderItem> items = new ArrayList<>();

    public void addItem(PaymentOrderItem item) {
        items.add(item);
        item.setPaymentOrder(this);
    }
}