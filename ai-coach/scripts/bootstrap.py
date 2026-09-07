"""컨테이너 시작 시 코퍼스 변경분을 한 번만 자동 인덱싱한다."""

import hashlib
import os
from pathlib import Path

from scripts.ingest import discover_documents, ingest


def corpus_fingerprint(corpus_dir: Path) -> str:
    digest = hashlib.sha256()
    for path in discover_documents(corpus_dir):
        digest.update(path.relative_to(corpus_dir).as_posix().encode())
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def bootstrap(corpus_dir: Path, marker: Path) -> bool:
    fingerprint = corpus_fingerprint(corpus_dir)
    if marker.exists() and marker.read_text(encoding="utf-8").strip() == fingerprint:
        print("코퍼스 변경 없음: 기존 Chroma 인덱스를 사용합니다.")
        return False
    ingest(corpus_dir, reset=True)
    marker.parent.mkdir(parents=True, exist_ok=True)
    marker.write_text(fingerprint, encoding="utf-8")
    return True


def main() -> None:
    corpus_dir = Path(os.getenv("CORPUS_DIR", "data/corpus"))
    chroma_dir = Path(os.getenv("CHROMA_DIR", "data/chroma"))
    bootstrap(corpus_dir, chroma_dir / ".corpus.sha256")


if __name__ == "__main__":
    main()
