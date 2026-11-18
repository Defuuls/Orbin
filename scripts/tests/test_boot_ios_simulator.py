from __future__ import annotations

import sys
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

import boot_ios_simulator as boot


def phone(name: str, udid: str, available: bool = True, state: str = "Shutdown") -> dict:
    return {"name": name, "udid": udid, "isAvailable": available, "state": state}


RUNTIME = "com.apple.CoreSimulator.SimRuntime.iOS-"


class ChooseDeviceTest(unittest.TestCase):
    def test_the_newest_runtime_at_or_above_the_minimum_wins(self) -> None:
        listing = {
            "devices": {
                RUNTIME + "26-5": [phone("iPhone 17", "a")],
                RUNTIME + "27-0": [phone("iPhone 17 Pro", "b")],
                RUNTIME + "26-6": [phone("iPhone 17", "c")],
            }
        }

        version, runtime, device = boot.choose_device(listing, (17, 4, 0))

        self.assertEqual((27, 0, 0), version)
        self.assertEqual(RUNTIME + "27-0", runtime)
        self.assertEqual("b", device["udid"])

    def test_a_runtime_below_the_minimum_is_never_chosen(self) -> None:
        listing = {"devices": {RUNTIME + "26-6": [phone("iPhone 17", "a")]}}

        with self.assertRaises(SystemExit) as raised:
            boot.choose_device(listing, (27, 0, 0))

        self.assertIn("iOS 27.0.0 or later", str(raised.exception))

    def test_unavailable_devices_ipads_and_other_platforms_are_skipped(self) -> None:
        listing = {
            "devices": {
                RUNTIME + "27-0": [phone("iPhone 17", "gone", available=False), phone("iPad Pro", "pad")],
                "com.apple.CoreSimulator.SimRuntime.watchOS-12-0": [phone("iPhone-shaped watch", "watch")],
                RUNTIME + "18-0": [phone("iPhone 16", "ok")],
            }
        }

        _, _, device = boot.choose_device(listing, (17, 4, 0))

        self.assertEqual("ok", device["udid"])

    def test_the_minimum_is_inclusive(self) -> None:
        listing = {"devices": {RUNTIME + "17-4": [phone("iPhone 15", "edge")]}}

        _, _, device = boot.choose_device(listing, (17, 4, 0))

        self.assertEqual("edge", device["udid"])


class ParseVersionTest(unittest.TestCase):
    def test_short_versions_are_padded(self) -> None:
        self.assertEqual((27, 0, 0), boot.parse_version("27"))
        self.assertEqual((17, 4, 0), boot.parse_version("17.4"))
        self.assertEqual((17, 4, 1), boot.parse_version("17.4.1"))

    def test_anything_else_is_refused(self) -> None:
        for text in ["", "17.", "a.b", "17.4.1.2", "-1", "17 4"]:
            with self.subTest(text=text), self.assertRaises(ValueError):
                boot.parse_version(text)

    def test_runtime_versions_are_read_from_simctl_identifiers(self) -> None:
        self.assertEqual((26, 5, 0), boot.runtime_version(RUNTIME + "26-5"))
        self.assertEqual((17, 4, 1), boot.runtime_version(RUNTIME + "17-4-1"))
        self.assertIsNone(boot.runtime_version("com.apple.CoreSimulator.SimRuntime.tvOS-27-0"))


if __name__ == "__main__":
    unittest.main()
