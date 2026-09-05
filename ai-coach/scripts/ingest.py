"""코퍼스 인제스트 CLI.

사용법:
    python -m scripts.ingest --path data/corpus

.md / .txt / .pdf 를 훑어 청킹 → OpenAI 임베딩 → ChromaDB upsert 한다.
각 문서 앞에 다음과 같은 front matter 로 메타데이터를 줄 수 있다(선택):

    ---
    title: 청년 전세자금대출 개요
    stage: FIRST        # BENCH/FIRST/SECOND/THIRD/HOME 중 하나, 또는 단계 무관이면 ALL
    source: 주택도시기금 안내
    ---

없으면 stage=ALL, title=파일명, source=파일명 으로 처리한다.
같은 파일을 다시 인제스트하면 청크 id 가 같아 덮어써진다(idempotent).
"""

import argparse
from pathlib import Path

from app.services.embeddings import embed_texts
from app.services.vectorstore import STAGE_ALL, upsert

VALID_STAGES = {"BENCH", "FIRST", "SECOND", "THIRD", "HOME", STAGE_ALL}


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
        if key in {"title", "stage", "source"} and value:
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


def chunk(text: str, size: int = 800, overlap: int = 100) -> list[str]:
    text = text.strip()
    if len(text) <= size:
        return [text] if text else []
    chunks: list[str] = []
    start = 0
    while start < len(text):
        end = start + size
        chunks.append(text[start:end].strip())
        start = end - overlap
    return [c for c in chunks if c]


def ingest(corpus_dir: Path) -> int:
    files = [p for p in sorted(corpus_dir.rglob("*")) if p.suffix.lower() in {".md", ".txt", ".pdf"}]
    if not files:
        print(f"코퍼스가 비어 있습니다: {corpus_dir}")
        return 0

    total_chunks = 0
    for path in files:
        rel = path.relative_to(corpus_dir).as_posix()
        meta, body = parse_front_matter(read_document(path), fallback_title=path.stem)
        pieces = chunk(body)
        if not pieces:
            continue
        ids = [f"{rel}::{i}" for i in range(len(pieces))]
        metadatas = [{"title": meta["title"], "stage": meta["stage"], "source": meta["source"]} for _ in pieces]
        embeddings = embed_texts(pieces)
        upsert(ids=ids, embeddings=embeddings, documents=pieces, metadatas=metadatas)
        total_chunks += len(pieces)
        print(f"  ✓ {rel}  stage={meta['stage']}  chunks={len(pieces)}")

    print(f"인제스트 완료: 문서 {len(files)}개, 청크 {total_chunks}개")
    return total_chunks


def main() -> None:
    parser = argparse.ArgumentParser(description="코퍼스를 ChromaDB 에 인제스트한다.")
    parser.add_argument("--path", default="data/corpus", help="코퍼스 디렉터리")
    args = parser.parse_args()
    ingest(Path(args.path))


if __name__ == "__main__":
    main()
