package com.gestordevendas.api.food;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DiningTableRepository extends JpaRepository<DiningTable,UUID>{List<DiningTable> findAllByCompany_IdOrderByNumberAsc(UUID companyId);boolean existsByCompany_IdAndNumber(UUID companyId,Integer number);}
