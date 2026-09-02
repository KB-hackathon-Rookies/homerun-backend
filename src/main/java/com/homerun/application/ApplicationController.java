package com.homerun.application;

import com.homerun.application.ApplicationDtos.ApplicationList;
import com.homerun.application.ApplicationDtos.ApplicationView;
import com.homerun.application.ApplicationDtos.CreateRequest;
import com.homerun.application.ApplicationDtos.UpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신청 실행(APP-01).
 *
 * <p>planId 를 파라미터로 받는다. 인증 필터가 들어오면 세션에서 꺼내고 소유권을 검증한다.
 */
@RestController
@RequestMapping("/api/v1/applications")
@Tag(name = "신청 실행", description = "신청 건 생성과 진행 상태·결과 기록")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "신청 목록 조회")
    public ApplicationList list(@RequestParam Long planId) {
        return service.list(planId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "신청 건 생성", description = "같은 계획에서 같은 정책을 두 번 신청할 수 없다.")
    public ApplicationView create(@Valid @RequestBody CreateRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "진행 상태·결과 기록",
            description = "거절이면 은행·보증기관·서류·상품 중 어디서 막혔는지를 함께 받는다. 단계에 맞는 다음 행동을 nextAction 으로 돌려준다.")
    public ApplicationView update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
        return service.update(id, request);
    }
}
