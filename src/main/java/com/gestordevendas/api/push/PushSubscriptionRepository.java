package com.gestordevendas.api.push;
import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription,UUID> { List<PushSubscription> findByCompanyId(UUID id); Optional<PushSubscription> findByEndpoint(String endpoint); }
