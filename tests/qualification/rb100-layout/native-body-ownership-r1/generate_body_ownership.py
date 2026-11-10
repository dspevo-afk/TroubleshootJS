#!/usr/bin/env python3
"""Emit the authenticated current R7 cleanup Source candidate."""

import argparse
from collections import namedtuple
import hashlib
import json
import os
from pathlib import Path
import stat
import sys


EmitResult = namedtuple("EmitResult", "destination provenance_json")

PINNED_STUDY_SHA256 = "9ac45b374c8aeee7a912a4e1452435b71b278aeae0a288e5a75dd82a149c006b"
PINNED_STUDY_BYTES = 234954
PINNED_HELPER_SHA256 = "8a7a5b0dfe82d4e230adcd1d531e6942664afbd96563dc5d21f40167b41c3832"
PINNED_HELPER_BYTES = 3860
PINNED_AUTHORED_MEMBERS_SHA256 = "8c2c6428e8a4c5ca45d912cd22d76f344c6ab968c11031e6fd0417974b852bde"
PINNED_AUTHORED_MEMBERS_BYTES = 22592
PINNED_AUTHORED_MODULE_SHA256 = "92ce7dc27bd08d395a1bc8155e4795d579885a8dbd26c765c2ee0219051e280c"
PINNED_AUTHORED_MODULE_BYTES = 18595
PINNED_PRE_OWNERSHIP_SHA256 = "c8b23f3939f2b866516dc4eaa7a869d22b4fd7e1ed24ef0bbeaf7f113fff4ba5"
PINNED_PRE_OWNERSHIP_BYTES = 234978
PINNED_FINAL_SOURCE_SHA256 = "48f67e37f5603fdf9eade38d41cd86c95fb7171b16edc7a6f6cd8b13637e0b00"
PINNED_FINAL_SOURCE_BYTES = 235025
SOURCE_CAP = 4 * 1024 * 1024
RESULT_CAP = 1024 * 1024
REPARSE_POINT = 0x400
STUDY_NAME = "CURRENT_STUDY.java"
HELPER_NAME = "CurrentBodyOwnership.java"
AUTHORED_MODULE_NAME = "generate_authored_flat_work_overlay.py"
AUTHORED_MEMBERS_NAME = "AuthoredFlatWorkMembers.java"
OUTPUT_NAME = "CohortTerminalTelemetryStudy.java"
ANCHOR = b"    /* One complete placeholder-body accounting pass before the Meter closes. */\n"
INSERTION = b"    CurrentBodyOwnership.validate(meter,body);\n"
PURE_PROVENANCE_SCHEMA = "TSJ_CURRENT_R7_AUTHORED_SOURCE_R1"
EMIT_MANIFEST_SCHEMA = "TSJ_CURRENT_R7_CLEANUP_SOURCE_EMIT_R1"


class EmitError(Exception):
    """A stable, concise source-emitter failure."""


def _snapshot(metadata):
    return (
        metadata.st_dev,
        metadata.st_ino,
        metadata.st_mode,
        metadata.st_nlink,
        metadata.st_size,
        getattr(metadata, "st_mtime_ns", int(metadata.st_mtime * 1000000000)),
        getattr(metadata, "st_ctime_ns", int(metadata.st_ctime * 1000000000)),
        getattr(metadata, "st_file_attributes", 0),
    )


def _handle_identity(snapshot):
    return (
        snapshot[0],
        snapshot[1],
        stat.S_IFMT(snapshot[2]),
        snapshot[4],
        snapshot[5],
        snapshot[7],
    )


def _assert_safe_path(path, code, lstat=os.lstat):
    try:
        absolute = Path(os.path.abspath(str(path)))
        resolved = absolute.resolve(strict=True)
        if os.path.normcase(str(absolute)) != os.path.normcase(str(resolved)):
            raise EmitError(code)
        for current in list(reversed(absolute.parents)) + [absolute]:
            item = lstat(str(current))
            if stat.S_ISLNK(item.st_mode):
                raise EmitError(code)
            if getattr(item, "st_file_attributes", 0) & REPARSE_POINT:
                raise EmitError(code)
        return absolute
    except EmitError:
        raise
    except (OSError, RuntimeError, ValueError):
        raise EmitError(code)


def _assert_safe_directory(path, code):
    directory = _assert_safe_path(path, code)
    try:
        metadata = os.lstat(str(directory))
    except OSError:
        raise EmitError(code)
    if not stat.S_ISDIR(metadata.st_mode):
        raise EmitError(code)
    return directory


def _read_stable(path, code):
    path = _assert_safe_path(path, code)
    try:
        before = os.lstat(str(path))
    except OSError:
        raise EmitError(code)
    before_snapshot = _snapshot(before)
    if not stat.S_ISREG(before.st_mode) or before.st_size <= 0 or before.st_size > SOURCE_CAP:
        raise EmitError(code)
    try:
        with path.open("rb") as source:
            opened = _snapshot(os.fstat(source.fileno()))
            if _handle_identity(before_snapshot) != _handle_identity(opened):
                raise EmitError(code)
            raw = source.read(SOURCE_CAP + 1)
            after_handle = _snapshot(os.fstat(source.fileno()))
    except EmitError:
        raise
    except OSError:
        raise EmitError(code)
    try:
        after_snapshot = _snapshot(os.lstat(str(path)))
    except OSError:
        raise EmitError(code)
    if before_snapshot != after_snapshot:
        raise EmitError(code)
    if _handle_identity(opened) != _handle_identity(after_handle):
        raise EmitError(code)
    if len(raw) != before.st_size or len(raw) > SOURCE_CAP:
        raise EmitError(code)
    _assert_safe_path(path, code)
    return raw


def _read_root_file(root, filename, code):
    directory = _assert_safe_directory(root, code)
    return _read_stable(directory / filename, code)


def insert_at_unique_anchor(study):
    if study.count(ANCHOR) != 1:
        raise EmitError("EMIT_ANCHOR")
    offset = study.index(ANCHOR)
    return study[:offset] + INSERTION + study[offset:]


def _require_pin(data, expected_bytes, expected_sha256, code):
    if (type(data) is not bytes or len(data) > SOURCE_CAP
            or len(data) != expected_bytes
            or hashlib.sha256(data).hexdigest() != expected_sha256):
        raise EmitError(code)


def _authenticated_deriver(module_source):
    try:
        module_text = module_source.decode("ascii")
        module_code = compile(
            module_text, "<authenticated-authored-flat-work-module>", "exec",
            dont_inherit=True,
        )
    except (UnicodeDecodeError, SyntaxError):
        raise EmitError("EMIT_AUTHORED_MODULE_SYNTAX")
    namespace = {"__name__": "_tsj_authenticated_current_r7_authored_source"}
    try:
        exec(module_code, namespace, namespace)
    except Exception:
        raise EmitError("EMIT_AUTHORED_MODULE_EXEC")
    derive = namespace.get("derive_current_flat_work")
    result_type = namespace.get("GenerationResult")
    error_type = namespace.get("OverlayError")
    if not callable(derive) or not isinstance(result_type, type) or not isinstance(error_type, type):
        raise EmitError("EMIT_AUTHORED_MODULE_API")
    return derive, result_type, error_type


def _validate_pure_provenance(data, derived_source):
    if type(data) is not bytes or not data or len(data) > RESULT_CAP:
        raise EmitError("EMIT_PURE_PROVENANCE_SIZE")
    if data.startswith(b"\xef\xbb\xbf") or b"\r" in data or not data.endswith(b"\n"):
        raise EmitError("EMIT_PURE_PROVENANCE_FORMAT")
    try:
        text = data.decode("ascii")
        value = json.loads(text)
        canonical = (
            json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
            + "\n"
        ).encode("ascii")
    except (UnicodeDecodeError, ValueError, TypeError):
        raise EmitError("EMIT_PURE_PROVENANCE_FORMAT")
    if canonical != data or not isinstance(value, dict):
        raise EmitError("EMIT_PURE_PROVENANCE_CANONICAL")
    if (value.get("schema") != PURE_PROVENANCE_SCHEMA
            or value.get("status") != "SOURCE_ONLY"):
        raise EmitError("EMIT_PURE_PROVENANCE_SCHEMA")
    output = value.get("output")
    if (not isinstance(output, dict)
            or output.get("bytes") != len(derived_source)
            or output.get("sha256") != hashlib.sha256(derived_source).hexdigest()):
        raise EmitError("EMIT_PURE_PROVENANCE_OUTPUT")
    return text, value


def _source_record(role, basename, data):
    return {
        "role": role,
        "basename": basename,
        "bytes": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
    }


def _manifest_bytes(study, helper, module_source, authored_members,
                    derived_source, final_source, pure_provenance):
    pure_ascii, pure_object = _validate_pure_provenance(
        pure_provenance, derived_source
    )
    manifest = {
        "schema": EMIT_MANIFEST_SCHEMA,
        "status": "SOURCE_ONLY",
        "limits": {
            "sourceCapBytes": SOURCE_CAP,
            "manifestCapBytes": RESULT_CAP,
        },
        "inputs": {
            "currentStudy": _source_record(
                "source-root-study", STUDY_NAME, study
            ),
            "ownershipHelper": _source_record(
                "component-root-helper", HELPER_NAME, helper
            ),
            "authoredModule": _source_record(
                "authored-root-pure-module", AUTHORED_MODULE_NAME, module_source
            ),
            "authoredMembers": _source_record(
                "authored-root-template", AUTHORED_MEMBERS_NAME, authored_members
            ),
        },
        "derivedSource": {
            "role": "pre-ownership-current-study",
            "bytes": len(derived_source),
            "sha256": hashlib.sha256(derived_source).hexdigest(),
        },
        "finalSource": {
            "role": "exclusive-output",
            "basename": OUTPUT_NAME,
            "bytes": len(final_source),
            "sha256": hashlib.sha256(final_source).hexdigest(),
        },
        "pureProvenance": {
            "bytes": len(pure_provenance),
            "sha256": hashlib.sha256(pure_provenance).hexdigest(),
            "ascii": pure_ascii,
            "object": pure_object,
        },
    }
    try:
        encoded = (
            json.dumps(manifest, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
            + "\n"
        ).encode("ascii")
    except (TypeError, ValueError, UnicodeEncodeError):
        raise EmitError("EMIT_MANIFEST_FORMAT")
    if len(encoded) > RESULT_CAP:
        raise EmitError("EMIT_MANIFEST_SIZE")
    return encoded


def emit(source_root, component_root, authored_root, output_root):
    study = _read_root_file(source_root, STUDY_NAME, "EMIT_SOURCE_INPUT")
    helper = _read_root_file(component_root, HELPER_NAME, "EMIT_COMPONENT_INPUT")
    authored_module = _read_root_file(
        authored_root, AUTHORED_MODULE_NAME, "EMIT_AUTHORED_MODULE_INPUT"
    )
    authored_members = _read_root_file(
        authored_root, AUTHORED_MEMBERS_NAME, "EMIT_AUTHORED_FRAGMENT_INPUT"
    )

    _require_pin(
        study, PINNED_STUDY_BYTES, PINNED_STUDY_SHA256, "EMIT_SOURCE_PIN"
    )
    _require_pin(
        helper, PINNED_HELPER_BYTES, PINNED_HELPER_SHA256, "EMIT_COMPONENT_PIN"
    )
    _require_pin(
        authored_module, PINNED_AUTHORED_MODULE_BYTES,
        PINNED_AUTHORED_MODULE_SHA256, "EMIT_AUTHORED_MODULE_PIN",
    )
    _require_pin(
        authored_members, PINNED_AUTHORED_MEMBERS_BYTES,
        PINNED_AUTHORED_MEMBERS_SHA256, "EMIT_AUTHORED_FRAGMENT_PIN",
    )

    derive, result_type, error_type = _authenticated_deriver(authored_module)
    try:
        result = derive(
            current_source=study,
            authored_members_source=authored_members,
        )
    except Exception as failure:
        if isinstance(failure, error_type):
            code = str(failure)
            if code and code.isascii() and code.replace("_", "").isalnum():
                raise EmitError("EMIT_DERIVE_" + code)
        raise EmitError("EMIT_DERIVE_FAILURE")
    if type(result) is not result_type:
        raise EmitError("EMIT_DERIVE_RESULT")
    derived_source = result.study_source
    pure_provenance = result.provenance_json
    if (type(derived_source) is not bytes
            or len(derived_source) != PINNED_PRE_OWNERSHIP_BYTES
            or hashlib.sha256(derived_source).hexdigest() != PINNED_PRE_OWNERSHIP_SHA256):
        raise EmitError("EMIT_PRE_OWNERSHIP_OUTPUT_PIN")
    output = insert_at_unique_anchor(derived_source)
    if (len(output) != PINNED_FINAL_SOURCE_BYTES
            or hashlib.sha256(output).hexdigest() != PINNED_FINAL_SOURCE_SHA256):
        raise EmitError("EMIT_FINAL_OUTPUT_PIN")
    manifest = _manifest_bytes(
        study, helper, authored_module, authored_members, derived_source,
        output, pure_provenance,
    )
    directory = _assert_safe_directory(output_root, "EMIT_OUTPUT_ROOT")

    destination = directory / OUTPUT_NAME
    try:
        _assert_safe_directory(directory, "EMIT_OUTPUT_ROOT")
        with destination.open("xb") as stream:
            if stream.write(output) != len(output):
                raise EmitError("EMIT_OUTPUT_WRITE")
    except EmitError:
        raise
    except FileExistsError:
        raise EmitError("EMIT_OUTPUT_EXISTS")
    except OSError:
        raise EmitError("EMIT_OUTPUT_WRITE")
    return EmitResult(destination, manifest)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--component-root", required=True)
    parser.add_argument("--authored-root", required=True)
    parser.add_argument("--output-root", required=True)
    args = parser.parse_args(argv)
    try:
        result = emit(
            args.source_root, args.component_root, args.authored_root, args.output_root
        )
    except EmitError as failure:
        print(str(failure), file=sys.stderr)
        return 2
    try:
        output_stream = getattr(sys.stdout, "buffer", None)
        if output_stream is None:
            written = sys.stdout.write(result.provenance_json.decode("ascii"))
            if written != len(result.provenance_json):
                raise OSError("partial stdout write")
            sys.stdout.flush()
        else:
            written = output_stream.write(result.provenance_json)
            if written != len(result.provenance_json):
                raise OSError("partial stdout write")
            output_stream.flush()
    except OSError:
        print("EMIT_MANIFEST_WRITE", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
