package com.codescope.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CodeScope Backend API")
                        .version("1.0.0")
                        .description("""
                                Intelligent Application Analysis & Feedback Engine  
                                ---
                                Use this API to authenticate, upload projects, analyze source code, 
                                and generate AI-based improvement reports.
                                """)
                        .contact(new Contact()
                                .name("CodeScope Dev Team")
                                .email("support@codescope.ai")
                                .url("https://codescope.ai"))
                        .license(new License().name("Apache 2.0").url("http://springdoc.org"))
                )
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Dev Server"),
                        new Server().url("https://api.codescope.ai").description("Production Server")
                ));
    }
}
