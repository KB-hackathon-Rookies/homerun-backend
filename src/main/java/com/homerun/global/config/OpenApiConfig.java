package com.homerun.global.config;

import com.homerun.global.security.config.PublicEndpoints;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI homerunOpenApi() {
        String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("HomeRun Backend API")
                        .description("주소 검색, 건축물대장 및 주택 실거래가 통합 API")
                        .version("v1")
                        .contact(new Contact().name("KB Hackathon Rookies")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .schemaRequirement(
                        schemeName,
                        new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"));
    }

    /**
     * 로그인 전에 부르는 경로에서 자물쇠를 뗀다.
     *
     * <p>위에서 {@code bearerAuth} 를 전역으로 걸기 때문에 모든 엔드포인트가 그것을 물려받는다. 그러면
     * 문서가 거짓말을 한다 — 아직 계정도 없는 사람이 회원가입에 쓰는 휴대전화·이메일 인증번호 발송에
     * "액세스 토큰이 필요하다" 고 적히고, 이 문서로 만든 클라이언트는 넣을 수 없는 토큰을 넣으려 든다.
     *
     * <p>어느 경로가 공개인지는 {@link PublicEndpoints} 가 정하고 보안 설정도 같은 것을 본다. 둘이
     * 갈라지지 않게 하려고 목록을 한 곳에 뒀다.
     */
    @Bean
    OpenApiCustomizer publicEndpointsAreNotLocked() {
        AntPathMatcher matcher = new AntPathMatcher();
        return openApi -> openApi.getPaths().forEach((path, item) -> {
            unlock(matcher, path, PublicEndpoints.GET, item, PathItem::getGet);
            unlock(matcher, path, PublicEndpoints.POST, item, PathItem::getPost);
        });
    }

    /** 경로가 목록에 걸리면 해당 오퍼레이션의 보안 요구를 비운다. 빈 목록이어야 전역 상속을 끊는다. */
    private void unlock(
            AntPathMatcher matcher,
            String path,
            String[] patterns,
            PathItem item,
            Function<PathItem, Operation> operationOf) {
        Operation operation = operationOf.apply(item);
        if (operation == null) {
            return;
        }
        if (Arrays.stream(patterns).anyMatch(pattern -> matcher.match(pattern, path))) {
            operation.setSecurity(List.of());
        }
    }
}
