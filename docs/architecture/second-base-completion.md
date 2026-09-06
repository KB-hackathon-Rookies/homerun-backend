# 2루 완료 처리 계약

## API

- `POST /api/v1/plans/{planId}/second-base/complete`
- `GET /api/v1/plans/{planId}/second-base/result`

완료 요청은 `expectedDecisionRevision`과 `ruleVersion`을 받는다. 최종 매물이나 상담 결과가 다른
요청에서 바뀌었다면 `PRP_014`로 오래된 완료 요청을 거절한다.

## 완료 조건

2루 완료에는 다음 조건이 모두 필요하다.

1. 계획에 최종 매물과 그 매물의 상담 결과가 함께 선택돼 있다.
2. 상담 답변이 `POSSIBLE`이다.
3. 상품 종류와 담보·보증 방식이 `UNKNOWN`이 아니다.
4. 은행이 안내한 한도와 금리가 저장돼 있다.
5. `SECOND_POLICY_SELECTION` 관문이 READY 또는 DOING 상태다.

`못 들었어요` 값은 상담 저장과 비교를 막지 않지만, 최종 대출 조건을 확정할 수는 없다. 완료 조건이
부족하면 `PRP_015`를 반환하고 계획과 관문은 변경하지 않는다.

## 멱등성과 단계 이동

최종 선택이 실제로 바뀔 때만 `decisionRevision`이 증가한다. 같은 선택을 다시 저장해도 revision과
선택 시각은 바뀌지 않는다.

같은 revision의 완료 요청을 다시 보내면 `second_base_submission`에 저장한 결과 스냅샷을 반환하며
관문을 다시 완료하거나 제출 행을 중복 생성하지 않는다. 최초 완료 시에는 2루 관문을 DONE으로 만들고
3루 관문을 READY로 해제하며 계획의 현재 단계는 THIRD가 된다. 완료 화면 복원을 위해 마지막 방문 위치는
SECOND의 `SECOND_BASE_RESULT`로 남긴다.
