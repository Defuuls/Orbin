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
    def test_requested_play_is_not_display_ready_until_playing_event(self) -> None:
        self.run_production_script(r"""
            video = media();
            video.readyState = 2;
            const pending = deferred();
            video.playResult = () => pending.promise;
            const requested = run(0).split(',');
            assert.equal(video.paused, false, 'play() can clear paused before any frame has been played');
            assert.equal(requested[4], '1', 'A pending initial play remains buffering');
            assert.equal(requested[5], '0', 'paused=false does not acknowledge the first frame');
            video.emit('canplay');
            video.emit('timeupdate');
            assert.equal(run(0).split(',')[5], '0', 'Readiness events before playing cannot expose the player');
            pending.resolve();
            await flush();
            assert.equal(run(0).split(',')[5], '0', 'A fulfilled play promise alone is not a rendered frame');
            // HAVE_CURRENT_DATA (2) stays loading so upcoming work cannot compete with an underbuffered clip.
            assert.equal(run(0).split(',')[4], '1');
            video.readyState = 3;
            video.emit('playing');
            const playing = run(0).split(',');
            assert.equal(playing[4], '0');
            assert.equal(playing[5], '1', 'Playing with future data reveals the playback surface');
            assert.equal(video.playCalls, 1, 'Readiness polling must not recreate or restart playback');
            for (const callbacks of video.listeners.values()) {
                assert.equal(callbacks.length, 1, 'Polling installs each media event listener only once');
            }
        """)

    def test_buffering_and_seeking_hide_the_surface_until_playback_recovers(self) -> None:
        self.run_production_script(r"""
            video = media();
            video.readyState = 3;
            run(0);
            await flush();
            video.emit('playing');
            assert.deepEqual(run(0).split(',').slice(4), ['0', '1']);

            video.emit('stalled');
            assert.deepEqual(
                run(0).split(',').slice(4), ['0', '1'],
                'A network stalled event does not hide frames while enough future data remains buffered'
            );
            video.emit('waiting');
            assert.deepEqual(run(0).split(',').slice(4), ['1', '0'], 'An actual waiting event blocks readiness');
            video.emit('timeupdate');
            assert.deepEqual(run(0).split(',').slice(4), ['0', '1']);

            for (const event of ['waiting', 'stalled']) {
                video.readyState = 2;
                video.emit(event);
                assert.deepEqual(run(0).split(',').slice(4), ['1', '0'], event + ' keeps the poster visible');
                video.readyState = 3;
                video.emit('timeupdate');
                assert.deepEqual(run(0).split(',').slice(4), ['0', '1'], 'Progress after ' + event + ' restores readiness');
            }

            video.seeking = true;
            video.emit('seeking');
            assert.deepEqual(run(0).split(',').slice(4), ['1', '0'], 'Seeking is a buffering transition');
            video.seeking = false;
            video.readyState = 3;
            video.emit('seeked');
            video.emit('timeupdate');
            assert.deepEqual(run(0).split(',').slice(4), ['0', '1']);

            video.readyState = 0;
            video.emit('emptied');
            assert.deepEqual(run(0).split(',').slice(4), ['1', '0'], 'An emptied source has no current frame');
            assert.equal(video.playCalls, 1, 'Recovering readiness must reuse the same playback command');
        """)

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

    def test_same_command_speed_changes_preserve_pending_ack_and_manual_pause(self) -> None:
        self.run_production_script(r"""
            video = media();
            video.currentTime = 4.5;
            const first = deferred();
            video.playResult = () => first.promise;
            run(0);
            const pending = window.__orbinPlaybackPending;
            run(5);
            assert.equal(video.playbackRate, 1.25, 'The current pending command accepts a speed update');
            assert.equal(window.__orbinPlaybackPending, pending, 'Speed does not replace pending playback');
            assert.equal(window.__orbinPlaybackApplied, undefined, 'A speed update is not a play acknowledgement');
            assert.equal(video.playCalls, 1);
            first.resolve();
            await flush();
            assert.equal(window.__orbinPlaybackApplied, 1);
            assert.equal(window.__orbinPlaybackPending, null);

            video.paused = true; // Native controls can pause after the play promise is acknowledged.
            run(6);
            assert.equal(video.playbackRate, 2);
            assert.equal(video.paused, true, 'Changing speed never resumes a manual pause');
            assert.equal(video.currentTime, 4.5, 'Changing speed retains the selected playback position');
            assert.equal(video.playCalls, 1);
            assert.equal(window.__orbinPlaybackApplied, 1, 'The existing acknowledgement remains valid');

            run(3);
            run(7);
            assert.equal(video.playbackRate, 1.25, 'An acknowledged pause also accepts a new rate');
            assert.equal(video.paused, true);
            assert.equal(video.pauseCalls, 1, 'Polling a paused rate update does not issue another command');
            assert.equal(video.playCalls, 1);
            assert.equal(window.__orbinPlaybackApplied, 3);
        """)

    def run_production_script(self, checks: str) -> None:
        source = SCRIPT_SOURCE.read_text(encoding="utf-8")
        template = re.search(r'return """(.*?)"""\.trimIndent\(\)', source, re.DOTALL)
        self.assertIsNotNone(template, "The production script must remain an inspectable raw JavaScript string")
        readiness = re.search(r'private const val WEBM_READINESS_SCRIPT = """(.*?)"""', source, re.DOTALL)
        self.assertIsNotNone(readiness, "The production readiness events must remain inspectable")
        scripts = []
        for command, play, muted, loop, speed in [
            (1, True, True, True, 1.0),
            (1, True, False, False, 1.0),
            (2, True, False, False, 1.0),
            (3, False, False, False, 1.0),
            (4, True, True, True, 1.0),
            (1, True, False, False, 1.25),
            (1, True, False, False, 2.0),
            (3, False, False, False, 1.25),
        ]:
            scripts.append(Template(textwrap.dedent(template.group(1))).substitute(
                command=command,
                play=str(play).lower(),
                muted=str(muted).lower(),
                loop=str(loop).lower(),
                speed=speed,
                WEBM_READINESS_SCRIPT=readiness.group(1),
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
                    paused: true, ended: false, currentTime: 0, duration: 10, playbackRate: 1.0,
                    readyState: 0, seeking: false, listeners: new Map(),
                    playCalls: 0, pauseCalls: 0,
                    playResult: () => Promise.resolve(),
                    play() { this.playCalls++; this.paused=false; this.ended=false; return this.playResult(); },
                    pause() { this.pauseCalls++; this.paused=true; },
                    addEventListener(name, callback) {
                        const callbacks = this.listeners.get(name) || [];
                        callbacks.push(callback);
                        this.listeners.set(name, callbacks);
                    },
                    emit(name) {
                        for (const callback of this.listeners.get(name) || []) {
                            callback.call(this, {type: name, target: this});
                        }
                    }
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
