"""서비스 설정. 환경변수에서 읽는다(Spring 과 JWT_SECRET 공유)."""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # OpenAI
    openai_api_key: str = ""
    openai_chat_model: str = "gpt-4o-mini"
    openai_embedding_model: str = "text-embedding-3-small"
    openai_timeout_seconds: float = 10.0
    openai_max_retries: int = 1

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
