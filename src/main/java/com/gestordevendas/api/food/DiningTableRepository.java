package com.gestordevendas.api.food;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DiningTableRepository extends JpaRepository<DiningTable, UUID> {
    List<DiningTable> findAllByCompany_IdOrderByNumberAsc(UUID companyId);
    List<DiningTable> findAllByCompany_IdAndActiveTrueOrderByNumberAsc(UUID companyId);
    Optional<DiningTable> findByIdAndCompany_IdAndActiveTrue(UUID id, UUID companyId);
    Optional<DiningTable> findByCompany_IdAndNumberAndActiveTrue(UUID companyId, Integer number);
    boolean existsByCompany_IdAndNumberAndActiveTrue(UUID companyId, Integer number);
    boolean existsByCompany_IdAndActiveTrueAndNameIgnoreCase(UUID companyId, String name);
    boolean existsByCompany_IdAndActiveTrueAndNameIgnoreCaseAndIdNot(UUID companyId, String name, UUID id);
}
