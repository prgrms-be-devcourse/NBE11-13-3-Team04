import importlib
from unittest.mock import Mock

import pytest
from fastapi.testclient import TestClient

from app.core.config import Settings

main = importlib.import_module("app.main")


def test_liveness_after_successful_startup(monkeypatch) -> None:
    monkeypatch.setattr(main, "settings", Settings(_env_file=None, auto_create_tables=True))
    initialize = Mock()
    monkeypatch.setattr(main, "create_tables", initialize)
    monkeypatch.setattr(main, "recover_interrupted_jobs", Mock(return_value=0))
    with TestClient(main.app) as client:
        response = client.get("/health/live")

    initialize.assert_called_once()
    assert response.status_code == 200
    assert response.json() == {"status": "UP", "service": "iter-ai-service"}


def test_startup_fails_if_database_initialization_fails(monkeypatch) -> None:
    monkeypatch.setattr(main, "settings", Settings(_env_file=None, auto_create_tables=True))
    monkeypatch.setattr(main, "create_tables", Mock(side_effect=RuntimeError("DB unavailable")))
    monkeypatch.setattr(main, "recover_interrupted_jobs", Mock(return_value=0))
    with pytest.raises(RuntimeError, match="DB unavailable"), TestClient(main.app):
        pass
