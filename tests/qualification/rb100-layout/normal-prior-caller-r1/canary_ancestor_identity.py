"""Selected-Windows-I-B host canary for shared ancestor identity projections.

It loads only pinned reader/observer SOURCE modules. All mutations and byte
observer fixtures are owned synthetic files. No prior DATA or journal is opened.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import sys
import tempfile
import time
import types
from pathlib import PureWindowsPath


MODULE_LIMIT = 4 * 1024 * 1024
REPARSE_POINT = 0x400
HEX64 = re.compile(r"[0-9a-f]{64}\Z")
SNAPSHOT_FIELDS = ("dev", "ino", "mode", "size", "mtime", "ctime", "attrs")
CALL_NAMES = (
    "owner.json", "parent.json", "process.json", "request.json", "result.json",
    "stderr.txt", "stdout.txt",
)
DATA_NAMES = ("op-1.jsonl", "result.json")
RESOURCE_NAMES = ("qualification", "nativePrior", "nativeData")
ABSENCE_NAMES = ("processRoot", "stderrLog", "stdoutLog")
TOOL_NAMES = (
    "qualificationPython", "rootCallPowerShell", "nativeJava",
    "rootCallHarness", "processModule",
)


class CanaryFailure(Exception):
    def __init__(self, code):
        self.code = code
        super().__init__(code)


class _Parser(argparse.ArgumentParser):
    def error(self, _message):
        raise CanaryFailure("CLI_ARGUMENTS")


def _snapshot(st):
    return (
        st.st_dev, st.st_ino, st.st_mode, st.st_size,
        getattr(st, "st_mtime_ns", int(st.st_mtime * 1000000000)),
        getattr(st, "st_ctime_ns", int(st.st_ctime * 1000000000)),
        getattr(st, "st_file_attributes", 0),
    )


def _handle_identity(snapshot):
    return (
        snapshot[0], snapshot[1], stat.S_IFMT(snapshot[2]),
        snapshot[3], snapshot[4], snapshot[6],
    )


def _same_path(left, right):
    return os.path.normcase(os.path.normpath(str(left))) == os.path.normcase(
        os.path.normpath(str(right)))


def _validate_source_path(raw):
    if (type(raw) is not str or not raw or len(raw) > 32767 or
            "\x00" in raw or "/" in raw or raw.startswith("\\\\")):
        raise CanaryFailure("SOURCE_PATH")
    pure = PureWindowsPath(raw)
    if (not pure.is_absolute() or pure.root != "\\" or
            re.fullmatch(r"[A-Za-z]:", pure.drive) is None or
            os.path.normcase(os.path.normpath(raw)) != os.path.normcase(raw)):
        raise CanaryFailure("SOURCE_PATH")
    current = pure.parts[0]
    for index, part in enumerate(pure.parts[1:], start=1):
        if part in ("", ".", "..") or part.endswith((".", " ")) or ":" in part:
            raise CanaryFailure("SOURCE_PATH")
        current = os.path.join(current, part)
        try:
            st = os.lstat(current)
        except OSError:
            raise CanaryFailure("SOURCE_PATH") from None
        if (stat.S_ISLNK(st.st_mode) or
                getattr(st, "st_file_attributes", 0) & REPARSE_POINT):
            raise CanaryFailure("SOURCE_REPARSE")
        leaf = index == len(pure.parts) - 1
        if not leaf and not stat.S_ISDIR(st.st_mode):
            raise CanaryFailure("SOURCE_PATH_KIND")
        if leaf and not stat.S_ISREG(st.st_mode):
            raise CanaryFailure("SOURCE_PATH_KIND")
    if not _same_path(os.path.abspath(raw), os.path.realpath(raw)):
        raise CanaryFailure("SOURCE_NONCANONICAL")
    return raw


def _read_pinned_source(path, expected_sha, label):
    _validate_source_path(path)
    if type(expected_sha) is not str or HEX64.fullmatch(expected_sha) is None:
        raise CanaryFailure(label + "_SHA_FORMAT")
    try:
        before = _snapshot(os.lstat(path))
    except OSError:
        raise CanaryFailure(label + "_PATH") from None
    if (not stat.S_ISREG(before[2]) or before[3] <= 0 or
            before[3] > MODULE_LIMIT or before[6] & REPARSE_POINT):
        raise CanaryFailure(label + "_BOUNDS")
    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        fd = os.open(path, flags)
    except OSError:
        raise CanaryFailure(label + "_OPEN") from None
    chunks = []
    remaining = MODULE_LIMIT + 1
    try:
        opened = _snapshot(os.fstat(fd))
        if (not stat.S_ISREG(opened[2]) or
                _handle_identity(before) != _handle_identity(opened)):
            raise CanaryFailure(label + "_HANDLE")
        while remaining:
            block = os.read(fd, min(65536, remaining))
            if not block:
                break
            chunks.append(block)
            remaining -= len(block)
        raw = b"".join(chunks)
        after_handle = _snapshot(os.fstat(fd))
    except CanaryFailure:
        raise
    except OSError:
        raise CanaryFailure(label + "_READ") from None
    finally:
        os.close(fd)
    try:
        after = _snapshot(os.lstat(path))
    except OSError:
        raise CanaryFailure(label + "_CHANGED") from None
    if (before != after or _handle_identity(before) != _handle_identity(after_handle)
            or not raw or len(raw) > MODULE_LIMIT or len(raw) != before[3]):
        raise CanaryFailure(label + "_CHANGED")
    digest = hashlib.sha256(raw).hexdigest()
    if digest != expected_sha:
        raise CanaryFailure(label + "_SHA256")
    return raw, digest


def _exec_module(name, path, raw):
    if name in sys.modules or type(raw) is not bytes or not raw:
        raise CanaryFailure("MODULE_PRECONDITION")
    module = types.ModuleType(name)
    module.__file__ = path
    module.__package__ = ""
    sys.modules[name] = module
    try:
        exec(compile(raw, path, "exec", dont_inherit=True), module.__dict__)
    except Exception:
        sys.modules.pop(name, None)
        raise CanaryFailure("MODULE_EXEC") from None
    return module


def _module_function(module, name):
    function = getattr(module, name, None)
    if (type(function) is not types.FunctionType or
            function.__module__ != module.__name__ or
            function.__globals__ is not module.__dict__ or
            function.__name__ != name):
        raise CanaryFailure("MODULE_FUNCTION_BINDING")
    return function


def _load_owner_modules(args):
    if (not HEX64.fullmatch(args.reader_sha) or
            not HEX64.fullmatch(args.observer_sha)):
        raise CanaryFailure("MODULE_SHA_FORMAT")
    # Authenticate both bounded SOURCE blobs before executing either module.
    reader_raw, reader_sha = _read_pinned_source(
        args.reader_source, args.reader_sha, "READER_SOURCE")
    observer_raw, observer_sha = _read_pinned_source(
        args.observer_source, args.observer_sha, "OBSERVER_SOURCE")
    suffix = hashlib.sha256((reader_sha + observer_sha).encode("ascii")).hexdigest()[:16]
    reader = _exec_module("_tsj_ancestor_reader_" + suffix,
                          args.reader_source, reader_raw)
    for name in (
            "_check_windows_local_lexical_path", "_inspect_path", "_read_file_once",
            "_path_identity", "_token_identity"):
        _module_function(reader, name)
    if (not isinstance(getattr(reader, "ReaderError", None), type) or
            not issubclass(reader.ReaderError, Exception)):
        raise CanaryFailure("READER_ERROR_BINDING")
    try:
        confirmed, digest, _token = reader._read_file_once(
            args.observer_source, MODULE_LIMIT, len(observer_raw),
            observer_sha, "ANCESTOR_CANARY_OBSERVER_SOURCE")
    except Exception:
        raise CanaryFailure("OBSERVER_SOURCE_READER_AUTH") from None
    if confirmed != observer_raw or digest != observer_sha:
        raise CanaryFailure("OBSERVER_SOURCE_READER_BINDING")
    observer = _exec_module("_tsj_ancestor_observer_" + suffix,
                            args.observer_source, observer_raw)
    _module_function(observer, "observe_resource_facts")
    _module_function(observer, "observe_byte_facts")
    if (not isinstance(getattr(observer, "CallerObservationError", None), type) or
            not issubclass(observer.CallerObservationError, Exception)):
        raise CanaryFailure("OBSERVER_ERROR_BINDING")
    if (tuple(observer.RESOURCE_NAMES) != RESOURCE_NAMES or
            tuple(observer.CALL_DIRECTORY_NAMES) != CALL_NAMES or
            tuple(observer.DATA_DIRECTORY_NAMES) != DATA_NAMES or
            tuple(observer.ABSENCE_FACT_NAMES) != ABSENCE_NAMES or
            tuple(observer.TOOL_FACT_NAMES) != TOOL_NAMES):
        raise CanaryFailure("OBSERVER_CONTRACT_SHAPE")
    return reader, observer, reader_sha, observer_sha


def _error_code(exc):
    code = getattr(exc, "code", None)
    if type(code) is str and re.fullmatch(r"[A-Za-z0-9_]{1,96}", code):
        return code
    return "UNEXPECTED_" + type(exc).__name__.upper()


def _write_exclusive(path, raw):
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_BINARY", 0)
    fd = os.open(path, flags, 0o600)
    try:
        view = memoryview(raw)
        offset = 0
        while offset < len(raw):
            count = os.write(fd, view[offset:offset + 65536])
            if type(count) is not int or count <= 0:
                raise CanaryFailure("FIXTURE_WRITE")
            offset += count
        os.fsync(fd)
    finally:
        os.close(fd)


def _mkdir(path):
    os.mkdir(path)
    return path


def _create_owned_fixture(receipt):
    base = tempfile.mkdtemp(prefix="tsj-ancestor-identity-canary-")
    root_path = os.path.abspath(base)
    temp_root = os.path.abspath(tempfile.gettempdir())
    receipt.update({"rootPath": root_path, "tempRoot": temp_root,
                    "stableIdentity": None})
    try:
        common = os.path.normcase(os.path.commonpath((temp_root, root_path)))
        before = _snapshot(os.lstat(root_path))
    except (OSError, ValueError):
        raise CanaryFailure("FIXTURE_ROOT_IDENTITY") from None
    if (common != os.path.normcase(temp_root) or _same_path(temp_root, root_path)
            or not stat.S_ISDIR(before[2]) or before[1] <= 0
            or before[6] & REPARSE_POINT):
        raise CanaryFailure("FIXTURE_ROOT_IDENTITY")
    receipt["stableIdentity"] = {
        "dev": before[0], "ino": before[1],
        "kind": stat.S_IFMT(before[2]), "attrs": before[6],
    }
    owner_path = os.path.join(root_path, "OWNER.json")
    owner_raw = json.dumps({
        "owner": "canary_ancestor_identity",
        "schema": "tsj-ancestor-identity-canary-owner-v1",
        "status": "SYNTHETIC_ONLY",
    }, ensure_ascii=True, sort_keys=True, separators=(",", ":"),
       allow_nan=False).encode("ascii") + b"\n"
    _write_exclusive(owner_path, owner_raw)
    try:
        after = _snapshot(os.lstat(root_path))
        owner_after = _snapshot(os.lstat(owner_path))
    except OSError:
        raise CanaryFailure("FIXTURE_OWNER_RECHECK") from None
    before_id = (before[0], before[1], stat.S_IFMT(before[2]), before[6])
    after_id = (after[0], after[1], stat.S_IFMT(after[2]), after[6])
    if (before_id != after_id or not stat.S_ISREG(owner_after[2])
            or owner_after[6] & REPARSE_POINT or owner_after[3] != len(owner_raw)):
        raise CanaryFailure("FIXTURE_OWNER_RECHECK")
    receipt.update({"ownerFile": "OWNER.json",
                    "ownerSha256": hashlib.sha256(owner_raw).hexdigest()})
    return root_path


def _file(path, raw):
    if not os.path.isdir(os.path.dirname(path)):
        raise CanaryFailure("FIXTURE_PARENT")
    _write_exclusive(path, raw)
    return path


def _find_snapshot(identity, path):
    for component_path, snapshot in identity:
        if _same_path(component_path, path):
            return snapshot
    return None


def _stable_identity(snapshot):
    return snapshot[0], snapshot[1], snapshot[2], snapshot[6]


def _same_file_bytes(path, expected):
    with open(path, "rb") as stream:
        return stream.read() == expected


def _force_real_directory_mtime_change(directory, sibling):
    before = _snapshot(os.lstat(directory))
    _file(sibling, b"synthetic unrelated sibling\n")
    after = before
    for _ in range(6):
        os.utime(directory, None)
        after = _snapshot(os.lstat(directory))
        if after[4] != before[4]:
            return before, after
        time.sleep(0.01)
    raise CanaryFailure("CLOCK_CONTROL_NOT_OBSERVED")


def _snapshot_differences(case, path, before, after, location):
    if before is None or after is None:
        return [{
            "case": case, "path": path, "location": location,
            "field": "snapshotPresence",
            "before": before is not None, "after": after is not None,
        }]
    rows = []
    for index, field in enumerate(
            ("dev", "ino", "mode", "size", "mtime", "ctime", "attrs")):
        if before[index] != after[index]:
            rows.append({
                "case": case, "path": path, "location": location,
                "field": field, "before": before[index], "after": after[index],
            })
    return rows


def _record(name, expected, observed, ok=None, **details):
    matched = (expected == observed) if ok is None else bool(ok)
    return {
        "name": name,
        "expectedCode": expected,
        "observedCode": observed,
        "status": "MATCHED" if matched else "MISMATCH",
        **details,
    }
def _read_once_with_hook(reader, path, expected_bytes, expected_sha, role, mutation):
    original = reader._inspect_path
    trace = {"first": None, "postMutation": None, "mutated": False, "calls": 0}
    code = "OBSERVED"
    value = None

    def hooked(raw_path, expected_kind):
        result = original(raw_path, expected_kind)
        if not trace["mutated"] and _same_path(raw_path, path):
            trace["calls"] += 1
            if trace["calls"] == 1:
                trace["first"] = result
                trace["mutated"] = True
                mutation(result, original, trace)
        return result

    reader._inspect_path = hooked
    try:
        value = reader._read_file_once(
            path, 4 * 1024 * 1024, expected_bytes, expected_sha, role)
    except Exception as exc:
        code = _error_code(exc)
    finally:
        reader._inspect_path = original
    try:
        trace["postMutation"] = original(path, "file")
    except Exception as exc:
        trace["postMutationError"] = _error_code(exc)
    return code, value, trace


def _leaf_clock_case(reader, root, diagnostics):
    case_root = _mkdir(os.path.join(root, "leaf-clock"))
    parent = _mkdir(os.path.join(case_root, "parent"))
    path = os.path.join(parent, "source.bin")
    expected = b"synthetic source bytes for stable ancestor projection\n"
    digest = hashlib.sha256(expected).hexdigest()
    _file(path, expected)
    first_raw, first_sha, first_token = reader._read_file_once(
        path, 4096, len(expected), digest, "ANCESTOR_CANARY_SOURCE")
    token_parent_before = _find_snapshot(first_token[1], parent)
    leaf_before = first_token[1][-1][1]
    clock_before, _clock_after = _force_real_directory_mtime_change(
        parent, os.path.join(parent, "unrelated-sibling.bin"))
    second_raw, second_sha, second_token = reader._read_file_once(
        path, 4096, len(expected), digest, "ANCESTOR_CANARY_SOURCE")
    token_parent_after = _find_snapshot(second_token[1], parent)
    leaf_after = second_token[1][-1][1]
    path_identity_stable = (
        reader._path_identity(first_token[1]) == reader._path_identity(second_token[1]))
    token_identity_stable = (
        reader._token_identity(first_token) == reader._token_identity(second_token))
    clock_changed = (
        token_parent_before is not None and token_parent_after is not None and
        token_parent_before[4] != token_parent_after[4] and
        clock_before[4] != _clock_after[4])
    sha_ok = (
        first_sha == second_sha == digest and
        hashlib.sha256(first_raw).hexdigest() == digest and
        hashlib.sha256(second_raw).hexdigest() == digest)
    ok = (
        first_raw == second_raw == expected and sha_ok and clock_changed and
        leaf_before == leaf_after and path_identity_stable and token_identity_stable)
    diagnostics.extend(_snapshot_differences(
        "leaf_source_ancestor_clock", parent,
        token_parent_before, token_parent_after, "ancestor"))
    diagnostics.extend(_snapshot_differences(
        "leaf_source_ancestor_clock", path, leaf_before, leaf_after, "leaf"))
    return _record(
        "leaf_source_parent_clock_and_token_projection", "OBSERVED",
        "OBSERVED" if ok else "MISMATCH", ok=ok,
        independentSha256=digest, parentMtimeChanged=clock_changed,
        leafSnapshotUnchanged=leaf_before == leaf_after,
        pathIdentityStable=path_identity_stable,
        tokenIdentityStable=token_identity_stable,
    )


def _leaf_replacement_case(reader, root, diagnostics):
    case_root = _mkdir(os.path.join(root, "leaf-replacement"))
    parent = _mkdir(os.path.join(case_root, "parent"))
    path = os.path.join(parent, "source.bin")
    backup = os.path.join(parent, "source-displaced.bin")
    raw = b"same bytes across owned same-name replacement\n"
    digest = hashlib.sha256(raw).hexdigest()
    _file(path, raw)

    def replace(_before_result, _original, trace):
        trace["beforeLeaf"] = _snapshot(os.lstat(path))
        os.rename(path, backup)
        _file(path, raw)
        trace["afterLeaf"] = _snapshot(os.lstat(path))

    code, _value, trace = _read_once_with_hook(
        reader, path, len(raw), digest, "ANCESTOR_CANARY_REPLACEMENT", replace)
    before = trace.get("beforeLeaf")
    after = trace.get("afterLeaf")
    file_id_changed = (
        before is not None and after is not None and
        (before[0], before[1]) != (after[0], after[1]))
    same_bytes = _same_file_bytes(path, raw) and _same_file_bytes(backup, raw)
    diagnostics.extend(_snapshot_differences(
        "same_name_file_replacement", path, before, after, "leaf"))
    return _record(
        "same_name_file_replacement", "FILE_CHANGED", code,
        ok=code == "FILE_CHANGED" and file_id_changed and same_bytes,
        leafFileIdChanged=file_id_changed,
        replacementBytesEqual=same_bytes,
        independentSha256=digest,
    )


def _leaf_metadata_content_case(reader, root, diagnostics):
    case_root = _mkdir(os.path.join(root, "leaf-metadata-content"))
    parent = _mkdir(os.path.join(case_root, "parent"))
    metadata_path = os.path.join(parent, "metadata.bin")
    metadata_raw = b"stable bytes with changed leaf metadata\n"
    metadata_sha = hashlib.sha256(metadata_raw).hexdigest()
    _file(metadata_path, metadata_raw)

    def touch_leaf(before_result, _original, trace):
        before_leaf = before_result[2][-1][1]
        for _ in range(6):
            os.utime(metadata_path, None)
            after_leaf = _snapshot(os.lstat(metadata_path))
            if after_leaf[4] != before_leaf[4]:
                trace["beforeLeaf"] = before_leaf
                trace["afterLeaf"] = after_leaf
                return
            time.sleep(0.01)
        raise CanaryFailure("LEAF_MTIME_CONTROL_NOT_OBSERVED")

    metadata_code, _value, metadata_trace = _read_once_with_hook(
        reader, metadata_path, len(metadata_raw), metadata_sha,
        "ANCESTOR_CANARY_METADATA", touch_leaf)
    before_meta = metadata_trace.get("beforeLeaf")
    after_meta = metadata_trace.get("afterLeaf")
    diagnostics.extend(_snapshot_differences(
        "leaf_metadata_mutation", metadata_path, before_meta, after_meta, "leaf"))

    content_path = os.path.join(parent, "content.bin")
    original = b"independent content hash rejects mutation\n"
    changed = bytes((original[0] ^ 1,)) + original[1:]
    original_sha = hashlib.sha256(original).hexdigest()
    changed_sha = hashlib.sha256(changed).hexdigest()
    _file(content_path, original)
    _old_bytes, _old_sha, old_token = reader._read_file_once(
        content_path, 4096, len(original), original_sha, "ANCESTOR_CANARY_CONTENT")
    with open(content_path, "r+b") as stream:
        stream.write(changed)
        stream.flush()
        os.fsync(stream.fileno())
    before_touch = _snapshot(os.lstat(content_path))
    after_touch = before_touch
    for _ in range(6):
        os.utime(content_path, None)
        after_touch = _snapshot(os.lstat(content_path))
        if after_touch[4] != before_touch[4]:
            break
        time.sleep(0.01)
    content_error = _capture_read(reader, content_path, len(original), original_sha)
    new_bytes, new_sha, new_token = reader._read_file_once(
        content_path, 4096, len(changed), changed_sha, "ANCESTOR_CANARY_CONTENT")
    token_changed = reader._token_identity(old_token) != reader._token_identity(new_token)
    diagnostics.extend(_snapshot_differences(
        "leaf_content_mutation", content_path,
        old_token[1][-1][1], new_token[1][-1][1], "leaf"))
    metadata_ok = (
        metadata_code == "FILE_CHANGED" and before_meta is not None
        and after_meta is not None and before_meta != after_meta)
    content_ok = (
        content_error == "SHA256" and new_bytes == changed and
        new_sha == changed_sha and original_sha != changed_sha and token_changed
        and after_touch[4] != before_touch[4])
    observed = "metadata=" + metadata_code + ";content=" + content_error
    expected = "metadata=FILE_CHANGED;content=SHA256"
    return {
        "name": "leaf_metadata_and_content_mutation",
        "expectedCode": expected,
        "observedCode": observed,
        "status": "MATCHED" if metadata_ok and content_ok else "MISMATCH",
        "metadataSnapshotChanged": before_meta != after_meta,
        "originalSha256": original_sha,
        "mutatedSha256": changed_sha,
        "contentTokenIdentityChanged": token_changed,
    }


def _capture_read(reader, path, expected_bytes, expected_sha):
    try:
        reader._read_file_once(
            path, 4096, expected_bytes, expected_sha, "ANCESTOR_CANARY_CONTENT")
    except Exception as exc:
        return _error_code(exc)
    return "OBSERVED"


def _ancestor_replacement_case(reader, root, diagnostics):
    case_root = _mkdir(os.path.join(root, "ancestor-replacement"))
    parent = _mkdir(os.path.join(case_root, "parent"))
    alternate = _mkdir(os.path.join(case_root, "alternate-parent"))
    displaced = os.path.join(case_root, "displaced-parent")
    path = os.path.join(parent, "same-leaf.bin")
    alternate_leaf = os.path.join(alternate, "same-leaf.bin")
    raw = b"same leaf object across owned ancestor replacement\n"
    digest = hashlib.sha256(raw).hexdigest()
    _file(path, raw)
    try:
        os.link(path, alternate_leaf)
    except OSError as exc:
        return {
            "name": "ancestor_replacement_retaining_same_leaf",
            "expectedCode": "FILE_CHANGED",
            "observedCode": "HARDLINK_CONTROL_UNAVAILABLE",
            "status": "BLOCKED",
            "controlErrorType": type(exc).__name__,
        }

    def replace_parent(before_result, original_inspect, trace):
        before_identity = before_result[2]
        trace["beforeParent"] = _find_snapshot(before_identity, parent)
        trace["beforeLeaf"] = before_identity[-1][1]
        os.rename(parent, displaced)
        os.rename(alternate, parent)
        after_result = original_inspect(path, "file")
        trace["afterIdentity"] = after_result[2]
        trace["afterParent"] = _find_snapshot(after_result[2], parent)
        trace["afterLeaf"] = after_result[2][-1][1]

    code, _value, trace = _read_once_with_hook(
        reader, path, len(raw), digest, "ANCESTOR_CANARY_PARENT", replace_parent)
    before_parent = trace.get("beforeParent")
    after_parent = trace.get("afterParent")
    before_leaf = trace.get("beforeLeaf")
    after_leaf = trace.get("afterLeaf")
    same_leaf = before_leaf is not None and before_leaf == after_leaf
    ancestor_changed = (
        before_parent is not None and after_parent is not None and
        _stable_identity(before_parent) != _stable_identity(after_parent))
    diagnostics.extend(_snapshot_differences(
        "ancestor_replacement_retaining_same_leaf", parent,
        before_parent, after_parent, "ancestor"))
    diagnostics.extend(_snapshot_differences(
        "ancestor_replacement_retaining_same_leaf", path,
        before_leaf, after_leaf, "leaf"))
    return _record(
        "ancestor_replacement_retaining_same_leaf", "FILE_CHANGED", code,
        ok=code == "FILE_CHANGED" and same_leaf and ancestor_changed,
        leafSnapshotExactlySame=same_leaf,
        ancestorStableIdentityChanged=ancestor_changed,
        independentSha256=digest,
    )
def _populate_resource_fixture(case_root):
    resource_root = _mkdir(os.path.join(case_root, "resource-root"))
    directories = {}
    for name in RESOURCE_NAMES:
        directories[name] = _mkdir(os.path.join(resource_root, name))
        names = DATA_NAMES if name == "nativeData" else CALL_NAMES
        for file_name in names:
            body = ("synthetic resource " + name + " " + file_name + "\n").encode("ascii")
            _file(os.path.join(directories[name], file_name), body)
    directory_specs = {
        name: {"path": directories[name], "containmentRoot": resource_root}
        for name in RESOURCE_NAMES
    }
    absence_specs = {}
    for name in ("qualification", "nativePrior"):
        absent = os.path.join(resource_root, "absent", name)
        absence_specs[name] = {
            "processRoot": {
                "path": os.path.join(absent, "process-root"),
                "containmentRoot": resource_root,
            },
            "stderrLog": {
                "path": os.path.join(absent, "stderr.log"),
                "containmentRoot": resource_root,
            },
            "stdoutLog": {
                "path": os.path.join(absent, "stdout.log"),
                "containmentRoot": resource_root,
            },
        }
    return resource_root, directory_specs, absence_specs


def _capture_resource(observer, reader, directory_specs, absence_specs):
    try:
        facts, evidence = observer.observe_resource_facts(
            reader=reader, directory_specs=directory_specs,
            absence_specs=absence_specs)
        return "OBSERVED", (facts, evidence)
    except Exception as exc:
        return _error_code(exc), None


def _resource_clock_case(reader, observer, root, diagnostics):
    case_root = _mkdir(os.path.join(root, "resource-outer-clock"))
    resource_root, directory_specs, absence_specs = _populate_resource_fixture(case_root)
    first_code, first = _capture_resource(
        observer, reader, directory_specs, absence_specs)
    if first_code != "OBSERVED":
        return _record(
            "resource_outer_ancestor_clock", "OBSERVED", first_code,
            resourceFactsStable=False)
    root_before = reader._inspect_path(resource_root, "directory")[2]
    outer_before = _snapshot(os.lstat(case_root))
    _clock_before, outer_after = _force_real_directory_mtime_change(
        case_root, os.path.join(case_root, "unrelated-outer-sibling.bin"))
    root_after = reader._inspect_path(resource_root, "directory")[2]
    second_code, second = _capture_resource(
        observer, reader, directory_specs, absence_specs)
    outer_clock_changed = outer_before[4] != outer_after[4]
    root_leaf_unchanged = root_before[-1][1] == root_after[-1][1]
    facts_stable = (
        second_code == "OBSERVED" and first[0] == second[0] and first[1] == second[1])
    diagnostics.extend(_snapshot_differences(
        "resource_outer_ancestor_clock", case_root,
        outer_before, outer_after, "ancestor"))
    diagnostics.extend(_snapshot_differences(
        "resource_outer_ancestor_clock", resource_root,
        root_before[-1][1], root_after[-1][1], "leaf"))
    ok = outer_clock_changed and root_leaf_unchanged and facts_stable
    return _record(
        "resource_outer_ancestor_clock", "OBSERVED",
        "OBSERVED" if ok else second_code, ok=ok,
        outerAncestorMtimeChanged=outer_clock_changed,
        resourceRootLeafSnapshotUnchanged=root_leaf_unchanged,
        resourceFactsAndEvidenceStable=facts_stable,
    )


def _resource_reject_case(observer, reader, root, label, mutate, expected):
    case_root = _mkdir(os.path.join(root, label))
    resource_root, directory_specs, absence_specs = _populate_resource_fixture(case_root)
    control = mutate(resource_root, directory_specs, absence_specs)
    observed, _value = _capture_resource(
        observer, reader, directory_specs, absence_specs)
    row = _record(label, expected, observed)
    return row, control


def _add_internal_sibling(_resource_root, directory_specs, _absence_specs):
    path = os.path.join(directory_specs["qualification"]["path"], "unexpected.bin")
    _file(path, b"unexpected synthetic sibling\n")
    return {"case": "resource_internal_sibling", "path": path, "location": "leaf"}


def _replace_leaf_with_directory(_resource_root, directory_specs, _absence_specs):
    path = os.path.join(directory_specs["qualification"]["path"], "owner.json")
    os.unlink(path)
    os.mkdir(path)
    return {"case": "resource_leaf_kind", "path": path, "location": "leaf"}


def _create_existing_absence_target(_resource_root, _directory_specs, absence_specs):
    path = absence_specs["qualification"]["processRoot"]["path"]
    os.makedirs(os.path.dirname(path), exist_ok=True)
    os.mkdir(path)
    return {"case": "resource_existing_target", "path": path, "location": "leaf"}


def _byte_observer_synthetic_case(reader, observer, root):
    case_root = _mkdir(os.path.join(root, "byte-observer"))
    source_dir = _mkdir(os.path.join(case_root, "source-parent"))
    tool_dir = _mkdir(os.path.join(case_root, "tool-parent"))
    source_path = os.path.join(source_dir, "source.bin")
    source_raw = b"synthetic SOURCE body for observer token recheck\n"
    source_sha = hashlib.sha256(source_raw).hexdigest()
    _file(source_path, source_raw)
    source_specs = {
        "fixtureSource": {
            "path": source_path, "expectedBytes": len(source_raw),
            "maxBytes": 4096, "sha256": source_sha,
        }
    }
    tool_specs = {}
    expected_tool_shas = {}
    for name in TOOL_NAMES:
        path = os.path.join(tool_dir, name + ".bin")
        raw = ("synthetic tool bytes for " + name + "\n").encode("ascii")
        digest = hashlib.sha256(raw).hexdigest()
        _file(path, raw)
        tool_specs[name] = {
            "path": path, "expectedBytes": len(raw),
            "maxBytes": 4096, "sha256": digest,
        }
        expected_tool_shas[name] = digest

    first_path = tool_specs[TOOL_NAMES[0]]["path"]
    original = reader._inspect_path
    seam = {"calls": 0, "changed": False, "before": None, "after": None}

    def hooked(path, expected_kind):
        result = original(path, expected_kind)
        if not seam["changed"] and _same_path(path, first_path):
            seam["calls"] += 1
            if seam["calls"] == 2:
                seam["before"], seam["after"] = _force_real_directory_mtime_change(
                    tool_dir, os.path.join(tool_dir, "unrelated-byte-sibling.bin"))
                seam["changed"] = True
        return result

    reader._inspect_path = hooked
    try:
        observed = observer.observe_byte_facts(
            reader=reader, tool_specs=tool_specs, source_specs=source_specs,
            include_harness_module_link=False)
        code = "OBSERVED"
    except Exception as exc:
        observed = None
        code = _error_code(exc)
    finally:
        reader._inspect_path = original

    clock_changed = (
        seam["before"] is not None and seam["after"] is not None and
        seam["before"][4] != seam["after"][4])
    facts_bound = (
        type(observed) is dict and
        set(observed.get("sourceFacts", {})) == {"fixtureSource"} and
        observed["sourceFacts"]["fixtureSource"]["sha256"] == source_sha and
        observed["sourceFacts"]["fixtureSource"]["bytes"] == len(source_raw) and
        set(observed.get("toolFacts", {})) == set(TOOL_NAMES) and
        all(observed["toolFacts"][name]["sha256"] == expected_tool_shas[name]
            for name in TOOL_NAMES))
    return _record(
        "observer_byte_token_recheck_on_synthetic_files", "OBSERVED",
        "OBSERVED" if code == "OBSERVED" and clock_changed and facts_bound else code,
        ok=code == "OBSERVED" and clock_changed and facts_bound,
        siblingParentMtimeChanged=clock_changed,
        independentSourceSha256=source_sha,
        independentToolSha256=expected_tool_shas,
        returnedFactsBound=facts_bound,
    )


def _run_canary(reader, observer, root, cases, diagnostics):
    cases.append(_leaf_clock_case(reader, root, diagnostics))
    cases.append(_leaf_replacement_case(reader, root, diagnostics))
    cases.append(_leaf_metadata_content_case(reader, root, diagnostics))
    cases.append(_ancestor_replacement_case(reader, root, diagnostics))
    cases.append(_byte_observer_synthetic_case(reader, observer, root))
    cases.append(_resource_clock_case(reader, observer, root, diagnostics))

    row, control = _resource_reject_case(
        observer, reader, root, "resource_internal_sibling",
        _add_internal_sibling, "qualification_DIRECTORY_CLOSURE")
    cases.append(row)
    diagnostics.append(control)

    row, control = _resource_reject_case(
        observer, reader, root, "resource_leaf_kind",
        _replace_leaf_with_directory, "qualification_ENTRY_TYPE_REGULAR")
    cases.append(row)
    diagnostics.append(control)

    row, control = _resource_reject_case(
        observer, reader, root, "resource_existing_target",
        _create_existing_absence_target, "ABSENCE_TARGET_PRESENT")
    cases.append(row)
    diagnostics.append(control)

    # Reparse behavior is checked by the maintained real-junction CLI matrix;
    # this focused canary does not require file-symlink privilege.


def _parse_args(argv=None):
    parser = _Parser(prog="canary_ancestor_identity", add_help=False)
    parser.add_argument("--reader-source", required=True)
    parser.add_argument("--reader-sha", required=True)
    parser.add_argument("--observer-source", required=True)
    parser.add_argument("--observer-sha", required=True)
    return parser.parse_args(argv)


def _emit(payload):
    sys.stdout.write(json.dumps(
        payload, ensure_ascii=True, sort_keys=True, separators=(",", ":"),
        allow_nan=False) + "\n")
    sys.stdout.flush()


def main(argv=None):
    cases, diagnostics = [], []
    receipt = None
    fixture_created = False
    try:
        if sys.platform != "win32" or os.name != "nt":
            raise CanaryFailure("WINDOWS_REQUIRED")
        if not sys.flags.isolated or not sys.dont_write_bytecode:
            raise CanaryFailure("REQUIRES_I_B")
        args = _parse_args(argv)
        reader, observer, reader_sha, observer_sha = _load_owner_modules(args)
        receipt = {}
        root = _create_owned_fixture(receipt)
        fixture_created = True
        _run_canary(reader, observer, root, cases, diagnostics)
        payload = {
            "schema": "tsj-normal-prior-ancestor-identity-canary-r1",
            "status": "DIAGNOSTIC_ONLY",
            "readerSha256": reader_sha,
            "observerSha256": observer_sha,
            "caseCount": len(cases),
            "cases": cases,
            "privateDiagnostics": diagnostics,
            "fixtureDisposition": "RETAINED_SYNTHETIC_ONLY",
            "privateRetainedFixture": receipt,
            "externalRaceGuarantee": "NOT_CLAIMED",
            "qualification": "NOT_RUN",
            "priorData": "NOT_READ",
            "phaseJournal": "NOT_READ",
        }
        _emit(payload)
        return 0 if all(case["status"] == "MATCHED" for case in cases) else 2
    except CanaryFailure as exc:
        retained = receipt is not None and "rootPath" in receipt
        _emit({
            "schema": "tsj-normal-prior-ancestor-identity-canary-r1",
            "status": "DIAGNOSTIC_ONLY",
            "failureCode": exc.code,
            "cases": cases,
            "privateDiagnostics": diagnostics,
            "fixtureDisposition": (
                "RETAINED_SYNTHETIC_ONLY" if retained else "NOT_CREATED"),
            **({"privateRetainedFixture": receipt} if retained else {}),
            "externalRaceGuarantee": "NOT_CLAIMED",
            "qualification": "NOT_RUN",
            "priorData": "NOT_READ",
            "phaseJournal": "NOT_READ",
        })
        return 2
    except Exception as exc:
        retained = receipt is not None and "rootPath" in receipt
        _emit({
            "schema": "tsj-normal-prior-ancestor-identity-canary-r1",
            "status": "DIAGNOSTIC_ONLY",
            "failureCode": "UNEXPECTED_" + type(exc).__name__.upper(),
            "cases": cases,
            "privateDiagnostics": diagnostics,
            "fixtureDisposition": (
                "RETAINED_SYNTHETIC_ONLY" if retained else "NOT_CREATED"),
            **({"privateRetainedFixture": receipt} if retained else {}),
            "externalRaceGuarantee": "NOT_CLAIMED",
            "qualification": "NOT_RUN",
            "priorData": "NOT_READ",
            "phaseJournal": "NOT_READ",
        })
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
