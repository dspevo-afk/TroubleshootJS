"""Standalone selected-Windows-I-B canary for the pinned prior-caller output helpers.

This fixture exercises only authenticated source modules and synthetic files under
an owned temporary directory. It never calls preflight, qualify, or the fixed-role
reader entrypoint that opens prior data.
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
import types
from pathlib import Path, PureWindowsPath


MAX_CALLER_SOURCE_BYTES = 4 * 1024 * 1024
MAX_READER_SOURCE_BYTES = 4 * 1024 * 1024
REPARSE_POINT = 0x400
OWNER_SCHEMA = "tsj-normal-prior-caller-output-canary-fixture-owner-v1"
_SHA256 = re.compile(r"[0-9a-f]{64}\Z")


class CanaryFailure(Exception):
    def __init__(self, code):
        self.code = code
        super().__init__(code)


class _Parser(argparse.ArgumentParser):
    def error(self, _message):
        raise CanaryFailure("CANARY_CLI_ARGUMENTS")


def _snapshot(st):
    return (
        st.st_dev,
        st.st_ino,
        st.st_mode,
        st.st_size,
        getattr(st, "st_mtime_ns", int(st.st_mtime * 1000000000)),
        getattr(st, "st_ctime_ns", int(st.st_ctime * 1000000000)),
        getattr(st, "st_file_attributes", 0),
    )


def _handle_identity(snapshot):
    return (
        snapshot[0],
        snapshot[1],
        stat.S_IFMT(snapshot[2]),
        snapshot[3],
        snapshot[4],
        snapshot[6],
    )


def _validate_source_path(raw):
    if (type(raw) is not str or not raw or len(raw) > 32767 or
            "\x00" in raw or "/" in raw or raw.startswith("\\\\")):
        raise CanaryFailure("SOURCE_PATH")
    pure = PureWindowsPath(raw)
    if (not pure.is_absolute() or pure.root != "\\" or
            re.fullmatch(r"[A-Za-z]:", pure.drive) is None):
        raise CanaryFailure("SOURCE_PATH")
    if os.path.normcase(os.path.normpath(raw)) != os.path.normcase(raw):
        raise CanaryFailure("SOURCE_PATH")
    parts = pure.parts
    current = Path(parts[0])
    for index, part in enumerate(parts[1:], start=1):
        if part in ("", ".", "..") or part.endswith((".", " ")) or ":" in part:
            raise CanaryFailure("SOURCE_PATH")
        current = current / part
        try:
            item = os.lstat(str(current))
        except OSError:
            raise CanaryFailure("SOURCE_PATH") from None
        if (stat.S_ISLNK(item.st_mode) or
                bool(getattr(item, "st_file_attributes", 0) & REPARSE_POINT)):
            raise CanaryFailure("SOURCE_REPARSE")
        is_leaf = index == len(parts) - 1
        if (not is_leaf and not stat.S_ISDIR(item.st_mode)) or (
                is_leaf and not stat.S_ISREG(item.st_mode)):
            raise CanaryFailure("SOURCE_KIND")
    try:
        resolved = Path(raw).resolve(strict=True)
    except (OSError, RuntimeError, ValueError):
        raise CanaryFailure("SOURCE_PATH") from None
    if os.path.normcase(os.path.abspath(raw)) != os.path.normcase(str(resolved)):
        raise CanaryFailure("SOURCE_PATH")
    return raw


def _read_pinned_source(path, limit, expected_sha, label):
    _validate_source_path(path)
    if type(expected_sha) is not str or _SHA256.fullmatch(expected_sha) is None:
        raise CanaryFailure("SOURCE_" + label + "_SHA_FORMAT")
    try:
        before_stat = os.lstat(path)
        before = _snapshot(before_stat)
    except OSError:
        raise CanaryFailure("SOURCE_" + label + "_PATH") from None
    if (not stat.S_ISREG(before[2]) or
            bool(before[6] & REPARSE_POINT) or before[3] > limit):
        raise CanaryFailure("SOURCE_" + label + "_BOUNDS")
    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(path, flags)
    except OSError:
        raise CanaryFailure("SOURCE_" + label + "_OPEN") from None
    blocks = []
    remaining = limit + 1
    try:
        opened = _snapshot(os.fstat(descriptor))
        if (not stat.S_ISREG(opened[2]) or
                _handle_identity(before) != _handle_identity(opened)):
            raise CanaryFailure("SOURCE_" + label + "_HANDLE")
        while remaining:
            block = os.read(descriptor, min(65536, remaining))
            if not block:
                break
            blocks.append(block)
            remaining -= len(block)
        raw = b"".join(blocks)
        after_handle = _snapshot(os.fstat(descriptor))
    except CanaryFailure:
        raise
    except OSError:
        raise CanaryFailure("SOURCE_" + label + "_READ") from None
    finally:
        os.close(descriptor)
    try:
        after_stat = os.lstat(path)
        after = _snapshot(after_stat)
    except OSError:
        raise CanaryFailure("SOURCE_" + label + "_PATH_CHANGED") from None
    if (before != after or
            _handle_identity(before) != _handle_identity(after_handle) or
            not stat.S_ISREG(after[2]) or not raw or
            len(raw) > limit or len(raw) != before[3]):
        raise CanaryFailure("SOURCE_" + label + "_CHANGED")
    digest = hashlib.sha256(raw).hexdigest()
    if digest != expected_sha:
        raise CanaryFailure("SOURCE_" + label + "_SHA256")
    return raw, digest


def _exec_authenticated_module(name, path, raw):
    if name in sys.modules or type(raw) is not bytes or not raw:
        raise CanaryFailure("SOURCE_MODULE_PRECONDITION")
    module = types.ModuleType(name)
    module.__file__ = path
    module.__package__ = ""
    sys.modules[name] = module
    try:
        code = compile(raw, path, "exec", dont_inherit=True)
        exec(code, module.__dict__)
    except Exception:
        sys.modules.pop(name, None)
        raise CanaryFailure("SOURCE_MODULE_EXEC") from None
    return module


def _load_modules(args):
    reader_raw, reader_sha = _read_pinned_source(
        args.reader, MAX_READER_SOURCE_BYTES, args.reader_sha, "READER")
    caller_raw, caller_sha = _read_pinned_source(
        args.caller, MAX_CALLER_SOURCE_BYTES, args.caller_sha, "CALLER")

    suffix = hashlib.sha256((reader_sha + caller_sha).encode("ascii")).hexdigest()[:16]
    reader = _exec_authenticated_module("_tsj_output_canary_reader_" + suffix,
                                        args.reader, reader_raw)

    # Re-authenticate the selected caller with the pinned reader immediately
    # before executing it; both checks still precede ModuleType execution.
    try:
        reader._inspect_path(args.caller, "file")
        confirmed, digest, _token = reader._read_file_once(
            args.caller, MAX_CALLER_SOURCE_BYTES, len(caller_raw), caller_sha,
            "CANARY_CALLER_SOURCE")
    except Exception:
        raise CanaryFailure("SOURCE_CALLER_READER_AUTH") from None
    if confirmed != caller_raw or digest != caller_sha:
        raise CanaryFailure("SOURCE_CALLER_READER_BINDING")

    caller = _exec_authenticated_module("_tsj_output_canary_caller_" + suffix,
                                        args.caller, caller_raw)
    required_caller = (
        "_assert_module_functions",
        "_root_bindings",
        "_verify_root_bindings",
        "_readback_output",
        "_write_exclusive",
        "_validate_output_closure",
    )
    required_reader = ("_inspect_path", "_read_file_once")
    try:
        caller._assert_module_functions(caller, required_caller, "canary")
        for name in required_reader:
            function = getattr(reader, name, None)
            if (type(function) is not types.FunctionType or
                    function.__module__ != reader.__name__ or
                    function.__globals__ is not reader.__dict__ or
                    function.__name__ != name):
                raise CanaryFailure("READER_FUNCTION_BINDING")
    except CanaryFailure:
        raise
    except Exception:
        raise CanaryFailure("CALLER_FUNCTION_BINDING") from None
    return caller, reader, caller_sha, reader_sha


def _make_fixture(base, label, caller, reader):
    root = Path(base) / label
    os.mkdir(str(root))
    n_root = root / "fake-N"
    d_root = root / "fake-D"
    scratch = root / "scratch"
    output = scratch / "output"
    for directory in (n_root, d_root, scratch, output):
        os.mkdir(str(directory))
    config = {
        "roots": {"N": str(n_root), "D": str(d_root)},
        "scratchRoot": str(scratch),
        "dataOutputRoot": str(output),
    }
    _bindings, roots_sha = caller._root_bindings(
        reader, config["roots"], config["scratchRoot"], config["dataOutputRoot"])
    return config, roots_sha


def _create_fixture_owner(base, receipt):
    temp_root = os.path.abspath(tempfile.gettempdir())
    root_path = os.path.abspath(base)
    receipt.update({
        "rootPath": root_path,
        "stableIdentity": None,
        "tempRoot": temp_root,
    })
    try:
        common = os.path.normcase(os.path.commonpath((temp_root, root_path)))
        temp_norm = os.path.normcase(temp_root)
        root_norm = os.path.normcase(root_path)
        before_stat = os.lstat(root_path)
    except (OSError, ValueError):
        raise CanaryFailure("FIXTURE_ROOT_IDENTITY") from None
    before = _snapshot(before_stat)
    if (common != temp_norm or root_norm == temp_norm or
            not stat.S_ISDIR(before[2]) or before[1] <= 0 or
            bool(before[6] & REPARSE_POINT)):
        raise CanaryFailure("FIXTURE_ROOT_IDENTITY")
    receipt["stableIdentity"] = {
        "device": before[0],
        "fileId": before[1],
        "kind": stat.S_IFMT(before[2]),
        "attributes": before[6],
    }

    owner_path = os.path.join(root_path, "OWNER.json")
    owner_raw = (json.dumps({
        "owner": "canary_caller_output",
        "schema": OWNER_SCHEMA,
        "status": "SYNTHETIC_ONLY",
    }, ensure_ascii=True, sort_keys=True, separators=(",", ":"),
       allow_nan=False) + "\n").encode("ascii")
    flags = (os.O_WRONLY | os.O_CREAT | os.O_EXCL |
             getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0))
    try:
        descriptor = os.open(owner_path, flags, 0o600)
    except OSError:
        raise CanaryFailure("FIXTURE_OWNER_CREATE") from None
    try:
        owner_stat = os.fstat(descriptor)
        if not stat.S_ISREG(owner_stat.st_mode):
            raise CanaryFailure("FIXTURE_OWNER_KIND")
        offset = 0
        while offset < len(owner_raw):
            written = os.write(descriptor, owner_raw[offset:offset + 65536])
            if type(written) is not int or written <= 0:
                raise CanaryFailure("FIXTURE_OWNER_WRITE")
            offset += written
        os.fsync(descriptor)
    except CanaryFailure:
        raise
    except OSError:
        raise CanaryFailure("FIXTURE_OWNER_WRITE") from None
    finally:
        os.close(descriptor)

    try:
        after = _snapshot(os.lstat(root_path))
        owner_after = _snapshot(os.lstat(owner_path))
    except OSError:
        raise CanaryFailure("FIXTURE_OWNER_RECHECK") from None
    before_identity = (before[0], before[1], stat.S_IFMT(before[2]), before[6])
    after_identity = (after[0], after[1], stat.S_IFMT(after[2]), after[6])
    if (before_identity != after_identity or not stat.S_ISDIR(after[2]) or
            bool(after[6] & REPARSE_POINT) or not stat.S_ISREG(owner_after[2]) or
            bool(owner_after[6] & REPARSE_POINT) or owner_after[3] != len(owner_raw)):
        raise CanaryFailure("FIXTURE_OWNER_RECHECK")
    receipt["stableIdentity"] = {
        "device": after[0],
        "fileId": after[1],
        "kind": stat.S_IFMT(after[2]),
        "attributes": after[6],
    }


def _capture(caller, action):
    try:
        return "PASS", action()
    except caller.CallerError as exc:
        return exc.code, None
    except CanaryFailure as exc:
        return exc.code, None
    except Exception as exc:
        return "UNEXPECTED_" + type(exc).__name__.upper(), None


def _add_case(cases, name, expected, observed, **details):
    row = {
        "name": name,
        "expectedCode": expected,
        "observedCode": observed,
        "status": "PASS" if expected == observed else "FAIL",
    }
    row.update(details)
    cases.append(row)
    return row["status"] == "PASS"


def _write(caller, reader, config, roots_sha, name, raw, phase):
    return caller._write_exclusive(
        reader, config,
        os.path.join(config["dataOutputRoot"], name),
        raw, caller.MAX_OUTPUT_BYTES, config["scratchRoot"], roots_sha, phase)


def _run_canary(caller, reader, base, cases):
    # Exclusive bounded write and authenticated readback positive.
    config, roots_sha = _make_fixture(base, "write-positive", caller, reader)
    path = os.path.join(config["dataOutputRoot"], "bounded-output.bin")
    raw = b"synthetic bounded output\n"

    def write_positive():
        write_fact = _write(caller, reader, config, roots_sha,
                            "bounded-output.bin", raw, "canary-write-positive")
        token = caller._readback_output(
            reader, config, path, raw, caller.MAX_OUTPUT_BYTES,
            roots_sha, "canary-readback-positive")
        if (write_fact != {"bytes": len(raw), "sha256": caller._sha(raw)} or
                type(token) is not tuple or len(token) != 4 or token[2] != token[3]):
            raise CanaryFailure("POSITIVE_READBACK_ASSERT")
        return True

    observed, value = _capture(caller, write_positive)
    _add_case(cases, "bounded_exclusive_write_and_readback", "PASS",
              observed, readbackBound=value is True)

    # A second create at the same path must reject and preserve original bytes.
    def overwrite():
        _write(caller, reader, config, roots_sha,
               "bounded-output.bin", b"replacement bytes\n", "canary-overwrite")
        return None

    observed, _ = _capture(caller, overwrite)
    preserved = False
    if observed == "EXCLUSIVE_OUTPUT_CREATE":
        code, _value = _capture(caller, lambda: caller._readback_output(
            reader, config, path, raw, caller.MAX_OUTPUT_BYTES,
            roots_sha, "canary-overwrite-preserved"))
        preserved = code == "PASS"
    _add_case(cases, "overwrite_rejected", "EXCLUSIVE_OUTPUT_CREATE",
              observed, originalBytesPreserved=preserved)
    if not preserved:
        cases[-1]["status"] = "FAIL"

    # Owned same-name directory replacement control, not an external race proof.
    replacement_config, replacement_roots_sha = _make_fixture(
        base, "root-replacement", caller, reader)
    old_output = Path(replacement_config["dataOutputRoot"])
    displaced = old_output.parent / "output-displaced"
    os.rename(str(old_output), str(displaced))
    os.mkdir(str(old_output))
    observed, _ = _capture(caller, lambda: _write(
        caller, reader, replacement_config, replacement_roots_sha,
        "successor-must-stay-empty.bin", b"must not be written\n",
        "canary-root-replacement"))
    with os.scandir(str(old_output)) as entries:
        successor_empty = next(entries, None) is None
    _add_case(cases, "same_name_output_root_replacement", "ROOTS_CHANGED",
              observed, successorEmpty=successor_empty,
              control="owned_fixture_swap_not_external_race_proof")
    if not successor_empty:
        cases[-1]["status"] = "FAIL"

    # Exact final closure positive, followed by wrong-byte and extra-file rejects.
    closure_config, closure_roots_sha = _make_fixture(base, "closure", caller, reader)
    expected_names = (caller.ATTEMPT_FILE,) + tuple(
        name for name, _label in caller.ROLE_OUTPUT_NAMES)
    expected_files = {
        name: ("synthetic output for " + name + "\n").encode("ascii")
        for name in expected_names
    }
    write_codes = []
    for name, file_raw in expected_files.items():
        code, _value = _capture(caller, lambda n=name, b=file_raw: _write(
            caller, reader, closure_config, closure_roots_sha,
            n, b, "canary-closure-write-" + n.replace(".", "-")))
        write_codes.append(code)

    def closure_positive():
        if any(code != "PASS" for code in write_codes):
            raise CanaryFailure("CLOSURE_FIXTURE_WRITE")
        facts = caller._validate_output_closure(
            reader, closure_config, expected_files, closure_roots_sha,
            "canary-closure-positive")
        if (type(facts) is not dict or facts.get("fileCount") != len(expected_names) or
                len(facts.get("files", ())) != len(expected_names) or
                not all(item.get("identityBound") is True for item in facts["files"])):
            raise CanaryFailure("CLOSURE_POSITIVE_ASSERT")
        return True

    observed, value = _capture(caller, closure_positive)
    _add_case(cases, "final_exact_closure_positive", "PASS", observed,
              exactFileCount=value is True and len(expected_names) == 5)

    wrong_files = dict(expected_files)
    target_name = "context.json"
    target_raw = expected_files[target_name]
    wrong_files[target_name] = bytes((target_raw[0] ^ 1,)) + target_raw[1:]
    observed, _ = _capture(caller, lambda: caller._validate_output_closure(
        reader, closure_config, wrong_files, closure_roots_sha,
        "canary-closure-wrong-bytes"))
    _add_case(cases, "closure_wrong_bytes_rejected", "READER_SHA256", observed)

    observed, _ = _capture(caller, lambda: _write(
        caller, reader, closure_config, closure_roots_sha,
        "unexpected-extra.bin", b"synthetic extra file\n", "canary-extra-write"))
    extra_write_ok = observed == "PASS"
    if extra_write_ok:
        observed, _ = _capture(caller, lambda: caller._validate_output_closure(
            reader, closure_config, expected_files, closure_roots_sha,
            "canary-closure-extra-file"))
    else:
        observed = "EXTRA_FIXTURE_WRITE_" + observed
    _add_case(cases, "closure_extra_file_rejected", "OUTPUT_CLOSURE_NAMES",
              observed, extraFileCreated=extra_write_ok)

    return cases


def _parse_args(argv):
    parser = _Parser(prog="canary_caller_output", add_help=False)
    parser.add_argument("--caller", required=True)
    parser.add_argument("--caller-sha", required=True)
    parser.add_argument("--reader", required=True)
    parser.add_argument("--reader-sha", required=True)
    return parser.parse_args(argv)


def _emit(payload):
    sys.stdout.write(json.dumps(payload, ensure_ascii=True, sort_keys=True,
                                separators=(",", ":"), allow_nan=False) + "\n")
    sys.stdout.flush()


def main(argv=None):
    cases = []
    fixture_created = False
    retained_fixture = None
    try:
        if sys.platform != "win32" or os.name != "nt":
            raise CanaryFailure("CANARY_WINDOWS_REQUIRED")
        if not sys.flags.isolated or not sys.dont_write_bytecode:
            raise CanaryFailure("CANARY_REQUIRES_I_B")
        args = _parse_args(argv)
        caller, reader, caller_sha, reader_sha = _load_modules(args)
        base = tempfile.mkdtemp(prefix="tsj-caller-output-canary-")
        fixture_created = True
        retained_fixture = {}
        _create_fixture_owner(base, retained_fixture)
        _run_canary(caller, reader, base, cases)
        status = "PASS" if all(case["status"] == "PASS" for case in cases) else "FAIL"
        payload = {
            "schema": "tsj-normal-prior-caller-output-canary-v1",
            "status": status,
            "callerSha256": caller_sha,
            "readerSha256": reader_sha,
            "cases": cases,
            "fixtureDisposition": "RETAINED_SYNTHETIC_ONLY",
            "privateRetainedFixture": retained_fixture,
            "rootReplacementControl": "OWNED_FIXTURE_SWAP_NOT_EXTERNAL_RACE_PROOF",
        }
        _emit(payload)
        return 0 if status == "PASS" else 2
    except CanaryFailure as exc:
        _emit({
            "schema": "tsj-normal-prior-caller-output-canary-v1",
            "status": "FAIL",
            "failureCode": exc.code,
            "cases": cases,
            "fixtureDisposition": (
                "RETAINED_SYNTHETIC_ONLY" if fixture_created else "NOT_CREATED"),
            **({"privateRetainedFixture": retained_fixture}
               if retained_fixture is not None else {}),
        })
        return 2
    except Exception as exc:
        _emit({
            "schema": "tsj-normal-prior-caller-output-canary-v1",
            "status": "FAIL",
            "failureCode": "UNEXPECTED_" + type(exc).__name__.upper(),
            "cases": cases,
            "fixtureDisposition": (
                "RETAINED_SYNTHETIC_ONLY" if fixture_created else "NOT_CREATED"),
            **({"privateRetainedFixture": retained_fixture}
               if retained_fixture is not None else {}),
        })
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
