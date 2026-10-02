"""Host-driven acceptance checks for compiled developer attributes.

Each case navigates Edge to a relative ``circuitjs.html`` URL and reads
attributes from ``html`` until a declared terminal state appears. Passive CDP
DOM events observe synchronous developer fixtures without renderer polling;
the terminal state and report still require bounded final attribute reads.
The runner does not inject JavaScript or call application controllers.  Its output is
retained for audit and replay; this script never removes it.

Usage::

    python tests/browser/compiled_attribute_acceptance.py REPO OUTPUT SPEC_JSON

The output directory must be a new directory below the OS temporary directory.
The spec is an object containing ``cases``.  Each case contains ``name``,
``path``, ``stateAttribute``, ``timeoutSeconds``, and exactly one of
``expectedState`` or ``expectedPrefix``.  ``terminalPrefixes`` defaults to
``["PASS", "FAIL"]`` and ``reportAttribute`` is optional.
"""

from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Thread
from urllib.parse import urlsplit

import asyncio
import hashlib
import json
import math
import os
import re
import subprocess
import sys
import tempfile
import time
import traceback
from datetime import datetime, timezone


try:
    from playwright.async_api import TimeoutError as PlaywrightTimeoutError
    from playwright.async_api import async_playwright
except ImportError:  # Validation still works on a host without Playwright.
    PlaywrightTimeoutError = TimeoutError
    async_playwright = None


SCRIPT_SCHEMA = 1
DEFAULT_TERMINAL_PREFIXES = ["PASS", "FAIL"]
POLL_SECONDS = 0.2
MAX_ATTRIBUTE_TIMEOUT_MS = 10000
# Outer capture allowance for an aggregate compiled census. Application jobs
# retain their own independent deadlines, unit counts and per-unit guards.
MAX_TIMEOUT_SECONDS = 900.0
ATTRIBUTE_RE = re.compile(r"^data-tsj-[A-Za-z0-9][A-Za-z0-9-]*$")
CASE_NAME_RE = re.compile(r"[^A-Za-z0-9._-]+")
INPUT_ROOTS = ("src", "war")
CHUNK_SIZE = 1024 * 1024


class QuietHandler(SimpleHTTPRequestHandler):
    """Serve the compiled web root without polluting the acceptance log."""

    def log_message(self, *args):
        pass

    def _serve_optional_favicon(self):
        # The browser may request the optional site icon even though this
        # compiled fixture intentionally has no favicon file.  Keep that
        # normal host request out of the app's HTTP error budget; every other
        # missing or failed resource still follows SimpleHTTPRequestHandler
        # and remains a strict acceptance error.
        if self.path != "/favicon.ico":
            return False
        self.send_response(204)
        self.send_header("Content-Length", "0")
        self.end_headers()
        return True

    def do_GET(self):
        if not self._serve_optional_favicon():
            super().do_GET()

    def do_HEAD(self):
        if not self._serve_optional_favicon():
            super().do_HEAD()


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def is_within(path, parent):
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def require_string(value, label):
    if not isinstance(value, str) or not value:
        raise ValueError(label + " must be a non-empty string")
    if "\r" in value or "\n" in value or any(ord(char) < 32 for char in value):
        raise ValueError(label + " contains a control character")
    return value


def validate_attribute(value, label):
    value = require_string(value, label)
    if not ATTRIBUTE_RE.fullmatch(value):
        raise ValueError(label + " must be a data-tsj-* attribute")
    return value


def validate_prefix(value, label):
    value = require_string(value, label)
    if len(value) > 256:
        raise ValueError(label + " is too long")
    return value


def validate_case_path(value):
    value = require_string(value, "case.path")
    if "\\" in value or value.startswith("/") or value.startswith("//"):
        raise ValueError("case.path must be a relative URL")
    parsed = urlsplit(value)
    if parsed.scheme or parsed.netloc or parsed.path != "circuitjs.html":
        raise ValueError("case.path must name circuitjs.html with an optional query")
    if parsed.fragment:
        raise ValueError("case.path must not contain a fragment")
    return value


def validate_timeout(value, label):
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(label + " must be a number")
    value = float(value)
    if not math.isfinite(value) or value <= 0 or value > MAX_TIMEOUT_SECONDS:
        raise ValueError(label + " must be greater than 0 and no more than 900 seconds")
    return value


def normalise_case(case, index):
    if not isinstance(case, dict):
        raise ValueError("cases[%d] must be an object" % index)
    allowed = {"name", "path", "stateAttribute", "expectedState", "expectedPrefix",
               "terminalPrefixes", "timeoutSeconds", "reportAttribute"}
    unknown = sorted(set(case) - allowed)
    if unknown:
        raise ValueError("cases[%d] has unsupported fields: %s" %
                         (index, ", ".join(unknown)))
    name = require_string(case.get("name"), "cases[%d].name" % index)
    path = validate_case_path(case.get("path"))
    state_attribute = validate_attribute(case.get("stateAttribute"),
                                         "cases[%d].stateAttribute" % index)
    has_expected_state = "expectedState" in case
    has_expected_prefix = "expectedPrefix" in case
    if has_expected_state == has_expected_prefix:
        raise ValueError("cases[%d] must contain exactly one expectedState or expectedPrefix" % index)
    expected_state = (require_string(case["expectedState"],
                                     "cases[%d].expectedState" % index)
                      if has_expected_state else None)
    expected_prefix = (validate_prefix(case["expectedPrefix"],
                                       "cases[%d].expectedPrefix" % index)
                       if has_expected_prefix else None)

    terminal_prefixes = case.get("terminalPrefixes", DEFAULT_TERMINAL_PREFIXES)
    if not isinstance(terminal_prefixes, list) or not terminal_prefixes:
        raise ValueError("cases[%d].terminalPrefixes must be a non-empty array" % index)
    terminal_prefixes = [validate_prefix(prefix,
                                          "cases[%d].terminalPrefixes" % index)
                         for prefix in terminal_prefixes]
    report_attribute = case.get("reportAttribute")
    if report_attribute is not None:
        report_attribute = validate_attribute(report_attribute,
                                              "cases[%d].reportAttribute" % index)
    timeout_seconds = validate_timeout(case.get("timeoutSeconds"),
                                       "cases[%d].timeoutSeconds" % index)
    slug = CASE_NAME_RE.sub("-", name).strip("-._") or "case"
    return {
        "index": index,
        "name": name,
        "path": path,
        "stateAttribute": state_attribute,
        "expectedState": expected_state,
        "expectedPrefix": expected_prefix,
        "terminalPrefixes": terminal_prefixes,
        "reportAttribute": report_attribute,
        "timeoutSeconds": timeout_seconds,
        "slug": "%03d-%s" % (index, slug[:80]),
    }


def load_spec(spec_path):
    raw = spec_path.read_bytes()
    try:
        spec = json.loads(raw.decode("utf-8-sig"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError("spec JSON is invalid: " + str(error))
    if not isinstance(spec, dict) or set(spec) != {"cases"}:
        raise ValueError("spec JSON must contain only a cases array")
    cases = spec["cases"]
    if not isinstance(cases, list) or not cases:
        raise ValueError("spec.cases must be a non-empty array")
    return spec, raw, [normalise_case(case, index + 1)
                       for index, case in enumerate(cases)]


def input_files(repo):
    """Yield every regular file in the fixed source and compiled web roots."""
    seen = set()
    for root_name in INPUT_ROOTS:
        root = repo / root_name
        if root.is_symlink() or not root.is_dir():
            raise ValueError("input root is missing or a symlink: " + root_name)
        for directory, directory_names, file_names in os.walk(root, followlinks=False):
            directory_path = Path(directory)
            kept_directories = []
            for directory_name in directory_names:
                child = directory_path / directory_name
                if child.is_symlink():
                    raise ValueError("input tree contains a symlink: " +
                                     str(child.relative_to(repo)))
                kept_directories.append(directory_name)
            directory_names[:] = kept_directories
            for file_name in file_names:
                child = directory_path / file_name
                if child.is_symlink():
                    raise ValueError("input tree contains a symlink: " +
                                     str(child.relative_to(repo)))
                resolved = child.resolve()
                if not is_within(resolved, repo):
                    raise ValueError("input tree escapes the repository: " +
                                     str(child.relative_to(repo)))
                relative = resolved.relative_to(repo).as_posix()
                if relative not in seen:
                    seen.add(relative)
                    yield relative, resolved


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while True:
            block = stream.read(CHUNK_SIZE)
            if not block:
                return digest.hexdigest()
            digest.update(block)


def capture_input_manifest(repo):
    files = []
    for relative, path in sorted(input_files(repo), key=lambda item: item[0]):
        files.append({"path": relative, "size": path.stat().st_size,
                      "sha256": sha256_file(path)})
    canonical = json.dumps(files, sort_keys=True, separators=(",", ":")).encode("utf-8")
    source_files = [row for row in files if row["path"].startswith("src/")]
    web_files = [row for row in files if row["path"].startswith("war/")]
    def aggregate(rows):
        value = json.dumps(rows, sort_keys=True, separators=(",", ":")).encode("utf-8")
        return hashlib.sha256(value).hexdigest()
    return {
        "schema": 1,
        "capturedUtc": utc_now(),
        "roots": list(INPUT_ROOTS),
        "files": files,
        "fileCount": len(files),
        "sourceFileCount": len(source_files),
        "webFileCount": len(web_files),
        "sha256": hashlib.sha256(canonical).hexdigest(),
        "sourceSha256": aggregate(source_files),
        "webSha256": aggregate(web_files),
    }


def capture_script_identity(repo):
    script = Path(__file__).resolve()
    path = script.relative_to(repo).as_posix() if is_within(script, repo) else script.name
    return {"path": path, "size": script.stat().st_size,
            "sha256": sha256_file(script)}


def process_rows():
    query = ("$ErrorActionPreference='Stop'; Get-CimInstance Win32_Process -ErrorAction Stop | Select-Object "
             "@{n='pid';e={$_.ProcessId}},@{n='parent';e={$_.ParentProcessId}},"
             "@{n='created';e={$_.CreationDate.ToUniversalTime().ToString('o')}},"
             "ExecutablePath | ConvertTo-Json -Compress")
    powershell = Path(os.environ["SystemRoot"]) / "System32/WindowsPowerShell/v1.0/powershell.exe"
    result = subprocess.run([str(powershell), "-NoProfile", "-Command", query],
                            capture_output=True, text=True, timeout=20)
    if result.returncode:
        detail = {"returnCode": result.returncode,
                  "returnCodeHex": hex(result.returncode & 0xffffffff),
                  "stdoutLength": len(result.stdout), "stderrLength": len(result.stderr),
                  "stdout": result.stdout[:1000], "stderr": result.stderr[:1000]}
        raise RuntimeError("process identity query failed: " + json.dumps(detail))
    if not result.stdout.strip():
        raise RuntimeError("process identity query returned an empty inventory")
    rows = json.loads(result.stdout)
    rows = [rows] if isinstance(rows, dict) else rows
    if not isinstance(rows, list) or not rows:
        raise RuntimeError("process identity query returned an invalid inventory")
    seen = set()
    for row in rows:
        if (not isinstance(row, dict) or set(row) != {"pid", "parent", "created", "ExecutablePath"}
                or type(row["pid"]) is not int or row["pid"] < 0
                or type(row["parent"]) is not int or row["parent"] < 0
                or row["pid"] in seen or not isinstance(row["created"], str)
                or not row["created"]
                or (row["ExecutablePath"] is not None and not isinstance(row["ExecutablePath"], str))):
            raise RuntimeError("process identity query returned a malformed or duplicate row")
        try:
            created = datetime.fromisoformat(row["created"].replace("Z", "+00:00"))
        except ValueError as error:
            raise RuntimeError("process identity query returned an invalid creation time") from error
        if created.utcoffset() != timezone.utc.utcoffset(created):
            raise RuntimeError("process identity query creation time is not UTC")
        seen.add(row["pid"])
    own = [row for row in rows if row["pid"] == os.getpid()]
    if len(own) != 1 or not own[0]["ExecutablePath"]:
        raise RuntimeError("process identity query omitted the runner identity")
    return rows


def owned_edge_rows(rows):
    process_ids = {os.getpid()}
    while True:
        expanded = set(process_ids)
        for row in rows:
            try:
                if int(row.get("parent")) in process_ids:
                    expanded.add(int(row.get("pid")))
            except (TypeError, ValueError):
                continue
        if expanded == process_ids:
            break
        process_ids = expanded
    return [row for row in rows
            if int(row.get("pid", -1)) in process_ids and
            "msedge.exe" in (row.get("ExecutablePath") or "").lower()]


def save_json(output, name, value):
    path = output / name
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as stream:
        json.dump(value, stream, indent=2, ensure_ascii=False)
        stream.write("\n")


def save_raw(output, name, value):
    path = output / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(value.encode("utf-8"))
    return path


def expected_matches(case, state):
    if state is None:
        return False
    if case["expectedState"] is not None:
        return state == case["expectedState"]
    return state.startswith(case["expectedPrefix"])


def case_slug(case):
    return "cases/" + case["slug"]


class HtmlAttributeObserver:
    """Read-only DOM events, scoped to the declared HTML node and document URL."""

    def __init__(self, page, url, attribute, deadline, errors):
        self.page, self.url, self.attribute = page, url, attribute
        self.deadline, self.errors = deadline, errors
        self.session = None
        self.tasks = set()
        self.route_tasks = set()
        self.html_ready = asyncio.Event()
        origin = urlsplit(url)
        self.script_pattern = re.compile(r"^" + re.escape(origin.scheme + "://" + origin.netloc) +
            r"/circuitjs1/[0-9a-fA-F]{32}\.cache\.js(?:\?.*)?$")
        self.script_handler = self.hold_compiled_script
        self.script_requests = 0
        self.script_attachment = None
        self.listeners = []
        self.closed = self.session_lost = self.terminal_verified = False
        self.version = 0
        self.document_id = self.html_id = self.state = None
        self.document_backend = self.html_backend = None
        self.event_count = self.document_count = 0
        self.state_changes = 0

    def timeout(self):
        return max(.001, min(MAX_ATTRIBUTE_TIMEOUT_MS / 1000,
                            self.deadline - time.monotonic()))

    def failure(self, phase, error):
        self.errors.append({"phase": phase, "type": type(error).__name__,
                            "message": str(error)})

    def attach_html(self, node):
        if node.get("nodeName") != "HTML":
            return
        self.html_id = node["nodeId"]
        self.html_backend = node["backendNodeId"]
        self.html_ready.set()
        attributes = node.get("attributes", [])
        for index in range(0, len(attributes), 2):
            if attributes[index] == self.attribute:
                self.state = attributes[index + 1]

    async def acquire_document(self, version):
        try:
            value = await asyncio.wait_for(
                self.session.send("DOM.getDocument", {"depth": 1}), self.timeout())
            document = value["root"]
            if self.closed or version != self.version or document.get("documentURL") != self.url:
                return
            self.document_count += 1
            self.document_id = document["nodeId"]
            self.document_backend = document["backendNodeId"]
            for node in document.get("children", []):
                self.attach_html(node)
        except asyncio.CancelledError:
            raise
        except Exception as error:
            self.failure("DOM.getDocument", error)

    def document_updated(self, _):
        self.version += 1
        self.document_id = self.html_id = self.state = None
        self.html_ready.clear()
        for task in self.tasks:
            task.cancel()
        task = asyncio.create_task(self.acquire_document(self.version))
        self.tasks.add(task)
        task.add_done_callback(self.tasks.discard)

    def attribute_modified(self, value):
        if value["nodeId"] == self.html_id and value["name"] == self.attribute:
            self.event_count += 1
            if self.state != value["value"]:
                self.state_changes += 1
            self.state = value["value"]

    def attribute_removed(self, value):
        if value["nodeId"] == self.html_id and value["name"] == self.attribute:
            self.event_count += 1
            if self.state is not None:
                self.state_changes += 1
            self.state = None

    def child_inserted(self, value):
        if value["parentNodeId"] == self.document_id:
            self.attach_html(value["node"])

    def session_closed(self, _):
        self.session_lost = True
        self.failure("DOM.session-close", RuntimeError("Observer session closed before owned cleanup"))

    def guarded(self, phase, handler):
        def callback(value):
            if self.closed:
                return
            try:
                handler(value)
            except Exception as error:
                self.failure(phase, error)
        return callback

    async def hold_compiled_script(self, route):
        task = asyncio.current_task()
        self.route_tasks.add(task)
        try:
            self.script_requests += 1
            if route.request.resource_type != "script":
                raise ValueError("Selected compiled module request is not a script")
            if self.script_requests != 1:
                raise ValueError("More than one selected compiled module script request")
            # Attach to the main HTML document before the compiled app executes.
            # No generation job has started while its script request is held.
            await asyncio.wait_for(self.html_ready.wait(), self.timeout())
            if self.closed:
                await asyncio.wait_for(route.abort(), MAX_ATTRIBUTE_TIMEOUT_MS / 1000)
            else:
                self.script_attachment = {"generation": self.version,
                    "documentBackend": self.document_backend, "htmlBackend": self.html_backend,
                    "exactPageUrl": self.page.url == self.url, "resourceType": route.request.resource_type}
                if self.html_backend is None or self.document_backend is None or self.page.url != self.url:
                    raise ValueError("Compiled script lacks its exact attached HTML document")
                await asyncio.wait_for(route.continue_(), self.timeout())
        except asyncio.CancelledError:
            try:
                await asyncio.wait_for(route.abort(), MAX_ATTRIBUTE_TIMEOUT_MS / 1000)
            except Exception as error:
                self.failure("compiled-script-cancel-abort", error)
            raise
        except Exception as error:
            self.failure("compiled-script-attachment", error)
            try:
                await asyncio.wait_for(route.abort(), MAX_ATTRIBUTE_TIMEOUT_MS / 1000)
            except Exception as abort_error:
                self.failure("compiled-script-abort", abort_error)
        finally:
            self.route_tasks.discard(task)

    async def start(self):
        await asyncio.wait_for(self.page.route(self.script_pattern, self.script_handler), self.timeout())
        self.session = await asyncio.wait_for(
            self.page.context.new_cdp_session(self.page), self.timeout())
        for event, handler in (("DOM.documentUpdated", self.document_updated),
                               ("DOM.attributeModified", self.attribute_modified),
                               ("DOM.attributeRemoved", self.attribute_removed),
                               ("DOM.childNodeInserted", self.child_inserted),
                               ("close", self.session_closed)):
            callback = self.guarded(event, handler)
            self.listeners.append((event, callback))
            self.session.on(event, callback)
        await asyncio.wait_for(self.session.send("DOM.enable"), self.timeout())
        await self.acquire_document(self.version)

    async def verify_terminal_scope(self, generation, document_backend, html_backend, state, changes):
        try:
            if self.session_lost or self.page.url != self.url:
                raise ValueError("Terminal observer lost its exact document URL or session")
            value = await asyncio.wait_for(self.session.send("DOM.getDocument", {"depth": 1}), self.timeout())
            document = value["root"]
            html = next((node for node in document.get("children", [])
                         if node.get("nodeName") == "HTML"), None)
            attributes = dict(zip(html.get("attributes", [])[::2], html.get("attributes", [])[1::2])) if html else {}
            self.scope_identity = {"generationBefore": generation, "generationAfter": self.version,
                "stateChangesBefore": changes, "stateChangesAfter": self.state_changes,
                "documentNodeBefore": self.document_id, "documentNodeAfter": document["nodeId"],
                "htmlNodeBefore": self.html_id, "htmlNodeAfter": html["nodeId"] if html else None,
                "documentBackendBefore": document_backend, "documentBackendAfter": document["backendNodeId"],
                "htmlBackendBefore": html_backend, "htmlBackendAfter": html["backendNodeId"] if html else None,
                "pageUrlMatches": self.page.url == self.url, "documentUrlMatches": document.get("documentURL") == self.url,
                "stateMatches": self.state == state, "attributeMatches": attributes.get(self.attribute) == state}
            if (self.session_lost or changes != self.state_changes or
                    html_backend != self.html_backend or
                    self.state != state or self.page.url != self.url or
                    document.get("documentURL") != self.url or
                    document_backend != self.document_backend or document["backendNodeId"] != document_backend or
                    html is None or html["backendNodeId"] != html_backend or attributes.get(self.attribute) != state):
                raise ValueError("Terminal state/report did not retain their exact HTML document generation")
            self.terminal_verified = True
        except asyncio.CancelledError:
            raise
        except Exception as error:
            self.failure("terminal-document-scope", error)

    async def close(self):
        # Stop event work before awaiting anything: no successor task can escape.
        self.closed = True
        self.html_ready.set()
        try:
            await asyncio.wait_for(self.page.unroute(self.script_pattern, self.script_handler),
                                   MAX_ATTRIBUTE_TIMEOUT_MS / 1000)
        except Exception as error:
            self.failure("compiled-script-route-cleanup", error)
        pending_routes = set(self.route_tasks)
        for task in pending_routes:
            task.cancel()
        if pending_routes:
            _, survivors = await asyncio.wait(pending_routes, timeout=MAX_ATTRIBUTE_TIMEOUT_MS / 1000)
            if survivors:
                self.failure("compiled-script-task-cleanup", TimeoutError("Owned script route survived bounded cleanup"))
        if self.session is not None:
            for event, handler in self.listeners:
                try:
                    self.session.remove_listener(event, handler)
                except Exception as error:
                    self.failure("DOM.listener-cleanup", error)
        pending = set(self.tasks)
        for task in pending:
            task.cancel()
        if pending:
            _, survivors = await asyncio.wait(pending, timeout=MAX_ATTRIBUTE_TIMEOUT_MS / 1000)
            if survivors:
                self.failure("document-task-cleanup", TimeoutError("Owned DOM query survived bounded cancellation"))
        if self.session is not None:
            try:
                await asyncio.wait_for(self.session.detach(), MAX_ATTRIBUTE_TIMEOUT_MS / 1000)
            except Exception as error:
                self.failure("DOM.detach", error)


async def run_case(page, base_url, case, output):
    started = time.monotonic()
    started_utc = utc_now()
    deadline = started + case["timeoutSeconds"]
    page_errors = []
    http_errors = []
    console_errors = []
    read_errors = []
    observer_errors = []
    listener_cleanup_errors = []
    navigation_error = None
    last_observed_state = None
    terminal_reached = False
    timed_out = False

    def on_page_error(error):
        page_errors.append(str(error))

    def on_response(response):
        try:
            if response.status >= 400:
                http_errors.append({"url": response.url,
                                    "status": response.status,
                                    "statusText": response.status_text})
        except BaseException as error:
            http_errors.append({"error": str(error)})

    def on_console(message):
        try:
            if message.type == "error":
                location = message.location if isinstance(message.location, dict) else None
                console_errors.append({"type": message.type, "text": message.text,
                                       "location": location})
        except BaseException as error:
            console_errors.append({"error": str(error)})

    page.on("pageerror", on_page_error)
    page.on("response", on_response)
    page.on("console", on_console)
    url = base_url + "/" + case["path"]

    async def read_attribute(attribute):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            read_errors.append({"attribute": attribute, "type": "DeadlineExpired",
                                "message": "case deadline elapsed"})
            return None
        try:
            return await page.locator("html").get_attribute(
                attribute, timeout=max(1, min(MAX_ATTRIBUTE_TIMEOUT_MS,
                                             int(remaining * 1000))))
        except asyncio.CancelledError:
            raise
        except BaseException as error:
            read_errors.append({"attribute": attribute, "type": type(error).__name__,
                                "message": str(error)})
            return None

    observer = HtmlAttributeObserver(page, url, case["stateAttribute"], deadline, observer_errors)
    report = report_file = report_json_valid = report_json_error = None
    try:
        try:
            await observer.start()
        except asyncio.CancelledError:
            raise
        except BaseException as error:
            observer.failure("DOM.start", error)
        if not observer_errors:
            try:
                response = await page.goto(url, wait_until="domcontentloaded",
                    timeout=max(1, int(max(0, deadline - time.monotonic()) * 1000)))
                if response is not None and response.status >= 400 and not any(
                        row.get("url") == response.url and row.get("status") == response.status
                        for row in http_errors):
                    http_errors.append({"url": response.url, "status": response.status,
                                        "statusText": response.status_text})
            except PlaywrightTimeoutError as error:
                navigation_error = {"type": type(error).__name__, "message": str(error)}
                timed_out = True
            except asyncio.CancelledError:
                raise
            except BaseException as error:
                navigation_error = {"type": type(error).__name__, "message": str(error)}

        if navigation_error is None and not observer_errors:
            while time.monotonic() < deadline and not observer_errors:
                state = observer.state
                if state is not None:
                    last_observed_state = state
                    if any(state.startswith(prefix) for prefix in case["terminalPrefixes"]):
                        terminal_reached = True
                        terminal_generation = observer.version
                        terminal_document_backend = observer.document_backend
                        terminal_html_backend = observer.html_backend
                        terminal_state_changes = observer.state_changes
                        break
                await asyncio.sleep(min(POLL_SECONDS, max(0, deadline - time.monotonic())))
            if not terminal_reached and not observer_errors:
                timed_out = True
        if terminal_reached:
            state = await read_attribute(case["stateAttribute"])
            if state != last_observed_state:
                observer.failure("terminal-state-read", ValueError(
                    "DOM terminal state disagrees with the bounded HTML read"))
            if state is not None:
                last_observed_state = state
        elif navigation_error is not None:
            state = await read_attribute(case["stateAttribute"])
            if state is not None:
                last_observed_state = state

        if case["reportAttribute"] is not None:
            report = await read_attribute(case["reportAttribute"])
            if report is not None:
                report_file = save_raw(output, case_slug(case) + ".report.json", report)
                try:
                    json.loads(report)
                    report_json_valid = True
                except (TypeError, ValueError) as error:
                    report_json_valid = False
                    report_json_error = str(error)
        if last_observed_state is not None:
            save_raw(output, case_slug(case) + ".state.txt", last_observed_state)
        if terminal_reached:
            await observer.verify_terminal_scope(terminal_generation, terminal_document_backend, terminal_html_backend,
                                                 last_observed_state, terminal_state_changes)
    finally:
        # Keep all error listeners active through final state/report reads.
        await observer.close()
        for event, handler in (("pageerror", on_page_error),
                               ("response", on_response),
                               ("console", on_console)):
            try:
                page.remove_listener(event, handler)
            except BaseException as error:
                listener_cleanup_errors.append({"event": event,
                    "type": type(error).__name__, "message": str(error)})

    state_match = terminal_reached and expected_matches(case, last_observed_state)
    report_match = (case["reportAttribute"] is None or
                    (report is not None and report_json_valid is True))
    browser_error = bool(page_errors or http_errors or console_errors or
                         read_errors or listener_cleanup_errors or observer_errors)
    expected_failure_canary = (case["expectedState"] or case["expectedPrefix"]).startswith("FAIL")
    if timed_out:
        outcome = "TIMEOUT"
    elif navigation_error is not None:
        outcome = "FAIL"
    elif state_match and report_match and not browser_error:
        outcome = "PASS"
    else:
        outcome = "FAIL"
    finished = time.monotonic()
    receipt = {
        "schema": SCRIPT_SCHEMA,
        "case": {key: case[key] for key in (
            "index", "name", "path", "stateAttribute", "expectedState",
            "expectedPrefix", "terminalPrefixes", "timeoutSeconds", "reportAttribute")},
        "url": url,
        "startedUtc": started_utc,
        "finishedUtc": utc_now(),
        "operationSeconds": finished - started,
        "outcome": outcome,
        "terminalReached": terminal_reached,
        "stateMatch": state_match,
        "observedState": last_observed_state,
        "lastObservedState": last_observed_state,
        "timedOut": timed_out,
        "expectedFailureCanary": expected_failure_canary,
        "expectedFailureStateWasNotAnUnexpectedError": expected_failure_canary and state_match,
        "reportObserved": report is not None,
        "reportJsonValid": report_json_valid,
        "reportJsonError": report_json_error,
        "reportMatch": report_match,
        "reportLength": len(report) if report is not None else 0,
        "reportSha256": hashlib.sha256(report.encode("utf-8")).hexdigest()
        if report is not None else None,
        "reportFile": str(report_file.relative_to(output)).replace("\\", "/")
        if report_file is not None else None,
        "pageErrors": page_errors,
        "httpErrors": http_errors,
        "consoleErrors": console_errors,
        "attributeReadErrors": read_errors,
        "observerErrors": list(observer_errors),
        "observation": {"transport": "PASSIVE_CDP_DOM", "stateEvents": observer.event_count,
                        "compiledScriptsHeld": observer.script_requests,
                        "scriptAttachment": observer.script_attachment,
                        "currentDocuments": observer.document_count,
                        "finalStateReadRequired": terminal_reached,
                        "terminalScopeVerified": observer.terminal_verified,
                        "scopeIdentity": getattr(observer, "scope_identity", None)},
        "listenerCleanupErrors": listener_cleanup_errors,
        "navigationError": navigation_error,
    }
    save_json(output, case_slug(case) + ".json", receipt)
    return receipt


def validate_output(output, repo):
    if not output.is_absolute():
        raise ValueError("output directory must be an absolute path")
    output = output.resolve()
    temporary = Path(tempfile.gettempdir()).resolve()
    if not is_within(output, temporary):
        raise ValueError("output directory must be below the OS temporary directory")
    if is_within(output, repo):
        raise ValueError("output directory must be outside the repository")
    if output.exists():
        raise ValueError("output directory must not already exist")
    if not output.parent.is_dir():
        raise ValueError("output directory parent must already exist")
    return output


def save_error(result, phase, error):
    result["errors"].append({"phase": phase, "message": str(error),
                             "traceback": traceback.format_exc()})


async def main(argv):
    if len(argv) != 4:
        print("usage: compiled_attribute_acceptance.py REPO OUTPUT SPEC_JSON", file=sys.stderr)
        return 2
    try:
        repo = Path(argv[1]).expanduser().resolve()
        output = validate_output(Path(argv[2]).expanduser(), repo)
        spec_path = Path(argv[3]).expanduser().resolve()
        if not repo.is_dir() or not (repo / "war" / "circuitjs.html").is_file():
            raise ValueError("repository war/circuitjs.html is missing")
        if not spec_path.is_file():
            raise ValueError("spec JSON path is not a file")
        spec, raw_spec, cases = load_spec(spec_path)
    except (OSError, ValueError) as error:
        print("ERROR", str(error), file=sys.stderr)
        return 2

    try:
        output.mkdir()
    except OSError as error:
        print("ERROR unable to create unique output directory: " + str(error), file=sys.stderr)
        return 2
    (output / "spec.json").write_bytes(raw_spec)
    result = {"schema": SCRIPT_SCHEMA, "runner": "compiled_attribute_acceptance.py",
              "repository": str(repo), "startedUtc": utc_now(), "outcome": "NOT_RUN",
              "cases": [], "caseCount": len(cases), "errors": []}
    save_json(output, "run-start.json", result)

    server = None
    server_thread = None
    playwright = None
    context = None
    owned = []
    input_before = None
    runner_before = None
    base_url = None
    profile = output / "profile"

    try:
        input_before = capture_input_manifest(repo)
        runner_before = capture_script_identity(repo)
        save_json(output, "input-manifest-before.json", input_before)
        save_json(output, "runner-before.json", runner_before)
        result["inputAudit"] = {"status": "BEFORE_CAPTURED",
                                 "beforeSha256": input_before["sha256"],
                                 "sourceSha256": input_before["sourceSha256"],
                                 "webSha256": input_before["webSha256"],
                                 "fileCount": input_before["fileCount"]}

        server = ThreadingHTTPServer(("127.0.0.1", 0),
                                     partial(QuietHandler, directory=str(repo / "war")))
        server_thread = Thread(target=server.serve_forever, daemon=True,
                               name="compiled-attribute-http")
        server_thread.start()
        base_url = "http://127.0.0.1:%d" % server.server_port

        if async_playwright is None:
            raise RuntimeError("Playwright is not installed on this host")
        playwright = await async_playwright().start()
        context = await playwright.chromium.launch_persistent_context(
            str(profile), channel="msedge", headless=True,
            viewport={"width": 1440, "height": 1000},
            args=["--disable-background-timer-throttling",
                  "--disable-renderer-backgrounding"])
        rows = process_rows()
        owned = owned_edge_rows(rows)
        owner = next((row for row in rows if int(row.get("pid", -1)) == os.getpid()), None)
        if owner is None:
            raise RuntimeError("runner process identity was not returned")
        identity = {
            "schema": SCRIPT_SCHEMA,
            "runner": {"pid": owner.get("pid"), "created": owner.get("created"),
                        "executable": owner.get("ExecutablePath")},
            "edgeProcessesBeforeCases": owned,
            "profile": str(profile),
            "port": server.server_port,
            "baseUrl": base_url,
        }
        save_json(output, "identity.json", identity)
        save_json(output, "owned-processes-before.json", owned)
        if not owned:
            raise RuntimeError("no Edge descendant was observed before browser gates")
        page = context.pages[0] if context.pages else await context.new_page()
        result["identity"] = identity
        result["operationStartedUtc"] = utc_now()
        for case in cases:
            receipt = await run_case(page, base_url, case, output)
            result["cases"].append(receipt)
            save_json(output, "progress.json", result)
            print(receipt["outcome"], case["name"],
                  round(receipt["operationSeconds"], 3), flush=True)
        result["outcome"] = ("PASS" if all(row["outcome"] == "PASS"
                                           for row in result["cases"])
                              else "FAIL")
    except BaseException as error:
        result["outcome"] = "FAIL"
        save_error(result, "operation", error)
        print(traceback.format_exc(), file=sys.stderr, flush=True)
    finally:
        audit_started = time.monotonic()
        try:
            input_after = capture_input_manifest(repo)
            runner_after = capture_script_identity(repo)
            save_json(output, "input-manifest-after.json", input_after)
            save_json(output, "runner-after.json", runner_after)
            unchanged = (input_before is not None and
                         input_before["sha256"] == input_after["sha256"] and
                         runner_before is not None and
                         runner_before["sha256"] == runner_after["sha256"])
            result["inputAudit"] = {
                "status": "PASS" if unchanged else "FAIL",
                "beforeSha256": input_before["sha256"] if input_before else None,
                "afterSha256": input_after["sha256"],
                "sourceSha256Before": input_before["sourceSha256"] if input_before else None,
                "sourceSha256After": input_after["sourceSha256"],
                "webSha256Before": input_before["webSha256"] if input_before else None,
                "webSha256After": input_after["webSha256"],
                "runnerBeforeSha256": runner_before["sha256"] if runner_before else None,
                "runnerAfterSha256": runner_after["sha256"],
                "fileCountBefore": input_before["fileCount"] if input_before else None,
                "fileCountAfter": input_after["fileCount"],
                "seconds": time.monotonic() - audit_started,
            }
            if not unchanged and result["outcome"] == "PASS":
                result["outcome"] = "FAIL_INPUT_MUTATION"
        except BaseException as error:
            result["inputAudit"] = {"status": "FAIL",
                                     "seconds": time.monotonic() - audit_started}
            save_error(result, "input-after", error)
            if result["outcome"] == "PASS":
                result["outcome"] = "FAIL_INPUT_AUDIT"

        cleanup_started = time.monotonic()
        cleanup_errors = []
        try:
            if context is not None:
                try:
                    rows = process_rows()
                    owned = list({(row.get("pid"), row.get("created")): row
                                  for row in owned + owned_edge_rows(rows)}.values())
                    save_json(output, "owned-processes-before-close.json", owned)
                except BaseException as error:
                    cleanup_errors.append({"phase": "process-snapshot", "message": str(error)})
                try:
                    await asyncio.wait_for(context.close(), timeout=30)
                except BaseException as error:
                    cleanup_errors.append({"phase": "context-close", "message": str(error)})
            if playwright is not None:
                try:
                    await asyncio.wait_for(playwright.stop(), timeout=30)
                except BaseException as error:
                    cleanup_errors.append({"phase": "playwright-stop", "message": str(error)})
        finally:
            if server is not None:
                try:
                    server.shutdown()
                    server.server_close()
                except BaseException as error:
                    cleanup_errors.append({"phase": "server-shutdown", "message": str(error)})
            if server_thread is not None:
                server_thread.join(timeout=5)

        survivors = []
        saved_ids = {(row.get("pid"), row.get("created")) for row in owned}
        for _ in range(20):
            try:
                survivors = [row for row in process_rows()
                             if (row.get("pid"), row.get("created")) in saved_ids]
            except BaseException as error:
                cleanup_errors.append({"phase": "survivor-query", "message": str(error)})
                survivors = [{"error": str(error)}]
            if not survivors:
                break
            await asyncio.sleep(0.25)
        server_stopped = server_thread is None or not server_thread.is_alive()
        cleanup_ok = not cleanup_errors and not survivors and server_stopped
        result["cleanup"] = {
            "status": "PASS" if cleanup_ok else "FAIL",
            "seconds": time.monotonic() - cleanup_started,
            "serverStopped": server_stopped,
            "ownedSurvivors": survivors,
            "errors": cleanup_errors,
        }
        if not cleanup_ok:
            result["outcome"] = "FAIL_CLEANUP"
        result["finishedUtc"] = utc_now()
        save_json(output, "result.json", result)
        print("FINISHED", result["outcome"], "cases", len(result["cases"]), flush=True)

    return 0 if result["outcome"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(asyncio.run(main(sys.argv)))
