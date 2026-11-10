"""Pinned Source-only current-arm metadata read metering transformer and CLI."""

from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import stat
import sys
import types
from typing import NamedTuple


SOURCE_CAP = 4 * 1024 * 1024
FRAGMENT_CAP = 64 * 1024
IO_SOURCE_CAP = 4 * 1024 * 1024

STUDY_SOURCE_BYTES = 235950
STUDY_SOURCE_SHA256 = "f25010169adb2e4934a1303dc076cb948ba5f0c7160603a1fecd6b060e916541"
METERED_FRAGMENT_SHA256 = "5f5a569b10f3579de8f700e84496c8762450531e986e732e338ec79133c60222"
EXPECTED_OUTPUT_BYTES = 236944
EXPECTED_OUTPUT_SHA256 = "90f4c227d125587a7d1c8d3b324fe7fd375fd89af7a8a34a83a65a3c0d310bad"

IO_SOURCE_NAME = "generate_original_dto_metering.py"
IO_SOURCE_BYTES = 20512
IO_SOURCE_SHA256 = "d4d7c547037f466d2b2fc0217c0048584e20fed06ff035414b0dbe4fd6fc69a6"
INPUT_STUDY_NAME = "CohortTerminalTelemetryStudy.java"
INPUT_FRAGMENT_NAME = "CurrentArmReadMetering.java.fragment"
OUTPUT_STUDY_NAME = "CohortTerminalTelemetryStudy.java"

_REPARSE_POINT = 0x400
_SECTION_PREFIX = b"// TSJ_ARM_READ_SECTION "
_SECTION_END = b"// TSJ_ARM_READ_END"
_SECTION_NAMES = (
    "executeArmWithProfile",
    "containsMetered",
    "armListRead",
    "validateObservation",
    "r9CanaryArmDto",
)

_OLD_SPANS = (
    ("executeArmWithProfile", 61964, 65145, 3181,
     "fa54d5b0bf1c8657a731a8085978d2d29306dcc935c4a9997cea2b87b385e527", 2,
     b'@SuppressWarnings("unchecked")\nprivate static ArmResult executeArmWithProfile(',
     b"private static ArmResult executeArmWithProfile("),
    ("containsMetered", 76827, 77130, 303,
     "dc5205492d20d0a9240236b1bbd511130f086307501a367f434d7b15f9985b45", 1,
     b"    static boolean containsMetered(",
     b"static boolean containsMetered("),
    ("validateObservation", 77130, 85525, 8395,
     "c0d3ba45d1c3f5198934e088a2e472dfcda1360b133342566d0d41658f845cba", 1,
     b'    @SuppressWarnings("unchecked")\n    static void validateObservation(',
     b"static void validateObservation("),
    ("r9CanaryArmDto", 234018, 235043, 1025,
     "44155741db6a61d8cfd6bda0431fe30c5b20fff651af3eb645fcc5c3349856bc", 1,
     b"private static Map<String,Object> r9CanaryArmDto(",
     b"private static Map<String,Object> r9CanaryArmDto("),
)

_SECTION_PREFIXES = {
    "executeArmWithProfile":
        b'@SuppressWarnings("unchecked")\nprivate static ArmResult executeArmWithProfile(',
    "containsMetered":
        b"    static boolean containsMetered(",
    "armListRead":
        b"    private static <T> T armListRead(",
    "validateObservation":
        b'    @SuppressWarnings("unchecked")\n    static void validateObservation(',
    "r9CanaryArmDto":
        b"private static Map<String,Object> r9CanaryArmDto(",
}
_SECTION_SIGNATURES = {
    "executeArmWithProfile": b"private static ArmResult executeArmWithProfile(",
    "containsMetered": b"static boolean containsMetered(",
    "armListRead": b"private static <T> T armListRead(",
    "validateObservation": b"static void validateObservation(",
    "r9CanaryArmDto": b"private static Map<String,Object> r9CanaryArmDto(",
}


class GenerationError(Exception):
    """Stable code-only failures for the pure API and bootstrap."""

    def __init__(self, code):
        self.code = code
        super().__init__(code)


class GenerationResult(NamedTuple):
    study_source: bytes
    provenance_json: bytes


@dataclass(frozen=True)
class _BootstrapGuard:
    path: Path
    path_signature: tuple
    directories: tuple
    role: str

    def verify(self):
        _verify_directory_entries(self.directories, self.role)
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


def _sha256(raw):
    return hashlib.sha256(raw).hexdigest()


def _require(ok, code):
    if not ok:
        raise GenerationError(code)


def _normalized(path_text):
    return os.path.normcase(os.path.normpath(os.path.abspath(path_text)))


def _is_reparse(entry):
    attrs = getattr(entry, "st_file_attributes", 0) or 0
    return stat.S_ISLNK(entry.st_mode) or bool(attrs & _REPARSE_POINT)


def _cross_path_handle_identity(entry):
    # Path stat and handle fstat can differ in ctime/birth-time on Windows.
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
    return (
        _cross_path_handle_identity(entry),
        getattr(entry, "st_ctime_ns", None),
        getattr(entry, "st_ctime", None),
        getattr(entry, "st_birthtime_ns", None),
        getattr(entry, "st_birthtime", None),
    )


def _handle_signature(entry):
    return (
        _cross_path_handle_identity(entry),
        getattr(entry, "st_ctime_ns", None),
        getattr(entry, "st_ctime", None),
        getattr(entry, "st_birthtime_ns", None),
        getattr(entry, "st_birthtime", None),
    )


def _directory_identity(entry):
    # Directory content and timestamps are intentionally excluded.
    return (
        entry.st_dev,
        entry.st_ino,
        stat.S_IFMT(entry.st_mode),
        getattr(entry, "st_file_attributes", 0) or 0,
    )


def _path_chain(path):
    return list(reversed(path.parents)) + [path]


def _strict_existing_path(path_value, role):
    try:
        path = Path(os.path.abspath(os.fspath(path_value)))
        resolved = path.resolve(strict=True)
    except FileNotFoundError:
        raise GenerationError(role + "_MISSING") from None
    except (OSError, TypeError, ValueError):
        raise GenerationError(role + "_PATH") from None
    if _normalized(str(resolved)) != _normalized(str(path)):
        raise GenerationError(role + "_REPARSE")
    return path


def _directory_entries(path_value, role):
    path = _strict_existing_path(path_value, role)
    entries = []
    for item in _path_chain(path):
        try:
            current = os.lstat(item)
        except FileNotFoundError:
            raise GenerationError(role + "_MISSING") from None
        except OSError:
            raise GenerationError(role + "_PATH") from None
        if _is_reparse(current):
            raise GenerationError(role + "_REPARSE")
        if not stat.S_ISDIR(current.st_mode):
            raise GenerationError(role + "_PATH")
        entries.append((str(item), _directory_identity(current)))
    return tuple(entries)


def _verify_directory_entries(entries, role):
    for path_text, expected in entries:
        try:
            current = os.lstat(path_text)
        except OSError:
            raise GenerationError(role + "_CHANGED") from None
        if _is_reparse(current) or not stat.S_ISDIR(current.st_mode):
            raise GenerationError(role + "_REPARSE")
        if _directory_identity(current) != expected:
            raise GenerationError(role + "_CHANGED")


def _bootstrap_read_io_source(path_value):
    """Bounded no-follow bootstrap; no external module is loaded before its pin."""
    role = "IO_SOURCE"
    path = _strict_existing_path(path_value, role)
    directories = _directory_entries(path.parent, role)
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
    if before_path.st_size != IO_SOURCE_BYTES or before_path.st_size > IO_SOURCE_CAP:
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

    raw = None
    pending_error = None
    try:
        try:
            before_handle = os.fstat(descriptor)
            if _is_reparse(before_handle) or not stat.S_ISREG(before_handle.st_mode):
                raise GenerationError(role + "_REPARSE")
            if before_handle.st_nlink != 1:
                raise GenerationError(role + "_LINKED")
            if (_cross_path_handle_identity(before_path)
                    != _cross_path_handle_identity(before_handle)):
                raise GenerationError(role + "_CHANGED")

            chunks = []
            total = 0
            while total <= IO_SOURCE_CAP:
                chunk = os.read(descriptor, min(65536, IO_SOURCE_CAP + 1 - total))
                if not chunk:
                    break
                chunks.append(chunk)
                total += len(chunk)
            if total != IO_SOURCE_BYTES or total > IO_SOURCE_CAP:
                raise GenerationError(role + "_SIZE")
            raw = b"".join(chunks)

            after_handle = os.fstat(descriptor)
            after_path = os.lstat(path)
            if (_handle_signature(after_handle) != _handle_signature(before_handle)
                    or _path_signature(after_path) != _path_signature(before_path)):
                raise GenerationError(role + "_CHANGED")
            _verify_directory_entries(directories, role)
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
    guard = _BootstrapGuard(path, _path_signature(before_path), directories, role)
    return raw, guard


def _load_pinned_io_module(raw):
    """Compile and exec only the already-authenticated fixed owner source bytes."""
    private_module = types.ModuleType("_tsj_original_dto_metering_owner_private_r1")
    private_module.__file__ = "<pinned-generate_original_dto_metering.py>"
    try:
        compiled = compile(raw, IO_SOURCE_NAME, "exec")
        exec(compiled, private_module.__dict__)
    except Exception:
        raise GenerationError("IO_SOURCE_EXEC") from None
    required = (
        "_read_stable_file",
        "_directory_guard",
        "_write_exclusive",
        "derive_original_dto_metering",
        "GenerationError",
    )
    if any(not hasattr(private_module, name) for name in required):
        raise GenerationError("IO_SOURCE_API")
    return private_module


def _unique_index(raw, token, code):
    first = raw.find(token)
    if first < 0 or raw.find(token, first + 1) >= 0:
        raise GenerationError(code)
    return first


def _matching_brace_end(raw, open_brace, code):
    if open_brace < 0 or raw[open_brace:open_brace + 1] != b"{":
        raise GenerationError(code)
    depth = 0
    state = 0
    index = open_brace
    while index < len(raw):
        current = raw[index]
        following = raw[index + 1] if index + 1 < len(raw) else 0
        if state == 1:
            if current == 0x0A:
                state = 0
        elif state == 2:
            if current == 0x2A and following == 0x2F:
                state = 0
                index += 1
        elif state == 3 or state == 4:
            quote = 0x22 if state == 3 else 0x27
            if current == 0x5C:
                index += 1
            elif current == quote:
                state = 0
        else:
            if current == 0x2F and following == 0x2F:
                state = 1
                index += 1
            elif current == 0x2F and following == 0x2A:
                state = 2
                index += 1
            elif current == 0x22:
                state = 3
            elif current == 0x27:
                state = 4
            elif current == 0x7B:
                depth += 1
            elif current == 0x7D:
                depth -= 1
                if depth == 0:
                    return index + 1
                if depth < 0:
                    raise GenerationError(code)
        index += 1
    raise GenerationError(code)


def _method_end(raw, signature, code):
    declaration = _unique_index(raw, signature, code)
    opening = raw.find(b"{", declaration + len(signature))
    return _matching_brace_end(raw, opening, code)


def _read_old_spans(study_source):
    spans = {}
    for (name, start, end, byte_count, expected_sha, trailing_lf,
         start_marker, signature) in _OLD_SPANS:
        if (study_source.count(start_marker) != 1
                or study_source[start:start + len(start_marker)] != start_marker):
            raise GenerationError("SOURCE_STRUCTURE")
        old = study_source[start:end]
        if (len(old) != byte_count or _sha256(old) != expected_sha):
            raise GenerationError("SOURCE_SPAN")
        method_end = _method_end(old, signature, "SOURCE_STRUCTURE")
        tail = old[method_end:]
        if tail != b"\n" * trailing_lf:
            raise GenerationError("SOURCE_STRUCTURE")
        spans[name] = {
            "start": start,
            "end": end,
            "old": old,
            "oldMethod": old[:method_end],
            "tail": tail,
            "trailingLf": trailing_lf,
        }
    return spans


def _parse_fragment_sections(fragment_source):
    if fragment_source.count(_SECTION_PREFIX) != len(_SECTION_NAMES):
        raise GenerationError("FRAGMENT_STRUCTURE")
    if fragment_source.count(_SECTION_END) != 1 or not fragment_source.endswith(_SECTION_END):
        raise GenerationError("FRAGMENT_STRUCTURE")

    sections = {}
    cursor = 0
    for index, name in enumerate(_SECTION_NAMES):
        marker = _SECTION_PREFIX + name.encode("ascii") + b"\n"
        if fragment_source.count(marker) != 1 or not fragment_source.startswith(marker, cursor):
            raise GenerationError("FRAGMENT_STRUCTURE")
        body_start = cursor + len(marker)
        next_marker = (
            _SECTION_PREFIX + _SECTION_NAMES[index + 1].encode("ascii") + b"\n"
            if index + 1 < len(_SECTION_NAMES)
            else _SECTION_END
        )
        next_position = fragment_source.find(next_marker, body_start)
        if next_position < 0:
            raise GenerationError("FRAGMENT_STRUCTURE")
        sections[name] = fragment_source[body_start:next_position]
        cursor = next_position

    if fragment_source[cursor:] != _SECTION_END:
        raise GenerationError("FRAGMENT_STRUCTURE")
    return sections


def _section_method_body(raw_section, name):
    body = raw_section.rstrip(b"\n")
    prefix = _SECTION_PREFIXES[name]
    signature = _SECTION_SIGNATURES[name]
    if not body.startswith(prefix) or body.count(signature) != 1:
        raise GenerationError("FRAGMENT_STRUCTURE")
    end = _method_end(body, signature, "FRAGMENT_STRUCTURE")
    if end != len(body):
        raise GenerationError("FRAGMENT_STRUCTURE")
    return body


def _canonical_json(value):
    return (json.dumps(
        value,
        ensure_ascii=True,
        sort_keys=True,
        separators=(",", ":"),
    ) + "\n").encode("ascii")


def derive_current_arm_reads(*, study_source, metered_fragment_source):
    """Pure pinned Source transform. No filesystem access or owner execution."""
    if type(study_source) is not bytes:
        raise GenerationError("SOURCE_TYPE")
    if type(metered_fragment_source) is not bytes:
        raise GenerationError("FRAGMENT_TYPE")
    if not 0 < len(study_source) <= SOURCE_CAP:
        raise GenerationError("SOURCE_SIZE")
    if not 0 < len(metered_fragment_source) <= FRAGMENT_CAP:
        raise GenerationError("FRAGMENT_SIZE")
    study_sha = _sha256(study_source)
    if len(study_source) != STUDY_SOURCE_BYTES or study_sha != STUDY_SOURCE_SHA256:
        raise GenerationError("SOURCE_SHA256")
    if (METERED_FRAGMENT_SHA256 == "ROOT_PIN_PENDING"
            or EXPECTED_OUTPUT_SHA256 == "ROOT_PIN_PENDING"
            or EXPECTED_OUTPUT_BYTES == "ROOT_PIN_PENDING"):
        raise GenerationError("PINS_PENDING")
    fragment_sha = _sha256(metered_fragment_source)
    if fragment_sha != METERED_FRAGMENT_SHA256:
        raise GenerationError("FRAGMENT_SHA256")

    # All pins and byte-level shape checks precede decoding.
    if b"\r" in study_source or b"\x00" in study_source:
        raise GenerationError("SOURCE_STRUCTURE")
    if b"\r" in metered_fragment_source or b"\x00" in metered_fragment_source:
        raise GenerationError("FRAGMENT_STRUCTURE")
    spans = _read_old_spans(study_source)
    raw_sections = _parse_fragment_sections(metered_fragment_source)
    sections = {
        name: _section_method_body(raw_sections[name], name)
        for name in _SECTION_NAMES
    }
    try:
        study_source.decode("utf-8", "strict")
    except UnicodeDecodeError:
        raise GenerationError("SOURCE_UTF8") from None
    try:
        metered_fragment_source.decode("utf-8", "strict")
    except UnicodeDecodeError:
        raise GenerationError("FRAGMENT_UTF8") from None

    replacement_by_name = {}
    for name, *_ in _OLD_SPANS:
        body = sections[name].rstrip(b"\n")
        replacement_by_name[name] = body + spans[name]["tail"]
    replacement_by_name["containsMetered"] = (
        sections["containsMetered"].rstrip(b"\n")
        + b"\n\n"
        + sections["armListRead"].rstrip(b"\n")
        + spans["containsMetered"]["tail"]
    )

    sorted_spans = sorted(_OLD_SPANS, key=lambda row: row[1])
    output_parts = []
    outside_parts = []
    outside_records = []
    cursor = 0
    method_records = {}
    for (name, start, end, _byte_count, _expected_sha, _trailing_lf,
         _start_marker, _signature) in sorted_spans:
        outside = study_source[cursor:start]
        outside_parts.append(outside)
        outside_records.append({
            "beforeMethod": name,
            "bytes": len(outside),
            "sha256": _sha256(outside),
        })
        output_parts.extend((outside, replacement_by_name[name]))
        method_records[name] = {
            "before": {
                "bytes": len(spans[name]["oldMethod"]),
                "sha256": _sha256(spans[name]["oldMethod"]),
            },
            "after": {
                "bytes": len(sections[name].rstrip(b"\n")),
                "sha256": _sha256(sections[name].rstrip(b"\n")),
            },
            "oldSpanBytes": len(spans[name]["old"]),
            "oldSpanSha256": _sha256(spans[name]["old"]),
            "oldTrailingLf": spans[name]["trailingLf"],
            "replacementBytes": len(replacement_by_name[name]),
            "replacementSha256": _sha256(replacement_by_name[name]),
        }
        cursor = end
    final_outside = study_source[cursor:]
    outside_parts.append(final_outside)
    outside_records.append({
        "afterMethod": _OLD_SPANS[-1][0],
        "bytes": len(final_outside),
        "sha256": _sha256(final_outside),
    })
    output_parts.append(final_outside)
    emitted = b"".join(output_parts)
    outside_bytes = b"".join(outside_parts)

    if len(emitted) != EXPECTED_OUTPUT_BYTES:
        raise GenerationError("OUTPUT_SIZE")
    output_sha = _sha256(emitted)
    if output_sha != EXPECTED_OUTPUT_SHA256:
        raise GenerationError("OUTPUT_SHA256")
    helper = sections["armListRead"].rstrip(b"\n")

    provenance = {
        "format": "TSJ_CURRENT_ARM_READS_SOURCE_PROVENANCE_R1",
        "status": "PASS_SOURCE_TRANSFORM_ONLY",
        "inputs": [
            {"role": "current_study_source", "bytes": len(study_source),
             "sha256": study_sha},
            {"role": "metered_fragment", "bytes": len(metered_fragment_source),
             "sha256": fragment_sha},
        ],
        "methods": method_records,
        "helper": {
            "name": "armListRead",
            "bytes": len(helper),
            "sha256": _sha256(helper),
        },
        "conservedOutside": {
            "bytes": len(outside_bytes),
            "sha256": _sha256(outside_bytes),
            "segments": outside_records,
        },
        "sourceOnlyPrice": {
            "executeArmWithProfile": {
                "NULL_fieldAndCohortReads": 7,
                "OBSERVE_fieldAndCohortReads": 9,
                "OBSERVE_mapReads_nonQuality": 2,
                "OBSERVE_mapReads_quality": 3,
                "successfulStateReplacement": {
                    "addedUnits": 2,
                    "copyEntries": 1,
                    "copyChunks": 1,
                },
                "deeperCalleesIncluded": False,
            },
            "validateObservation": {
                "successfulDirectReadTotals": {
                    "NO_FAILURE": 8,
                    "NON_BRANCH_REJECTION": 10,
                    "FIRST_NON_FOUND": 13,
                },
                "callbacksPositiveAdditionalMapReads": 3,
                "callbacksPositiveAdditionalListArgumentReads": 2,
                "callbacksPositiveAdditionalReadsTotal": 5,
                "guardPredicateAndSelectedHeadReadsIncluded": False,
            },
            "containsMetered": {
                "prepaidTraversalUnits": "2*n+3",
                "sizeObservations": 1,
                "iteratorCreations": 1,
                "hasNextProbes": "n+1",
                "nextAdvances": "n",
                "comparisonGuardUnitsPerVisitedEntry": 1,
                "fullBoundPrepaidOnEarlyMatch": True,
            },
            "r9CanaryArmDto": {
                "sourceOnlyAdditionalUnits": 3,
                "freshFieldReads": 1,
                "observationCleanupFieldReads": 2,
            },
        },
        "output": {
            "role": OUTPUT_STUDY_NAME,
            "bytes": len(emitted),
            "sha256": output_sha,
        },
    }
    return GenerationResult(emitted, _canonical_json(provenance))


def _parse_cli(argv):
    allowed = {
        "--source-root": "source_root",
        "--component-root": "component_root",
        "--io-source-root": "io_source_root",
        "--output-root": "output_root",
    }
    values = {}
    if len(argv) != 8:
        raise GenerationError("CLI_ARGUMENTS")
    for index in range(0, 8, 2):
        key = argv[index]
        value = argv[index + 1]
        if key not in allowed or allowed[key] in values or not value:
            raise GenerationError("CLI_ARGUMENTS")
        values[allowed[key]] = value
    if len(values) != 4:
        raise GenerationError("CLI_ARGUMENTS")
    return values


def _write_stderr_code(code):
    try:
        sys.stderr.write(code + "\n")
        sys.stderr.flush()
    except Exception:
        pass


def _load_owner_io(raw):
    if len(raw) != IO_SOURCE_BYTES or _sha256(raw) != IO_SOURCE_SHA256:
        raise GenerationError("IO_SOURCE_SHA256")
    private_module = _load_pinned_io_module(raw)
    return private_module


def main(argv=None):
    try:
        options = _parse_cli(list(sys.argv[1:] if argv is None else argv))
        io_path = Path(options["io_source_root"]) / IO_SOURCE_NAME

        # This bounded local reader is the only bootstrap before the pinned
        # owner IO source has authenticated and exposed its stable-path adapters.
        io_bytes, bootstrap_guard = _bootstrap_read_io_source(io_path)
        owner = _load_owner_io(io_bytes)

        io_bytes_again, io_guard = owner._read_stable_file(
            io_path, "IO_SOURCE", owner.SOURCE_CAP
        )
        if io_bytes_again != io_bytes:
            raise owner.GenerationError("IO_SOURCE_CHANGED")

        study_path = Path(options["source_root"]) / INPUT_STUDY_NAME
        fragment_path = Path(options["component_root"]) / INPUT_FRAGMENT_NAME
        study_bytes, study_guard = owner._read_stable_file(
            study_path, "SOURCE", SOURCE_CAP
        )
        fragment_bytes, fragment_guard = owner._read_stable_file(
            fragment_path, "FRAGMENT", FRAGMENT_CAP
        )
        result = derive_current_arm_reads(
            study_source=study_bytes,
            metered_fragment_source=fragment_bytes,
        )

        output_guard = owner._directory_guard(options["output_root"], "OUTPUT")
        output_path = output_guard.path / OUTPUT_STUDY_NAME
        if os.path.lexists(output_path):
            raise owner.GenerationError("OUTPUT_EXISTS")

        # Every consumed input and output directory is revalidated before create.
        bootstrap_guard.verify()
        io_guard.verify()
        study_guard.verify()
        fragment_guard.verify()
        output_guard.verify()
        owner._write_exclusive(output_path, result.study_source)

        # Owner writer has closed the exclusive output; read it back before stdout.
        output_guard.verify()
        written, _ = owner._read_stable_file(output_path, "OUTPUT", SOURCE_CAP)
        if written != result.study_source or _sha256(written) != EXPECTED_OUTPUT_SHA256:
            raise owner.GenerationError("OUTPUT_WRITE")

        stream = getattr(sys.stdout, "buffer", None)
        if stream is None:
            raise owner.GenerationError("OUTPUT_WRITE")
        try:
            manifest_written = stream.write(result.provenance_json)
            if manifest_written != len(result.provenance_json):
                raise owner.GenerationError("OUTPUT_WRITE")
            stream.flush()
        except owner.GenerationError:
            raise
        except Exception:
            raise owner.GenerationError("OUTPUT_WRITE") from None
        return 0
    except Exception as exc:
        code = getattr(exc, "code", None)
        _write_stderr_code(code if isinstance(code, str) else "INTERNAL_ERROR")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
