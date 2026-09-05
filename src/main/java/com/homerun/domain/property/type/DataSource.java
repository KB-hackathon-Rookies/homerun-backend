package com.homerun.domain.property.type;

/**
 * 매물 정보를 어디서 얻었는가. {@code property.ck_property_area_source} ·
 * {@code ck_property_house_type_source} 와 값이 같아야 한다.
 *
 * <p>자동으로 얻은 값과 사람이 적은 값을 구분하지 않으면 어느 쪽을 믿을지 알 수 없다. 건축물대장
 * 자동판별은 단독·다가구에서 실패하고(FR-P1-09), 전용면적은 실거래 매칭이 없으면 못 가져온다
 * (FR-P1-10). 둘 다 그때는 사용자가 직접 적는다.
 */
public enum DataSource {
    /** 외부 조회로 얻었다. 건축물대장 판별, 실거래 매칭. */
    AUTO,
    /** 사용자가 직접 입력했다. */
    MANUAL
}
