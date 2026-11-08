#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

python3 scripts/validate_architecture.py
python3 scripts/validate_repo.py
python3 -m unittest discover -s scripts/tests
./gradlew ktlintCheck detekt test lint --continue -Porbin.warningsAsErrors=true
