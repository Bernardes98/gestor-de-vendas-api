package com.gestordevendas.api.push;
import com.fasterxml.jackson.databind.ObjectMapper;import nl.martijndwars.webpush.Notification;import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Service;import org.springframework.transaction.event.TransactionalEventListener;import org.springframework.transaction.event.TransactionPhase;import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
@Service public class PushService {
 private final PushSubscriptionRepository repo;private final ApplicationEventPublisher publisher;private final ObjectMapper json;
 @Value("${push.vapid.public-key:}") private String publicKey; @Value("${push.vapid.private-key:}") private String privateKey; @Value("${push.vapid.subject:mailto:admin@gestordevendas.online}") private String subject;
 public PushService(PushSubscriptionRepository r,ApplicationEventPublisher p,ObjectMapper j){repo=r;publisher=p;json=j;}
 public String publicKey(){return publicKey;}
 public void register(UUID company,UUID user,String endpoint,String p256dh,String auth){if(endpoint==null||!endpoint.startsWith("https://")||endpoint.length()>2048||p256dh==null||auth==null)throw new IllegalArgumentException("Inscrição push inválida");repo.findByEndpoint(endpoint).ifPresent(repo::delete);repo.save(new PushSubscription(company,user,endpoint,p256dh,auth));}
 public void unregister(UUID company,UUID user,String endpoint){repo.findByEndpoint(endpoint).filter(s->s.getCompanyId().equals(company)&&s.getUserId().equals(user)).ifPresent(repo::delete);}
 public void order(UUID company,String kind){publisher.publishEvent(new NewOrder(company,kind));}
 public record NewOrder(UUID company,String kind){}
 @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT) public void deliver(NewOrder e){
 if(publicKey.isBlank()||privateKey.isBlank())return;
 try {var service=new nl.martijndwars.webpush.PushService(publicKey,privateKey,subject);var payload=json.writeValueAsString(Map.of("title","Novo pedido recebido!","body",e.kind().equals("snack")?"Novo pedido de lanche aguardando aprovação":"Novo pedido de produtos aguardando análise","url",e.kind().equals("snack")?"/comandas":"/pedidos","tag","pedido-"+UUID.randomUUID()));
 for(var sub:repo.findByCompanyId(e.company()))try{service.send(new Notification(sub.getEndpoint(),sub.getP256dh(),sub.getAuth(),payload));}catch(Exception ex){System.err.println("Push não entregue: "+ex.getMessage());}
 }catch(Exception ex){System.err.println("Falha ao enviar push: "+ex.getMessage());}
 }
}
