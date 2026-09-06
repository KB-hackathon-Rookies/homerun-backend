package com.homerun.global.security.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 교차 출처 요청을 허용할 프론트 주소.
 *
 * <p>배포 주소가 아직 없어서 코드에 박지 않는다. 환경변수로 덮어쓴다.
 *
 * <p>리프레시 토큰이 httpOnly 쿠키라 <b>와일드카드를 쓸 수 없다.</b> 자격 증명을 실은 요청은
 * 브라우저가 `Access-Control-Allow-Origin: *` 를 거부한다. 그래서 목록으로 받는다.
 */
@ConfigurationProperties("app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
