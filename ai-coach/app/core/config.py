"""서비스 설정. 환경변수에서 읽는다(Spring 과 JWT_SECRET 공유)."""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # OpenAI (임베딩은 항상 이 키/기본 엔드포인트를 쓴다)
    openai_api_key: str = ""
    openai_chat_model: str = "gpt-4o-mini"
    openai_embedding_model: str = "text-embedding-3-small"
    openai_timeout_seconds: float = 10.0
    openai_max_retries: int = 1

    # 챗 LLM 만 OpenAI 호환 다른 provider(Qwen/DashScope, 자체 vLLM 등)로 교체할 때 쓴다.
    # 비우면 위 OpenAI 키/기본 엔드포인트로 폴백한다(= 기존 GPT 동작 유지).
    # 임베딩은 바꾸지 않는다 — 코퍼스가 OpenAI 임베딩으로 인제스트돼 있어 같은 모델을 유지해야 검색이 맞다.
    chat_base_url: str = ""
    chat_api_key: str = ""

    # Spring 이 발급한 JWT 를 검증하기 위한 공유 시크릿. 반드시 백엔드의 JWT_SECRET 과 같아야 한다.
    jwt_secret: str = ""
    jwt_algorithm: str = "HS256"

    # ChromaDB (임베디드 persistent)
    chroma_dir: str = "./data/chroma"
    chroma_collection: str = "homerun_corpus"

    # 검색/생성
    top_k: int = 4

    # CORS 허용 오리진(쉼표 구분). 프론트 오리진을 넣는다.
    cors_origins: str = "*"

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    @property
    def cors_origin_list(self) -> list[str]:
        return [origin.strip() for origin in self.cors_origins.split(",") if origin.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
