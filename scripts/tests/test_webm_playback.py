"""Execute the production WebKit control script against a delayed HTML media element."""
from __future__ import annotations

import json
import re
import shutil
import subprocess
import textwrap
import unittest
from pathlib import Path
from string import Template

ROOT = Path(__file__).resolve().parents[2]
SCRIPT_SOURCE = ROOT / "app-ios/src/commonMain/kotlin/com/orbin/ios/WebMPlayback.kt"


@unittest.skipUnless(shutil.which("node"), "Node is required to execute the production media JavaScript")
class WebMPlaybackScriptTest(unittest.TestCase):
    def test_delayed_document_manual_pause_audio_loop_and_replay(self) -> None:
        source = SCRIPT_SOURCE.read_text(encoding="utf-8")
        template = re.search(r'return """(.*?)"""\.trimIndent\(\)', source, re.DOTALL)
        self.assertIsNotNone(template, "The production script must remain an inspectable raw JavaScript string")
        scripts = []
        for command, play, muted, loop in [
            (1, True, True, True),
            (1, True, False, False),
            (2, True, False, False),
            (3, False, False, False),
            (4, True, True, True),
        ]:
            scripts.append(Template(textwrap.dedent(template.group(1))).substitute(
                command=command,
                play=str(play).lower(),
                muted=str(muted).lower(),
                loop=str(loop).lower(),
            ))
        program = r"""
            const assert = require('node:assert/strict');
            const scripts = JSON.parse(process.argv[1]);
            let video = null;
            global.window = {};
            global.document = {getElementById: () => video};
            const run = index => eval(scripts[index]);
            function media() {
                return {
                    paused: true, ended: false, currentTime: 0, duration: 10,
                    playCalls: 0, pauseCalls: 0,
                    play() { this.playCalls++; this.paused=false; this.ended=false; return Promise.resolve(); },
                    pause() { this.pauseCalls++; this.paused=true; }
                };
            }
            assert.equal(run(0), null, 'A not-yet-loaded DOM must not acknowledge a command');
            video = media();
            run(0);
            assert.equal(video.playCalls, 1, 'The requested play must apply once the element exists');
            video.paused = true;
            run(0);
            run(1);
            assert.equal(video.playCalls, 1, 'Polling or changing audio/loop must preserve manual pause');
            assert.equal(video.paused, true);
            assert.equal(video.muted, false);
            assert.equal(video.loop, false);
            run(2);
            run(2);
            assert.equal(video.playCalls, 2, 'A fresh explicit command plays exactly once');
            run(3);
            assert.equal(video.paused, true);
            assert.equal(video.pauseCalls, 1);
            run(2);
            assert.equal(video.paused, true, 'An older queued command cannot undo a newer pause');
            assert.equal(video.playCalls, 2);
            video.ended = true;
            video.currentTime = 10;
            run(4);
            assert.equal(video.currentTime, 0, 'An explicit replay restarts an ended clip');
            assert.equal(video.playCalls, 3);
            global.window = {};
            video = media();
            run(4);
            assert.equal(video.playCalls, 1, 'A replacement document receives the current command');
        """
        result = subprocess.run(
            [shutil.which("node"), "-e", program, json.dumps(scripts)],
            check=False,
            capture_output=True,
            text=True,
            timeout=10,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
