package com.gestordevendas.api.sale;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {
    @EntityGraph(attributePaths = {"company", "client"})
    Optional<Sale> findByIdAndCompanyId(UUID id, UUID companyId);

    @EntityGraph(attributePaths = {"company", "client"})
    List<Sale> findAllByCompanyIdOrderBySoldAtDesc(UUID companyId);

    @Query(value = """
        select s.* from public.vendas s
        left join api_internal.venda_estados ve on ve.venda_id = s.id
        where s.empresa_id = :companyId
          and coalesce(ve.status, 'ATIVA') = 'ATIVA'
          and coalesce(ve.forma_pagamento,
              case when s.status_pagamento = 'A_RECEBER' then 'PRAZO' else 'AVISTA' end) = 'PRAZO'
        order by s.data_venda desc
        """, nativeQuery = true)
    List<Sale> findAllActiveCredit(@Param("companyId") UUID companyId);

    @Query(value = """
        select s.* from public.vendas s
        left join api_internal.venda_estados ve on ve.venda_id = s.id
        where s.empresa_id = :companyId
          and coalesce(ve.status, 'ATIVA') = 'ATIVA'
        order by s.data_venda desc
        limit 5
        """, nativeQuery = true)
    List<Sale> findTop5Active(@Param("companyId") UUID companyId);

    @Query(value = """
        select s.* from public.vendas s
        left join api_internal.venda_estados ve on ve.venda_id = s.id
        where s.empresa_id = :companyId
          and coalesce(ve.status, 'ATIVA') = 'ATIVA'
          and s.data_venda >= :from and s.data_venda < :to
        order by s.data_venda desc
        """, nativeQuery = true)
    List<Sale> findAllActiveInRange(@Param("companyId") UUID companyId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to);

    long countByCompanyId(UUID companyId);
}
