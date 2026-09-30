package com.gestordevendas.api.route;
import com.gestordevendas.api.client.*; import com.gestordevendas.api.common.security.CurrentUserService; import com.gestordevendas.api.company.*; import com.gestordevendas.api.tenant.*;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.util.*;
@Service
public class ClientRouteService {
 private final ClientRouteRepository repo; private final ClientService clients; private final CompanyRepository companies; private final CurrentUserService currentUser; private final TenantContextService tenants; private final TenantGuard guard;
 public ClientRouteService(ClientRouteRepository r, ClientService c, CompanyRepository co, CurrentUserService u, TenantContextService t, TenantGuard g){repo=r;clients=c;companies=co;currentUser=u;tenants=t;guard=g;}
 private TenantContext ctx(){return tenants.requireForUser(currentUser.requireUserId());}
 @Transactional(readOnly=true) public List<RouteClientResponse> list(){var c=ctx(); return repo.findAllByCompanyIdOrderByDayAscPositionAsc(c.companyId()).stream().map(RouteClientResponse::from).toList();}
 @Transactional public RouteClientResponse add(RouteDay day, UUID clientId){var c=ctx();guard.requireOwnerOrAdmin(c); var existing=repo.findByCompanyIdAndDayAndClientId(c.companyId(),day,clientId); if(existing.isPresent()) return RouteClientResponse.from(existing.get()); var client=clients.requireActive(clientId,c.companyId()); var company=companies.findById(c.companyId()).orElseThrow(); int pos=repo.findAllByCompanyIdAndDayOrderByPositionAsc(c.companyId(),day).size()+1; return RouteClientResponse.from(repo.save(ClientRoute.create(company,client,day,pos)));}
 @Transactional public void remove(RouteDay day, UUID clientId){var c=ctx();guard.requireOwnerOrAdmin(c); repo.findByCompanyIdAndDayAndClientId(c.companyId(),day,clientId).ifPresent(repo::delete); normalize(c.companyId(),day);}
 @Transactional public List<RouteClientResponse> reorder(RouteDay day, RouteReorderRequest req){var c=ctx();guard.requireOwnerOrAdmin(c); var rows=repo.findAllByCompanyIdAndDayOrderByPositionAsc(c.companyId(),day); var map=new HashMap<UUID,ClientRoute>(); rows.forEach(r->map.put(r.getClient().getId(),r)); int p=1; for(UUID id:req.clientIds()){var r=map.remove(id); if(r!=null) r.setPosition(p++);} for(var r:rows) if(map.containsKey(r.getClient().getId())) r.setPosition(p++); return repo.findAllByCompanyIdAndDayOrderByPositionAsc(c.companyId(),day).stream().map(RouteClientResponse::from).toList();}
 private void normalize(UUID companyId, RouteDay day){int p=1; for(var r:repo.findAllByCompanyIdAndDayOrderByPositionAsc(companyId,day)) r.setPosition(p++);}
}
