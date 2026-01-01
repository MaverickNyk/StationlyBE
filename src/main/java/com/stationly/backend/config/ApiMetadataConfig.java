package com.stationly.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class ApiMetadataConfig implements WebMvcConfigurer {

        /**
         * This method maps the "/docs" URL to the Scalar UI.
         * It solves the NoResourceFoundException by providing a direct route.
         */
        @Override
        public void addViewControllers(ViewControllerRegistry registry) {
                // Redirects /docs to the Scalar default path
                registry.addRedirectViewController("/docs", "/scalar");
                // Also handles /docs/ with a trailing slash just in case
                registry.addRedirectViewController("/docs/", "/scalar");
        }

        @Bean
        public OpenAPI stationlyOpenAPI() {
                return new OpenAPI()
                                .info(new Info()
                                                .title("Stationly API documentation")
                                                .description(
                                                                "API documentation for Stationly Backend. Provides access to Stations data, arrival predictions, and line status updates.\n\nOwned and maintained by **Stationly Limited**.")
                                                .version("v1.0.0")
                                                .contact(new Contact().name("Stationly Limited")
                                                                .email("support@stationly.co.uk"))
                                                .license(new License().name("Apache 2.0").url("http://springdoc.org")))
                                .servers(List.of(
                                                new Server().url("https://api.stationly.co.uk/StationlyBE")
                                                                .description("Production Server"),
                                                new Server().url("http://localhost:8080/StationlyBE")
                                                                .description("Local Development")));
        }
}