"""Selected-Windows bootstrap-read canary for authenticated SOURCE and tools.

The canary loads only the two caller-supplied, SHA-pinned SOURCE modules. It
does not call their CLI entry points, read prior-role DATA, or inspect a phase
journal. Tool files are read as bytes only; executable files are never launched.
"""

import hashlib
import json
import os
import re
import shutil
import stat
import sys
import tempfile
import types
from pathlib import Path, PureWindowsPath


CALLER_MAX_BYTES = 4 * 1024 * 1024
READER_MAX_BYTES = 4 * 1024 * 1024
TOOL_MAX_BYTES = 8 * 1024 * 1024
TOOL_SPECS_MAX_BYTES = 64 * 1024
TOOL_SPECS_BYTES = 2175
TOOL_SPECS_SHA256 = "d9f4c0c279dc855b9b35b64cde8563ab62989b5a0dc39550c1945cb02c7e5b1b"
PHASE = "bootstrap-canary"
HEX64 = re.compile(r"[0-9a-f]{64}\Z")
REPARSE_POINT = 0x400
TOOL_NAMES = (
    "qualificationPython", "rootCallPowerShell", "nativeJava",
    "rootCallHarness", "processModule",
)
SOURCE_NAMES = ("currentNormalizer", "currentObserver")


class CanaryError(Exception):
    def __init__(self, code):
        self.code = code
        super().__init__(code)


def _need(condition, code):
    if not condition:
        raise CanaryError(code)


def _path_text(raw):
    _need(sys.platform == "win32" and os.name == "nt", "WINDOWS_REQUIRED")
    _need(type(raw) is str and bool(raw) and len(raw) <= 32767 and "\x00" not in raw,
          "PATH")
    _need("/" not in raw and not raw.startswith("\\\\"), "NONCANONICAL")
    pure = PureWindowsPath(raw)
    _need(pure.is_absolute() and pure.root == "\\" and
          re.fullmatch(r"[A-Za-z]:", pure.drive) is not None, "PATH")
    _need(os.path.normcase(os.path.normpath(raw)) == os.path.normcase(raw), "NONCANONICAL")
    return Path(raw)


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


def _file_identity(snapshot):
    # Windows can infer permission bits from an extension on path stats.
    return (snapshot[0], snapshot[1], stat.S_IFMT(snapshot[2]),
            snapshot[3], snapshot[4], snapshot[6])


def _is_reparse(st):
    return (stat.S_ISLNK(st.st_mode) or
            bool(getattr(st, "st_file_attributes", 0) & REPARSE_POINT))


def _read_authenticated_input(path_text, maximum, expected_sha256,
                             expected_bytes=None, error_prefix="SOURCE"):
    """Small pre-load SOURCE bootstrap; final I/O behavior is checked by caller."""
    path = _path_text(path_text)
    try:
        real = path.resolve(strict=True)
        before_stat = os.lstat(str(path))
    except (OSError, RuntimeError, ValueError):
        raise CanaryError(error_prefix + "_PATH") from None
    _need(os.path.normcase(os.path.abspath(str(path))) == os.path.normcase(str(real)),
          error_prefix + "_NONCANONICAL")
    _need(stat.S_ISREG(before_stat.st_mode) and not _is_reparse(before_stat),
          error_prefix + "_FILE_KIND")
    before = _snapshot(before_stat)
    _need(before[3] <= maximum, error_prefix + "_SIZE")
    if expected_bytes is not None:
        _need(type(expected_bytes) is int and before[3] == expected_bytes,
              error_prefix + "_EXPECTED_BYTES")

    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(str(real), flags)
    except OSError:
        raise CanaryError(error_prefix + "_OPEN") from None
    chunks = []
    remaining = maximum + 1
    try:
        opened = _snapshot(os.fstat(descriptor))
        _need(stat.S_ISREG(opened[2]) and _file_identity(opened) == _file_identity(before),
              error_prefix + "_CHANGED")
        while remaining:
            block = os.read(descriptor, min(65536, remaining))
            if not block:
                break
            chunks.append(block)
            remaining -= len(block)
        raw = b"".join(chunks)
        after_handle = _snapshot(os.fstat(descriptor))
    except CanaryError:
        raise
    except OSError:
        raise CanaryError(error_prefix + "_READ") from None
    finally:
        os.close(descriptor)

    try:
        after_stat = os.lstat(str(path))
        after_real = path.resolve(strict=True)
    except (OSError, RuntimeError, ValueError):
        raise CanaryError(error_prefix + "_CHANGED") from None
    after = _snapshot(after_stat)
    _need(after == before and os.path.normcase(str(after_real)) == os.path.normcase(str(real))
          and _file_identity(after_handle) == _file_identity(before) and len(raw) <= maximum,
          error_prefix + "_CHANGED")
    if expected_bytes is not None:
        _need(len(raw) == expected_bytes, error_prefix + "_EXPECTED_BYTES")
    digest = hashlib.sha256(raw).hexdigest()
    _need(type(expected_sha256) is str and HEX64.fullmatch(expected_sha256) is not None,
          error_prefix + "_SHA256")
    _need(digest == expected_sha256, error_prefix + "_SHA256")
    return raw, digest


def _pairs_no_duplicates(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise CanaryError("TOOL_SPECS_DUPLICATE")
        result[key] = value
    return result


def _tool_specs_from_bytes(raw):
    _need(type(raw) is bytes and raw.isascii(), "TOOL_SPECS_ASCII")
    try:
        value = json.loads(raw.decode("ascii"), object_pairs_hook=_pairs_no_duplicates,
                           parse_constant=lambda _item: (_ for _ in ()).throw(
                               CanaryError("TOOL_SPECS_NUMBER")))
    except CanaryError:
        raise
    except Exception:
        raise CanaryError("TOOL_SPECS_JSON") from None
    _need(type(value) is dict and set(value) == {"sourceSpecs", "toolSpecs"},
          "TOOL_SPECS_KEYS")
    sources = value["sourceSpecs"]
    tools = value["toolSpecs"]
    _need(type(sources) is dict and set(sources) == set(SOURCE_NAMES),
          "TOOL_SOURCE_NAMES")
    _need(type(tools) is dict and set(tools) == set(TOOL_NAMES), "TOOL_NAMES")
    for name, table, cap in ((name, sources, CALLER_MAX_BYTES) for name in SOURCE_NAMES):
        _validate_spec(name, table[name], cap, "TOOL_SOURCE_SPEC")
    for name in TOOL_NAMES:
        _validate_spec(name, tools[name], TOOL_MAX_BYTES, "TOOL_SPEC")
    return tools


def _validate_spec(name, spec, maximum, error_prefix):
    _need(type(name) is str and type(spec) is dict and
          set(spec) == {"expectedBytes", "maxBytes", "path", "sha256"},
          error_prefix + "_SHAPE")
    _need(type(spec["path"]) is str and len(spec["path"]) <= 32767,
          error_prefix + "_PATH")
    _need(type(spec["expectedBytes"]) is int and 1 <= spec["expectedBytes"] <= maximum,
          error_prefix + "_EXPECTED_BYTES")
    _need(type(spec["maxBytes"]) is int and
          spec["expectedBytes"] <= spec["maxBytes"] <= maximum,
          error_prefix + "_MAX_BYTES")
    _need(type(spec["sha256"]) is str and HEX64.fullmatch(spec["sha256"]) is not None,
          error_prefix + "_SHA256")


def _load_source_module(name, path, raw):
    module = types.ModuleType(name)
    module.__file__ = path
    module.__package__ = ""
    sys.modules[name] = module
    try:
        code = compile(raw, path, "exec", dont_inherit=True)
        exec(code, module.__dict__)
    except Exception:
        sys.modules.pop(name, None)
        raise CanaryError(name.upper() + "_SOURCE_LOAD") from None
    return module


def _read_with_caller(caller, path, maximum, expected_bytes, expected_sha256,
                      case_name):
    try:
        raw = caller._bootstrap_read(path, maximum, PHASE)
    except Exception as exc:
        code = getattr(exc, "code", None)
        caller_error = getattr(caller, "CallerError", None)
        if (isinstance(caller_error, type) and isinstance(exc, caller_error) and
                type(code) is str and re.fullmatch(r"[A-Z0-9_]{1,96}", code) is not None):
            raise CanaryError(case_name.upper() + "_" + code) from None
        raise CanaryError(case_name.upper() + "_READ") from None
    _need(type(raw) is bytes, case_name.upper() + "_RETURN_TYPE")
    _need(len(raw) == expected_bytes and len(raw) <= maximum,
          case_name.upper() + "_EXPECTED_BYTES")
    digest = hashlib.sha256(raw).hexdigest()
    _need(digest == expected_sha256, case_name.upper() + "_SHA256")
    return raw, digest


def _case_ok(name, raw, digest):
    return {"case": name, "status": "PASS", "bytes": len(raw), "sha256": digest}


def _run_positive(cases, caller, name, path, maximum, expected_raw, expected_sha256):
    try:
        raw, digest = _read_with_caller(
            caller, path, maximum, len(expected_raw), expected_sha256, name)
        _need(raw == expected_raw, name.upper() + "_BYTE_EQUALITY")
        cases.append(_case_ok(name, raw, digest))
    except CanaryError as exc:
        cases.append({"case": name, "status": "FAIL", "code": exc.code})


def _run_tool_positive(cases, caller, name, spec):
    try:
        raw, digest = _read_with_caller(
            caller, spec["path"], spec["maxBytes"], spec["expectedBytes"],
            spec["sha256"], name)
        cases.append(_case_ok(name, raw, digest))
    except CanaryError as exc:
        cases.append({"case": name, "status": "FAIL", "code": exc.code})


def _run_negative(cases, caller, name, path, maximum, expected_code):
    try:
        caller._bootstrap_read(path, maximum, PHASE)
    except Exception as exc:
        code = getattr(exc, "code", None)
        phase = getattr(exc, "phase", None)
        caller_error = getattr(caller, "CallerError", None)
        if (isinstance(caller_error, type) and isinstance(exc, caller_error) and
                code == expected_code and phase == PHASE):
            cases.append({"case": name, "status": "PASS", "code": code, "phase": phase})
        else:
            observed = code if type(code) is str and re.fullmatch(r"[A-Z0-9_]{1,96}", code) else "OTHER"
            cases.append({"case": name, "status": "FAIL", "expectedCode": expected_code,
                          "observedCode": observed,
                          "typed": bool(isinstance(caller_error, type) and
                                        isinstance(exc, caller_error))})
    else:
        cases.append({"case": name, "status": "FAIL", "expectedCode": expected_code,
                      "observedCode": "ACCEPTED"})


def _fixture_root_identity(reader, root):
    try:
        root = root.resolve(strict=True)
        temp_root = Path(tempfile.gettempdir()).resolve(strict=True)
        common = os.path.normcase(os.path.commonpath((str(temp_root), str(root))))
        _need(common == os.path.normcase(str(temp_root)), "FIXTURE_CONTAINMENT")
        _path, real, identities = reader._inspect_path(str(root), "directory")
        _need(os.path.normcase(str(real)) == os.path.normcase(str(root)), "FIXTURE_CANONICAL")
        leaf = identities[-1][1]
        identity = (leaf[0], leaf[1], stat.S_IFMT(leaf[2]), leaf[6])
        return root, temp_root, identity
    except CanaryError:
        raise
    except Exception:
        raise CanaryError("FIXTURE_CREATE") from None


def _stable_identity_from_stat(st):
    return (st.st_dev, st.st_ino, stat.S_IFMT(st.st_mode),
            getattr(st, "st_file_attributes", 0))


def _identity_record(identity):
    return {
        "device": identity[0],
        "fileId": identity[1],
        "fileKind": identity[2],
        "attributes": identity[3],
    }


def _private_retained_fixture(root, temp_root, identity):
    if root is None:
        return None
    root_text = os.path.abspath(str(root))
    if temp_root is None:
        try:
            temp_root_text = str(Path(tempfile.gettempdir()).resolve(strict=True))
        except Exception:
            temp_root_text = tempfile.gettempdir()
    else:
        temp_root_text = str(temp_root)
    if identity is None:
        try:
            identity = _stable_identity_from_stat(os.lstat(root_text))
        except OSError:
            identity = (None, None, None, None)
    return {
        "rootPath": root_text,
        "stableIdentity": _identity_record(identity),
        "tempRoot": temp_root_text,
    }


def _write_fixture_owner(root, temp_root, identity):
    owner_path = root / "OWNER.json"
    owner = {
        "schema": "tsj-selected-windows-caller-io-fixture-owner-v1",
        "rootPath": str(root),
        "tempRoot": str(temp_root),
        "rootIdentity": _identity_record(identity),
    }
    raw = (json.dumps(owner, ensure_ascii=True, sort_keys=True,
                      separators=(",", ":")) + "\n").encode("ascii")
    flags = (os.O_WRONLY | os.O_CREAT | os.O_EXCL |
             getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0))
    try:
        descriptor = os.open(str(owner_path), flags, 0o600)
    except OSError:
        raise CanaryError("FIXTURE_OWNER_CREATE") from None
    try:
        opened = os.fstat(descriptor)
        _need(stat.S_ISREG(opened.st_mode), "FIXTURE_OWNER_KIND")
        offset = 0
        while offset < len(raw):
            written = os.write(descriptor, raw[offset:offset + 4096])
            _need(type(written) is int and written > 0, "FIXTURE_OWNER_WRITE")
            offset += written
        os.fsync(descriptor)
    except CanaryError:
        raise
    except OSError:
        raise CanaryError("FIXTURE_OWNER_WRITE") from None
    finally:
        os.close(descriptor)
    try:
        owner_stat = os.lstat(str(owner_path))
        _need(stat.S_ISREG(owner_stat.st_mode) and not _is_reparse(owner_stat)
              and owner_stat.st_size == len(raw), "FIXTURE_OWNER_READBACK")
        if owner_path.read_bytes() != raw:
            raise CanaryError("FIXTURE_OWNER_READBACK")
    except CanaryError:
        raise
    except OSError:
        raise CanaryError("FIXTURE_OWNER_READBACK") from None
    return raw


def _cleanup_fixture_root(reader, root, temp_root, identity, owner_raw):
    try:
        _path, real, identities = reader._inspect_path(str(root), "directory")
        common = os.path.normcase(os.path.commonpath((str(temp_root), str(real))))
        _need(common == os.path.normcase(str(temp_root)) and
              os.path.normcase(str(real)) == os.path.normcase(str(root)), "FIXTURE_CONTAINMENT")
        leaf = identities[-1][1]
        current_identity = (leaf[0], leaf[1], stat.S_IFMT(leaf[2]), leaf[6])
        _need(current_identity == identity, "FIXTURE_IDENTITY")
        for current, directories, files in os.walk(str(root), topdown=True, followlinks=False):
            for name in directories + files:
                child = Path(current) / name
                child_abs = os.path.abspath(str(child))
                child_common = os.path.normcase(os.path.commonpath((str(root), child_abs)))
                _need(child_common == os.path.normcase(str(root)), "FIXTURE_ESCAPE")
                item = os.lstat(str(child))
                _need(not _is_reparse(item), "FIXTURE_REPARSE")
        owner_path = root / "OWNER.json"
        owner_stat = os.lstat(str(owner_path))
        _need(stat.S_ISREG(owner_stat.st_mode) and not _is_reparse(owner_stat)
              and owner_stat.st_size == len(owner_raw), "FIXTURE_OWNER_IDENTITY")
        _need(owner_path.read_bytes() == owner_raw, "FIXTURE_OWNER_CONTENT")
        shutil.rmtree(str(root))
        _need(not os.path.lexists(str(root)), "FIXTURE_ROOT_STILL_EXISTS")
    except CanaryError:
        raise
    except Exception:
        raise CanaryError("FIXTURE_CLEANUP") from None


def _run_synthetic_cases(cases, caller, reader):
    root = None
    temp_root = None
    root_identity = None
    owner_raw = None
    all_before = len(cases)
    try:
        root = Path(tempfile.mkdtemp(prefix="tsj-caller-io-canary-"))
        root, temp_root, root_identity = _fixture_root_identity(reader, root)
        owner_raw = _write_fixture_owner(root, temp_root, root_identity)
        regular = root / "regular.bin"
        payload = b"bounded synthetic bytes"
        regular.write_bytes(payload)
        _run_positive(cases, caller, "synthetic-regular-positive", str(regular),
                      len(payload), payload, hashlib.sha256(payload).hexdigest())

        over_limit = root / "over-limit.bin"
        over_limit.write_bytes(b"12345")
        _run_negative(cases, caller, "synthetic-over-limit", str(over_limit), 4,
                      "BOOTSTRAP_SIZE")

        missing = root / "missing.bin"
        _run_negative(cases, caller, "synthetic-missing", str(missing), 32,
                      "BOOTSTRAP_PATH")

        wrong_kind = root / "wrong-kind"
        wrong_kind.mkdir()
        _run_negative(cases, caller, "synthetic-wrong-kind", str(wrong_kind), 32,
                      "BOOTSTRAP_FILE_KIND")
    except CanaryError as exc:
        cases.append({"case": "synthetic-fixture-setup", "status": "FAIL", "code": exc.code})
    except Exception:
        cases.append({"case": "synthetic-fixture-setup", "status": "FAIL",
                      "code": "FIXTURE_IO"})

    fixture_cases = cases[all_before:]
    passed = (bool(fixture_cases) and
              all(case["status"] == "PASS" for case in cases))
    if root is None or temp_root is None or root_identity is None:
        status = "RETAINED_ON_SETUP_FAILURE" if root is not None else "NOT_CREATED"
        return status, _private_retained_fixture(root, temp_root, root_identity)
    if not passed:
        return "RETAINED_ON_FAILURE", _private_retained_fixture(root, temp_root, root_identity)
    try:
        _cleanup_fixture_root(reader, root, temp_root, root_identity, owner_raw)
        return "PASS", None
    except CanaryError as exc:
        cases.append({"case": "synthetic-fixture-cleanup", "status": "FAIL", "code": exc.code})
        return "RETAINED_ON_CLEANUP_FAILURE", _private_retained_fixture(
            root, temp_root, root_identity)


def _parse_args(argv):
    flags = ("--caller", "--caller-sha", "--reader", "--reader-sha", "--tool-specs")
    _need(type(argv) in (list, tuple) and len(argv) == len(flags) * 2, "CLI_ARGUMENTS")
    options = {}
    for index in range(0, len(argv), 2):
        flag, value = argv[index:index + 2]
        _need(type(flag) is str and flag in flags and flag not in options and
              type(value) is str and bool(value) and not value.startswith("--"),
              "CLI_ARGUMENTS")
        options[flag] = value
    _need(set(options) == set(flags), "CLI_ARGUMENTS")
    for flag in ("--caller-sha", "--reader-sha"):
        _need(HEX64.fullmatch(options[flag]) is not None, "CLI_SHA")
    return options


def run_canary(argv):
    options = _parse_args(argv)
    caller_path = options["--caller"]
    reader_path = options["--reader"]
    tool_specs_path = options["--tool-specs"]

    caller_raw, caller_sha = _read_authenticated_input(
        caller_path, CALLER_MAX_BYTES, options["--caller-sha"], error_prefix="CALLER_SOURCE")
    reader_raw, reader_sha = _read_authenticated_input(
        reader_path, READER_MAX_BYTES, options["--reader-sha"], error_prefix="READER_SOURCE")
    tool_specs_raw, _tool_specs_sha = _read_authenticated_input(
        tool_specs_path, TOOL_SPECS_MAX_BYTES, TOOL_SPECS_SHA256,
        expected_bytes=TOOL_SPECS_BYTES, error_prefix="TOOL_SPECS")
    tool_specs = _tool_specs_from_bytes(tool_specs_raw)

    # Only authenticated SOURCE is compiled. The distinct module names prevent
    # either source's __main__ block from running.
    caller = _load_source_module("tsj_caller_io_subject", caller_path, caller_raw)
    reader = _load_source_module("tsj_caller_io_reader", reader_path, reader_raw)
    _need(callable(getattr(caller, "_bootstrap_read", None)), "CALLER_BOOTSTRAP_API")
    _need(isinstance(getattr(caller, "CallerError", None), type), "CALLER_ERROR_API")
    _need(callable(getattr(reader, "_inspect_path", None)), "READER_PATH_API")

    cases = []
    _run_positive(cases, caller, "caller-source", caller_path, CALLER_MAX_BYTES,
                  caller_raw, caller_sha)
    _run_positive(cases, caller, "reader-source", reader_path, READER_MAX_BYTES,
                  reader_raw, reader_sha)
    _run_positive(cases, caller, "tool-specs-metadata", tool_specs_path,
                  TOOL_SPECS_MAX_BYTES, tool_specs_raw, TOOL_SPECS_SHA256)
    for name in TOOL_NAMES:
        spec = tool_specs[name]
        # Expected length and hash come from the separately SHA-pinned
        # source-metadata file, never from the measured tool bytes.
        _run_tool_positive(cases, caller, "tool-" + name, spec)
    fixture_cleanup, private_fixture = _run_synthetic_cases(cases, caller, reader)
    status = "PASS" if all(case["status"] == "PASS" for case in cases) else "FAIL"
    result = {
        "schema": "tsj-selected-windows-caller-io-canary-v1",
        "status": status,
        "phase": PHASE,
        "toolSpecsSha256": TOOL_SPECS_SHA256,
        "cases": cases,
        "fixtureCleanup": fixture_cleanup,
        "sourceModulesLoadedAfterSha": True,
        "toolBytesExecuted": False,
        "priorDataRead": False,
        "journalRead": False,
    }
    if private_fixture is not None:
        result["privateRetainedFixture"] = private_fixture
    return result


def _emit(result):
    line = json.dumps(result, ensure_ascii=True, sort_keys=True,
                      separators=(",", ":")) + "\n"
    encoded = line.encode("ascii")
    if sys.stdout.buffer.write(encoded) != len(encoded):
        return 2
    sys.stdout.buffer.flush()
    return 0 if result.get("status") == "PASS" else 2


def main(argv=None):
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        return _emit({"schema": "tsj-selected-windows-caller-io-canary-v1",
                      "status": "FAIL", "code": "ISOLATION_REQUIRED"})
    try:
        result = run_canary(sys.argv[1:] if argv is None else argv)
    except CanaryError as exc:
        result = {"schema": "tsj-selected-windows-caller-io-canary-v1",
                  "status": "FAIL", "code": exc.code, "cases": []}
    except Exception:
        result = {"schema": "tsj-selected-windows-caller-io-canary-v1",
                  "status": "FAIL", "code": "CANARY_UNEXPECTED", "cases": []}
    return _emit(result)


if __name__ == "__main__":
    raise SystemExit(main())
