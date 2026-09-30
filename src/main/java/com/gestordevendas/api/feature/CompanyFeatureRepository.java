package com.gestordevendas.api.feature;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CompanyFeatureRepository extends JpaRepository<CompanyFeatureAccess, UUID> {
    List<CompanyFeatureAccess> findAllByCompany_Id(UUID companyId);
    Optional<CompanyFeatureAccess> findByCompany_IdAndFeature(UUID companyId, CompanyFeature feature);
}
