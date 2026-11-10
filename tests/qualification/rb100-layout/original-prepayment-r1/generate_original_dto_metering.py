"""Pinned current-R7 Source-only original DTO metering transform.

This module derives one reviewed Java Source file from exact pinned bytes. It
never loads or executes Java and never writes from the pure API.
"""
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import stat
import sys
from typing import NamedTuple

SOURCE_CAP = 4 * 1024 * 1024
FRAGMENT_CAP = 64 * 1024
CURRENT_R7_SOURCE_SHA256 = "48f67e37f5603fdf9eade38d41cd86c95fb7171b16edc7a6f6cd8b13637e0b00"
METERED_FRAGMENT_SHA256 = "16173e6b40e11efb2c517a99a424fadd53cfeeb549a170682f5ed473a1cdf702"
EXPECTED_OUTPUT_SHA256 = "f25010169adb2e4934a1303dc076cb948ba5f0c7160603a1fecd6b060e916541"
EXPECTED_OUTPUT_BYTES = 235950
EXPECTED_OLD_SPAN_BYTES = 3947
EXPECTED_OLD_SPAN_SHA256 = "0435e8b21f22979554da6b33574a050b3a75fbae13c19abb084842c7721c3c68"

INPUT_STUDY_NAME = "CohortTerminalTelemetryStudy.java"
INPUT_FRAGMENT_NAME = "OriginalDtoMetering.java.fragment"
OUTPUT_STUDY_NAME = "CohortTerminalTelemetryStudy.java"

_ANNOTATION_AND_METHOD = (
    b'    @SuppressWarnings("unchecked")\n'
    b'    static Map<String,Object> originalDto('
)
_METHOD_DECLARATION = b"    static Map<String,Object> originalDto("
_HELPER_DECLARATION = (
    b"    private static Object originalDtoMapRead("
    b"SetupMeter m,java.util.Map<?,?> source,String key) throws Exception {"
)
_METHOD_END = b"        return out;\n    }"
_PAD_PROOF_DECLARATION = b"    static final class PadProof {"
_REPARSE_POINT = 0x400


class GenerationError(Exception):
    """A stable, path-free error code from derivation or the CLI."""

    def __init__(self, code):
        self.code = code
        super().__init__(code)


class GenerationResult(NamedTuple):
    study_source: bytes
    provenance_json: bytes


@dataclass(frozen=True)
class _DirectoryGuard:
    path: Path
    entries: tuple
    role: str

    def verify(self):
        for path_text, expected in self.entries:
            try:
                current = os.lstat(path_text)
            except OSError:
                raise GenerationError(self.role + "_CHANGED") from None
            if _is_reparse(current) or not stat.S_ISDIR(current.st_mode):
                raise GenerationError(self.role + "_REPARSE")
            if _directory_identity(current) != expected:
                raise GenerationError(self.role + "_CHANGED")


@dataclass(frozen=True)
class _InputGuard:
    path: Path
    path_signature: tuple
    directory_guard: _DirectoryGuard
    role: str

    def verify(self):
        self.directory_guard.verify()
        try:
            resolved = self.path.resolve(strict=True)
            current = os.lstat(self.path)
        except OSError:
            raise GenerationError(self.role + "_CHANGED") from None
        if _normalized(str(resolved)) != _normalized(str(self.path)):
            raise GenerationError(self.role + "_REPARSE")
        if _is_reparse(current):
            raise GenerationError(self.role + "_REPARSE")
        if not stat.S_ISREG(current.st_mode) or current.st_nlink != 1:
            raise GenerationError(self.role + "_CHANGED")
        if _path_signature(current) != self.path_signature:
            raise GenerationError(self.role + "_CHANGED")


def _require(condition, code):
    if not condition:
        raise GenerationError(code)


def _sha256(raw):
    return hashlib.sha256(raw).hexdigest()


def _normalized(path_text):
    return os.path.normcase(os.path.normpath(os.path.abspath(path_text)))


def _is_reparse(entry):
    attrs = getattr(entry, "st_file_attributes", 0) or 0
    return stat.S_ISLNK(entry.st_mode) or bool(attrs & _REPARSE_POINT)


def _cross_path_handle_identity(entry):
    # Path stat and handle fstat can report different ctime/birth-time values
    # on Windows. Compare only the metadata common to both APIs here.
    return (
        entry.st_dev,
        entry.st_ino,
        entry.st_mode,
        entry.st_nlink,
        entry.st_size,
        entry.st_mtime_ns,
        getattr(entry, "st_file_attributes", 0) or 0,
    )


def _path_signature(entry):
    # Compare lstat snapshots with each other, retaining same-API time fields.
    return (
        _cross_path_handle_identity(entry),
        getattr(entry, "st_ctime_ns", None),
        getattr(entry, "st_ctime", None),
        getattr(entry, "st_birthtime_ns", None),
        getattr(entry, "st_birthtime", None),
    )


def _handle_signature(entry):
    # Compare fstat snapshots with each other, retaining same-API time fields.
    return (
        _cross_path_handle_identity(entry),
        getattr(entry, "st_ctime_ns", None),
        getattr(entry, "st_ctime", None),
        getattr(entry, "st_birthtime_ns", None),
        getattr(entry, "st_birthtime", None),
    )


def _directory_identity(entry):
    # Directory content changes (including the output file creation) do not
    # change this identity snapshot.
    return (
        entry.st_dev,
        entry.st_ino,
        stat.S_IFMT(entry.st_mode),
        getattr(entry, "st_file_attributes", 0) or 0,
    )


def _path_chain(path):
    return list(reversed(path.parents)) + [path]


def _directory_guard(path_value, role):
    try:
        path = Path(os.path.abspath(os.fspath(path_value)))
        resolved = path.resolve(strict=True)
    except FileNotFoundError:
        raise GenerationError(role + "_MISSING") from None
    except (OSError, TypeError, ValueError):
        raise GenerationError(role + "_PATH") from None

    if _normalized(str(resolved)) != _normalized(str(path)):
        raise GenerationError(role + "_REPARSE")

    entries = []
    for item in _path_chain(path):
        try:
            entry = os.lstat(item)
        except FileNotFoundError:
            raise GenerationError(role + "_MISSING") from None
        except OSError:
            raise GenerationError(role + "_PATH") from None
        if _is_reparse(entry):
            raise GenerationError(role + "_REPARSE")
        if not stat.S_ISDIR(entry.st_mode):
            raise GenerationError(role + "_PATH")
        entries.append((str(item), _directory_identity(entry)))
    return _DirectoryGuard(path, tuple(entries), role)


def _canonical_input_path(path_value, role):
    try:
        path = Path(os.path.abspath(os.fspath(path_value)))
        resolved = path.resolve(strict=True)
    except FileNotFoundError:
        raise GenerationError(role + "_MISSING") from None
    except (OSError, TypeError, ValueError):
        raise GenerationError(role + "_IO") from None
    if _normalized(str(resolved)) != _normalized(str(path)):
        raise GenerationError(role + "_REPARSE")
    return path


def _read_stable_file(path_value, role, cap):
    path = _canonical_input_path(path_value, role)
    parent_guard = _directory_guard(path.parent, role)
    try:
        before_path = os.lstat(path)
    except FileNotFoundError:
        raise GenerationError(role + "_MISSING") from None
    except OSError:
        raise GenerationError(role + "_IO") from None

    if _is_reparse(before_path):
        raise GenerationError(role + "_REPARSE")
    if not stat.S_ISREG(before_path.st_mode):
        raise GenerationError(role + "_NOT_REGULAR")
    if before_path.st_nlink != 1:
        raise GenerationError(role + "_LINKED")
    if before_path.st_size <= 0 or before_path.st_size > cap:
        raise GenerationError(role + "_SIZE")

    flags = os.O_RDONLY
    flags |= getattr(os, "O_BINARY", 0)
    flags |= getattr(os, "O_NOFOLLOW", 0)
    flags |= getattr(os, "O_NONBLOCK", 0)
    try:
        descriptor = os.open(path, flags)
    except FileNotFoundError:
        raise GenerationError(role + "_MISSING") from None
    except OSError:
        raise GenerationError(role + "_IO") from None

    result = None
    pending_error = None
    try:
        try:
            before_handle = os.fstat(descriptor)
            if _is_reparse(before_handle) or not stat.S_ISREG(before_handle.st_mode):
                raise GenerationError(role + "_REPARSE")
            if before_handle.st_nlink != 1:
                raise GenerationError(role + "_LINKED")
            if (_cross_path_handle_identity(before_handle)
                    != _cross_path_handle_identity(before_path)):
                raise GenerationError(role + "_CHANGED")

            chunks = []
            total = 0
            while total <= cap:
                chunk = os.read(descriptor, min(65536, cap + 1 - total))
                if not chunk:
                    break
                chunks.append(chunk)
                total += len(chunk)
            if total > cap:
                raise GenerationError(role + "_SIZE")
            raw = b"".join(chunks)

            after_handle = os.fstat(descriptor)
            after_path = os.lstat(path)
            if (_handle_signature(after_handle)
                    != _handle_signature(before_handle)
                    or _path_signature(after_path)
                    != _path_signature(before_path)):
                raise GenerationError(role + "_CHANGED")
            parent_guard.verify()
            if len(raw) != before_path.st_size:
                raise GenerationError(role + "_CHANGED")
            guard = _InputGuard(path, _path_signature(before_path), parent_guard, role)
            result = (raw, guard)
        except GenerationError as exc:
            pending_error = exc
        except FileNotFoundError:
            pending_error = GenerationError(role + "_CHANGED")
        except OSError:
            pending_error = GenerationError(role + "_IO")
        except Exception as exc:
            pending_error = exc
    finally:
        try:
            os.close(descriptor)
        except Exception:
            if pending_error is None:
                pending_error = GenerationError(role + "_CLOSE")

    if pending_error is not None:
        raise pending_error
    return result


def _unique_index(raw, token, code):
    first = raw.find(token)
    if first < 0 or raw.find(token, first + 1) >= 0:
        raise GenerationError(code)
    return first


def _method_bytes(raw, code):
    _require(raw.count(_METHOD_END) == 1, code)
    end = raw.find(_METHOD_END) + len(_METHOD_END)
    return raw[:end], end


def _canonical_json(value):
    return (json.dumps(
        value,
        ensure_ascii=True,
        sort_keys=True,
        separators=(",", ":"),
    ) + "\n").encode("ascii")


def derive_original_dto_metering(*, study_source, metered_fragment_source):
    """Return the exact pinned Source transform and canonical provenance bytes."""
    if type(study_source) is not bytes:
        raise GenerationError("SOURCE_TYPE")
    if type(metered_fragment_source) is not bytes:
        raise GenerationError("FRAGMENT_TYPE")
    if not 0 < len(study_source) <= SOURCE_CAP:
        raise GenerationError("SOURCE_SIZE")
    if not 0 < len(metered_fragment_source) <= FRAGMENT_CAP:
        raise GenerationError("FRAGMENT_SIZE")

    source_sha = _sha256(study_source)
    fragment_sha = _sha256(metered_fragment_source)
    if source_sha != CURRENT_R7_SOURCE_SHA256:
        raise GenerationError("SOURCE_SHA256")
    if fragment_sha != METERED_FRAGMENT_SHA256:
        raise GenerationError("FRAGMENT_SHA256")

    # Pin authentication happens before either byte string is decoded.
    try:
        study_source.decode("utf-8", "strict")
    except UnicodeDecodeError:
        raise GenerationError("SOURCE_UTF8") from None
    try:
        metered_fragment_source.decode("utf-8", "strict")
    except UnicodeDecodeError:
        raise GenerationError("FRAGMENT_UTF8") from None
    if b"\r" in study_source or b"\x00" in study_source:
        raise GenerationError("SOURCE_STRUCTURE")
    if b"\r" in metered_fragment_source or b"\x00" in metered_fragment_source:
        raise GenerationError("FRAGMENT_STRUCTURE")

    start = _unique_index(
        study_source, _ANNOTATION_AND_METHOD, "SOURCE_BOUNDARY"
    )
    end = _unique_index(study_source, _PAD_PROOF_DECLARATION, "SOURCE_BOUNDARY")
    if end <= start:
        raise GenerationError("SOURCE_BOUNDARY")
    old_span = study_source[start:end]
    if (len(old_span) != EXPECTED_OLD_SPAN_BYTES
            or _sha256(old_span) != EXPECTED_OLD_SPAN_SHA256
            or not old_span.endswith(b"    }\n\n")):
        raise GenerationError("SOURCE_BOUNDARY")
    old_method, _ = _method_bytes(old_span, "SOURCE_METHOD")

    fragment = metered_fragment_source
    if (not fragment.startswith(_ANNOTATION_AND_METHOD)
            or fragment.count(_METHOD_DECLARATION) != 1
            or not fragment.endswith(b"    }")):
        raise GenerationError("FRAGMENT_STRUCTURE")
    helper_start = _unique_index(
        fragment, _HELPER_DECLARATION, "FRAGMENT_STRUCTURE"
    )
    new_method, method_end = _method_bytes(fragment, "FRAGMENT_METHOD")
    if method_end >= helper_start or fragment[method_end:helper_start] != b"\n\n":
        raise GenerationError("FRAGMENT_STRUCTURE")
    helper = fragment[helper_start:]

    # These Source counts bind the authored fragment to the frozen logical census.
    if (new_method.count(b"r7ReadObject(m,") != 25
            or new_method.count(b"originalDtoMapRead(m,") != 14
            or new_method.count(b"m.charge(1);") != 8
            or new_method.count(b"m.charge(2);") != 1
            or new_method.count(b"m.copy(60);") != 1
            or new_method.count(b"m.copy(45);") != 1
            or new_method.count(b"new java.util.ArrayList<String>(") != 1
            or new_method.count(b"new java.util.LinkedHashMap<String,Object>()") != 7
            or b"m.finish(" in new_method):
        raise GenerationError("FRAGMENT_CENSUS")
    if (helper.count(b"m.charge(1);") != 1
            or helper.count(b"source.get(key)") != 1
            or helper.count(b"ORIGINAL_DTO_SOURCE_MAP_MISSING") != 1):
        raise GenerationError("FRAGMENT_MAP_HELPER")

    replacement = fragment.rstrip(b"\n") + b"\n\n"
    emitted = study_source[:start] + replacement + study_source[end:]
    if len(emitted) != EXPECTED_OUTPUT_BYTES:
        raise GenerationError("OUTPUT_SIZE")
    emitted_sha = _sha256(emitted)
    if emitted_sha != EXPECTED_OUTPUT_SHA256:
        raise GenerationError("OUTPUT_SHA256")
    prefix = study_source[:start]
    suffix = study_source[end:]
    if not emitted.startswith(prefix) or not emitted.endswith(suffix):
        raise GenerationError("OUTPUT_CONSERVATION")

    proof = {
        "format": "TSJ_ORIGINAL_DTO_METERING_SOURCE_PROVENANCE_R1",
        "status": "PASS_SOURCE_TRANSFORM_ONLY",
        "inputs": [
            {
                "role": "current_r7_study_source",
                "bytes": len(study_source),
                "sha256": source_sha,
            },
            {
                "role": "metered_fragment",
                "bytes": len(metered_fragment_source),
                "sha256": fragment_sha,
            },
        ],
        "methods": {
            "originalDto_before": {
                "bytes": len(old_method),
                "sha256": _sha256(old_method),
            },
            "originalDto_after": {
                "bytes": len(new_method),
                "sha256": _sha256(new_method),
            },
            "originalDtoMapRead": {
                "bytes": len(helper),
                "sha256": _sha256(helper),
            },
        },
        "conservedOutside": {
            "prefixBytes": len(prefix),
            "prefixSha256": _sha256(prefix),
            "suffixBytes": len(suffix),
            "suffixSha256": _sha256(suffix),
            "totalBytes": len(prefix) + len(suffix),
            "joinedSha256": _sha256(prefix + suffix),
        },
        "sourceOnlyPrice": {
            "logicalUnits": 154,
            "fieldReads": 25,
            "mapReads": 14,
            "containers": 8,
            "copySlots": 105,
            "copyChunks": 2,
            "finishCalls": 0,
        },
        "output": {
            "role": OUTPUT_STUDY_NAME,
            "bytes": len(emitted),
            "sha256": emitted_sha,
        },
    }
    return GenerationResult(emitted, _canonical_json(proof))


def _parse_cli(argv):
    values = {}
    allowed = {
        "--source-root": "source_root",
        "--component-root": "component_root",
        "--output-root": "output_root",
    }
    if len(argv) != 6:
        raise GenerationError("CLI_ARGUMENTS")
    for index in range(0, 6, 2):
        key = argv[index]
        value = argv[index + 1]
        if key not in allowed or allowed[key] in values or not value:
            raise GenerationError("CLI_ARGUMENTS")
        values[allowed[key]] = value
    if len(values) != 3:
        raise GenerationError("CLI_ARGUMENTS")
    return values


def _write_exclusive(path, raw):
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    flags |= getattr(os, "O_BINARY", 0)
    flags |= getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(path, flags, 0o600)
    except FileExistsError:
        raise GenerationError("OUTPUT_EXISTS") from None
    except OSError:
        raise GenerationError("OUTPUT_WRITE") from None

    pending_error = None
    try:
        try:
            entry = os.fstat(descriptor)
            if _is_reparse(entry) or not stat.S_ISREG(entry.st_mode):
                raise GenerationError("OUTPUT_WRITE")
            if entry.st_nlink != 1:
                raise GenerationError("OUTPUT_WRITE")
            offset = 0
            while offset < len(raw):
                written = os.write(descriptor, raw[offset:])
                if written <= 0:
                    raise GenerationError("OUTPUT_WRITE")
                offset += written
            os.fsync(descriptor)
            after = os.fstat(descriptor)
            if (not stat.S_ISREG(after.st_mode) or after.st_nlink != 1
                    or after.st_size != len(raw)):
                raise GenerationError("OUTPUT_WRITE")
        except GenerationError as exc:
            pending_error = exc
        except Exception:
            pending_error = GenerationError("OUTPUT_WRITE")
    finally:
        try:
            os.close(descriptor)
        except Exception:
            if pending_error is None:
                pending_error = GenerationError("OUTPUT_CLOSE")

    if pending_error is not None:
        raise pending_error


def _write_stderr_code(code):
    try:
        sys.stderr.write(code + "\n")
        sys.stderr.flush()
    except Exception:
        pass


def main(argv=None):
    try:
        options = _parse_cli(list(sys.argv[1:] if argv is None else argv))
        source_path = Path(options["source_root"]) / INPUT_STUDY_NAME
        fragment_path = Path(options["component_root"]) / INPUT_FRAGMENT_NAME
        source_bytes, source_guard = _read_stable_file(
            source_path, "SOURCE", SOURCE_CAP
        )
        fragment_bytes, fragment_guard = _read_stable_file(
            fragment_path, "FRAGMENT", FRAGMENT_CAP
        )
        result = derive_original_dto_metering(
            study_source=source_bytes,
            metered_fragment_source=fragment_bytes,
        )

        output_guard = _directory_guard(options["output_root"], "OUTPUT")
        output_path = output_guard.path / OUTPUT_STUDY_NAME
        if os.path.lexists(output_path):
            raise GenerationError("OUTPUT_EXISTS")

        # Revalidate all consumed paths immediately before the exclusive create.
        source_guard.verify()
        fragment_guard.verify()
        output_guard.verify()
        _write_exclusive(output_path, result.study_source)

        # The stdout manifest is emitted only after the Java file is closed and
        # its bytes have been read back through the same stable-path checks.
        output_guard.verify()
        written, _ = _read_stable_file(output_path, "OUTPUT", SOURCE_CAP)
        if written != result.study_source or _sha256(written) != EXPECTED_OUTPUT_SHA256:
            raise GenerationError("OUTPUT_WRITE")
        stream = getattr(sys.stdout, "buffer", None)
        if stream is None:
            raise GenerationError("OUTPUT_WRITE")
        manifest_written = stream.write(result.provenance_json)
        if manifest_written != len(result.provenance_json):
            raise GenerationError("OUTPUT_WRITE")
        stream.flush()
        return 0
    except GenerationError as exc:
        _write_stderr_code(exc.code)
        return 2
    except Exception:
        _write_stderr_code("INTERNAL_ERROR")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
