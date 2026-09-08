"""Redis 기반 짧은 대화 기억. Redis 장애는 코치 질문 자체를 막지 않는다."""

from dataclasses import asdict, dataclass
from functools import lru_cache
import json
import logging
import re

from redis import Redis
from redis.exceptions import RedisError

from app.core.config import settings

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class Turn:
    question: str
    answer: str


class ConversationStore:
    def __init__(self, client: Redis | None = None) -> None:
        self.client = client or Redis(
            host=settings.redis_host,
            port=settings.redis_port,
            password=settings.redis_password or None,
            db=settings.redis_db,
            decode_responses=True,
            socket_connect_timeout=0.5,
            socket_timeout=0.5,
        )

    def load(self, member_id: int, conversation_id: str) -> list[Turn]:
        try:
            values = self.client.lrange(self._key(member_id, conversation_id), 0, -1)
            return [Turn(**json.loads(value)) for value in values]
        except (RedisError, ValueError, TypeError, KeyError):
            logger.warning("AI 코치 대화 기록을 불러오지 못했습니다.", exc_info=True)
            return []

    def append(self, member_id: int, conversation_id: str, question: str, answer: str) -> None:
        key = self._key(member_id, conversation_id)
        value = json.dumps(
            asdict(Turn(question=_redact(question), answer=_redact(answer))), ensure_ascii=False
        )
        try:
            pipeline = self.client.pipeline(transaction=True)
            pipeline.rpush(key, value)
            pipeline.ltrim(key, -settings.conversation_max_turns, -1)
            pipeline.expire(key, settings.conversation_ttl_seconds)
            pipeline.execute()
        except RedisError:
            logger.warning("AI 코치 대화 기록을 저장하지 못했습니다.", exc_info=True)

    def clear(self, member_id: int, conversation_id: str) -> None:
        try:
            self.client.delete(self._key(member_id, conversation_id))
        except RedisError:
            logger.warning("AI 코치 대화 기록을 삭제하지 못했습니다.", exc_info=True)

    @staticmethod
    def _key(member_id: int, conversation_id: str) -> str:
        return f"ai-coach:conversation:{member_id}:{conversation_id}"


@lru_cache
def get_conversation_store() -> ConversationStore:
    return ConversationStore()


def _redact(text: str) -> str:
    """대화 연결에 불필요한 직접 식별자와 계좌 형태 숫자는 Redis에 남기지 않는다."""
    patterns = (
        (r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", "[이메일]"),
        (r"(?<!\d)01[016789][ -]?\d{3,4}[ -]?\d{4}(?!\d)", "[휴대전화]"),
        (r"(?<!\d)\d{6}[ -]?\d{7}(?!\d)", "[주민번호]"),
        (r"(?<!\d)\d{10,16}(?!\d)", "[계좌번호]"),
    )
    redacted = text
    for pattern, replacement in patterns:
        redacted = re.sub(pattern, replacement, redacted)
    return redacted
