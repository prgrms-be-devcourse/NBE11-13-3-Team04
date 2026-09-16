import pytest
from fastapi import HTTPException

from app.core.config import Settings
from app.core.security import verify_internal_api_key


def test_internal_api_key_can_be_disabled_for_local(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(
        "app.core.security.get_settings",
        lambda: Settings(_env_file=None, internal_auth_enabled=False),
    )

    assert verify_internal_api_key(None) is None


def test_wrong_internal_api_key_is_rejected(monkeypatch: pytest.MonkeyPatch) -> None:
    settings = Settings(
        _env_file=None,
        internal_auth_enabled=True,
        internal_api_key="shared-secret",
    )
    monkeypatch.setattr("app.core.security.get_settings", lambda: settings)

    with pytest.raises(HTTPException) as exc_info:
        verify_internal_api_key("wrong-secret")

    assert exc_info.value.status_code == 401


@pytest.mark.parametrize("key", ["", "   "])
def test_empty_configured_key_is_not_accepted(monkeypatch, key: str) -> None:
    settings = Settings(_env_file=None, internal_auth_enabled=True, internal_api_key=key)
    monkeypatch.setattr("app.core.security.get_settings", lambda: settings)
    with pytest.raises(HTTPException) as exc_info:
        verify_internal_api_key(key)
    assert exc_info.value.status_code == 503
    assert settings.readiness_errors()


def test_correct_internal_api_key_is_accepted(monkeypatch) -> None:
    settings = Settings(
        _env_file=None, internal_auth_enabled=True, internal_api_key="test-internal-key"
    )
    monkeypatch.setattr("app.core.security.get_settings", lambda: settings)
    assert verify_internal_api_key("test-internal-key") is None
