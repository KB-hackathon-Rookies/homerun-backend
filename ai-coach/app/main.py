"""AI 코치 FastAPI 진입점. Spring(8080)과 분리된 8000 포트로 뜬다."""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings
from app.routers import coach, health

app = FastAPI(title="HomeRun AI Coach", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origin_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router)
app.include_router(coach.router)
