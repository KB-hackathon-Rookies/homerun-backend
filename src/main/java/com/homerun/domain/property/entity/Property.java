package com.homerun.domain.property.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 매물 한 건.
 *
 * <p>지금은 소유 계획을 확인하는 용도로만 쓴다. 그래서 컬럼을 다 매핑하지 않았고 만드는
 * 생성자도 두지 않았다. 이 엔티티로 저장하면 나머지 컬럼이 조용히 비워지므로, 쓰기가
 * 필요해지면 그때 나머지를 채워서 열어야 한다.
 */
@Entity
@Table(name = "property")
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    protected Property() {}

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }
}
