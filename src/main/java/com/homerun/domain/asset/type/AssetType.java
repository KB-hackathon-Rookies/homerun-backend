package com.homerun.domain.asset.type;

/** {@code asset_option.asset_type}. 지금은 세금·순위·기여금 손실이 문서로 확인된 세 종류만 다룬다. */
public enum AssetType {
    IRP,
    HOUSING_SUBSCRIPTION,
    /** 청년미래적금(AST-01-06). 일반 중도해지 시 정부기여금·비과세를 잃는다(FCT-083). */
    YOUTH_SAVINGS
}
