package com.homerun.domain.region.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "region")
public class Region {

    @Id
    private Long id;

    protected Region() {}

    public Long getId() {
        return id;
    }
}
