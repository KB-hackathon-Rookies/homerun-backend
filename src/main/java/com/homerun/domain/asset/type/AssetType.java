package com.homerun.domain.asset.type;

/**
 * {@code asset_option.asset_type}. 지금은 세금·순위 손실이 문서로 확인된 두 종류만 다룬다.
 *
 * <p>청년미래적금(AST-01-06)은 범위에서 뺐다(#175). 손실 안내 팩트(FCT-083·084)는 레지스트리에
 * 남아 있으므로 되살릴 때 다시 쓸 수 있다.
 */
public enum AssetType {
    IRP,
    HOUSING_SUBSCRIPTION
}
