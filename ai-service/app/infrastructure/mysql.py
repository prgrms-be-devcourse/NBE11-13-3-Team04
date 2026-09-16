from collections.abc import Generator
from functools import lru_cache

from sqlalchemy import Engine, create_engine, text
from sqlalchemy.orm import Session, sessionmaker

from app.core.config import get_settings
from app.domain.jobs import Base


# 연결 상태 확인과 재사용 설정을 포함한 공용 MySQL engine을 한 번만 만든다.
@lru_cache
def get_engine() -> Engine:
    settings = get_settings()
    return create_engine(
        settings.database_url,
        pool_pre_ping=True,
        pool_recycle=1800,
        pool_size=settings.db_pool_size,
        max_overflow=settings.db_max_overflow,
    )


# 같은 engine 설정을 공유하면서 요청마다 독립된 session을 만들 factory를 한 번만 준비한다.
@lru_cache
def get_session_factory() -> sessionmaker[Session]:
    return sessionmaker(bind=get_engine(), autoflush=False, expire_on_commit=False)


# FastAPI 요청이 끝나면 자동으로 닫히는 DB session을 제공한다.
def get_session() -> Generator[Session, None, None]:
    with get_session_factory()() as session:
        yield session


# 별도 migration 도구가 없는 MVP 환경에서 설정된 AI 테이블만 준비한다.
def create_tables() -> None:
    Base.metadata.create_all(get_engine())


# readiness 확인용으로 실제 SQL 한 번을 실행해 연결 가능 여부를 검사한다.
def check_database() -> None:
    with get_engine().connect() as connection:
        connection.execute(text("SELECT 1"))
