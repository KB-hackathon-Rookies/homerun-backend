package com.homerun.domain.terms.repository;

import com.homerun.domain.terms.entity.Term;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TermRepository extends JpaRepository<Term, Long> {

    @Query("""
            select t from Term t
            where t.required = true
              and t.effectiveFrom <= :date
              and (t.effectiveTo is null or t.effectiveTo >= :date)
            order by t.code asc
            """)
    List<Term> findActiveRequiredTerms(@Param("date") LocalDate date);
}
