package com.gestordevendas.api.route;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRouteRepository extends JpaRepository<ClientRoute, UUID> {

 @EntityGraph(attributePaths = "client")
 List<ClientRoute> findAllByCompanyIdOrderByDayAscPositionAsc(UUID companyId);

 @EntityGraph(attributePaths = "client")
 List<ClientRoute> findAllByCompanyIdAndDayOrderByPositionAsc(
         UUID companyId,
         RouteDay day
 );

 Optional<ClientRoute> findByCompanyIdAndDayAndClientId(
         UUID companyId,
         RouteDay day,
         UUID clientId
 );
}