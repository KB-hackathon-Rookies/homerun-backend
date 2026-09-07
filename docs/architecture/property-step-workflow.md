# 2루 매물 STEP 저장과 카드 계약

## 확정한 흐름

매물은 계획마다 최대 5개까지 등록하고 비교 요청은 한 번에 2~3개만 받는다. 목록 기본 정렬은
`BLUE → GREEN → YELLOW → RED`다. 매물 사이트 연동은 하지 않으며 주소 검색 결과를 사용자가 선택한다.

비교 후보는 `DELETE /api/v1/plans/{planId}/properties/{propertyId}`로 삭제한다. 최종 선택이나
계약에 사용한 매물은 이력을 보호하기 위해 삭제하지 못하며, 아직 후보인 매물의 검증·상담·판정
데이터는 같은 트랜잭션에서 함께 삭제한다.

`POST /api/v1/plans/{planId}/properties/analysis`는 주소와 보증금을 받아 매물을 만들고 건축물대장·실거래가를
조회한다. 응답의 `workflow`에 다음으로 저장할 STEP과 revision이 포함된다.

| STEP | API | 저장 내용 |
| --- | --- | --- |
| 2 | `PUT /properties/{propertyId}/steps/building` | 자동조회 실패 시 주택유형·전용면적 수동 입력 |
| 3 | `PUT /properties/{propertyId}/steps/violation` | 정부24에서 직접 확인한 위반건축물 여부 |
| 4 | `PUT /properties/{propertyId}/steps/registry` | 등기부 질문, 선순위채권, 공시가격·기준연도·출처 |

각 저장은 `expectedRevision`을 검사한다. 다른 탭이나 요청이 먼저 저장했다면 `PRP_009`를 반환해 최신
`GET /properties/{propertyId}/resume` 결과를 다시 읽게 한다. RED가 된 매물은 이후 STEP으로 되돌리지 않는다.

## 외부 조회 경계

- 건축물대장 API는 주용도와 다가구 가능성을 확인하는 데 쓴다.
- 공개 표제부 응답의 위반건축물 필드를 신뢰하지 않는다. STEP 3 수기 확인 전에는 항상 미확인이다.
- 실거래 매칭에 성공하면 전용면적 출처는 `AUTO`, 실패 후 입력하면 `MANUAL`이다.
- 공시가격은 금액만 저장하지 않는다. 기준연도와 `REALTY_PRICE_APARTMENT`,
  `REALTY_PRICE_DETACHED`, `HOMETAX_STANDARD_VALUE` 중 출처를 함께 저장한다.

## 상태와 카드

`GET /properties/{propertyId}/card`는 매물, STEP, 신호등, 건물·등기 검증, 공시가격, 상품 판정과 상담
목록을 한 번에 반환한다. 필수 건물·등기 확인이 끝나 GREEN 또는 BLUE가 되기 전에는 상품을 카드에
노출하지 않고 상품 판정 API도 `PRP_011`로 막는다. STEP 4 저장 결과가 GREEN이면 1루 입력을
사용해 매물별 상품 판정을 자동 실행한다.

| 상태 | 의미 |
| --- | --- |
| `IN_PROGRESS` | 다음 STEP 입력이 남음 |
| `NEEDS_CONFIRMATION` | STEP 4의 모름 값 때문에 YELLOW 유지 |
| `READY_FOR_CONSULTATION` | 필수 확인을 통과해 GREEN |
| `CONSULTED` | 상담 기록이 있어 BLUE |
| `BLOCKED` | 위반건축물·근생·등기 위험으로 RED |

대출 가능성과 보증금 안전성은 합치지 않는다. 공시가격 126% 또는 담보인정비율에서 막혀도 그것만으로
매물 신호등을 RED로 바꾸지 않으며 카드의 검증 결과에서 별도로 보여준다.
