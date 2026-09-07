package com.bank.iso20022converter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI iso20022ConverterOpenApi() {
        return new OpenAPI().info(new Info()
                .title("ISO 20022 Message Conversion API")
                .description("XML<->JSON conversion and cross-message-type ('clearing') "
                        + "transformation for a curated set of ISO 20022 payment messages.")
                .version("v1"));
    }
}
