"""Freeze the strict source and tool inputs for the P8 literal canary."""
from __future__ import annotations

import sys

if not sys.flags.isolated:
    sys.stderr.write("P8_INPUT_MAKER_FAIL ISOLATED_MODE_REQUIRED\n")
    raise SystemExit(2)
if not sys.dont_write_bytecode:
    sys.stderr.write("P8_INPUT_MAKER_FAIL BYTECODE_DISABLED_REQUIRED\n")
    raise SystemExit(2)

import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat


HERE = Path(__file__).resolve().parent
P6 = HERE.parent / "canonical-prefix-rejection-stages-r1"
P6_PINS_SHA256 = "b640587f9cb1ac3f3ec8feff85940f4b7a9c8fda37570b50bfa30c39fd067771"
P6_BASELINE_SHA256 = "f94eca24bac4b24cc264fe4ca5038c68cbbf54b8530bf529ed3ec66e48ecf019"
P6_AUDITOR_SHA256 = "5ac397cec89917d446a77e77c1c572eabfff28381b080ab1ad4bf800de59134d"
P6_PARENT = "831d69a41d9504bfb845caef6942e9ef58c9d6d2"
P8_PARENT = "0891474cadc4b9e00d7911e81c124721a11efb87"
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
CONTROL_SHA256 = {
    "PLAN_R1.md": "0bf95e2de4d2662f7d57604c910dab89081abcc11a7597288b19ffdaecc9dc93",
    "API_SCHEMA_R1.md": "915583d2594a74135bd3269c74a8c190cfb1d47c9d0aa28062b24578e17de7ff",
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


class Reject(Exception):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


def need(ok: bool, code: str) -> None:
    if not ok:
        raise Reject(code)


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def parse(raw: bytes):
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


def _no_reparse(path: Path) -> None:
    absolute = Path(os.path.abspath(path))
    for item in (absolute, *absolute.parents):
        st = item.lstat()
        need(not stat.S_ISLNK(st.st_mode), "REPARSE_PATH")
        need(not (getattr(st, "st_file_attributes", 0) & 0x400), "REPARSE_PATH")


def stable_read(path: Path, limit: int = 104857600) -> bytes:
    path = Path(os.path.abspath(path))
    _no_reparse(path)
    before = path.stat()
    need(stat.S_ISREG(before.st_mode) and 0 <= before.st_size <= limit, "FILE_SIZE")

    def identity(st):
        return st.st_dev, st.st_ino, st.st_size, st.st_mtime_ns, st.st_nlink

    with path.open("rb") as stream:
        opened = os.fstat(stream.fileno())
        raw = stream.read(limit + 1)
        closed = os.fstat(stream.fileno())
    after = path.stat()
    need(identity(before) == identity(opened) == identity(closed) == identity(after),
         "FILE_CHANGED")
    need(before.st_ctime_ns == after.st_ctime_ns and
         opened.st_ctime_ns == closed.st_ctime_ns, "FILE_CHANGED")
    need(len(raw) == after.st_size and len(raw) <= limit, "FILE_CHANGED")
    return raw


def canonical_existing(path_value: str | Path, want_dir: bool | None = None) -> Path:
    path = Path(path_value)
    need(path.is_absolute(), "ABSOLUTE_PATH_REQUIRED")
    need(str(path) == os.path.abspath(str(path)), "NONCANONICAL_PATH")
    _no_reparse(path)
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
    _no_reparse(raw_root)
    resolved_root = raw_root.resolve(strict=True)
    need(resolved_root.is_dir(), "SYSTEMROOT_PATH")
    canonical_root = canonical_existing(resolved_root, want_dir=True)
    return canonical_existing(
        canonical_root / "System32/WindowsPowerShell/v1.0/powershell.exe", want_dir=False)


def relpath(value: object) -> str:
    need(type(value) is str and value != "", "SOURCE_PATH")
    need("\\" not in value, "SOURCE_PATH")
    path = PurePosixPath(value)
    need(not path.is_absolute() and all(part not in ("", ".", "..") for part in path.parts),
         "SOURCE_PATH")
    return value


def exact_keys(value: object, keys: set[str], code: str) -> dict:
    need(type(value) is dict and set(value) == keys, code)
    return value


def exact_int(value: object, low: int = 0) -> bool:
    return type(value) is int and value >= low


def file_record(path: Path, expected: dict | None = None) -> dict:
    path = canonical_existing(path, want_dir=False)
    raw = stable_read(path, 8388608)
    row = {"path": str(path), "bytes": len(raw), "sha256": sha(raw)}
    if expected is not None:
        need(row["bytes"] == expected["bytes"] and row["sha256"] == expected["sha256"],
             "EXTRA_INPUT_HASH")
    return row


def p6_refs(p6: dict) -> dict[str, dict]:
    need(type(p6.get("files")) is list, "P6_FILES")
    result = {}
    for row in p6["files"]:
        exact_keys(row, {"path", "bytes", "sha256"}, "P6_FILE_ROW")
        path = canonical_existing(row["path"], want_dir=False)
        need(exact_int(row["bytes"]) and
             type(row["sha256"]) is str and re.fullmatch(r"[0-9a-f]{64}", row["sha256"]),
             "P6_FILE_ROW")
        key = os.path.normcase(str(path))
        need(key not in result, "P6_FILE_DUPLICATE")
        result[key] = row
    return result


def require_p6_file(refs: dict[str, dict], path: Path, digest: str | None = None) -> dict:
    path = canonical_existing(path, want_dir=False)
    row = refs.get(os.path.normcase(str(path)))
    need(row is not None, "P6_UNBOUND_FILE")
    if digest is not None:
        need(row["sha256"] == digest, "P6_FILE_PIN")
    return row


def build_inputs(repository_value: str, output_value: str,
                 java_home_value: str, python_exe_value: str) -> tuple[bytes, dict]:
    repository = canonical_existing(repository_value, want_dir=True)
    java_home = canonical_existing(java_home_value, want_dir=True)
    python_exe = canonical_existing(python_exe_value, want_dir=False)
    need(HERE == Path(__file__).resolve().parent, "SELF_PATH")

    output = Path(output_value)
    need(output.is_absolute(), "OUTPUT_ABSOLUTE_REQUIRED")
    output = Path(os.path.abspath(output))
    need(not output.exists(), "OUTPUT_EXISTS")
    parent = canonical_existing(output.parent, want_dir=True)
    need(not output.is_dir(), "OUTPUT_EXISTS")
    # The manifest can live beside this study, but it cannot overwrite an
    # authenticated input or be placed inside the E source checkout.
    try:
        output.relative_to(repository)
    except ValueError:
        pass
    else:
        raise Reject("OUTPUT_IN_SOURCE")

    p6_pins_path = canonical_existing(P6 / "input-pins-r6.json", want_dir=False)
    p6_raw = stable_read(p6_pins_path, 4194304)
    need(sha(p6_raw) == P6_PINS_SHA256, "P6_PINS_HASH")
    p6 = parse(p6_raw)
    need(type(p6) is dict and p6.get("schema") == "tsj-canonical-prefix-stage-inputs-v1",
         "P6_SCHEMA")
    need(p6.get("parent") == P6_PARENT, "P6_PARENT")
    need(type(p6.get("changedRows")) is list and not p6["changedRows"], "P6_SOURCE_DELTA")
    need(type(p6.get("sourceRows")) is list and len(p6["sourceRows"]) == 1084,
         "P6_SOURCE_CENSUS")
    need(type(p6.get("modeFiles")) is dict and set(p6["modeFiles"]) == set(P6_SOURCE_ROLES),
         "P6_MODE_KEYS")
    refs = p6_refs(p6)

    baseline_path = canonical_existing(p6["baselineManifestPath"], want_dir=False)
    need(sha(stable_read(baseline_path, 4194304)) == P6_BASELINE_SHA256 and
         p6["baselineManifestSha256"] == P6_BASELINE_SHA256, "P6_BASELINE_HASH")
    baseline = parse(stable_read(baseline_path, 4194304))
    need(type(baseline) is dict and baseline.get("schema") ==
         "tsj-prefix-rejection-source-baseline-v1", "P6_BASELINE_SCHEMA")
    need(baseline.get("parent") == P6_PARENT and
         Path(p6["historicalRoot"]) == Path(baseline["repositoryRoot"]), "P6_BASELINE_CONTEXT")
    need(p6["sourceRows"] == baseline.get("files"), "P6_SOURCE_CENSUS")
    require_p6_file(refs, baseline_path, P6_BASELINE_SHA256)

    p6_auditor = canonical_existing(p6["preflight"], want_dir=False)
    need(p6_auditor == P6 / "audit_inputs.py", "P6_AUDITOR_PATH")
    require_p6_file(refs, p6_auditor, P6_AUDITOR_SHA256)

    source_rows = p6["sourceRows"]
    production = 0
    source_seen = set()
    for row in source_rows:
        exact_keys(row, {"path", "bytes", "role", "sha256"}, "SOURCE_ROW")
        rel = relpath(row["path"])
        need(exact_int(row["bytes"]) and row["bytes"] <= 104857600 and
             type(row["sha256"]) is str and re.fullmatch(r"[0-9a-f]{64}", row["sha256"]) and
             type(row["role"]) is str, "SOURCE_ROW")
        need(rel not in source_seen, "SOURCE_DUPLICATE")
        source_seen.add(rel)
        raw = stable_read(repository.joinpath(*PurePosixPath(rel).parts))
        need(len(raw) == row["bytes"] and sha(raw) == row["sha256"], "SOURCE_HASH")
        if rel.startswith("src/"):
            production += 1
    need(len(source_rows) == 1084 and production == 850, "SOURCE_CENSUS")

    # The maintained writer belongs to E. Its bytes must match the accepted
    # P6 writer record exactly; all other P6 role paths remain untouched.
    p6_modes = p6["modeFiles"]
    writer_rel = Path("tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java")
    writer = canonical_existing(repository / writer_rel, want_dir=False)
    accepted_writer = canonical_existing(p6_modes["writer"], want_dir=False)
    accepted_writer_row = require_p6_file(refs, accepted_writer)
    need(file_record(writer, accepted_writer_row)["sha256"] ==
         accepted_writer_row["sha256"], "WRITER_SOURCE")

    mode_files = {
        "router": p6_modes["router"],
        "support": p6_modes["support"],
        "writer": str(writer),
        "adapter": p6_modes["adapter"],
        "blocker": p6_modes["blocker"],
        "queries": p6_modes["queries"],
        "stageInspector": p6_modes["stageInspector"],
        "netRouterOverlay": str(canonical_existing(HERE / "overlays-r1/PcbNetRouter.java",
                                                     want_dir=False)),
        "rulesOverlay": str(canonical_existing(HERE / "overlays-r1/PcbTwoLayerRules.java",
                                                 want_dir=False)),
        "reasonCanary": str(canonical_existing(
            HERE / "source/CanonicalGeometricReasonCanary.java", want_dir=False)),
    }
    need(set(mode_files) == set(P8_MODE_ROLES), "MODE_ROLE_KEYS")

    bridge_root = canonical_existing(p6["bridgeRoot"], want_dir=True)
    bridge_manifest = canonical_existing(p6["bridgeManifest"], want_dir=False)
    need(bridge_manifest.parent == bridge_root, "BRIDGE_CONTEXT")
    need(bridge_manifest == canonical_existing(
        P6.parent / "outf-graph-r1/host-prep-r1/host-authored-v6-r2/driver/scratch-bridge-inputs.json",
        want_dir=False), "BRIDGE_CONTEXT")
    bridge_rows = parse(stable_read(bridge_manifest, 65536))
    need(type(bridge_rows) is list and len(bridge_rows) == len(BRIDGES), "BRIDGE_MANIFEST")
    for row, expected in zip(bridge_rows, BRIDGES):
        need(type(row) is dict and set(row) == {"path", "bytes", "sha256"} and
             (row["path"], row["bytes"], row["sha256"]) == expected,
             "BRIDGE_MANIFEST")

    powershell = powershell_executable()
    java = canonical_existing(java_home / "bin/java.exe", want_dir=False)
    javac = canonical_existing(java_home / "bin/javac.exe", want_dir=False)
    need(sha(stable_read(python_exe, 104857600)) == PYTHON_SHA256, "PYTHON_PIN")
    need(sha(stable_read(java, 104857600)) == JAVA_SHA256, "JAVA_PIN")
    need(sha(stable_read(javac, 104857600)) == JAVAC_SHA256, "JAVAC_PIN")
    need(sha(stable_read(powershell, 104857600)) == POWERSHELL_SHA256, "POWERSHELL_PIN")

    files_by_key: dict[str, dict] = {}

    def add(path: Path, expected: dict | None = None) -> None:
        row = file_record(path, expected)
        key = os.path.normcase(row["path"])
        previous = files_by_key.get(key)
        need(previous is None or previous == row, "DUPLICATE_FILE_PIN")
        files_by_key[key] = row

    add(p6_pins_path, {"bytes": len(p6_raw), "sha256": P6_PINS_SHA256})
    add(baseline_path, require_p6_file(refs, baseline_path, P6_BASELINE_SHA256))
    add(p6_auditor, require_p6_file(refs, p6_auditor, P6_AUDITOR_SHA256))
    add(bridge_manifest, require_p6_file(refs, bridge_manifest))
    for name, _size, digest in BRIDGES:
        path = bridge_root / ("bridge-" + name)
        add(path, require_p6_file(refs, path, digest))
    for role in P6_SOURCE_ROLES:
        path = canonical_existing(p6_modes[role], want_dir=False)
        add(path, require_p6_file(refs, path))
    # Add all P8-owned contracts, host/reader/test sources, exact overlays, and
    # the input-producing tools themselves. Missing inputs are a hard failure.
    for relative in P8_CONSUMED_FILES:
        path = HERE / Path(relative)
        if relative in CONTROL_SHA256:
            row = file_record(path)
            need(row["sha256"] == CONTROL_SHA256[relative], "CONTROL_DOCUMENT_HASH")
            add(path, row)
        elif relative == "run-canary.ps1":
            host_row = file_record(path)
            need(host_row["bytes"] == HOST_BYTES and host_row["sha256"] == HOST_SHA256,
                 "HOST_PIN")
            add(path, host_row)
        else:
            add(path)
    add(python_exe, {"bytes": len(stable_read(python_exe, 104857600)), "sha256": PYTHON_SHA256})
    add(java, {"bytes": len(stable_read(java, 104857600)), "sha256": JAVA_SHA256})
    add(javac, {"bytes": len(stable_read(javac, 104857600)), "sha256": JAVAC_SHA256})
    add(powershell, {"bytes": len(stable_read(powershell, 104857600)),
                     "sha256": POWERSHELL_SHA256})
    need(len(files_by_key) == len({row["path"] for row in files_by_key.values()}),
         "DUPLICATE_FILE_PIN")

    pins = {
        "schema": "tsj-q100-geometric-canary-inputs-v1",
        "repositoryRoot": str(repository),
        "parent": P8_PARENT,
        "p6PinsPath": str(p6_pins_path),
        "p6PinsSha256": P6_PINS_SHA256,
        "sourceRows": source_rows,
        "files": sorted(files_by_key.values(), key=lambda row: row["path"].casefold()),
        "modeFiles": mode_files,
        "bridgeManifest": str(bridge_manifest),
        "bridgeRoot": str(bridge_root),
        "javaHome": str(java_home),
        "pythonExe": str(python_exe),
        "hostPath": str(canonical_existing(HERE / "run-canary.ps1", want_dir=False)),
        "auditorPath": str(canonical_existing(HERE / "audit_canary_inputs.py", want_dir=False)),
        "planPath": str(canonical_existing(HERE / "PLAN_R1.md", want_dir=False)),
        "apiPath": str(canonical_existing(HERE / "API_SCHEMA_R1.md", want_dir=False)),
        "hostContractPath": str(canonical_existing(HERE / "CANARY_HOST_R1.md", want_dir=False)),
    }
    raw = (json.dumps(pins, sort_keys=True, indent=2, ensure_ascii=True) + "\n").encode("ascii")
    need(len(raw) <= 4194304, "PINS_SIZE")
    # The path is created only after every source and consumed input is closed.
    need(parent.is_dir() and not output.exists(), "OUTPUT_EXISTS")
    try:
        with output.open("xb") as stream:
            stream.write(raw)
            stream.flush()
            os.fsync(stream.fileno())
    except FileExistsError as exc:
        raise Reject("OUTPUT_EXISTS") from exc
    need(stable_read(output, 4194304) == raw, "OUTPUT_CLOSE_CHANGED")
    result = {
        "status": "INPUTS_FROZEN_NOT_NATIVE_ACCEPTANCE",
        "sourceRows": len(source_rows),
        "productionFiles": production,
        "extraFiles": len(files_by_key),
        "pinsSha256": sha(raw),
    }
    return raw, result


def main() -> int:
    if len(sys.argv) != 5:
        print("P8_INPUT_MAKER_FAIL USAGE", file=sys.stderr)
        return 2
    try:
        _raw, result = build_inputs(*sys.argv[1:])
    except Reject as exc:
        print("P8_INPUT_MAKER_FAIL " + exc.code, file=sys.stderr)
        return 2
    except Exception:
        print("P8_INPUT_MAKER_FAIL INPUTS_INVALID", file=sys.stderr)
        return 2
    print(json.dumps(result, sort_keys=True, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
