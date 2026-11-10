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
        self.run_production_script(r"""
            assert.equal(run(0), null, 'A delayed DOM must not acknowledge a command');
            video = media();
            run(0);
            await flush();
            assert.equal(video.playCalls, 1);
            video.paused = true;
            run(0);
            run(1);
            await flush();
            assert.equal(video.playCalls, 1, 'Polling and audio/loop changes preserve manual pause');
            assert.equal(video.paused, true);
            assert.equal(video.muted, false);
            assert.equal(video.loop, false);
            run(2);
            run(2);
            await flush();
            assert.equal(video.playCalls, 2, 'One current pending command starts only one play attempt');
            run(3);
            assert.equal(video.paused, true);
            assert.equal(video.pauseCalls, 1);
            run(2);
            assert.equal(video.paused, true, 'An older queued command cannot undo a newer pause');
            video.ended = true;
            video.currentTime = 10;
            run(4);
            await flush();
            assert.equal(video.currentTime, 0, 'Explicit replay restarts an ended clip');
            assert.equal(video.playCalls, 3);
            global.window = {};
            video = media();
            run(4);
            await flush();
            assert.equal(video.playCalls, 1, 'A replacement document receives the current command');
        """)

    def test_rejected_play_is_retried_then_applied_once(self) -> None:
        self.run_production_script(r"""
            video = media();
            const initial = deferred();
            video.playResult = () => initial.promise;
            run(0);
            run(0);
            run(1);
            assert.equal(video.playCalls, 1, 'Pending playback does not start duplicate attempts');
            assert.equal(window.__orbinPlaybackApplied, undefined, 'Starting play is not success');
            initial.reject(Object.assign(new Error('not attached yet'), {name: 'NotAllowedError'}));
            await flush();
            assert.equal(window.__orbinPlaybackPending, null);
            assert.equal(window.__orbinPlaybackApplied, undefined);
            assert.match(window.__orbinPlaybackLastRejection, /NotAllowedError: not attached yet/);
            video.playResult = () => Promise.resolve();
            run(0);
            assert.equal(video.playCalls, 2, 'The same rejected command retries after mounting');
            assert.equal(window.__orbinPlaybackApplied, undefined);
            await flush();
            assert.equal(window.__orbinPlaybackApplied, 1);
            assert.equal(window.__orbinPlaybackPending, null);
            assert.equal(window.__orbinPlaybackLastRejection, null);
            video.paused = true;
            run(0);
            run(1);
            await flush();
            assert.equal(video.playCalls, 2, 'A fulfilled request preserves a later manual pause');
            assert.equal(video.paused, true);
            video.playResult = () => { throw new Error('temporary synchronous failure'); };
            run(2);
            assert.equal(window.__orbinPlaybackApplied, 1);
            assert.equal(window.__orbinPlaybackPending, null);
            video.playResult = () => Promise.resolve();
            run(2);
            await flush();
            assert.equal(window.__orbinPlaybackApplied, 2, 'Synchronous play failures also retry');
        """)

    def test_stale_play_completions_cannot_replace_newer_play_or_pause(self) -> None:
        self.run_production_script(r"""
            video = media();
            const first = deferred(), second = deferred();
            const attempts = [first, second];
            video.playResult = () => attempts.shift().promise;
            run(0);
            run(2);
            const newest = window.__orbinPlaybackPending;
            assert.equal(video.playCalls, 2);
            first.resolve();
            await flush();
            assert.equal(window.__orbinPlaybackPending, newest, 'Old success cannot clear newer pending play');
            assert.equal(window.__orbinPlaybackApplied, undefined);
            assert.equal(video.paused, false, 'Old success cannot pause a newer play request');
            run(3);
            assert.equal(video.paused, true);
            video.paused = false; // Simulate an old native play completing after the newer pause.
            second.resolve();
            await flush();
            assert.equal(video.paused, true, 'Late play fulfillment must preserve the newest pause');
            assert.equal(window.__orbinPlaybackApplied, 3);
            assert.equal(window.__orbinPlaybackPending, null);

            global.window = {};
            video = media();
            const old = deferred(), current = deferred();
            const rejectedAttempts = [old, current];
            video.playResult = () => rejectedAttempts.shift().promise;
            run(0);
            run(3);
            run(4);
            const currentPending = window.__orbinPlaybackPending;
            old.reject(new Error('obsolete request'));
            await flush();
            assert.equal(window.__orbinPlaybackPending, currentPending, 'Old rejection cannot clear newer play');
            assert.equal(window.__orbinPlaybackApplied, 3);
            assert.equal(window.__orbinPlaybackLastRejection, undefined);
            current.resolve();
            await flush();
            assert.equal(window.__orbinPlaybackApplied, 4);
            assert.equal(video.paused, false);

            global.window = {};
            video = media();
            const rejected = deferred();
            video.playResult = () => rejected.promise;
            run(0);
            run(3);
            rejected.reject(new Error('aborted by pause'));
            await flush();
            run(0);
            assert.equal(window.__orbinPlaybackApplied, 3);
            assert.equal(video.paused, true, 'Old rejection and stale polling cannot undo pause');
            assert.equal(video.playCalls, 1);
        """)

    def test_latest_intent_is_recorded_before_document_readiness(self) -> None:
        self.run_production_script(r"""
            run(3);
            assert.deepEqual(window.__orbinPlaybackRequested, {command: 3, play: false});
            assert.equal(window.__orbinPlaybackApplied, undefined, 'Missing DOM cannot acknowledge pause');
            run(2);
            assert.deepEqual(window.__orbinPlaybackRequested, {command: 3, play: false});
            video = media();
            run(2);
            await flush();
            assert.equal(video.playCalls, 0, 'Older play cannot undo a pause sent before DOM readiness');
            assert.equal(video.paused, true);
            run(3);
            assert.equal(window.__orbinPlaybackApplied, 3);
            assert.equal(video.pauseCalls, 1);
            global.window = {};
            video = null;
            run(4);
            video = media();
            run(3);
            assert.equal(video.pauseCalls, 0, 'Older pause cannot replace a newer pre-DOM play request');
            assert.equal(window.__orbinPlaybackApplied, undefined);
            run(4);
            await flush();
            assert.equal(video.playCalls, 1);
            assert.equal(window.__orbinPlaybackApplied, 4);
        """)

    def run_production_script(self, checks: str) -> None:
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
        setup = r"""
            const assert = require('node:assert/strict');
            const scripts = JSON.parse(process.argv[1]);
            let video = null;
            global.window = {};
            global.document = {getElementById: () => video};
            const run = index => eval(scripts[index]);
            const flush = async () => { await Promise.resolve(); await Promise.resolve(); };
            function deferred() {
                let resolve, reject;
                const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
                return {promise, resolve, reject};
            }
            function media() {
                return {
                    paused: true, ended: false, currentTime: 0, duration: 10,
                    playCalls: 0, pauseCalls: 0,
                    playResult: () => Promise.resolve(),
                    play() { this.playCalls++; this.paused=false; this.ended=false; return this.playResult(); },
                    pause() { this.pauseCalls++; this.paused=true; }
                };
            }
        """
        program = setup + "\n(async () => {\n" + checks + "\n})().catch(error => {console.error(error); process.exit(1);});"
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
