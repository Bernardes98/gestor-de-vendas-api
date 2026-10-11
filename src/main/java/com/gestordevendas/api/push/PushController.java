package com.gestordevendas.api.push;
import com.gestordevendas.api.common.security.CurrentUserService;import com.gestordevendas.api.tenant.TenantContextService;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/push") public class PushController {
 private final PushService push;private final CurrentUserService users;private final TenantContextService tenants;
 public PushController(PushService p,CurrentUserService u,TenantContextService t){push=p;users=u;tenants=t;}
 public record Keys(String p256dh,String auth){} public record Subscription(String endpoint,Keys keys){}
 @GetMapping("/key") public java.util.Map<String,String> key(){return java.util.Map.of("publicKey",push.publicKey());}
 @PostMapping("/subscriptions") public void register(@RequestBody Subscription s){var ctx=tenants.requireForUser(users.requireUserId());push.register(ctx.companyId(),ctx.userId(),s.endpoint(),s.keys().p256dh(),s.keys().auth());}
 @DeleteMapping("/subscriptions") public void unregister(@RequestBody Subscription s){var ctx=tenants.requireForUser(users.requireUserId());push.unregister(ctx.companyId(),ctx.userId(),s.endpoint());}
}
