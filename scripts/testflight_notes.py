#!/usr/bin/env python3
"""Fill a TestFlight build's "What to Test" from CHANGELOG.md.

The TestFlight workflow runs this after uploading, so testers read what changed in the TestFlight
app itself: the build's release section of the changelog ("## [158-Yuzu]"), or the Unreleased
section for a manual build made between releases, as plain text.

    testflight_notes.py --version-name 158-Yuzu --marketing 158 --build 176.202609262351 \
        --bundle-id io.github.defuuls.orbin

It signs its App Store Connect requests with the upload's API key: KEY_ID, ISSUER_ID and the .p8
file at API_KEY_PATH, from the environment. A new build takes Apple a few minutes to register, so
it waits for the build to appear before writing its notes.

`--print` only prints the notes it would write, with no network and no key, for checking locally.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CHANGELOG = ROOT / "CHANGELOG.md"
API = "https://api.appstoreconnect.apple.com/v1"

# App Store Connect's limit for "What to Test".
MAX_NOTES = 4000
# How long to wait for an uploaded build to show up in App Store Connect.
BUILD_WAIT_SECONDS = 30 * 60
BUILD_POLL_SECONDS = 30

SECTION = re.compile(r"^## \[(?P<name>[^\]]+)\]")
LINK = re.compile(r"\[([^\]]+)\]\([^)]+\)")


def changelog_section(changelog: str, name: str) -> str | None:
    """The body of the changelog section headed "## [name]", or None if there is none."""
    lines = changelog.splitlines()
    body: list[str] | None = None
    for line in lines:
        heading = SECTION.match(line)
        if heading:
            if body is not None:
                break
            if heading.group("name") == name:
                body = []
            continue
        if body is not None:
            body.append(line)
    if body is None:
        return None
    text = "\n".join(body).strip()
    return text or None


def plain(markdown: str) -> str:
    """Changelog Markdown as the plain text TestFlight shows: headings, bullets, no markup."""
    out: list[str] = []
    for line in markdown.splitlines():
        line = line.rstrip()
        if line.startswith("### "):
            if out and out[-1] != "":
                out.append("")
            out.append(line[4:].strip() + ":")
            continue
        if line.startswith("- "):
            line = "• " + line[2:]
        line = LINK.sub(r"\1", line).replace("**", "").replace("`", "")
        if line or (out and out[-1] != ""):
            out.append(line)
    return "\n".join(out).strip()


def notes_for(changelog: str, version_name: str) -> str:
    """What to Test for a build of version_name: its release section, else what is unreleased."""
    section = changelog_section(changelog, version_name)
    if section is not None:
        text = f"Orbin {version_name}\n\n{plain(section)}"
    else:
        unreleased = changelog_section(changelog, "Unreleased")
        if unreleased is None:
            text = f"Orbin {version_name}"
        else:
            text = f"Orbin {version_name}, with changes not yet released:\n\n{plain(unreleased)}"
    return truncate(text)


def truncate(text: str, limit: int = MAX_NOTES) -> str:
    """Cut to the limit at a line boundary, marking the cut."""
    if len(text) <= limit:
        return text
    cut = text[: limit - 2]
    if "\n" in cut:
        cut = cut[: cut.rindex("\n")]
    return cut.rstrip() + "\n…"


def token(key_id: str, issuer_id: str, key_path: str) -> str:
    """A 20-minute App Store Connect API token."""
    import jwt  # PyJWT with its crypto extra, installed by the workflow.

    now = int(time.time())
    return jwt.encode(
        {"iss": issuer_id, "iat": now, "exp": now + 20 * 60, "aud": "appstoreconnect-v1"},
        Path(key_path).read_text(encoding="utf-8"),
        algorithm="ES256",
        headers={"kid": key_id, "typ": "JWT"},
    )


def request(method: str, path: str, bearer: str, body: dict | None = None) -> dict:
    url = path if path.startswith("https://") else API + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", f"Bearer {bearer}")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=60) as response:
        payload = response.read()
    return json.loads(payload) if payload else {}


def find_build(bearer: str, bundle_id: str, marketing: str, build: str) -> str:
    """The App Store Connect id of the build just uploaded, waiting for it to register."""
    query = urllib.parse.urlencode({"filter[bundleId]": bundle_id, "limit": 1})
    apps = request("GET", f"/apps?{query}", bearer)["data"]
    if not apps:
        raise SystemExit(f"No App Store Connect app has bundle id {bundle_id}.")
    app_id = apps[0]["id"]
    query = urllib.parse.urlencode(
        {
            "filter[app]": app_id,
            "filter[version]": build,
            "filter[preReleaseVersion.version]": marketing,
            "limit": 1,
        }
    )
    deadline = time.monotonic() + BUILD_WAIT_SECONDS
    while True:
        builds = request("GET", f"/builds?{query}", bearer)["data"]
        if builds:
            return builds[0]["id"]
        if time.monotonic() > deadline:
            raise SystemExit(f"Build {build} did not appear in App Store Connect in time.")
        time.sleep(BUILD_POLL_SECONDS)


def write_notes(bearer: str, build_id: str, notes: str, locale: str = "en-US") -> None:
    """Set the build's What to Test in [locale], replacing any notes already there."""
    existing = request("GET", f"/builds/{build_id}/betaBuildLocalizations", bearer)["data"]
    match = next((item for item in existing if item["attributes"].get("locale") == locale), None)
    if match is not None:
        request(
            "PATCH",
            f"/betaBuildLocalizations/{match['id']}",
            bearer,
            {"data": {"type": "betaBuildLocalizations", "id": match["id"], "attributes": {"whatsNew": notes}}},
        )
    else:
        request(
            "POST",
            "/betaBuildLocalizations",
            bearer,
            {
                "data": {
                    "type": "betaBuildLocalizations",
                    "attributes": {"locale": locale, "whatsNew": notes},
                    "relationships": {"build": {"data": {"type": "builds", "id": build_id}}},
                }
            },
        )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--version-name", required=True, help='The release, "158-Yuzu".')
    parser.add_argument("--marketing", help='TestFlight\'s version, "158".')
    parser.add_argument("--build", help='TestFlight\'s build number, "176.202609262351".')
    parser.add_argument("--bundle-id")
    parser.add_argument("--print", action="store_true", help="Only print the notes.")
    args = parser.parse_args()

    notes = notes_for(CHANGELOG.read_text(encoding="utf-8"), args.version_name)
    if args.print:
        print(notes)
        return 0
    if not (args.marketing and args.build and args.bundle_id):
        parser.error("--marketing, --build and --bundle-id are needed unless --print")

    bearer = token(os.environ["KEY_ID"], os.environ["ISSUER_ID"], os.environ["API_KEY_PATH"])
    try:
        build_id = find_build(bearer, args.bundle_id, args.marketing, args.build)
        # The wait can outlast a token; sign a fresh one to write.
        bearer = token(os.environ["KEY_ID"], os.environ["ISSUER_ID"], os.environ["API_KEY_PATH"])
        write_notes(bearer, build_id, notes)
    except urllib.error.HTTPError as error:
        detail = error.read().decode(errors="replace")
        raise SystemExit(f"App Store Connect refused the request ({error.code}): {detail}") from error
    print(f"What to Test set for build {args.build}:\n\n{notes}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
