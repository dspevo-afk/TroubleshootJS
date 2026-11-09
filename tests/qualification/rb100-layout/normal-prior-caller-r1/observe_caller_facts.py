"""Observe explicitly configured caller SOURCE/tool and resource facts.

The caller must pass the exact, source-authenticated fixed-role-reader module.
This module checks the required helper interface but does not authenticate the
module bytes; the caller owns source binding and phase authorization. No paths
are discovered from manifests, receipts, or directory recursion here.
"""

from __future__ import annotations

import errno
import hashlib
import json
import ntpath
import os
import re
import types
from pathlib import Path


MAX_READER_BYTES = 8 * 1024 * 1024
MAX_SOURCE_BYTES = 4 * 1024 * 1024
CALL_DIRECTORY_NAMES = (
    "owner.json", "parent.json", "process.json", "request.json", "result.json",
    "stderr.txt", "stdout.txt",
)
DATA_DIRECTORY_NAMES = ("op-1.jsonl", "result.json")
TOOL_FACT_NAMES = (
    "qualificationPython", "rootCallPowerShell", "nativeJava",
    "rootCallHarness", "processModule",
)
RESOURCE_NAMES = ("qualification", "nativePrior", "nativeData")
CALL_RESOURCE_NAMES = ("qualification", "nativePrior")
ABSENCE_FACT_NAMES = ("processRoot", "stderrLog", "stdoutLog")
_NAME = re.compile(r"[A-Za-z][A-Za-z0-9_]{0,63}\Z")
_SHA256 = re.compile(r"[0-9a-f]{64}\Z")
_READER_FUNCTIONS = (
    "_check_windows_local_lexical_path", "_inspect_path", "_read_file_once",
    "_path_identity", "_token_identity",
)


class CallerObservationError(ValueError):
    """Stable, path-free error from the caller-facts observer."""

    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


def _need(condition: bool, code: str) -> None:
    if not condition:
        raise CallerObservationError(code)


def _exact_keys(value, expected, code: str) -> None:
    _need(type(value) is dict and set(value) == set(expected), code)


def _is_sha(value) -> bool:
    return type(value) is str and _SHA256.fullmatch(value) is not None


def _reader_api(reader):
    _need(type(reader) is types.ModuleType, "READER_MODULE_TYPE")
    api = {}
    for name in _READER_FUNCTIONS:
        function = getattr(reader, name, None)
        _need(callable(function), "READER_API")
        api[name] = function
    reader_error = getattr(reader, "ReaderError", None)
    _need(isinstance(reader_error, type)
          and issubclass(reader_error, Exception), "READER_API")
    api["ReaderError"] = reader_error
    return api


def _reader_call(api, name, *args, error_code):
    try:
        return api[name](*args)
    except Exception as exc:
        if isinstance(exc, api["ReaderError"]):
            reader_code = getattr(exc, "code", None)
            if (type(reader_code) is str and
                    re.fullmatch(r"[A-Z0-9_]{1,64}", reader_code)):
                raise CallerObservationError(error_code + "_" + reader_code) from None
        raise CallerObservationError(error_code + "_UNEXPECTED") from None


def _path_check(api, path: str, code: str):
    _reader_call(api, "_check_windows_local_lexical_path", path,
                 error_code=code)


def _inspect(api, path: str, expected_kind: str, code: str):
    value = _reader_call(api, "_inspect_path", path, expected_kind,
                         error_code=code)
    _need(type(value) is tuple and len(value) == 3, code)
    lexical, real, identity = value
    _need(isinstance(lexical, Path) and isinstance(real, Path)
          and type(identity) is tuple, code)
    return str(lexical), str(real), _project_path_identity(api, identity, code)


def _is_canonical_identity_path(value) -> bool:
    if type(value) is not str or not value:
        return False
    try:
        return value == os.path.normcase(os.path.abspath(value))
    except (OSError, ValueError):
        return False


def _identity_json(identity, code="IDENTITY_SHAPE"):
    _need(type(identity) is tuple and bool(identity), code)
    output = []
    for index, row in enumerate(identity):
        is_leaf = index == len(identity) - 1
        snapshot_size = 7 if is_leaf else 4
        _need(type(row) is tuple and len(row) == 2
              and _is_canonical_identity_path(row[0])
              and type(row[1]) is tuple and len(row[1]) == snapshot_size
              and all(type(value) is int for value in row[1]), code)
        output.append({"path": row[0], "snapshot": list(row[1])})
    return output


def _project_path_identity(api, raw_identity, code):
    projected = _reader_call(api, "_path_identity", raw_identity,
                             error_code=code + "_PATH_IDENTITY")
    _identity_json(projected, code + "_PATH_IDENTITY_SHAPE")
    return projected


def _project_token_identity(api, raw_token, code):
    projected = _reader_call(api, "_token_identity", raw_token,
                             error_code=code + "_TOKEN_IDENTITY")
    _need(type(projected) is tuple and len(projected) == 4
          and _is_canonical_identity_path(projected[0])
          and type(projected[1]) is tuple
          and type(projected[2]) is tuple and len(projected[2]) == 6
          and type(projected[3]) is tuple and len(projected[3]) == 6
          and all(type(value) is int for value in projected[2] + projected[3])
          and projected[2] == projected[3], code + "_TOKEN_IDENTITY_SHAPE")
    _identity_json(projected[1], code + "_TOKEN_PATH_IDENTITY_SHAPE")
    _need(projected[1][-1][0] == projected[0], code + "_TOKEN_PATH_BINDING")
    return projected


def _within_existing_root(root_real: str, child_real: str) -> bool:
    try:
        root_text = os.path.normcase(os.path.abspath(root_real))
        child_text = os.path.normcase(os.path.abspath(child_real))
        common = os.path.normcase(os.path.commonpath((root_text, child_text)))
    except (OSError, ValueError):
        return False
    return common == root_text and child_text != root_text


def _within_lexical_root(root_real: str, child_path: str) -> bool:
    try:
        root_text = ntpath.normcase(ntpath.normpath(root_real))
        child_text = ntpath.normcase(ntpath.normpath(child_path))
        common = ntpath.normcase(ntpath.commonpath((root_text, child_text)))
    except (OSError, ValueError):
        return False
    return common == root_text and child_text != root_text


def _canonical_json_bytes(value) -> bytes:
    try:
        return json.dumps(value, ensure_ascii=True, sort_keys=False,
                          separators=(",", ":"), allow_nan=False).encode("ascii")
    except (TypeError, ValueError, UnicodeError, RecursionError):
        raise CallerObservationError("EVIDENCE_DOMAIN") from None


def _sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _validate_fact_spec(name, spec, *, maximum_limit: int, code: str) -> None:
    _need(type(name) is str and _NAME.fullmatch(name) is not None, code + "_NAME")
    _exact_keys(spec, ("path", "expectedBytes", "maxBytes", "sha256"), code + "_KEYS")
    _need(type(spec["path"]) is str and len(spec["path"]) <= 32767,
          code + "_PATH")
    _need(type(spec["expectedBytes"]) is int and 1 <= spec["expectedBytes"]
          <= maximum_limit, code + "_EXPECTED_BYTES")
    _need(type(spec["maxBytes"]) is int
          and spec["expectedBytes"] <= spec["maxBytes"] <= maximum_limit,
          code + "_LIMIT")
    _need(_is_sha(spec["sha256"]), code + "_SHA")


def _read_fact(api, name, spec, *, category: str):
    path = spec["path"]
    _path_check(api, path, category + "_PATH")
    role = "CALLER_" + category.upper() + "_" + name
    raw, helper_digest, token = _reader_call(
        api, "_read_file_once", path, spec["maxBytes"], spec["expectedBytes"],
        spec["sha256"], role, error_code=category + "_READ")
    _need(type(raw) is bytes and type(token) is tuple, category + "_READ_SHAPE")
    token = _project_token_identity(api, token, category)
    digest = _sha(raw)
    _need(type(helper_digest) is str
          and len(raw) == spec["expectedBytes"] and len(raw) <= spec["maxBytes"]
          and digest == spec["sha256"] and helper_digest == digest,
          category + "_CONTENT_BINDING")
    return ({"path": path, "bytes": len(raw), "sha256": digest}, token, raw)


def _read_fact_map(api, specs, *, category: str, maximum_limit: int,
                   required_names=None):
    _need(type(specs) is dict and bool(specs), category + "_SPECS")
    if required_names is not None:
        _need(set(specs) == set(required_names), category + "_NAMES")
        names = required_names
    else:
        _need(all(type(name) is str and _NAME.fullmatch(name) is not None
                  for name in specs), category + "_NAMES")
        names = tuple(sorted(specs))
    facts, tokens, retained = {}, {}, {}
    for name in names:
        spec = specs[name]
        _validate_fact_spec(name, spec, maximum_limit=maximum_limit,
                            code=category + "_SPEC")
        fact, token, raw = _read_fact(api, name, spec, category=category)
        facts[name] = fact
        tokens[name] = (fact["bytes"], fact["sha256"], token)
        if name == "rootCallHarness":
            retained[name] = raw
    return facts, tokens, retained


def _recheck_fact_map(api, specs, initial_tokens, *, category: str,
                      maximum_limit: int, names):
    for name in names:
        fact, token, _raw = _read_fact(api, name, specs[name], category=category)
        expected_bytes, expected_sha, expected_token = initial_tokens[name]
        _need(fact["bytes"] == expected_bytes and fact["sha256"] == expected_sha
              and token == expected_token, category + "_CHANGED")


def observe_byte_facts(*, reader, tool_specs, source_specs,
                       include_harness_module_link: bool = True):
    """Read only explicitly named source/tool files and return actual byte facts.

    ``reader`` must be the module instance whose source bytes the trusted caller
    has already authenticated. Tool specs must have the exact five normalizer
    tool names; source specs are explicit SOURCE manifest inputs. This function
    never obtains paths from a manifest row and never returns file bodies.
    These reads are individually checked and rechecked, not one atomic snapshot;
    the caller must recheck all consumed inputs after normalization.
    """
    api = _reader_api(reader)
    _need(type(include_harness_module_link) is bool, "HARNESS_LINK_FLAG")
    tool_facts, tool_tokens, retained = _read_fact_map(
        api, tool_specs, category="TOOL", maximum_limit=MAX_READER_BYTES,
        required_names=TOOL_FACT_NAMES)
    source_facts, source_tokens, _ = _read_fact_map(
        api, source_specs, category="SOURCE", maximum_limit=MAX_SOURCE_BYTES)

    link_evidence = None
    if include_harness_module_link:
        harness = tool_facts["rootCallHarness"]
        module = tool_facts["processModule"]
        _path_check(api, module["path"], "PROCESS_MODULE_PATH")
        try:
            needle = module["path"].encode("utf-8", errors="strict")
        except UnicodeEncodeError:
            raise CallerObservationError("PROCESS_MODULE_PATH_ENCODING") from None
        harness_raw = retained.get("rootCallHarness")
        _need(type(harness_raw) is bytes and bool(needle), "HARNESS_SOURCE_MISSING")
        occurrences = harness_raw.count(needle)
        _need(occurrences >= 1, "HARNESS_PROCESS_MODULE_LINK")
        link_record = {
            "schema": "tsj-caller-harness-module-link-v1",
            "harnessPath": harness["path"],
            "harnessBytes": harness["bytes"],
            "harnessSha256": harness["sha256"],
            "processModulePath": module["path"],
            "processModuleBytes": module["bytes"],
            "processModuleSha256": module["sha256"],
            "pathBytesSha256": _sha(needle),
            "occurrences": occurrences,
            "embedded": True,
        }
        link_evidence = {
            "record": link_record,
            "evidenceSha256": _sha(_canonical_json_bytes(link_record)),
        }

    _recheck_fact_map(api, tool_specs, tool_tokens, category="TOOL",
                      maximum_limit=MAX_READER_BYTES, names=TOOL_FACT_NAMES)
    _recheck_fact_map(api, source_specs, source_tokens, category="SOURCE",
                      maximum_limit=MAX_SOURCE_BYTES, names=tuple(sorted(source_specs)))
    return {
        "toolFacts": tool_facts,
        "sourceFacts": source_facts,
        "harnessModuleLinkEvidence": link_evidence,
    }


def _scan_names(directory_path: str):
    try:
        with os.scandir(directory_path) as entries:
            names = [entry.name for entry in entries]
    except OSError:
        raise CallerObservationError("RESOURCE_DIRECTORY_READ") from None
    _need(all(type(name) is str for name in names), "RESOURCE_DIRECTORY_NAMES")
    return sorted(names)


def _observe_directory_once(api, path: str, containment_root: str,
                            expected_names: tuple[str, ...], name: str):
    _path_check(api, containment_root, name + "_ROOT_PATH")
    _path_check(api, path, name + "_DIRECTORY_PATH")
    root_lexical, root_real, root_before = _inspect(
        api, containment_root, "directory", name + "_ROOT_INSPECT")
    directory_lexical, directory_real, directory_before = _inspect(
        api, path, "directory", name + "_DIRECTORY_INSPECT")
    _need(_within_existing_root(root_real, directory_real),
          name + "_DIRECTORY_CONTAINMENT")

    names_before = _scan_names(directory_real)
    _need(len(names_before) == len(expected_names)
          and set(names_before) == set(expected_names),
          name + "_DIRECTORY_CLOSURE")
    entries_before = []
    for child_name in expected_names:
        child = os.path.join(directory_real, child_name)
        child_lexical, child_real, child_identity = _inspect(
            api, child, "file", name + "_ENTRY_TYPE")
        _need(_within_existing_root(directory_real, child_real),
              name + "_ENTRY_CONTAINMENT")
        entries_before.append({
            "name": child_name,
            "path": child_lexical,
            "identity": _identity_json(child_identity),
        })

    names_after = _scan_names(directory_real)
    _need(len(names_after) == len(expected_names)
          and set(names_after) == set(expected_names),
          name + "_DIRECTORY_CLOSURE")
    entries_after = []
    for child_name in expected_names:
        child = os.path.join(directory_real, child_name)
        child_lexical, child_real, child_identity = _inspect(
            api, child, "file", name + "_ENTRY_RECHECK")
        _need(_within_existing_root(directory_real, child_real),
              name + "_ENTRY_CONTAINMENT")
        entries_after.append({
            "name": child_name,
            "path": child_lexical,
            "identity": _identity_json(child_identity),
        })

    _directory_after_lexical, directory_after_real, directory_after = _inspect(
        api, path, "directory", name + "_DIRECTORY_RECHECK")
    _root_after_lexical, root_after_real, root_after = _inspect(
        api, containment_root, "directory", name + "_ROOT_RECHECK")
    _need(names_before == names_after and entries_before == entries_after
          and directory_after == directory_before
          and os.path.normcase(directory_after_real) == os.path.normcase(directory_real)
          and root_after == root_before
          and os.path.normcase(root_after_real) == os.path.normcase(root_real),
          name + "_RESOURCE_CHANGED")

    return {
        "name": name,
        "path": directory_lexical,
        "containmentRoot": root_lexical,
        "directoryIdentity": _identity_json(directory_before),
        "directoryNames": list(expected_names),
        "entries": entries_before,
    }


def _is_confirmed_missing(error: OSError) -> bool:
    return (isinstance(error, FileNotFoundError)
            and error.errno == errno.ENOENT
            and getattr(error, "winerror", None) in (2, 3))


def _lstat_missing(path: str, code: str) -> None:
    try:
        os.lstat(path)
    except OSError as error:
        if _is_confirmed_missing(error):
            return
        raise CallerObservationError(code) from None
    raise CallerObservationError("ABSENCE_TARGET_PRESENT")


def _observe_absence_once(api, path: str, kind: str, containment_root: str,
                          name: str):
    _path_check(api, path, name + "_PATH")
    _path_check(api, containment_root, name + "_ROOT_PATH")
    pure = _reader_call(api, "_check_windows_local_lexical_path", path,
                        error_code=name + "_PATH")
    root_lexical, root_real, root_before = _inspect(
        api, containment_root, "directory", name + "_ROOT_INSPECT")
    _need(_within_lexical_root(root_real, path), name + "_CONTAINMENT")

    parts = pure.parts
    _need(type(parts) is tuple and len(parts) >= 2, name + "_PATH")
    current = Path(parts[0])
    current_lexical, current_real, current_identity = _inspect(
        api, str(current), "directory", name + "_ANCESTOR_INSPECT")
    missing_component = None
    components = parts[1:]
    for index, component in enumerate(components):
        candidate = current / component
        candidate_text = str(candidate)
        try:
            os.lstat(candidate_text)
        except OSError as error:
            if not _is_confirmed_missing(error):
                raise CallerObservationError(name + "_ABSENCE_IO") from None
            missing_component = candidate_text
            break
        candidate_kind = "directory" if index + 1 < len(components) else kind
        child_lexical, child_real, child_identity = _inspect(
            api, candidate_text, candidate_kind, name + "_EXISTING_COMPONENT")
        current = candidate
        current_lexical, current_real, current_identity = (
            child_lexical, child_real, child_identity)
        if index + 1 == len(components):
            raise CallerObservationError("ABSENCE_TARGET_PRESENT")

    _need(missing_component is not None, name + "_ABSENCE_UNPROVEN")
    _lstat_missing(missing_component, name + "_ABSENCE_IO")
    _lstat_missing(path, name + "_ABSENCE_IO")

    _ancestor_lexical, _ancestor_real, ancestor_after = _inspect(
        api, current_lexical, "directory", name + "_ANCESTOR_RECHECK")
    _root_after_lexical, root_after_real, root_after = _inspect(
        api, containment_root, "directory", name + "_ROOT_RECHECK")
    _lstat_missing(missing_component, name + "_ABSENCE_IO")
    _lstat_missing(path, name + "_ABSENCE_IO")
    _need(ancestor_after == current_identity and root_after == root_before
          and os.path.normcase(root_after_real) == os.path.normcase(root_real),
          name + "_ABSENCE_CHANGED")

    return {
        "path": path,
        "kind": kind,
        "containmentRoot": root_lexical,
        "missingComponent": missing_component,
        "existingAncestor": current_lexical,
        "existingAncestorIdentity": _identity_json(current_identity),
        "rootIdentity": _identity_json(root_before),
        "absent": True,
    }


def _observe_absence(api, spec, *, kind: str, name: str):
    _exact_keys(spec, ("path", "containmentRoot"), name + "_SPEC_KEYS")
    _need(type(spec["path"]) is str and type(spec["containmentRoot"]) is str,
          name + "_SPEC_TYPES")
    first = _observe_absence_once(api, spec["path"], kind,
                                  spec["containmentRoot"], name)
    second = _observe_absence_once(api, spec["path"], kind,
                                   spec["containmentRoot"], name)
    _need(first == second, name + "_ABSENCE_CHANGED")
    return first


def observe_resource_facts(*, reader, directory_specs, absence_specs):
    """Observe fixed immediate directory closures and process/log absences.

    Directory paths and containment roots are externally configured. The scan
    is shallow and only accepts the contract's exact seven-name call closures
    and two-name native-data closure. Absence paths must be explicit, canonical
    Windows paths beneath externally configured scratch roots. Each observation
    is rechecked independently; the result is not an atomic host snapshot.
    """
    api = _reader_api(reader)
    _exact_keys(directory_specs, RESOURCE_NAMES, "DIRECTORY_SPECS")
    _exact_keys(absence_specs, CALL_RESOURCE_NAMES, "ABSENCE_SPECS")
    resource_facts = {}
    evidence = {"schema": "tsj-caller-resource-evidence-v2", "resources": {}}

    for name in RESOURCE_NAMES:
        spec = directory_specs[name]
        _exact_keys(spec, ("path", "containmentRoot"), name + "_DIRECTORY_SPEC_KEYS")
        _need(type(spec["path"]) is str and type(spec["containmentRoot"]) is str,
              name + "_DIRECTORY_SPEC_TYPES")
        expected_names = DATA_DIRECTORY_NAMES if name == "nativeData" else CALL_DIRECTORY_NAMES
        directory_record = _observe_directory_once(
            api, spec["path"], spec["containmentRoot"], expected_names, name)
        absences = {}
        if name in CALL_RESOURCE_NAMES:
            call_absences = absence_specs[name]
            _exact_keys(call_absences, ABSENCE_FACT_NAMES, name + "_ABSENCE_KEYS")
            for field_name in ABSENCE_FACT_NAMES:
                kind = "directory" if field_name == "processRoot" else "file"
                absences[field_name] = _observe_absence(
                    api, call_absences[field_name], kind=kind,
                    name=name.upper() + "_" + field_name.upper())

        observation = {
            "schema": "tsj-caller-resource-observation-v2",
            "identityProjection": "tsj-fixed-role-reader-path-identity-v1",
            "resource": name,
            "directory": directory_record,
            "absences": absences,
        }
        evidence_sha = _sha(_canonical_json_bytes(observation))
        facts = {"directoryNames": list(expected_names),
                 "evidenceSha256": evidence_sha}
        present_absence_fields = (ABSENCE_FACT_NAMES
                                  if name in CALL_RESOURCE_NAMES else ())
        for field_name in present_absence_fields:
            facts[field_name] = {
                "path": absences[field_name]["path"],
                "absent": True,
            }
        resource_facts[name] = facts
        evidence["resources"][name] = {
            "record": observation,
            "evidenceSha256": evidence_sha,
        }
    return resource_facts, evidence


def observe_caller_facts(*, reader, tool_specs, source_specs,
                         directory_specs, absence_specs):
    """Collect actual source/tool byte facts, source link, closure and absence.

    The supplied reader module must already be source-authenticated by the
    trusted caller. This function only verifies its helper API and uses those
    exact path/read helpers; it does not load a module or claim source identity.
    Returned facts bind the observations made here, not an atomic filesystem
    state; the caller must recheck consumed sources, tools and resource facts
    after the normalizer has completed.
    """
    byte_facts = observe_byte_facts(
        reader=reader, tool_specs=tool_specs, source_specs=source_specs,
        include_harness_module_link=True)
    resource_facts, resource_evidence = observe_resource_facts(
        reader=reader, directory_specs=directory_specs,
        absence_specs=absence_specs)
    observation_evidence = {
        "schema": "tsj-caller-observation-evidence-v2",
        "harnessModuleLink": byte_facts["harnessModuleLinkEvidence"],
        "resources": resource_evidence["resources"],
    }
    observation_evidence["evidenceSha256"] = _sha(
        _canonical_json_bytes(observation_evidence))
    return {
        "toolFacts": byte_facts["toolFacts"],
        "sourceFacts": byte_facts["sourceFacts"],
        "resourceFacts": resource_facts,
        "observationEvidence": observation_evidence,
    }
