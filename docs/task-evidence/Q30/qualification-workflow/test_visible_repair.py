"""Focused pure checks for the bounded headed-visible Q30 driver."""
from __future__ import annotations

import os
from pathlib import Path
import tempfile
import types
import unittest


DRIVER_PATH = Path(__file__).with_name("visible_repair.py")
DRIVER = types.ModuleType("visible_repair_test_target")
DRIVER.__file__ = str(DRIVER_PATH)
exec(compile(DRIVER_PATH.read_text(encoding="utf-8"), str(DRIVER_PATH), "exec"), DRIVER.__dict__)


class InputInventoryTests(unittest.TestCase):
    def make_fixture(self, root: Path) -> tuple[Path, Path, Path]:
        app = root / "private-app"
        fixture_files = {
            "src/example/Board.java": b"class Board {}\n",
            "war/circuitjs1/circuitjs1.nocache.js": b"compiled bundle\n",
            "war/WEB-INF/deploy/manifest.txt": b"deployment\n",
            "war/circuitjs.html": b"<!doctype html>\n",
            "war/tsj-workbench-ui.js": b"window.workbench = true;\n",
            "war/tsj-workbench-ui.css": b".workbench { display: block; }\n",
            "scripts/preview.ps1": b"# preview\n",
            "scripts/VerifierIsolation.psm1": b"# isolation\n",
        }
        for relative, content in fixture_files.items():
            path = app / Path(relative)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
        return app, app / "war/tsj-workbench-ui.js", app / "war/tsj-workbench-ui.css"

    def test_inventory_hash_covers_both_player_web_assets(self):
        temporary = tempfile.mkdtemp(prefix="q30-visible-repair-inventory-")
        self.evidence = Path(temporary)
        print("retained web-inventory fixture: " + temporary, flush=True)
        app, ui_js, ui_css = self.make_fixture(Path(temporary))
        baseline = DRIVER.hash_inputs(app)
        inventory_paths = {row["path"] for row in baseline["files"]}
        self.assertIn("war/tsj-workbench-ui.js", inventory_paths)
        self.assertIn("war/tsj-workbench-ui.css", inventory_paths)

        ui_js.write_bytes(ui_js.read_bytes() + b"// changed JS\n")
        after_js_change = DRIVER.hash_inputs(app)
        self.assertNotEqual(baseline["sha256"], after_js_change["sha256"])

        ui_css.write_bytes(ui_css.read_bytes() + b"/* changed CSS */\n")
        after_css_change = DRIVER.hash_inputs(app)
        self.assertNotEqual(after_js_change["sha256"], after_css_change["sha256"])

class MeterParserTests(unittest.TestCase):
    def test_voltage_units_are_converted_to_volts(self):
        readings = (
            ("2.16 \u03bcV", 2.16e-6),
            ("2.16 \u00b5V", 2.16e-6),
            ("2.16 uV", 2.16e-6),
            ("3.3 mV", 0.0033),
            ("11.983 V", 11.983),
        )
        for display, expected_volts in readings:
            with self.subTest(display=display):
                text, volts = DRIVER.meter_value(
                    display + "\nBoard Power: ON\nSupply 1: 12 V")
                self.assertEqual(text, display)
                self.assertAlmostEqual(volts, expected_volts, delta=1e-12)

    def test_missing_voltage_is_not_a_reading(self):
        with self.assertRaises(ValueError):
            DRIVER.meter_value("UNKNOWN\nBoard Power: OFF")


@unittest.skipUnless(os.name == "nt", "requires native Windows Toolhelp process APIs")
class ToolhelpProcessTableTests(unittest.TestCase):
    def test_process_rows_are_complete_and_include_this_process(self):
        rows = DRIVER.process_table()
        self.assertTrue(rows)
        own_pid = os.getpid()
        by_pid = {row["pid"]: row for row in rows}
        self.assertIn(own_pid, by_pid)
        self.assertIn("python", by_pid[own_pid]["imageName"].casefold())
        for row in rows:
            with self.subTest(pid=row.get("pid")):
                self.assertIs(type(row.get("pid")), int)
                self.assertGreaterEqual(row["pid"], 0)  # Toolhelp includes the System Idle Process (PID0).
                self.assertIs(type(row.get("parentPid")), int)
                self.assertGreaterEqual(row["parentPid"], 0)
                self.assertIsInstance(row.get("imageName"), str)
                self.assertTrue(row["imageName"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
