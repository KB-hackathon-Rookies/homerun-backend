"""예상 질문 세트가 코퍼스로 답변 가능한지(근거 문서 존재) 검증한다.

실제 답변 품질 eval 은 OpenAI 임베딩·챗이 필요해 여기서 돌리지 않는다. 대신 "사용자가 물을
질문마다 그 답의 근거가 될 코퍼스 문서가 stage/topic 으로 실제 존재하고, 핵심어까지 담고
있는가"를 stdlib 로 검증한다 — 코퍼스에서 문서를 지우거나 질문이 늘어날 때 갭을 잡는다.
"""

import unittest
from pathlib import Path

from scripts.ingest import discover_documents, parse_front_matter
from tests.coach_eval_questions import EVAL_QUESTIONS

CORPUS_DIR = Path("data/corpus")
STAGE_ALL = "ALL"


def _load_corpus() -> list[tuple[dict, str]]:
    documents = []
    for path in discover_documents(CORPUS_DIR):
        metadata, body = parse_front_matter(path.read_text(encoding="utf-8"), path.stem)
        documents.append((metadata, body))
    return documents


class CoachCorpusCoverageTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.documents = _load_corpus()
        assert cls.documents, "코퍼스 문서를 하나도 읽지 못했습니다."

    def test_every_expected_question_has_grounding(self) -> None:
        """예상 질문마다 (질문 단계 또는 공통) + topic 이 맞는 근거 문서가 있고, 핵심어를 담고 있다."""
        for question in EVAL_QUESTIONS:
            serving = [
                (metadata, body)
                for metadata, body in self.documents
                if metadata.get("topic") == question.topic
                and metadata.get("stage") in (question.stage, STAGE_ALL)
            ]
            self.assertTrue(
                serving,
                f"'{question.question}' 에 답할 근거 문서가 없습니다"
                f" (stage={question.stage}, topic={question.topic}).",
            )
            self.assertTrue(
                any(question.keyword in body for _, body in serving),
                f"'{question.question}' 의 근거 문서(topic={question.topic})에"
                f" 핵심어 '{question.keyword}' 가 없습니다.",
            )

    def test_every_corpus_topic_has_expected_question(self) -> None:
        """코퍼스에 있는 모든 topic 은 예상 질문 세트로 exercise 된다(새 문서 추가 시 질문 누락 방지)."""
        asked_topics = {question.topic for question in EVAL_QUESTIONS}
        for metadata, _ in self.documents:
            self.assertIn(
                metadata.get("topic"),
                asked_topics,
                f"코퍼스 topic '{metadata.get('topic')}'({metadata.get('title')})에 대응하는 예상 질문이 없습니다.",
            )

    def test_all_flow_stages_have_questions(self) -> None:
        stages = {question.stage for question in EVAL_QUESTIONS}
        for stage in ("FIRST", "SECOND", "THIRD", "HOME"):
            self.assertIn(stage, stages, f"{stage} 단계 예상 질문이 없습니다.")


if __name__ == "__main__":
    unittest.main()
