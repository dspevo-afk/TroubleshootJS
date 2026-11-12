"""Bounded source preflight and gated normal-prior qualification caller.

Preflight authenticates only explicitly configured current SOURCE, selected
tools, roots, and the public allowlist metadata.  Qualification requires the
root-owned phase journal before it calls the fixed 20-role reader.  This file
does not follow manifest rows, invoke old producers, or produce a native call.
The caller must be launched with ``python -I -B`` on Windows.
"""

from __future__ import annotations

import argparse
import datetime
import hashlib
import json
import ntpath
import os
import re
import stat
import sys
import types


CONFIG_SCHEMA = "tsj-normal-prior-caller-config-v1"
SOURCE_MANIFEST_SCHEMA = "tsj-q100-normal-prior-source-manifest-v1"
SOURCE_MANIFEST_PURPOSE = "SOURCE_ONLY_BEFORE_DATA"
PHASE_JOURNAL_SCHEMA = "tsj-q100-normal-prior-phase-journal-v1"
PHASE_JOURNAL_STATUS = "ALLOW_FIXED_20_NORMAL_PRIOR_READ"
CURRENT_SOURCE_AUTHORITY_SCHEMA = "tsj-q100-current-source-authority-v1"
ROOT_BINDINGS_SCHEMA = "tsj-q100-normal-prior-root-bindings-v1"
ATTEMPT_SCHEMA = "tsj-q100-normal-prior-read-attempt-v1"
EVIDENCE_SCHEMA = "tsj-q100-normal-prior-qualification-evidence-v1"

ALLOWLIST_SHA256 = "c487eae6ed0e070aaad75dccd14d23313ed9da2c6bda7f93bfeb886109f20f91"
READER_SHA256 = "863a258cbab888cf112b9e927e16a099436632e9a1d43eefe0b70690c30543d8"
OBSERVER_SHA256 = "4786259585a13819350add727bb2bfbca9f6890db208fbe8f4f60959d8c94f8c"
NORMALIZER_SHA256 = "9112b6b4ceaf9721e92057e9a8ecdb5698fb602725c4131d937acbf1390c0081"
CODEC_SHA256 = "c1f27377d5d7a6c83384e34cc2ded334b0b022cfb68722265533976c0661ba27"
KERNEL_SHA256 = "f87c3ef799a47f14955240111d7af20737db3c329242c036f449eb1002f11873"
PROVENANCE_SHA256 = "215ad455f799117d30ca9a7e3101ee9326c8af4fdd30295c4fd0d28331a7436f"
READER_GATE_SHA256 = "3b44e3dcfcc2456c1b2cf09258da21abb7c7b04cdc65156c908657b1d1c507ef"
NORMALIZER_GATE_SHA256 = "7bb3b7467426274fc1bf1a0960129528d321fbc6218cd43a30e6690099bfdbfe"

N_PRIOR_SOURCE_SHA256 = "84c8dbf2805ab5607f1e79f1d1fa67cc1a2e4ec06d3a22fdd44771ce48d03b52"
P07_SOURCE_SHA256 = "a314cdc732069bca17f73a724cd21295af334d9baead82fcdb13bf0600c5554e"

SOURCE_NAMES = (
    "caller", "observer", "reader", "normalizer", "codec", "kernel",
    "provenance", "allowlist",
)
TOOL_NAMES = (
    "qualificationPython", "rootCallPowerShell", "nativeJava",
    "rootCallHarness", "processModule",
)
CONFIG_KEYS = (
    "schema", "sourceSpecs", "toolSpecs", "roots", "scratchRoot",
    "allowlistPath", "dataOutputRoot",
)
SPEC_KEYS = ("path", "expectedBytes", "maxBytes", "sha256")
SOURCE_PINS = {
    "observer": OBSERVER_SHA256,
    "reader": READER_SHA256,
    "normalizer": NORMALIZER_SHA256,
    "codec": CODEC_SHA256,
    "kernel": KERNEL_SHA256,
    "provenance": PROVENANCE_SHA256,
    "allowlist": ALLOWLIST_SHA256,
}
TOOL_FIXED_PINS = {
    "qualificationPython": (171744, "ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec"),
    "rootCallPowerShell": (495616, "60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f"),
    "rootCallHarness": (2941, "28aab617e686d2a4eec074a46b2e2861f6fa7c008544b33f7b070f0c6d77ea1d"),
    "processModule": (1053900, "fc533d9b15007d0eb42cccb10e39fbfc30fc89b732fbf977e295218d67197b73"),
}
MAX_CONFIG_BYTES = 1024 * 1024
MAX_MANIFEST_BYTES = 1024 * 1024
MAX_JOURNAL_BYTES = 64 * 1024
MAX_SOURCE_BYTES = 4 * 1024 * 1024
MAX_READER_TOOL_BYTES = 8 * 1024 * 1024
MAX_OUTPUT_BYTES = 1024 * 1024
MAX_PROVENANCE_BYTES = 1024 * 1024
HEX64 = re.compile(r"[0-9a-f]{64}\Z")
UTC_TEXT = re.compile(r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]{1,9})?Z\Z")

PROCESS_FIELDS = (
    "FilePath", "Arguments", "ProcessId", "ProcessStartTicks", "ExitCode",
    "Stdout", "Stderr", "StdoutLog", "StderrLog", "ProcessRoot",
    "TerminationProven",
)
OWNER_FIELDS = (
    "schema", "pid", "startTicks", "executable", "script", "request", "createdUtc",
)
PARENT_FIELDS = ("schema", "elapsedMs", "process")
REQUEST_FIELDS = ("FilePath", "Arguments", "LimitMs")
RESULT_FIELDS = (
    "status", "elapsedMs", "exitCode", "terminationProven", "stdoutBytes",
    "stderrBytes", "processRootAbsent", "totalElapsedMs",
)
ROLE_OUTPUT_NAMES = (
    ("context.json", "context"),
    ("native-projection.json", "projection"),
    ("current-input.java", "java"),
    ("qualification-evidence.json", "evidence"),
)
ATTEMPT_FILE = "read-attempt.json"
FAILURE_FILE = "failure-evidence.json"


class CallerError(ValueError):
    """Path-free, phase-labeled rejection from the caller."""

    def __init__(self, code, phase="caller"):
        if type(code) is not str or re.fullmatch(r"[A-Z0-9_]{1,96}", code) is None:
            code = "CALLER_ERROR"
        self.code = code
        self.phase = phase
        self.reporting_code = None
        super().__init__(code)


def _need(condition, code, phase="caller"):
    if not condition:
        raise CallerError(code, phase)


def _exact_keys(value, keys, code, phase="caller"):
    _need(type(value) is dict, code + "_TYPE", phase)
    _need(len(value) == len(keys) and set(value) == set(keys), code, phase)


def _is_int(value, low=0, high=2**63 - 1):
    return type(value) is int and low <= value <= high


def _is_sha(value):
    return type(value) is str and HEX64.fullmatch(value) is not None


def _valid_utc_text(value):
    if type(value) is not str or UTC_TEXT.fullmatch(value) is None:
        return False
    date_text, time_text = value[:-1].split("T", 1)
    try:
        datetime.date.fromisoformat(date_text)
    except ValueError:
        return False
    clock = time_text.split(".", 1)[0]
    hour, minute, second = (int(part) for part in clock.split(":"))
    return hour <= 23 and minute <= 59 and second <= 59


def _sha(raw):
    return hashlib.sha256(raw).hexdigest()


def _reject_number(_text):
    raise CallerError("JSON_FLOAT_OR_CONSTANT", "json")


def _pairs_no_duplicates(pairs):
    value = {}
    for key, child in pairs:
        _need(key not in value, "JSON_DUPLICATE_KEY", "json")
        value[key] = child
    return value


def _strict_json(raw, maximum, code, *, require_object=True):
    _need(type(raw) is bytes and 0 < len(raw) <= maximum, code + "_SIZE", "json")
    _need(not raw.startswith(b"\xef\xbb\xbf"), code + "_BOM", "json")
    try:
        value = json.loads(
            raw.decode("utf-8", errors="strict"),
            object_pairs_hook=_pairs_no_duplicates,
            parse_float=_reject_number,
            parse_constant=_reject_number,
        )
    except CallerError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError, ValueError, RecursionError):
        raise CallerError(code + "_JSON", "json") from None
    if require_object:
        _need(type(value) is dict, code + "_ROOT", "json")
    return value


def _canonical_json(value, *, newline=False):
    try:
        raw = json.dumps(value, ensure_ascii=True, sort_keys=False,
                         separators=(",", ":"), allow_nan=False).encode("ascii")
    except (TypeError, ValueError, UnicodeError, RecursionError):
        raise CallerError("JSON_SERIALIZE", "serialize") from None
    return raw + (b"\n" if newline else b"")


def _freeze_digest(value, field):
    material = {key: child for key, child in value.items() if key != field}
    value[field] = _sha(_canonical_json(material))
    return value


def _path_text(value, code):
    _need(type(value) is str and bool(value) and len(value) <= 32767
          and "\x00" not in value and "\r" not in value and "\n" not in value,
          code, "config")
    _need(ntpath.isabs(value) and not value.startswith("\\\\"), code + "_ABSOLUTE", "config")
    return value


def _norm_path(value):
    return ntpath.normcase(ntpath.normpath(value.replace("/", "\\")))


def _bootstrap_snapshot(st):
    return (st.st_dev, st.st_ino, st.st_mode, st.st_size,
            getattr(st, "st_mtime_ns", int(st.st_mtime * 1_000_000_000)),
            getattr(st, "st_ctime_ns", int(st.st_ctime * 1_000_000_000)),
            getattr(st, "st_file_attributes", 0))


def _bootstrap_handle_identity(snapshot):
    return (snapshot[0], snapshot[1], stat.S_IFMT(snapshot[2]),
            snapshot[3], snapshot[4], snapshot[6])


def _bootstrap_handle_anchor(snapshot):
    return (snapshot[0], snapshot[1], stat.S_IFMT(snapshot[2]))


def _bootstrap_read(path, maximum, phase):
    """Bounded leaf-only bootstrap for config and the pinned reader source.

    Full canonical ancestor/reparse checks are performed immediately through
    the authenticated fixed reader before these bytes are used for any other
    source/tool or before a phase journal is accepted.
    """
    _path_text(path, "BOOTSTRAP_PATH")
    try:
        before_stat = os.lstat(path)
    except OSError:
        raise CallerError("BOOTSTRAP_PATH", phase) from None
    reparse = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
    _need(stat.S_ISREG(before_stat.st_mode) and not stat.S_ISLNK(before_stat.st_mode)
          and not (getattr(before_stat, "st_file_attributes", 0) & reparse),
          "BOOTSTRAP_FILE_KIND", phase)
    before = _bootstrap_snapshot(before_stat)
    _need(before_stat.st_size <= maximum, "BOOTSTRAP_SIZE", phase)
    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(path, flags)
    except OSError:
        raise CallerError("BOOTSTRAP_OPEN", phase) from None
    chunks = []
    remaining = maximum + 1
    try:
        opened_stat = os.fstat(descriptor)
        opened_snapshot = _bootstrap_snapshot(opened_stat)
        _need(stat.S_ISREG(opened_stat.st_mode)
              and _bootstrap_handle_identity(opened_snapshot)
                  == _bootstrap_handle_identity(before),
              "BOOTSTRAP_CHANGED", phase)
        while remaining:
            block = os.read(descriptor, min(65536, remaining))
            if not block:
                break
            chunks.append(block)
            remaining -= len(block)
        raw = b"".join(chunks)
        after_handle = _bootstrap_snapshot(os.fstat(descriptor))
    except CallerError:
        raise
    except OSError:
        raise CallerError("BOOTSTRAP_READ", phase) from None
    finally:
        os.close(descriptor)
    try:
        after_stat = os.lstat(path)
    except OSError:
        raise CallerError("BOOTSTRAP_CHANGED", phase) from None
    _need(stat.S_ISREG(after_stat.st_mode)
          and not stat.S_ISLNK(after_stat.st_mode)
          and not (getattr(after_stat, "st_file_attributes", 0) & reparse)
          and _bootstrap_snapshot(after_stat) == before
          and _bootstrap_handle_identity(after_handle)
              == _bootstrap_handle_identity(opened_snapshot)
          and len(raw) <= maximum, "BOOTSTRAP_CHANGED", phase)
    return raw


def _validate_fact_spec(name, spec, maximum_limit, phase="config"):
    _need(type(name) is str and re.fullmatch(r"[A-Za-z][A-Za-z0-9_]{0,63}", name),
          "SPEC_NAME", phase)
    _exact_keys(spec, SPEC_KEYS, "SPEC_KEYS", phase)
    _path_text(spec["path"], "SPEC_PATH")
    _need(_is_int(spec["expectedBytes"], 1, maximum_limit), "SPEC_EXPECTED_BYTES", phase)
    _need(_is_int(spec["maxBytes"], spec["expectedBytes"], maximum_limit),
          "SPEC_MAX_BYTES", phase)
    _need(_is_sha(spec["sha256"]), "SPEC_SHA256", phase)


def _validate_config(config, *, current_file=None):
    _exact_keys(config, CONFIG_KEYS, "CONFIG_KEYS", "config")
    _need(config["schema"] == CONFIG_SCHEMA, "CONFIG_SCHEMA", "config")
    source_specs = config["sourceSpecs"]
    tool_specs = config["toolSpecs"]
    _exact_keys(source_specs, SOURCE_NAMES, "SOURCE_SPEC_NAMES", "config")
    _exact_keys(tool_specs, TOOL_NAMES, "TOOL_SPEC_NAMES", "config")
    for name in SOURCE_NAMES:
        limit = MAX_SOURCE_BYTES
        if name == "allowlist":
            limit = 1024 * 1024
        _validate_fact_spec(name, source_specs[name], limit)
        pin = SOURCE_PINS.get(name)
        if pin is not None:
            _need(source_specs[name]["sha256"] == pin, "SOURCE_PIN_" + name.upper(), "config")
    for name in TOOL_NAMES:
        _validate_fact_spec(name, tool_specs[name], MAX_READER_TOOL_BYTES)
        fixed = TOOL_FIXED_PINS.get(name)
        if fixed is not None:
            _need((tool_specs[name]["expectedBytes"], tool_specs[name]["sha256"]) == fixed,
                  "TOOL_PIN_" + name.upper(), "config")
    roots = config["roots"]
    _exact_keys(roots, ("N", "D"), "ROOT_NAMES", "config")
    for name in ("N", "D"):
        _path_text(roots[name], "ROOT_" + name + "_PATH")
    scratch = _path_text(config["scratchRoot"], "SCRATCH_ROOT")
    output = _path_text(config["dataOutputRoot"], "DATA_OUTPUT_ROOT")
    allowlist_path = _path_text(config["allowlistPath"], "ALLOWLIST_PATH")
    _need(_norm_path(allowlist_path) == _norm_path(source_specs["allowlist"]["path"]),
          "ALLOWLIST_SOURCE_PATH_BINDING", "config")
    _need(source_specs["allowlist"]["sha256"] == ALLOWLIST_SHA256,
          "ALLOWLIST_SOURCE_SHA256", "config")
    _need(_norm_path(roots["N"]) != _norm_path(roots["D"]), "ROOTS_DISTINCT", "config")
    _need(_norm_path(current_file or __file__)
          == _norm_path(source_specs["caller"]["path"]),
          "CALLER_SELF_PATH", "config")
    return config


def _reader_call(reader, name, *args, phase, **kwargs):
    function = getattr(reader, name, None)
    _need(callable(function), "READER_API", phase)
    try:
        return function(*args, **kwargs)
    except CallerError:
        raise
    except Exception as exc:
        code = getattr(exc, "code", None)
        if type(code) is str and re.fullmatch(r"[A-Z0-9_]{1,64}", code):
            raise CallerError("READER_" + code, phase) from None
        raise CallerError("READER_UNEXPECTED", phase) from None


def _read_with_reader(reader, path, maximum, expected_bytes, digest, role, phase):
    _reader_call(reader, "_check_windows_local_lexical_path", path, phase=phase)
    result = _reader_call(
        reader, "_read_file_once", path, maximum, expected_bytes, digest,
        role, phase=phase)
    _need(type(result) is tuple and len(result) == 3, "READER_RESULT_SHAPE", phase)
    raw, helper_sha, token = result
    _need(type(raw) is bytes and type(token) is tuple and _sha(raw) == digest
          and helper_sha == digest and (expected_bytes is None or len(raw) == expected_bytes)
          and len(raw) <= maximum, "READER_RESULT_BINDING", phase)
    return raw, token


def _execute_authenticated_source(name, path, raw, module_name, phase):
    _need(type(raw) is bytes and bool(raw), "SOURCE_MODULE_BYTES", phase)
    try:
        code = compile(raw, path, "exec", dont_inherit=True)
        module = types.ModuleType(module_name)
        module.__file__ = path
        module.__package__ = ""
        sys.modules[module_name] = module
        exec(code, module.__dict__)
    except Exception:
        sys.modules.pop(module_name, None)
        raise CallerError("SOURCE_MODULE_LOAD_" + name.upper(), phase) from None
    return module


def _assert_module_functions(module, names, phase):
    _need(type(module) is types.ModuleType, "MODULE_TYPE", phase)
    bound = {}
    for name in names:
        function = getattr(module, name, None)
        _need(type(function) is types.FunctionType
              and function.__module__ == module.__name__
              and function.__globals__ is module.__dict__
              and function.__name__ == name,
              "MODULE_FUNCTION_BINDING_" + name.upper(), phase)
        bound[name] = function
    return bound


def _root_bindings(reader, roots, scratch_root=None, data_output_root=None):
    identities = {}
    paths = dict(roots)
    if scratch_root is not None:
        paths["scratch"] = scratch_root
    if data_output_root is not None:
        paths["dataOutputRoot"] = data_output_root
    binding_names = ("N", "D")
    if scratch_root is not None:
        binding_names += ("scratch",)
    if data_output_root is not None:
        binding_names += ("dataOutputRoot",)
    for name in binding_names:
        lexical, real, identity = _reader_call(
            reader, "_inspect_path", paths[name], "directory", phase="roots")
        _need(type(identity) is tuple and bool(identity), "ROOT_IDENTITY", "roots")
        leaf = identity[-1]
        _need(type(leaf) is tuple and len(leaf) == 2 and type(leaf[1]) is tuple
              and len(leaf[1]) == 7 and all(type(item) is int for item in leaf[1]),
              "ROOT_LEAF_IDENTITY", "roots")
        snapshot = leaf[1]
        _need(snapshot[0] >= 0 and snapshot[1] > 0
              and stat.S_ISDIR(snapshot[2]), "ROOT_STABLE_FILE_ID", "roots")
        canonical_path = os.path.normcase(os.path.normpath(os.path.abspath(str(real))))
        path_sha = _sha(canonical_path.encode("utf-8", errors="strict"))
        identities[name] = {
            "pathSha256": path_sha,
            "device": snapshot[0],
            "fileId": snapshot[1],
            "mode": snapshot[2],
            "attributes": snapshot[6],
        }
    _need(identities["N"]["pathSha256"] != identities["D"]["pathSha256"],
          "ROOTS_CANONICAL_DISTINCT", "roots")
    bindings = {"schema": ROOT_BINDINGS_SCHEMA, "roots": identities}
    return bindings, _sha(_canonical_json(bindings))


def _check_directories(reader, config):
    root_results = {}
    for label in ("N", "D"):
        root_results[label] = _reader_call(
            reader, "_inspect_path", config["roots"][label], "directory", phase="roots")
    scratch = _reader_call(
        reader, "_inspect_path", config["scratchRoot"], "directory", phase="roots")
    output = _reader_call(
        reader, "_inspect_path", config["dataOutputRoot"], "directory", phase="roots")
    try:
        scratch_real = os.path.normcase(os.path.abspath(str(scratch[1])))
        output_real = os.path.normcase(os.path.abspath(str(output[1])))
        common = os.path.normcase(os.path.commonpath((scratch_real, output_real)))
    except (OSError, ValueError):
        raise CallerError("DATA_OUTPUT_CONTAINMENT", "roots") from None
    _need(common == scratch_real and output_real != scratch_real,
          "DATA_OUTPUT_CONTAINMENT", "roots")
    return root_results, scratch, output


def _verify_root_bindings(reader, config, expected_roots_sha, phase):
    _bindings, actual_roots_sha = _root_bindings(
        reader, config["roots"], config["scratchRoot"], config["dataOutputRoot"])
    _check_directories(reader, config)
    _need(actual_roots_sha == expected_roots_sha,
          "ROOTS_CHANGED", phase)
    return actual_roots_sha


def _directory_empty(path, phase):
    try:
        with os.scandir(path) as entries:
            first = next(entries, None)
    except OSError:
        raise CallerError("OUTPUT_DIRECTORY_READ", phase) from None
    _need(first is None, "DATA_OUTPUT_NOT_EMPTY", phase)


def _validate_kernel_provenance(raw, kernel_sha):
    proof = _strict_json(raw, MAX_PROVENANCE_BYTES, "KERNEL_PROVENANCE")
    _exact_keys(proof, ("kernelSha256", "modules", "schema"), "KERNEL_PROVENANCE_KEYS")
    _need(proof["schema"] == "tsj-prior-kernel-extraction-v1"
          and proof["kernelSha256"] == kernel_sha
          and type(proof["modules"]) is list and len(proof["modules"]) == 2,
          "KERNEL_PROVENANCE_BINDING", "provenance")
    labels = []
    source_shas = []
    for row in proof["modules"]:
        _need(type(row) is dict and type(row.get("label")) is str
              and _is_sha(row.get("sourceSha256")), "KERNEL_PROVENANCE_MODULE", "provenance")
        labels.append(row["label"])
        source_shas.append(row["sourceSha256"])
    _need(labels == ["N", "P07"]
          and source_shas == [N_PRIOR_SOURCE_SHA256, P07_SOURCE_SHA256],
          "KERNEL_PROVENANCE_SOURCE_BINDING", "provenance")
    return proof


def _pathless_facts(facts, names, code):
    _need(type(facts) is dict and set(facts) == set(names), code + "_NAMES", "facts")
    rows = []
    for name in names:
        fact = facts[name]
        _exact_keys(fact, ("path", "bytes", "sha256"), code + "_ROW", "facts")
        _need(type(fact["path"]) is str and _is_int(fact["bytes"], 1)
              and _is_sha(fact["sha256"]), code + "_FACT_FIELDS", "facts")
        rows.append({"name": name, "bytes": fact["bytes"], "sha256": fact["sha256"]})
    return rows


def _source_manifest(config_sha, allowlist_sha, kernel_provenance_sha,
                     roots_sha, byte_observation):
    source_rows = _pathless_facts(byte_observation["sourceFacts"], SOURCE_NAMES, "SOURCE_FACTS")
    tool_rows = _pathless_facts(byte_observation["toolFacts"], TOOL_NAMES, "TOOL_FACTS")
    link = byte_observation.get("harnessModuleLinkEvidence")
    _need(type(link) is dict and type(link.get("evidenceSha256")) is str
          and _is_sha(link["evidenceSha256"]), "HARNESS_MODULE_LINK_EVIDENCE", "facts")
    byte_evidence = {
        "schema": "tsj-normal-prior-source-observation-v1",
        "sourceFacts": source_rows,
        "toolFacts": tool_rows,
        "harnessModuleLinkEvidenceSha256": link["evidenceSha256"],
    }
    return {
        "schema": SOURCE_MANIFEST_SCHEMA,
        "purpose": SOURCE_MANIFEST_PURPOSE,
        "configSha256": config_sha,
        "allowlistSha256": allowlist_sha,
        "kernelProvenanceSha256": kernel_provenance_sha,
        "rootsSha256": roots_sha,
        "sourceFacts": source_rows,
        "toolFacts": tool_rows,
        "harnessModuleLinkEvidenceSha256": link["evidenceSha256"],
        "byteObservationEvidenceSha256": _sha(_canonical_json(byte_evidence)),
    }


def _source_manifest_bytes(manifest):
    _validate_source_manifest_shape(manifest)
    raw = _canonical_json(manifest, newline=True)
    _need(len(raw) <= MAX_MANIFEST_BYTES, "SOURCE_MANIFEST_SIZE", "manifest")
    return raw


def _validate_source_manifest_shape(value):
    _exact_keys(value, (
        "schema", "purpose", "configSha256", "allowlistSha256",
        "kernelProvenanceSha256", "rootsSha256", "sourceFacts", "toolFacts",
        "harnessModuleLinkEvidenceSha256", "byteObservationEvidenceSha256",
    ), "SOURCE_MANIFEST_KEYS", "manifest")
    _need(value["schema"] == SOURCE_MANIFEST_SCHEMA
          and value["purpose"] == SOURCE_MANIFEST_PURPOSE,
          "SOURCE_MANIFEST_SCHEMA", "manifest")
    for field in ("configSha256", "allowlistSha256", "kernelProvenanceSha256",
                  "rootsSha256", "harnessModuleLinkEvidenceSha256",
                  "byteObservationEvidenceSha256"):
        _need(_is_sha(value[field]), "SOURCE_MANIFEST_" + field.upper(), "manifest")
    for field, names in (("sourceFacts", SOURCE_NAMES), ("toolFacts", TOOL_NAMES)):
        rows = value[field]
        _need(type(rows) is list and len(rows) == len(names),
              "SOURCE_MANIFEST_" + field.upper() + "_COUNT", "manifest")
        for name, row in zip(names, rows):
            _exact_keys(row, ("name", "bytes", "sha256"),
                        "SOURCE_MANIFEST_" + field.upper() + "_ROW", "manifest")
            _need(row["name"] == name and _is_int(row["bytes"], 1)
                  and _is_sha(row["sha256"]),
                  "SOURCE_MANIFEST_" + field.upper() + "_BINDING", "manifest")


def _source_module_state(reader, observer, config, byte_observation, phase):
    source_specs = config["sourceSpecs"]
    source_raw = {}
    for name in ("normalizer", "codec", "kernel", "provenance", "allowlist"):
        spec = source_specs[name]
        raw, _token = _read_with_reader(
            reader, spec["path"], spec["maxBytes"], spec["expectedBytes"],
            spec["sha256"], "CALLER_SOURCE_" + name.upper(), phase)
        source_raw[name] = raw
    observed_sources = byte_observation["sourceFacts"]
    for name, raw in source_raw.items():
        fact = observed_sources[name]
        _exact_keys(fact, ("path", "bytes", "sha256"),
                    "LOADED_SOURCE_FACT_FIELDS", phase)
        _need(type(fact["path"]) is str and _is_int(fact["bytes"], 1)
              and _is_sha(fact["sha256"])
              and fact["bytes"] == len(raw) and fact["sha256"] == _sha(raw)
              and _norm_path(fact["path"]) == _norm_path(source_specs[name]["path"]),
              "LOADED_SOURCE_OBSERVATION_BINDING_" + name.upper(), phase)
    kernel_module = _execute_authenticated_source(
        "kernel", source_specs["kernel"]["path"], source_raw["kernel"],
        "_tsj_authenticated_prior_kernel", phase)
    normalizer = _execute_authenticated_source(
        "normalizer", source_specs["normalizer"]["path"], source_raw["normalizer"],
        "_tsj_authenticated_prior_normalizer", phase)
    codec = _execute_authenticated_source(
        "codec", source_specs["codec"]["path"], source_raw["codec"],
        "_tsj_authenticated_context_codec", phase)
    proof = _validate_kernel_provenance(source_raw["provenance"], KERNEL_SHA256)
    _need(_sha(source_raw["provenance"]) == PROVENANCE_SHA256,
          "KERNEL_PROVENANCE_SHA256", phase)
    _need(_sha(source_raw["allowlist"]) == ALLOWLIST_SHA256,
          "ALLOWLIST_SHA256", phase)
    _allowlist, pins = _compare_allowlist(
        reader, normalizer, source_raw["allowlist"], config["allowlistPath"])
    kernel_callbacks = _assert_module_functions(
        kernel_module, ("stable_cohort_order", "validate_prior_run",
                        "validate_prior_terminal_failure", "result_tree_minimum_units",
                        "validate_operation"), phase)
    codec_callbacks = _assert_module_functions(
        codec, ("validate_context", "canonical_context", "native_projection"), phase)
    _assert_module_functions(codec, ("canonical_json", "generate_input_java"), phase)
    _assert_module_functions(
        normalizer, ("normalize_prior", "_validate_allowlist_bytes", "_json_load"), phase)
    _need(tuple(kernel_callbacks) == tuple(normalizer.KERNEL_CALLBACK_KEYS)
          and tuple(codec_callbacks) == tuple(normalizer.CODEC_CALLBACK_KEYS),
          "CALLBACK_KEY_BINDING", phase)
    for name, function in kernel_callbacks.items():
        _need(function is getattr(kernel_module, name), "KERNEL_CALLBACK_PROVENANCE", phase)
    for name, function in codec_callbacks.items():
        _need(function is getattr(codec, name), "CODEC_CALLBACK_PROVENANCE", phase)
    return {
        "sourceRaw": source_raw,
        "kernel": kernel_module,
        "kernelCallbacks": kernel_callbacks,
        "normalizer": normalizer,
        "codec": codec,
        "codecCallbacks": codec_callbacks,
        "provenance": proof,
        "pins": pins,
        "allowlistBytes": source_raw["allowlist"],
    }


def _load_reader_and_observer(config, *, phase):
    reader_spec = config["sourceSpecs"]["reader"]
    reader_raw = _bootstrap_read(reader_spec["path"], MAX_SOURCE_BYTES, phase)
    _need(len(reader_raw) == reader_spec["expectedBytes"]
          and len(reader_raw) <= reader_spec["maxBytes"]
          and _sha(reader_raw) == READER_SHA256 == reader_spec["sha256"],
          "READER_BOOTSTRAP_SHA256", phase)
    reader = _execute_authenticated_source(
        "reader", reader_spec["path"], reader_raw,
        "_tsj_authenticated_fixed_role_reader", phase)
    _assert_module_functions(
        reader, ("_check_windows_local_lexical_path", "_inspect_path",
                 "_read_file_once", "_load_pinned_allowlist", "read_normal_prior",
                 "_token_identity"), phase)
    verified_reader, _token = _read_with_reader(
        reader, reader_spec["path"], reader_spec["maxBytes"],
        reader_spec["expectedBytes"], READER_SHA256,
        "CALLER_SOURCE_READER", phase)
    _need(verified_reader == reader_raw, "READER_BOOTSTRAP_RECHECK", phase)
    observer_spec = config["sourceSpecs"]["observer"]
    observer_raw, _observer_token = _read_with_reader(
        reader, observer_spec["path"], observer_spec["maxBytes"],
        observer_spec["expectedBytes"], OBSERVER_SHA256,
        "CALLER_SOURCE_OBSERVER", phase)
    observer = _execute_authenticated_source(
        "observer", observer_spec["path"], observer_raw,
        "_tsj_authenticated_caller_observer", phase)
    _assert_module_functions(observer, ("observe_byte_facts", "observe_resource_facts"), phase)
    return reader, observer, reader_raw, observer_raw


def _compare_allowlist(reader, normalizer, allowlist_raw, allowlist_path):
    try:
        value, pins = normalizer._validate_allowlist_bytes(allowlist_raw)
    except Exception as exc:
        code = getattr(exc, "code", None)
        if type(code) is str and re.fullmatch(r"[A-Z0-9_]{1,96}", code):
            raise CallerError("NORMALIZER_ALLOWLIST_" + code, "allowlist") from None
        raise CallerError("NORMALIZER_ALLOWLIST_REJECT", "allowlist") from None
    try:
        reader_specs, evidence = reader._load_pinned_allowlist(allowlist_path)
    except Exception as exc:
        code = getattr(exc, "code", None)
        if type(code) is str and re.fullmatch(r"[A-Z0-9_]{1,64}", code):
            raise CallerError("READER_ALLOWLIST_" + code, "allowlist") from None
        raise CallerError("READER_ALLOWLIST_REJECT", "allowlist") from None
    _need(type(reader_specs) in (tuple, list) and len(reader_specs) == 20
          and type(evidence) is dict and evidence.get("roleCount") == 20
          and evidence.get("allowlistSha256") == ALLOWLIST_SHA256,
          "READER_ALLOWLIST_EVIDENCE", "allowlist")
    reader_rows = {}
    for item in reader_specs:
        row = {"role": item.role, "root": item.root, "path": item.relative_path,
               "maxBytes": item.max_bytes, "expectedBytes": item.expected_bytes,
               "sha256": item.sha256}
        if item.exact_content_authority is not None:
            row["exactContentAuthority"] = item.exact_content_authority
            row["summaryParseMaxBytes"] = item.summary_parse_max_bytes
        reader_rows[item.role] = row
    _need(type(pins) is dict and set(reader_rows) == set(pins)
          and all(reader_rows[role] == pins[role] for role in pins),
          "ALLOWLIST_READER_NORMALIZER_DISAGREEMENT", "allowlist")
    _need(value["roleCount"] == 20 and len(pins) == 20,
          "ALLOWLIST_ROLE_COUNT", "allowlist")
    return value, pins


def _observe_source_tools(observer, reader, config):
    try:
        result = observer.observe_byte_facts(
            reader=reader, tool_specs=config["toolSpecs"],
            source_specs=config["sourceSpecs"], include_harness_module_link=True)
    except Exception as exc:
        code = getattr(exc, "code", None)
        if type(code) is str and re.fullmatch(r"[A-Z0-9_]{1,96}", code):
            raise CallerError("SOURCE_TOOL_OBSERVATION_" + code, "source-tool") from None
        raise CallerError("SOURCE_TOOL_OBSERVATION", "source-tool") from None
    _exact_keys(result, ("toolFacts", "sourceFacts", "harnessModuleLinkEvidence"),
                "SOURCE_TOOL_OBSERVATION_RESULT", "source-tool")
    for category, specs, names in (
            ("sourceFacts", config["sourceSpecs"], SOURCE_NAMES),
            ("toolFacts", config["toolSpecs"], TOOL_NAMES)):
        facts = result[category]
        _need(type(facts) is dict and set(facts) == set(names),
              "SOURCE_TOOL_FACT_NAMES_" + category.upper(), "source-tool")
        for name in names:
            fact = facts[name]
            _exact_keys(fact, ("path", "bytes", "sha256"),
                        "SOURCE_TOOL_FACT_ROW", "source-tool")
            spec = specs[name]
            _need(type(fact["path"]) is str and _is_int(fact["bytes"], 1)
                  and _is_sha(fact["sha256"])
                  and _norm_path(fact["path"]) == _norm_path(spec["path"])
                  and fact["bytes"] == spec["expectedBytes"]
                  and fact["sha256"] == spec["sha256"],
                  "SOURCE_TOOL_FACT_BINDING_" + name.upper(), "source-tool")
    link = result["harnessModuleLinkEvidence"]
    _exact_keys(link, ("record", "evidenceSha256"),
                "HARNESS_MODULE_LINK_FIELDS", "source-tool")
    record = link["record"]
    _exact_keys(record, (
        "schema", "harnessPath", "harnessBytes", "harnessSha256",
        "processModulePath", "processModuleBytes", "processModuleSha256",
        "pathBytesSha256", "occurrences", "embedded",
    ), "HARNESS_MODULE_LINK_RECORD", "source-tool")
    _need(_is_sha(link["evidenceSha256"])
          and _is_sha(record["harnessSha256"])
          and _is_sha(record["processModuleSha256"])
          and type(record["harnessPath"]) is str
          and type(record["processModulePath"]) is str
          and _is_int(record["harnessBytes"], 1)
          and _is_int(record["processModuleBytes"], 1)
          and _sha(_canonical_json(record)) == link["evidenceSha256"]
          and link["record"].get("embedded") is True
          and link["record"].get("harnessPath")
              == result["toolFacts"]["rootCallHarness"]["path"]
          and link["record"].get("harnessBytes")
              == result["toolFacts"]["rootCallHarness"]["bytes"]
          and link["record"].get("harnessSha256")
              == result["toolFacts"]["rootCallHarness"]["sha256"]
          and link["record"].get("processModulePath")
              == result["toolFacts"]["processModule"]["path"]
          and link["record"].get("processModuleBytes")
              == result["toolFacts"]["processModule"]["bytes"]
          and link["record"].get("processModuleSha256")
              == result["toolFacts"]["processModule"]["sha256"]
          and _is_sha(link["record"].get("pathBytesSha256"))
          and _is_int(link["record"].get("occurrences"), 1)
          and link["record"].get("schema") == "tsj-caller-harness-module-link-v1",
          "HARNESS_MODULE_LINK_BINDING", "source-tool")
    return result


def _validate_current_modules(config, reader, observer, byte_facts):
    state = _source_module_state(reader, observer, config, byte_facts, "module-load")
    return state


def _preflight_state(config_raw, config_path, config_sha, config, reader, observer,
                     initial_config_token):
    _need(_sha(config_raw) == config_sha, "CONFIG_SHA256", "config")
    verified_config, config_token = _read_with_reader(
        reader, config_path, MAX_CONFIG_BYTES, len(config_raw), config_sha,
        "CALLER_CONFIG", "config")
    _need(verified_config == config_raw, "CONFIG_RECHECK", "config")
    _need(_reader_token_identity(reader, config_token, "CONFIG", "config")
          == _reader_token_identity(reader, initial_config_token, "CONFIG", "config"),
          "CONFIG_IDENTITY_CHANGED", "config")
    roots, _roots_sha = _root_bindings(
        reader, config["roots"], config["scratchRoot"], config["dataOutputRoot"])
    _check_directories(reader, config)
    first_observation = _observe_source_tools(observer, reader, config)
    module_state = _validate_current_modules(config, reader, observer, first_observation)
    allowlist_value, pins = _compare_allowlist(
        reader, module_state["normalizer"], module_state["allowlistBytes"],
        config["allowlistPath"])
    _need(allowlist_value["schema"] == "tsj-full-d-v2-normal-oracle-allowlist-v1"
          and allowlist_value["status"] == "FROZEN_PINS_UNREAD_READER_GATES_REQUIRED",
          "ALLOWLIST_PUBLIC_CONTRACT", "allowlist")
    second_observation = _observe_source_tools(observer, reader, config)
    _need(second_observation == first_observation, "SOURCE_TOOL_CHANGED_DURING_PREFLIGHT", "preflight")
    _need(set(pins) == set(module_state["normalizer"].EXPECTED_ROLE_NAMES),
          "NORMALIZER_ALLOWLIST_ROLE_API", "allowlist")
    source_manifest = _source_manifest(
        config_sha, ALLOWLIST_SHA256, PROVENANCE_SHA256, _roots_sha, first_observation)
    manifest_raw = _source_manifest_bytes(source_manifest)
    return {
        "reader": reader,
        "observer": observer,
        "modules": module_state,
        "byteObservation": first_observation,
        "sourceManifest": source_manifest,
        "sourceManifestBytes": manifest_raw,
        "sourceManifestSha256": _sha(manifest_raw),
        "configToken": config_token,
        "rootBindings": roots,
        "rootsSha256": _roots_sha,
    }


def _read_config_and_runtime(config_path, config_sha, phase):
    _need(_is_sha(config_sha), "CONFIG_SHA256_FORMAT", phase)
    config_raw = _bootstrap_read(config_path, MAX_CONFIG_BYTES, phase)
    _need(_sha(config_raw) == config_sha, "CONFIG_SHA256", phase)
    config = _strict_json(config_raw, MAX_CONFIG_BYTES, "CONFIG", require_object=True)
    _exact_keys(config, CONFIG_KEYS, "CONFIG_KEYS", "config")
    _need(config["schema"] == CONFIG_SCHEMA, "CONFIG_SCHEMA", "config")
    source_specs = config.get("sourceSpecs")
    _need(type(source_specs) is dict and set(source_specs) == set(SOURCE_NAMES),
          "SOURCE_SPEC_NAMES", "config")
    _validate_fact_spec("reader", source_specs["reader"], MAX_SOURCE_BYTES)
    _need(source_specs["reader"]["sha256"] == READER_SHA256,
          "READER_SOURCE_PIN", "config")
    reader, observer, _reader_raw, _observer_raw = _load_reader_and_observer(config, phase=phase)
    verified_config, config_token = _read_with_reader(
        reader, config_path, MAX_CONFIG_BYTES, len(config_raw), config_sha,
        "CALLER_CONFIG", phase)
    _need(verified_config == config_raw, "CONFIG_RECHECK", phase)
    _validate_config(config, current_file=__file__)
    return config_raw, config, reader, observer, config_token


def _readback_output(reader, config, path, expected_raw, maximum,
                     expected_roots_sha, phase):
    _need(type(expected_raw) is bytes and 0 < len(expected_raw) <= maximum,
          "OUTPUT_READBACK_EXPECTATION", phase)
    _verify_root_bindings(reader, config, expected_roots_sha, phase + "-readback-before")
    raw, token = _read_with_reader(
        reader, path, maximum, len(expected_raw), _sha(expected_raw),
        "CALLER_OUTPUT_READBACK", phase)
    _need(raw == expected_raw, "OUTPUT_READBACK_BYTES", phase)
    _verify_root_bindings(reader, config, expected_roots_sha, phase + "-readback-after")
    _need(type(token) is tuple and len(token) == 4
          and type(token[2]) is tuple and type(token[3]) is tuple
          and token[2] == token[3],
          "OUTPUT_READBACK_TOKEN", phase)
    return token


def _write_exclusive(reader, config, path, raw, maximum, containment_root,
                     expected_roots_sha, phase, *, with_readback_token=False):
    _need(type(with_readback_token) is bool, "OUTPUT_TOKEN_OPTION", phase)
    _need(type(raw) is bytes and 0 < len(raw) <= maximum, "OUTPUT_SIZE", phase)
    _need(_is_sha(expected_roots_sha), "OUTPUT_ROOT_BINDING_SHA", phase)
    _verify_root_bindings(reader, config, expected_roots_sha, phase + "-before-write")
    _reader_call(reader, "_check_windows_local_lexical_path", path, phase=phase)
    parent = ntpath.dirname(path)
    _reader_call(reader, "_inspect_path", parent, "directory", phase=phase)
    if containment_root is not None:
        root_result = _reader_call(reader, "_inspect_path", containment_root,
                                   "directory", phase=phase)
        parent_result = _reader_call(reader, "_inspect_path", parent,
                                     "directory", phase=phase)
        try:
            root_real = os.path.normcase(os.path.abspath(str(root_result[1])))
            parent_real = os.path.normcase(os.path.abspath(str(parent_result[1])))
            common = os.path.normcase(os.path.commonpath((root_real, parent_real)))
        except (OSError, ValueError):
            raise CallerError("OUTPUT_CONTAINMENT", phase) from None
        _need(common == root_real, "OUTPUT_CONTAINMENT", phase)
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_BINARY", 0)
    try:
        descriptor = os.open(path, flags, 0o600)
    except OSError:
        raise CallerError("EXCLUSIVE_OUTPUT_CREATE", phase) from None
    opened_snapshot = None
    written_snapshot = None
    try:
        try:
            opened_snapshot = _bootstrap_snapshot(os.fstat(descriptor))
        except OSError:
            raise CallerError("OUTPUT_HANDLE_IDENTITY", phase) from None
        _need(stat.S_ISREG(opened_snapshot[2]) and opened_snapshot[3] == 0,
              "OUTPUT_NEW_HANDLE", phase)
        _verify_root_bindings(reader, config, expected_roots_sha,
                              phase + "-before-content")
        view = memoryview(raw)
        offset = 0
        while offset < len(raw):
            written = os.write(descriptor, view[offset:offset + 65536])
            _need(type(written) is int and written > 0, "OUTPUT_WRITE", phase)
            offset += written
        try:
            os.fsync(descriptor)
            written_snapshot = _bootstrap_snapshot(os.fstat(descriptor))
        except OSError:
            raise CallerError("OUTPUT_FLUSH_OR_IDENTITY", phase) from None
        _need(stat.S_ISREG(written_snapshot[2])
              and written_snapshot[3] == len(raw)
              and _bootstrap_handle_anchor(opened_snapshot)
                  == _bootstrap_handle_anchor(written_snapshot),
              "OUTPUT_HANDLE_CHANGED", phase)
    except CallerError:
        raise
    except OSError:
        raise CallerError("OUTPUT_WRITE", phase) from None
    finally:
        os.close(descriptor)
    _verify_root_bindings(reader, config, expected_roots_sha, phase + "-after-write")
    token = _readback_output(reader, config, path, raw, maximum,
                             expected_roots_sha, phase)
    expected_handle_identity = _bootstrap_handle_identity(written_snapshot)
    _need(token[2] == expected_handle_identity
          and token[3] == expected_handle_identity,
          "OUTPUT_READBACK_IDENTITY", phase)
    fact = {"bytes": len(raw), "sha256": _sha(raw)}
    if with_readback_token:
        # Preserve the exact readback already matched to the written handle.
        # A later independent read cannot recover this identity after replacement.
        fact["readbackToken"] = token
    return fact


def _write_preflight_manifest(reader, config, manifest_output, raw, roots_sha):
    _path_text(manifest_output, "MANIFEST_OUTPUT")
    _reader_call(reader, "_check_windows_local_lexical_path", manifest_output,
                 phase="manifest-write")
    output_root = _norm_path(config["dataOutputRoot"])
    manifest_path = _norm_path(manifest_output)
    try:
        common = ntpath.commonpath((output_root, manifest_path))
    except ValueError:
        common = None
    _need(common != output_root, "MANIFEST_OUTPUT_OVERLAPS_DATA_ROOT", "manifest-write")
    _write_exclusive(reader, config, manifest_output, raw, MAX_MANIFEST_BYTES,
                     config["scratchRoot"], roots_sha, "manifest-write")


def _recheck_config_input(reader, config_path, config_raw, config_sha,
                          config_token, phase):
    current_raw, current_token = _read_with_reader(
        reader, config_path, MAX_CONFIG_BYTES, len(config_raw), config_sha,
        "CALLER_CONFIG", phase)
    _require_same_input_document(
        reader, "CONFIG", config_raw, config_token, current_raw, current_token, phase)


def _recheck_preflight_inputs(reader, observer, config, config_path,
                              config_raw, config_sha, state):
    _verify_root_bindings(reader, config, state["rootsSha256"],
                          "preflight-final-inputs-before")
    _recheck_config_input(reader, config_path, config_raw, config_sha,
                          state["configToken"], "preflight-final-inputs")
    current_observation = _observe_source_tools(observer, reader, config)
    _need(current_observation == state["byteObservation"],
          "SOURCE_TOOL_CHANGED_AFTER_MANIFEST_WRITE", "preflight-final-inputs")
    _verify_root_bindings(reader, config, state["rootsSha256"],
                          "preflight-final-inputs-after")


def _output_directory_names(reader, config, expected_roots_sha, phase):
    _verify_root_bindings(reader, config, expected_roots_sha, phase + "-before")
    try:
        with os.scandir(config["dataOutputRoot"]) as entries:
            names = [entry.name for entry in entries]
    except OSError:
        raise CallerError("OUTPUT_CLOSURE_READ", phase) from None
    _need(all(type(name) is str for name in names), "OUTPUT_CLOSURE_NAMES_TYPE", phase)
    _verify_root_bindings(reader, config, expected_roots_sha, phase + "-after")
    return tuple(sorted(names))


def _validate_output_closure(reader, config, expected_files,
                             expected_roots_sha, phase="output-closure", *,
                             expected_write_tokens=None, include_source_token=False):
    expected_names = (ATTEMPT_FILE,) + tuple(name for name, _label in ROLE_OUTPUT_NAMES)
    _exact_keys(expected_files, expected_names, "OUTPUT_CLOSURE_EXPECTED_FILES", phase)
    _need(type(include_source_token) is bool, "OUTPUT_CLOSURE_TOKEN_OPTION", phase)
    _need(not include_source_token or expected_write_tokens is not None,
          "OUTPUT_CLOSURE_TOKEN_REQUIRED", phase)
    if expected_write_tokens is not None:
        _exact_keys(expected_write_tokens, expected_names, "OUTPUT_CLOSURE_TOKEN_KEYS", phase)
        for name in expected_names:
            _reader_token_identity(reader, expected_write_tokens[name],
                                   "OUTPUT_CLOSURE", phase)
    names_before = _output_directory_names(reader, config, expected_roots_sha,
                                           phase + "-before-reads")
    _need(names_before == tuple(sorted(expected_names)), "OUTPUT_CLOSURE_NAMES", phase)
    facts = []
    source_token = None
    for name in expected_names:
        raw = expected_files[name]
        _need(type(raw) is bytes and 0 < len(raw) <= MAX_OUTPUT_BYTES,
              "OUTPUT_CLOSURE_FILE_SIZE", phase)
        token = _readback_output(
            reader, config, ntpath.join(config["dataOutputRoot"], name), raw,
            MAX_OUTPUT_BYTES, expected_roots_sha, phase + "-" + name.replace(".", "-"))
        if expected_write_tokens is not None:
            _require_same_input_document(
                reader, "OUTPUT_CLOSURE", raw, expected_write_tokens[name], raw,
                token, phase + "-" + name.replace(".", "-"))
        if name == "current-input.java":
            source_token = token
        facts.append({"name": name, "bytes": len(raw), "sha256": _sha(raw),
                      "identityBound": token[2] == token[3]})
    names_after = _output_directory_names(reader, config, expected_roots_sha,
                                          phase + "-after-reads")
    _need(names_after == tuple(sorted(expected_names)) and names_after == names_before,
          "OUTPUT_CLOSURE_CHANGED", phase)
    result = {"fileCount": len(facts), "files": facts}
    if include_source_token:
        result["producerSourceToken"] = source_token
    return result


def preflight(*, config_path, config_sha, manifest_output):
    """Authenticate current SOURCE/tool bytes and exclusively write the manifest."""
    config_raw, config, reader, observer, initial_config_token = _read_config_and_runtime(
        config_path, config_sha, "preflight")
    state = _preflight_state(config_raw, config_path, config_sha, config, reader,
                             observer, initial_config_token)
    _verify_root_bindings(reader, config, state["rootsSha256"], "preflight-output")
    _directory_empty(config["dataOutputRoot"], "preflight")
    _write_preflight_manifest(reader, config, manifest_output,
                              state["sourceManifestBytes"], state["rootsSha256"])
    _recheck_preflight_inputs(reader, observer, config, config_path,
                              config_raw, config_sha, state)
    _verify_root_bindings(reader, config, state["rootsSha256"], "preflight-output-after")
    return {
        "status": "PASS_SOURCE_PREFLIGHT_ONLY",
        "sourceManifestSha256": state["sourceManifestSha256"],
        "rootBindings": state["rootBindings"],
        "rootsSha256": state["rootsSha256"],
        "sourceCount": len(state["sourceManifest"]["sourceFacts"]),
        "toolCount": len(state["sourceManifest"]["toolFacts"]),
        "roleRead": "NOT_RUN",
    }


def _read_authenticated_document(reader, path, maximum, expected_sha, role, phase):
    _need(_is_sha(expected_sha), role + "_SHA256_FORMAT", phase)
    raw, token = _read_with_reader(reader, path, maximum, None, expected_sha,
                                   role, phase)
    return raw, _strict_json(raw, maximum, role, require_object=True), token


def _require_same_input_document(reader, role, expected_raw, expected_token,
                                 current_raw, current_token, phase):
    _need(current_raw == expected_raw, role + "_BYTES_CHANGED_AFTER_WRITES", phase)
    _need(_reader_token_identity(reader, expected_token, role, phase)
          == _reader_token_identity(reader, current_token, role, phase),
          role + "_IDENTITY_CHANGED_AFTER_WRITES", phase)


def _reader_token_identity(reader, token, role, phase):
    """Use the authenticated reader's shared cross-read token projection."""
    _need(type(token) is tuple and len(token) == 4
          and type(token[2]) is tuple and type(token[3]) is tuple
          and token[2] == token[3], role + "_CONTROL_TOKEN", phase)
    return _reader_call(reader, "_token_identity", token, phase=phase)


def _validate_phase_journal(journal, *, config_sha, manifest_sha, roots_sha,
                            allowlist_sha=ALLOWLIST_SHA256):
    expected = (
        "schema", "status", "sourceManifestSha256", "configSha256", "rootsSha256",
        "allowlistSha256", "readerGateSha256", "normalizerGateSha256",
        "callerGateSha256", "createdUtc",
    )
    _exact_keys(journal, expected, "PHASE_JOURNAL_KEYS", "phase-journal")
    _need(journal["schema"] == PHASE_JOURNAL_SCHEMA
          and journal["status"] == PHASE_JOURNAL_STATUS,
          "PHASE_JOURNAL_STATUS", "phase-journal")
    bindings = {
        "sourceManifestSha256": manifest_sha,
        "configSha256": config_sha,
        "rootsSha256": roots_sha,
        "allowlistSha256": allowlist_sha,
        "readerGateSha256": READER_GATE_SHA256,
        "normalizerGateSha256": NORMALIZER_GATE_SHA256,
    }
    for key, expected_sha in bindings.items():
        _need(_is_sha(journal[key]) and journal[key] == expected_sha,
              "PHASE_JOURNAL_" + key.upper(), "phase-journal")
    _need(_is_sha(journal["callerGateSha256"]),
          "PHASE_JOURNAL_CALLER_GATE", "phase-journal")
    _need(_valid_utc_text(journal["createdUtc"]),
          "PHASE_JOURNAL_CREATED_UTC", "phase-journal")
    return journal


def _read_fixed_roles_after_journal(reader, *, config, journal, config_sha,
                                    manifest_sha, roots_sha, read_started_state):
    _validate_phase_journal(journal, config_sha=config_sha,
                            manifest_sha=manifest_sha, roots_sha=roots_sha)
    _verify_root_bindings(reader, config, roots_sha, "role-read-roots")
    read_function = getattr(reader, "read_normal_prior", None)
    _need(type(read_function) is types.FunctionType
          and read_function.__module__ == reader.__name__
          and read_function.__globals__ is reader.__dict__
          and read_function.__name__ == "read_normal_prior",
          "FIXED_ROLE_READER_API", "role-read")
    read_started_state["value"] = True
    try:
        result = read_function(
            roots=config["roots"], allowlist_path=config["allowlistPath"])
    except Exception as exc:
        code = getattr(exc, "code", None)
        if type(code) is str and re.fullmatch(r"[A-Z0-9_]{1,64}", code):
            raise CallerError("FIXED_ROLE_READ_" + code, "role-read") from None
        raise CallerError("FIXED_ROLE_READ_UNEXPECTED", "role-read") from None
    _verify_root_bindings(reader, config, roots_sha, "role-read-roots-after")
    return result


def _manifest_matches(pathless_manifest, expected_manifest):
    _validate_source_manifest_shape(pathless_manifest)
    _need(pathless_manifest == expected_manifest,
          "SOURCE_MANIFEST_CURRENT_FACTS", "manifest")


def _make_current_source_authority(manifest_sha):
    value = {
        "schema": CURRENT_SOURCE_AUTHORITY_SCHEMA,
        "sourceSha256": manifest_sha,
    }
    value["authoritySha256"] = _sha(_canonical_json(value))
    return value


def _make_normalizer_authority(config, state, resource_facts):
    normalizer = state["modules"]["normalizer"]
    authority = {
        "schema": normalizer.NORMALIZER_AUTHORITY_SCHEMA,
        "authoritySha256": "0" * 64,
        "kernelProvenanceSha256": PROVENANCE_SHA256,
        "nRoot": config["roots"]["N"],
        "dRoot": config["roots"]["D"],
        "currentSourceAuthority": _make_current_source_authority(
            state["sourceManifestSha256"]),
        "sourceBindings": dict(normalizer.SOURCE_BINDING_PINS),
        "toolFacts": state["byteObservation"]["toolFacts"],
        "resourceFacts": resource_facts,
    }
    _freeze_digest(authority, "authoritySha256")
    return authority


def _receipt_absence_specs(normalizer, role_bytes, scratch_root):
    call_roles = {
        "nativePrior": "N_PRIOR_CALL_PROCESS_JSON",
        "qualification": "D_PRIOR_QUALIFICATION_PROCESS_JSON",
    }
    output = {}
    for call_name, role in call_roles.items():
        raw = role_bytes[role]
        process = normalizer._json_load(
            raw, normalizer.MAX_HOST_RECEIPT_BYTES,
            "CALLER_" + call_name.upper() + "_PROCESS", allow_float=True)
        _exact_keys(process, PROCESS_FIELDS, "PROCESS_RECEIPT_FIELDS", "receipt-paths")
        absence = {}
        for field_name, process_key in (("processRoot", "ProcessRoot"),
                                        ("stdoutLog", "StdoutLog"),
                                        ("stderrLog", "StderrLog")):
            path = process[process_key]
            _need(type(path) is str and bool(path),
                  "PROCESS_RECEIPT_PATH", "receipt-paths")
            absence[field_name] = {"path": path, "containmentRoot": scratch_root}
        output[call_name] = absence
    return output


def _resource_specs(config):
    roots = config["roots"]
    return {
        "qualification": {
            "path": ntpath.join(roots["D"], "prior-input-qualify-r2"),
            "containmentRoot": roots["D"],
        },
        "nativePrior": {
            "path": ntpath.join(roots["N"], "native-observe-r3"),
            "containmentRoot": roots["N"],
        },
        "nativeData": {
            "path": ntpath.join(roots["N"], "native-data-r3"),
            "containmentRoot": roots["N"],
        },
    }


def _observe_resources(observer, reader, config, role_bytes, normalizer):
    absence_specs = _receipt_absence_specs(normalizer, role_bytes, config["scratchRoot"])
    try:
        return observer.observe_resource_facts(
            reader=reader, directory_specs=_resource_specs(config),
            absence_specs=absence_specs)
    except Exception as exc:
        code = getattr(exc, "code", None)
        if type(code) is str and re.fullmatch(r"[A-Z0-9_]{1,96}", code):
            raise CallerError("RESOURCE_OBSERVATION_" + code, "resources") from None
        raise CallerError("RESOURCE_OBSERVATION", "resources") from None


def _output_projection_bytes(codec, projection):
    try:
        raw = codec.canonical_json(projection)
    except Exception as exc:
        code = getattr(exc, "code", None)
        if type(code) is str and re.fullmatch(r"[A-Z0-9_]{1,96}", code):
            raise CallerError("PROJECTION_SERIALIZE_" + code, "outputs") from None
        raise CallerError("PROJECTION_SERIALIZE", "outputs") from None
    _need(type(raw) is bytes and 0 < len(raw) <= MAX_OUTPUT_BYTES
          and raw.endswith(b"\n"), "PROJECTION_OUTPUT_SIZE", "outputs")
    return raw


def _attempt_record(config_sha, manifest_sha, journal_sha, roots_sha):
    return {
        "schema": ATTEMPT_SCHEMA,
        "status": "READ_ATTEMPT_RESERVED",
        "configSha256": config_sha,
        "sourceManifestSha256": manifest_sha,
        "phaseJournalSha256": journal_sha,
        "rootsSha256": roots_sha,
        "allowlistSha256": ALLOWLIST_SHA256,
        "readScope": "FIXED_20_NORMAL_PRIOR_ROLES",
    }


def _failure_record(code, phase, config_sha, manifest_sha, journal_sha,
                    roots_sha, read_started):
    return {
        "schema": EVIDENCE_SCHEMA,
        "status": "FAIL",
        "phase": phase,
        "code": code,
        "configSha256": config_sha,
        "sourceManifestSha256": manifest_sha,
        "phaseJournalSha256": journal_sha,
        "rootsSha256": roots_sha,
        "allowlistSha256": ALLOWLIST_SHA256,
        "roleReadStarted": bool(read_started),
        "nativeCalls": "NOT_RUN",
    }


def _write_failure(reader, config, record, expected_roots_sha):
    raw = _canonical_json(record, newline=True)
    _need(len(raw) <= MAX_OUTPUT_BYTES, "FAILURE_EVIDENCE_SIZE", "failure-evidence")
    path = ntpath.join(config["dataOutputRoot"], FAILURE_FILE)
    return _write_exclusive(reader, config, path, raw, MAX_OUTPUT_BYTES,
                            config["scratchRoot"], expected_roots_sha,
                            "failure-evidence")


def _best_effort_failure_evidence(reader, config, record, expected_roots_sha,
                                  original_error):
    try:
        _write_failure(reader, config, record, expected_roots_sha)
    except CallerError as report_error:
        original_error.reporting_code = report_error.code
    except Exception:
        original_error.reporting_code = "FAILURE_EVIDENCE_UNEXPECTED"


def _recheck_consumed_inputs(reader, observer, config, state, *,
                             config_path, config_raw, config_sha,
                             manifest_path, manifest_sha, manifest_raw,
                             manifest_value, manifest_token,
                             journal_path, journal_sha, journal_raw,
                             journal_value, journal_token,
                             role_bytes, reader_evidence,
                             resource_facts, resource_evidence,
                             read_started_state):
    phase = "final-input-recheck"
    roots_sha = state["rootsSha256"]
    _verify_root_bindings(reader, config, roots_sha, phase + "-before")

    current_config_raw, current_config_token = _read_with_reader(
        reader, config_path, MAX_CONFIG_BYTES, len(config_raw), config_sha,
        "CALLER_CONFIG", phase)
    _require_same_input_document(
        reader, "CONFIG", config_raw, state["configToken"],
        current_config_raw, current_config_token, phase)

    current_manifest_raw, current_manifest_value, current_manifest_token = (
        _read_authenticated_document(
            reader, manifest_path, MAX_MANIFEST_BYTES, manifest_sha,
            "SOURCE_MANIFEST", phase))
    _require_same_input_document(
        reader, "SOURCE_MANIFEST", manifest_raw, manifest_token,
        current_manifest_raw, current_manifest_token, phase)
    _need(current_manifest_value == manifest_value == state["sourceManifest"],
          "SOURCE_MANIFEST_CONTENT_CHANGED_AFTER_WRITES", phase)

    current_journal_raw, current_journal_value, current_journal_token = (
        _read_authenticated_document(
            reader, journal_path, MAX_JOURNAL_BYTES, journal_sha,
            "PHASE_JOURNAL", phase))
    _require_same_input_document(
        reader, "PHASE_JOURNAL", journal_raw, journal_token,
        current_journal_raw, current_journal_token, phase)
    _need(current_journal_value == journal_value,
          "PHASE_JOURNAL_CONTENT_CHANGED_AFTER_WRITES", phase)
    _validate_phase_journal(
        current_journal_value, config_sha=config_sha,
        manifest_sha=manifest_sha, roots_sha=roots_sha)

    current_observation = _observe_source_tools(observer, reader, config)
    _need(current_observation == state["byteObservation"],
          "SOURCE_TOOL_CHANGED_AFTER_OUTPUT_WRITES", phase)
    current_resources, current_resource_evidence = _observe_resources(
        observer, reader, config, role_bytes, state["modules"]["normalizer"])
    _need(current_resources == resource_facts
          and current_resource_evidence == resource_evidence,
          "RESOURCES_CHANGED_AFTER_OUTPUT_WRITES", phase)
    current_roles, current_reader_evidence = _read_fixed_roles_after_journal(
        reader, config=config, journal=current_journal_value,
        config_sha=config_sha, manifest_sha=manifest_sha, roots_sha=roots_sha,
        read_started_state=read_started_state)
    _need(current_roles == role_bytes and current_reader_evidence == reader_evidence,
          "ROLE_BYTES_CHANGED_AFTER_OUTPUT_WRITES", phase)
    _verify_root_bindings(reader, config, roots_sha, phase + "-after")


def _qualify_impl(config_path, config_sha, source_manifest_path,
                  source_manifest_sha, phase_journal_path, phase_journal_sha, *,
                  include_source_token=False):
    _need(type(include_source_token) is bool, "OUTPUT_CLOSURE_TOKEN_OPTION", "qualify-options")
    config_raw, config, reader, observer, initial_config_token = _read_config_and_runtime(
        config_path, config_sha, "qualify-preflight")
    state = _preflight_state(config_raw, config_path, config_sha, config, reader,
                             observer, initial_config_token)
    manifest_raw, manifest_value, manifest_token = _read_authenticated_document(
        reader, source_manifest_path, MAX_MANIFEST_BYTES,
        source_manifest_sha, "SOURCE_MANIFEST", "source-manifest")
    _need(_sha(manifest_raw) == source_manifest_sha
          and manifest_raw == state["sourceManifestBytes"],
          "SOURCE_MANIFEST_SHA_OR_CONTENT", "source-manifest")
    _manifest_matches(manifest_value, state["sourceManifest"])
    journal_raw, journal, journal_token = _read_authenticated_document(
        reader, phase_journal_path, MAX_JOURNAL_BYTES,
        phase_journal_sha, "PHASE_JOURNAL", "phase-journal")
    _need(_sha(journal_raw) == phase_journal_sha, "PHASE_JOURNAL_SHA256", "phase-journal")
    _validate_phase_journal(
        journal, config_sha=config_sha, manifest_sha=source_manifest_sha,
        roots_sha=state["rootsSha256"])
    _verify_root_bindings(reader, config, state["rootsSha256"], "pre-read-gate")
    _directory_empty(config["dataOutputRoot"], "pre-read-gate")

    attempt = _canonical_json(_attempt_record(
        config_sha, source_manifest_sha, phase_journal_sha, state["rootsSha256"]), newline=True)
    attempt_write = _write_exclusive(
        reader, config, ntpath.join(config["dataOutputRoot"], ATTEMPT_FILE), attempt,
        MAX_OUTPUT_BYTES, config["scratchRoot"], state["rootsSha256"],
        "read-attempt-marker", with_readback_token=True)
    write_tokens = {ATTEMPT_FILE: attempt_write["readbackToken"]}
    read_started_state = {"value": False}
    phase = "role-read"
    try:
        role_bytes, reader_evidence = _read_fixed_roles_after_journal(
            reader, config=config, journal=journal, config_sha=config_sha,
            manifest_sha=source_manifest_sha, roots_sha=state["rootsSha256"],
            read_started_state=read_started_state)
        _need(type(role_bytes) is dict and set(role_bytes) == set(state["modules"]["pins"])
              and len(role_bytes) == 20, "FIXED_ROLE_MAP", phase)
        _need(type(reader_evidence) is dict
              and reader_evidence.get("scope") == "BYTE_AUTHENTICATION_ONLY"
              and reader_evidence.get("roleCount") == 20
              and reader_evidence.get("finalSha256Recheck") == "PASS",
              "FIXED_ROLE_READER_EVIDENCE", phase)

        phase = "resources"
        resource_facts, resource_evidence = _observe_resources(
            observer, reader, config, role_bytes, state["modules"]["normalizer"])
        phase = "normalization"
        authority = _make_normalizer_authority(config, state, resource_facts)
        try:
            result = state["modules"]["normalizer"].normalize_prior(
                role_bytes, state["modules"]["allowlistBytes"], authority,
                state["modules"]["kernelCallbacks"], state["modules"]["codecCallbacks"])
        except Exception as exc:
            code = getattr(exc, "code", None)
            if type(code) is str and re.fullmatch(r"[A-Z0-9_]{1,96}", code):
                raise CallerError("NORMALIZER_" + code, phase) from None
            raise CallerError("NORMALIZER_REJECT", phase) from None

        phase = "recheck"
        current_observation = _observe_source_tools(observer, reader, config)
        _need(current_observation == state["byteObservation"],
              "SOURCE_TOOL_CHANGED_AFTER_NORMALIZATION", phase)
        current_resources, current_resource_evidence = _observe_resources(
            observer, reader, config, role_bytes, state["modules"]["normalizer"])
        _need(current_resources == resource_facts
              and current_resource_evidence == resource_evidence,
              "RESOURCES_CHANGED_AFTER_NORMALIZATION", phase)
        _verify_root_bindings(reader, config, state["rootsSha256"], phase)
        rechecked_roles, rechecked_evidence = _read_fixed_roles_after_journal(
            reader, config=config, journal=journal, config_sha=config_sha,
            manifest_sha=source_manifest_sha, roots_sha=state["rootsSha256"],
            read_started_state=read_started_state)
        _need(rechecked_roles == role_bytes and rechecked_evidence == reader_evidence,
              "ROLE_BYTES_CHANGED_AFTER_NORMALIZATION", phase)

        phase = "outputs"
        context_raw = result.context_bytes
        try:
            independently_canonical_context = state["modules"]["codec"].canonical_context(
                result.context)
            independently_projected = state["modules"]["codec"].native_projection(
                result.context)
        except Exception as exc:
            code = getattr(exc, "code", None)
            if type(code) is str and re.fullmatch(r"[A-Z0-9_]{1,96}", code):
                raise CallerError("OUTPUT_CONTEXT_VALIDATE_" + code, phase) from None
            raise CallerError("OUTPUT_CONTEXT_VALIDATE", phase) from None
        _need(type(context_raw) is bytes
              and context_raw == independently_canonical_context,
              "CONTEXT_BYTES_NOT_CANONICAL", phase)
        _need(result.native_projection == independently_projected,
              "NATIVE_PROJECTION_MISMATCH", phase)
        projection_raw = _output_projection_bytes(
            state["modules"]["codec"], result.native_projection)
        context_sha = _sha(context_raw)
        java_raw = state["modules"]["codec"].generate_input_java(
            result.context, expected_context_sha256=context_sha)
        _need(type(java_raw) is bytes and 0 < len(java_raw) <= MAX_OUTPUT_BYTES,
              "CURRENT_JAVA_DATA_SIZE", phase)
        _need(type(context_raw) is bytes and 0 < len(context_raw) <= MAX_OUTPUT_BYTES,
              "CONTEXT_DATA_SIZE", phase)
        role_hashes = [
            {"role": role, "bytes": len(role_bytes[role]), "sha256": _sha(role_bytes[role])}
            for role in state["modules"]["normalizer"].EXPECTED_ROLE_NAMES
        ]
        output_facts = {
            "context": {"bytes": len(context_raw), "sha256": _sha(context_raw)},
            "projection": {"bytes": len(projection_raw), "sha256": _sha(projection_raw)},
            "java": {"bytes": len(java_raw), "sha256": _sha(java_raw)},
        }
        evidence = {
            "schema": EVIDENCE_SCHEMA,
            "status": "PRIOR_INPUT_READY_PENDING_FINAL_CHECKS",
            "scope": "FIXED_20_NORMAL_PRIOR_ONLY",
            "configSha256": config_sha,
            "sourceManifestSha256": source_manifest_sha,
            "phaseJournalSha256": phase_journal_sha,
            "rootsSha256": state["rootsSha256"],
            "allowlistSha256": ALLOWLIST_SHA256,
            "kernelProvenanceSha256": PROVENANCE_SHA256,
            "roleCount": len(role_hashes),
            "roleBytesTotal": sum(item["bytes"] for item in role_hashes),
            "roleHashes": role_hashes,
            "resourceEvidenceSha256": {
                name: value["evidenceSha256"] for name, value in resource_evidence["resources"].items()
            },
            "outputFacts": output_facts,
            "finalInputRecheck": "NOT_RUN_AT_EVIDENCE_WRITE",
            "outputClosure": "NOT_RUN_AT_EVIDENCE_WRITE",
            "nativeCalls": "NOT_RUN",
            "originalCalls": "NOT_RUN",
            "playable100": "NOT_CLAIMED",
        }
        evidence_raw = _canonical_json(evidence, newline=True)
        _need(len(evidence_raw) <= MAX_OUTPUT_BYTES, "EVIDENCE_OUTPUT_SIZE", phase)
        outputs = {
            "context.json": context_raw,
            "native-projection.json": projection_raw,
            "current-input.java": java_raw,
            "qualification-evidence.json": evidence_raw,
        }
        for name, raw in outputs.items():
            write_fact = _write_exclusive(
                reader, config, ntpath.join(config["dataOutputRoot"], name),
                raw, MAX_OUTPUT_BYTES, config["scratchRoot"],
                state["rootsSha256"], phase + "-" + name.replace(".", "-"),
                with_readback_token=True)
            write_tokens[name] = write_fact["readbackToken"]
        _recheck_consumed_inputs(
            reader, observer, config, state,
            config_path=config_path, config_raw=config_raw, config_sha=config_sha,
            manifest_path=source_manifest_path, manifest_sha=source_manifest_sha,
            manifest_raw=manifest_raw, manifest_value=manifest_value,
            manifest_token=manifest_token,
            journal_path=phase_journal_path, journal_sha=phase_journal_sha,
            journal_raw=journal_raw, journal_value=journal, journal_token=journal_token,
            role_bytes=role_bytes, reader_evidence=reader_evidence,
            resource_facts=resource_facts, resource_evidence=resource_evidence,
            read_started_state=read_started_state)
        expected_files = {ATTEMPT_FILE: attempt}
        expected_files.update(outputs)
        output_closure = _validate_output_closure(
            reader, config, expected_files, state["rootsSha256"],
            expected_write_tokens=write_tokens, include_source_token=include_source_token)
        _verify_root_bindings(reader, config, state["rootsSha256"], "outputs-after")
        return {
            "status": "PASS_PRIOR_INPUT_ONLY",
            "contextSha256": output_facts["context"]["sha256"],
            "projectionSha256": output_facts["projection"]["sha256"],
            "javaDataSha256": output_facts["java"]["sha256"],
            "roleCount": len(role_hashes),
            "outputClosure": output_closure,
            "finalInputRecheck": "PASS",
            "nativeCalls": "NOT_RUN",
        }
    except CallerError as exc:
        _best_effort_failure_evidence(reader, config, _failure_record(
            exc.code, exc.phase or phase, config_sha, source_manifest_sha,
            phase_journal_sha, state["rootsSha256"], read_started_state["value"]),
            state["rootsSha256"], exc)
        raise
    except Exception:
        original_error = CallerError("UNEXPECTED", phase)
        _best_effort_failure_evidence(reader, config, _failure_record(
            "UNEXPECTED", phase, config_sha, source_manifest_sha,
            phase_journal_sha, state["rootsSha256"], read_started_state["value"]),
            state["rootsSha256"], original_error)
        raise original_error from None


def qualify(*, config_path, config_sha, source_manifest_path,
            source_manifest_sha, phase_journal_path, phase_journal_sha,
            include_source_token=False):
    return _qualify_impl(config_path, config_sha, source_manifest_path,
                         source_manifest_sha, phase_journal_path, phase_journal_sha,
                         include_source_token=include_source_token)


def _parse_cli(argv=None):
    parser = argparse.ArgumentParser(prog="qualify_prior.py")
    commands = parser.add_subparsers(dest="command", required=True)
    pre = commands.add_parser("preflight")
    pre.add_argument("--config", required=True)
    pre.add_argument("--config-sha", required=True)
    pre.add_argument("--manifest-output", required=True)
    run = commands.add_parser("qualify")
    run.add_argument("--config", required=True)
    run.add_argument("--config-sha", required=True)
    run.add_argument("--source-manifest", required=True)
    run.add_argument("--source-manifest-sha", required=True)
    run.add_argument("--phase-journal", required=True)
    run.add_argument("--phase-journal-sha", required=True)
    return parser.parse_args(argv)


def main(argv=None):
    if sys.platform != "win32" or os.name != "nt":
        print("CALLER_WINDOWS_REQUIRED")
        return 2
    if not sys.flags.isolated or not sys.dont_write_bytecode:
        print("CALLER_REQUIRES_I_B")
        return 2
    try:
        args = _parse_cli(argv)
        if args.command == "preflight":
            summary = preflight(config_path=args.config, config_sha=args.config_sha,
                                manifest_output=args.manifest_output)
        else:
            summary = qualify(
                config_path=args.config, config_sha=args.config_sha,
                source_manifest_path=args.source_manifest,
                source_manifest_sha=args.source_manifest_sha,
                phase_journal_path=args.phase_journal,
                phase_journal_sha=args.phase_journal_sha)
        print(_canonical_json(summary).decode("ascii"))
        return 0
    except CallerError as exc:
        print(exc.code)
        if exc.reporting_code is not None:
            print("FAILURE_EVIDENCE_WRITE_" + exc.reporting_code, file=sys.stderr)
        return 2
    except Exception:
        print("CALLER_UNEXPECTED")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
