package com.nlb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Apstraktna bazna klasa koja pruža 'createdAt' i 'updatedAt' polja
 * za sve entitete koji je naslede.
 * * @MappedSuperclass govori JPA da uključi ova polja u tabele
 * podklasa, umesto da kreira 'base_entity' tabelu.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}