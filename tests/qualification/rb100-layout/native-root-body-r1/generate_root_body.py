#!/usr/bin/env python3
"""Insert the pinned root-body constructor into the pinned current codec."""

import argparse
import hashlib
import os
from pathlib import Path
import stat
import sys


PINNED_CODEC_SHA256 = "76fb7bbc8af73e41fd8d79a26823a0687f61e2a812e4b9de251bc953c04c7522"
PINNED_CODEC_BYTES = 18580
PINNED_FRAGMENT_SHA256 = "2e1070eb3ff29ffd90a706b061d26edd4a20f8da272ca7b8342f3b03ab977363"
PINNED_FRAGMENT_BYTES = 1539
SOURCE_CAP = 4 * 1024 * 1024
REPARSE_POINT = 0x400
CODEC_NAME = "CurrentV2CanonicalCodec.java"
FRAGMENT_NAME = "RootBodyConstructor.java.fragment"
OUTPUT_NAME = "CurrentV2CanonicalCodec.java"
ANCHOR = b"    private CurrentV2CanonicalCodec() {\n"


class EmitError(Exception):
    """A stable, concise source-emitter failure."""


def _snapshot(metadata):
    # atime is deliberately excluded: reading a pinned input may update it.
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


def _check_plain_path_item(metadata, code):
    if stat.S_ISLNK(metadata.st_mode):
        raise EmitError(code)
    if getattr(metadata, "st_file_attributes", 0) & REPARSE_POINT:
        raise EmitError(code)


def _assert_safe_path(path, code):
    try:
        absolute = Path(os.path.abspath(os.fspath(path)))
        resolved = absolute.resolve(strict=True)
        if os.path.normcase(os.fspath(absolute)) != os.path.normcase(os.fspath(resolved)):
            raise EmitError(code)
        for current in list(reversed(absolute.parents)) + [absolute]:
            metadata = os.lstat(os.fspath(current))
            _check_plain_path_item(metadata, code)
        return absolute
    except EmitError:
        raise
    except (OSError, RuntimeError, ValueError, TypeError):
        raise EmitError(code)


def _assert_safe_directory(path, code):
    directory = _assert_safe_path(path, code)
    try:
        metadata = os.lstat(os.fspath(directory))
    except OSError:
        raise EmitError(code)
    if not stat.S_ISDIR(metadata.st_mode):
        raise EmitError(code)
    _check_plain_path_item(metadata, code)
    return directory


def _read_stable(path, code):
    path = _assert_safe_path(path, code)
    try:
        before = os.lstat(os.fspath(path))
    except OSError:
        raise EmitError(code)
    before_snapshot = _snapshot(before)
    if (
        not stat.S_ISREG(before.st_mode)
        or before.st_size <= 0
        or before.st_size > SOURCE_CAP
    ):
        raise EmitError(code)
    _check_plain_path_item(before, code)
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
        after_snapshot = _snapshot(os.lstat(os.fspath(path)))
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


def insert_at_unique_anchor(codec, fragment):
    if codec.count(ANCHOR) != 1:
        raise EmitError("ROOTBODY_ANCHOR")
    offset = codec.index(ANCHOR)
    output = codec[:offset] + fragment + codec[offset:]
    if len(output) > SOURCE_CAP:
        raise EmitError("ROOTBODY_OUTPUT_BOUND")
    return output


def _write_exclusive(path, data):
    try:
        with path.open("xb") as stream:
            initial = _snapshot(os.fstat(stream.fileno()))
            if not stat.S_ISREG(initial[2]):
                raise EmitError("ROOTBODY_OUTPUT_WRITE")
            if getattr(os.fstat(stream.fileno()), "st_file_attributes", 0) & REPARSE_POINT:
                raise EmitError("ROOTBODY_OUTPUT_WRITE")
            written = stream.write(data)
            if written != len(data):
                raise EmitError("ROOTBODY_OUTPUT_WRITE")
            stream.flush()
            os.fsync(stream.fileno())
            after_handle = _snapshot(os.fstat(stream.fileno()))
    except EmitError:
        raise
    except FileExistsError:
        raise EmitError("ROOTBODY_OUTPUT_EXISTS")
    except OSError:
        raise EmitError("ROOTBODY_OUTPUT_WRITE")

    try:
        after_path = _snapshot(os.lstat(os.fspath(path)))
    except OSError:
        raise EmitError("ROOTBODY_OUTPUT_WRITE")
    if after_path[4] != len(data) or _handle_identity(after_handle) != _handle_identity(after_path):
        raise EmitError("ROOTBODY_OUTPUT_WRITE")
    _assert_safe_path(path, "ROOTBODY_OUTPUT_WRITE")


def emit(source_root, component_root, output_root):
    codec = _read_root_file(source_root, CODEC_NAME, "ROOTBODY_SOURCE_INPUT")
    fragment = _read_root_file(component_root, FRAGMENT_NAME, "ROOTBODY_COMPONENT_INPUT")

    if (
        len(codec) != PINNED_CODEC_BYTES
        or hashlib.sha256(codec).hexdigest() != PINNED_CODEC_SHA256
    ):
        raise EmitError("ROOTBODY_SOURCE_PIN")
    if (
        len(fragment) != PINNED_FRAGMENT_BYTES
        or hashlib.sha256(fragment).hexdigest() != PINNED_FRAGMENT_SHA256
    ):
        raise EmitError("ROOTBODY_COMPONENT_PIN")

    output = insert_at_unique_anchor(codec, fragment)
    directory = _assert_safe_directory(output_root, "ROOTBODY_OUTPUT_ROOT")
    destination = directory / OUTPUT_NAME
    _assert_safe_directory(directory, "ROOTBODY_OUTPUT_ROOT")
    _write_exclusive(destination, output)
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