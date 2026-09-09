package com.homerun.domain.terms.repository;

import com.homerun.domain.terms.entity.Term;
import com.homerun.domain.terms.type.TermScope;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TermRepository extends JpaRepository<Term, Long> {

    /**
     * 그 범위에서 지금 유효한 약관 전부. <b>선택 항목도 포함한다</b> — 화면이 선택 동의를 그리려면
     * 목록에 있어야 한다. 필수 여부는 {@code isRequired} 로 구분한다.
     */
    @Query("""
            select t from Term t
            where t.scope = :scope
              and t.effectiveFrom <= :date
              and (t.effectiveTo is null or t.effectiveTo >= :date)
            order by t.code asc
            """)
    List<Term> findActiveTerms(@Param("scope") TermScope scope, @Param("date") LocalDate date);

    /**
     * 그 범위에서 반드시 동의해야 하는 약관.
     *
     * <p>{@code SERVICE} 로 부르는 곳이 앱 전체를 막는 필터다. 기능별 범위를 여기에 섞으면 그 기능을
     * 쓰지 않는 사용자까지 막힌다.
     */
    @Query("""
            select t from Term t
            where t.scope = :scope
              and t.required = true
              and t.effectiveFrom <= :date
              and (t.effectiveTo is null or t.effectiveTo >= :date)
            order by t.code asc
            """)
    List<Term> findActiveRequiredTerms(@Param("scope") TermScope scope, @Param("date") LocalDate date);
}
