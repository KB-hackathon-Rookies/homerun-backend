# 2루 은행 사전상담 계약

## 저장 조건

- 매물의 필수 검증이 끝난 GREEN 또는 상담 이력이 있는 BLUE 상태에서만 상담 결과를 저장한다.
- 같은 매물에 여러 은행 상담을 제한 없이 저장할 수 있다.
- 가능 여부는 `POSSIBLE`, `DIFFICULT`, `DOCUMENT_REVIEW_REQUIRED`, `NOT_HEARD`로 구분한다.
- 안내 상품은 `YOUTH_BEOTIMMOK`, `GENERAL_BEOTIMMOK`, `BANK_LOAN`, `UNKNOWN`으로 구분한다.
- 담보·보증 방식은 HUG, HF, SGI, 채권양도, 기타 외에 `UNKNOWN`을 지원한다.
- 안내 한도와 금리는 듣지 못했다면 `null`로 저장한다. 모르는 값을 0으로 저장하지 않는다.

상담 결과를 하나라도 저장하면 매물 신호등은 BLUE가 된다. 다만 `NOT_HEARD`와
`DOCUMENT_REVIEW_REQUIRED`도 상담 기록으로 저장해 다음 은행 상담으로 이어갈 수 있다는 뜻이지,
대출 가능으로 확정했다는 뜻은 아니다.

## 비교와 최종 선택

`POST /api/v1/plans/{planId}/properties/compare`는 요청한 2~3개 매물과 함께 각 매물의 상담 건수,
최신 상담, 대출 가능 답변 중 최대 안내 한도를 가진 상담을 반환한다. 은행이 안내한 값은 자동 계산값과
섞지 않고 상담 결과 원문으로 노출한다.

`PUT /api/v1/plans/{planId}/properties/decision`은 `POSSIBLE` 답변만 최종 선택할 수 있다. 어려움,
서류 검토 필요, 못 들음 결과는 저장·비교할 수 있지만 최종 대출 조건으로 확정할 수 없다.
