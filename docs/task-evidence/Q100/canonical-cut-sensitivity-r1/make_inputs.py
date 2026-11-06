"""Freeze the bounded P7 cut-sensitivity input census; this does not run analysis."""
from __future__ import annotations

import hashlib
import json
import math
import os
from pathlib import Path
import stat
import sys
import tempfile

sys.dont_write_bytecode = True

P7 = Path(__file__).resolve(strict=True).parent
Q = P7.parent
P6 = Q / "canonical-prefix-rejection-stages-r1"
P5 = Q / "canonical-prefix-blockers-r1"
P4 = Q / "canonical-prefix-capture-r1"

PARENT = "23f94f8f4ea8025fe24475fabcb194840e472fd8"
P6_PARENT = "831d69a41d9504bfb845caef6942e9ef58c9d6d2"
P6_PINS_SHA = "b640587f9cb1ac3f3ec8feff85940f4b7a9c8fda37570b50bfa30c39fd067771"
P6_READER_SHA = "1d58c06ecb3581ad7d8c9eef1dfcd653d8ba1e5b797b2dc7cda76b2ed621a96e"
P6_QUAL_SHA = "d7f39f1a77e302e9e883b4aedbdeb9c416a93cf4cfa85029710b04da5b25d692"
GRAPH_SHA = "c146cef4336d96eb6b6597569115d746d6a1fffe3b0c2ee2759d0e259d8b2a00"
VIA_SHA = "924edc27078935e18da981117a09011989233afafff2345402c7bd5c1105d9b1"
CAPTURE_HELPER_SHA = "26cbdc32361f07f759cc32dd3fe432fd640e329ce7e51dd583094dc10d0af962"
PYTHON_SHA = "ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec"
POWERSHELL_SHA = "60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f"
VERIFIER_MODULE_SHA = "fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c"
MAX_PIN_BYTES = 8_388_608
MAX_SOURCE_BYTES = 67_108_864
CHUNK_BYTES = 32_768
LIMITS = {
    "workNanos": 90_000_000_000,
    "groupNanos": 5_000_000_000,
    "groups": 640,
    "batchEntries": 4096,
    "cloneEntries": 1_048_576,
    "observationUnits": 32768,
    "artifactBytes": 8_388_608,
    "resultBytes": 65536,
    "lineBytes": 65536,
    "readChunkBytes": 32768,
    "specBytes": 4_194_304,
    "parentMs": 180000,
    "workerMs": 180000,
    "cleanupMs": 120000,
    "outerMs": 1800000,
}
SPEC_FIELDS = set(
    "schema parent repositoryRoot sourceManifest sourceManifestSha256 "
    "parentReader parentReaderSha256 parentPins parentPinsSha256 parentRun "
    "parentQualification parentQualificationSha256 transport transportSha256 "
    "blockers blockersSha256 stages stagesSha256 cutInput cutInputSha256 "
    "plan planSha256 protocol protocolSha256 schemaDocument schemaDocumentSha256 "
    "analysisModule analysisModuleSha256 worker workerSha256 tests testsSha256 "
    "graphOracle graphOracleSha256 viaOracle viaOracleSha256 captureReader "
    "captureReaderSha256 selectedPython selectedPythonSha256 selectedPowerShell "
    "selectedPowerShellSha256 files limits addendum addendumSha256".split()
)
RUN_ROOT_FILES = (
    "compiler-launch-intent.json",
    "compiler-process.json",
    "compiler-sources.txt",
    "consumed-bridge-copies.json",
    "host-launch.json",
    "host-result.json",
    "native-capture-limit-negative.json",
    "native-capture-negative-intent.json",
    "native-launch-intent.json",
    "native-process.json",
    "post-source-process.json",
    "pre-native-process.json",
    "preflight-process.json",
    "scratch-intent.json",
)
RUN_DATA_FILES = (
    "canonical-stage-index.json",
    "fixed-prefix.jsonl",
    "op-1.jsonl",
    "prefix-blockers.json",
    "prefix-stages.json",
)


class Rejected(ValueError):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


def need(condition: bool, code: str) -> None:
    if not condition:
        raise Rejected(code)


def sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def canonical_bytes(value: object) -> bytes:
    return (
        json.dumps(
            value,
            sort_keys=True,
            ensure_ascii=True,
            allow_nan=False,
            separators=(",", ":"),
        )
        + "\n"
    ).encode("ascii")


def _pairs(pairs):
    result = {}
    for key, value in pairs:
        need(type(key) is str and key not in result, "JSON_DUPLICATE_KEY")
        result[key] = value
    return result


def _finite_float(text: str) -> float:
    value = float(text)
    need(math.isfinite(value), "JSON_NONFINITE")
    return value


def _reject_constant(_text: str):
    raise Rejected("JSON_NONFINITE")


def decode_json(raw: bytes):
    need(type(raw) is bytes and not raw.startswith(b"\xef\xbb\xbf"), "JSON_BYTES")
    try:
        return json.loads(
            raw.decode("utf-8", "strict"),
            object_pairs_hook=_pairs,
            parse_float=_finite_float,
            parse_constant=_reject_constant,
        )
    except Rejected:
        raise
    except (UnicodeError, json.JSONDecodeError):
        raise Rejected("JSON_INVALID") from None


def _key(info):
    return (
        info.st_dev,
        info.st_ino,
        info.st_size,
        info.st_mtime_ns,
        info.st_nlink,
    )


def _local_metadata(info):
    return (
        getattr(info, "st_ctime_ns", None),
        getattr(info, "st_birthtime_ns", getattr(info, "st_birthtime", None)),
        getattr(info, "st_file_attributes", None),
    )


def _reject_reparse(path: Path) -> None:
    for item in (path, *path.parents):
        try:
            info = item.lstat()
        except OSError:
            raise Rejected("PATH_MISSING") from None
        need(not stat.S_ISLNK(info.st_mode), "PATH_REPARSE")
        need(not (getattr(info, "st_file_attributes", 0) & 0x400), "PATH_REPARSE")


def existing_path(value, want_directory: bool | None = None) -> Path:
    path = Path(os.fspath(value))
    need(path.is_absolute(), "ABSOLUTE_PATH_REQUIRED")
    normalized = os.path.normpath(os.fspath(path))
    need(os.path.normcase(normalized) == os.path.normcase(os.fspath(path)), "PATH_NOT_CANONICAL")
    _reject_reparse(path)
    try:
        resolved = path.resolve(strict=True)
    except OSError:
        raise Rejected("PATH_MISSING") from None
    need(os.path.normcase(os.fspath(resolved)) == os.path.normcase(normalized), "PATH_NOT_CANONICAL")
    info = resolved.stat()
    if want_directory is True:
        need(stat.S_ISDIR(info.st_mode), "DIRECTORY_REQUIRED")
    elif want_directory is False:
        need(stat.S_ISREG(info.st_mode), "FILE_REQUIRED")
    return resolved


def stable_file(path: Path, maximum: int, collect: bool = False):
    path = existing_path(path, want_directory=False)
    before = path.stat()
    need(stat.S_ISREG(before.st_mode) and 0 < before.st_size <= maximum, "FILE_SIZE_OR_TYPE")
    hasher = hashlib.sha256()
    chunks = [] if collect else None
    count = 0
    try:
        with path.open("rb") as stream:
            opened = os.fstat(stream.fileno())
            need(_key(before) == _key(opened), "FILE_OPEN_CHANGED")
            while True:
                chunk = stream.read(CHUNK_BYTES)
                if not chunk:
                    break
                count += len(chunk)
                need(count <= maximum, "FILE_GROWTH")
                hasher.update(chunk)
                if collect:
                    chunks.append(chunk)
            closed = os.fstat(stream.fileno())
    except Rejected:
        raise
    except OSError:
        raise Rejected("FILE_READ") from None
    after_path = existing_path(path, want_directory=False)
    need(after_path == path, "FILE_PATH_CHANGED")
    after = after_path.stat()
    need(_key(before) == _key(opened) == _key(closed) == _key(after), "FILE_CHANGED")
    need(_local_metadata(before) == _local_metadata(after), "FILE_PATH_METADATA_CHANGED")
    need(_local_metadata(opened) == _local_metadata(closed), "FILE_HANDLE_METADATA_CHANGED")
    need(count == before.st_size, "FILE_LENGTH")
    raw = b"".join(chunks) if collect else None
    return path, count, hasher.hexdigest(), raw


def read_json(path: Path, maximum: int = 4_194_304, expected_sha: str | None = None):
    resolved, size, digest, raw = stable_file(path, maximum, collect=True)
    if expected_sha is not None:
        need(digest == expected_sha, "INPUT_HASH")
    return resolved, size, digest, decode_json(raw)


def file_row(path: Path, expected=None) -> dict:
    resolved, size, digest, _ = stable_file(path, MAX_PIN_BYTES)
    if expected is not None:
        need(type(expected) is dict and set(expected) >= {"path", "bytes", "sha256"}, "EXPECTED_PIN")
        need(type(expected["bytes"]) is int and expected["bytes"] == size, "INPUT_LENGTH")
        need(type(expected["sha256"]) is str and expected["sha256"] == digest, "INPUT_HASH")
        expected_path = existing_path(expected["path"], want_directory=False)
        need(expected_path == resolved, "INPUT_PATH")
    return {"path": os.fspath(resolved), "bytes": size, "sha256": digest}


def _within(path: Path, root: Path) -> bool:
    try:
        path.relative_to(root)
        return True
    except ValueError:
        return False


def new_output(value, repo: Path, parent_source: Path, temp_root: Path) -> Path:
    path = Path(os.fspath(value))
    need(path.is_absolute(), "OUTPUT_ABSOLUTE_REQUIRED")
    normalized = os.path.normpath(os.fspath(path))
    need(os.path.normcase(normalized) == os.path.normcase(os.fspath(path)), "OUTPUT_NOT_CANONICAL")
    need(path.name not in ("", ".", ".."), "OUTPUT_NAME")
    parent = existing_path(path.parent, want_directory=True)
    candidate = parent / path.name
    need(not os.path.lexists(os.fspath(candidate)), "OUTPUT_EXISTS")
    need(_within(candidate, temp_root), "OUTPUT_NOT_TEMP")
    need(not _within(candidate, repo) and not _within(candidate, parent_source), "OUTPUT_IN_SOURCE")
    return candidate


def add_source_rows(rows, repo: Path, parent_source: Path) -> None:
    need(type(rows) is list and len(rows) == 1084, "SOURCE_ROW_COUNT")
    seen = set()
    for row in rows:
        need(type(row) is dict and set(row) == {"role", "path", "bytes", "sha256"}, "SOURCE_ROW_FIELDS")
        need(type(row["role"]) is str and row["role"], "SOURCE_ROLE")
        need(type(row["path"]) is str and row["path"], "SOURCE_RELATIVE_PATH")
        relative = Path(row["path"])
        need(not relative.is_absolute() and ".." not in relative.parts, "SOURCE_ESCAPE")
        need(relative.as_posix() == row["path"] and "." not in relative.parts, "SOURCE_PATH")
        need(type(row["bytes"]) is int and 0 < row["bytes"] <= MAX_SOURCE_BYTES, "SOURCE_SIZE")
        digest = row["sha256"]
        need(type(digest) is str and len(digest) == 64 and all(c in "0123456789abcdef" for c in digest),
             "SOURCE_SHA")
        key = row["path"].casefold()
        need(key not in seen, "SOURCE_DUPLICATE")
        seen.add(key)
        for root in (repo, parent_source):
            _, size, actual, _ = stable_file(root.joinpath(*relative.parts), MAX_SOURCE_BYTES)
            need(size == row["bytes"] and actual == digest, "SOURCE_HASH")


class Census:
    def __init__(self):
        self.rows = {}

    def add(self, path, expected=None) -> dict:
        row = file_row(Path(path), expected)
        key = os.path.normcase(row["path"])
        old = self.rows.get(key)
        if old is not None:
            need(old == row, "CONFLICTING_DUPLICATE_PIN")
            return old
        self.rows[key] = row
        return row

    def sorted_rows(self):
        need(0 < len(self.rows) <= 512, "PIN_CENSUS")
        return sorted(self.rows.values(), key=lambda row: os.path.normcase(row["path"]))


def _assert_source_json_pin(census: Census, path: Path, expected_sha: str, key: str):
    resolved, _, digest, value = read_json(path, expected_sha=expected_sha)
    census.add(resolved, {"path": os.fspath(resolved), "bytes": resolved.stat().st_size, "sha256": digest})
    return resolved, value


def main(argv=None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    try:
        need(len(args) == 3, "ARGUMENTS")
        p6_pins_path = existing_path(P6 / "input-pins-r6.json", want_directory=False)
        _, _, _, p6pins = read_json(p6_pins_path, expected_sha=P6_PINS_SHA)
        need(type(p6pins) is dict and p6pins.get("parent") == P6_PARENT, "P6_PINS_IDENTITY")
        need(type(p6pins.get("files")) is list and len(p6pins["files"]) == 84, "P6_FILE_CENSUS")
        need(type(p6pins.get("sourceRows")) is list and len(p6pins["sourceRows"]) == 1084,
             "P6_SOURCE_ROWS")

        repository = existing_path(args[0], want_directory=True)
        parent_source = existing_path(p6pins["repositoryRoot"], want_directory=True)
        need(repository != parent_source, "SOURCE_ROOT_IDENTITY")
        temp_root = existing_path(tempfile.gettempdir(), want_directory=True)
        spec_path = new_output(args[1], repository, parent_source, temp_root)
        manifest_path = new_output(args[2], repository, parent_source, temp_root)
        need(os.path.normcase(os.fspath(spec_path)) != os.path.normcase(os.fspath(manifest_path)),
             "OUTPUT_COLLISION")

        baseline_path = existing_path(P6 / "source-baseline-r1.json", want_directory=False)
        need(existing_path(p6pins["baselineManifestPath"], want_directory=False) == baseline_path,
             "P6_BASELINE_PATH")
        _, _, _, baseline = read_json(
            baseline_path,
            expected_sha=p6pins["baselineManifestSha256"],
        )
        need(type(baseline) is dict and baseline.get("files") == p6pins["sourceRows"]
             and len(baseline["files"]) == 1084, "SOURCE_BASELINE")
        need(baseline.get("parent") == P6_PARENT, "SOURCE_BASELINE_PARENT")
        source_rows = [dict(row) for row in p6pins["sourceRows"]]
        add_source_rows(source_rows, repository, parent_source)

        inventory = {
            "schema": "tsj-canonical-cut-source-inventory-v1",
            "parent": PARENT,
            "repositoryRoot": os.fspath(repository),
            "parentSourceRoot": os.fspath(parent_source),
            "files": source_rows,
        }
        inventory_raw = canonical_bytes(inventory)
        need(len(inventory_raw) <= 4_194_304, "SOURCE_MANIFEST_SIZE")
        try:
            with manifest_path.open("xb") as stream:
                stream.write(inventory_raw)
                stream.flush()
                os.fsync(stream.fileno())
        except OSError:
            raise Rejected("SOURCE_MANIFEST_WRITE") from None
        manifest_row = file_row(manifest_path)
        manifest_sha = manifest_row["sha256"]

        p6_reader = P6 / "read_stages.py"
        p6_qual = P6 / "qualification-r6.json"
        p6_plan = P6 / "NEXT_SENSITIVITY_PLAN.md"
        _, p6_reader_size, p6_reader_sha, _ = stable_file(p6_reader, MAX_PIN_BYTES)
        need(p6_reader_sha == P6_READER_SHA, "P6_READER_PIN")
        _, p6_qual_size, p6_qual_sha, p6qual = stable_file(p6_qual, MAX_PIN_BYTES, collect=True)
        need(p6_qual_sha == P6_QUAL_SHA, "P6_QUALIFICATION_PIN")
        p6qual_value = decode_json(p6qual)
        need(p6qual_value.get("status") == "PASS_FINITE_ZERO_DELTA_STAGE_DIAGNOSIS_ONLY"
             and p6qual_value.get("inputPinsSha256") == P6_PINS_SHA, "P6_QUALIFICATION_STATUS")

        p5_cut = existing_path(p6pins["fullCutInputPath"], want_directory=False)
        need(p5_cut.name == "cut-input-r1.json" and p5_cut.parent == existing_path(P5, True),
             "P5_CUT_INPUT_PATH")
        need(p6pins.get("fullCutInputSha256") == "1a1d7739d3a8ce4409896b56477c68f38b12b8d77f0d0264e16b0b3cf1bf70c5",
             "P5_CUT_INPUT_PIN")

        preflight_path = P6 / "observe-r6" / "preflight-process.json"
        _, _, _, preflight = read_json(preflight_path, maximum=1_048_576)
        preflight_script = existing_path(P6 / "audit_inputs.py", want_directory=False)
        expected_preflight_args = [
            "-B",
            os.fspath(preflight_script),
            os.fspath(parent_source),
            os.fspath(baseline_path),
            p6pins["baselineManifestSha256"],
            os.fspath(p6_pins_path),
            P6_PINS_SHA,
        ]
        need(
            type(preflight) is dict
            and type(preflight.get("FilePath")) is str
            and preflight.get("Arguments") == expected_preflight_args
            and type(preflight.get("ExitCode")) is int
            and preflight["ExitCode"] == 0
            and preflight.get("TerminationProven") is True
            and preflight.get("Stderr") == ""
            and type(preflight.get("ProcessId")) is int
            and preflight["ProcessId"] > 0
            and type(preflight.get("ProcessStartTicks")) is int
            and preflight["ProcessStartTicks"] > 0,
            "P6_PREFLIGHT_RECEIPT",
        )
        selected_python = existing_path(preflight["FilePath"], want_directory=False)
        _, _, selected_python_sha, _ = stable_file(selected_python, MAX_PIN_BYTES)
        need(selected_python_sha == PYTHON_SHA, "SELECTED_PYTHON_PIN")
        system_root = os.environ.get("SystemRoot") or os.environ.get("WINDIR")
        need(type(system_root) is str and system_root, "SYSTEM_ROOT")
        selected_powershell = existing_path(
            Path(system_root) / "System32" / "WindowsPowerShell" / "v1.0" / "powershell.exe",
            want_directory=False,
        )
        _, _, selected_powershell_sha, _ = stable_file(selected_powershell, MAX_PIN_BYTES)
        need(selected_powershell_sha == POWERSHELL_SHA, "SELECTED_POWERSHELL_PIN")

        census = Census()
        for row in p6pins["files"]:
            need(type(row) is dict and set(row) == {"path", "bytes", "sha256"}, "P6_PIN_FIELDS")
            census.add(row["path"], row)
        for path, expected in (
            (p6_pins_path, P6_PINS_SHA),
            (p6_reader, P6_READER_SHA),
            (p6_qual, P6_QUAL_SHA),
            (baseline_path, None),
            (p6_plan, None),
        ):
            census.add(path)
            if expected is not None:
                _, _, actual, _ = stable_file(Path(path), MAX_PIN_BYTES)
                need(actual == expected, "P6_REQUIRED_PIN")

        for run_name in ("observe-r6", "null-r6"):
            run_root = P6 / run_name
            existing_path(run_root, want_directory=True)
            for filename in RUN_ROOT_FILES:
                census.add(run_root / filename)
            for filename in RUN_DATA_FILES:
                census.add(run_root / "data" / filename)

        for run_name in ("observe-r6", "null-r6"):
            run_root = P6 / run_name
            for filename in ("native-process.json", "compiler-process.json"):
                _, _, _, receipt = read_json(run_root / filename, maximum=1_048_576)
                need(type(receipt) is dict and type(receipt.get("FilePath")) is str, "P6_TOOL_RECEIPT")
                census.add(receipt["FilePath"])

        p6_index_path = P6 / "observe-r6" / "data" / "canonical-stage-index.json"
        _, _, _, p6_index = read_json(p6_index_path, maximum=65536)
        need(p6_index.get("mode") == "OBSERVE"
             and p6_index.get("sourceManifestSha256") == P6_PINS_SHA, "P6_OBSERVE_INDEX")

        census.add(p5_cut, {"path": os.fspath(p5_cut), "bytes": p5_cut.stat().st_size,
                            "sha256": p6pins["fullCutInputSha256"]})

        p4_manifest_path = existing_path(P4 / "oracles-r1.json", want_directory=False)
        _, _, _, p4_oracles = read_json(p4_manifest_path, maximum=65536)
        need(type(p4_oracles) is list and len(p4_oracles) == 3, "P4_ORACLE_MANIFEST")
        p4_by_name = {}
        for row in p4_oracles:
            need(type(row) is dict and set(row) == {"path", "bytes", "sha256"}, "P4_ORACLE_FIELDS")
            name = Path(row["path"]).name
            need(name in ("read_fixed_prefix_graph.py", "fixed_prefix_minimum_vias.py",
                          "read_fixed_prefix_capture.py") and name not in p4_by_name,
                 "P4_ORACLE_NAME")
            p4_by_name[name] = row
        need(p4_by_name["read_fixed_prefix_graph.py"]["sha256"] == GRAPH_SHA
             and p4_by_name["fixed_prefix_minimum_vias.py"]["sha256"] == VIA_SHA
             and p4_by_name["read_fixed_prefix_capture.py"]["sha256"] == CAPTURE_HELPER_SHA,
             "P4_ORACLE_PIN")
        p4_oracle_dir = existing_path(P4 / "oracles-r1", want_directory=True)
        graph_oracle = p4_oracle_dir / "read_fixed_prefix_graph.py"
        via_oracle = p4_oracle_dir / "fixed_prefix_minimum_vias.py"
        capture_helper = p4_oracle_dir / "read_fixed_prefix_capture.py"
        census.add(p4_manifest_path)
        census.add(graph_oracle, {"path": os.fspath(graph_oracle), **{
            "bytes": p4_by_name["read_fixed_prefix_graph.py"]["bytes"],
            "sha256": GRAPH_SHA,
        }})
        census.add(via_oracle, {"path": os.fspath(via_oracle), **{
            "bytes": p4_by_name["fixed_prefix_minimum_vias.py"]["bytes"],
            "sha256": VIA_SHA,
        }})
        census.add(capture_helper, {"path": os.fspath(capture_helper), **{
            "bytes": p4_by_name["read_fixed_prefix_capture.py"]["bytes"],
            "sha256": CAPTURE_HELPER_SHA,
        }})
        capture_reader = P4 / "read_capture.py"
        census.add(capture_reader)

        files = (
            ("parentPins", p6_pins_path),
            ("parentReader", p6_reader),
            ("parentQualification", p6_qual),
            ("transport", P6 / "observe-r6" / "data" / "fixed-prefix.jsonl"),
            ("blockers", P6 / "observe-r6" / "data" / "prefix-blockers.json"),
            ("stages", P6 / "observe-r6" / "data" / "prefix-stages.json"),
            ("cutInput", p5_cut),
            ("plan", p6_plan),
            ("protocol", P7 / "PROTOCOL_R1.md"),
            ("schemaDocument", P7 / "SCHEMA_R1.md"),
            ("addendum", P7 / "SCHEMA_ADDENDUM_R2.md"),
            ("analysisModule", P7 / "cut_sensitivity_core.py"),
            ("worker", P7 / "run_cut_sensitivity.py"),
            ("tests", P7 / "test_cut_sensitivity.py"),
            ("graphOracle", graph_oracle),
            ("viaOracle", via_oracle),
            ("captureReader", capture_reader),
            ("selectedPython", selected_python),
            ("selectedPowerShell", selected_powershell),
            ("sourceManifest", manifest_path),
        )
        role_values = {}
        for role, path in files:
            row = census.add(path)
            role_values[role] = os.fspath(Path(row["path"]))
            role_values[role + "Sha256"] = row["sha256"]

        for path in (
            P7 / "SOURCE_CENSUS_R3.md",
            P7 / "HOST_CLOSURE_R4.md",
            P7 / "read_cut_sensitivity.py",
            P7 / "run-cut-sensitivity.ps1",
            P7 / "run-selected-host.ps1",
            P7 / "make_inputs.py",
            repository / "scripts" / "VerifierIsolation.psm1",
        ):
            census.add(path)
        _, _, verifier_sha, _ = stable_file(repository / "scripts" / "VerifierIsolation.psm1",
                                             MAX_PIN_BYTES)
        need(verifier_sha == VERIFIER_MODULE_SHA, "VERIFIER_MODULE_PIN")

        role_values.update({
            "schema": "tsj-canonical-cut-sensitivity-input-v1",
            "parent": PARENT,
            "repositoryRoot": os.fspath(repository),
            "parentRun": os.fspath(existing_path(P6 / "observe-r6", want_directory=True)),
            "sourceManifest": os.fspath(manifest_path),
            "sourceManifestSha256": manifest_sha,
            "files": census.sorted_rows(),
            "limits": LIMITS,
        })
        need(set(role_values) == SPEC_FIELDS, "SPEC_FIELDS")
        spec_raw = canonical_bytes(role_values)
        need(len(spec_raw) <= LIMITS["specBytes"], "SPEC_SIZE")
        try:
            with spec_path.open("xb") as stream:
                stream.write(spec_raw)
                stream.flush()
                os.fsync(stream.fileno())
        except OSError:
            raise Rejected("SPEC_WRITE") from None

        print(json.dumps({
            "status": "P7_INPUTS_FROZEN_SOURCE_ONLY",
            "parent": PARENT,
            "sourceRows": len(source_rows),
            "pinnedFiles": len(role_values["files"]),
            "sourceManifestSha256": manifest_sha,
            "specSha256": sha256(spec_raw),
        }, sort_keys=True, separators=(",", ":")))
        return 0
    except Rejected as error:
        print(json.dumps({"status": "REJECTED", "error": error.code},
                         sort_keys=True, separators=(",", ":")))
        return 2
    except Exception:
        print(json.dumps({"status": "REJECTED", "error": "INPUT_OR_OUTPUT"},
                         sort_keys=True, separators=(",", ":")))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
