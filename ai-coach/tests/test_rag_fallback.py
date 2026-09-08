import unittest
from unittest.mock import patch

from app.schemas.coach import Stage
from app.services import rag
from app.services.vectorstore import Hit


class RagFallbackTest(unittest.TestCase):
    def test_search_failure_returns_retry_message_without_sources(self):
        with patch("app.services.rag.embed_query", side_effect=TimeoutError):
            response = rag.answer("대출 조건 알려줘", Stage.FIRST, None)

        self.assertIn("다시 시도", response.answer)
        self.assertEqual([], response.sources)

    def test_generation_failure_keeps_retrieved_sources(self):
        hit = Hit("청년 버팀목", "주택도시기금", "https://example.com", "조건 안내", 0.1)
        with (
            patch("app.services.rag.embed_query", return_value=[0.1]),
            patch("app.services.rag.vectorstore.hybrid_search", return_value=[hit]),
            patch("app.services.rag._generate", side_effect=TimeoutError),
        ):
            response = rag.answer("대출 조건 알려줘", Stage.FIRST, None)

        self.assertIn("근거 자료", response.answer)
        self.assertEqual("청년 버팀목", response.sources[0].title)


if __name__ == "__main__":
    unittest.main()
