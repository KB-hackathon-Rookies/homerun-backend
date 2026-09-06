# 1루 확정 플로우와 구현 경계

최종 결정 기준: 2026-09-05 사용자 확인. 취소선과 이전 논의의 미해결 체크리스트는 요구사항으로 사용하지 않는다.

## 확정 결정

- 혼인·배우자 소득·신혼부부 특례를 1루 정책 매칭 전제로 추가하지 않는다. 본인 소득 기준의 MVP다.
- `maritalStatus`는 기존 API/다른 정책과의 호환성을 위해 보존하되 전세 진단 완료 필수 입력에서 제외한다.
- 일반 버팀목을 숨기는 것은 기획 예시에서만 적용한다. 실제 정책 결과에서 상품별 숨김 필터를 만들지 않는다.
- 자기자금이 부족하다는 이유로 매칭 상품을 삭제하지 않는다. 필요 자기자금과 부족액을 구분해 보여준다.
- 페르소나는 재직 1년 이상인 사례를 뜻한다. 사용자 이름, 이메일, 식별자 등으로 한도를 우회하는 예외는 없다.
- 기업 규모와 재직기간은 정규직·계약직·일용직·인턴에만 질문한다. 프리랜서·무직에는 요구하지 않는다.
- 재직 1년 미만을 입력하지 못하게 차단하지 않는다. 입력 완료와 대출 가능 여부는 서로 다른 판단이다.
- 국민은행 카드는 항상 제공하는 상담 안내다. 승인, 확정 금리 또는 확정 한도를 만들어 넣지 않는다.
- 예시의 서울시 금리 2.10% 등은 운영 계산 기본값으로 사용하지 않는다.

## 이번에 반영한 API 계약

### 입력 저장과 완료

`PUT /api/v1/plans/{planId}/input`은 기존 전체 스냅샷 자동저장 API를 그대로 쓴다.
`FIRST_DIAGNOSIS` 완료 검증 중 `leaseType=JEONSE`에만 다음 기준을 적용한다.

| 구분 | 필드 |
| --- | --- |
| 공통 필수 값 또는 명시적 모름 | `hopeDeposit`, `regionId`, `isHomeless`, `householderStatus`, `employmentType`, `monthlyIncome`, `netAssets`, `availableCash` |
| 급여근로자만 추가 | `companySize`, `employmentMonths` |
| 해당 화면에서 필수가 아님 | `maritalStatus`, `monthlyRent`, `maintenanceFee`, `currentDeposit`, `maxMonthlyBurden`, `areaM2`, `houseType` |

모름을 허용하는 기존 COM-05 계약은 유지한다. 명시적 모름은 추가 확인이며 PASS로 간주하지 않는다.
기존 WOLSE/BANJEONSE의 완료 검증은 변경하지 않았다.

### STEP 저장과 최종 제출

`PUT /api/v1/plans/{planId}/input/steps/{stepCode}`는 다음 버튼을 누를 때 현재 STEP만 병합 저장한다.
`GET /api/v1/plans/{planId}/input/resume`는 저장된 입력과 다음 미완료 STEP을 반환한다.

최종 확인 후 `POST /api/v1/plans/{planId}/first-base/complete`를 호출한다. 이 API는 입력 revision을
검증한 뒤 대출 적용 전 DIA-02 기준 결과 저장, 서버 정책 판정, 정책별 최소·최대 금리 시나리오 생성,
`FIRST_DIAGNOSIS` 완료, 2루 잠금 해제를 하나의 트랜잭션으로 처리한다. 최종 제출 요청은 비용과 월
생활비만 받고 예상 대출액·월 이자는 받지 않는다.
동일 revision 재요청은 최초 진단 결과를 반환하고 중복 저장하지 않는다. 필수 판단 항목을 `unknownFields`로
저장한 경우에는 계산값을 지어내지 않고 `NEEDS_CONFIRMATION`과 확인할 필드 목록을 반환하며 1루를 완료하지 않는다.

완료 후 `GET /api/v1/plans/{planId}/first-base/result`로 기준 진단, 정책 카드, 정책별 시나리오와 현재
계획 진행 상태를 한 번에 복원한다. 정책 판정과 시나리오는 완료 당시 스냅샷을 사용하며 GET 요청에서
재판정하거나 새 진단을 저장하지 않는다.

### 결과 카드

`POST /api/v1/plans/{planId}/policies/jeonse/evaluate`의 기존 `results`는 실패·추가 확인을 포함한 전체 판정을 보존한다.
추가한 `cards`는 청년 버팀목 → 일반 버팀목 → 서울시 이자지원 → 국민은행 상담 순서다.

- `POLICY`: PASS와 NEED_INFO를 표시한다. NEED_INFO는 매칭 성공과 구분해 추가 확인 상태로 그린다.
- `CONSULTATION`: `verdict`, `estimate`가 null이다. 은행이 자동 승인한 상품으로 표시하면 안 된다.
- FAIL은 카드 목록에서 제외하지만 `results`의 탈락 사유는 그대로 제공한다.
- `availableCash`는 사용자가 입력한 사용 가능 현금만 사용한다. `netAssets`나 계좌 잔액으로 대체하지 않는다.
- `ownFundsShortfall = max(0, ownFundsRequired - availableCash)`. 필요한 값이 없으면 0이 아니라 null이다.
- 반환보증·보증료 지원 응답은 기존 판정을 유지하며 `cards=[]`다.

## 정책 정합성 경계

`V66`은 공식 원문을 다시 확인해 충돌 팩트를 정리하고 운영 규칙의 안전장치를 보강했다.

1. 본인 무주택과 세대원 전원 무주택을 혼동하지 않는다. `household_homeless`가 없으면 계속 추가 확인이며 본인 `isHomeless`로 대체하지 않는다.
2. `hasExistingJeonseLoan=true`는 불충족이다. false여도 성년 세대원의 기금대출과 차주·배우자의 주택담보대출을 확인한 것이 아니므로 `NO_DUPLICATE_LOAN`은 은행 확인 전까지 `NEED_INFO`다.
3. 주택유형은 아파트·오피스텔·빌라 3종 화이트리스트로 제한하지 않는다. 공식 기준은 주택과 주거용 오피스텔이므로 비주거용 여부와 면적을 각각 확인한다.
4. 회원 생년월일·병역은 [진단용 회원정보와 초깃값 API](plan-profile-prefill.md)로 연결했다. 오픈뱅킹 소득은 [안전한 금융정보 동기화](open-banking-plan-sync.md)로 연결했으며 미확인 외부값은 정책 판정에 쓰지 않는다. 계좌 잔액·확정 가능한 소득·대출상환액은 금융 스냅샷에 보관하지만 순자산·가용현금·월 부담 상한으로 자동 변환하지 않는다. 실수령 추정액은 은행의 증빙 연소득과 같지 않다.
5. 재직 1년 미만의 2천만원 한도 가능성, 서울시 기준금리와 지원금리의 적용 시점은 여전히 은행·기관 확인 영역이다. 기존 숫자나 예시를 복사해 확정하지 않는다.
6. 유주택·무직 등 서비스 준비 중 안내는 실제 대출 불가 판정과 분리해야 한다. 입력 완료 검증에서 이를 대출 탈락으로 처리하지 않았다.

청년·일반 버팀목은 안전장치를 반영한 version 11을 ACTIVE로 사용한다. 서울시 이자지원은 기존 검수된 version 3을 유지한다.

공식 확인 경로:

- [청년전용 버팀목](https://nhuf.molit.go.kr/FP/FP05/FP0502/FP05020301.jsp)
- [일반 버팀목](https://nhuf.molit.go.kr/FP/FP05/FP0502/FP05020101.jsp)
- [서울시 청년 임차보증금](https://housing.seoul.go.kr/site/main/content/sh01_040901)

초기 조회에서는 주택도시기금 원문이 시간초과였으나, 지역별 규칙 후속 작업에서 원문을 확인했다. 확인한 수치와 아직 검수가 필요한 조건은 위 지역별 규칙 문서에 분리했다.
