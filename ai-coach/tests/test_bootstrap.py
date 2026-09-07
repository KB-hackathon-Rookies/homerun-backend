import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from scripts.bootstrap import bootstrap, corpus_fingerprint


class BootstrapTest(unittest.TestCase):
    def test_same_corpus_is_not_ingested_twice(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            corpus = root / "corpus"
            corpus.mkdir()
            (corpus / "policy.md").write_text("정책 안내", encoding="utf-8")
            marker = root / "chroma" / ".corpus.sha256"
            marker.parent.mkdir()
            marker.write_text(corpus_fingerprint(corpus), encoding="utf-8")

            with patch("scripts.bootstrap.ingest") as ingest:
                self.assertFalse(bootstrap(corpus, marker))
                ingest.assert_not_called()

    def test_changed_corpus_is_ingested_and_marker_is_updated(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            corpus = root / "corpus"
            corpus.mkdir()
            (corpus / "policy.md").write_text("새 정책 안내", encoding="utf-8")
            marker = root / "chroma" / ".corpus.sha256"

            with patch("scripts.bootstrap.ingest") as ingest:
                self.assertTrue(bootstrap(corpus, marker))
                ingest.assert_called_once_with(corpus, reset=True)
                self.assertEqual(corpus_fingerprint(corpus), marker.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
