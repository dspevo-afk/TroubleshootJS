#!/usr/bin/env python3
"""Insert the pinned body-ownership call into the pinned current Study source."""

import argparse
import hashlib
import os
from pathlib import Path
import stat
import sys


PINNED_STUDY_SHA256 = "524318f4023fa3b884494657a24dffaac063352cc6e21b4c71bd84e41c0e69e4"
PINNED_STUDY_BYTES = 234907
PINNED_HELPER_SHA256 = "8a7a5b0dfe82d4e230adcd1d531e6942664afbd96563dc5d21f40167b41c3832"
SOURCE_CAP = 4 * 1024 * 1024
REPARSE_POINT = 0x400
STUDY_NAME = "CURRENT_STUDY.java"
HELPER_NAME = "CurrentBodyOwnership.java"
OUTPUT_NAME = "CohortTerminalTelemetryStudy.java"
ANCHOR = b"    /* One complete placeholder-body accounting pass before the Meter closes. */\n"
INSERTION = b"    CurrentBodyOwnership.validate(meter,body);\n"


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


def emit(source_root, component_root, output_root):
    study = _read_root_file(source_root, STUDY_NAME, "EMIT_SOURCE_INPUT")
    helper = _read_root_file(component_root, HELPER_NAME, "EMIT_COMPONENT_INPUT")

    if len(study) != PINNED_STUDY_BYTES or hashlib.sha256(study).hexdigest() != PINNED_STUDY_SHA256:
        raise EmitError("EMIT_SOURCE_PIN")
    if hashlib.sha256(helper).hexdigest() != PINNED_HELPER_SHA256:
        raise EmitError("EMIT_COMPONENT_PIN")

    output = insert_at_unique_anchor(study)
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
    return destination


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--component-root", required=True)
    parser.add_argument("--output-root", required=True)
    args = parser.parse_args(argv)
    try:
        emit(args.source_root, args.component_root, args.output_root)
    except EmitError as failure:
        print(str(failure), file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
