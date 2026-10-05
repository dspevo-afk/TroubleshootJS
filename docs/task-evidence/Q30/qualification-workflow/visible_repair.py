"""Bounded, headed Q30 player-flow evidence through ordinary Playwright input.

The driver never evaluates page JavaScript or calls a game/controller API.
It launches only the supplied private production preview and its own isolated
Edge profile. The parent must bind --app to a freshly built private source root.
"""
from __future__ import annotations

import argparse
import ctypes
from ctypes import wintypes
from datetime import datetime, timezone
import hashlib
import importlib.metadata
import importlib.util
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import sys
import tempfile
import time
import traceback
from urllib.request import urlopen


TOTAL_DEADLINE_SECONDS = 20 * 60
PREVIEW_READY_SECONDS = 60
TICKET_OBSERVATION_MS = 125_000
CONTROL_READY_SECONDS = 180
VOLTAGE_RE = re.compile(r"([-+]?(?:\d+(?:\.\d*)?|\.\d+))\s*(\u03bcV|\u00b5V|uV|mV|V)\b")
REPARSE_POINT = 0x400

SUPPLIES_3 = [(1, "+12V"), (2, "+5V"), (3, "+12V")]
CASES = [
    {
        "seed": 10387,
        "size": 20,
        "component": "KA",
        "replacementCategory": "Relays",
        "acquire": "Add to Parts Tray: Relays, 5 V coil / SPDT / 24 V DC, 0.5 A contacts",
        "part": "Part 21 - 5 V coil / SPDT / 24 V DC, 0.5 A contacts",
        "seat": "Output A / KA",
        "supplies": SUPPLIES_3,
        "outputs": ["JOA"],
        "faultStates": ["Set sensor A HIGH"],
        "healthyStates": [
            ("Set sensor A LOW", {"JOA": "LOW"}),
            ("Set sensor A HIGH", {"JOA": "HIGH"}),
        ],
    },
    {
        "seed": 10226,
        "size": 30,
        "component": "DREV",
        "replacementCategory": "Diodes",
        "acquire": "Add to Parts Tray: Diodes, Generic silicon diode - Narrow lead spacing",
        "part": "Part 31 - Generic silicon diode",
        "seat": "12 V entry / DREV",
        "supplies": SUPPLIES_3,
        "outputs": ["JOA"],
        "faultStates": ["Set sensor A HIGH"],
        "healthyStates": [
            ("Set sensor A LOW", {"JOA": "LOW"}),
            ("Set sensor A HIGH", {"JOA": "HIGH"}),
        ],
    },
    {
        "seed": 10014,
        "size": 40,
        "component": "REN",
        "replacementCategory": "Resistors",
        "search": "10000",
        "acquire": "Add to Parts Tray: Resistors, 10000 Ohm +/-5% - Wide lead spacing",
        "part": "Part 41 - 10000 Ohm +/-5%",
        "seat": "5 V regulation / REN",
        "supplies": [(1, "+12V"), (2, "+5V"), (3, "+5V"), (4, "+12V")],
        "outputs": ["JOA", "JOB"],
        "faultStates": ["Set both sensors HIGH"],
        "healthyStates": [
            ("Set both sensors LOW", {"JOA": "LOW", "JOB": "LOW"}),
            ("Set sensor A HIGH, B LOW", {"JOA": "HIGH", "JOB": "LOW"}),
            ("Set sensor A LOW, B HIGH", {"JOA": "LOW", "JOB": "HIGH"}),
            ("Set both sensors HIGH", {"JOA": "HIGH", "JOB": "HIGH"}),
        ],
    },
]


class FlowFailure(RuntimeError):
    pass


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def write_json(path: Path, value) -> None:
    temp_path = path.with_suffix(path.suffix + ".tmp")
    temp_path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.replace(temp_path, path)


def is_reparse(path: Path) -> bool:
    try:
        return bool(path.lstat().st_file_attributes & REPARSE_POINT)
    except AttributeError:
        return path.is_symlink()


def hash_inputs(app: Path) -> dict:
    """Hash actual raw sources, compiled GWT output and preview inputs."""
    scopes = (
        "src",
        "war",
        "scripts/preview.ps1",
        "scripts/VerifierIsolation.psm1",
    )
    entries = []
    for relative in scopes:
        scope = app / Path(relative)
        if not scope.exists() or is_reparse(scope):
            raise FlowFailure("Input scope is missing or a reparse point: " + relative)
        if scope.is_file():
            paths = [scope]
        else:
            paths = []
            pending = [scope]
            while pending:
                directory = pending.pop()
                for item in sorted(directory.iterdir(), key=lambda p: p.name.casefold()):
                    if is_reparse(item):
                        raise FlowFailure("Reparse point inside input inventory: " + str(item))
                    if item.is_dir():
                        pending.append(item)
                    elif item.is_file():
                        paths.append(item)
                    else:
                        raise FlowFailure("Unsupported filesystem entry in input inventory: " + str(item))
        for path in paths:
            digest = hashlib.sha256()
            size = 0
            with path.open("rb") as stream:
                for block in iter(lambda: stream.read(1024 * 1024), b""):
                    digest.update(block)
                    size += len(block)
            entries.append({
                "scope": relative,
                "path": path.relative_to(app).as_posix(),
                "bytes": size,
                "sha256": digest.hexdigest(),
            })
    entries.sort(key=lambda row: (row["scope"], row["path"]))
    encoded = json.dumps(entries, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return {
        "status": "PASS",
        "scopes": list(scopes),
        "fileCount": len(entries),
        "totalBytes": sum(row["bytes"] for row in entries),
        "sha256": hashlib.sha256(encoded).hexdigest(),
        "files": entries,
    }


def load_identity_helper():
    helper_path = Path(__file__).with_name("windows_process_identity.py")
    if not helper_path.is_file() or is_reparse(helper_path):
        raise FlowFailure("Maintained native Windows identity helper is unavailable.")
    spec = importlib.util.spec_from_file_location("visible_repair_process_identity", helper_path)
    if spec is None or spec.loader is None:
        raise FlowFailure("Could not load maintained native Windows identity helper.")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module, hashlib.sha256(helper_path.read_bytes()).hexdigest()


def native_identity(identity_api, pid: int) -> dict:
    query = identity_api.query_process(pid)
    if query.get("queryStatus") != "PRESENT":
        raise FlowFailure("Native process identity query was not PRESENT for PID " + str(pid))
    value = query.get("identity")
    if (not isinstance(value, dict) or value.get("pid") != pid or
            type(value.get("creationFileTimeTicks")) is not int or
            value.get("birthPrecision") != identity_api.NATIVE_100NS or
            not value.get("executable")):
        raise FlowFailure("Native PID, birth, or image identity was incomplete for PID " + str(pid))
    return value


class PROCESSENTRY32W(ctypes.Structure):
    _fields_ = [
        ("dwSize", wintypes.DWORD),
        ("cntUsage", wintypes.DWORD),
        ("th32ProcessID", wintypes.DWORD),
        ("th32DefaultHeapID", ctypes.c_size_t),
        ("th32ModuleID", wintypes.DWORD),
        ("cntThreads", wintypes.DWORD),
        ("th32ParentProcessID", wintypes.DWORD),
        ("pcPriClassBase", ctypes.c_long),
        ("dwFlags", wintypes.DWORD),
        ("szExeFile", wintypes.WCHAR * 260),
    ]


def process_table() -> list[dict]:
    """Read PID-parent-image rows using Toolhelp; this function never mutates processes."""
    if os.name != "nt":
        raise FlowFailure("Visible Q30 driver requires Windows.")
    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel32.CreateToolhelp32Snapshot.restype = wintypes.HANDLE
    kernel32.CreateToolhelp32Snapshot.argtypes = [wintypes.DWORD, wintypes.DWORD]
    kernel32.Process32FirstW.restype = wintypes.BOOL
    kernel32.Process32FirstW.argtypes = [wintypes.HANDLE, ctypes.POINTER(PROCESSENTRY32W)]
    kernel32.Process32NextW.restype = wintypes.BOOL
    kernel32.Process32NextW.argtypes = [wintypes.HANDLE, ctypes.POINTER(PROCESSENTRY32W)]
    kernel32.CloseHandle.restype = wintypes.BOOL
    kernel32.CloseHandle.argtypes = [wintypes.HANDLE]
    snapshot = kernel32.CreateToolhelp32Snapshot(0x00000002, 0)
    invalid = ctypes.c_void_p(-1).value
    if not snapshot or snapshot == invalid:
        raise OSError(ctypes.get_last_error(), "CreateToolhelp32Snapshot failed")
    rows = []
    try:
        entry = PROCESSENTRY32W()
        entry.dwSize = ctypes.sizeof(PROCESSENTRY32W)
        ctypes.set_last_error(0)
        ok = kernel32.Process32FirstW(snapshot, ctypes.byref(entry))
        while ok:
            rows.append({
                "pid": int(entry.th32ProcessID),
                "parentPid": int(entry.th32ParentProcessID),
                "imageName": str(entry.szExeFile),
            })
            entry.dwSize = ctypes.sizeof(PROCESSENTRY32W)
            ctypes.set_last_error(0)
            ok = kernel32.Process32NextW(snapshot, ctypes.byref(entry))
        enumeration_error = ctypes.get_last_error()
        if enumeration_error != 18:  # ERROR_NO_MORE_FILES is the sole normal end.
            raise OSError(enumeration_error, "Toolhelp process snapshot ended incompletely")
    finally:
        if not kernel32.CloseHandle(snapshot):
            raise OSError(ctypes.get_last_error(), "CloseHandle for Toolhelp snapshot failed")
    if not rows:
        raise FlowFailure("Toolhelp returned no process rows.")
    return rows


def descendants(rows: list[dict], parent_pid: int) -> set[int]:
    parents = {row["pid"]: row["parentPid"] for row in rows}
    found = {parent_pid}
    while True:
        expanded = found | {pid for pid, parent in parents.items() if parent in found}
        if expanded == found:
            return found
        found = expanded


def visible_edge_windows(edge_pids: set[int]) -> list[dict]:
    if os.name != "nt":
        return []
    user32 = ctypes.WinDLL("user32", use_last_error=True)
    enum_proc = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
    user32.EnumWindows.restype = wintypes.BOOL
    user32.EnumWindows.argtypes = [enum_proc, wintypes.LPARAM]
    user32.GetWindowThreadProcessId.restype = wintypes.DWORD
    user32.GetWindowThreadProcessId.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.DWORD)]
    user32.IsWindowVisible.restype = wintypes.BOOL
    user32.IsWindowVisible.argtypes = [wintypes.HWND]
    user32.GetWindowTextLengthW.restype = ctypes.c_int
    user32.GetWindowTextLengthW.argtypes = [wintypes.HWND]
    user32.GetWindowTextW.restype = ctypes.c_int
    user32.GetWindowTextW.argtypes = [wintypes.HWND, wintypes.LPWSTR, ctypes.c_int]
    windows = []

    @enum_proc
    def callback(hwnd, _):
        pid = wintypes.DWORD()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        if int(pid.value) not in edge_pids or not user32.IsWindowVisible(hwnd):
            return True
        length = user32.GetWindowTextLengthW(hwnd)
        title = ctypes.create_unicode_buffer(max(2, length + 1))
        user32.GetWindowTextW(hwnd, title, len(title))
        windows.append({
            "hwnd": int(hwnd),
            "pid": int(pid.value),
            "visible": True,
            "title": title.value,
        })
        return True

    if not user32.EnumWindows(callback, 0):
        raise OSError(ctypes.get_last_error(), "EnumWindows failed")
    return windows


def meter_value(body: str) -> tuple[str, float]:
    pre_power = body.split("Board Power:", 1)[0]
    matches = list(VOLTAGE_RE.finditer(pre_power))
    if not matches:
        raise ValueError("No numeric voltage is visible in the meter display.")
    match = matches[-1]
    value = float(match.group(1))
    unit = match.group(2)
    multiplier = 1e-6 if unit in ("\u03bcV", "\u00b5V", "uV") else (1e-3 if unit == "mV" else 1.0)
    return match.group(0), value * multiplier


class VisibleRun:
    def __init__(self, output: Path, app: Path, identity_api):
        self.output = output
        self.app = app
        self.identity_api = identity_api
        self.started = time.monotonic()
        self.deadline = self.started + TOTAL_DEADLINE_SECONDS
        self.result = {
            "schema": 1,
            "outcome": "NOT_RUN",
            "startedUtc": utc_now(),
            "phase": "initialize",
            "headed": True,
            "browserChannel": "msedge",
            "headless": False,
            "browserCount": 1,
            "hostTimerPolicy": "task-local browser background timer/render throttling disabled; solver unchanged",
            "controlObservationBoundSeconds": CONTROL_READY_SECONDS,
            "controlBoundReason": "r5 actual removal control still disabled at 115s; observer-only180s, no application cap change",
            "gameFunctionsCalled": False,
            "pageEvaluateUsed": False,
            "syntheticDispatchUsed": False,
            "apiBoundary": [
                "Playwright Python locator, select, fill, mouse, keyboard, visible body text, screenshot",
                "No page.evaluate, hidden snapshot, controller, solver or injected state calls",
            ],
            "actions": [],
            "cases": [],
            "errors": [],
        }
        self.sequence = 0
        self.page = None
        self.context = None
        self.playwright = None
        self.preview = None
        self.preview_identity = None
        self.preview_log = None
        self.edge_identities = {}

    def save(self, final: bool = False):
        self.result["finishedUtc"] = utc_now() if final else None
        self.result["elapsedSeconds"] = round(time.monotonic() - self.started, 3)
        write_json(self.output / ("result.json" if final else "result-progress.json"), self.result)

    def phase(self, name: str):
        self.result["phase"] = name
        self.save()

    def action(self, action_name: str, operation, **command) :
        if time.monotonic() >= self.deadline:
            raise TimeoutError("20-minute visible-QA deadline expired before " + action_name)
        self.sequence += 1
        row = {
            "sequence": self.sequence,
            "phase": self.result["phase"],
            "name": action_name,
            "command": command,
            "startedUtc": utc_now(),
            "status": "RUNNING",
        }
        self.result["actions"].append(row)
        self.save()
        started = time.monotonic()
        try:
            value = operation()
            row["status"] = "PASS"
            if isinstance(value, str):
                row["visibleText"] = value
            elif isinstance(value, dict):
                row["observation"] = value
            elif value is not None:
                row["observation"] = value
            return value
        except BaseException as error:
            row["status"] = "FAIL"
            row["error"] = repr(error)
            row["traceback"] = traceback.format_exc()
            raise
        finally:
            row["elapsedSeconds"] = round(time.monotonic() - started, 3)
            row["finishedUtc"] = utc_now()
            self.save()

    def click(self, name: str):
        return self.action("click " + name,
                           lambda: self.page.get_by_role("button", name=name, exact=True).click(),
                           op="locator.click", role="button", name=name)

    def click_text(self, text: str):
        return self.action("click visible text " + text,
                           lambda: self.page.get_by_text(text, exact=True).click(),
                           op="locator.click", text=text)

    def select(self, field: str, label: str):
        return self.action("select " + field + " = " + label,
                           lambda: self.page.get_by_label(field, exact=True).select_option(label=label),
                           op="locator.select_option", field=field, label=label)

    def fill(self, field: str, text: str):
        return self.action("fill " + field,
                           lambda: self.page.get_by_label(field, exact=True).fill(text),
                           op="locator.fill", field=field, text=text)

    def mouse_click(self, x: int, y: int):
        return self.action("visible menu mouse click",
                           lambda: self.page.mouse.click(x, y),
                           op="mouse.click", x=x, y=y)

    def key(self, key: str):
        return self.action("visible keyboard " + key,
                           lambda: self.page.keyboard.press(key),
                           op="keyboard.press", key=key)

    def body(self, label: str) -> str:
        return self.action(label, lambda: self.page.locator("body").inner_text(),
                           op="visible-body.inner_text")

    def expect_visible_text(self, text: str, timeout_ms: int = 30_000, scope_dialog: bool = False) -> str:
        timeout_ms = min(timeout_ms, max(1, int((self.deadline - time.monotonic()) * 1000)))
        def wait():
            container = self.page.get_by_role("dialog") if scope_dialog else self.page
            container.get_by_text(text, exact=True).wait_for(state="visible", timeout=timeout_ms)
            return self.page.locator("body").inner_text()
        body = self.action("wait for visible text " + text, wait,
                           op="locator.wait_for", text=text, state="visible", timeoutMs=timeout_ms,
                           scope="visible-dialog" if scope_dialog else "page")
        if text not in body:
            raise FlowFailure("Visible text locator resolved but body did not contain: " + text)
        return body

    def expect_body(self, description: str, predicate, expected: str) -> str:
        def inspect():
            value = self.page.locator("body").inner_text()
            if not predicate(value):
                raise FlowFailure("Expected visible UI state: " + expected)
            return value
        return self.action(description, inspect, op="visible-body.assert", expected=expected)

    def screenshot(self, filename: str):
        if Path(filename).name != filename or not filename.endswith(".png"):
            raise FlowFailure("Unsafe screenshot filename.")
        path = self.output / "screenshots" / filename
        if len(list((self.output / "screenshots").glob("*.png"))) >= 5:
            raise FlowFailure("The five-screenshot evidence limit was reached.")
        return self.action("headed screenshot " + filename,
                           lambda: (self.page.screenshot(path=str(path)), str(path))[1],
                           op="page.screenshot", path=str(path))

    def wait_button_enabled(self, name: str, timeout_seconds: int = CONTROL_READY_SECONDS, refresh_component: str | None = None) -> bool:
        timeout_ms = min(timeout_seconds * 1000, max(1, int((self.deadline - time.monotonic()) * 1000)))
        def wait():
            locator = self.page.get_by_role("button", name=name, exact=True)
            locator.wait_for(state="visible", timeout=timeout_ms)
            until = time.monotonic() + timeout_ms / 1000
            next_selection = time.monotonic() + 5
            while time.monotonic() < until:
                if locator.is_enabled():
                    return True
                if refresh_component and time.monotonic() >= next_selection:
                    self.set_component(refresh_component)
                    next_selection = time.monotonic() + 5
                time.sleep(0.2)
            raise TimeoutError("Visible UI control stayed disabled: " + name)
        return self.action("wait for enabled UI control " + name, wait,
                           op="locator.is_enabled polling", name=name, timeoutMs=timeout_ms,
                           refreshViaOrdinaryComponentSelection=refresh_component)

    def select_menu_field(self, label: str, option: str):
        # These are the two visible native selects on the normal menu. The
        # product-field locator is the same ordinary locator route used by the
        # maintained procedural acceptance driver; it does not inspect app
        # state or call a controller.
        return self.action("select visible menu " + label + " = " + option,
                           lambda: self.page.locator("label.tsj-product-field")
                               .filter(has_text=label).locator("select")
                               .select_option(label=option),
                           op="locator.select_option", label=label, option=option)

    def configure_menu(self):
        self.action("wait for the normal menu fields after navigation",
            lambda: self.page.locator("label.tsj-product-field").filter(has_text="Difficulty").wait_for(state="visible", timeout=15_000),
            op="locator.wait_for", field="Difficulty", state="visible", timeoutMs=15000)
        body = self.body("read visible challenge menu")
        if "Difficulty" not in body or "Board family" not in body:
            raise FlowFailure("The normal challenge menu was not visible.")
        if "MEDIUM" not in body or "Multi-rail control board" not in body:
            self.select_menu_field("Difficulty", "MEDIUM")
            self.select_menu_field("Board family", "Multi-rail control board")
        self.expect_body(
            "verify visible Q30 menu selection",
            lambda value: "MEDIUM" in value and "Multi-rail control board" in value,
            "MEDIUM and Multi-rail control board",
        )

    def prepare_seed(self, seed: int):
        self.configure_menu()
        self.click_text("Enter an exact seed")
        self.fill("Exact seed (signed decimal integer)", str(seed))
        observation_started = time.monotonic()
        self.click("Prepare exact seed")
        self.expect_visible_text("Accept ticket and start", TICKET_OBSERVATION_MS)
        self.result["cases"][-1]["ticketPreparationElapsedSeconds"] = round(
            time.monotonic() - observation_started, 3)
        self.result["cases"][-1]["ticketObservationBoundSeconds"] = TICKET_OBSERVATION_MS / 1000
        self.save()
        self.click("Accept ticket and start")
        self.expect_visible_text("Run customer retest")
        self.expect_body("verify powered workbench",
                         lambda value: "Board Power: ON" in value,
                         "Board Power: ON")

    def set_inputs(self, button: str):
        self.click("Service ticket")
        self.click(button)
        self.click("Service ticket")

    def place_probes(self, output: str):
        self.click("Board view")
        self.select("Probe terminal", output + " terminal 1")
        self.click("Place red probe")
        self.select("Probe terminal", output + " terminal 2")
        self.click("Place black probe")
        self.click("Board view")

    def read_voltage(self, output: str, expected: str, minimum: float, maximum: float) -> dict:
        self.place_probes(output)
        def wait_reading():
            until = min(time.monotonic() + 8.0, self.deadline)
            last_body = ""
            while time.monotonic() < until:
                last_body = self.page.locator("body").inner_text()
                try:
                    display, volts = meter_value(last_body)
                    return {"display": display, "volts": volts, "visibleText": last_body}
                except ValueError:
                    time.sleep(0.1)
            raise FlowFailure("No numeric DC voltage appeared for " + output + ": " + last_body[-500:])
        measured = self.action("read actual displayed DC voltage at " + output, wait_reading,
                               op="visible-body voltage observation", output=output)
        def check():
            if not (minimum <= measured["volts"] <= maximum):
                raise FlowFailure(
                    output + " measured " + measured["display"] +
                    "; expected " + expected
                )
            return measured
        return self.action("check " + output + " voltage " + expected, check,
                           op="numeric oracle", range=[minimum, maximum], unit="V")

    def faulty_observation(self, case: dict, screenshot: bool):
        for state in case["faultStates"]:
            self.set_inputs(state)
        self.click("DC V")
        readings = {}
        for output in case["outputs"]:
            readings[output] = self.read_voltage(output, "LOW (<0.05 V) despite HIGH input", 0.0, 0.05)
        self.click("Run customer retest")
        body = self.expect_visible_text("Customer retest did not pass. Continue troubleshooting.")
        if screenshot:
            self.screenshot("001-20-faulty.png")
        return {"readings": readings, "retest": "FAIL_EXPECTED", "visibleText": body[-1200:]}

    def set_component(self, component: str):
        self.click("Board view")
        self.select("Select component", component)
        self.click("Board view")
        self.click("Component")

    def set_power(self, case: dict, connected: bool):
        self.click("Bench power")
        verb = "Connect" if connected else "Disconnect"
        for number, voltage in case["supplies"]:
            self.click(f"{verb} Supply {number} ({voltage})")
        self.click("Bench power")
        expected = "Board Power: ON" if connected else "Board Power: OFF"
        self.expect_body("verify board power " + ("ON" if connected else "OFF"),
                         lambda value: expected in value, expected)

    def acquire_part(self, case: dict):
        self.click("Parts Tray")
        tray = self.page.get_by_role("dialog", name="Parts Tray", exact=True)
        tray.wait_for(state="visible", timeout=10_000)
        before_labels = self.action("record loose parts before Shop acquisition",
            lambda: tray.get_by_role("button", name=re.compile(r"^Part [1-9][0-9]* - ")).all_text_contents(),
            op="visible button text")
        if len(before_labels) != 1:
            raise FlowFailure("Expected the one removed original in the tray: " + repr(before_labels))
        self.click("Parts Tray")
        self.click("Shop")
        self.click(case["replacementCategory"])
        if case.get("search"):
            self.fill("Search components", case["search"])
        self.wait_button_enabled(case["acquire"])
        self.click(case["acquire"])
        self.expect_visible_text("Added to the real Parts Tray. Select the loose part on the workbench to install it.", scope_dialog=True)
        self.click("Close")
        self.click("Parts Tray")
        def read_acquired_label():
            tray = self.page.get_by_role("dialog", name="Parts Tray", exact=True)
            tray.wait_for(state="visible", timeout=10_000)
            part_buttons = tray.get_by_role("button", name=re.compile(r"^Part [1-9][0-9]* - "))
            part_buttons.first.wait_for(state="visible", timeout=10_000)
            labels = part_buttons.all_text_contents()
            acquired_labels = [label for label in labels if label not in before_labels]
            if len(labels) != 2 or len(acquired_labels) != 1 or not all(label in labels for label in before_labels):
                raise FlowFailure("Expected the removed original plus exactly one newly acquired part: " + repr(labels))
            return acquired_labels[0]
        acquired = self.action("read newly acquired part label from visible Parts Tray", read_acquired_label,
                               op="visible button text", expectedLooseParts=2)
        case["part"] = acquired
        self.result["cases"][-1]["acquiredPartLabel"] = acquired
        self.click("Parts Tray")

    def install_acquired(self, case: dict):
        self.click("Parts Tray")
        self.click(case["part"])
        install = "Install as " + case["seat"]
        self.wait_button_enabled(install)
        self.click(install)
        self.click("Parts Tray")

    def verify_repaired_behavior(self, case: dict) -> dict:
        outputs = {}
        for button, expected in case["healthyStates"]:
            self.set_inputs(button)
            for output, logic in expected.items():
                low = logic == "LOW"
                outputs[button + "/" + output] = self.read_voltage(
                    output,
                    "LOW (<0.05 V)" if low else "HIGH (10.8-12.6 V)",
                    0.0 if low else 10.8,
                    0.05 if low else 12.6,
                )
        self.click("Run customer retest")
        body = self.expect_visible_text("FUNCTION VERIFIED")
        return {"readings": outputs, "retest": "FUNCTION VERIFIED", "visibleText": body[-1200:]}

    def optional_removal_roundtrip(self, case: dict):
        """Use actual UI to make a proven-good board fail, then repair/retest it."""
        self.click("Return to board")
        self.set_power(case, connected=False)
        self.set_component(case["component"])
        self.wait_button_enabled("Remove component")
        self.click("Remove component")
        self.set_power(case, connected=True)
        locator = self.page.get_by_role("button", name="Run customer retest", exact=True)
        if not locator.is_visible() or not locator.is_enabled():
            self.set_power(case, connected=False)
            self.click("Parts Tray")
            self.click(case["part"])
            install = "Install as " + case["seat"]
            self.wait_button_enabled(install)
            self.click(install)
            self.click("Parts Tray")
            self.set_power(case, connected=True)
            self.click("Run customer retest")
            restored = self.expect_visible_text("FUNCTION VERIFIED")
            return {
                "status": "NOT_RUN_UI_DID_NOT_OFFER_FRESH_RETEST",
                "restored": "FUNCTION VERIFIED",
                "visibleText": restored[-1200:],
            }
        before = self.body("capture visible retest state before removal retest")
        self.click("Run customer retest")
        failed = self.expect_visible_text("Customer retest did not pass. Continue troubleshooting.")
        if "FUNCTION VERIFIED" in failed and failed == before:
            return {"status": "NOT_PROVED_CACHED_RESULT", "visibleText": failed[-1200:]}
        self.set_power(case, connected=False)
        self.click("Parts Tray")
        self.click(case["part"])
        install = "Install as " + case["seat"]
        self.wait_button_enabled(install)
        self.click(install)
        self.click("Parts Tray")
        self.set_power(case, connected=True)
        self.click("Run customer retest")
        passed = self.expect_visible_text("FUNCTION VERIFIED")
        if passed == failed:
            return {"status": "NOT_PROVED_REPAIR_RESULT_DID_NOT_CHANGE"}
        return {"status": "PASS_REAL_REMOVAL_FAIL_AND_REPAIR_PASS",
                "failedText": failed[-800:], "passedText": passed[-800:]}

    def run_case(self, case: dict, index: int, do_roundtrip: bool):
        case_result = {
            "seed": case["seed"],
            "packages": case["size"],
            "status": "STARTED",
            "initialTicket": "SEEDED_FAULTY_CHALLENGE; normal exact-seed UI has no healthy-first state",
            "startedUtc": utc_now(),
        }
        self.result["cases"].append(case_result)
        self.save()
        self.phase(f"seed-{case['seed']}-prepare")
        self.prepare_seed(case["seed"])
        # Screenshots are bounded to menu, 20-part faulty/verified, and 30/40 verified.
        initial = self.body(f"record visible {case['size']}-part initial workbench")
        case_result["initialWorkbench"] = initial[-1600:]
        self.save()
        self.phase(f"seed-{case['seed']}-faulty")
        case_result["faulty"] = self.faulty_observation(case, screenshot=(case["size"] == 20))
        self.phase(f"seed-{case['seed']}-repair")
        self.set_power(case, connected=False)
        self.set_component(case["component"])
        self.wait_button_enabled("Remove component", refresh_component=case["component"])
        self.click("Remove component")
        case_result["physicalReadinessObservation"] = "ordinary component selection refreshed during bounded wait; automatic control refresh NOT_PROVED"
        self.acquire_part(case)
        self.install_acquired(case)
        self.set_power(case, connected=True)
        self.phase(f"seed-{case['seed']}-repaired-retest")
        case_result["repaired"] = self.verify_repaired_behavior(case)
        if case["size"] == 20:
            self.screenshot("002-20-verified.png")
        elif case["size"] == 30:
            self.screenshot("003-30-verified.png")
        else:
            self.screenshot("004-40-verified.png")
        if do_roundtrip:
            self.phase(f"seed-{case['seed']}-optional-removal-roundtrip")
            case_result["postRepairRemovalRoundtrip"] = self.optional_removal_roundtrip(case)
            if case_result["postRepairRemovalRoundtrip"]["status"] != "PASS_REAL_REMOVAL_FAIL_AND_REPAIR_PASS":
                raise FlowFailure("Requested optional removal roundtrip was not proved")
        else:
            case_result["postRepairRemovalRoundtrip"] = {"status": "NOT_RUN_NO_FRESH_HEALTHY_TO_FAULT_SEQUENCE"}
        case_result["status"] = "PASS"
        case_result["finishedUtc"] = utc_now()
        self.save()
        self.click("New board / Main menu")


def validate_paths(args) -> tuple[Path, Path, Path, Path]:
    app = args.app.resolve(strict=True)
    if not app.is_dir():
        raise FlowFailure("--app must be the current private production source root.")
    output = args.output.resolve(strict=False)
    temp_root = Path(tempfile.gettempdir()).resolve(strict=True)
    if output == temp_root or not output.is_relative_to(temp_root):
        raise FlowFailure("--output must be a new directory beneath the OS temp root.")
    if output.exists():
        raise FlowFailure("--output already exists; choose a unique OS-temp directory.")
    powershell = args.powershell.resolve(strict=True)
    deps = args.deps.resolve(strict=True)
    if not powershell.is_file() or powershell.name.casefold() not in ("pwsh.exe", "pwsh"):
        raise FlowFailure("--powershell must be the physical PowerShell 7 executable.")
    if not deps.is_dir() or not (deps / "playwright").is_dir():
        raise FlowFailure("--deps must contain the installed Playwright Python package.")
    required = (
        app / "scripts" / "preview.ps1",
        app / "scripts" / "VerifierIsolation.psm1",
        app / "war" / "circuitjs.html",
        app / "war" / "circuitjs1" / "circuitjs1.nocache.js",
    )
    for path in required:
        if not path.is_file() or is_reparse(path):
            raise FlowFailure("Private production preview input is missing or unsafe: " + str(path))
    return app, output, powershell, deps


def choose_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def verify_preview(run: VisibleRun, port: int, timeout: float = PREVIEW_READY_SECONDS) -> dict:
    deadline = time.monotonic() + timeout
    last_error = None
    uri = f"http://127.0.0.1:{port}/__tsj/verify-identity"
    while time.monotonic() < deadline:
        if run.preview is None or run.preview.poll() is not None:
            raise FlowFailure("Production preview exited before identity became ready.")
        try:
            with urlopen(uri, timeout=2) as response:
                identity = json.load(response)
            return identity
        except Exception as error:
            last_error = repr(error)
            time.sleep(0.2)
    raise TimeoutError("Production preview identity route timed out: " + str(last_error))


def launch_preview(run: VisibleRun, powershell: Path) -> int:
    port = choose_port()
    preview_script = run.app / "scripts" / "preview.ps1"
    log_path = run.output / "preview.log"
    run.preview_log = log_path.open("wb")
    command = [
        str(powershell), "-NoProfile", "-NonInteractive", "-File", str(preview_script),
        "-Port", str(port), "-ForceTcpListener",
    ]
    creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
    run.preview = subprocess.Popen(
        command,
        cwd=str(run.app),
        stdin=subprocess.DEVNULL,
        stdout=run.preview_log,
        stderr=subprocess.STDOUT,
        creationflags=creationflags,
    )
    run.result["preview"] = {
        "pid": run.preview.pid,
        "command": command,
        "port": port,
        "startedUtc": utc_now(),
    }
    run.save()
    run.preview_identity = native_identity(run.identity_api, run.preview.pid)
    expected_shell = os.path.normcase(os.path.normpath(str(powershell)))
    actual_shell = os.path.normcase(os.path.normpath(run.preview_identity["executable"]))
    if actual_shell != expected_shell:
        raise FlowFailure("Preview process image did not match the supplied physical PowerShell 7 executable.")
    run.result["preview"]["nativeIdentity"] = run.preview_identity
    run.save()
    identity = verify_preview(run, port)
    expected_root = os.path.normcase(os.path.normpath(str(run.app)))
    serving_root = os.path.normcase(os.path.normpath(str(identity.get("repositoryRoot", ""))))
    expected_script = os.path.normcase(os.path.normpath(str(preview_script)))
    if (identity.get("protocol") != "troubleshootjs-preview-identity-v1" or
            serving_root != expected_root or
            os.path.normcase(os.path.normpath(str(identity.get("previewScript", "")))) != expected_script or
            identity.get("previewPort") != port or
            identity.get("processId") != run.preview.pid or
            identity.get("processStartTicks") != run.preview_identity["creationFileTimeTicks"] + 504_911_232_000_000_000):
        raise FlowFailure("Preview serving identity did not match the requested app, PID, birth, or port.")
    run.result["preview"]["servingIdentity"] = identity
    run.result["preview"]["servingStartClock"] = "DOTNET_100NS_SINCE_0001; converted exactly from native FILETIME epoch1601"
    run.save()
    return port


def close_preview(run: VisibleRun):
    if run.preview is None:
        return
    current = run.identity_api.query_process(run.preview.pid)
    classification = run.identity_api.compare_identity(run.preview_identity, current)
    run.result.setdefault("cleanup", {})["previewBeforeClose"] = {
        "classification": classification,
        "poll": run.preview.poll(),
    }
    if run.preview.poll() is None:
        if classification.get("status") != run.identity_api.MATCH:
            run.result.setdefault("cleanup", {})["previewStop"] = "LEFT_UNTOUCHED_IDENTITY_NOT_MATCH"
            run.result["errors"].append("Preview process identity changed or became unresolved; not terminated.")
            return
        run.preview.terminate()
        try:
            run.preview.wait(timeout=12)
        except subprocess.TimeoutExpired:
            current = run.identity_api.query_process(run.preview.pid)
            classification = run.identity_api.compare_identity(run.preview_identity, current)
            if classification.get("status") == run.identity_api.MATCH:
                run.preview.kill()
                try:
                    run.preview.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    run.result["errors"].append("Exact retained preview handle did not close after terminate/kill.")
            else:
                run.result["errors"].append("Preview identity changed during bounded close; left untouched.")
    current = run.identity_api.query_process(run.preview.pid)
    classification = run.identity_api.compare_identity(run.preview_identity, current)
    run.result.setdefault("cleanup", {})["previewAfterClose"] = {
        "classification": classification,
        "exitCode": run.preview.poll(),
    }
    if classification.get("status") != run.identity_api.ABSENT:
        run.result["errors"].append("Preview process absence was not proven after close.")
    else:
        run.result["cleanup"]["previewStop"] = "PASS_EXACT_INSTANCE_ABSENT"


def close_edge_processes(run: VisibleRun):
    if not run.edge_identities:
        return
    deadline = time.monotonic() + 15
    remaining = {}
    for pid, recorded in run.edge_identities.items():
        query = run.identity_api.query_process(pid)
        state = run.identity_api.compare_identity(recorded, query)
        if state.get("status") != run.identity_api.ABSENT:
            remaining[str(pid)] = {"classification": state, "query": query}
    while remaining and time.monotonic() < deadline:
        time.sleep(0.25)
        for raw_pid in list(remaining):
            pid = int(raw_pid)
            query = run.identity_api.query_process(pid)
            state = run.identity_api.compare_identity(run.edge_identities[pid], query)
            if state.get("status") == run.identity_api.ABSENT:
                del remaining[raw_pid]
            else:
                remaining[raw_pid] = {"classification": state, "query": query}
    run.result.setdefault("cleanup", {})["edgeAbsence"] = {
        "status": "PASS" if not remaining else "FAIL",
        "survivors": remaining,
        "recordedInstances": len(run.edge_identities),
    }
    if remaining:
        run.result["errors"].append("Task Edge instance absence was not proven; no process was terminated.")


def capture_edge_processes(run: VisibleRun, required: bool = False):
    rows = process_table()
    owned = descendants(rows, os.getpid())
    edge_rows = [row for row in rows
                 if row["pid"] in owned and row["imageName"].casefold() == "msedge.exe"]
    captured = []
    for row in edge_rows:
        identity = native_identity(run.identity_api, row["pid"])
        if Path(identity["executable"]).name.casefold() != "msedge.exe":
            raise FlowFailure("Edge process image identity did not resolve to msedge.exe.")
        previous = run.edge_identities.get(row["pid"])
        if previous is not None:
            current = run.identity_api.query_process(row["pid"])
            classification = run.identity_api.compare_identity(previous, current)
            if classification.get("status") != run.identity_api.MATCH:
                raise FlowFailure("Recorded Edge PID identity changed while the context was open.")
        else:
            run.edge_identities[row["pid"]] = identity
        captured.append(row["pid"])
    if required and not captured:
        raise FlowFailure("No Edge process descended from the bounded driver was observed.")
    if run.result.get("headedProof") is not None:
        run.result["headedProof"]["edgeExecutableIdentities"] = [
            {"pid": pid, **identity} for pid, identity in sorted(run.edge_identities.items())
        ]
        run.save()
    return captured


def start_ui(run: VisibleRun, deps: Path, port: int):
    sys.path.insert(0, str(deps))
    import playwright
    from playwright.sync_api import sync_playwright

    run.result["playwrightVersion"] = importlib.metadata.version("playwright")
    run.playwright = sync_playwright().start()
    profile = run.output / "profile"
    if profile.exists():
        raise FlowFailure("Isolated browser profile path already exists.")
    run.context = run.playwright.chromium.launch_persistent_context(
        str(profile),
        channel="msedge",
        headless=False,
        viewport={"width": 1440, "height": 1000},
        args=["--window-size=1460,1100", "--disable-background-timer-throttling",
              "--disable-renderer-backgrounding"],
    )
    run.page = run.context.pages[0]
    run.page.bring_to_front()
    if len(run.context.pages) != 1:
        raise FlowFailure("Expected exactly one page in the persistent Edge context.")
    run.page.set_default_timeout(15_000)
    run.page.on("pageerror", lambda error: run.result["errors"].append(
        {"phase": run.result["phase"], "error": str(error)}))

    edge_pids = set(capture_edge_processes(run, required=True))
    windows = visible_edge_windows(edge_pids)
    if not windows:
        raise FlowFailure("No visible top-level Edge window was proven.")
    run.result["headedProof"] = {
        "headlessArgument": False,
        "launchArguments": ["--window-size=1460,1100"],
        "edgeExecutableIdentities": [
            {"pid": pid, **identity} for pid, identity in sorted(run.edge_identities.items())
        ],
        "visibleWindows": windows,
        "pageCount": len(run.context.pages),
        "profile": str(profile),
    }
    run.save()
    run.page.goto(f"http://127.0.0.1:{port}/circuitjs.html", wait_until="domcontentloaded")
    run.expect_visible_text("Enter an exact seed", 30_000)
    capture_edge_processes(run)
    run.screenshot("000-menu.png")
    run.result["ready"] = {
        "status": "READY_HEADED_NORMAL_MENU",
        "observedUtc": utc_now(),
        "url": f"http://127.0.0.1:{port}/circuitjs.html",
        "pageCount": len(run.context.pages),
        "visibleWindows": windows,
    }
    run.save()


def close_browser(run: VisibleRun):
    if run.context is not None:
        try:
            capture_edge_processes(run)
        except Exception as error:
            run.result["errors"].append("Edge identity refresh before context close failed: " + repr(error))
        try:
            run.context.close()
            run.result.setdefault("cleanup", {})["contextClose"] = "PASS"
            capture_edge_processes(run)
        except Exception as error:
            run.result.setdefault("cleanup", {})["contextClose"] = "FAIL"
            run.result["errors"].append("Context close failed: " + repr(error))
        run.context = None
    if run.playwright is not None:
        try:
            run.playwright.stop()
            run.result.setdefault("cleanup", {})["playwrightStop"] = "PASS"
        except Exception as error:
            run.result.setdefault("cleanup", {})["playwrightStop"] = "FAIL"
            run.result["errors"].append("Playwright stop failed: " + repr(error))
        run.playwright = None
    close_edge_processes(run)


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--app", required=True, type=Path,
                        help="Fresh private production source root with scripts/ and war/.")
    parser.add_argument("--output", required=True, type=Path,
                        help="New unique evidence directory under the OS temp root.")
    parser.add_argument("--powershell", required=True, type=Path,
                        help="Physical PowerShell 7 executable used for preview hosting.")
    parser.add_argument("--deps", required=True, type=Path,
                        help="Existing Python dependency root containing Playwright.")
    parser.add_argument("--postrepair-removal-roundtrip", action="store_true",
                        help="Add a fresh healthy-to-faulty-to-repaired UI cycle after each initial pass.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    run = None
    output = None
    app = None
    before = None
    result = {
        "schema": 1,
        "outcome": "NOT_RUN",
        "startedUtc": utc_now(),
        "phase": "validate-arguments",
        "headed": True,
        "browserChannel": "msedge",
        "headless": False,
        "gameFunctionsCalled": False,
        "pageEvaluateUsed": False,
        "syntheticDispatchUsed": False,
        "actions": [],
        "cases": [],
        "errors": [],
    }
    try:
        app, output, powershell, deps = validate_paths(args)
        output.mkdir(parents=True, exist_ok=False)
        (output / "screenshots").mkdir()
        result["appRoot"] = str(app)
        result["outputRoot"] = str(output)
        result["powershell"] = str(powershell)
        result["depsRoot"] = str(deps)
        identity_api, identity_helper_hash = load_identity_helper()
        result["identityHelper"] = {
            "path": str(Path(__file__).with_name("windows_process_identity.py")),
            "sha256": identity_helper_hash,
        }
        result["driver"] = {
            "path": str(Path(__file__).resolve()),
            "sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        }
        result["previewLogLimit"] = (
            "UNCAPPED; preview output is retained in preview.log for diagnostics; "
            "the enclosing run has a 20-minute deadline."
        )
        run = VisibleRun(output, app, identity_api)
        run.result.update(result)
        run.result["controllerIdentity"] = native_identity(identity_api, os.getpid())
        run.phase("input-inventory-before")
        before = hash_inputs(app)
        write_json(output / "input-before.json", before)
        run.result["inputBefore"] = {
            "status": before["status"],
            "fileCount": before["fileCount"],
            "totalBytes": before["totalBytes"],
            "sha256": before["sha256"],
            "evidence": "input-before.json",
        }
        run.phase("preview-start")
        port = launch_preview(run, powershell)
        run.phase("headed-edge-start")
        start_ui(run, deps, port)
        for index, case in enumerate(CASES):
            run.run_case(case, index, args.postrepair_removal_roundtrip)
        run.result["outcome"] = "PASS_PENDING_CLEANUP"
    except BaseException as error:
        if run is not None:
            run.result["outcome"] = "BLOCKED" if isinstance(error, (OSError, ImportError, FlowFailure)) and run.result.get("phase") in (
                "validate-arguments", "input-inventory-before", "preview-start", "headed-edge-start"
            ) else "FAIL"
            run.result["failure"] = repr(error)
            run.result["failureTraceback"] = traceback.format_exc()
            run.result["errors"].append(repr(error))
            if run.page is not None:
                try:
                    run.result["failureVisibleText"] = run.page.locator("body").inner_text(timeout=3000)
                    if len(list((run.output / "screenshots").glob("*.png"))) < 5:
                        run.page.screenshot(path=str(run.output / "screenshots" / "failure-state.png"), timeout=3000)
                except Exception as capture_error:
                    run.result["failureCaptureError"] = repr(capture_error)
        else:
            result["outcome"] = "BLOCKED"
            result["failure"] = repr(error)
            result["failureTraceback"] = traceback.format_exc()
            result["errors"].append(repr(error))
    finally:
        cleanup_started = time.monotonic()
        if run is not None:
            try:
                close_browser(run)
            except BaseException as error:
                run.result["errors"].append("Browser cleanup phase failed: " + repr(error))
                run.result.setdefault("cleanup", {})["browserClose"] = "FAIL"
            try:
                close_preview(run)
            except BaseException as error:
                run.result["errors"].append("Preview cleanup phase failed: " + repr(error))
                run.result.setdefault("cleanup", {})["previewStop"] = "FAIL"
            if run.preview_log is not None:
                try:
                    run.preview_log.flush()
                    run.preview_log.close()
                    run.result.setdefault("cleanup", {})["previewLogClose"] = "PASS"
                except BaseException as error:
                    run.result.setdefault("cleanup", {})["previewLogClose"] = "FAIL"
                    run.result["errors"].append("Preview log close failed: " + repr(error))
            if app is not None and output is not None and output.is_dir():
                try:
                    run.phase("input-inventory-after")
                    after = hash_inputs(app)
                    write_json(output / "input-after.json", after)
                    run.result["inputAfter"] = {
                        "status": after["status"],
                        "fileCount": after["fileCount"],
                        "totalBytes": after["totalBytes"],
                        "sha256": after["sha256"],
                        "evidence": "input-after.json",
                    }
                    if before is None:
                        run.result["errors"].append(
                            "Input-before inventory is unavailable; input immutability is unproved.")
                    elif before["sha256"] != after["sha256"]:
                        run.result["errors"].append(
                            "Application source/compiled inputs changed during visible QA.")
                except BaseException as error:
                    run.result["errors"].append("Input-after inventory failed: " + repr(error))
            if run.result.get("outcome") == "PASS_PENDING_CLEANUP":
                run.result["outcome"] = "PASS" if not run.result["errors"] else "FAIL"
            run.result.setdefault("cleanup", {})["elapsedSeconds"] = round(time.monotonic() - cleanup_started, 3)
            run.result["finishedUtc"] = utc_now()
            run.result["elapsedSeconds"] = round(time.monotonic() - run.started, 3)
            run.result["phase"] = "closed"
            run.save(final=True)
            print(json.dumps({
                "outcome": run.result["outcome"],
                "cases": len(run.result["cases"]),
                "actions": len(run.result["actions"]),
                "cleanup": run.result.get("cleanup"),
                "output": str(output),
            }, ensure_ascii=False), flush=True)
            return 0 if run.result["outcome"] == "PASS" else (2 if run.result["outcome"] == "BLOCKED" else 1)
        else:
            if output is not None and output.is_dir():
                result["finishedUtc"] = utc_now()
                result["cleanup"] = {"elapsedSeconds": round(time.monotonic() - cleanup_started, 3)}
                write_json(output / "result.json", result)
            print(json.dumps({"outcome": result["outcome"], "failure": result.get("failure")}), flush=True)
            return 2


if __name__ == "__main__":
    sys.dont_write_bytecode = True
    raise SystemExit(main())
