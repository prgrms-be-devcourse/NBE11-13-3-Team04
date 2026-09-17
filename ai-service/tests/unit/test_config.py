from pydantic import SecretStr

from app.core.config import Settings


def test_openai_key_is_not_exposed_in_settings_repr() -> None:
    settings = Settings(_env_file=None, OPENAI_API_KEY="test-secret-value")

    assert isinstance(settings.openai_api_key, SecretStr)
    assert "test-secret-value" not in repr(settings)


def test_shared_environment_requires_internal_auth() -> None:
    try:
        Settings(_env_file=None, env="production", internal_auth_enabled=False)
    except ValueError as exc:
        assert "internal API key authentication" in str(exc)
    else:
        raise AssertionError("production configuration must reject disabled internal auth")
