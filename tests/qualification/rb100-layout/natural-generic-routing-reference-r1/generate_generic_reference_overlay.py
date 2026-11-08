"""Derive the byte-pinned native generic routing reference entry/header overlay.

Only source bytes are consumed. The public P generator bootstraps the pinned
exporter's checked I/O; no descriptor, captured body or authored input is read.
"""
from __future__ import annotations

import hashlib
import json
import stat
import sys
import types
from pathlib import Path

STUDY_SHA256 = "88986781508aa34783d11e651082122f4bf8e0710a2263b39cc9b597fce9a8cf"
BASE_GENERATOR_SHA256 = "d11df617dc255257e3ac42cba45a9a8d78482f4fec9ea52f846a6f48f97ce1f0"
IO_HELPER_SHA256 = "df9c297f4eb491117bade351ceedf26ccef5059e47ad9a7f36006000f1a10923"
PHYSICAL_OWNER_SHA256 = "7bf4ab57198ff9444381ec5d677c1a2bc309bfa3e7f01b188c631ffe5f2b966c"
WIRE_SHA256 = "dd738f0bbac0edc4f4cbaf4a0d2bba80ebeda9a581d79c052e29df8976e73dd6"
FIXTURE_ORACLE_SHA256 = "5d2b62c98bd229a73be0e54e4b6adcde4d5080fb87046d8bd770455f80c1224a"
MAX_SOURCE_BYTES = 4 * 1024 * 1024
MAX_BASE_BYTES = 256 * 1024
SCHEMA = "tsj-generic-routing-reference-overlay-v1"
PACKET_SCHEMA = "tsj-generic-routing-reference-v1"
PACKET_STATUS = "GENERIC_NATURAL_REFERENCES_RECORDED_ONLY"
ROOT_KEYS = (
    "schema", "status", "runId", "sourceManifestSha256", "wireContractSha256",
    "references", "sourceReview",
)
CHANGES = (
    "GENERIC_REFERENCE_MAIN", "HEADER_KEY_COUNT", "HEADER_KEYS",
    "HEADER_SCHEMA", "HEADER_STATUS", "HEADER_MEMBERS",
    "GEOMETRY_PATH_STATES", "GEOMETRY_MAP_TRANSITIONS", "GEOMETRY_CONTAINER_TYPES",
)
CLI_CODES = (
    ("--study", "OWNER_SOURCE"), ("--base-generator", "BASE_GENERATOR"),
    ("--io-helper", "IO_HELPER"), ("--output-dir", "OUTPUT_DIRECTORY"),
)


class OverlayError(ValueError):
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


def replace_region(raw: bytes, start: bytes, end: bytes, replacement: bytes,
                   code: str) -> bytes:
    need(type(raw) is bytes and type(start) is bytes and type(end) is bytes and
         type(replacement) is bytes and bool(start) and bool(end), "SPLICE_TYPES")
    need(raw.count(start) == 1, code + "_START")
    need(raw.count(end) == 1, code + "_END_COUNT")
    begin = raw.index(start)
    finish = raw.find(end, begin + len(start))
    need(finish >= 0, code + "_END")
    return replace_once(raw, raw[begin:finish], replacement)


MAIN = b'''    public static void main(String[] args) throws Exception {
        long started=System.nanoTime();
        if (args==null || args.length!=4 ||
                !"GENERIC_ROUTING_REFERENCE_V1".equals(args[0]) ||
                args[1]==null || !UUID.matcher(args[1]).matches() ||
                args[2]==null || !SHA.matcher(args[2]).matches() ||
                args[3]==null || !SHA.matcher(args[3]).matches() ||
                !"dd738f0bbac0edc4f4cbaf4a0d2bba80ebeda9a581d79c052e29df8976e73dd6".equals(args[3]))
            throw new IllegalStateException("GENERIC_ROUTING_REFERENCE_ONLY");
        GenericRoutingReferenceCanary.run(args,started);
    }

'''

OLD_KEYS = b'''    case 5:return "complete"; case 6:return "blocked"; case 7:return "nonBranchQuality";
    case 8:return "genericCallbackThrow"; case 9:return "directOwnerAbortReuse";
    case 10:return "sourceReview";
'''
NEW_KEYS = b'''    case 5:return "references"; case 6:return "sourceReview";
'''
OLD_MEMBERS = b'''        case 7:
            meter.charge(1);
            if (value==null) break;
            meter.charge(1);
            r5Require(value instanceof java.util.Map,"R9_CANARY_MEMBER");
            break;
        default:
            meter.charge(1);
            r5Require(value instanceof java.util.Map,"R9_CANARY_MEMBER");
'''
NEW_MEMBERS = b'''        case 5:
            meter.charge(1);
            r5Require(value instanceof java.util.Map,"R9_CANARY_MEMBER");
            meter.charge(1);
            r5Require(((java.util.Map<?,?>)value).size()==2,"R9_REFERENCE_KEYS");
            break;
        case 6:
            meter.charge(1);
            r5Require(value instanceof java.util.Map,"R9_CANARY_MEMBER");
            meter.charge(1);
            r5Require(((java.util.Map<?,?>)value).size()==7,"R9_SOURCE_REVIEW_KEYS");
            break;
'''

PATH_MAP_TRANSITIONS = b'''static int r9PathMapChild(int state,String key) {
    switch (state) {
    case R9_PATH_ROOT:
        return "references".equals(key)?R9_PATH_REFERENCES:R9_PATH_OTHER;
    case R9_PATH_REFERENCES:
        return "OPEN".equals(key) || "BLOCKED".equals(key)?R9_PATH_ARM:R9_PATH_OTHER;
    case R9_PATH_ARM:
        return "run".equals(key)?R9_PATH_FRESH:R9_PATH_OTHER;
    case R9_PATH_FRESH:
        return "layout".equals(key)?R9_PATH_LAYOUT:R9_PATH_OTHER;
    case R9_PATH_LAYOUT:
        return "components".equals(key)?R9_PATH_COMPONENTS:R9_PATH_OTHER;
    case R9_PATH_COMPONENT:
        return "geometryCanonical".equals(key)?R9_PATH_GEOMETRY:R9_PATH_OTHER;
    default:return R9_PATH_OTHER;
    }
}
'''
PATH_CONTAINER_TYPES = b'''static boolean r9PathTypeValid(int state,Object value) {
    switch (state) {
    case R9_PATH_ROOT:case R9_PATH_REFERENCES:case R9_PATH_ARM:case R9_PATH_FRESH:case R9_PATH_COMPONENT:
        return value instanceof java.util.Map;
    case R9_PATH_LAYOUT:
        return value==null || value instanceof java.util.Map;
    case R9_PATH_COMPONENTS:return value instanceof java.util.List;
    case R9_PATH_GEOMETRY:return value instanceof String;
    case R9_PATH_OTHER:return true;
    default:return false;
    }
}
'''


def derive(source: bytes) -> tuple[bytes, tuple[str, ...]]:
    need(type(source) is bytes and 0 < len(source) <= MAX_SOURCE_BYTES, "SOURCE_SIZE")
    need(sha(source) == STUDY_SHA256, "STUDY_HASH")
    need(b"\r" not in source, "STUDY_LINE_ENDINGS")
    result = replace_region(source,
                            b"    public static void main(String[] args) throws Exception {\n",
                            b"    static final class Arguments {\n", MAIN,
                            "GENERIC_REFERENCE_MAIN")
    changes = ["GENERIC_REFERENCE_MAIN"]

    def splice(name: str, before: bytes, after: bytes) -> None:
        nonlocal result
        result = replace_once(result, before, after)
        changes.append(name)

    splice("HEADER_KEY_COUNT", b"static final int R9_CANARY_KEYS=11;\n",
           b"static final int R9_CANARY_KEYS=7;\n")
    splice("HEADER_KEYS", OLD_KEYS, NEW_KEYS)
    splice("HEADER_SCHEMA",
           b'            r5Require("tsj-selected-observation-wire-canary-v2".equals(value),"R9_CANARY_SCHEMA");\n',
           b'            r5Require("tsj-generic-routing-reference-v1".equals(value),"R9_CANARY_SCHEMA");\n')
    splice("HEADER_STATUS",
           b'            r5Require("GENERIC_OBSERVATIONS_RECORDED_ONLY".equals(value),"R9_CANARY_STATUS");\n',
           b'            r5Require("GENERIC_NATURAL_REFERENCES_RECORDED_ONLY".equals(value),"R9_CANARY_STATUS");\n')
    splice("HEADER_MEMBERS", OLD_MEMBERS, NEW_MEMBERS)
    splice("GEOMETRY_PATH_STATES",
           b"static final int R9_PATH_ROOT=0, R9_PATH_ARM=1, R9_PATH_OPTIONAL_ARM=2,\n",
           b"static final int R9_PATH_ROOT=0, R9_PATH_ARM=1, R9_PATH_REFERENCES=2,\n")
    result = replace_region(result, b"static int r9PathMapChild(int state,String key) {\n",
                            b"static int r9PathListChild(int state) {\n",
                            PATH_MAP_TRANSITIONS, "GEOMETRY_MAP_TRANSITIONS")
    changes.append("GEOMETRY_MAP_TRANSITIONS")
    result = replace_region(result, b"static boolean r9PathTypeValid(int state,Object value) {\n",
                            b"static int r9PathStringLimit(int state) {",
                            PATH_CONTAINER_TYPES, "GEOMETRY_CONTAINER_TYPES")
    changes.append("GEOMETRY_CONTAINER_TYPES")
    need(tuple(changes) == CHANGES and len(result) <= MAX_SOURCE_BYTES,
         "DERIVED_SOURCE_SIZE")
    return result, tuple(changes)


def lexical_path(path_value: str, code: str) -> Path:
    """Reject raw lexical aliases before any pinned loader executes."""
    need(type(path_value) is str and path_value and "\0" not in path_value,
         code + "_PATH")
    path = Path(path_value)
    need(path.is_absolute(), code + "_ABSOLUTE")
    need(str(path) == path_value and ".." not in path.parts, code + "_CANONICAL")
    return path


def load_base_generator(path_value: str) -> types.ModuleType:
    """Same small bootstrap for the byte-pinned public checked-I/O owner."""
    path = lexical_path(path_value, "BASE_GENERATOR")
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
    except (OSError, RuntimeError):
        raise OverlayError("BASE_GENERATOR_READ") from None
    need(0 < len(raw) <= MAX_BASE_BYTES and sha(raw) == BASE_GENERATOR_SHA256,
         "BASE_GENERATOR_HASH")
    module = types.ModuleType("_pinned_generic_reference_base")
    module.__file__ = "<pinned-generic-reference-base>"
    try:
        exec(compile(raw, module.__file__, "exec"), module.__dict__)
    except Exception:
        raise OverlayError("BASE_GENERATOR_LOAD") from None
    return module


def parse_args(argv) -> dict[str, str]:
    keys = {flag for flag, _ in CLI_CODES}
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
    need(type(options) is dict and set(options) == {flag for flag, _ in CLI_CODES},
         "GENERATOR_OPTIONS")
    need(all(type(value) is str and value and "\0" not in value for value in options.values()),
         "GENERATOR_OPTIONS")
    for option, code in CLI_CODES:
        lexical_path(options[option], code)
    base = load_base_generator(options["--base-generator"])
    need(base.IO_HELPER_SHA256 == IO_HELPER_SHA256, "IO_HELPER_PIN")
    io = base.load_io(options["--io-helper"])
    output = io._checked_directory(options["--output-dir"], "OUTPUT_DIRECTORY")
    need(not io._directory_names(output, "OUTPUT_DIRECTORY"), "OUTPUT_NOT_EMPTY")
    original = io._read_regular(options["--study"], MAX_SOURCE_BYTES, "OWNER_SOURCE")
    derived, changes = derive(original)
    proof = {
        "schema": SCHEMA, "status": "GENERATED_NATIVE_ONLY_NOT_COMPILED",
        "studyParentSha256": sha(original), "studyParentBytes": len(original),
        "physicalOwnerSha256": PHYSICAL_OWNER_SHA256,
        "baseGeneratorSha256": BASE_GENERATOR_SHA256, "ioHelperSha256": IO_HELPER_SHA256,
        "wireContractSha256": WIRE_SHA256, "fixtureOracleSha256": FIXTURE_ORACLE_SHA256,
        "derivedSha256": sha(derived), "derivedBytes": len(derived),
        "changes": list(changes), "packetSchema": PACKET_SCHEMA,
        "packetStatus": PACKET_STATUS, "rootKeys": list(ROOT_KEYS),
        "references": "ORDERED_MAP_OPEN_BLOCKED", "sourceReviewKeys": 7,
        "headerAllocation": "EXISTING_SCALAR_SWITCH_NO_KEY_ARRAY",
        "headerMemberInspections": "PRECHARGED_TYPE_AND_MAP_SIZE",
        "geometryValuePath": "references.{OPEN,BLOCKED}.run.layout.components[index].geometryCanonical",
        "geometryPathChange": "EXISTING_ALLOWANCE_NEW_ENVELOPE_EXACT_CASE_MAP_AND_COMPONENT_LIST",
        "geometryTextLimit": 4096, "ordinaryTextLimit": 256,
        "publicationOwner": "UNCHANGED_R9_BODY_METER_TREE_COUNT_BYTE_COPY_STDOUT",
        "publicationFee": "3*ceil(bytes/4096)",
        "authoredInvocations": 0, "fullD": False,
        "physicalQualification": False, "solverQualification": False,
        "productionOwner": "UNCHANGED", "budgets": "UNCHANGED",
    }
    proof_raw = (json.dumps(proof, sort_keys=True, indent=2) + "\n").encode("ascii")
    files = {"CohortTerminalTelemetryStudy.java": derived, "SOURCE_PROOF.json": proof_raw}
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
        sys.stderr.write("GENERIC_REFERENCE_OVERLAY_FAIL PYTHON_ISOLATION\n")
        return 2
    try:
        result = generate(parse_args(sys.argv[1:] if argv is None else argv))
    except Exception as failure:
        code = getattr(failure, "code", "INTERNAL_ERROR")
        if type(code) is not str or not code or any(c not in "ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789" for c in code):
            code = "INTERNAL_ERROR"
        sys.stderr.write("GENERIC_REFERENCE_OVERLAY_FAIL " + code + "\n")
        return 2
    sys.stdout.write(json.dumps({
        "schema": SCHEMA, "status": result["status"],
        "derivedSha256": result["derivedSha256"], "files": 2,
    }, separators=(",", ":")) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
