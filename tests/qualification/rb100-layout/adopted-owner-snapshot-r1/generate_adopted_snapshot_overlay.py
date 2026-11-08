"""Derive the adopted-owner closed-snapshot native source overlay.

Only the byte-pinned final Study is transformed. The pinned public generator
and exporter retain their ownership of selected source, file, and output I/O.
"""
from __future__ import annotations

import hashlib
import json
import stat
import sys
import types
from pathlib import Path

STUDY_SHA256 = "7d62a0f343a270056bfd7e536145a47874ccc90c10cd24b93ec89ee6d6f94570"
BASE_GENERATOR_SHA256 = "d11df617dc255257e3ac42cba45a9a8d78482f4fec9ea52f846a6f48f97ce1f0"
IO_HELPER_SHA256 = "df9c297f4eb491117bade351ceedf26ccef5059e47ad9a7f36006000f1a10923"
MAX_SOURCE_BYTES = 4 * 1024 * 1024
MAX_BASE_BYTES = 256 * 1024
SCHEMA = "tsj-native-adopted-snapshot-overlay-r1"
CHANGES = (
    "DENY_ALL_MAIN_ENTRIES",
    "REQUIRE_ADOPTED_OWNER_FOR_CLOSED_SNAPSHOT",
)


class OverlayError(ValueError):
    """Stable path-free diagnostic for a rejected derivation."""

    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


def need(condition: bool, code: str) -> None:
    if not condition:
        raise OverlayError(code)


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def replace_once(raw: bytes, before: bytes, after: bytes) -> bytes:
    need(type(raw) is bytes and type(before) is bytes and type(after) is bytes,
         "SPLICE_TYPES")
    need(bool(before) and raw.count(before) == 1, "SPLICE_COUNT")
    return raw.replace(before, after, 1)


MAIN_BEFORE = b'''    public static void main(String[] args) throws Exception {
        long started=System.nanoTime();
        if (args==null || args.length!=6 ||
                !"GENERIC_ROUTING_R7_REFERENCE_V1".equals(args[0]) ||
                args[1]==null || !UUID.matcher(args[1]).matches() ||
                args[2]==null || !SHA.matcher(args[2]).matches() ||
                !"4142410d8719f2e3b73edc9481ba9a2a40f0476f19f242844e97b42bde366741".equals(args[3]) ||
                !"5d2b62c98bd229a73be0e54e4b6adcde4d5080fb87046d8bd770455f80c1224a".equals(args[4]))
            throw new IllegalStateException("GENERIC_ROUTING_R7_REFERENCE_ONLY");
        GenericRoutingReferenceCanary.run(args,started);
    }

'''
MAIN_AFTER = b'''    public static void main(String[] args) throws Exception {
        throw new IllegalStateException("ADOPTED_SNAPSHOT_COMPONENT_ONLY");
    }

'''
SNAPSHOT_BEFORE = b'''            groupUnits>=0 && groupUnits<=32768 && constructorEntries>=0 && prior==0 &&
            !ownerStarted && !ownerAdopted,
            "R5_METADATA_SNAPSHOT");'''
SNAPSHOT_AFTER = b'''            groupUnits>=0 && groupUnits<=32768 && constructorEntries>=0 && prior>=1 &&
            ownerStarted && ownerAdopted,
            "R5_METADATA_SNAPSHOT");'''


def derive(source: bytes) -> tuple[bytes, tuple[str, ...]]:
    need(type(source) is bytes and 0 < len(source) <= MAX_SOURCE_BYTES, "SOURCE_SIZE")
    need(sha(source) == STUDY_SHA256, "STUDY_HASH")
    need(b"\r" not in source, "STUDY_LINE_ENDINGS")
    result = source
    changes = []

    result = replace_once(result, MAIN_BEFORE, MAIN_AFTER)
    changes.append(CHANGES[0])
    result = replace_once(result, SNAPSHOT_BEFORE, SNAPSHOT_AFTER)
    changes.append(CHANGES[1])
    need(tuple(changes) == CHANGES and len(result) <= MAX_SOURCE_BYTES,
         "DERIVED_SOURCE_SIZE")
    return result, tuple(changes)


def load_base_generator(path_value: str) -> types.ModuleType:
    """Load only the byte-pinned public generator that owns helper selection."""
    need(type(path_value) is str and path_value and "\0" not in path_value,
         "BASE_GENERATOR_PATH")
    path = Path(path_value)
    need(path.is_absolute(), "BASE_GENERATOR_ABSOLUTE")
    need(str(path) == path_value, "BASE_GENERATOR_CANONICAL")
    try:
        for item in (path, *path.parents):
            info = item.lstat()
            if stat.S_ISLNK(info.st_mode) or (getattr(info, "st_file_attributes", 0) & 0x400):
                raise OverlayError("BASE_GENERATOR_REPARSE")
        need(path.resolve(strict=True) == path, "BASE_GENERATOR_CANONICAL")
        info = path.lstat()
        need(stat.S_ISREG(info.st_mode) and 0 < info.st_size <= MAX_BASE_BYTES,
             "BASE_GENERATOR_FILE")
        with path.open("rb") as stream:
            raw = stream.read(MAX_BASE_BYTES + 1)
    except OverlayError:
        raise
    except (OSError, RuntimeError, TypeError, ValueError):
        raise OverlayError("BASE_GENERATOR_READ") from None
    need(0 < len(raw) <= MAX_BASE_BYTES and sha(raw) == BASE_GENERATOR_SHA256,
         "BASE_GENERATOR_HASH")
    module = types.ModuleType("_pinned_adopted_snapshot_base")
    module.__file__ = "<pinned-adopted-snapshot-base>"
    try:
        exec(compile(raw, module.__file__, "exec"), module.__dict__)
    except Exception:
        raise OverlayError("BASE_GENERATOR_LOAD") from None
    return module


def parse_args(argv) -> dict[str, str]:
    keys = {"--study", "--base-generator", "--io-helper", "--output-dir"}
    need(type(argv) in (list, tuple) and len(argv) == 8, "CLI_ARGUMENTS")
    values = {}
    for at in range(0, 8, 2):
        key, value = argv[at:at + 2]
        need(type(key) is str and key in keys and key not in values and
             type(value) is str and value and "\0" not in value and not value.startswith("--"),
             "CLI_ARGUMENTS")
        values[key] = value
    need(set(values) == keys, "CLI_ARGUMENTS")
    return values


def generate(options: dict[str, str]) -> dict[str, object]:
    need(type(options) is dict and set(options) == {
        "--study", "--base-generator", "--io-helper", "--output-dir"
    }, "GENERATOR_OPTIONS")
    need(all(type(value) is str and value and "\0" not in value
             for value in options.values()), "GENERATOR_OPTIONS")
    # Reject lexical aliases before Path normalizes separators and dot segments.
    for option, code in (("--study", "OWNER_SOURCE"),
                         ("--base-generator", "BASE_GENERATOR"),
                         ("--io-helper", "IO_HELPER"),
                         ("--output-dir", "OUTPUT_DIRECTORY")):
        need(str(Path(options[option])) == options[option], code + "_CANONICAL")

    base = load_base_generator(options["--base-generator"])
    need(getattr(base, "IO_HELPER_SHA256", None) == IO_HELPER_SHA256, "IO_HELPER_PIN")
    io = base.load_io(options["--io-helper"])
    output = io._checked_directory(options["--output-dir"], "OUTPUT_DIRECTORY")
    need(not io._directory_names(output, "OUTPUT_DIRECTORY"), "OUTPUT_NOT_EMPTY")
    original = io._read_regular(options["--study"], MAX_SOURCE_BYTES, "OWNER_SOURCE")
    derived, changes = derive(original)

    proof = {
        "schema": SCHEMA,
        "status": "GENERATED_NATIVE_ONLY_NOT_COMPILED",
        "studyParentSha256": sha(original),
        "studyParentBytes": len(original),
        "baseGeneratorSha256": BASE_GENERATOR_SHA256,
        "ioHelperSha256": IO_HELPER_SHA256,
        "derivedSha256": sha(derived),
        "derivedBytes": len(derived),
        "changes": list(changes),
        "mainEntry": "DENY_BEFORE_ARGUMENT_PARSING_OR_IO",
        "closedSnapshot": "PRIOR_GROUPS_POSITIVE_AND_ORIGINAL_OWNER_STARTED_AND_ADOPTED",
        "otherSnapshotGuards": "UNCHANGED",
        "physicalOwner": "UNCHANGED",
        "authoredInvocations": 0,
        "budgets": "UNCHANGED",
    }
    proof_raw = (json.dumps(proof, sort_keys=True, indent=2) + "\n").encode("ascii")
    files = {
        "CohortTerminalTelemetryStudy.java": derived,
        "SOURCE_PROOF.json": proof_raw,
    }
    for name, raw in files.items():
        output = io._checked_directory(output, "OUTPUT_DIRECTORY")
        io._write_exclusive(output / name, raw, MAX_SOURCE_BYTES, "OUTPUT_FILE")
    output = io._checked_directory(output, "OUTPUT_DIRECTORY")
    names = io._directory_names(output, "OUTPUT_DIRECTORY")
    need(len(names) == 2 and set(names) == set(files), "OUTPUT_CLOSURE")
    for name, raw in files.items():
        need(io._read_regular(output / name, MAX_SOURCE_BYTES, "OUTPUT_READBACK") == raw,
             "OUTPUT_READBACK_CHANGED")
    return proof


def main(argv=None) -> int:
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        sys.stderr.write("ADOPTED_SNAPSHOT_OVERLAY_FAIL PYTHON_ISOLATION\n")
        return 2
    try:
        result = generate(parse_args(sys.argv[1:] if argv is None else argv))
    except Exception as failure:
        code = getattr(failure, "code", "INTERNAL_ERROR")
        if type(code) is not str or not code or any(
                c not in "ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789" for c in code):
            code = "INTERNAL_ERROR"
        sys.stderr.write("ADOPTED_SNAPSHOT_OVERLAY_FAIL " + code + "\n")
        return 2
    sys.stdout.write(json.dumps({
        "schema": SCHEMA,
        "status": result["status"],
        "derivedSha256": result["derivedSha256"],
        "files": 2,
    }, separators=(",", ":")) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())