"""Export four pinned source files as a non-build, non-replay review packet."""

from __future__ import annotations

import ast
import hashlib
import json
import ntpath
import os
import re
import stat
import sys
from pathlib import Path


SCHEMA = "tsj-cohort-terminal-source-review-export-r1"
STATUS = "NOT_BUILD_SOURCE_NOT_REPLAY"
MAX_SOURCE_BYTES = 4 * 1024 * 1024
MAX_REVIEW_BYTES = MAX_SOURCE_BYTES + 64 * 1024
MAX_MANIFEST_BYTES = 256 * 1024
WRITE_CHUNK_BYTES = 4096
SOURCE_ROLES = ("study", "reader", "builder", "descriptor-helper")
REVIEW_FILES = {
    "study": "study.review.txt",
    "reader": "reader.review.txt",
    "builder": "builder.review.txt",
    "descriptor-helper": "descriptor-helper.review.txt",
}
SOURCE_PINS = {
    "study": "8f7c3775c1479d86798464f75fb107c4e5bd1e5bda6ab93f4e39b14beaf6b221",
    "reader": "2d53b33fb77e85c6c36f46b013612883866cffea2996596751ffcb2d30e2bc0a",
    "builder": "271db07f1b1dbd0a30e8016024b4fe87f89b3050447dd4bab64ff8a135751321",
    "descriptor-helper": "2cebcf6a26c61a3600c9ffcf5e1f1268e62bd966598a57e7d71587173d5b8724",
}
CLI_FLAGS = (
    "--study", "--reader", "--builder", "--descriptor-helper", "--output",
)
ABSOLUTE_PATH_START = re.compile(
    r"(?i)(?<![A-Za-z0-9_])(?<![\\/])(?:[A-Z]:[\\/]|[\\/]{2}[A-Za-z0-9._-]+[\\/][^\\/\s\"'()\[\]{};,]+)"
)


class ExportError(ValueError):
    """Stable, path-free diagnostic for a rejected export operation."""

    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


def _need(condition: bool, code: str) -> None:
    if not condition:
        raise ExportError(code)


def _sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _has_absolute_windows_path(value: str) -> bool:
    return any(ntpath.isabs(value[match.start():])
               for match in ABSOLUTE_PATH_START.finditer(value))


def _reject_paths(text: str, code: str) -> None:
    _need(not _has_absolute_windows_path(text), code)


def redact_python_path_literals(raw: bytes, *, first_token: int = 1):
    """Replace absolute-Windows-path text/byte literals, retaining other bytes.

    The returned metadata contains the original literal's SHA-256 and source
    location, never its value. AST columns are UTF-8 byte offsets as defined by
    Python's parser; the exported one-based column reflects that byte offset.
    """
    _need(type(raw) is bytes and 0 < len(raw) <= MAX_SOURCE_BYTES,
          "PYTHON_SOURCE_SIZE")
    _need(type(first_token) is int and first_token >= 1, "PATH_TOKEN_START")
    try:
        text = raw.decode("utf-8", errors="strict")
        tree = ast.parse(text)
    except (UnicodeDecodeError, SyntaxError, ValueError):
        raise ExportError("PYTHON_SOURCE_PARSE") from None

    line_starts = [0]
    for line in raw.splitlines(keepends=True):
        line_starts.append(line_starts[-1] + len(line))

    nodes = [node for node in ast.walk(tree)
             if isinstance(node, ast.Constant) and type(node.value) in (str, bytes)
             and _has_absolute_windows_path(
                 node.value if type(node.value) is str
                 else node.value.decode("latin-1"))]
    nodes.sort(key=lambda node: (node.lineno, node.col_offset,
                                 node.end_lineno, node.end_col_offset))
    edits = []
    metadata = []
    token_number = first_token
    for node in nodes:
        _need(node.end_lineno is not None and node.end_col_offset is not None
              and 1 <= node.lineno <= len(line_starts)
              and 1 <= node.end_lineno <= len(line_starts),
              "PYTHON_LITERAL_SPAN")
        start = line_starts[node.lineno - 1] + node.col_offset
        end = line_starts[node.end_lineno - 1] + node.end_col_offset
        _need(0 <= start < end <= len(raw), "PYTHON_LITERAL_SPAN")
        literal_raw = raw[start:end]
        try:
            literal_text = literal_raw.decode("utf-8", errors="strict")
            parsed_literal = ast.literal_eval(literal_text)
        except (UnicodeDecodeError, SyntaxError, ValueError, TypeError):
            raise ExportError("PYTHON_LITERAL_SPAN") from None
        _need(type(parsed_literal) is type(node.value) and parsed_literal == node.value,
              "PYTHON_LITERAL_SPAN")
        token = "<PATH_{:04d}>".format(token_number)
        replacement = json.dumps(token, ensure_ascii=True).encode("ascii")
        if type(node.value) is bytes:
            replacement = b"b" + replacement
        edits.append((start, end, replacement))
        metadata.append({
            "token": token,
            "literalSha256": _sha(literal_raw),
            "line": node.lineno,
            "column": node.col_offset + 1,
        })
        token_number += 1

    projected = raw
    for start, end, replacement in reversed(edits):
        projected = projected[:start] + replacement + projected[end:]
    try:
        projected_text = projected.decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        raise ExportError("PYTHON_REVIEW_UTF8") from None
    _reject_paths(projected_text, "ABSOLUTE_PATH_REMAINS")
    return projected, metadata, token_number


def project_source(role: str, raw: bytes, *, first_token: int = 1):
    """Create review text for one pinned source body without touching the file."""
    _need(role in SOURCE_ROLES, "SOURCE_ROLE")
    _need(type(raw) is bytes and 0 < len(raw) <= MAX_SOURCE_BYTES,
          "SOURCE_SIZE")
    original_sha = _sha(raw)
    if role == "study":
        body = raw
        replacements = []
        next_token = first_token
    else:
        body, replacements, next_token = redact_python_path_literals(
            raw, first_token=first_token)
    # Review files have one final line ending. Keep the original source pin and
    # its line-ending style while removing empty terminal lines from the view.
    terminal = re.search(rb"(?:\r\n|\n|\r){2,}\Z", body)
    if terminal is not None:
        ending = (b"\r\n" if body.endswith(b"\r\n")
                  else b"\n" if body.endswith(b"\n") else b"\r")
        body = body[:terminal.start()] + ending
    try:
        body_text = body.decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        raise ExportError("SOURCE_UTF8") from None
    _reject_paths(body_text, "ABSOLUTE_PATH_REMAINS")
    header = (
        "# SOURCE REVIEW ONLY: NOT_BUILD_SOURCE_NOT_REPLAY\n"
        "# role={role} originalSha256={sha} originalBytes={size}\n"
        "# Absolute Windows path text/byte literals are represented by numbered tokens.\n"
        "# Review normalization removes terminal empty lines only.\n"
    ).format(role=role, sha=original_sha, size=len(raw)).encode("ascii")
    review = header + body
    _need(len(review) <= MAX_REVIEW_BYTES, "REVIEW_SIZE")
    _reject_paths(review.decode("utf-8"), "ABSOLUTE_PATH_REMAINS")
    return review, replacements, next_token


def parse_cli_args(argv):
    """Parse exactly one value pair for each supported long option."""
    _need(type(argv) in (list, tuple) and len(argv) == 2 * len(CLI_FLAGS),
          "CLI_ARGUMENTS")
    values = {}
    for index in range(0, len(argv), 2):
        flag, value = argv[index], argv[index + 1]
        _need(type(flag) is str and flag in CLI_FLAGS and flag not in values
              and type(value) is str and bool(value) and "\x00" not in value
              and not value.startswith("--"), "CLI_ARGUMENTS")
        values[flag] = value
    _need(set(values) == set(CLI_FLAGS), "CLI_ARGUMENTS")
    return values


def _is_reparse(info) -> bool:
    return stat.S_ISLNK(info.st_mode) or bool(
        getattr(info, "st_file_attributes", 0) & 0x400)


def _canonical_explicit(path_value, code: str) -> Path:
    try:
        path = path_value if isinstance(path_value, Path) else Path(path_value)
        _need(path.is_absolute(), code + "_ABSOLUTE")
        resolved = path.resolve(strict=True)
        _need(resolved == path, code + "_CANONICAL")
        for component in (path, *path.parents):
            info = component.lstat()
            _need(not _is_reparse(info), code + "_REPARSE")
    except ExportError:
        raise
    except (OSError, RuntimeError, TypeError, ValueError):
        raise ExportError(code + "_PATH") from None
    return path


def _checked_directory(path_value, code: str) -> Path:
    path = _canonical_explicit(path_value, code)
    try:
        info = path.lstat()
    except OSError:
        raise ExportError(code + "_STAT") from None
    _need(stat.S_ISDIR(info.st_mode) and not _is_reparse(info), code + "_TYPE")
    return path


def _read_regular(path_value, limit: int, code: str) -> bytes:
    path = _canonical_explicit(path_value, code)
    try:
        before = path.lstat()
    except OSError:
        raise ExportError(code + "_STAT") from None
    _need(stat.S_ISREG(before.st_mode) and not _is_reparse(before), code + "_TYPE")
    _need(0 < before.st_size <= limit, code + "_SIZE")
    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0)
    descriptor = None
    try:
        descriptor = os.open(str(path), flags)
        opened = os.fstat(descriptor)
        _need(stat.S_ISREG(opened.st_mode)
              and (opened.st_dev, opened.st_ino) == (before.st_dev, before.st_ino),
              code + "_IDENTITY")
        with os.fdopen(descriptor, "rb", closefd=True) as stream:
            descriptor = None
            raw = stream.read(limit + 1)
    except ExportError:
        raise
    except OSError:
        raise ExportError(code + "_READ") from None
    finally:
        if descriptor is not None:
            try:
                os.close(descriptor)
            except OSError:
                pass
    _need(0 < len(raw) <= limit, code + "_SIZE")
    return raw


def _directory_names(path: Path, code: str):
    try:
        names = [entry.name for entry in path.iterdir()]
    except OSError:
        raise ExportError(code + "_READ") from None
    return names


def _write_exclusive(path: Path, raw: bytes, limit: int, code: str) -> None:
    _need(type(raw) is bytes and 0 < len(raw) <= limit, code + "_SIZE")
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    flags |= getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0)
    descriptor = None
    try:
        descriptor = os.open(str(path), flags, 0o600)
        with os.fdopen(descriptor, "wb", closefd=True) as stream:
            descriptor = None
            offset = 0
            while offset < len(raw):
                end = min(offset + WRITE_CHUNK_BYTES, len(raw))
                written = stream.write(raw[offset:end])
                _need(written is not None and written > 0, code + "_WRITE")
                offset += written
            stream.flush()
            os.fsync(stream.fileno())
    except ExportError:
        raise
    except FileExistsError:
        raise ExportError(code + "_EXISTS") from None
    except OSError:
        raise ExportError(code + "_WRITE") from None
    finally:
        if descriptor is not None:
            try:
                os.close(descriptor)
            except OSError:
                pass


def export_review_sources(source_paths, output_dir, *, expected_pins=None):
    """Read, project, and exclusively write the bounded five-file packet.

    ``expected_pins`` is an internal test seam; the CLI always supplies the
    immutable four-source pins declared in this module.
    """
    pins = SOURCE_PINS if expected_pins is None else expected_pins
    _need(type(source_paths) is dict and set(source_paths) == set(SOURCE_ROLES),
          "SOURCE_ARGUMENTS")
    _need(type(pins) is dict and set(pins) == set(SOURCE_ROLES)
          and all(type(pins[role]) is str
                  and re.fullmatch(r"[0-9a-f]{64}", pins[role]) is not None
                  for role in SOURCE_ROLES), "SOURCE_PIN_SET")

    output = _checked_directory(output_dir, "OUTPUT_DIRECTORY")
    _need(bool(output.name), "OUTPUT_PARENT")
    names = _directory_names(output, "OUTPUT_DIRECTORY")
    _need(not names, "OUTPUT_NOT_EMPTY")

    originals = {}
    for role in SOURCE_ROLES:
        raw = _read_regular(source_paths[role], MAX_SOURCE_BYTES,
                            "SOURCE_" + role.upper().replace("-", "_"))
        _need(_sha(raw) == pins[role], "SOURCE_HASH_" + role.upper().replace("-", "_"))
        originals[role] = raw

    review_rows = []
    review_bodies = {}
    next_token = 1
    for role in SOURCE_ROLES:
        review, replacements, next_token = project_source(
            role, originals[role], first_token=next_token)
        review_bodies[role] = review
        review_rows.append({
            "role": role,
            "originalSha256": _sha(originals[role]),
            "originalBytes": len(originals[role]),
            "reviewFile": REVIEW_FILES[role],
            "reviewSha256": _sha(review),
            "reviewBytes": len(review),
            "replacedPathLiterals": replacements,
        })

    manifest = {
        "schema": SCHEMA,
        "status": STATUS,
        "reviewNormalization": "TERMINAL_EMPTY_LINES_ONLY_ORIGINAL_SOURCE_PINS_UNCHANGED",
        "sources": review_rows,
    }
    manifest_raw = (json.dumps(manifest, ensure_ascii=True, separators=(",", ":"),
                                allow_nan=False) + "\n").encode("ascii")
    _need(len(manifest_raw) <= MAX_MANIFEST_BYTES, "EXPORT_MANIFEST_SIZE")
    _reject_paths(manifest_raw.decode("ascii"), "ABSOLUTE_PATH_REMAINS")

    output = _checked_directory(output, "OUTPUT_DIRECTORY")
    _need(not _directory_names(output, "OUTPUT_DIRECTORY"), "OUTPUT_NOT_EMPTY")
    expected_files = {REVIEW_FILES[role] for role in SOURCE_ROLES} | {"SOURCE_EXPORT.json"}
    for row in review_rows:
        output = _checked_directory(output, "OUTPUT_DIRECTORY")
        _write_exclusive(output / row["reviewFile"],
                         review_bodies[row["role"]],
                         MAX_REVIEW_BYTES, "OUTPUT_REVIEW_" + row["role"].upper())
    output = _checked_directory(output, "OUTPUT_DIRECTORY")
    _write_exclusive(output / "SOURCE_EXPORT.json", manifest_raw,
                     MAX_MANIFEST_BYTES, "OUTPUT_MANIFEST")

    output = _checked_directory(output, "OUTPUT_DIRECTORY")
    actual_names = _directory_names(output, "OUTPUT_DIRECTORY")
    _need(len(actual_names) == len(expected_files)
          and set(actual_names) == expected_files, "OUTPUT_CLOSURE")
    for row in review_rows:
        readback = _read_regular(output / row["reviewFile"],
                                 MAX_REVIEW_BYTES, "OUTPUT_READBACK_" + row["role"].upper())
        _need(_sha(readback) == row["reviewSha256"],
              "OUTPUT_REVIEW_READBACK_" + row["role"].upper())
    manifest_readback = _read_regular(
        output / "SOURCE_EXPORT.json", MAX_MANIFEST_BYTES, "OUTPUT_MANIFEST_READBACK")
    _need(manifest_readback == manifest_raw, "OUTPUT_MANIFEST_READBACK_HASH")
    return manifest


def main(argv=None) -> int:
    argv = sys.argv[1:] if argv is None else argv
    try:
        options = parse_cli_args(argv)
        source_paths = {
            "study": Path(options["--study"]),
            "reader": Path(options["--reader"]),
            "builder": Path(options["--builder"]),
            "descriptor-helper": Path(options["--descriptor-helper"]),
        }
        export_review_sources(source_paths, Path(options["--output"]))
    except ExportError as failure:
        sys.stderr.write("SOURCE_EXPORT_FAIL " + failure.code + "\n")
        return 2
    except Exception:
        sys.stderr.write("SOURCE_EXPORT_FAIL INTERNAL_ERROR\n")
        return 2
    sys.stdout.write("SOURCE_EXPORT_PASS " + SCHEMA + " files=5\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
