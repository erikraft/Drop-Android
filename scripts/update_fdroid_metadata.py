#!/usr/bin/env python3
"""Update the self-hosted F-Droid metadata to match the Gradle version."""
from __future__ import annotations

import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[1]
GRADLE_FILE = ROOT / "app" / "build.gradle"
METADATA_FILE = ROOT / "fdroid" / "metadata" / "com.erikraft.drop.yml"

VERSION_CODE_PATTERN = re.compile(r"versionCode\s+(\d+)")
VERSION_NAME_PATTERN = re.compile(r'versionName\s+"([^"]+)"')


def extract_versions() -> tuple[str, str]:
    """Read versionCode and versionName from the Gradle build file."""
    text = GRADLE_FILE.read_text(encoding="utf-8")
    version_code_match = VERSION_CODE_PATTERN.search(text)
    version_name_match = VERSION_NAME_PATTERN.search(text)

    if not version_code_match:
        raise SystemExit("Could not locate versionCode in app/build.gradle")
    if not version_name_match:
        raise SystemExit("Could not locate versionName in app/build.gradle")

    return version_name_match.group(1), version_code_match.group(1)


def get_commit_sha() -> str:
    """Return the short SHA of the current commit for traceability."""
    try:
        sha = subprocess.check_output(["git", "rev-parse", "--short", "HEAD"], cwd=ROOT)
    except subprocess.CalledProcessError as exc:  # pragma: no cover - defensive
        raise SystemExit("Failed to query git commit SHA") from exc
    return sha.decode().strip()


def update_metadata(version_name: str, version_code: str, sha: str) -> None:
    text = METADATA_FILE.read_text(encoding="utf-8")

    replacements = {
        r"(?m)^CurrentVersion: .+$": f"CurrentVersion: {version_name}",
        r"(?m)^CurrentVersionCode: .+$": f"CurrentVersionCode: {version_code}",
        r"(?m)^# Git commit: .+$": f"# Git commit: {sha}",
    }

    for pattern, replacement in replacements.items():
        if re.search(pattern, text):
            text = re.sub(pattern, replacement, text)
        else:  # pragma: no cover - metadata initially missing comment, add it
            if "# Git commit:" in replacement:
                text = text.rstrip() + f"\n# Git commit: {sha}\n"

    METADATA_FILE.write_text(text, encoding="utf-8")


def main() -> None:
    version_name, version_code = extract_versions()
    sha = get_commit_sha()
    update_metadata(version_name, version_code, sha)


if __name__ == "__main__":
    main()
