"""Host-driven acceptance checks for compiled developer attributes.

Each case navigates Edge to a relative ``circuitjs.html`` URL and reads
attributes from ``html`` until a declared terminal state appears.  The runner
does not inject JavaScript or call application controllers.  Its output is
retained for audit and replay; this script never removes it.

Usage::

    python normal_screen_host.py REPO OUTPUT SPEC_JSON

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


# Prefer a task-local Playwright install when present, without changing the
# shared Python runtime.  A system installation remains supported as a fallback.
HOST_DEPENDENCIES = Path(__file__).resolve().parent / "deps"
if (HOST_DEPENDENCIES / "playwright").is_dir():
    sys.path.insert(0, str(HOST_DEPENDENCIES))


try:
    from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
    from playwright.sync_api import sync_playwright
except ImportError:  # Validation still works on a host without Playwright.
    PlaywrightTimeoutError = TimeoutError
    sync_playwright = None


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

    def end_headers(self):
        # Keep each compiled-resource navigation independently auditable. A
        # cached response may be revalidated as 304, which has no body for the
        # response listener to hash.
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

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
    query = ("Get-CimInstance Win32_Process | Select-Object "
             "@{n='pid';e={$_.ProcessId}},@{n='parent';e={$_.ParentProcessId}},"
             "@{n='created';e={$_.CreationDate.ToUniversalTime().ToString('o')}},"
             "ExecutablePath | ConvertTo-Json -Compress")
    result = subprocess.run(["powershell.exe", "-NoProfile", "-Command", query],
                            capture_output=True, text=True, timeout=20)
    if result.returncode:
        raise RuntimeError("process identity query failed: " + result.stderr.strip())
    if not result.stdout.strip():
        return []
    rows = json.loads(result.stdout)
    return [rows] if isinstance(rows, dict) else rows


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


def run_case(page, base_url, case, output):
    started = time.monotonic()
    started_utc = utc_now()
    deadline = started + case["timeoutSeconds"]
    page_errors = []
    http_errors = []
    console_errors = []
    read_errors = []
    listener_cleanup_errors = []
    compiled_resources = []
    compiled_resource_errors = []
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
            resource_path = urlsplit(response.url).path
            resource_name = Path(resource_path).name
            resource_kind = next((suffix for suffix in ("cache.js", "cache.html")
                                  if resource_name.endswith("." + suffix)), None)
            if resource_kind is not None:
                resource = {
                    "url": response.url,
                    "permutation": resource_name[:-len("." + resource_kind)],
                    "kind": resource_kind,
                    "status": response.status,
                }
                try:
                    body = response.body()
                    resource["length"] = len(body)
                    resource["sha256"] = hashlib.sha256(body).hexdigest()
                except BaseException as error:
                    resource["bodyError"] = {"type": type(error).__name__,
                                              "message": str(error)}
                    compiled_resource_errors.append({"url": response.url,
                                                     **resource["bodyError"]})
                compiled_resources.append(resource)
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

    def read_attribute(attribute):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            read_errors.append({"attribute": attribute, "type": "DeadlineExpired",
                                "message": "case deadline elapsed"})
            return None
        try:
            return page.locator("html").get_attribute(
                attribute, timeout=max(1, min(MAX_ATTRIBUTE_TIMEOUT_MS,
                                             int(remaining * 1000))))
        except BaseException as error:
            read_errors.append({"attribute": attribute, "type": type(error).__name__,
                                "message": str(error)})
            return None

    try:
        try:
            page.bring_to_front()
            response = page.goto(url, wait_until="domcontentloaded",
                                 timeout=max(1, int(case["timeoutSeconds"] * 1000)))
            if response is not None and response.status >= 400 and not any(
                    row.get("url") == response.url and row.get("status") == response.status
                    for row in http_errors):
                http_errors.append({"url": response.url, "status": response.status,
                                    "statusText": response.status_text})
        except PlaywrightTimeoutError as error:
            navigation_error = {"type": type(error).__name__, "message": str(error)}
            timed_out = True
        except BaseException as error:
            navigation_error = {"type": type(error).__name__, "message": str(error)}

        if navigation_error is None:
            while time.monotonic() < deadline:
                state = read_attribute(case["stateAttribute"])
                if state is not None:
                    last_observed_state = state
                    if any(state.startswith(prefix) for prefix in case["terminalPrefixes"]):
                        terminal_reached = True
                        break
                time.sleep(min(POLL_SECONDS, max(0, deadline - time.monotonic())))
            if not terminal_reached:
                timed_out = True
        else:
            # Preserve the last state available after a navigation error without
            # attempting application-side recovery.
            state = read_attribute(case["stateAttribute"])
            if state is not None:
                last_observed_state = state
    finally:
        # The event lists are owned by this case.  Detach handlers before the
        # receipt is returned so later navigations cannot mutate old results.
        for event, handler in (("pageerror", on_page_error),
                               ("response", on_response),
                               ("console", on_console)):
            try:
                page.remove_listener(event, handler)
            except BaseException as error:
                listener_cleanup_errors.append({"event": event,
                                                "type": type(error).__name__,
                                                "message": str(error)})

    report = None
    report_file = None
    report_json_valid = None
    report_json_error = None
    if case["reportAttribute"] is not None:
        report = read_attribute(case["reportAttribute"])
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

    # Captured after the application terminal receipt; never inside its budget.
    screenshot_file = None
    if terminal_reached:
        try:
            screenshot_file = case_slug(case) + ".png"
            page.screenshot(path=str(output / screenshot_file), timeout=10000)
        except BaseException as error:
            read_errors.append({"phase": "terminal-screenshot", "message": str(error)})

    state_match = terminal_reached and expected_matches(case, last_observed_state)
    report_match = (case["reportAttribute"] is None or
                    (report is not None and report_json_valid is True))
    browser_error = bool(page_errors or http_errors or console_errors or
                         read_errors or listener_cleanup_errors or
                         compiled_resource_errors)
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
        "listenerCleanupErrors": listener_cleanup_errors,
        "navigationError": navigation_error,
        "compiledResources": compiled_resources,
        "compiledResourceErrors": compiled_resource_errors,
        "terminalScreenshot": screenshot_file,
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


def main(argv):
    if len(argv) != 4:
        print("usage: normal_screen_host.py REPO OUTPUT SPEC_JSON", file=sys.stderr)
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
    result = {"schema": SCRIPT_SCHEMA, "runner": "normal_screen_host.py",
              "repository": str(repo), "startedUtc": utc_now(), "outcome": "NOT_RUN",
              "cases": [], "caseCount": len(cases), "errors": [],
              "browserStartupMilliseconds": None}
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
    browser_startup_milliseconds = None

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

        if sync_playwright is None:
            raise RuntimeError("Playwright is not installed on this host")
        playwright = sync_playwright().start()
        if profile.exists():
            raise RuntimeError("isolated browser profile path already exists")
        browser_start_started = time.monotonic()
        try:
            context = playwright.chromium.launch_persistent_context(
                str(profile), channel="msedge", headless=False,
                viewport={"width": 1440, "height": 1000})
        finally:
            browser_startup_milliseconds = (time.monotonic() - browser_start_started) * 1000
            result["browserStartupMilliseconds"] = browser_startup_milliseconds
        page = context.pages[0] if context.pages else context.new_page()
        browser = context.browser
        if browser is None:
            raise RuntimeError("Edge browser identity was unavailable")
        browser_identity = {
            "name": "Microsoft Edge",
            "version": browser.version,
            "userAgent": page.evaluate("navigator.userAgent"),
        }
        if not browser_identity["version"] or not browser_identity["userAgent"]:
            raise RuntimeError("Edge version or user agent was unavailable")
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
            "browser": browser_identity,
            "browserStartupMilliseconds": browser_startup_milliseconds,
        }
        save_json(output, "identity.json", identity)
        save_json(output, "owned-processes-before.json", owned)
        if not owned:
            raise RuntimeError("no Edge descendant was observed before browser gates")
        result["identity"] = identity
        result["operationStartedUtc"] = utc_now()
        for case in cases:
            receipt = run_case(page, base_url, case, output)
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
                    owned = list({(row.get("pid"), row.get("created"),
                                   row.get("ExecutablePath")): row
                                  for row in owned + owned_edge_rows(rows)}.values())
                    save_json(output, "owned-processes-before-close.json", owned)
                except BaseException as error:
                    cleanup_errors.append({"phase": "process-snapshot", "message": str(error)})
                try:
                    context.close()
                except BaseException as error:
                    cleanup_errors.append({"phase": "context-close", "message": str(error)})
            if playwright is not None:
                try:
                    playwright.stop()
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
        saved_ids = {(row.get("pid"), row.get("created"),
                      row.get("ExecutablePath")) for row in owned}
        for _ in range(20):
            try:
                survivors = [row for row in process_rows()
                             if (row.get("pid"), row.get("created"),
                                 row.get("ExecutablePath")) in saved_ids]
            except BaseException as error:
                cleanup_errors.append({"phase": "survivor-query", "message": str(error)})
                survivors = [{"error": str(error)}]
            if not survivors:
                break
            time.sleep(0.25)
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
    sys.exit(main(sys.argv))
