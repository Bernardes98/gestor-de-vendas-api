package com.gestordevendas.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.gestordevendas.api.report.BusinessTimeProperties;

@SpringBootApplication
@EnableConfigurationProperties(BusinessTimeProperties.class)
public class GestorDeVendasApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(GestorDeVendasApiApplication.class, args);
    }
}
