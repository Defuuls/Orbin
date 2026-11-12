"""Exercise the actual bounded shell guard with a fake adb transport on PATH."""

import os
from pathlib import Path
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

mode = os.environ["FAKE_ADB_MODE"]
trace = Path(os.environ["FAKE_ADB_TRACE"])
args = " ".join(sys.argv[1:])
with trace.open("a") as output:
    output.write(args + "\n")
if args == "root":
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


if __name__ == "__main__":
    unittest.main()
