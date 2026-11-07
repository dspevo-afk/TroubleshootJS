"""Independent strict source and transform auditor for the P8 CANARY inputs."""
from __future__ import annotations

import sys

if not sys.flags.isolated:
    sys.stderr.write("P8_INPUT_AUDIT_FAIL ISOLATED_MODE_REQUIRED\n")
    raise SystemExit(2)
if not sys.dont_write_bytecode:
    sys.stderr.write("P8_INPUT_AUDIT_FAIL BYTECODE_DISABLED_REQUIRED\n")
    raise SystemExit(2)

import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import types


HERE = Path(__file__).resolve().parent
P6 = HERE.parent / "canonical-prefix-rejection-stages-r1"
P6_PINS_SHA256 = "b640587f9cb1ac3f3ec8feff85940f4b7a9c8fda37570b50bfa30c39fd067771"
P6_BASELINE_SHA256 = "f94eca24bac4b24cc264fe4ca5038c68cbbf54b8530bf529ed3ec66e48ecf019"
P6_AUDITOR_SHA256 = "5ac397cec89917d446a77e77c1c572eabfff28381b080ab1ad4bf800de59134d"
P6_PARENT = "831d69a41d9504bfb845caef6942e9ef58c9d6d2"
P8_PARENT = "0891474cadc4b9e00d7911e81c124721a11efb87"
PLAN_SHA256 = "0bf95e2de4d2662f7d57604c910dab89081abcc11a7597288b19ffdaecc9dc93"
API_SCHEMA_SHA256 = "915583d2594a74135bd3269c74a8c190cfb1d47c9d0aa28062b24578e17de7ff"
CONTROL_SHA256 = {
    "PLAN_R1.md": PLAN_SHA256,
    "API_SCHEMA_R1.md": API_SCHEMA_SHA256,
    "CANARY_HOST_R1.md": "4fce0587587b8bf4c1e18064171bba2215ef68a194c8c0b77ce4c85088c3843d",
    "CANARY_HOST_ADDENDUM_R2.md": "409575ef4d9b939895779d458d7e3f20992782d630cb6634231fe16ba8554772",
    "CANARY_HOST_ADDENDUM_R3.md": "e5a118638cbac72c60f3c165557e4dc5f27dca936f95f0951a3a1a28c70679e2",
    "CANARY_OUTPUT_R1.md": "025645230416ee748f6d386916088f9338fb7f326b84ae4d426c317bdfaeb7b0",
    "CANARY_READER_CONTROLS_R1.md": "c886401dd9a47a214fc8aa8085b5e28eb359398882ebd1731c8506f5645ed505",
    "CANARY_REJECTION_R1.md": "d544ec790582dc1e1ff4bb5ffbe3a92d11947c4747f9d11a983dac5c77191d43",
    "CANARY_REJECTION_ADDENDUM_R2.md": "30d7b2d5c011289dbe1104ce713bda3fa9f4f58290ad27ff0e6d880a89a43749",
    "CANARY_REJECTION_ADDENDUM_R3.md": "b4e7158ad5a9dad68666f2e21063113fae02a5ee6200d9fd56992ce7de2bae68",
    "PYTHON_ISOLATION_R1.md": "33875c9534fdb0cfb113975a8007094936946f1be6741e5b367b224a4a64f1a6",
    "LITERAL_CANARIES_R2.md": "c2bf8d169102b8d515f55682c63137531adf05dfaafa4880ec41397d26372a7f",
    "LITERAL_CANARIES_ADDENDUM_R3.md": "6883c073523210a7f1ee5b76f18147a87ee6352072dca17e2657c035ad646433",
}
PYTHON_SHA256 = "ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec"
JAVA_SHA256 = "e71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce"
JAVAC_SHA256 = "61fa6463ee5b59004719b9833a16b148740cfa9f2d1f5a95ca598cc90be04c64"
POWERSHELL_SHA256 = "60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f"
HOST_SHA256 = "0ceb429f4120173843f2c1bd73d4ed2659b225a0a7d1515def65dbc594f179e0"
HOST_BYTES = 59932
P6_SOURCE_ROLES = (
    "router", "support", "writer", "adapter", "blocker", "queries",
    "netRouterOverlay", "stageInspector",
)
P8_MODE_ROLES = (
    "router", "support", "writer", "adapter", "blocker", "queries",
    "stageInspector", "netRouterOverlay", "rulesOverlay", "reasonCanary",
)
BRIDGES = (
    ("CirSim.java", 359392, "63b5d7aeb1026ab0855a0bd1a7c5142d53b9e9f4be63344313dbe4af37567812"),
    ("ChipElm.java", 10949, "a01bb797189410fb82f8b87753a783e7ca9d912b9ce99a4c6c96b35365696e24"),
    ("PhysicalSpecificationDeveloperVerifier.java", 264,
     "616b5daea7d17dfc3c4dc125f79f30175a33202fffda2cbddb789a38f53cd5b7"),
)
CONTROL_FILES = (
    "PLAN_R1.md",
    "API_SCHEMA_R1.md",
    "CANARY_HOST_R1.md",
    "CANARY_HOST_ADDENDUM_R2.md",
    "CANARY_HOST_ADDENDUM_R3.md",
    "CANARY_OUTPUT_R1.md",
    "CANARY_READER_CONTROLS_R1.md",
    "CANARY_REJECTION_R1.md",
    "CANARY_REJECTION_ADDENDUM_R2.md",
    "CANARY_REJECTION_ADDENDUM_R3.md",
    "PYTHON_ISOLATION_R1.md",
    "LITERAL_CANARIES_R2.md",
    "LITERAL_CANARIES_ADDENDUM_R3.md",
    "SOURCE_AUDIT_R1.md",
)
P8_CONSUMED_FILES = (
    "build_overlay.py",
    "make_canary_inputs.py",
    "audit_canary_inputs.py",
    "run-canary.ps1",
    "run-static-tests.ps1",
    "run-canary-controls.ps1",
    "read_canary.py",
    "test_overlay.py",
    *CONTROL_FILES,
    "overlays-r1/PcbNetRouter.java",
    "overlays-r1/PcbTwoLayerRules.java",
    "overlays-r1/overlay-manifest.json",
    "overlays-r1/overlay.diff",
    "source/CanonicalGeometricReasonCanary.java",
)
CONTROL_DOCS = frozenset({
    "PLAN_R1.md", "API_SCHEMA_R1.md", "CANARY_HOST_R1.md",
    "CANARY_HOST_ADDENDUM_R2.md", "CANARY_HOST_ADDENDUM_R3.md",
    "CANARY_OUTPUT_R1.md", "CANARY_READER_CONTROLS_R1.md",
    "CANARY_REJECTION_R1.md", "CANARY_REJECTION_ADDENDUM_R2.md",
    "CANARY_REJECTION_ADDENDUM_R3.md", "PYTHON_ISOLATION_R1.md",
    "LITERAL_CANARIES_R2.md", "LITERAL_CANARIES_ADDENDUM_R3.md",
    "SOURCE_AUDIT_R1.md",
})
NONCONTROL_EXTRAS = frozenset({
    "build_overlay.py", "make_canary_inputs.py", "audit_canary_inputs.py",
    "run-canary.ps1", "run-static-tests.ps1", "run-canary-controls.ps1",
    "read_canary.py", "test_overlay.py", "overlays-r1/PcbNetRouter.java",
    "overlays-r1/PcbTwoLayerRules.java", "overlays-r1/overlay-manifest.json",
    "overlays-r1/overlay.diff", "source/CanonicalGeometricReasonCanary.java",
})
if (set(CONTROL_FILES) != CONTROL_DOCS or
        set(CONTROL_SHA256) != CONTROL_DOCS - {"SOURCE_AUDIT_R1.md"} or
        set(P8_CONSUMED_FILES) != CONTROL_DOCS | NONCONTROL_EXTRAS):
    raise RuntimeError("P8_INPUT_FILE_ALLOWLIST_DRIFT")
TOP_KEYS = {
    "schema", "repositoryRoot", "parent", "p6PinsPath", "p6PinsSha256",
    "sourceRows", "files", "modeFiles", "bridgeManifest", "bridgeRoot",
    "javaHome", "pythonExe", "hostPath", "auditorPath", "planPath",
    "apiPath", "hostContractPath",
}
REASON_SITES = (
    "START_ESCAPE_DIRECTION", "END_ESCAPE_DIRECTION", "DOMAIN_UNKNOWN_NET",
    "DOMAIN_CORRIDOR_OVERLAP", "DOMAIN_MEMBERSHIP_SIDE", "EMPTY_FACE_RESERVATION",
    "ADJACENT_RESERVATION", "PAD_ESCAPE_INTERSECTION", "PROFILED_PAD_TOUCH",
    "COMPONENT_COURTYARD",
)


class Reject(Exception):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


def need(ok: bool, code: str) -> None:
    if not ok:
        raise Reject(code)


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def parse_bootstrap(raw: bytes):
    need(not raw.startswith(b"\xef\xbb\xbf"), "JSON_BOM")

    def pairs(rows):
        result = {}
        for key, value in rows:
            need(key not in result, "DUP_JSON_KEY")
            result[key] = value
        return result

    def bad_constant(_value):
        raise Reject("NONFINITE_JSON")

    try:
        return json.loads(raw.decode("utf-8"), object_pairs_hook=pairs,
                          parse_constant=bad_constant)
    except Reject:
        raise
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise Reject("INVALID_JSON") from exc


def bootstrap_read(path: Path, limit: int) -> bytes:
    path = Path(os.path.abspath(path))
    for item in (path, *path.parents):
        st = item.lstat()
        need(not stat.S_ISLNK(st.st_mode) and
             not (getattr(st, "st_file_attributes", 0) & 0x400), "REPARSE_PATH")
    before = path.stat()
    need(stat.S_ISREG(before.st_mode) and 0 <= before.st_size <= limit, "FILE_SIZE")

    def identity(st):
        return st.st_dev, st.st_ino, st.st_size, st.st_mtime_ns, st.st_nlink

    with path.open("rb") as stream:
        opened = os.fstat(stream.fileno())
        raw = stream.read(limit + 1)
        closed = os.fstat(stream.fileno())
    after = path.stat()
    need(identity(before) == identity(opened) == identity(closed) == identity(after) and
         before.st_ctime_ns == after.st_ctime_ns and
         opened.st_ctime_ns == closed.st_ctime_ns and
         len(raw) == after.st_size <= limit, "FILE_CHANGED")
    return raw


def canonical(path_value: str | Path, want_dir: bool | None = None) -> Path:
    need(type(path_value) is str or isinstance(path_value, Path), "PATH_TYPE")
    path = Path(path_value)
    need(path.is_absolute() and str(path) == os.path.abspath(str(path)), "NONCANONICAL_PATH")
    for item in (path, *path.parents):
        st = item.lstat()
        need(not stat.S_ISLNK(st.st_mode) and
             not (getattr(st, "st_file_attributes", 0) & 0x400), "REPARSE_PATH")
    resolved = path.resolve(strict=True)
    need(str(resolved) == str(path), "NONCANONICAL_PATH")
    if want_dir is True:
        need(path.is_dir(), "DIRECTORY_REQUIRED")
    elif want_dir is False:
        need(path.is_file(), "FILE_REQUIRED")
    return path


def powershell_executable() -> Path:
    raw_value = os.environ.get("SystemRoot")
    raw_root = Path(raw_value if raw_value is not None else r"C:\Windows")
    need(raw_root.is_absolute() and str(raw_root) == os.path.abspath(str(raw_root)),
         "SYSTEMROOT_PATH")
    # Check the literal environment path before resolving case or aliases.
    for item in (raw_root, *raw_root.parents):
        st = item.lstat()
        need(not stat.S_ISLNK(st.st_mode) and
             not (getattr(st, "st_file_attributes", 0) & 0x400), "REPARSE_PATH")
    resolved_root = raw_root.resolve(strict=True)
    need(resolved_root.is_dir(), "SYSTEMROOT_PATH")
    canonical_root = canonical(resolved_root, want_dir=True)
    return canonical(canonical_root / "System32/WindowsPowerShell/v1.0/powershell.exe",
                     want_dir=False)


def exact_keys(value: object, keys: set[str], code: str) -> dict:
    need(type(value) is dict and set(value) == keys, code)
    return value


def exact_int(value: object, low: int = 0) -> bool:
    return type(value) is int and value >= low


def source_rel(value: object) -> str:
    need(type(value) is str and value and "\\" not in value, "SOURCE_PATH")
    path = PurePosixPath(value)
    need(not path.is_absolute() and all(part not in ("", ".", "..") for part in path.parts),
         "SOURCE_PATH")
    return value


def import_authenticated_source(name: str, path: Path, raw: bytes):
    module = types.ModuleType(name)
    module.__file__ = str(path)
    module.__package__ = ""
    sys.modules[name] = module
    exec(compile(raw, str(path), "exec"), module.__dict__)
    return module


def import_p6_helper(path: Path, raw: bytes):
    module = import_authenticated_source("p8_authenticated_p6_audit_inputs", path, raw)
    need(callable(getattr(module, "read", None)) and
         callable(getattr(module, "parse", None)) and
         callable(getattr(module, "sha", None)), "P6_AUDITOR_API")
    return module


def checked_read(helper, path: Path, limit: int, code: str) -> bytes:
    try:
        return helper.read(path, limit)
    except Exception as exc:
        text = str(exc)
        if text == "REPARSE":
            raise Reject("REPARSE_PATH") from exc
        if text == "FILE_SIZE":
            raise Reject("FILE_SIZE") from exc
        raise Reject(code) from exc


def checked_parse(helper, raw: bytes, code: str):
    try:
        return helper.parse(raw)
    except Exception as exc:
        text = str(exc)
        if text == "DUP_JSON":
            raise Reject("DUP_JSON_KEY") from exc
        if text == "NONFINITE_JSON":
            raise Reject("NONFINITE_JSON") from exc
        raise Reject(code) from exc


def row_map(rows: object, code: str, max_bytes: int = 8388608) -> dict[str, dict]:
    need(type(rows) is list, code)
    found = {}
    for row in rows:
        exact_keys(row, {"path", "bytes", "sha256"}, code)
        need(type(row["path"]) is str and exact_int(row["bytes"]) and
             row["bytes"] <= max_bytes and type(row["sha256"]) is str and
             re.fullmatch(r"[0-9a-f]{64}", row["sha256"]) is not None, code)
        path = canonical(row["path"], want_dir=False)
        key = os.path.normcase(str(path))
        need(key not in found, "DUPLICATE_FILE_PIN")
        found[key] = row
    return found


def require_row(found: dict[str, dict], path: Path, digest: str | None = None,
                code: str = "EXTRA_INPUT_HASH") -> dict:
    path = canonical(path, want_dir=False)
    row = found.get(os.path.normcase(str(path)))
    need(row is not None, "UNBOUND_INPUT")
    if digest is not None:
        need(row["sha256"] == digest, code)
    return row


def assert_file(helper, path: Path, row: dict, code: str = "EXTRA_INPUT_HASH") -> bytes:
    raw = checked_read(helper, path, row["bytes"], code)
    need(len(raw) == row["bytes"] and sha(raw) == row["sha256"], code)
    return raw


def verify_p6_context(helper, pins: dict) -> tuple[dict, dict, dict[str, dict]]:
    p6_path = canonical(pins["p6PinsPath"], want_dir=False)
    need(p6_path == canonical(P6 / "input-pins-r6.json", want_dir=False) and
         pins["p6PinsSha256"] == P6_PINS_SHA256, "P6_PINS_CONTEXT")
    p6_raw = checked_read(helper, p6_path, 4194304, "P6_PINS_HASH")
    need(sha(p6_raw) == P6_PINS_SHA256, "P6_PINS_HASH")
    p6 = checked_parse(helper, p6_raw, "P6_PINS_JSON")
    need(type(p6) is dict and p6.get("schema") ==
         "tsj-canonical-prefix-stage-inputs-v1" and p6.get("parent") == P6_PARENT,
         "P6_CONTEXT")
    need(type(p6.get("changedRows")) is list and not p6["changedRows"], "P6_SOURCE_DELTA")
    need(type(p6.get("sourceRows")) is list and len(p6["sourceRows"]) == 1084,
         "P6_SOURCE_CENSUS")
    need(type(p6.get("modeFiles")) is dict and set(p6["modeFiles"]) == set(P6_SOURCE_ROLES),
         "P6_MODE_KEYS")
    p6files = row_map(p6.get("files"), "P6_FILE_ROW")

    baseline_path = canonical(p6.get("baselineManifestPath"), want_dir=False)
    need(p6.get("baselineManifestSha256") == P6_BASELINE_SHA256, "P6_BASELINE_HASH")
    baseline_row = require_row(p6files, baseline_path, P6_BASELINE_SHA256, "P6_BASELINE_HASH")
    baseline_raw = assert_file(helper, baseline_path, baseline_row, "P6_BASELINE_HASH")
    need(sha(baseline_raw) == P6_BASELINE_SHA256, "P6_BASELINE_HASH")
    baseline = checked_parse(helper, baseline_raw, "P6_BASELINE_JSON")
    need(type(baseline) is dict and baseline.get("schema") ==
         "tsj-prefix-rejection-source-baseline-v1" and baseline.get("parent") == P6_PARENT,
         "P6_BASELINE_CONTEXT")
    need(Path(p6.get("historicalRoot", "")) == Path(baseline.get("repositoryRoot", "")) and
         p6["sourceRows"] == baseline.get("files"), "P6_BASELINE_CONTEXT")

    p6_auditor = canonical(p6.get("preflight"), want_dir=False)
    need(p6_auditor == canonical(P6 / "audit_inputs.py", want_dir=False), "P6_AUDITOR_PATH")
    auditor_row = require_row(p6files, p6_auditor, P6_AUDITOR_SHA256, "P6_AUDITOR_PIN")
    auditor_raw = assert_file(helper, p6_auditor, auditor_row, "P6_AUDITOR_PIN")
    need(sha(auditor_raw) == P6_AUDITOR_SHA256, "P6_AUDITOR_PIN")
    return p6, baseline, p6files


def validate_sources(helper, repository: Path, pins: dict, p6: dict) -> int:
    rows = pins["sourceRows"]
    need(rows == p6["sourceRows"] and len(rows) == 1084, "SOURCE_CENSUS")
    production = 0
    seen = set()
    for row in rows:
        exact_keys(row, {"path", "bytes", "role", "sha256"}, "SOURCE_ROW")
        rel = source_rel(row["path"])
        need(rel not in seen, "SOURCE_DUPLICATE")
        seen.add(rel)
        need(exact_int(row["bytes"]) and row["bytes"] <= 104857600 and
             type(row["role"]) is str and type(row["sha256"]) is str and
             re.fullmatch(r"[0-9a-f]{64}", row["sha256"]) is not None, "SOURCE_ROW")
        path = repository.joinpath(*PurePosixPath(rel).parts)
        raw = checked_read(helper, path, 104857600, "SOURCE_CHANGED")
        need(len(raw) == row["bytes"] and sha(raw) == row["sha256"], "SOURCE_HASH")
        production += rel.startswith("src/")
    need(len(seen) == 1084 and production == 850, "SOURCE_CENSUS")
    return production


def build_expected_modes(repository: Path, p6: dict) -> dict[str, str]:
    modes = p6["modeFiles"]
    writer = canonical(repository /
                       "tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java",
                       want_dir=False)
    expected = {
        "router": str(canonical(modes["router"], want_dir=False)),
        "support": str(canonical(modes["support"], want_dir=False)),
        "writer": str(writer),
        "adapter": str(canonical(modes["adapter"], want_dir=False)),
        "blocker": str(canonical(modes["blocker"], want_dir=False)),
        "queries": str(canonical(modes["queries"], want_dir=False)),
        "stageInspector": str(canonical(modes["stageInspector"], want_dir=False)),
        "netRouterOverlay": str(canonical(HERE / "overlays-r1/PcbNetRouter.java", want_dir=False)),
        "rulesOverlay": str(canonical(HERE / "overlays-r1/PcbTwoLayerRules.java", want_dir=False)),
        "reasonCanary": str(canonical(HERE / "source/CanonicalGeometricReasonCanary.java",
                                       want_dir=False)),
    }
    return expected


def expected_extra_paths(helper, pins: dict, p6: dict, p6files: dict,
                         modes: dict[str, str]) -> dict[str, Path]:
    expected: dict[str, Path] = {}

    def add(path_value: str | Path) -> None:
        path = canonical(path_value, want_dir=False)
        key = os.path.normcase(str(path))
        need(key not in expected, "DUPLICATE_EXPECTED_INPUT")
        expected[key] = path

    add(pins["p6PinsPath"])
    add(p6["baselineManifestPath"])
    add(p6["preflight"])
    add(pins["bridgeManifest"])
    bridge_root = canonical(pins["bridgeRoot"], want_dir=True)
    for name, _size, _digest in BRIDGES:
        add(bridge_root / ("bridge-" + name))
    for role in P6_SOURCE_ROLES:
        add(p6["modeFiles"][role])
    for relative in P8_CONSUMED_FILES:
        add(HERE / Path(relative))
    add(pins["pythonExe"])
    add(Path(pins["javaHome"]) / "bin/java.exe")
    add(Path(pins["javaHome"]) / "bin/javac.exe")
    add(powershell_executable())
    return expected


def validate_bridges(helper, pins: dict, p6: dict, p6files: dict) -> None:
    root = canonical(pins["bridgeRoot"], want_dir=True)
    manifest = canonical(pins["bridgeManifest"], want_dir=False)
    need(root == canonical(p6["bridgeRoot"], want_dir=True) and
         manifest == canonical(p6["bridgeManifest"], want_dir=False) and
         manifest.parent == root, "BRIDGE_CONTEXT")
    row = require_row(p6files, manifest, code="BRIDGE_CONTEXT")
    raw = assert_file(helper, manifest, row, "BRIDGE_CONTEXT")
    bridge_rows = checked_parse(helper, raw, "BRIDGE_MANIFEST")
    need(type(bridge_rows) is list and len(bridge_rows) == len(BRIDGES), "BRIDGE_MANIFEST")
    for actual, expected in zip(bridge_rows, BRIDGES):
        need(type(actual) is dict and set(actual) == {"path", "bytes", "sha256"} and
             (actual["path"], actual["bytes"], actual["sha256"]) == expected,
             "BRIDGE_MANIFEST")
        path = canonical(root / ("bridge-" + actual["path"]), want_dir=False)
        bridge_pin = require_row(p6files, path, actual["sha256"], "BRIDGE_INPUT")
        need(bridge_pin["bytes"] == actual["bytes"], "BRIDGE_INPUT")
        assert_file(helper, path, bridge_pin, "BRIDGE_INPUT")


def validate_tools(helper, pins: dict) -> None:
    python = canonical(pins["pythonExe"], want_dir=False)
    java_home = canonical(pins["javaHome"], want_dir=True)
    java = canonical(java_home / "bin/java.exe", want_dir=False)
    javac = canonical(java_home / "bin/javac.exe", want_dir=False)
    powershell = powershell_executable()
    for path, expected in ((python, PYTHON_SHA256), (java, JAVA_SHA256),
                           (javac, JAVAC_SHA256), (powershell, POWERSHELL_SHA256)):
        raw = checked_read(helper, path, 104857600, "TOOL_PIN")
        need(sha(raw) == expected, "TOOL_PIN")


def validate_overlay(helper, pins: dict, file_rows: dict[str, dict], p6: dict,
                     p6files: dict) -> None:
    builder_path = canonical(HERE / "build_overlay.py", want_dir=False)
    builder_row = require_row(file_rows, builder_path)
    builder_raw = assert_file(helper, builder_path, builder_row)
    try:
        builder = import_authenticated_source("p8_authenticated_overlay_builder", builder_path,
                                              builder_raw)
    except Exception as exc:
        raise Reject("OVERLAY_BUILDER_IMPORT") from exc

    repository = canonical(pins["repositoryRoot"], want_dir=True)
    router_path = canonical(repository /
                            "src/com/lushprojects/circuitjs1/client/PcbNetRouter.java",
                            want_dir=False)
    rules_path = canonical(repository /
                           "src/com/lushprojects/circuitjs1/client/PcbTwoLayerRules.java",
                           want_dir=False)
    p6_router_path = canonical(p6["modeFiles"]["netRouterOverlay"], want_dir=False)
    router_source = checked_read(helper, router_path, 2097152, "ROUTER_SOURCE")
    rules_source = checked_read(helper, rules_path, 2097152, "RULES_SOURCE")
    p6_router = assert_file(helper, p6_router_path,
                            require_row(p6files, p6_router_path, "0432eed2ac4e84c647aee867cc1856bff7969280b094794bc0acb95907d66d94",
                                        "P6_ROUTER_PIN"), "P6_ROUTER_PIN")
    need(len(router_source) == 129081 and
         sha(router_source) == "92347537ca59629f91d6a72596cd59d6520cff2deebb3681f22d6d9ec82c21ec",
         "ROUTER_SOURCE_PIN")
    need(len(rules_source) == 24212 and
         sha(rules_source) == "b04f9906d726117412d62b6022ea5b8e2f26bf111063292b0d7560f51b0fb8cf",
         "RULES_SOURCE_PIN")
    try:
        router_overlay = builder.make_router_overlay(p6_router)
        rules_overlay = builder.make_rules_overlay(rules_source)
        _router, router_preserved, router_edits = builder._build_router_overlay(p6_router)
        _rules, rules_preserved, rules_edits = builder._build_rules_overlay(rules_source)
        expected_diff = builder._diff(
            p6_router, router_overlay, "P6/overlays-r1/PcbNetRouter.java",
            "P8/overlays-r1/PcbNetRouter.java")
        expected_diff += builder._diff(
            rules_source, rules_overlay,
            "E/src/com/lushprojects/circuitjs1/client/PcbTwoLayerRules.java",
            "P8/overlays-r1/PcbTwoLayerRules.java")
    except Exception as exc:
        raise Reject("OVERLAY_TRANSFORM") from exc

    actual_router_path = canonical(HERE / "overlays-r1/PcbNetRouter.java", want_dir=False)
    actual_rules_path = canonical(HERE / "overlays-r1/PcbTwoLayerRules.java", want_dir=False)
    router_actual = assert_file(helper, actual_router_path, require_row(file_rows, actual_router_path))
    rules_actual = assert_file(helper, actual_rules_path, require_row(file_rows, actual_rules_path))
    need(router_overlay == router_actual and rules_overlay == rules_actual, "OVERLAY_TRANSFORM")
    diff_path = canonical(HERE / "overlays-r1/overlay.diff", want_dir=False)
    diff = assert_file(helper, diff_path, require_row(file_rows, diff_path))
    need(diff == expected_diff, "OVERLAY_DIFF")

    manifest_path = canonical(HERE / "overlays-r1/overlay-manifest.json", want_dir=False)
    manifest_raw = assert_file(helper, manifest_path, require_row(file_rows, manifest_path))
    manifest = checked_parse(helper, manifest_raw, "OVERLAY_MANIFEST_JSON")
    manifest = exact_keys(manifest, {
        "schema", "builder", "frozenDocuments", "inputs", "outputs", "routerEdits",
        "rulesEdits", "routerUnchangedSegments", "rulesUnchangedSegments",
        "reasonSitesExactlyOnce", "proof",
    }, "OVERLAY_MANIFEST_KEYS")
    need(manifest["schema"] == "tsj-q100-geometric-reasons-overlay-manifest-r1",
         "OVERLAY_MANIFEST_SCHEMA")
    need(manifest["builder"] == {"path": "build_overlay.py", "sha256": sha(builder_raw)},
         "OVERLAY_MANIFEST_BUILDER")
    plan_path = canonical(HERE / "PLAN_R1.md", want_dir=False)
    api_path = canonical(HERE / "API_SCHEMA_R1.md", want_dir=False)
    plan_raw = assert_file(helper, plan_path, require_row(file_rows, plan_path))
    api_raw = assert_file(helper, api_path, require_row(file_rows, api_path))
    need(manifest["frozenDocuments"] == {
        "PLAN_R1.md": {"sha256": sha(plan_raw)},
        "API_SCHEMA_R1.md": {"sha256": sha(api_raw)},
    }, "OVERLAY_MANIFEST_DOCUMENTS")
    need(manifest["inputs"] == {
        "E production Router": {
            "path": "E/src/com/lushprojects/circuitjs1/client/PcbNetRouter.java",
            "bytes": len(router_source), "sha256": sha(router_source),
        },
        "accepted P6 Router overlay": {
            "path": "P6/overlays-r1/PcbNetRouter.java",
            "bytes": len(p6_router), "sha256": sha(p6_router),
        },
        "E production rules": {
            "path": "src/com/lushprojects/circuitjs1/client/PcbTwoLayerRules.java",
            "bytes": len(rules_source), "sha256": sha(rules_source),
        },
    }, "OVERLAY_MANIFEST_INPUTS")
    outputs = {
        "PcbNetRouter.java": {"bytes": len(router_overlay), "sha256": sha(router_overlay)},
        "PcbTwoLayerRules.java": {"bytes": len(rules_overlay), "sha256": sha(rules_overlay)},
        "overlay.diff": {"bytes": len(diff), "sha256": sha(diff)},
    }
    need(manifest["outputs"] == outputs, "OVERLAY_MANIFEST_OUTPUTS")
    need(manifest["routerEdits"] == router_edits and manifest["rulesEdits"] == rules_edits and
         manifest["routerUnchangedSegments"] == router_preserved and
         manifest["rulesUnchangedSegments"] == rules_preserved, "OVERLAY_MANIFEST_EDITS")
    sites = {site: 1 for site in REASON_SITES}
    need(manifest["reasonSitesExactlyOnce"] == sites, "OVERLAY_MANIFEST_SITES")
    proof = {
        "nonWhitelistedBytesIdentical": True,
        "p6CoarseObserverSeamByteIdentical": True,
        "p6BoundsAndEndpointLoopByteIdentical": True,
        "p6CanOccupyBodyByteIdentical": True,
        "originalRouterGuardExpressionsRetained": True,
        "originalDomainGuardExpressionsRetained": True,
        "predicateOrder": ["isLegalMove", "canTraverse", "canOccupy"],
        "emptyFaceReservationPredicateEvaluatedOnce": True,
        "routerReasonObserverCopiedToFixedGraphCopies": False,
        "rulesObserverStoredOnSharedRulesObject": False,
        "rulesObserverIsPerCallOnly": True,
        "productionFilesChanged": False,
    }
    need(manifest["proof"] == proof, "OVERLAY_MANIFEST_PROOF")
    canonical_manifest = (json.dumps(manifest, sort_keys=True, indent=2,
                                     ensure_ascii=True) + "\n").encode("ascii")
    need(manifest_raw == canonical_manifest, "OVERLAY_MANIFEST_FORMAT")


def validate_manifest(helper, pins: dict) -> tuple[dict, dict[str, dict], dict[str, Path], int]:
    exact_keys(pins, TOP_KEYS, "INPUT_MANIFEST_KEYS")
    need(pins["schema"] == "tsj-q100-geometric-canary-inputs-v1", "INPUT_SCHEMA")
    need(pins["parent"] == P8_PARENT, "P8_PARENT")
    for name in ("repositoryRoot", "p6PinsPath", "p6PinsSha256", "bridgeManifest",
                 "bridgeRoot", "javaHome", "pythonExe", "hostPath", "auditorPath",
                 "planPath", "apiPath", "hostContractPath"):
        need(type(pins[name]) is str and pins[name], "INPUT_FIELD_TYPE")
    need(type(pins["sourceRows"]) is list and type(pins["files"]) is list and
         type(pins["modeFiles"]) is dict, "INPUT_FIELD_TYPE")
    repository = canonical(pins["repositoryRoot"], want_dir=True)
    need(pins["p6PinsSha256"] == P6_PINS_SHA256, "P6_PINS_CONTEXT")
    repository = canonical(repository, want_dir=True)
    p6, _baseline, p6files = verify_p6_context(helper, pins)
    production = validate_sources(helper, repository, pins, p6)

    expected_modes = build_expected_modes(repository, p6)
    p6_writer = canonical(p6["modeFiles"]["writer"], want_dir=False)
    p6_writer_row = require_row(p6files, p6_writer, code="P6_FILE_PIN")
    maintained_writer = next((row for row in pins["sourceRows"] if row["path"] ==
                              "tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java"),
                             None)
    need(maintained_writer is not None and
         (maintained_writer["bytes"], maintained_writer["sha256"]) ==
         (p6_writer_row["bytes"], p6_writer_row["sha256"]), "WRITER_SOURCE")
    exact_keys(pins["modeFiles"], set(P8_MODE_ROLES), "MODE_ROLE_KEYS")
    actual_modes = {}
    for role, expected in expected_modes.items():
        value = pins["modeFiles"][role]
        need(type(value) is str and canonical(value, want_dir=False) == Path(expected),
             "ROLE_MAPPING")
        actual_modes[role] = canonical(value, want_dir=False)
    need(canonical(pins["auditorPath"], want_dir=False) == canonical(HERE / "audit_canary_inputs.py",
                                                                      want_dir=False),
         "SELF_PIN")
    need(canonical(pins["hostPath"], want_dir=False) == canonical(HERE / "run-canary.ps1",
                                                                  want_dir=False),
         "HOST_PATH")
    need(canonical(pins["planPath"], want_dir=False) == canonical(HERE / "PLAN_R1.md",
                                                                  want_dir=False) and
         canonical(pins["apiPath"], want_dir=False) == canonical(HERE / "API_SCHEMA_R1.md",
                                                                 want_dir=False) and
         canonical(pins["hostContractPath"], want_dir=False) == canonical(HERE / "CANARY_HOST_R1.md",
                                                                          want_dir=False),
         "CONTROL_PATHS")
    need(canonical(pins["bridgeRoot"], want_dir=True) == canonical(p6["bridgeRoot"], want_dir=True) and
         canonical(pins["bridgeManifest"], want_dir=False) ==
         canonical(p6["bridgeManifest"], want_dir=False), "BRIDGE_CONTEXT")

    file_rows = row_map(pins["files"], "EXTRA_FILE_ROW")
    expected_paths = expected_extra_paths(helper, pins, p6, p6files, expected_modes)
    need(set(file_rows) == set(expected_paths), "EXTRA_FILE_CENSUS")
    self_path = canonical(HERE / "audit_canary_inputs.py", want_dir=False)
    self_row = require_row(file_rows, self_path, code="SELF_PIN")
    assert_file(helper, self_path, self_row, "SELF_PIN")
    host_path = canonical(pins["hostPath"], want_dir=False)
    host_row = require_row(file_rows, host_path, HOST_SHA256, "HOST_PIN")
    need(host_row["bytes"] == HOST_BYTES, "HOST_PIN")
    assert_file(helper, host_path, host_row, "HOST_PIN")
    for key, path in expected_paths.items():
        row = file_rows[key]
        # The P6-authenticated subset stays exactly bound to its accepted row.
        p6key = os.path.normcase(str(path))
        p6row = p6files.get(p6key)
        if path == canonical(pins["p6PinsPath"], want_dir=False):
            need(row["sha256"] == P6_PINS_SHA256, "P6_PINS_HASH")
        elif path == canonical(pins["pythonExe"], want_dir=False):
            need(row["sha256"] == PYTHON_SHA256, "TOOL_PIN")
        elif path == canonical(Path(pins["javaHome"]) / "bin/java.exe", want_dir=False):
            need(row["sha256"] == JAVA_SHA256, "TOOL_PIN")
        elif path == canonical(Path(pins["javaHome"]) / "bin/javac.exe", want_dir=False):
            need(row["sha256"] == JAVAC_SHA256, "TOOL_PIN")
        else:
            need(p6row is None or
                 (row["bytes"], row["sha256"]) == (p6row["bytes"], p6row["sha256"]),
                 "P6_FILE_PIN")
        assert_file(helper, path, row)
    for name, digest in CONTROL_SHA256.items():
        path = canonical(HERE / name, want_dir=False)
        row = require_row(file_rows, path, digest, "CONTROL_DOCUMENT_HASH")
        assert_file(helper, path, row, "CONTROL_DOCUMENT_HASH")
    validate_tools(helper, pins)
    validate_bridges(helper, pins, p6, p6files)
    validate_overlay(helper, pins, file_rows, p6, p6files)
    return p6, file_rows, expected_paths, production


def close_inputs(helper, manifest_path: Path, manifest_raw: bytes,
                 pins: dict, file_rows: dict[str, dict], expected_paths: dict[str, Path]) -> None:
    need(checked_read(helper, manifest_path, 4194304, "INPUT_PINS_HASH") == manifest_raw,
         "CLOSE_CHANGED")
    p6_path = canonical(pins["p6PinsPath"], want_dir=False)
    p6_raw = checked_read(helper, p6_path, 4194304, "P6_PINS_HASH")
    need(sha(p6_raw) == P6_PINS_SHA256, "CLOSE_CHANGED")
    for key, path in expected_paths.items():
        assert_file(helper, path, file_rows[key], "CLOSE_CHANGED")
    repository = canonical(pins["repositoryRoot"], want_dir=True)
    for row in pins["sourceRows"]:
        path = repository.joinpath(*PurePosixPath(row["path"]).parts)
        raw = checked_read(helper, path, 104857600, "CLOSE_CHANGED")
        need(len(raw) == row["bytes"] and sha(raw) == row["sha256"], "CLOSE_CHANGED")


def validate_run(pins_path_value: str, expected_sha: str) -> dict:
    need(sys.flags.isolated and sys.dont_write_bytecode, "PYTHON_ISOLATION_REQUIRED")
    need(re.fullmatch(r"[0-9a-f]{64}", expected_sha) is not None, "INPUT_PINS_HASH")
    manifest_path = canonical(pins_path_value, want_dir=False)
    manifest_raw_bootstrap = bootstrap_read(manifest_path, 4194304)
    need(sha(manifest_raw_bootstrap) == expected_sha, "INPUT_PINS_HASH")
    pins_bootstrap = parse_bootstrap(manifest_raw_bootstrap)
    need(type(pins_bootstrap) is dict and set(pins_bootstrap) == TOP_KEYS, "INPUT_MANIFEST_KEYS")

    # Authenticate the immutable P6 reader before importing its stable IO/JSON
    # helper. No unauthenticated source module is executed.
    p6_path = canonical(pins_bootstrap.get("p6PinsPath", ""), want_dir=False)
    need(p6_path == canonical(P6 / "input-pins-r6.json", want_dir=False) and
         pins_bootstrap.get("p6PinsSha256") == P6_PINS_SHA256, "P6_PINS_CONTEXT")
    p6_raw_bootstrap = bootstrap_read(p6_path, 4194304)
    need(sha(p6_raw_bootstrap) == P6_PINS_SHA256, "P6_PINS_HASH")
    p6_bootstrap = parse_bootstrap(p6_raw_bootstrap)
    need(type(p6_bootstrap) is dict and p6_bootstrap.get("schema") ==
         "tsj-canonical-prefix-stage-inputs-v1" and p6_bootstrap.get("parent") == P6_PARENT,
         "P6_CONTEXT")
    p6_files_bootstrap = row_map(p6_bootstrap.get("files"), "P6_FILE_ROW")
    helper_path = canonical(p6_bootstrap.get("preflight", ""), want_dir=False)
    need(helper_path == canonical(P6 / "audit_inputs.py", want_dir=False), "P6_AUDITOR_PATH")
    helper_row = require_row(p6_files_bootstrap, helper_path, P6_AUDITOR_SHA256, "P6_AUDITOR_PIN")
    helper_raw = bootstrap_read(helper_path, 1048576)
    need(len(helper_raw) == helper_row["bytes"] and sha(helper_raw) == P6_AUDITOR_SHA256,
         "P6_AUDITOR_PIN")
    helper = import_p6_helper(helper_path, helper_raw)

    manifest_raw = checked_read(helper, manifest_path, 4194304, "INPUT_PINS_HASH")
    need(manifest_raw == manifest_raw_bootstrap and sha(manifest_raw) == expected_sha,
         "INPUT_PINS_HASH")
    pins = checked_parse(helper, manifest_raw, "INPUT_MANIFEST_JSON")
    canonical_manifest = (json.dumps(pins, sort_keys=True, indent=2,
                                     ensure_ascii=True) + "\n").encode("ascii")
    need(manifest_raw == canonical_manifest, "INPUT_MANIFEST_FORMAT")
    p6, file_rows, expected_paths, production = validate_manifest(helper, pins)
    close_inputs(helper, manifest_path, manifest_raw, pins, file_rows, expected_paths)
    result = {
        "status": "PASS_Q100_GEOMETRIC_CANARY_INPUTS_ONLY",
        "sourceRows": 1084,
        "productionFiles": production,
        "extraFiles": len(file_rows),
        "pinsSha256": expected_sha,
    }
    return result


def main() -> int:
    if len(sys.argv) != 3:
        print("P8_INPUT_AUDIT_FAIL USAGE", file=sys.stderr)
        return 2
    try:
        result = validate_run(sys.argv[1], sys.argv[2])
        line = "PASS_Q100_GEOMETRIC_CANARY_INPUTS_ONLY " + json.dumps(
            result, sort_keys=True, separators=(",", ":")) + "\n"
        encoded = line.encode("ascii")
        if len(encoded) > 65536:
            raise Reject("RESULT_SIZE")
        sys.stdout.buffer.write(encoded)
        sys.stdout.buffer.flush()
        return 0
    except Reject as exc:
        print("P8_INPUT_AUDIT_FAIL " + exc.code, file=sys.stderr)
        return 2
    except Exception:
        print("P8_INPUT_AUDIT_FAIL INPUTS_INVALID", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
