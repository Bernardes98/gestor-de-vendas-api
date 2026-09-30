package com.gestordevendas.api.feature;

import com.gestordevendas.api.company.Company;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "company_features", uniqueConstraints = @UniqueConstraint(name = "uk_company_feature", columnNames = {"company_id", "feature"}))
public class CompanyFeatureAccess {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id", nullable = false) private Company company;
    @Enumerated(EnumType.STRING) @Column(name = "feature", nullable = false, length = 30) private CompanyFeature feature;
    @Column(name = "enabled", nullable = false) private boolean enabled;

    protected CompanyFeatureAccess() {}
    private CompanyFeatureAccess(Company company, CompanyFeature feature, boolean enabled) {
        this.id = UUID.randomUUID(); this.company = company; this.feature = feature; this.enabled = enabled;
    }
    public static CompanyFeatureAccess create(Company company, CompanyFeature feature, boolean enabled) { return new CompanyFeatureAccess(company, feature, enabled); }
    public CompanyFeature getFeature() { return feature; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
