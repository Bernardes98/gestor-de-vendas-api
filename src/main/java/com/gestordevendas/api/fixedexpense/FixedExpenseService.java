package com.gestordevendas.api.fixedexpense;
import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.company.CompanyRepository;
import com.gestordevendas.api.feature.*;
import com.gestordevendas.api.tenant.*;
import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class FixedExpenseService {
    private final FixedExpenseRepository repo; private final CompanyRepository companies; private final CurrentUserService currentUser; private final TenantContextService tenants; private final TenantGuard guard; private final CompanyFeatureService features;
    public FixedExpenseService(FixedExpenseRepository r, CompanyRepository c, CurrentUserService u, TenantContextService t, TenantGuard g, CompanyFeatureService f){repo=r;companies=c;currentUser=u;tenants=t;guard=g;features=f;}
    private TenantContext ctx(){var c=tenants.requireForUser(currentUser.requireUserId()); features.require(c.companyId(), CompanyFeature.FIXED_EXPENSES); return c;}
    @Transactional(readOnly=true) public List<FixedExpenseResponse> list(){var c=ctx(); return repo.findAllByCompany_IdOrderByNameAsc(c.companyId()).stream().map(FixedExpenseResponse::from).toList();}
    @Transactional public FixedExpenseResponse create(FixedExpenseRequest req){var c=ctx(); guard.requireOwner(c); var company=companies.findById(c.companyId()).orElseThrow(); return FixedExpenseResponse.from(repo.save(FixedExpense.create(company, clean(req.name()), req.amount(), req.dueDay(), cleanNullable(req.notes()))));}
    @Transactional public FixedExpenseResponse update(UUID id, FixedExpenseRequest req){var c=ctx(); guard.requireOwner(c); var e=require(id,c.companyId()); e.update(clean(req.name()),req.amount(),req.dueDay(),cleanNullable(req.notes())); return FixedExpenseResponse.from(e);}
    @Transactional public void delete(UUID id){var c=ctx(); guard.requireOwner(c); repo.delete(require(id,c.companyId()));}
    private FixedExpense require(UUID id, UUID companyId){return repo.findByIdAndCompany_Id(id,companyId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"FIXED_EXPENSE_NOT_FOUND","Gasto fixo não encontrado."));}
    private String clean(String s){return s.trim();} private String cleanNullable(String s){return s==null||s.isBlank()?null:s.trim();}
}
