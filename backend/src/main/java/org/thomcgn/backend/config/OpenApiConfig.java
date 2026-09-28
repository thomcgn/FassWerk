package org.thomcgn.backend.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {

    @Bean
    OpenAPI fassWerkOpenApi(@Nullable BuildProperties buildProperties) {
        String version = buildProperties != null ? buildProperties.getVersion() : "dev";

        // Keep the contract independent of the host and port used to export it.
        return new OpenAPI().addServersItem(new Server().url("/")).info(new Info()
                .title("FassWerk API")
                .version(version)
                .description("API fuer Reservierung, Tischabrechnung, Lagerverwaltung, Bar-Administration und Schichtabrechnung.")
                .contact(new Contact().name("FassWerk Team")));
    }
}

