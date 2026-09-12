#!/usr/bin/env python3
"""Behavioral guards for INF-007 backup/restore scripts.

Uses temporary directories and fake executables. Never connects to real
PostgreSQL, Docker, Kafka, or production volumes.
"""

from __future__ import annotations

import hashlib
import os
import platform
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BACKUP_SCRIPT = ROOT / "infra" / "scripts" / "backup-postgres.sh"
RESTORE_SCRIPT = ROOT / "infra" / "scripts" / "restore-postgres-isolated.sh"

IS_WINDOWS = sys.platform.startswith("win")


def _bash_works(bash: Path) -> bool:
    """Return True only if Bash can resolve core Unix tools used by fakes."""
    try:
        completed = subprocess.run(
            [
                str(bash),
                "-lc",
                "command -v mv && command -v sha256sum && command -v chmod && command -v stat",
            ],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=15,
            check=False,
        )
    except (OSError, subprocess.SubprocessError):
        return False
    return completed.returncode == 0 and bool(completed.stdout.strip())


def resolve_bash() -> Path:
    """Resolve a working Bash for Windows and Linux/macOS CI.

    On Windows, prefer Git Bash over the WindowsApps WSL stub that
    ``shutil.which('bash')`` often returns.
    """
    candidates: list[Path] = []
    which = shutil.which("bash")
    windowsapps: Path | None = None
    if which:
        found = Path(which)
        if IS_WINDOWS and "WindowsApps" in str(found):
            windowsapps = found
        else:
            candidates.append(found)

    if IS_WINDOWS:
        candidates.extend(
            [
                Path(r"C:\Program Files\Git\bin\bash.exe"),
                Path(r"C:\Program Files\Git\usr\bin\bash.exe"),
                Path(os.environ.get("PROGRAMFILES", r"C:\Program Files"))
                / "Git"
                / "bin"
                / "bash.exe",
                Path(os.environ.get("LOCALAPPDATA", ""))
                / "Programs"
                / "Git"
                / "bin"
                / "bash.exe",
            ]
        )
        if windowsapps is not None:
            candidates.append(windowsapps)

    seen: set[str] = set()
    for candidate in candidates:
        key = str(candidate).lower()
        if key in seen:
            continue
        seen.add(key)
        if not candidate.exists():
            continue
        if _bash_works(candidate):
            return candidate

    raise FileNotFoundError(
        "A working Bash executable was not found. "
        "Install Git Bash on Windows, or system bash on Linux/macOS, "
        "so INF-007 backup/restore behavioral tests can run. "
        f"which(bash)={which!r}"
    )


try:
    BASH = resolve_bash()
    BASH_RESOLVE_ERROR: str | None = None
except FileNotFoundError as exc:
    BASH = None
    BASH_RESOLVE_ERROR = str(exc)


def to_bash_path(path: Path | str) -> str:
    text = str(path).replace("\\", "/")
    if IS_WINDOWS and len(text) >= 2 and text[1] == ":":
        return f"/{text[0].lower()}{text[2:]}"
    return text


def write_executable(path: Path, content: str) -> None:
    path.write_text(content, encoding="utf-8", newline="\n")
    path.chmod(path.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)


def resolve_system_command(name: str) -> str:
    """Resolve a real system command path via Bash, then shutil.which."""
    if BASH is not None:
        completed = subprocess.run(
            [str(BASH), "-lc", f"command -v {name}"],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            check=False,
        )
        found = completed.stdout.strip().splitlines()
        if completed.returncode == 0 and found and found[0]:
            return found[0]
    which = shutil.which(name)
    if which:
        return which
    raise FileNotFoundError(f"Required system command not found: {name}")


class ScriptTestCase(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        if BASH is None:
            # Do not silently skip on CI; fail with a clear setup error.
            raise RuntimeError(BASH_RESOLVE_ERROR)
        cls.bash = BASH
        cls.real_mv = resolve_system_command("mv")
        cls.real_sha256sum = resolve_system_command("sha256sum")
        cls.real_stat = resolve_system_command("stat")
        cls.real_chmod = resolve_system_command("chmod")
        print(
            f"[INF-007 guards] os={platform.system()} "
            f"platform={sys.platform} bash={cls.bash}",
            flush=True,
        )

    def setUp(self) -> None:
        if BASH is None:
            self.fail(BASH_RESOLVE_ERROR)

        self.tmp = tempfile.TemporaryDirectory()
        self.tmp_path = Path(self.tmp.name)
        self.bin_dir = self.tmp_path / "bin"
        self.bin_dir.mkdir()
        self.log_file = self.tmp_path / "fake-commands.log"
        self.log_file.write_text("", encoding="utf-8")
        self.backup_dir = self.tmp_path / "backups"
        self.backup_dir.mkdir()
        self.install_default_tool_fakes()

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def install_default_tool_fakes(self) -> None:
        self.install_fake_date("20990101T000000Z")
        write_executable(
            self.bin_dir / "mv",
            f"""#!/usr/bin/env bash
set -euo pipefail
echo "mv $@" >> "${{FAKE_LOG}}"
"{self.real_mv}" "$@"
""",
        )
        write_executable(
            self.bin_dir / "chmod",
            f"""#!/usr/bin/env bash
set -euo pipefail
echo "chmod $@" >> "${{FAKE_LOG}}"
"{self.real_chmod}" "$@"
""",
        )
        self.install_fake_sha256sum(succeed=True)

    def install_fake_date(self, value: str) -> None:
        # Use printf %s to preserve trailing spaces when needed by invalid cases.
        write_executable(
            self.bin_dir / "date",
            f"""#!/usr/bin/env bash
printf '%s' '{value}'
""",
        )

    def install_fake_sha256sum(self, succeed: bool = True) -> None:
        if succeed:
            content = f"""#!/usr/bin/env bash
set -euo pipefail
echo "$@" >> "${{FAKE_LOG}}"
file="$1"
hash="$("{self.real_sha256sum}" "$file" | awk '{{print $1}}')"
echo "${{hash}}  $(basename "$file")"
"""
        else:
            content = """#!/usr/bin/env bash
echo "$@" >> "${FAKE_LOG}"
echo "ERROR: fake sha256sum failed" >&2
exit 1
"""
        write_executable(self.bin_dir / "sha256sum", content)

    def install_fake_pg_dump(self, mode: str = "success") -> None:
        content = f"""#!/usr/bin/env bash
set -euo pipefail
echo "pg_dump $@" >> "${{FAKE_LOG}}"
mode="{mode}"
file=""
for arg in "$@"; do
  case "$arg" in
    --file=*) file="${{arg#--file=}}" ;;
  esac
done
if [[ -z "$file" ]]; then
  echo "ERROR: missing --file" >&2
  exit 2
fi
if [[ "$mode" == "fail" ]]; then
  echo "ERROR: fake pg_dump failed" >&2
  exit 1
fi
printf 'FAKE-DUMP-BYTES' > "$file"
"""
        write_executable(self.bin_dir / "pg_dump", content)

    def install_fake_mv_fail_checksum(self) -> None:
        content = f"""#!/usr/bin/env bash
set -euo pipefail
echo "mv $@" >> "${{FAKE_LOG}}"
src="$1"
dst="$2"
if [[ "$src" == *.sha256.tmp && "$dst" == *.sha256 ]]; then
  echo "ERROR: fake mv checksum finalize failed" >&2
  exit 1
fi
"{self.real_mv}" "$src" "$dst"
"""
        write_executable(self.bin_dir / "mv", content)

    def install_fake_chmod_fail(self) -> None:
        write_executable(
            self.bin_dir / "chmod",
            """#!/usr/bin/env bash
echo "chmod $@" >> "${FAKE_LOG}"
echo "ERROR: fake chmod failed" >&2
exit 1
""",
        )

    def install_restore_fakes(
        self,
        db_exists: bool = False,
        restore_exit: int = 0,
    ) -> None:
        exists_value = "1" if db_exists else ""
        psql = f"""#!/usr/bin/env bash
set -euo pipefail
echo "psql $@" >> "${{FAKE_LOG}}"
cmd=""
for arg in "$@"; do
  case "$arg" in
    --command=*) cmd="${{arg#--command=}}" ;;
  esac
done
if [[ "$cmd" == *"SELECT 1 FROM pg_database"* ]]; then
  printf '%s' "{exists_value}"
  exit 0
fi
if [[ "$cmd" == *"CREATE DATABASE"* ]]; then
  echo "CREATE_DATABASE" >> "${{FAKE_LOG}}"
  exit 0
fi
exit 0
"""
        restore = f"""#!/usr/bin/env bash
set -euo pipefail
echo "pg_restore $@" >> "${{FAKE_LOG}}"
for arg in "$@"; do
  if [[ "$arg" == "--clean" || "$arg" == "--if-exists" ]]; then
    echo "ERROR: overwrite/clean options are forbidden" >&2
    exit 99
  fi
done
exit {restore_exit}
"""
        write_executable(self.bin_dir / "psql", psql)
        write_executable(self.bin_dir / "pg_restore", restore)
        self.install_fake_sha256sum(succeed=True)

    def base_env(self) -> dict[str, str]:
        env = os.environ.copy()
        bash_bin = to_bash_path(self.bin_dir)
        # Keep fake tools first for tools without *_BIN overrides.
        path_parts = [bash_bin, "/usr/bin", "/bin"]
        existing = env.get("PATH", "")
        if existing:
            path_parts.append(existing)
        env["PATH"] = os.pathsep.join(path_parts) if IS_WINDOWS else ":".join(path_parts)
        # Prefer Unix-style PATH for Bash even on Windows.
        env["PATH"] = f"{bash_bin}:/usr/bin:/bin"
        env["FAKE_LOG"] = to_bash_path(self.log_file)
        env.pop("PGPASSWORD", None)
        env.pop("BACKUP_TIMESTAMP", None)
        return env

    def tool_overrides(self) -> dict[str, str]:
        return {
            "DATE_BIN": to_bash_path(self.bin_dir / "date"),
            "SHA256SUM_BIN": to_bash_path(self.bin_dir / "sha256sum"),
            "MV_BIN": to_bash_path(self.bin_dir / "mv"),
            "CHMOD_BIN": to_bash_path(self.bin_dir / "chmod"),
            "PG_DUMP_BIN": to_bash_path(self.bin_dir / "pg_dump"),
            "PSQL_BIN": to_bash_path(self.bin_dir / "psql"),
            "PG_RESTORE_BIN": to_bash_path(self.bin_dir / "pg_restore"),
        }

    def run_bash(
        self,
        script: Path,
        env: dict[str, str],
        timeout: float = 10.0,
    ) -> subprocess.CompletedProcess[str]:
        assert self.bash is not None
        return subprocess.run(
            [str(self.bash), to_bash_path(script)],
            cwd=str(ROOT),
            env=env,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout,
        )

    def fake_calls(self) -> str:
        return self.log_file.read_text(encoding="utf-8")

    def backup_env(self, **overrides: str) -> dict[str, str]:
        env = self.base_env()
        env.update(self.tool_overrides())
        env.update(
            {
                "BACKUP_DIR": to_bash_path(self.backup_dir),
                "POSTGRES_HOST": "127.0.0.1",
                "POSTGRES_PORT": "5432",
                "POSTGRES_DB": "ssabangpalbang",
                "POSTGRES_USER": "ssabang",
                "POSTGRES_PASSWORD": "secret-password-value",
            }
        )
        env.update(overrides)
        return env

    def restore_env(self, **overrides: str) -> dict[str, str]:
        env = self.base_env()
        env.update(self.tool_overrides())
        env.update(
            {
                "ISOLATED_RESTORE_HOST": "127.0.0.1",
                "ISOLATED_RESTORE_PORT": "5433",
                "ISOLATED_RESTORE_DB": "ssabangpalbang_restore_verify",
                "ISOLATED_RESTORE_USER": "restore_user",
                "ISOLATED_RESTORE_PASSWORD": "secret-restore-password",
                "ISOLATED_RESTORE_VOLUME": "postgres-restore-verify-data",
                "PRODUCTION_POSTGRES_DB": "ssabangpalbang",
                "PRODUCTION_POSTGRES_VOLUME": "postgres-prod-data",
                "CONFIRM_ISOLATED_RESTORE": "true",
                "CONFIRM_APPLICATION_DISCONNECTED": "true",
                "CONFIRM_SEPARATE_VOLUME": "true",
            }
        )
        env.update(overrides)
        return env

    def make_backup_pair(self) -> tuple[Path, Path]:
        dump = self.tmp_path / "sample.dump"
        dump.write_bytes(b"FAKE-DUMP-BYTES")
        digest = hashlib.sha256(dump.read_bytes()).hexdigest()
        checksum = Path(str(dump) + ".sha256")
        checksum.write_text(
            f"{digest}  {dump.name}\n",
            encoding="utf-8",
            newline="\n",
        )
        return dump, checksum

    def file_mode(self, path: Path) -> str:
        assert self.bash is not None
        mode_result = subprocess.run(
            [
                str(self.bash),
                "-lc",
                f"\"{self.real_stat}\" -c '%a' '{to_bash_path(path)}'",
            ],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        self.assertEqual(mode_result.returncode, 0, mode_result.stderr)
        return mode_result.stdout.strip()


class BackupGuardsTest(ScriptTestCase):
    def test_forbidden_path_fails_without_pg_dump(self) -> None:
        forbidden = self.tmp_path / "postgres-prod-data"
        forbidden.mkdir()
        self.install_fake_pg_dump()
        result = self.run_bash(
            BACKUP_SCRIPT,
            self.backup_env(BACKUP_DIR=to_bash_path(forbidden)),
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("pg_dump", self.fake_calls())

    def test_invalid_db_name_fails_without_pg_dump(self) -> None:
        self.install_fake_pg_dump()
        result = self.run_bash(
            BACKUP_SCRIPT,
            self.backup_env(POSTGRES_DB="../evil"),
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("pg_dump", self.fake_calls())

    def test_existing_artifacts_refused(self) -> None:
        self.install_fake_pg_dump()
        for name in (
            "ssabangpalbang_ssabangpalbang_20990101T000000Z.dump",
            "ssabangpalbang_ssabangpalbang_20990101T000000Z.dump.tmp",
            "ssabangpalbang_ssabangpalbang_20990101T000000Z.dump.sha256",
            "ssabangpalbang_ssabangpalbang_20990101T000000Z.dump.sha256.tmp",
        ):
            target = self.backup_dir / name
            target.write_text("existing", encoding="utf-8")
            self.log_file.write_text("", encoding="utf-8")
            result = self.run_bash(BACKUP_SCRIPT, self.backup_env())
            self.assertNotEqual(result.returncode, 0, name)
            self.assertNotIn("pg_dump", self.fake_calls())
            self.assertEqual(target.read_text(encoding="utf-8"), "existing")
            target.unlink()

    def test_valid_timestamp_calls_pg_dump_and_names_file(self) -> None:
        self.install_fake_date("20990101T000000Z")
        self.install_fake_pg_dump(mode="success")
        result = self.run_bash(BACKUP_SCRIPT, self.backup_env())
        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        self.assertIn("pg_dump", self.fake_calls())
        expected = (
            self.backup_dir / "ssabangpalbang_ssabangpalbang_20990101T000000Z.dump"
        )
        self.assertTrue(expected.is_file())

    def test_empty_timestamp_fails_without_pg_dump(self) -> None:
        self.install_fake_date("")
        self.install_fake_pg_dump()
        result = self.run_bash(BACKUP_SCRIPT, self.backup_env())
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("pg_dump", self.fake_calls())

    def test_path_separator_timestamp_fails_without_pg_dump(self) -> None:
        self.install_fake_date("2099/01/01T000000Z")
        self.install_fake_pg_dump()
        result = self.run_bash(BACKUP_SCRIPT, self.backup_env())
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("pg_dump", self.fake_calls())

    def test_invalid_format_timestamp_fails_without_pg_dump(self) -> None:
        self.install_fake_date("20990101-000000Z")
        self.install_fake_pg_dump()
        result = self.run_bash(BACKUP_SCRIPT, self.backup_env())
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("pg_dump", self.fake_calls())

    def test_whitespace_timestamp_fails_without_pg_dump(self) -> None:
        self.install_fake_date("20990101T000000Z ")
        self.install_fake_pg_dump()
        result = self.run_bash(BACKUP_SCRIPT, self.backup_env())
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("pg_dump", self.fake_calls())

        self.log_file.write_text("", encoding="utf-8")
        self.install_fake_date("2099 0101T000000Z")
        result = self.run_bash(BACKUP_SCRIPT, self.backup_env())
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("pg_dump", self.fake_calls())

    def test_fake_dump_failure_leaves_no_final_artifacts(self) -> None:
        self.install_fake_pg_dump(mode="fail")
        result = self.run_bash(BACKUP_SCRIPT, self.backup_env())
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(list(self.backup_dir.glob("*.dump")), [])
        self.assertEqual(list(self.backup_dir.glob("*.sha256")), [])
        self.assertEqual(list(self.backup_dir.glob("*.tmp")), [])

    def test_success_creates_dump_checksum_pair_with_restricted_permissions(
        self,
    ) -> None:
        self.install_fake_pg_dump(mode="success")
        result = self.run_bash(BACKUP_SCRIPT, self.backup_env())
        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        dumps = list(self.backup_dir.glob("*.dump"))
        checksums = list(self.backup_dir.glob("*.sha256"))
        self.assertEqual(len(dumps), 1)
        self.assertEqual(len(checksums), 1)
        dump = dumps[0]
        checksum = checksums[0]
        self.assertEqual(dump.read_bytes(), b"FAKE-DUMP-BYTES")
        expected = hashlib.sha256(dump.read_bytes()).hexdigest()
        self.assertTrue(checksum.read_text(encoding="utf-8").startswith(expected))
        self.assertIn("chmod 600", self.fake_calls())

        for path in (dump, checksum):
            mode = self.file_mode(path)
            if IS_WINDOWS:
                self.assertIn(mode, {"600", "640", "644"}, path)
            else:
                self.assertEqual(mode, "600", path)

        combined = result.stdout + result.stderr
        self.assertNotIn("secret-password-value", combined)
        self.assertNotIn("PGPASSWORD", combined)

    def test_chmod_failure_fails_and_cleans_artifacts(self) -> None:
        self.install_fake_pg_dump(mode="success")
        self.install_fake_chmod_fail()
        result = self.run_bash(BACKUP_SCRIPT, self.backup_env())
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("chmod", self.fake_calls())
        self.assertEqual(list(self.backup_dir.glob("*.dump")), [])
        self.assertEqual(list(self.backup_dir.glob("*.sha256")), [])
        self.assertEqual(list(self.backup_dir.glob("*.tmp")), [])

    def test_checksum_failure_leaves_no_final_artifacts(self) -> None:
        self.install_fake_pg_dump(mode="success")
        self.install_fake_sha256sum(succeed=False)
        result = self.run_bash(BACKUP_SCRIPT, self.backup_env())
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(list(self.backup_dir.glob("*.dump")), [])
        self.assertEqual(list(self.backup_dir.glob("*.sha256")), [])
        self.assertEqual(list(self.backup_dir.glob("*.tmp")), [])

    def test_checksum_finalize_failure_removes_incomplete_dump(self) -> None:
        self.install_fake_pg_dump(mode="success")
        self.install_fake_mv_fail_checksum()
        result = self.run_bash(BACKUP_SCRIPT, self.backup_env())
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(list(self.backup_dir.glob("*.dump")), [])
        self.assertEqual(list(self.backup_dir.glob("*.sha256")), [])
        self.assertEqual(list(self.backup_dir.glob("*.tmp")), [])


class RestoreGuardsTest(ScriptTestCase):
    def test_missing_confirm_flag_fails_without_db_tools(self) -> None:
        dump, _ = self.make_backup_pair()
        self.install_restore_fakes()
        env = self.restore_env(
            BACKUP_FILE=to_bash_path(dump),
            CONFIRM_ISOLATED_RESTORE="false",
        )
        result = self.run_bash(RESTORE_SCRIPT, env)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("psql", self.fake_calls())
        self.assertNotIn("pg_restore", self.fake_calls())

    def test_production_db_name_rejected(self) -> None:
        dump, _ = self.make_backup_pair()
        self.install_restore_fakes()
        env = self.restore_env(
            BACKUP_FILE=to_bash_path(dump),
            ISOLATED_RESTORE_DB="ssabangpalbang",
            PRODUCTION_POSTGRES_DB="ssabangpalbang",
        )
        result = self.run_bash(RESTORE_SCRIPT, env)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("psql", self.fake_calls())
        self.assertNotIn("pg_restore", self.fake_calls())

    def test_postgres_prod_data_rejected(self) -> None:
        dump, _ = self.make_backup_pair()
        self.install_restore_fakes()
        env = self.restore_env(
            BACKUP_FILE=to_bash_path(dump),
            ISOLATED_RESTORE_VOLUME="postgres-prod-data",
        )
        result = self.run_bash(RESTORE_SCRIPT, env)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("psql", self.fake_calls())
        self.assertNotIn("pg_restore", self.fake_calls())

    def test_nonlocal_host_rejected(self) -> None:
        dump, _ = self.make_backup_pair()
        self.install_restore_fakes()
        env = self.restore_env(
            BACKUP_FILE=to_bash_path(dump),
            ISOLATED_RESTORE_HOST="db.internal.example",
        )
        result = self.run_bash(RESTORE_SCRIPT, env)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("psql", self.fake_calls())
        self.assertNotIn("pg_restore", self.fake_calls())

    def test_production_default_port_rejected(self) -> None:
        dump, _ = self.make_backup_pair()
        self.install_restore_fakes()
        env = self.restore_env(
            BACKUP_FILE=to_bash_path(dump),
            ISOLATED_RESTORE_PORT="5432",
        )
        result = self.run_bash(RESTORE_SCRIPT, env)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("psql", self.fake_calls())
        self.assertNotIn("pg_restore", self.fake_calls())

    def test_checksum_mismatch_fails_before_psql(self) -> None:
        dump, checksum = self.make_backup_pair()
        checksum.write_text(
            "0" * 64 + "  sample.dump\n",
            encoding="utf-8",
            newline="\n",
        )
        self.install_restore_fakes()
        env = self.restore_env(BACKUP_FILE=to_bash_path(dump))
        result = self.run_bash(RESTORE_SCRIPT, env)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("psql", self.fake_calls())
        self.assertNotIn("pg_restore", self.fake_calls())

    def test_existing_db_rejected_without_create_or_restore(self) -> None:
        dump, _ = self.make_backup_pair()
        self.install_restore_fakes(db_exists=True)
        env = self.restore_env(BACKUP_FILE=to_bash_path(dump))
        result = self.run_bash(RESTORE_SCRIPT, env)
        self.assertNotEqual(result.returncode, 0)
        calls = self.fake_calls()
        self.assertIn("psql", calls)
        self.assertNotIn("CREATE_DATABASE", calls)
        self.assertNotIn("pg_restore", calls)

    def test_new_db_restore_without_clean_options(self) -> None:
        dump, _ = self.make_backup_pair()
        self.install_restore_fakes(db_exists=False, restore_exit=0)
        env = self.restore_env(BACKUP_FILE=to_bash_path(dump))
        result = self.run_bash(RESTORE_SCRIPT, env)
        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        calls = self.fake_calls()
        self.assertIn("CREATE_DATABASE", calls)
        self.assertIn("pg_restore", calls)
        self.assertNotIn("--clean", calls)
        self.assertNotIn("--if-exists", calls)

    def test_restore_failure_exits_without_auto_delete(self) -> None:
        dump, _ = self.make_backup_pair()
        self.install_restore_fakes(db_exists=False, restore_exit=1)
        env = self.restore_env(BACKUP_FILE=to_bash_path(dump))
        result = self.run_bash(RESTORE_SCRIPT, env)
        self.assertNotEqual(result.returncode, 0)
        combined = result.stdout + result.stderr + self.fake_calls()
        self.assertIn("pg_restore", self.fake_calls())
        self.assertNotIn("DROP DATABASE", combined)
        self.assertNotIn("docker", combined.lower())
        self.assertIn("partially restored", result.stderr)


class StaticPolicyTest(unittest.TestCase):
    def test_scripts_keep_core_safety_phrases(self) -> None:
        backup = BACKUP_SCRIPT.read_text(encoding="utf-8")
        restore = RESTORE_SCRIPT.read_text(encoding="utf-8")
        self.assertIn("umask 077", backup)
        self.assertIn("--format=custom", backup)
        self.assertIn("postgres-prod-data", backup)
        self.assertIn("YYYYMMDDTHHMMSSZ", backup)
        self.assertNotIn("BACKUP_TIMESTAMP", backup)
        self.assertNotIn("|| true", backup)
        self.assertIn("CONFIRM_ISOLATED_RESTORE", restore)
        self.assertIn("CONFIRM_APPLICATION_DISCONNECTED", restore)
        self.assertIn("CONFIRM_SEPARATE_VOLUME", restore)
        self.assertNotIn("ALLOW_NON_LOCAL_ISOLATED_RESTORE", restore)
        self.assertNotIn("--clean", restore)
        self.assertIn("already exists", restore)
        self.assertIn("operator-declared", restore)

    def test_bash_resolver_is_portable(self) -> None:
        source = Path(__file__).read_text(encoding="utf-8")
        self.assertIn("shutil.which(\"bash\")", source)
        self.assertIn("IS_WINDOWS", source)
        self.assertIn("_bash_works", source)
        self.assertNotIn(
            'GIT_BASH = Path(r"C:\\Program Files\\Git\\bin\\bash.exe")',
            source,
        )
        resolved = resolve_bash()
        self.assertTrue(resolved.exists(), resolved)
        self.assertTrue(_bash_works(resolved), resolved)
        if not IS_WINDOWS:
            which = shutil.which("bash")
            self.assertIsNotNone(which)
            # On Linux/macOS the working bash must come from PATH discovery.
            self.assertEqual(Path(which).resolve(), resolved.resolve())
            self.assertNotIn("WindowsApps", str(resolved))


if __name__ == "__main__":
    print(
        f"[INF-007 guards] os={platform.system()} "
        f"platform={sys.platform} bash={BASH}",
        flush=True,
    )
    unittest.main(verbosity=2)
