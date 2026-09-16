package com.gestordevendas.api.user;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyMembershipRepository extends JpaRepository<CompanyMembership, UUID> {
    @Override
    @EntityGraph(attributePaths = {"company", "user"})
    Optional<CompanyMembership> findById(UUID id);

    @Query("select m from CompanyMembership m join fetch m.company join fetch m.user where m.user.id = :userId")
    Optional<CompanyMembership> findByUserId(@Param("userId") UUID userId);

    boolean existsByCompanyIdAndUserId(UUID companyId, UUID userId);

    @Query("select m from CompanyMembership m join fetch m.user where m.company.id = :companyId and m.active = true")
    List<CompanyMembership> findActiveByCompanyId(@Param("companyId") UUID companyId);

    @Query("select count(m) from CompanyMembership m where m.company.id = :companyId and m.role = :role and m.active = true")
    long countActiveByCompanyIdAndRole(@Param("companyId") UUID companyId, @Param("role") CompanyRole role);
}
