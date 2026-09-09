# 오픈뱅킹 금융정보 → 1루 입력 동기화

## 금융결제원 원문과 현재 매핑

2026-09-05 금융결제원 개발자 문서를 기준으로 확인했다.

| 원문 API/필드 | 서비스 가공 | 계획 입력 사용 |
| --- | --- | --- |
| 잔액조회 `balance_amt` | 등록 계좌 잔액 합계 | 조회가 모두 성공하면 금융 스냅샷에 저장. 순자산이 아님 |
| 잔액조회 `available_amt` | 등록 계좌 출금가능액 합계 | 요약 표시만. 가용현금으로 자동 저장하지 않음 |
| 거래내역 `inout_type=입금`, `tran_type=급여`, `tran_amt` | 최근 완료 3개월의 월별 급여 합계 평균(원 단위 HALF_UP) | 모든 등록 계좌 조회 성공 + 3개월 각각 감지 시 `monthlyIncome` 제안/저장 |
| 대출목록 `loan_list` | 금융기관별 대출 계좌 중복 제거 | 대출 개수·목록 표시 |
| 대출기본 `res_list.trans_type=02`, `trans_amt` | 최근 완료 3개월 상환 합계 / 3 | 대출목록·상환상세가 모두 성공하면 금융 스냅샷에 저장. plan_input에는 저장하지 않음 |

공식 문서:

- [잔액조회](https://developers.kftc.or.kr/dev/openapi/open-banking/balance)
- [거래내역조회](https://developers.kftc.or.kr/dev/openapi/open-banking/transaction)
- [대출계좌목록·기본정보](https://developers.kftc.or.kr/dev/openapi/open-banking/loans-list)
- [대출·리스기본정보조회](https://developers.kftc.or.kr/dev/openapi/open-banking/loans-basic)

대출목록 회차번호의 원문 응답명은 `account_num_seq`다. 기존 파서의 `account_seq`를 고치고, 기존 테스트/샌드박스 응답 호환을 위해 후자를 fallback으로만 허용한다. 대출기본 조회 요청에서는 문서대로 `account_seq`를 사용한다.

## 사업자 등록 전 데이터 모킹

위 조회 API(계좌 목록·잔액·거래내역·대출)는 사업자 등록을 마친 이용기관만 호출할 수 있다. 등록 전에는 `external-api.open-banking.mock-data=true`(env `OPEN_BANKING_MOCK_DATA`)로 켜서 업스트림 경계인 `OpenBankingClient` 구현을 `MockDataOpenBankingClient`로 바꾼다. 기본값은 false 이고 플래그가 꺼져 있으면 모킹 구현은 컨텍스트에 아예 올라오지 않으므로, 운영·CI 는 그대로 실제 업스트림을 호출한다.

- 인가는 모킹하지 않는다. `authorizationUri` / `exchangeAuthorizationCode` / `refreshToken` 은 실제 구현으로 위임하므로 데모에서도 진짜 금융결제원 인증 화면을 쓴다.
- 연결(`open_banking_connection`)이 없는 회원은 모킹이 켜져 있어도 지금과 똑같이 `OPEN_BANKING_NOT_CONNECTED`로 실패한다. 계좌 소유권 검증도 그대로다.
- 픽스처는 사회초년생 한 명의 일관된 프로필이다. 급여통장에 매달 280만원 급여가 들어오고 적금 80만·청약 10만·월세·카드가 빠져나가 잔액이 320만원에서 유지된다. 세 계좌 잔액 합계는 4,000만원, 전세자금대출 상환은 매달 15만원이다. 계좌 별칭에 `샘플`이 들어가고 예금주는 홍길동, 계좌번호는 마스킹되어 있어 화면에서 실데이터와 구분된다.

### 데모 전용 페르소나 적재 경로

`POST /api/v1/plans/{planId}/input/open-banking-sync/mock`은 **같은 플래그에 묶인 데모 전용 경로**다. `MockOpenBankingPlanSyncController` 에 `@ConditionalOnProperty(prefix = "external-api.open-banking", name = "mock-data", havingValue = "true")` 가 걸려 있어, 기본값(false)에서는 빈이 올라오지 않고 경로도 없다(404).

이 경로만 조건부인 이유는 적재 방식 때문이다. 위 동기화와 달리 선택한 페르소나의 소득·순자산을 `financialDataConfirmed=true`, 즉 **확인된 값으로** 계획 입력에 넣는다. 확인된 값은 아래 "정책 판정 안전장치"의 NEED_INFO 로 빠지지 않고 곧바로 PASS/FAIL 비교에 들어간다. 열려 있으면 오픈뱅킹 연결도 실제 거래내역도 없이 판정 근거를 지어낼 수 있다.

진짜 동기화(`POST .../open-banking-sync`)는 `OpenBankingPlanSyncController` 에 그대로 남아 플래그와 무관하게 모든 환경에 있다. 조건은 클래스 단위로만 걸 수 있어 둘을 한 클래스에 두면 가짜를 끄는 순간 진짜까지 꺼지므로, 컨트롤러를 나누되 **URL 은 바꾸지 않았다.**

## 동기화 API

인증 후 `POST /api/v1/plans/{planId}/input/open-banking-sync`를 호출한다. 필요한 경우 `bankCodes=004,020`처럼 추가 대출조회 금융기관 코드를 전달한다.

처리 순서:

1. 외부 API를 호출하기 전에 계획 소유권을 확인한다.
2. 기존 토큰 갱신 로직을 거쳐 연결 계좌, 잔액, 최근 완료 3개월 거래, 대출·상환내역을 조회한다.
3. 모든 등록 계좌의 거래 조회가 성공하고 세 달 모두 급여 거래가 있을 때만 평균 월 실수령 추정액을 plan_input.monthly_income에 저장한다.
4. 저장 출처는 `OPEN_BANKING`, 확인 상태는 `false`다. 기존 명시적 모름은 해당 필드에 한해 해제한다.
5. 입력이 바뀌면 이전 revision을 이력에 저장하고 FIRST_DIAGNOSIS 이후 단계를 재계산 필요 상태로 바꾼다.
6. 같은 값의 재동기화는 revision을 올리지 않는다. `MANUAL` 소득이나 이미 확인된 금융값은 자동으로 덮어쓰지 않는다.

`monthlyIncomeSyncStatus`는 `APPLIED`, `UNCHANGED`, `MANUAL_VALUE_PRESERVED`, `CONFIRMED_VALUE_PRESERVED`, `NOT_APPLICABLE` 중 하나다. 연결 실패는 기존 오픈뱅킹 오류 응답을 사용하고, 일부 조회 실패는 summary coverage/warnings와 함께 `NOT_APPLICABLE`로 반환한다.

## 금융정보 스냅샷

계획 입력 동기화가 외부 요약을 받은 뒤 기존 `financial_snapshot` 테이블에 해당 시점의 안전한 값만 별도 저장한다. 동기화 응답의 `snapshot`으로 바로 확인할 수 있고, 이후에는 `GET /api/v1/open-banking/financial-snapshots/latest`로 외부 API를 다시 호출하지 않고 최근 값을 조회한다.

- `financialAsset`: 등록 계좌 잔액 조회가 전부 성공하고 계좌가 하나 이상일 때만 저장한다. 전체 순자산을 뜻하지 않는다.
- `monthlyIncome`: 계획 입력과 같은 기준으로, 모든 등록 계좌의 최근 완료 3개월 거래 조회가 성공하고 매월 급여가 감지될 때만 저장한다.
- `monthlyDebtPayment`: 조회 대상 금융기관의 대출목록과 모든 대출 상환상세가 성공할 때만 저장한다.
- `monthlyExpense`, `loanBalance`: 현재 금융결제원 응답만으로 확정할 수 없으므로 `null`이다.
- 신뢰 가능한 값이 하나도 없으면 빈 스냅샷을 저장하지 않는다. 스냅샷은 원문 계좌번호나 토큰을 보관하지 않는다.

스냅샷은 외부 데이터를 수집한 기록이므로 `confirmedByUser=false`로 저장한다. 이 플래그는 스냅샷 전체 확인 상태이며, 현재의 소득 확인 API가 다른 금융값까지 확인한 것으로 만들지는 않는다.

## 확인 또는 수동 교체

동기화 후 `PUT /api/v1/plans/{planId}/input/financial-income`을 호출한다.

- 그대로 확인: `{"action":"CONFIRM_OPEN_BANKING"}`. 요청 금액을 받지 않고 서버에 저장된 동기화 금액만 확정한다.
- 직접 수정: `{"action":"USE_MANUAL","monthlyIncome":3100000}`. 소득 출처를 `MANUAL`로 바꾸며, 다른 외부 자산까지 확인한 것으로 처리하지 않는다.

소유한 계획의 저장된 입력만 변경할 수 있다. 확인/수정 전 revision을 이력에 남기고 이후 단계를 재계산 대상으로 만든다. 같은 확인 또는 같은 수동값의 반복 요청은 멱등하며 revision을 추가하지 않는다.

기존 전체 입력 PUT에서 `OPEN_BANKING`을 보내는 경우에도 서버에 이미 동기화된 동일 금액만 허용한다. 클라이언트가 임의의 월소득을 외부 출처로 표시하거나, 기존과 다른 외부 자산값을 확인 상태로 제출하면 `PLAN_017`로 거부한다. 기존에 저장된 동일 외부값의 재저장은 호환성을 위해 유지한다.

## 정책 판정 안전장치

오픈뱅킹 거래내역에서 얻는 값은 통장 실수령 추정액이며 은행이 심사하는 증빙 연소득과 동일하지 않다. 따라서 `incomeSource=OPEN_BANKING`이고 `financialDataConfirmed!=true`이면 소득 조건은 PASS/FAIL이 아니라 NEED_INFO다. 전용 확인 API 또는 검증된 기존 전체 입력 PUT으로 확인한 뒤에만 정책 수치 비교에 사용한다.

계좌 등록 범위가 전체 자산을 보장하지 않고 부동산·증권·보증금·부채를 모두 알 수 없으므로 다음 자동 변환은 하지 않는다.

- `totalAccountBalance → netAssets`
- `totalAvailableBalance → availableCash`
- `averageMonthlyLoanRepayment → maxMonthlyBurden`

화면에는 출금가능액·대출상환액과 coverage/warnings를 참고 정보로 표시하고 사용자가 별도로 확인·입력해야 한다. 금융 스냅샷에 보관하더라도 이를 `netAssets`, `availableCash`, `maxMonthlyBurden`으로 자동 변환하지 않는다. 원문 계좌번호와 토큰은 응답하거나 로그에 남기지 않는다.
