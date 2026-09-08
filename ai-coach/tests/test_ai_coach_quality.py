import json
import unittest
from unittest.mock import MagicMock, patch

from app.routers.coach import ask, clear_conversation
from app.schemas.coach import AskRequest, AskResponse, ResponseType, Stage
from app.services import rag, vectorstore
from app.services.clarification import missing_information
from app.services.conversation import ConversationStore, Turn


class AiCoachQualityTest(unittest.TestCase):
    def test_personal_loan_question_asks_for_missing_income(self):
        follow_up = missing_information("내가 청년 버팀목 대출 가능할까?", Stage.FIRST, {})
        self.assertIn("연소득", follow_up)

        response = rag.answer("내가 청년 버팀목 대출 가능할까?", Stage.FIRST, {})
        self.assertEqual(ResponseType.CLARIFICATION, response.response_type)
        self.assertEqual(follow_up, response.follow_up_question)

    def test_contract_schedule_asks_for_balance_date(self):
        follow_up = missing_information("계약 일정은 언제부터 준비해?", Stage.THIRD, {})
        self.assertIn("잔금 예정일", follow_up)

    def test_structured_answer_is_parsed_and_formatted(self):
        generated = rag._parse_generated(json.dumps({
            "summary": "가입 가능성을 확인할 수 있어요. [근거 1]",
            "reasons": ["연령 조건을 확인하세요. [근거 1]"],
            "next_actions": ["소득 서류를 준비하세요."],
            "warnings": ["최종 결과는 은행 심사에 따라 달라요."],
            "follow_up_question": None,
        }))

        answer = rag._format_answer(generated)

        self.assertIn("근거 1", answer)
        self.assertIn("다음 할 일", answer)
        self.assertIn("주의", answer)

    def test_short_follow_up_reuses_previous_question_for_retrieval(self):
        query = rag._retrieval_query("연봉은 3천이야", [Turn("청년 버팀목 가능할까?", "연소득을 알려주세요")])

        self.assertIn("청년 버팀목", query)
        self.assertIn("연봉은 3천", query)

    def test_hybrid_search_uses_keyword_and_deduplicates_source(self):
        collection = MagicMock()
        collection.query.return_value = {
            "ids": [["dense-1", "dense-2"]],
            "documents": [["일반 금융 안내", "다른 일반 안내"]],
            "metadatas": [[
                {"title": "일반", "source": "가이드", "source_path": "general.md", "stage": "FIRST"},
                {"title": "기타", "source": "가이드", "source_path": "other.md", "stage": "FIRST"},
            ]],
            "distances": [[0.9, 0.95]],
        }
        collection.get.return_value = {
            "ids": ["keyword-1", "keyword-2"],
            "documents": ["청년 버팀목 전세자금대출 조건", "청년 버팀목 제출 서류"],
            "metadatas": [
                {"title": "버팀목", "source": "주택도시기금", "source_path": "youth.md", "stage": "FIRST"},
                {"title": "버팀목", "source": "주택도시기금", "source_path": "youth.md", "stage": "FIRST"},
            ],
        }

        with patch("app.services.vectorstore.get_collection", return_value=collection):
            hits = vectorstore.hybrid_search([0.1], "청년 버팀목 가능해?", "FIRST", 4)

        self.assertEqual(1, len(hits))
        self.assertEqual("버팀목", hits[0].title)


class ConversationStoreTest(unittest.TestCase):
    def test_keeps_only_bounded_turns_and_sets_ttl(self):
        client = MagicMock()
        pipeline = client.pipeline.return_value
        store = ConversationStore(client)

        store.append(7, "main", "질문", "답변")

        pipeline.rpush.assert_called_once()
        stored = pipeline.rpush.call_args.args[1]
        self.assertNotIn("context", stored)
        pipeline.ltrim.assert_called_once_with("ai-coach:conversation:7:main", -5, -1)
        pipeline.expire.assert_called_once_with("ai-coach:conversation:7:main", 1800)
        pipeline.execute.assert_called_once()

    def test_load_and_clear_are_scoped_by_member(self):
        client = MagicMock()
        client.lrange.return_value = ['{"question":"앞 질문","answer":"앞 답변"}']
        store = ConversationStore(client)

        self.assertEqual([Turn("앞 질문", "앞 답변")], store.load(7, "loan"))
        store.clear(7, "loan")

        client.delete.assert_called_once_with("ai-coach:conversation:7:loan")

    def test_direct_identifiers_are_redacted_before_storage(self):
        client = MagicMock()
        pipeline = client.pipeline.return_value
        store = ConversationStore(client)

        store.append(7, "main", "010-1234-5678로 연락해", "test@example.com")

        stored = pipeline.rpush.call_args.args[1]
        self.assertNotIn("010-1234-5678", stored)
        self.assertNotIn("test@example.com", stored)

    def test_router_passes_recent_history_and_saves_only_question_and_answer(self):
        store = MagicMock(spec=ConversationStore)
        history = [Turn("앞 질문", "앞 답변")]
        store.load.return_value = history
        request = AskRequest(
            question="이어서 설명해줘",
            stage=Stage.FIRST,
            context={"annualIncome": 3000},
            conversation_id="loan",
        )
        expected = AskResponse(answer="새 답변", stage=Stage.FIRST, sources=[], conversation_id="loan")

        with patch("app.routers.coach.rag.answer", return_value=expected) as answer:
            response = ask(request, member_id=7, conversations=store)

        self.assertEqual(expected, response)
        answer.assert_called_once_with(
            request.question,
            request.stage,
            request.context,
            history=history,
            conversation_id="loan",
        )
        store.append.assert_called_once_with(7, "loan", "이어서 설명해줘", "새 답변")

        clear_conversation("loan", member_id=7, conversations=store)
        store.clear.assert_called_once_with(7, "loan")


if __name__ == "__main__":
    unittest.main()
