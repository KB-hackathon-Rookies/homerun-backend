package com.homerun.domain.region.entity;

import com.homerun.domain.region.type.PolicyArea;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "region")
public class Region {

    @Id
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_area", length = 20)
    private PolicyArea policyArea;

    protected Region() {}

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Long getParentId() {
        return parentId;
    }

    public PolicyArea getPolicyArea() {
        return policyArea;
    }
}
