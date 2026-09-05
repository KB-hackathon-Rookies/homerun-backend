# 코퍼스(RAG 원문)

이 폴더의 문서를 인제스트해 AI 코치가 근거로 삼는다. **실제 문서는 나중에 여기에 넣는다.**
지금은 구조 검증용 샘플 1건만 있다.

## 넣는 방법

1. `.md` / `.txt` / `.pdf` 파일을 이 폴더(하위 폴더 포함)에 둔다.
2. 각 문서 맨 앞에 front matter 로 메타데이터를 준다(선택이지만 권장):

   ```
   ---
   title: 문서 제목
   stage: FIRST        # BENCH / FIRST / SECOND / THIRD / HOME 중 하나, 단계 무관이면 ALL
   source: 출처(기관·문서명 등)
   ---
   본문...
   ```

   - `stage` 가 특정 단계면 그 단계 사용자에게만 검색된다.
   - 단계와 무관한 공통 지식은 `stage: ALL` 로 둔다(모든 단계 검색에 포함).
   - front matter 가 없으면 stage=ALL, title=파일명 으로 처리한다.

3. 인제스트 실행:

   ```bash
   python -m scripts.ingest --path data/corpus
   ```

같은 파일을 다시 인제스트하면 덮어쓴다(idempotent).
