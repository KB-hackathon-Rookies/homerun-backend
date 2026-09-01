package com.homerun.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI homerunOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("HomeRun Backend API")
                        .description("주소 검색, 건축물대장 및 주택 실거래가 통합 API")
                        .version("v1")
                        .contact(new Contact().name("KB Hackathon Rookies")));
    }
}
