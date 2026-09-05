# 오픈뱅킹 금융정보 → 1루 입력 동기화

## 금융결제원 원문과 현재 매핑

2026-09-05 금융결제원 개발자 문서를 기준으로 확인했다.

| 원문 API/필드 | 서비스 가공 | 계획 입력 사용 |
| --- | --- | --- |
| 잔액조회 `balance_amt` | 등록 계좌 잔액 합계 | 요약 표시만. 순자산이 아님 |
| 잔액조회 `available_amt` | 등록 계좌 출금가능액 합계 | 요약 표시만. 가용현금으로 자동 저장하지 않음 |
| 거래내역 `inout_type=입금`, `tran_type=급여`, `tran_amt` | 최근 완료 3개월의 월별 급여 합계 평균(원 단위 HALF_UP) | 모든 등록 계좌 조회 성공 + 3개월 각각 감지 시 `monthlyIncome` 제안/저장 |
| 대출목록 `loan_list` | 금융기관별 대출 계좌 중복 제거 | 대출 개수·목록 표시 |
| 대출기본 `res_list.trans_type=02`, `trans_amt` | 최근 완료 3개월 상환 합계 / 3 | 월평균 대출 상환 참고 정보. plan_input에는 아직 대응 필드가 없어 저장하지 않음 |

공식 문서:

- [잔액조회](https://developers.kftc.or.kr/dev/openapi/open-banking/balance)
- [거래내역조회](https://developers.kftc.or.kr/dev/openapi/open-banking/transaction)
- [대출계좌목록·기본정보](https://developers.kftc.or.kr/dev/openapi/open-banking/loans-list)
- [대출·리스기본정보조회](https://developers.kftc.or.kr/dev/openapi/open-banking/loans-basic)

대출목록 회차번호의 원문 응답명은 `account_num_seq`다. 기존 파서의 `account_seq`를 고치고, 기존 테스트/샌드박스 응답 호환을 위해 후자를 fallback으로만 허용한다. 대출기본 조회 요청에서는 문서대로 `account_seq`를 사용한다.

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

## 정책 판정 안전장치

오픈뱅킹 거래내역에서 얻는 값은 통장 실수령 추정액이며 은행이 심사하는 증빙 연소득과 동일하지 않다. 따라서 `incomeSource=OPEN_BANKING`이고 `financialDataConfirmed!=true`이면 소득 조건은 PASS/FAIL이 아니라 NEED_INFO다. 사용자가 값을 확인해 기존 전체 입력 PUT으로 `financialDataConfirmed=true`를 저장한 뒤에만 정책 수치 비교에 사용한다.

계좌 등록 범위가 전체 자산을 보장하지 않고 부동산·증권·보증금·부채를 모두 알 수 없으므로 다음 자동 변환은 하지 않는다.

- `totalAccountBalance → netAssets`
- `totalAvailableBalance → availableCash`
- `averageMonthlyLoanRepayment → maxMonthlyBurden`

화면에는 출금가능액·대출상환액과 coverage/warnings를 참고 정보로 표시하고 사용자가 별도로 확인·입력해야 한다. 원문 계좌번호와 토큰은 응답하거나 로그에 남기지 않는다.
