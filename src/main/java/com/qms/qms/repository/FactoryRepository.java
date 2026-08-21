package com.qms.qms.repository;

import com.qms.qms.entity.Factory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FactoryRepository extends JpaRepository<Factory, Long> {
    Optional<Factory> findByCodeIgnoreCase(String code);
}
