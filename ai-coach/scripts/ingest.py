"""코퍼스 인제스트 CLI.

사용법:
    python -m scripts.ingest --path data/corpus

.md / .txt / .pdf 를 훑어 청킹 → OpenAI 임베딩 → ChromaDB upsert 한다.
각 문서 앞에 다음과 같은 front matter 로 메타데이터를 줄 수 있다(선택):

    ---
    title: 청년 전세자금대출 개요
    stage: FIRST        # BENCH/FIRST/SECOND/THIRD/HOME 중 하나, 또는 단계 무관이면 ALL
    source: 주택도시기금 안내
    source_url: https://example.com
    topic: loan_eligibility
    fact_codes: FCT-003,FCT-008
    verified_at: 2026-09-07
    ---

없으면 stage=ALL, title=파일명, source=파일명 으로 처리한다.
같은 파일을 다시 인제스트하면 청크 id 가 같아 덮어써진다(idempotent).
"""

import argparse
from pathlib import Path

STAGE_ALL = "ALL"
VALID_STAGES = {"BENCH", "FIRST", "SECOND", "THIRD", "HOME", STAGE_ALL}
OPTIONAL_METADATA = {
    "source_url",
    "topic",
    "product_code",
    "fact_codes",
    "verified_at",
    "effective_from",
    "effective_to",
}


def parse_front_matter(text: str, fallback_title: str) -> tuple[dict, str]:
    """--- ... --- front matter 를 떼어내 (메타, 본문) 을 돌려준다."""
    meta = {"title": fallback_title, "stage": STAGE_ALL, "source": fallback_title}
    if not text.startswith("---"):
        return meta, text
    end = text.find("\n---", 3)
    if end == -1:
        return meta, text
    header = text[3:end].strip()
    body = text[end + 4 :].lstrip("\n")
    for line in header.splitlines():
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        key, value = key.strip().lower(), value.strip()
        if key in {"title", "stage", "source", *OPTIONAL_METADATA} and value:
            meta[key] = value
    stage = meta["stage"].upper()
    meta["stage"] = stage if stage in VALID_STAGES else STAGE_ALL
    return meta, body


def read_document(path: Path) -> str:
    if path.suffix.lower() == ".pdf":
        from pypdf import PdfReader

        reader = PdfReader(str(path))
        return "\n".join(page.extract_text() or "" for page in reader.pages)
    return path.read_text(encoding="utf-8")


def chunk(text: str, size: int = 1000) -> list[str]:
    """제목 문맥을 보존하면서 문단 경계로 청킹한다.

    정책 조건과 예외가 글자 수 경계에서 갈라지면 검색 결과가 왜곡된다. 현재 heading을 각
    청크 앞에 붙이고, 한 문단이 size를 넘는 경우에만 안전장치로 잘라낸다.
    """
    text = text.strip()
    if not text:
        return []

    chunks: list[str] = []
    current_heading = ""
    current: list[str] = []

    def flush() -> None:
        if current:
            chunks.append("\n\n".join(current).strip())
            current.clear()

    for paragraph in [part.strip() for part in text.split("\n\n") if part.strip()]:
        if paragraph.startswith("#"):
            flush()
            current_heading = paragraph
            current.append(paragraph)
            continue

        prefix = [] if current else ([current_heading] if current_heading else [])
        candidate = "\n\n".join([*current, *prefix, paragraph])
        if current and len(candidate) > size:
            flush()
            if current_heading:
                current.append(current_heading)

        if len(paragraph) <= size:
            current.append(paragraph)
            continue

        flush()
        for start in range(0, len(paragraph), size):
            piece = paragraph[start : start + size].strip()
            chunks.append("\n\n".join(filter(None, [current_heading, piece])))

    flush()
    return [piece for piece in chunks if piece]


def discover_documents(corpus_dir: Path) -> list[Path]:
    """안내용 README를 제외한 지원 형식 문서만 고른다."""
    return [
        path
        for path in sorted(corpus_dir.rglob("*"))
        if path.suffix.lower() in {".md", ".txt", ".pdf"} and path.name.lower() != "readme.md"
    ]


def ingest(corpus_dir: Path, reset: bool = False) -> int:
    # 문서 파싱·청킹 단위 테스트는 외부 SDK 없이도 실행되도록 런타임 의존성을 늦게 읽는다.
    from app.services.embeddings import embed_texts
    from app.services.vectorstore import delete_source, reset_collection, upsert

    files = discover_documents(corpus_dir)
    if not files:
        print(f"코퍼스가 비어 있습니다: {corpus_dir}")
        return 0
    if reset:
        reset_collection()

    total_chunks = 0
    for path in files:
        rel = path.relative_to(corpus_dir).as_posix()
        meta, body = parse_front_matter(read_document(path), fallback_title=path.stem)
        pieces = chunk(body)
        if not pieces:
            continue
        ids = [f"{rel}::{i}" for i in range(len(pieces))]
        metadata = {
            key: value
            for key, value in meta.items()
            if key in {"title", "stage", "source", *OPTIONAL_METADATA} and value
        }
        metadata["source_path"] = rel
        metadatas = [metadata.copy() for _ in pieces]
        embeddings = embed_texts(pieces)
        delete_source(rel)
        upsert(ids=ids, embeddings=embeddings, documents=pieces, metadatas=metadatas)
        total_chunks += len(pieces)
        print(f"  ✓ {rel}  stage={meta['stage']}  chunks={len(pieces)}")

    print(f"인제스트 완료: 문서 {len(files)}개, 청크 {total_chunks}개")
    return total_chunks


def main() -> None:
    parser = argparse.ArgumentParser(description="코퍼스를 ChromaDB 에 인제스트한다.")
    parser.add_argument("--path", default="data/corpus", help="코퍼스 디렉터리")
    parser.add_argument("--reset", action="store_true", help="기존 컬렉션을 비우고 전체 코퍼스를 다시 적재")
    args = parser.parse_args()
    ingest(Path(args.path), reset=args.reset)


if __name__ == "__main__":
    main()
