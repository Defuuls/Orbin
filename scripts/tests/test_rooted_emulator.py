"""Exercise the actual bounded shell guard with a fake adb transport on PATH."""

import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "ensure_rooted_emulator.sh"
FAKE_ADB = r'''
import os
from pathlib import Path
import sys
import time
import subprocess

mode = os.environ["FAKE_ADB_MODE"]
trace = Path(os.environ["FAKE_ADB_TRACE"])
args = " ".join(sys.argv[1:])
if "FAKE_ADB_PIDS" in os.environ:
    with Path(os.environ["FAKE_ADB_PIDS"]).open("a") as output:
        output.write(str(os.getpid()) + "\n")
with trace.open("a") as output:
    output.write(args + "\n")
if args == "root":
    if mode == "leaky-root":
        child = subprocess.Popen([sys.executable, "-c", "import time; time.sleep(30)"])
        Path(os.environ["FAKE_ADB_CHILD"]).write_text(str(child.pid))
    if mode == "hung-root":
        time.sleep(30)
    if mode in ("closed-root", "unprivileged", "unreachable"):
        print("adb: unable to connect for root: closed", file=sys.stderr)
        sys.exit(1)
    print("adbd is already running as root")
    sys.exit(0)
if args == "wait-for-device":
    if mode == "hung-wait":
        time.sleep(30)
    sys.exit(1 if mode == "unreachable" else 0)
if args == "shell id -u":
    if mode == "hung-uid":
        time.sleep(30)
    if mode == "unreachable":
        sys.exit(1)
    if mode == "closed-root" and trace.read_text().count("shell id -u\n") == 1:
        print("error: closed", file=sys.stderr)
        sys.exit(1)
    if mode == "unprivileged":
        print("2000\r")
    elif mode == "not-exact-zero":
        print(" 0\r")
    elif mode == "failed-zero":
        print("0\r")
        sys.exit(1)
    else:
        print("0\r")
    sys.exit(0)
sys.exit(2)
'''


class RootedEmulatorTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.trace = self.root / "trace.txt"
        adb = self.root / "adb"
        adb.write_text(f"#!{sys.executable}\n" + FAKE_ADB)
        adb.chmod(0o755)

    def run_guard(self, mode, arguments=("1",)):
        environment = dict(os.environ)
        environment.update(PATH=str(self.root) + os.pathsep + environment["PATH"],
                           FAKE_ADB_MODE=mode, FAKE_ADB_TRACE=str(self.trace))
        started = time.monotonic()
        result = subprocess.run(["bash", str(SCRIPT), *arguments], env=environment,
                                capture_output=True, text=True, timeout=4)
        self.assertLess(time.monotonic() - started, 3, "Every adb stage must share the outer deadline")
        return result

    def test_closed_root_exit_one_reconnects_retries_uid_and_accepts_only_root(self):
        result = self.run_guard("closed-root")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Confirmed rooted emulator UID 0", result.stdout)
        calls = self.trace.read_text().splitlines()
        self.assertEqual(["root", "wait-for-device", "shell id -u", "shell id -u"], calls)
        self.assertIn("closed", result.stderr)

    def test_already_rooted_succeeds_with_the_default_production_limit(self):
        result = self.run_guard("already-root", ())
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(["root", "wait-for-device", "shell id -u"], self.trace.read_text().splitlines())

    def test_unprivileged_uid_never_proceeds_even_after_root_command(self):
        result = self.run_guard("unprivileged")
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("Confirmed", result.stdout)
        self.assertIn("refusing to continue", result.stderr)
        self.assertGreaterEqual(self.trace.read_text().count("shell id -u"), 2)

    def test_unreachable_transport_fails_within_the_same_deadline(self):
        result = self.run_guard("unreachable")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("refusing to continue", result.stderr)

    def test_hung_root_reconnect_and_uid_calls_are_all_bounded(self):
        for mode in ("hung-root", "hung-wait", "hung-uid"):
            with self.subTest(mode=mode):
                result = self.run_guard(mode)
                self.assertNotEqual(0, result.returncode)
                self.assertNotIn("Confirmed", result.stdout)

    def test_only_exact_successful_uid_zero_is_accepted(self):
        for mode in ("not-exact-zero", "failed-zero"):
            with self.subTest(mode=mode):
                result = self.run_guard(mode)
                self.assertNotEqual(0, result.returncode)
                self.assertNotIn("Confirmed", result.stdout)

    def test_invalid_limits_fail_before_invoking_adb(self):
        for arguments in (("0",), ("-1",), ("1.5",), ("abc",), ("1", "2")):
            with self.subTest(arguments=arguments):
                result = self.run_guard("already-root", arguments)
                self.assertEqual(2, result.returncode)
                self.assertIn("Usage", result.stderr)
                self.assertFalse(self.trace.exists())


    def test_leader_only_timeout_context_cannot_leave_adb_descendants_holding_pipes(self):
        # Models the observed CI failure boundary without claiming its timeout binary's identity.
        # The old guard fails this exact capture deadline after printing its refusal message.
        timeout = self.root / "timeout"
        timeout.write_text(f"#!{sys.executable}\n" + """import subprocess,sys
worker = subprocess.Popen(sys.argv[3:])
try:
    worker.wait(timeout=float(sys.argv[2].removesuffix('s')))
except subprocess.TimeoutExpired:
    worker.kill()
    worker.wait()
    sys.exit(137)
sys.exit(worker.returncode)
""")
        timeout.chmod(0o755)
        bash_env = self.root / "bash-env"
        bash_env.write_text("set -m\n")
        for mode in ("hung-root", "hung-uid"):
            environment = dict(os.environ)
            environment.update(PATH=str(self.root) + os.pathsep + environment["PATH"],
                               FAKE_ADB_MODE=mode, FAKE_ADB_TRACE=str(self.trace), BASH_ENV=str(bash_env),
                               FAKE_ADB_PIDS=str(self.root / (mode + "-pids")))
            started = time.monotonic()
            process = subprocess.Popen(["bash", str(SCRIPT), "1"], env=environment,
                                       stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
                                       start_new_session=True)
            try:
                stdout, _ = process.communicate(timeout=4)
                self.assertNotEqual(0, process.returncode)
                self.assertNotIn("Confirmed", stdout)
                self.assertLess(time.monotonic() - started, 3)
            finally:
                # Also clean a broken implementation's escaped job-control child on assertion failure.
                pids = self.root / (mode + "-pids")
                if pids.exists():
                    for pid in pids.read_text().splitlines():
                        try:
                            os.kill(int(pid), signal.SIGKILL)
                        except ProcessLookupError:
                            pass
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                process.communicate()

    def test_successful_root_confirmation_also_cleans_orphaned_adb_children(self):
        environment = dict(os.environ)
        child = self.root / "child-pid"
        environment.update(PATH=str(self.root) + os.pathsep + environment["PATH"],
                           FAKE_ADB_MODE="leaky-root", FAKE_ADB_TRACE=str(self.trace), FAKE_ADB_CHILD=str(child))
        started = time.monotonic()
        process = subprocess.Popen(["bash", str(SCRIPT), "1"], env=environment,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
                                   start_new_session=True)
        try:
            stdout, stderr = process.communicate(timeout=4)
            self.assertEqual(0, process.returncode, stderr)
            self.assertIn("Confirmed rooted emulator UID 0", stdout)
            self.assertTrue(child.exists(), "Fake adb must actually fork the pipe-holding child")
            self.assertLess(time.monotonic() - started, 3)
        finally:
            if child.exists():
                try:
                    os.kill(int(child.read_text()), signal.SIGKILL)
                except ProcessLookupError:
                    pass
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            process.communicate()


if __name__ == "__main__":
    unittest.main()
