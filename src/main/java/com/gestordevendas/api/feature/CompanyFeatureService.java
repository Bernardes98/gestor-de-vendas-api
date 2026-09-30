package com.gestordevendas.api.feature;

import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.company.CompanyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class CompanyFeatureService {
    private final CompanyFeatureRepository repository;
    private final CompanyRepository companyRepository;
    public CompanyFeatureService(CompanyFeatureRepository repository, CompanyRepository companyRepository) {
        this.repository = repository; this.companyRepository = companyRepository;
    }

    @Transactional(readOnly = true)
    public Map<CompanyFeature, Boolean> get(UUID companyId) {
        EnumMap<CompanyFeature, Boolean> result = new EnumMap<>(CompanyFeature.class);
        for (CompanyFeature feature : CompanyFeature.values()) result.put(feature, true);
        repository.findAllByCompany_Id(companyId).forEach(row -> result.put(row.getFeature(), row.isEnabled()));
        return result;
    }

    @Transactional(readOnly = true)
    public boolean enabled(UUID companyId, CompanyFeature feature) { return get(companyId).getOrDefault(feature, true); }

    @Transactional
    public Map<CompanyFeature, Boolean> update(UUID companyId, Map<CompanyFeature, Boolean> values) {
        var company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Empresa não encontrada."));
        values.forEach((feature, enabled) -> {
            var row = repository.findByCompany_IdAndFeature(companyId, feature)
                .orElseGet(() -> CompanyFeatureAccess.create(company, feature, enabled));
            row.setEnabled(enabled);
            repository.save(row);
        });
        return get(companyId);
    }

    public void require(UUID companyId, CompanyFeature feature) {
        if (!enabled(companyId, feature)) throw new ApiException(HttpStatus.FORBIDDEN, "FEATURE_DISABLED", "Este recurso não está liberado para a empresa.");
    }
}
