package com.homerun.domain.notification.controller;

import com.homerun.domain.notification.dto.request.RegisterDeviceTokenRequest;
import com.homerun.domain.notification.dto.response.NotificationResponse;
import com.homerun.domain.notification.service.DeviceTokenService;
import com.homerun.domain.notification.service.NotificationQueryService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "알림", description = "FCM 디바이스 토큰 등록과 인박스 조회")
public class NotificationController {

    private final DeviceTokenService deviceTokenService;
    private final NotificationQueryService notificationQueryService;

    public NotificationController(
            DeviceTokenService deviceTokenService, NotificationQueryService notificationQueryService) {
        this.deviceTokenService = deviceTokenService;
        this.notificationQueryService = notificationQueryService;
    }

    @PostMapping("/tokens")
    @Operation(summary = "디바이스 FCM 토큰 등록/갱신")
    public ResponseEntity<ApiResponse<Void>> registerToken(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody RegisterDeviceTokenRequest request) {
        deviceTokenService.register(principal.memberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    @GetMapping
    @Operation(summary = "내 알림 목록 조회")
    public ApiResponse<List<NotificationResponse>> list(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(notificationQueryService.list(principal.memberId(), unreadOnly, limit));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "알림 읽음 처리")
    public ApiResponse<NotificationResponse> markRead(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long notificationId) {
        return ApiResponse.success(notificationQueryService.markRead(principal.memberId(), notificationId));
    }
}
