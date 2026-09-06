# 코퍼스(RAG 원문)

이 폴더의 검수된 문서를 인제스트해 AI 코치가 근거로 삼는다. 개인정보나 사용자별 진단
결과는 코퍼스에 넣지 않는다. 개인화 값은 질문 시 `context`로 전달하고, 정확한 판정·금액은
Spring 정책 엔진의 계산 결과를 사용한다.

## 넣는 방법

1. `.md` / `.txt` / `.pdf` 파일을 이 폴더(하위 폴더 포함)에 둔다.
2. 각 문서 맨 앞에 front matter 로 메타데이터를 준다(선택이지만 권장):

   ```
   ---
   title: 문서 제목
   stage: FIRST        # BENCH / FIRST / SECOND / THIRD / HOME 중 하나, 단계 무관이면 ALL
   source: 출처(기관·문서명 등)
   source_url: https://원문주소
   topic: loan_eligibility
   product_code: JEONSE-YOUTH-BEOTIMMOK
   fact_codes: FCT-003,FCT-008
   verified_at: 2026-09-07
   ---
   본문...
   ```

   - `stage` 가 특정 단계면 그 단계 사용자에게만 검색된다.
   - 단계와 무관한 공통 지식은 `stage: ALL` 로 둔다(모든 단계 검색에 포함).
   - 정책 수치는 `fact_codes`로 `config_effective`의 근거와 연결한다.
   - 외부 정책 문서는 확인일을 `verified_at`에 적고, 원문 링크를 `source_url`에 넣는다.
   - `CONFLICT`, `UNKNOWN` 팩트와 검수 전 수치는 확정 사실처럼 작성하지 않는다.
   - front matter 가 없으면 stage=ALL, title=파일명 으로 처리한다.

3. 인제스트 실행:

   ```bash
   python -m scripts.ingest --path data/corpus --reset
   ```

같은 파일을 다시 인제스트하면 그 파일의 이전 청크를 먼저 지우고 새 청크로 교체한다. `--reset`은
삭제된 파일의 과거 청크까지 없애므로 배포용 전체 갱신에서 사용한다.
