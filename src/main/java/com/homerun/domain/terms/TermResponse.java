package com.homerun.domain.terms;

import java.time.LocalDate;

public record TermResponse(
        String code, String version, String title, boolean required, String contentUrl, LocalDate effectiveFrom) {

    public static TermResponse from(Term term) {
        return new TermResponse(
                term.getCode(),
                term.getVersion(),
                term.getTitle(),
                term.isRequired(),
                term.getContentUrl(),
                term.getEffectiveFrom());
    }
}
