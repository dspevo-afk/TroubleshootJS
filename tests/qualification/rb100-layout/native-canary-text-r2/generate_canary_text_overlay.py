#!/usr/bin/env python3
"""Derive the exact R9.2 canary text-policy overlay from its external parent."""

import sys

if not sys.flags.isolated or not sys.dont_write_bytecode:
    raise SystemExit("run this transformer with Python -I -B")

import argparse
import hashlib
import json
import os
import stat
import tempfile
from pathlib import Path


SOURCE_NAME = "CohortTerminalTelemetryStudy.java"
SOURCE_SHA256 = "4ea0f0950c31d05e86b6339c918381d7bc0dd0d17999b499a7729f0ffd9e7750"
OUTPUT_SHA256 = "dacca39996a54ac3c65e8507367cf06401e8b289922b207a3ee0c4e6e22def98"
OUTPUT_BYTES = 216099
PROOF_NAME = "overlay-proof.json"
MAX_SOURCE_BYTES = 1048576
MAX_CONTROL_BYTES = 65536
MAX_PROOF_BYTES = 8192
REPARSE_POINT_ATTRIBUTE = 0x400
FRAGMENT_PINS = (
    ("CanonicalJson.fragment.java", "e6318d4bc04691ba2ff8ac4131ae16ca7c6c5806e45690022ce475346b231a88"),
    ("R9CanonicalJson.fragment.java", "f63dbb28ffe9599b7c661f0c089701c47dd86a17aade1f813a9cce2da3ed36d8"),
    ("R9CanaryTextPolicy.fragment.java", "8196c38224cedef7feb4a79dc2edafedcb35ff3bbb46c83f3b4cea2b2877fddf"),
    ("R9CanaryStdout.fragment.java", "6f475d31468753fed0f81e160efbbeb79a93057a3873065125b28d4569bef35b"),
)


def sha256(raw):
    return hashlib.sha256(raw).hexdigest()


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def checked_existing(path, kind, label):
    path = Path(path)
    require(".." not in path.parts, label + ": parent traversal is not allowed")
    absolute = Path(os.path.abspath(os.fspath(path)))
    for component in reversed((absolute,) + tuple(absolute.parents)):
        metadata = component.lstat()
        require(not stat.S_ISLNK(metadata.st_mode) and not (
            getattr(metadata, "st_file_attributes", 0) & REPARSE_POINT_ATTRIBUTE
        ), label + ": symlink or reparse-point component")
        leaf_file = component == absolute and kind == "file"
        require(stat.S_ISREG(metadata.st_mode) if leaf_file else stat.S_ISDIR(metadata.st_mode),
                label + ": unsupported file or directory type")
    return absolute, absolute.stat()


def file_identity(metadata):
    return metadata.st_dev, metadata.st_ino


def read_regular(path, maximum, label):
    absolute, before = checked_existing(path, "file", label)
    require(before.st_size <= maximum, label + ": input exceeds byte bound")
    with absolute.open("rb") as handle:
        opened = os.fstat(handle.fileno())
        require(stat.S_ISREG(opened.st_mode) and file_identity(opened) == file_identity(before),
                label + ": file identity changed before read")
        raw = handle.read(maximum + 1)
        after_handle = os.fstat(handle.fileno())
    _, after = checked_existing(absolute, "file", label)
    require(len(raw) <= maximum and len(raw) == before.st_size == after_handle.st_size == after.st_size
            and file_identity(before) == file_identity(after_handle) == file_identity(after),
            label + ": file changed or exceeded its byte bound")
    return raw


def replace_once(source, label, old, new):
    count = source.count(old)
    require(count == 1, label + ": expected exactly one occurrence, found " + str(count))
    return source.replace(old, new, 1)


def transform(source, fragments):
    require(sha256(source) == SOURCE_SHA256, "source does not match the pinned parent")
    result = replace_once(source, "default formatter", fragments["CanonicalJson.fragment.java"],
                          fragments["R9CanonicalJson.fragment.java"])
    stdout = fragments["R9CanaryStdout.fragment.java"]
    modified = replace_once(
        stdout, "metadata tree call", b"    accountResultTree(canary,meter);",
        b"    validateR9CanaryTextPolicy(canary,meter); // exact paths and text, charged metadata\n"
        b"    accountResultTree(canary,meter);")
    old_call = b"writeCanonicalJson(canary,"
    new_call = b"writeCanonicalJsonCanaryValues(canary,"
    require(modified.count(old_call) == 2 and modified.count(new_call) == 0,
            "stdout formatter: expected exactly the two original A/B calls")
    modified = modified.replace(old_call, new_call)
    result = replace_once(result, "stdout fragment", stdout, modified)
    policy = fragments["R9CanaryTextPolicy.fragment.java"]
    require(result.count(policy) == 0, "text policy was already present")
    closing = result.rfind(b"}")
    require(closing >= 0 and result[closing + 1:] == b"\n\n", "unexpected final class closure")
    result = result[:closing] + b"\n" + policy + b"\n}" + result[closing + 1:]
    require(result.count(fragments["R9CanonicalJson.fragment.java"]) == 1
            and result.count(policy) == 1 and result.count(modified) == 1,
            "integrated fragment multiplicity changed")
    require(result.count(b'throw new IOException("R2 producer integration is incomplete")') == 1
            and b"COHORT_TERMINAL_CANARIES" not in result, "native main boundary changed")
    require(len(result) == OUTPUT_BYTES and sha256(result) == OUTPUT_SHA256,
            "derived bytes differ from the pinned R9.2 overlay")
    return result


def check_directory(path, expected_identity, expected_names):
    absolute, metadata = checked_existing(path, "directory", "output directory")
    require(file_identity(metadata) == expected_identity, "output directory identity changed")
    found = set()
    with os.scandir(absolute) as entries:
        for entry in entries:
            require(entry.name in expected_names and entry.name not in found,
                    "output directory contains an unexpected entry")
            checked_existing(absolute / entry.name, "file", "output artifact")
            found.add(entry.name)
    require(found == set(expected_names), "output directory closure differs")


def write_exclusive(path, raw, output_dir, identity, existing_names):
    check_directory(output_dir, identity, existing_names)
    require(path.parent == output_dir and not os.path.lexists(path), "output already exists")
    # No cleanup on failure: partial files remain for diagnosis, never overwritten.
    with path.open("xb") as handle:
        require(handle.write(raw) == len(raw), "short output write")
        handle.flush()
        os.fsync(handle.fileno())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, help="exact external parent source file")
    parser.add_argument("--output-dir", required=True,
                        help="existing empty owned directory strictly inside the OS temporary directory")
    args = parser.parse_args()
    try:
        output_dir, metadata = checked_existing(args.output_dir, "directory", "output directory")
        temporary, _ = checked_existing(tempfile.gettempdir(), "directory", "OS temporary directory")
        resolved_output = os.path.normcase(os.fspath(output_dir.resolve(strict=True)))
        resolved_temp = os.path.normcase(os.fspath(temporary.resolve(strict=True)))
        require(resolved_output != resolved_temp and os.path.commonpath(
            (resolved_output, resolved_temp)) == resolved_temp, "output directory is outside OS temporary storage")
        identity = file_identity(metadata)
        check_directory(output_dir, identity, ())
        source_path = Path(args.source)
        require(source_path.name == SOURCE_NAME, "source filename must be " + SOURCE_NAME)
        source = read_regular(source_path, MAX_SOURCE_BYTES, "source")
        require(sha256(source) == SOURCE_SHA256, "source does not match the pinned parent")
        script_path, _ = checked_existing(__file__, "file", "transformer")
        script_raw = read_regular(script_path, MAX_CONTROL_BYTES, "transformer")
        fragments, records = {}, []
        for name, pin in FRAGMENT_PINS:
            raw = read_regular(script_path.parent / name, MAX_CONTROL_BYTES, name)
            require(sha256(raw) == pin, name + ": fragment hash mismatch")
            normalized = raw.replace(b"\r\n", b"\n")
            require(b"\r" not in normalized, name + ": unsupported standalone CR")
            fragments[name] = normalized
            records.append({"name": name, "rawBytes": len(raw), "rawSha256": pin,
                            "normalizedBytes": len(normalized), "normalizedSha256": sha256(normalized)})
        derived = transform(source, fragments)
        proof = {
            "schema": "tsj-r9-canary-text-overlay-proof-v1",
            "status": "GENERATED_SOURCE_ONLY_NOT_ACCEPTED",
            "source": {"name": SOURCE_NAME, "bytes": len(source), "sha256": SOURCE_SHA256},
            "output": {"name": SOURCE_NAME, "bytes": OUTPUT_BYTES, "sha256": OUTPUT_SHA256},
            "transformer": {"name": script_path.name, "sha256": sha256(script_raw)},
            "fragments": records,
            "edits": {"canonicalFragmentReplacements": 1, "stdoutFragmentReplacements": 1,
                      "metadataPolicyInsertions": 1, "canaryFormatterCallReplacements": 2,
                      "finalPolicyInsertions": 1},
            "nativeMain": "FAIL_CLOSED_UNCHANGED",
            "authoredCaptures": 0, "authoredCohortSessions": 0,
            "runtime": "NOT_RUN", "fullD": "NOT_RUN_NOT_ACCEPTED",
        }
        proof_raw = (json.dumps(proof, sort_keys=True, indent=2) + "\n").encode("utf-8")
        require(len(proof_raw) <= MAX_PROOF_BYTES, "proof exceeds its fixed byte bound")
        target = output_dir / SOURCE_NAME
        write_exclusive(target, derived, output_dir, identity, ())
        require(read_regular(target, MAX_SOURCE_BYTES, "generated source") == derived,
                "generated source readback differs")
        write_exclusive(output_dir / PROOF_NAME, proof_raw, output_dir, identity, (SOURCE_NAME,))
        require(read_regular(output_dir / PROOF_NAME, MAX_PROOF_BYTES, "generated proof") == proof_raw,
                "generated proof readback differs")
        check_directory(output_dir, identity, (SOURCE_NAME, PROOF_NAME))
    except (OSError, ValueError) as failure:
        # OS exceptions can carry personal paths; public proof/status never does.
        reason = str(failure) if isinstance(failure, ValueError) else type(failure).__name__
        parser.error(reason)
    print(json.dumps({"status": "GENERATED_SOURCE_ONLY_NOT_ACCEPTED",
                      "outputSha256": OUTPUT_SHA256, "outputBytes": OUTPUT_BYTES}, sort_keys=True))


if __name__ == "__main__":
    main()
