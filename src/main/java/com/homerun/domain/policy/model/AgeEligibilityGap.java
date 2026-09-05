package com.homerun.domain.policy.model;

import java.time.LocalDate;

/**
 * 나이 하한을 아직 못 채운 조건이 채워지는 예정일(ALT-01-04 재도전 큐).
 *
 * @param eligibleFrom 이 날짜부터 나이 조건을 충족한다
 * @param label 화면에 보여줄 조건 라벨(기준이 된 fact의 item)
 * @param sourceUrl 기준 fact의 원문 링크
 */
public record AgeEligibilityGap(LocalDate eligibleFrom, String label, String sourceUrl) {}
