package com.gestordevendas.api.fixedexpense;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface FixedExpenseRepository extends JpaRepository<FixedExpense, UUID> {
    List<FixedExpense> findAllByCompany_IdOrderByNameAsc(UUID companyId);
    Optional<FixedExpense> findByIdAndCompany_Id(UUID id, UUID companyId);
}
