package com.gestordevendas.api.push;
import jakarta.persistence.*;import java.util.UUID;
@Entity @Table(name="push_subscriptions") public class PushSubscription {
 @Id private UUID id=UUID.randomUUID(); @Column(name="company_id",nullable=false) private UUID companyId; @Column(name="user_id",nullable=false) private UUID userId;
 @Column(nullable=false,length=2048,unique=true) private String endpoint; @Column(nullable=false,length=512) private String p256dh; @Column(nullable=false,length=256) private String auth;
 protected PushSubscription(){} public PushSubscription(UUID company,UUID user,String endpoint,String p256dh,String auth){this.companyId=company;this.userId=user;this.endpoint=endpoint;this.p256dh=p256dh;this.auth=auth;}
 public UUID getCompanyId(){return companyId;} public UUID getUserId(){return userId;} public String getEndpoint(){return endpoint;} public String getP256dh(){return p256dh;} public String getAuth(){return auth;}
}
