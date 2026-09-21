package com.gestordevendas.api.client;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {
    @EntityGraph(attributePaths = "company")
    Optional<Client> findByIdAndCompanyIdAndActiveTrue(UUID id, UUID companyId);

    @EntityGraph(attributePaths = "company")
    List<Client> findAllByCompanyIdAndActiveTrueOrderByNameAsc(UUID companyId);

    @EntityGraph(attributePaths = "company")
    Optional<Client> findByOrderTokenAndActiveTrue(UUID orderToken);

    long countByCompanyId(UUID companyId);
}
