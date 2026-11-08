"""Derive two standalone consumers of one byte-pinned observation V2 kernel.

Generation reads source only. Parent readers and the kernel are never executed;
the existing pinned P loader/exporter own checked source and output I/O.
The full component CLI remains unavailable pending whole-protocol migration.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import stat
import sys
import types

GENERIC_READER_SHA256 = "685e18d44f322a61d36f1aafa430cb562a5a7f536e2aa9c2640be929881d646b"
FULL_READER_SHA256 = "2d53b33fb77e85c6c36f46b013612883866cffea2996596751ffcb2d30e2bc0a"
KERNEL_SHA256 = "502ff2e1dec1c4d01ddb4e2841aae6f7e4073a62975c1cc9668a09cf5a52b89c"
BASE_GENERATOR_SHA256 = "d11df617dc255257e3ac42cba45a9a8d78482f4fec9ea52f846a6f48f97ce1f0"
IO_HELPER_SHA256 = "df9c297f4eb491117bade351ceedf26ccef5059e47ad9a7f36006000f1a10923"
MAX_SOURCE_BYTES = 4 * 1024 * 1024
MAX_BASE_BYTES = 256 * 1024
SCHEMA = "tsj-general-observation-consumer-overlay-v2"
CLI_FLAGS = ("--generic-reader", "--full-reader", "--kernel", "--base-generator",
             "--io-helper", "--output-dir")
OUTPUT_NAMES = ("read_selected_observation_v2.py",
                "read_full_observation_component_v2.py", "SOURCE_PROOF.json")
CHANGES = (
    "GENERIC_SNAPSHOT_ADAPTER", "GENERIC_HEAD_ADAPTER", "GENERIC_ACCOUNTING_ADAPTER",
    "FULL_SNAPSHOT_ADAPTER", "FULL_OBSERVATION_ADAPTER", "RETIRE_FULL_V1_CONSTANTS",
    "SANITIZE_FULL_DEPENDENCY_DEFAULT", "DENY_FULL_COMPONENT_CLI",
    "EMBED_IDENTICAL_KERNEL_ONCE_PER_READER",
)


class OverlayError(ValueError):
    def __init__(self, code):
        self.code = code
        super().__init__(code)


def need(condition, code):
    if not condition:
        raise OverlayError(code)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def replace_once(raw, before, after):
    need(type(raw) is bytes and type(before) is bytes and type(after) is bytes,
         "SPLICE_TYPES")
    need(bool(before) and raw.count(before) == 1, "SPLICE_COUNT")
    return raw.replace(before, after, 1)


def replace_region(raw, start, end, replacement, code):
    need(type(raw) is bytes and type(start) is bytes and type(end) is bytes
         and type(replacement) is bytes and bool(start) and bool(end), "SPLICE_TYPES")
    need(raw.count(start) == 1, code + "_START")
    need(raw.count(end) == 1, code + "_END_COUNT")
    begin = raw.index(start)
    finish = raw.find(end, begin + len(start))
    need(finish >= 0, code + "_END")
    return replace_once(raw, raw[begin:finish], replacement)


GENERIC_SNAPSHOT = b'''def _snapshot(s, c):
    """Keep the fixed R profile; project authenticated rows for the kernel."""
    net = _context(c)
    keys(s, SNAPSHOT_KEYS, "SNAPSHOT_KEYS")
    need(s["netId"] == net and c["traversedNetIds"] == [net], "SNAPSHOT_NET")
    start, root = s["startPadId"], s["rootPadId"]
    need(type(start) is str and type(root) is str and start != root
         and start in c["padsById"] and root in c["padsById"], "SNAPSHOT_PADS")
    pads = {pad_id: (pad["netId"], pad["x"], pad["y"])
            for pad_id, pad in c["padsById"].items()}
    try:
        ObservationV2Kernel.validate_snapshot(
            s, c["requestedNetIds"], c["traversedNetIds"], pads,
            {net: set(pads)}, (c["gridWidth"], c["gridHeight"]))
    except ObservationV2Error as failure:
        raise ReaderError(failure.code) from None


'''

GENERIC_HEAD = b'''def _head(h, s):
    try:
        return ObservationV2Kernel.validate_head(h, s)
    except ObservationV2Error as failure:
        raise ReaderError(failure.code) from None


'''

GENERIC_ACCOUNTING = b'''def _observation_accounting(o):
    try:
        return ObservationV2Kernel.accounting(o)
    except ObservationV2Error as failure:
        raise ReaderError(failure.code) from None


'''

FULL_SNAPSHOT = b'''def _validate_snapshot(snapshot, requested, traversed, pads_by_id, pads_by_net,
                       expected_grid_dimensions):
    try:
        return ObservationV2Kernel.validate_snapshot(
            snapshot, requested, traversed, pads_by_id, pads_by_net,
            expected_grid_dimensions)
    except ObservationV2Error as failure:
        raise ReaderError(failure.code) from None


'''

FULL_OBSERVATION = b'''def _validate_observation(value, arm_role, fresh_run, facts, captured,
                          pads_by_id, pads_by_net, expected_grid_dimensions):
    """Observation component only; captured is retained for caller compatibility."""
    try:
        return ObservationV2Kernel.validate_observation(
            value, arm_role, fresh_run["work"]["advanceCalls"],
            fresh_run["ordering"]["requestedNetIds"],
            fresh_run["ordering"]["traversedNetIds"], pads_by_id, pads_by_net,
            expected_grid_dimensions, facts["outcome"], facts["terminalFailure"])
    except ObservationV2Error as failure:
        raise ReaderError(failure.code) from None


'''

FULL_MAIN = b'''def main(argv=None) -> int:
    # No argument parsing, dependency loading, input reading or artifact writes.
    sys.stderr.write("GENERAL_OBSERVATION_COMPONENT_ONLY\\n")
    return 2


'''


def _pinned_source(raw, expected, code):
    need(type(raw) is bytes and 0 < len(raw) <= MAX_SOURCE_BYTES, code + "_SIZE")
    need(sha(raw) == expected, code + "_HASH")
    need(b"\r" not in raw and raw.endswith(b"\n"), code + "_LINE_ENDINGS")


def _kernel_pin_ready():
    need(type(KERNEL_SHA256) is str
         and re.fullmatch(r"[0-9a-f]{64}", KERNEL_SHA256) is not None,
         "ROOT_KERNEL_PIN_REQUIRED")


def _embed_kernel(reader, kernel):
    anchor = b"import types\n"
    need(reader.count(anchor) == 1, "KERNEL_IMPORT_ANCHOR")
    need(kernel not in reader and b"class ObservationV2Kernel:" not in reader
         and b"class ObservationV2Error(" not in reader, "KERNEL_ALREADY_PRESENT")
    result = replace_once(reader, anchor, anchor + b"\n" + kernel + b"\n")
    need(result.count(kernel) == 1, "KERNEL_EMBED_COUNT")
    return result


def derive(generic, full, kernel):
    """Pure source projection; no parent/kernel import or execution."""
    _kernel_pin_ready()
    for raw, expected, code in (
        (generic, GENERIC_READER_SHA256, "GENERIC_READER"),
        (full, FULL_READER_SHA256, "FULL_READER"),
        (kernel, KERNEL_SHA256, "KERNEL"),
    ):
        _pinned_source(raw, expected, code)
    need(b"from __future__" not in kernel and b"__main__" not in kernel,
         "KERNEL_STANDALONE_DOMAIN")
    r = replace_region(generic, b"def _snapshot(s, c):\n", b"def _head(h, s):\n",
                       GENERIC_SNAPSHOT, "GENERIC_SNAPSHOT")
    r = replace_region(r, b"def _head(h, s):\n", b"def _observation_accounting(o):\n",
                       GENERIC_HEAD, "GENERIC_HEAD")
    r = replace_region(r, b"def _observation_accounting(o):\n", b"def _case(value, name):\n",
                       GENERIC_ACCOUNTING, "GENERIC_ACCOUNTING")
    d = replace_region(full, b"def _validate_snapshot(snapshot, requested, traversed, pads_by_id, pads_by_net,\n",
                       b"def _validate_observation(value, arm_role, fresh_run, facts, captured,\n",
                       FULL_SNAPSHOT, "FULL_SNAPSHOT")
    d = replace_region(d, b"def _validate_observation(value, arm_role, fresh_run, facts, captured,\n",
                       b"def _validate_arm(value, arm_role, cohort_ids, captured, nreader,\n",
                       FULL_OBSERVATION, "FULL_OBSERVATION")
    # These constants have no remaining consumers outside the retired domains.
    for before in (
        b'OBSERVATION_SCHEMA = "tsj-cohort-terminal-observation-v1"\n',
        b'MAX_CALLBACKS = 247\n', b'MAX_OBSERVER_EVENT_UNITS = 161\n',
    ):
        d = replace_once(d, before, b"")
    d = replace_region(d, b"OBSERVATION_KEYS = (\n", b"ARM_CLEANUP_KEYS = (\n",
                       b"", "FULL_OBSOLETE_KEYS")
    # Structural anchor only: never place the personal parent literal in this
    # generator or proof. The exact source pin authenticates the assignment.
    pattern = rb'^N_ROOT = Path\("[^"\r\n]{1,1024}"\)\n'
    matches = tuple(re.finditer(pattern, d, re.MULTILINE))
    need(len(matches) == 1, "FULL_DEPENDENCY_DEFAULT_ANCHOR")
    d = replace_once(d, matches[0].group(),
                     b'N_ROOT = ROOT / "_unavailable_full_dependencies"\n')
    d = replace_region(d, b"def main(argv=None) -> int:\n",
                       b'if __name__ == "__main__":\n', FULL_MAIN, "FULL_MAIN")
    r, d = _embed_kernel(r, kernel), _embed_kernel(d, kernel)
    need(len(r) <= MAX_SOURCE_BYTES and len(d) <= MAX_SOURCE_BYTES, "DERIVED_SOURCE_SIZE")
    return r, d, CHANGES


def load_base_generator(path_value):
    """Reuse the frozen public P bootstrap from the selected-wire generator."""
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
    except (OSError, RuntimeError):
        raise OverlayError("BASE_GENERATOR_READ") from None
    need(0 < len(raw) <= MAX_BASE_BYTES and sha(raw) == BASE_GENERATOR_SHA256,
         "BASE_GENERATOR_HASH")
    module = types.ModuleType("_pinned_general_observation_base")
    module.__file__ = "<pinned-general-observation-base>"
    try:
        exec(compile(raw, module.__file__, "exec"), module.__dict__)
    except Exception:
        raise OverlayError("BASE_GENERATOR_LOAD") from None
    return module


def parse_args(argv):
    need(type(argv) in (list, tuple) and len(argv) == 2 * len(CLI_FLAGS), "CLI_ARGUMENTS")
    options = {}
    for at in range(0, len(argv), 2):
        flag, value = argv[at:at + 2]
        need(type(flag) is str and flag in CLI_FLAGS and flag not in options
             and type(value) is str and value and "\0" not in value
             and not value.startswith("--"), "CLI_ARGUMENTS")
        options[flag] = value
    need(set(options) == set(CLI_FLAGS), "CLI_ARGUMENTS")
    return options


def generate(options):
    need(type(options) is dict and set(options) == set(CLI_FLAGS), "GENERATOR_OPTIONS")
    need(all(type(value) is str and value and "\0" not in value
             for value in options.values()), "GENERATOR_OPTIONS")
    # Check raw strings first: Path normalizes aliases such as dot components.
    for flag in CLI_FLAGS:
        need(Path(options[flag]).is_absolute(), "INPUT_ABSOLUTE")
        need(str(Path(options[flag])) == options[flag], "INPUT_CANONICAL")
    _kernel_pin_ready()
    base = load_base_generator(options["--base-generator"])
    need(base.IO_HELPER_SHA256 == IO_HELPER_SHA256, "IO_HELPER_PIN")
    io = base.load_io(options["--io-helper"])
    output = io._checked_directory(options["--output-dir"], "OUTPUT_DIRECTORY")
    need(not io._directory_names(output, "OUTPUT_DIRECTORY"), "OUTPUT_NOT_EMPTY")
    generic = io._read_regular(options["--generic-reader"], MAX_SOURCE_BYTES, "GENERIC_READER")
    full = io._read_regular(options["--full-reader"], MAX_SOURCE_BYTES, "FULL_READER")
    kernel = io._read_regular(options["--kernel"], MAX_SOURCE_BYTES, "KERNEL")
    r, d, changes = derive(generic, full, kernel)
    proof = {
        "schema": SCHEMA, "status": "GENERATED_SOURCE_COMPONENTS_ONLY_NOT_FULL_D",
        "genericParentSha256": sha(generic), "genericParentBytes": len(generic),
        "fullParentSha256": sha(full), "fullParentBytes": len(full),
        "kernelSha256": sha(kernel), "kernelBytes": len(kernel),
        "baseGeneratorSha256": BASE_GENERATOR_SHA256, "ioHelperSha256": IO_HELPER_SHA256,
        "derivedGenericSha256": sha(r), "derivedGenericBytes": len(r),
        "derivedFullComponentSha256": sha(d), "derivedFullComponentBytes": len(d),
        "changes": list(changes), "observationSchema": "tsj-cohort-terminal-observation-v2",
        "observationKeys": 18, "kernelEmbedding": "VERBATIM_ONCE_PER_READER",
        "genericProfile": "UNCHANGED_FIXED_SIX_CASES",
        "fullWorkSummation": "UNCHANGED_OBSERVATION_TOTALS_ALREADY_HEAD_INCLUSIVE",
        "fullDependencyDefault": "ROOT_RELATIVE_UNAVAILABLE_NO_READ",
        "fullCli": "GENERAL_OBSERVATION_COMPONENT_ONLY",
        "fullDQualification": False, "authoredInvocations": 0, "budgets": "UNCHANGED",
    }
    proof_raw = (json.dumps(proof, sort_keys=True, indent=2) + "\n").encode("ascii")
    files = dict(zip(OUTPUT_NAMES, (r, d, proof_raw)))
    for name, raw in files.items():
        output = io._checked_directory(output, "OUTPUT_DIRECTORY")
        io._write_exclusive(output / name, raw, MAX_SOURCE_BYTES, "OUTPUT_FILE")
    output = io._checked_directory(output, "OUTPUT_DIRECTORY")
    names = io._directory_names(output, "OUTPUT_DIRECTORY")
    need(len(names) == 3 and set(names) == set(OUTPUT_NAMES), "OUTPUT_CLOSURE")
    for name, raw in files.items():
        need(io._read_regular(output / name, MAX_SOURCE_BYTES, "OUTPUT_READBACK") == raw,
             "OUTPUT_READBACK_CHANGED")
    return proof


def main(argv=None):
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        sys.stderr.write("GENERAL_OBSERVATION_OVERLAY_FAIL PYTHON_ISOLATION\n")
        return 2
    try:
        result = generate(parse_args(sys.argv[1:] if argv is None else argv))
    except Exception as failure:
        code = getattr(failure, "code", "INTERNAL_ERROR")
        if type(code) is not str or not code or any(
                c not in "ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789" for c in code):
            code = "INTERNAL_ERROR"
        sys.stderr.write("GENERAL_OBSERVATION_OVERLAY_FAIL " + code + "\n")
        return 2
    sys.stdout.write(json.dumps({"schema": SCHEMA, "status": result["status"],
                               "files": 3, "kernelSha256": result["kernelSha256"]},
                              separators=(",", ":")) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
