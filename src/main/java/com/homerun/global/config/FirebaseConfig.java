package com.homerun.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.homerun.global.external.fcm.FcmProperties;
import com.homerun.global.external.fcm.FcmSender;
import com.homerun.global.external.fcm.FirebaseFcmSender;
import com.homerun.global.external.fcm.NoOpFcmSender;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FCM 전송기 빈을 조건부로 등록한다.
 *
 * <p>{@code fcm.enabled=true} 이고 자격증명이 있으면 {@link FirebaseFcmSender}, 아니면
 * {@link NoOpFcmSender} 로 폴백한다. 덕분에 키가 없는 데모·로컬에서도 앱이 그대로 부팅된다.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "fcm", name = "enabled", havingValue = "true")
    FirebaseMessaging firebaseMessaging(FcmProperties properties) throws IOException {
        GoogleCredentials credentials = GoogleCredentials.fromStream(openCredentials(properties.credentials()));
        FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder().setCredentials(credentials);
        // 서비스계정 JSON 에 담긴 project_id 를 명시해 둔다. 없으면 messaging 이 project 를
        // 자격증명에서 재유도하지만, 로그·설정을 명확히 하려고 직접 세팅한다.
        if (credentials instanceof ServiceAccountCredentials serviceAccount && serviceAccount.getProjectId() != null) {
            optionsBuilder.setProjectId(serviceAccount.getProjectId());
        }
        FirebaseOptions options = optionsBuilder.build();
        FirebaseApp app =
                FirebaseApp.getApps().isEmpty() ? FirebaseApp.initializeApp(options) : FirebaseApp.getInstance();
        log.info("FCM 자격증명 로드 성공(project={})", app.getOptions().getProjectId());
        return FirebaseMessaging.getInstance(app);
    }

    @Bean
    @ConditionalOnProperty(prefix = "fcm", name = "enabled", havingValue = "true")
    FcmSender firebaseFcmSender(FirebaseMessaging firebaseMessaging) {
        log.info("FCM 실제 전송 활성화: FirebaseFcmSender");
        return new FirebaseFcmSender(firebaseMessaging);
    }

    @Bean
    @ConditionalOnMissingBean(FcmSender.class)
    FcmSender noOpFcmSender() {
        log.info("FCM 비활성화(fcm.enabled=false 또는 자격증명 없음): NoOpFcmSender 로 부팅");
        return new NoOpFcmSender();
    }

    /** 자격증명 문자열을 스트림으로. 원본 JSON, base64 JSON, 파일 경로를 모두 받는다. */
    private InputStream openCredentials(String credentials) throws IOException {
        if (credentials == null || credentials.isBlank()) {
            throw new IllegalStateException("fcm.enabled=true 이면 fcm.credentials 를 설정해야 합니다.");
        }
        String trimmed = credentials.strip();
        if (trimmed.startsWith("{")) {
            return new ByteArrayInputStream(trimmed.getBytes(StandardCharsets.UTF_8));
        }
        Path path = Path.of(trimmed);
        if (Files.exists(path)) {
            return Files.newInputStream(path);
        }
        byte[] decoded = Base64.getDecoder().decode(trimmed);
        return new ByteArrayInputStream(decoded);
    }
}
