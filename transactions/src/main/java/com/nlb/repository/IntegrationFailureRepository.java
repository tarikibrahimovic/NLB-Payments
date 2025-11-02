package com.nlb.repository;

import com.nlb.domain.IntegrationFailure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IntegrationFailureRepository extends JpaRepository<IntegrationFailure, UUID> {}