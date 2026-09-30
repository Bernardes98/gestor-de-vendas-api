package com.gestordevendas.api.supplier;
import com.gestordevendas.api.common.error.ApiException; import com.gestordevendas.api.common.security.CurrentUserService; import com.gestordevendas.api.company.*; import com.gestordevendas.api.tenant.*;
import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class SupplierService {
 private final SupplierRepository repository; private final CompanyRepository companyRepository; private final CurrentUserService currentUserService; private final TenantContextService tenantContextService; private final TenantGuard tenantGuard;
 public SupplierService(SupplierRepository r,CompanyRepository c,CurrentUserService u,TenantContextService t,TenantGuard g){repository=r;companyRepository=c;currentUserService=u;tenantContextService=t;tenantGuard=g;}
 @Transactional(readOnly=true) public List<SupplierResponse> list(){var c=current(); return repository.findAllByCompanyIdOrderByOrderIndexAscNameAsc(c.companyId()).stream().map(SupplierResponse::from).toList();}
 @Transactional public SupplierResponse create(SupplierRequest req){var c=admin(); ensure(c.companyId(),req.name(),null); Company company=companyRepository.findById(c.companyId()).orElseThrow(); return SupplierResponse.from(repository.save(Supplier.create(company,req.name(),repository.maxOrder(c.companyId())+1)));}
 @Transactional public SupplierResponse update(UUID id,SupplierRequest req){var c=admin(); Supplier s=require(id,c.companyId()); ensure(c.companyId(),req.name(),s); s.setName(req.name()); return SupplierResponse.from(s);}
 @Transactional public List<SupplierResponse> reorder(SupplierReorderRequest req){var c=admin(); var all=repository.findAllByCompanyIdOrderByOrderIndexAscNameAsc(c.companyId()); if(req.supplierIds().size()!=all.size()||new HashSet<>(req.supplierIds()).size()!=all.size()) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SUPPLIER_ORDER","A ordem deve conter todos os fornecedores uma única vez."); var byId=new HashMap<UUID,Supplier>(); all.forEach(s->byId.put(s.getId(),s)); for(int i=0;i<req.supplierIds().size();i++){Supplier s=byId.get(req.supplierIds().get(i)); if(s==null) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SUPPLIER_ORDER","Fornecedor inválido para esta empresa."); s.setOrderIndex(i+1);} return req.supplierIds().stream().map(byId::get).map(SupplierResponse::from).toList();}
 @Transactional public void delete(UUID id){var c=admin(); repository.delete(require(id,c.companyId()));}
 private Supplier require(UUID id,UUID companyId){return repository.findByIdAndCompanyId(id,companyId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"SUPPLIER_NOT_FOUND","Fornecedor não encontrado."));}
 private void ensure(UUID companyId,String name,Supplier current){boolean exists=repository.existsByCompanyIdAndNameIgnoreCase(companyId,name.trim()); if(exists&&(current==null||!current.getName().equalsIgnoreCase(name.trim()))) throw new ApiException(HttpStatus.CONFLICT,"SUPPLIER_ALREADY_EXISTS","Já existe um fornecedor com este nome.");}
 private TenantContext current(){return tenantContextService.requireForUser(currentUserService.requireUserId());} private TenantContext admin(){var c=current(); tenantGuard.requireOwnerOrAdmin(c); return c;}
}
