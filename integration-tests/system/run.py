import os
import shutil
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COMPOSE_FILE = ROOT / "integration-tests" / "system" / "compose.yml"
PROJECT_NAME = "iter-system-test"


def run(command: list[str], *, env: dict[str, str] | None = None) -> None:
    subprocess.run(command, cwd=ROOT, env=env, check=True)


def stage_jars() -> None:
    artifact_dir = ROOT / "integration-tests" / "system" / ".artifacts"
    artifact_dir.mkdir(exist_ok=True)
    copy_single_jar(ROOT / "apps" / "monolith" / "build" / "libs", "iter-*.jar", artifact_dir / "monolith.jar")
    copy_single_jar(ROOT / "apps" / "chat" / "build" / "libs", "iter-chat-*.jar", artifact_dir / "chat.jar")


def copy_single_jar(source_dir: Path, pattern: str, target: Path) -> None:
    candidates = list(source_dir.glob(pattern))
    if len(candidates) != 1:
        raise RuntimeError(f"{source_dir / pattern} 산출물이 한 개가 아닙니다: {candidates}")
    shutil.copy2(candidates[0], target)


def ensure_docker() -> None:
    try:
        subprocess.run(
            ["docker", "info", "--format", "{{.ServerVersion}}"],
            check=True,
            capture_output=True,
            text=True,
            timeout=15,
        )
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
        raise SystemExit(
            "Docker engine did not respond within 15 seconds; system tests were not started."
        ) from None


def main() -> None:
    wrapper = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    gradle = ["cmd", "/d", "/c", wrapper] if os.name == "nt" else [wrapper]
    compose = [
        "docker",
        "compose",
        "-p",
        PROJECT_NAME,
        "-f",
        str(COMPOSE_FILE),
    ]
    environment = os.environ.copy()
    environment.update(
        {
            "ITER_SYSTEM_COMPOSE_FILE": str(COMPOSE_FILE),
            "ITER_SYSTEM_COMPOSE_PROJECT": PROJECT_NAME,
            "AI_LIVE_TEST": "true",
            "AI_SERVICE_BASE_URL": "http://127.0.0.1:18000",
            "AI_INTERNAL_API_KEY": "iter-system-secret",
        }
    )

    ensure_docker()
    try:
        run([*gradle, ":apps:monolith:bootJar", ":apps:chat:bootJar"])
        stage_jars()
        run([*compose, "up", "-d", "--build"])
        run(
            [
                sys.executable,
                "-m",
                "pytest",
                "integration-tests/system/test_system_smoke.py",
                "-q",
            ],
            env=environment,
        )
        run([*gradle, "--no-daemon", ":services:ai:systemTest"], env=environment)
    except subprocess.CalledProcessError:
        subprocess.run([*compose, "ps"], cwd=ROOT, check=False)
        subprocess.run([*compose, "logs", "--no-color", "--tail", "200"], cwd=ROOT, check=False)
        raise
    finally:
        subprocess.run([*compose, "down", "-v", "--remove-orphans"], cwd=ROOT, check=False)


if __name__ == "__main__":
    main()
