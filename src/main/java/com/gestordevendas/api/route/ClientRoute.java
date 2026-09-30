package com.gestordevendas.api.route;

import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.company.Company;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "client_routes", uniqueConstraints = @UniqueConstraint(name="uk_client_route_day", columnNames={"company_id","route_day","client_id"}))
public class ClientRoute {
 @Id private UUID id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id", nullable=false) private Company company;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="client_id", nullable=false) private Client client;
 @Enumerated(EnumType.STRING) @Column(name="route_day", nullable=false, length=12) private RouteDay day;
 @Column(name="position", nullable=false) private int position;
 protected ClientRoute() {}
 public static ClientRoute create(Company company, Client client, RouteDay day, int position) { var r=new ClientRoute(); r.id=UUID.randomUUID(); r.company=company; r.client=client; r.day=day; r.position=position; return r; }
 public UUID getId(){return id;} public Client getClient(){return client;} public RouteDay getDay(){return day;} public int getPosition(){return position;} public void setPosition(int p){position=p;}
}
