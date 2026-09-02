package com.homerun.global.security;

import com.homerun.domain.terms.TermsService;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.response.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
public class RequiredTermsAgreementFilter extends OncePerRequestFilter {

    private final TermsService termsService;
    private final ObjectMapper objectMapper;

    public RequiredTermsAgreementFilter(TermsService termsService, ObjectMapper objectMapper) {
        this.termsService = termsService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof MemberPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!termsService.hasAgreedAllRequired(principal.memberId())) {
            ErrorCode errorCode = ErrorCode.REQUIRED_TERMS_AGREEMENT_REQUIRED;
            response.setStatus(errorCode.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(errorCode));
            return;
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/")
                || path.equals("/api/v1/terms")
                || path.startsWith("/api/v1/agreements")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs/")
                || path.startsWith("/actuator/health");
    }
}
