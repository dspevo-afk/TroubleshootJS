"""Bounded phases for the private-enabled Q30 qualification copy.

This module never edits the checkout.  Its only source change is an exact
false-to-true registration in an exclusive OS-temp copy; the public 1,355-file
snapshot remains the authority for preparation, build, menu, and archive.
"""
from __future__ import annotations

import gzip
import hashlib
import importlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import tarfile
import tempfile
import time


SCHEMA = 1
RAW_INPUT_COUNT = 1355
COMPILED_FILE_COUNT = 357
DEPLOYMENT_FILE_COUNT = 6
GWT_JAR_COUNT = 9
BUILD_TIMEOUT_MS = 900_000
MENU_TIMEOUT_MS = 1_100_000
CATALOG_PATH = "src/com/lushprojects/circuitjs1/client/PlayerFamilyCatalog.java"
CATALOG_FALSE = b"result.registerStagedFamily(new Rb30PlayerFamilyCapability(), false);"
CATALOG_TRUE = b"result.registerStagedFamily(new Rb30PlayerFamilyCapability(), true);"
GWT_PIN_PATH = "tests/qualification/q30-scale-acceptance/gwt-jars.json"
PRIVATE_MENU_ORIGIN_SHA256 = "75e79a0ba5bcb1e3365aee413f0fd94f25d560e08e1c9cd8816896633c3e5b1a"
HASH_RE = re.compile(r"^[0-9a-f]{64}$")
PERMUTATION_RE = re.compile(r"^war/circuitjs1/([0-9A-F]{32})\.cache\.js$")
INPUT_ROOTS = ("src", "war", "scripts", "tests")
EXCLUDED_DIRS = {
    ".git", ".tools", "build", "target", "node_modules", "__pycache__",
    ".pytest_cache", ".mypy_cache", ".ruff_cache", ".cache",
}
GENERATED_PREFIXES = ("war/circuitjs1", "war/WEB-INF/deploy")
WINDOWS_REPARSE = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)


class QualificationError(RuntimeError):
    pass


def _sha_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _sha_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _need(condition, message: str) -> None:
    if not condition:
        raise QualificationError(message)


def _json_bytes(value) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"),
                      ensure_ascii=False).encode("utf-8")


def _load_json(path: Path, label: str) -> tuple[dict, bytes]:
    try:
        raw = path.read_bytes()
        value = json.loads(raw.decode("utf-8"), object_pairs_hook=_unique_keys,
                           parse_constant=lambda item: (_ for _ in ()).throw(
                               QualificationError("non-finite JSON value: " + item)))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise QualificationError(label + " is unreadable or invalid JSON: " + str(error)) from error
    _need(isinstance(value, dict), label + " must be an object")
    return value, raw


def _unique_keys(pairs):
    value = {}
    for key, item in pairs:
        if key in value:
            raise QualificationError("duplicate JSON key: " + key)
        value[key] = item
    return value


def _write_json_exclusive(path: Path, value: dict) -> None:
    encoded = (json.dumps(value, sort_keys=True, indent=2,
                          ensure_ascii=False) + "\n").encode("utf-8")
    with path.open("xb") as stream:
        stream.write(encoded)
        stream.flush()
        os.fsync(stream.fileno())


def _is_reparse(path: Path) -> bool:
    try:
        return bool(path.lstat().st_file_attributes & WINDOWS_REPARSE)
    except (AttributeError, OSError):
        return path.is_symlink()


def _reject_reparse(path: Path, include_missing: bool = False) -> None:
    cursor = path
    while True:
        if cursor.exists() or cursor.is_symlink() or include_missing:
            _need(not _is_reparse(cursor), "reparse point or symlink is not allowed: " + str(cursor))
        if cursor.parent == cursor:
            break
        cursor = cursor.parent


def _inside(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def _safe_relative(text: str) -> PurePosixPath:
    _need(isinstance(text, str) and text and "\\" not in text,
          "snapshot path must be a non-empty slash-separated string")
    value = PurePosixPath(text)
    _need(not value.is_absolute() and value.parts and
          all(part not in ("", ".", "..") for part in value.parts) and
          value.parts[0] in INPUT_ROOTS,
          "snapshot path is outside allowed raw inputs: " + text)
    _need(not any(part in EXCLUDED_DIRS for part in value.parts),
          "snapshot path names an excluded generated/cache tree: " + text)
    _need(not any(text == prefix or text.startswith(prefix + "/")
                  for prefix in GENERATED_PREFIXES),
          "snapshot path names generated GWT output: " + text)
    return value


def _paths(manifest: dict, output_dir) -> tuple[Path, Path, dict]:
    _need(isinstance(manifest, dict), "manifest must be an object")
    repo = Path(manifest["repo"]).resolve(strict=True)
    root = Path(manifest["tempRoot"]).resolve(strict=True)
    output = Path(output_dir).resolve(strict=True)
    _need(root == output or output.parent == root,
          "phase output_dir must be the owned tempRoot or one direct phase child")
    temporary = Path(tempfile.gettempdir()).resolve(strict=True)
    _need(_inside(root, temporary) and not _inside(root, repo),
          "tempRoot must be OS-temp owned and outside the repository")
    _reject_reparse(repo)
    _reject_reparse(root)
    tools = manifest.get("tools")
    _need(isinstance(tools, dict), "manifest tools are missing")
    return repo, root, tools


def _snapshot(manifest: dict, repo: Path) -> tuple[dict, list[dict]]:
    binding = manifest.get("sourceSnapshot")
    _need(isinstance(binding, dict) and set(binding) == {"path", "sha256"},
          "sourceSnapshot must contain exactly path and sha256")
    expected_raw_hash = binding.get("sha256")
    _need(isinstance(expected_raw_hash, str) and HASH_RE.fullmatch(expected_raw_hash),
          "source snapshot SHA-256 pin is malformed")
    path = Path(binding["path"]).resolve(strict=True)
    _need(path.is_absolute(), "source snapshot path must be absolute")
    _reject_reparse(path)
    value, raw = _load_json(path, "source snapshot")
    _need(_sha_bytes(raw) == expected_raw_hash, "source snapshot bytes differ from manifest pin")
    _need(set(value) == {"schema", "baseHead", "files", "sourceIdentity"} and
          value.get("schema") == 1,
          "source snapshot must use the maintained four-key schema")
    rows = value.get("files")
    _need(isinstance(rows, list) and len(rows) == RAW_INPUT_COUNT,
          "source snapshot must contain exactly 1,355 raw input rows")
    paths = []
    for row in rows:
        _need(isinstance(row, dict) and set(row) == {"path", "sha256", "size"},
              "source snapshot row has an unexpected schema")
        _safe_relative(row["path"])
        _need(isinstance(row["sha256"], str) and HASH_RE.fullmatch(row["sha256"]) and
              type(row["size"]) is int and row["size"] >= 0,
              "source snapshot row has an invalid size or hash")
        paths.append(row["path"])
    _need(paths == sorted(paths) and len(set(paths)) == RAW_INPUT_COUNT,
          "source snapshot paths must be unique and canonically ordered")
    payload = {"schema": 1, "baseHead": value.get("baseHead"), "files": rows}
    _need(isinstance(value.get("baseHead"), str) and value["baseHead"] and
          _sha_bytes(_json_bytes(payload)) == value.get("sourceIdentity"),
          "source snapshot identity does not match its 1,355 rows")
    source_binding = manifest.get("sourceBinding")
    if source_binding is not None:
        _need(isinstance(source_binding, dict) and
              source_binding.get("baseHead") == value["baseHead"] and
              source_binding.get("sourceIdentity") == value["sourceIdentity"] and
              source_binding.get("sourceFiles") == RAW_INPUT_COUNT,
              "manifest source binding differs from the pinned source snapshot")
    return {"path": str(path), "sha256": expected_raw_hash, **value}, rows


def _raw_inventory(root: Path) -> list[dict]:
    found = []
    for root_name in INPUT_ROOTS:
        base = root / root_name
        _need(base.is_dir() and not _is_reparse(base),
              "raw input root is missing or unsafe: " + root_name)
        for directory, directory_names, file_names in os.walk(base, topdown=True,
                                                               followlinks=False):
            current = Path(directory)
            kept = []
            for name in sorted(directory_names):
                child = current / name
                relative = child.relative_to(root).as_posix()
                if name in EXCLUDED_DIRS or any(
                        relative == prefix or relative.startswith(prefix + "/")
                        for prefix in GENERATED_PREFIXES):
                    continue
                _need(not child.is_symlink() and not _is_reparse(child),
                      "raw input tree contains a symlink/reparse directory: " + relative)
                kept.append(name)
            directory_names[:] = kept
            for name in sorted(file_names):
                child = current / name
                relative = child.relative_to(root).as_posix()
                _safe_relative(relative)
                _need(child.is_file() and not child.is_symlink() and not _is_reparse(child),
                      "raw input is not a regular file: " + relative)
                found.append({"path": relative, "sha256": _sha_file(child),
                              "size": child.stat().st_size})
    return sorted(found, key=lambda row: row["path"])


def _compare_inventory(actual: list[dict], rows: list[dict], catalog_after: dict | None,
                       label: str) -> str:
    _need(len(actual) == len(rows) == RAW_INPUT_COUNT,
          label + " does not contain the exact 1,355 raw-input paths")
    _need([row["path"] for row in actual] == [row["path"] for row in rows],
          label + " path inventory differs from the source snapshot")
    for observed, expected in zip(actual, rows):
        digest = expected["sha256"]
        size = expected["size"]
        if catalog_after and expected["path"] == CATALOG_PATH:
            digest, size = catalog_after["afterSha256"], catalog_after["afterSize"]
        _need(observed["sha256"] == digest and observed["size"] == size,
              label + " raw input changed: " + expected["path"])
    return _sha_bytes(_json_bytes(actual))


def _catalog_expected(repo: Path, rows: list[dict]) -> tuple[bytes, bytes, dict]:
    row = next((item for item in rows if item["path"] == CATALOG_PATH), None)
    _need(row is not None, "source snapshot omits the staged Q30 catalog registration")
    source = repo.joinpath(*PurePosixPath(CATALOG_PATH).parts)
    _need(source.is_file() and not source.is_symlink() and not _is_reparse(source),
          "public catalog input is missing or unsafe")
    original = source.read_bytes()
    _need(len(original) == row["size"] and _sha_bytes(original) == row["sha256"],
          "public catalog bytes differ from the source snapshot")
    _need(original.count(CATALOG_FALSE) == 1 and original.count(CATALOG_TRUE) == 0,
          "expected exactly one disabled Q30 catalog registration")
    enabled = original.replace(CATALOG_FALSE, CATALOG_TRUE, 1)
    delta = {
        "path": CATALOG_PATH,
        "registrationBefore": False,
        "registrationAfter": True,
        "replacementCount": 1,
        "beforeSha256": _sha_bytes(original),
        "beforeSize": len(original),
        "afterSha256": _sha_bytes(enabled),
        "afterSize": len(enabled),
    }
    return original, enabled, delta


def _verify_repo(repo: Path, rows: list[dict]) -> str:
    actual = _raw_inventory(repo)
    return _compare_inventory(actual, rows, None, "public repository")


def _verify_private_app(repo: Path, app: Path, rows: list[dict],
                        delta: dict) -> str:
    original, enabled, exact_delta = _catalog_expected(repo, rows)
    _need(delta == exact_delta, "private catalog delta metadata is not the exact false-to-true edit")
    catalog = app.joinpath(*PurePosixPath(CATALOG_PATH).parts)
    _need(catalog.is_file() and not catalog.is_symlink() and
          catalog.read_bytes() == enabled,
          "private catalog differs from the single authorized enablement delta")
    actual = _raw_inventory(app)
    return _compare_inventory(actual, rows, delta, "private source copy")


def _jar_pins(manifest: dict, app: Path, rows: list[dict]) -> list[dict]:
    tools = manifest["tools"]
    gwt_home = Path(tools["gwtHome"]).resolve(strict=True)
    _reject_reparse(gwt_home)
    source_pin = app.joinpath(*PurePosixPath(GWT_PIN_PATH).parts)
    source_row = next((row for row in rows if row["path"] == GWT_PIN_PATH), None)
    _need(source_row is not None and source_pin.is_file() and
          _sha_file(source_pin) == source_row["sha256"],
          "source-bound nine-jar pin file is missing or changed")
    pin_file, _ = _load_json(source_pin, "source GWT jar pin file")
    names = pin_file.get("gwtJars")
    _need(pin_file.get("schema") == 1 and isinstance(names, dict) and
          len(names) == GWT_JAR_COUNT,
          "source-bound GWT jar pin set is not exactly nine jars")

    supplied = tools.get("gwtJars")
    _need(isinstance(supplied, list) and len(supplied) == GWT_JAR_COUNT,
          "manifest tools.gwtJars must pin nine absolute jar paths")
    by_name = {}
    for row in supplied:
        _need(isinstance(row, dict) and set(row) == {"path", "sha256", "size"},
              "manifest GWT jar pin has an unexpected schema")
        path = Path(row["path"]).resolve(strict=True)
        _need(path.parent == gwt_home and path.is_file() and not _is_reparse(path),
              "manifest GWT jar is outside the pinned gwtHome")
        _need(isinstance(row["sha256"], str) and HASH_RE.fullmatch(row["sha256"]) and
              type(row["size"]) is int and row["size"] >= 0,
              "manifest GWT jar size/hash is malformed")
        name = path.name
        _need(name not in by_name and name in names and
              row["sha256"] == names[name],
              "manifest GWT jar differs from its source-bound name/hash")
        _need(path.stat().st_size == row["size"] and _sha_file(path) == row["sha256"],
              "GWT jar bytes differ from their manifest pin: " + name)
        by_name[name] = {"name": name, "sha256": row["sha256"],
                         "size": row["size"], "source": path}
    _need(set(by_name) == set(names), "manifest and source GWT jar sets differ")
    return [by_name[name] for name in sorted(by_name)]


def _copy_jars(jars: list[dict], app: Path) -> str:
    tools_root = app / ".tools"
    tools_root.mkdir()
    destination = tools_root / "gwt-2.7.0"
    destination.mkdir()
    rows = []
    for row in jars:
        target = destination / row["name"]
        with row["source"].open("rb") as incoming, target.open("xb") as outgoing:
            shutil.copyfileobj(incoming, outgoing)
            outgoing.flush()
            os.fsync(outgoing.fileno())
        _need(target.stat().st_size == row["size"] and
              _sha_file(target) == row["sha256"],
              "copied GWT jar failed its pinned byte check: " + row["name"])
        rows.append({"path": target.relative_to(app).as_posix(),
                     "size": row["size"], "sha256": row["sha256"]})
    return _sha_bytes(_json_bytes(rows))


def _read_private_state(manifest: dict, root: Path):
    repo, _, tools = _paths(manifest, root)
    snapshot, rows = _snapshot(manifest, repo)
    state, _ = _load_json(root / "private-source.json", "private source receipt")
    _need(state.get("schema") == SCHEMA and state.get("status") == "PASS" and
          state.get("sourceSnapshot") == {
              "path": snapshot["path"], "sha256": snapshot["sha256"],
              "baseHead": snapshot["baseHead"], "sourceIdentity": snapshot["sourceIdentity"],
              "fileCount": RAW_INPUT_COUNT,
          }, "private source receipt is not bound to this manifest")
    app = root / "private-app"
    _need(app.is_dir() and not _is_reparse(app), "private app copy is missing or unsafe")
    delta = state.get("catalogDelta")
    _verify_private_app(repo, app, rows, delta)
    _need(_verify_repo(repo, rows) == state.get("publicInputInventorySha256"),
          "public repository raw inputs changed since preparation")
    jars = _jar_pins(manifest, app, rows)
    jar_dir = app / ".tools" / "gwt-2.7.0"
    copied = []
    for jar in jars:
        path = jar_dir / jar["name"]
        _need(path.is_file() and not path.is_symlink() and
              path.stat().st_size == jar["size"] and _sha_file(path) == jar["sha256"],
              "private copy of a pinned GWT jar changed: " + jar["name"])
        copied.append({"path": path.relative_to(app).as_posix(),
                       "size": jar["size"], "sha256": jar["sha256"]})
    _need(_sha_bytes(_json_bytes(copied)) == state.get("gwtJarInventorySha256"),
          "private copied GWT jar inventory differs from preparation")
    return repo, root, app, snapshot, rows, delta, jars, tools


def _run_owned(argv: list[str], task_root: Path, label: str, timeout_ms: int) -> dict:
    """Run one host child under the maintained kill-on-close Windows job."""
    runner = importlib.import_module("serial_runner")
    host = runner.run_command(argv, task_root / ("owned-" + label), timeout_ms)
    operation, cleanup, logs = (host.get("operation", {}), host.get("cleanup", {}),
                               host.get("logs", {}))
    _need(host.get("status") == "PASS" and host.get("handlesClosed") is True and
          type(operation) is dict and operation.get("exitCode") == 0 and
          type(cleanup) is dict and cleanup.get("cleanupVerified") is True and
          type(logs) is dict and all(type(value) is dict and
              not value.get("overflow") and not value.get("errors") and
              not value.get("readerAlive") and value.get("streamClosed")
              for value in logs.values()),
          label + " host command or exact process cleanup failed")
    return host


def _inventory_tree(root: Path, relative: str) -> list[dict]:
    base = root / Path(*PurePosixPath(relative).parts)
    _need(base.is_dir() and not _is_reparse(base),
          "compiled output directory is missing or unsafe: " + relative)
    rows = []
    for directory, directories, files in os.walk(base, topdown=True, followlinks=False):
        current = Path(directory)
        kept = []
        for name in sorted(directories):
            child = current / name
            _need(not child.is_symlink() and not _is_reparse(child),
                  "compiled output contains a symlink/reparse directory")
            kept.append(name)
        directories[:] = kept
        for name in sorted(files):
            path = current / name
            _need(path.is_file() and not path.is_symlink() and not _is_reparse(path),
                  "compiled output contains a non-regular file")
            rows.append({"path": path.relative_to(root).as_posix(),
                         "size": path.stat().st_size, "sha256": _sha_file(path)})
    return sorted(rows, key=lambda row: row["path"])


def _build_inventories(app: Path) -> dict:
    compiled = _inventory_tree(app, "war/circuitjs1")
    permutations = sorted(
        match.group(1) for row in compiled
        for match in [PERMUTATION_RE.fullmatch(row["path"])] if match)
    _need(len(compiled) == COMPILED_FILE_COUNT,
          "compiled GWT inventory must contain exactly 357 files")
    _need(len(permutations) == 5 and len(set(permutations)) == 5,
          "compiled GWT inventory must contain exactly five actual permutations")
    all_cache_js = [row["path"] for row in compiled if row["path"].endswith(".cache.js")]
    _need(len(all_cache_js) == 5,
          "compiled GWT inventory contains an unexpected permutation count")
    deployment = _inventory_tree(app, "war/WEB-INF/deploy/circuitjs1")
    expected_deploy = {
        "war/WEB-INF/deploy/circuitjs1/rpcPolicyManifest/manifest.txt"
    } | {
        "war/WEB-INF/deploy/circuitjs1/symbolMaps/" + item + ".symbolMap"
        for item in permutations
    }
    _need(len(deployment) == DEPLOYMENT_FILE_COUNT and
          {row["path"] for row in deployment} == expected_deploy and
          all(row["size"] > 0 for row in deployment),
          "GWT deployment outputs differ from the five compiled permutations")
    return {
        "compiledFileCount": len(compiled),
        "compiledInventorySha256": _sha_bytes(_json_bytes(compiled)),
        "permutations": permutations,
        "deploymentFileCount": len(deployment),
        "deploymentInventorySha256": _sha_bytes(_json_bytes(deployment)),
    }


def _verify_build_receipt(manifest: dict, root: Path, app: Path,
                          rows: list[dict], delta: dict, snapshot: dict) -> dict:
    value, _ = _load_json(root / "private-build.json", "private build receipt")
    _need(value.get("schema") == SCHEMA and value.get("status") == "PASS" and
          value.get("sourceSnapshotSha256") == snapshot["sha256"] and
          value.get("catalogDelta") == delta,
          "private build receipt is not a successful exact-source build")
    raw_hash = _verify_private_app(Path(manifest["repo"]).resolve(strict=True),
                                   app, rows, delta)
    _need(raw_hash == value.get("privateRawInputInventorySha256"),
          "private source inputs changed after the build")
    current = _build_inventories(app)
    for key in ("compiledFileCount", "compiledInventorySha256", "permutations",
                "deploymentFileCount", "deploymentInventorySha256"):
        _need(current.get(key) == value.get(key),
              "compiled GWT output changed after the build: " + key)
    return value


def prepare(manifest: dict, output_dir) -> dict:
    started = time.monotonic()
    try:
        repo, root, tools = _paths(manifest, output_dir)
        snapshot, rows = _snapshot(manifest, repo)
        _need(not (root / "private-app").exists() and
              not (root / "private-source.json").exists(),
              "private preparation outputs must be new")
        public_hash = _verify_repo(repo, rows)
        original, enabled, delta = _catalog_expected(repo, rows)
        jars = _jar_pins(manifest, repo, rows)
        app = root / "private-app"
        app.mkdir()
        copied = []
        for row in rows:
            relative = PurePosixPath(row["path"])
            source = repo.joinpath(*relative.parts)
            _need(source.is_file() and not source.is_symlink() and not _is_reparse(source),
                  "raw input is missing or unsafe: " + row["path"])
            target = app.joinpath(*relative.parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            with source.open("rb") as incoming, target.open("xb") as outgoing:
                shutil.copyfileobj(incoming, outgoing)
            _need(target.stat().st_size == row["size"] and _sha_file(target) == row["sha256"],
                  "copied input failed its source snapshot check: " + row["path"])
            copied.append({"path": row["path"], "sha256": row["sha256"],
                           "size": row["size"]})
        _need(_sha_bytes(_json_bytes(copied)) == _sha_bytes(_json_bytes(rows)),
              "copied private raw inventory differs from the 1,355 pinned rows")
        catalog = app.joinpath(*PurePosixPath(CATALOG_PATH).parts)
        _need(catalog.read_bytes() == original, "private catalog did not begin at its pinned disabled bytes")
        catalog.write_bytes(enabled)
        _need(catalog.read_bytes() == enabled and _sha_file(catalog) == delta["afterSha256"],
              "private catalog enablement failed exact byte verification")
        jar_inventory_hash = _copy_jars(jars, app)
        private_hash = _verify_private_app(repo, app, rows, delta)
        _need(_verify_repo(repo, rows) == public_hash,
              "public raw source changed during private preparation")
        source_receipt = {
            "schema": SCHEMA, "phase": "private-source-preparation", "status": "PASS",
            "sourceSnapshot": {
                "path": snapshot["path"], "sha256": snapshot["sha256"],
                "baseHead": snapshot["baseHead"],
                "sourceIdentity": snapshot["sourceIdentity"],
                "fileCount": RAW_INPUT_COUNT,
            },
            "sourceBinding": {
                "baseHead": snapshot["baseHead"],
                "observedHead": manifest.get("sourceBinding", {}).get("observedHead"),
                "sourceIdentity": snapshot["sourceIdentity"],
            },
            "privateApp": "private-app",
            "privateAppPath": str(app),
            "rawInputCount": RAW_INPUT_COUNT,
            "publicInputInventorySha256": public_hash,
            "privateInputInventorySha256": private_hash,
            "catalogDelta": delta,
            "gwtJarCount": len(jars),
            "gwtJarInventorySha256": jar_inventory_hash,
            "elapsedSeconds": round(time.monotonic() - started, 3),
        }
        _write_json_exclusive(root / "private-source.json", source_receipt)
        return {"status": "PASS", "details": source_receipt}
    except Exception as error:
        return {"status": "FAIL", "details": {
            "phase": "private-source-preparation",
            "elapsedSeconds": round(time.monotonic() - started, 3),
            "error": str(error),
        }}


def build(manifest: dict, output_dir) -> dict:
    started = time.monotonic()
    root = Path(output_dir)
    record = {"schema": SCHEMA, "phase": "private-jdk8-gwt-build",
              "status": "FAIL", "elapsedSeconds": 0.0}
    try:
        repo, root, tools = _paths(manifest, output_dir)
        repo, root, app, snapshot, rows, delta, jars, tools = _read_private_state(
            manifest, root)
        _need(not (root / "private-build.json").exists() and
              not (root / "owned-build").exists(),
              "private build outputs must be new")
        _need(not (app / "war/circuitjs1").exists() and
              not (app / "war/WEB-INF/deploy").exists(),
              "private build must begin without stale compiled or deployment outputs")
        _need(_verify_repo(repo, rows) == _load_json(
            root / "private-source.json", "private source receipt")[0]["publicInputInventorySha256"],
            "public raw inputs changed before the private build")
        java_home = Path(tools["javaHome"]).resolve(strict=True)
        powershell = Path(tools["powershell"]).resolve(strict=True)
        _need((java_home / "bin" / "java.exe").is_file() and powershell.is_file(),
              "pinned JDK8 Java or selected PowerShell is missing")
        script = app / "scripts" / "build.ps1"
        _need(script.is_file() and not script.is_symlink(), "maintained scripts/build.ps1 is missing")
        command = [
            str(powershell), "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-File", str(script), "-JavaHome", str(java_home), "-Target", "Compile",
            "-Style", "OBF", "-ProcessTimeoutSeconds", "900",
        ]
        host = _run_owned(command, root, "build", BUILD_TIMEOUT_MS)
        exit_code = host["operation"]["exitCode"]
        _need(_verify_repo(repo, rows) == _load_json(
            root / "private-source.json", "private source receipt")[0]["publicInputInventorySha256"],
            "public raw inputs changed during the private build")
        raw_hash = _verify_private_app(repo, app, rows, delta)
        inventory = _build_inventories(app)
        copied_jars = []
        for item in jars:
            path = app / ".tools" / "gwt-2.7.0" / item["name"]
            _need(path.is_file() and path.stat().st_size == item["size"] and
                  _sha_file(path) == item["sha256"],
                  "pinned private GWT jar changed during build: " + item["name"])
            copied_jars.append({"path": path.relative_to(app).as_posix(),
                                "size": item["size"], "sha256": item["sha256"]})
        _need(_sha_bytes(_json_bytes(copied_jars)) ==
              _load_json(root / "private-source.json", "private source receipt")[0][
                  "gwtJarInventorySha256"],
              "private GWT jar inventory changed during build")
        record.update(inventory)
        record.update({
            "status": "PASS", "exitCode": exit_code,
            "terminationProven": True, "host": host,
            "privateAppPath": str(app),
            "sourceSnapshotSha256": snapshot["sha256"],
            "sourceIdentity": snapshot["sourceIdentity"],
            "sourceHead": snapshot["baseHead"],
            "catalogDelta": delta,
            "rawInputCount": RAW_INPUT_COUNT,
            "privateRawInputInventorySha256": raw_hash,
            "publicInputsUnchanged": True,
            "gwtJarCount": GWT_JAR_COUNT,
            "gwtJarInventorySha256": _sha_bytes(_json_bytes(copied_jars)),
        })
        record["elapsedSeconds"] = round(time.monotonic() - started, 3)
        _write_json_exclusive(root / "private-build.json", record)
        return {"status": "PASS", "details": record}
    except Exception as error:
        record["failure"] = str(error)
        record["elapsedSeconds"] = round(time.monotonic() - started, 3)
        try:
            _write_json_exclusive(root / "private-build.json", record)
        except OSError:
            pass
        return {"status": "FAIL", "details": record}


def _validate_menu_result(path: Path) -> dict:
    result, raw = _load_json(path, "private menu result")
    cases = result.get("cases")
    replays = result.get("replays")
    stale = result.get("staleReplayEpoch")
    cleanup = result.get("cleanup")
    normal_replay = result.get("normalQ30Replay")
    _need(result.get("outcome") == "PASS" and isinstance(cases, list) and
          len(cases) == 33 and all(row.get("outcome") == "PASS" and
                                   row.get("privateMetadataAbsent") is True
                                   for row in cases),
          "private menu did not pass 33 launches across 11 enabled families")
    _need(isinstance(replays, list) and len(replays) == 2 and
          all(row.get("outcome") == "PASS" for row in replays) and
          isinstance(normal_replay, dict) and normal_replay.get("outcome") == "PASS",
          "private menu did not pass both saved replays and the normal Q30 replay")
    _need(isinstance(stale, dict) and stale.get("outcome") == "EXPECTED_REJECTED" and
          stale.get("noGenerationStarted") is True and
          stale.get("prelaunchStateUnchanged") is True,
          "stale replay was not rejected before mutation")
    _need(result.get("errors") == [] and not result.get("ownershipCaptureError"),
          "private menu reported page errors or failed its process ownership capture")
    _need(isinstance(cleanup, dict) and cleanup.get("serverStopped") is True and
          cleanup.get("portClosed") is True and cleanup.get("ownedSurvivors") == [],
          "private menu host/browser cleanup is incomplete")
    return {"resultSha256": _sha_bytes(raw), "caseCount": len(cases),
            "familyCount": 11, "savedReplayCount": len(replays),
            "normalQ30Replay": "PASS", "staleReplay": "EXPECTED_REJECTED",
            "cleanup": cleanup}


def menu(manifest: dict, output_dir) -> dict:
    started = time.monotonic()
    root = Path(output_dir)
    record = {"schema": SCHEMA, "phase": "private-enabled-menu-replay", "status": "FAIL"}
    try:
        repo, root, tools = _paths(manifest, output_dir)
        repo, root, app, snapshot, rows, delta, jars, tools = _read_private_state(manifest, root)
        build_record = _verify_build_receipt(manifest, root, app, rows, delta, snapshot)
        runner = Path(__file__).with_name("private_menu.py")
        _need(runner.is_file() and not runner.is_symlink(),
              "maintained private menu runner is missing or unsafe")
        python = Path(tools["python"]).resolve(strict=True)
        powershell = Path(tools["powershell"]).resolve(strict=True)
        deps = Path(tools["deps"]).resolve(strict=True)
        _need(python.is_file() and powershell.is_file() and deps.is_dir(),
              "selected host Python, PowerShell, or Playwright deps are missing")
        menu_output = root / "private-menu-output"
        _need(not menu_output.exists() and not (root / "owned-menu").exists(),
              "private menu outputs must be new")
        command = [str(python), "-B", "-u", str(runner), str(app), str(menu_output)]
        previous_pythonpath = os.environ.get("PYTHONPATH")
        previous_no_bytecode = os.environ.get("PYTHONDONTWRITEBYTECODE")
        previous_powershell = os.environ.get("TSJ_POWERSHELL")
        os.environ["PYTHONPATH"] = str(deps) + (
            os.pathsep + previous_pythonpath if previous_pythonpath else "")
        os.environ["PYTHONDONTWRITEBYTECODE"] = "1"
        os.environ["TSJ_POWERSHELL"] = str(powershell)
        try:
            host = _run_owned(command, root, "menu", MENU_TIMEOUT_MS)
        finally:
            if previous_pythonpath is None:
                os.environ.pop("PYTHONPATH", None)
            else:
                os.environ["PYTHONPATH"] = previous_pythonpath
            if previous_no_bytecode is None:
                os.environ.pop("PYTHONDONTWRITEBYTECODE", None)
            else:
                os.environ["PYTHONDONTWRITEBYTECODE"] = previous_no_bytecode
            if previous_powershell is None:
                os.environ.pop("TSJ_POWERSHELL", None)
            else:
                os.environ["TSJ_POWERSHELL"] = previous_powershell
        exit_code = host["operation"]["exitCode"]
        menu_result = _validate_menu_result(menu_output / "result.json")
        _need(_verify_repo(repo, rows) ==
              _load_json(root / "private-source.json", "private source receipt")[0][
                  "publicInputInventorySha256"],
              "public raw inputs changed during the private menu")
        _verify_build_receipt(manifest, root, app, rows, delta, snapshot)
        record.update({
            "status": "PASS", "exitCode": exit_code, "terminationProven": True,
            "host": host, "privateAppPath": str(app),
            "sourceSnapshotSha256": snapshot["sha256"],
            "sourceIdentity": snapshot["sourceIdentity"],
            "sourceHead": snapshot["baseHead"],
            "catalogDelta": delta,
            "compiledInventorySha256": build_record["compiledInventorySha256"],
            "runnerSha256": _sha_file(runner),
            "promotedFromSha256": PRIVATE_MENU_ORIGIN_SHA256,
            "result": menu_result,
            "publicInputsUnchanged": True,
        })
        record["elapsedSeconds"] = round(time.monotonic() - started, 3)
        _write_json_exclusive(root / "private-menu.json", record)
        return {"status": "PASS", "details": record}
    except Exception as error:
        record["failure"] = str(error)
        record["elapsedSeconds"] = round(time.monotonic() - started, 3)
        try:
            _write_json_exclusive(root / "private-menu.json", record)
        except OSError:
            pass
        return {"status": "FAIL", "details": record}


def archive(manifest: dict, output_dir) -> dict:
    started = time.monotonic()
    root = Path(output_dir)
    record = {"schema": SCHEMA, "phase": "private-enabled-source-archive", "status": "FAIL"}
    try:
        repo, root, tools = _paths(manifest, output_dir)
        repo, root, app, snapshot, rows, delta, jars, tools = _read_private_state(manifest, root)
        build_record = _verify_build_receipt(manifest, root, app, rows, delta, snapshot)
        _need(not (root / "private-source.tar.gz").exists() and
              not (root / "private-archive.json").exists(),
              "private source archive outputs must be new")
        raw_hash = _verify_private_app(repo, app, rows, delta)
        _need(_verify_repo(repo, rows) ==
              _load_json(root / "private-source.json", "private source receipt")[0][
                  "publicInputInventorySha256"],
              "public raw inputs changed before source archive")
        archive_path = root / "private-source.tar.gz"
        with archive_path.open("xb") as raw_stream:
            with gzip.GzipFile(fileobj=raw_stream, mode="wb", filename="",
                               compresslevel=9, mtime=0) as compressed:
                with tarfile.open(fileobj=compressed, mode="w",
                                  format=tarfile.PAX_FORMAT) as archive_file:
                    for row in rows:
                        relative = _safe_relative(row["path"])
                        source = app.joinpath(*relative.parts)
                        expected_hash = (delta["afterSha256"] if row["path"] == CATALOG_PATH
                                         else row["sha256"])
                        expected_size = (delta["afterSize"] if row["path"] == CATALOG_PATH
                                         else row["size"])
                        _need(source.is_file() and not source.is_symlink() and
                              source.stat().st_size == expected_size and
                              _sha_file(source) == expected_hash,
                              "source archive input changed: " + row["path"])
                        info = tarfile.TarInfo(row["path"])
                        info.size, info.mtime, info.uid, info.gid = expected_size, 0, 0, 0
                        info.uname, info.gname, info.mode = "", "", 0o644
                        with source.open("rb") as stream:
                            archive_file.addfile(info, stream)
        archive_rows = []
        with tarfile.open(archive_path, mode="r:gz") as archive_file:
            members = archive_file.getmembers()
            _need(len(members) == RAW_INPUT_COUNT,
                  "private source archive must contain exactly 1,355 members")
            _need([item.name for item in members] == [row["path"] for row in rows],
                  "private source archive member set/order differs from the source snapshot")
            for member, row in zip(members, rows):
                _safe_relative(member.name)
                _need(member.isfile() and member.size == (
                    delta["afterSize"] if row["path"] == CATALOG_PATH else row["size"]),
                    "private archive member is unsafe or has the wrong size")
                source = archive_file.extractfile(member)
                _need(source is not None, "private archive member cannot be read")
                with source:
                    digest = hashlib.sha256()
                    size = 0
                    for block in iter(lambda: source.read(1024 * 1024), b""):
                        digest.update(block)
                        size += len(block)
                expected_hash = (delta["afterSha256"] if row["path"] == CATALOG_PATH
                                 else row["sha256"])
                _need(size == member.size and digest.hexdigest() == expected_hash,
                      "private archive member bytes differ: " + row["path"])
                archive_rows.append({"path": row["path"], "size": size,
                                     "sha256": digest.hexdigest()})
        _need(_verify_private_app(repo, app, rows, delta) == raw_hash,
              "private source changed during archive verification")
        _need(_verify_repo(repo, rows) ==
              _load_json(root / "private-source.json", "private source receipt")[0][
                  "publicInputInventorySha256"],
              "public raw inputs changed during source archive")
        record.update({
            "status": "PASS", "sourceArchiveOnly": True,
            "archive": "private-source.tar.gz",
            "archiveSha256": _sha_file(archive_path),
            "memberCount": len(archive_rows),
            "memberInventorySha256": _sha_bytes(_json_bytes(archive_rows)),
            "rawInputCount": RAW_INPUT_COUNT,
            "sourceSnapshotSha256": snapshot["sha256"],
            "sourceIdentity": snapshot["sourceIdentity"],
            "sourceHead": snapshot["baseHead"],
            "catalogDelta": delta,
            "privateRawInputInventorySha256": raw_hash,
            "compiledInventorySha256": build_record["compiledInventorySha256"],
            "jarsIncluded": False,
            "publicInputsUnchanged": True,
            "scope": "private-enabled 1,355-input source archive; not Q30 acceptance",
        })
        record["elapsedSeconds"] = round(time.monotonic() - started, 3)
        _write_json_exclusive(root / "private-archive.json", record)
        return {"status": "PASS", "details": record}
    except Exception as error:
        record["failure"] = str(error)
        record["elapsedSeconds"] = round(time.monotonic() - started, 3)
        try:
            _write_json_exclusive(root / "private-archive.json", record)
        except OSError:
            pass
        return {"status": "FAIL", "details": record}
