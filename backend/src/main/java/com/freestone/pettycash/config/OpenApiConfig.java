package com.freestone.pettycash.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pettyCashOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Petty Cash Manager API")
                        .description("REST API for managing petty cash transactions, expenses, and reports")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Freestone Infotech")
                                .url("https://freestoneinfotech.com")));
    }
}
