# HomeRun AI 코치 (RAG)

Spring 백엔드(8080)와 분리된 독립 FastAPI 서비스(8000). 정책·계약 문서를 근거로 답하는
stage-aware RAG 코치다. Spring 이 발급한 JWT(HS256)를 **같은 시크릿으로 검증**하므로 프론트가
8000 으로 직접 호출한다.

## 구조

```
ai-coach/
  app/
    main.py              FastAPI 진입점(CORS, 라우터)
    core/config.py       환경설정(OpenAI 키, JWT_SECRET, Chroma)
    core/security.py     Spring JWT(HS256) 검증 → member id
    schemas/coach.py     AskRequest / AskResponse / Source / Stage
    routers/coach.py     POST /coach/ask
    routers/health.py    GET /health
    services/
      embeddings.py      OpenAI 임베딩
      vectorstore.py     ChromaDB 접근, stage IN {현재, ALL} 필터 검색
      rag.py             검색 → 프롬프트 → gpt-4o-mini → 답변 + 근거
  scripts/ingest.py      코퍼스 → 청킹 → 임베딩 → Chroma upsert (CLI)
  data/corpus/           RAG 원문(실문서는 나중에 주입, 지금은 샘플 1건)
  data/chroma/           persistent 벡터 저장(gitignore)
```

## 핵심 설계

- **근거(sources)는 LLM 이 아니라 retriever 결과로 만든다.** LLM 은 답변 텍스트만 생성하고,
  출처 목록은 검색 히트의 메타데이터(title/source/snippet)로 코드가 직접 구성한다(출처 환각 방지).
- **stage 검색은 현재 stage + ALL.** 문서를 `stage` 로 태깅하고, 검색은 `stage ∈ {현재 단계, ALL}`
  로 필터한다. 단계 전용 지식과 공통 지식을 함께 후보로 삼는다.

## 로컬 실행

```bash
cd ai-coach
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env   # OPENAI_API_KEY, JWT_SECRET(백엔드와 동일) 채우기

# 코퍼스 인제스트(샘플 포함)
python -m scripts.ingest --path data/corpus

# 서버 기동
uvicorn app.main:app --reload --port 8000
```

## 호출 예시

```bash
curl -X POST http://localhost:8000/coach/ask \
  -H "Authorization: Bearer <Spring이 발급한 accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"question":"전세자금대출 자격이 뭐예요?","stage":"FIRST"}'
```

응답:

```json
{
  "answer": "…근거 기반 답변…",
  "stage": "FIRST",
  "sources": [
    {"title": "청년 전세자금대출 기본 개념(샘플)", "source": "홈런 샘플 문서", "snippet": "…"}
  ]
}
```

## Docker

루트의 `compose.prod.yaml` 에 `ai-coach` 서비스로 포함되어 있다.

```bash
docker compose -f compose.prod.yaml up -d --build ai-coach
```

컨테이너 기동 후 인제스트는 컨테이너 안에서 한 번 실행한다:

```bash
docker compose -f compose.prod.yaml exec ai-coach python -m scripts.ingest --path data/corpus
```
