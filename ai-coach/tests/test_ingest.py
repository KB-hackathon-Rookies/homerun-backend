import unittest

from pathlib import Path
from tempfile import TemporaryDirectory

from scripts.ingest import chunk, discover_documents, parse_front_matter


class IngestTest(unittest.TestCase):
    def test_extended_front_matter_is_preserved(self):
        metadata, body = parse_front_matter(
            """---
title: 청년 버팀목
stage: FIRST
source: 주택도시기금
source_url: https://example.com/youth
topic: loan_eligibility
fact_codes: FCT-003,FCT-008
verified_at: 2026-09-07
---
본문
""",
            "fallback",
        )

        self.assertEqual("FIRST", metadata["stage"])
        self.assertEqual("https://example.com/youth", metadata["source_url"])
        self.assertEqual("FCT-003,FCT-008", metadata["fact_codes"])
        self.assertEqual("본문\n", body)

    def test_heading_is_repeated_when_section_is_split(self):
        pieces = chunk("# 위험 확인\n\n첫 문단입니다.\n\n두 번째 문단입니다.", size=20)

        self.assertGreaterEqual(len(pieces), 2)
        self.assertTrue(all(piece.startswith("# 위험 확인") for piece in pieces))

    def test_readme_is_not_a_corpus_document(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "README.md").write_text("안내", encoding="utf-8")
            (root / "policy.md").write_text("정책", encoding="utf-8")

            self.assertEqual([root / "policy.md"], discover_documents(root))


if __name__ == "__main__":
    unittest.main()
