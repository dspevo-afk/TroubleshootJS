#!/usr/bin/env python3
"""Create the bounded native copy-allowance overlay from its exact pinned parent."""

import argparse
import difflib
import hashlib
import os
import stat
import sys
from pathlib import Path


if not sys.flags.isolated or not sys.dont_write_bytecode:
    raise SystemExit("run this transformer with Python -I -B")


BASE_SHA256 = "dc53245d52c7187868b40456b3952cdb2ecf20bc920b2354ff400f60ff68410d"
SOURCE_NAME = "FailureCohortRoutingStudy.java"
DIFF_NAME = "FailureCohortRoutingStudy-copy-allowance.diff"
REPARSE_POINT_ATTRIBUTE = 0x400


REPLACEMENTS = (
    (
        "capture allowance inheritance",
        b'        CaptureData data=new CaptureData();',
        b'        CaptureData data=new CaptureData();\n        data.inheritCopyAllowance(meter);',
    ),
    (
        "reserveCopy remaining allowance",
        b'        if(count<0 || count>COPY_CHUNK || count>COPY_LIMIT-meter.copiedEntries)',
        b'        if(count<0 || count>COPY_CHUNK || count>(long)meter.copyAllowance-meter.copiedEntries)',
    ),
    (
        "fingerprint remaining allowance",
        b'        if(census.entries>COPY_LIMIT-meter.copiedEntries || census.entries+128>STEP_LIMIT)',
        b'        if(census.entries>meter.copyAllowance-meter.copiedEntries || census.entries+128>STEP_LIMIT)',
    ),
    (
        "pad-land remaining allowance",
        b'        if(pads>COPY_CHUNK || 11L*pads>COPY_LIMIT-meter.copiedEntries || 11L*pads+32>STEP_LIMIT)',
        b'        if(pads>COPY_CHUNK || 11L*pads>meter.copyAllowance-meter.copiedEntries || 11L*pads+32>STEP_LIMIT)',
    ),
    (
        "legacy callback aggregate allowance",
        b'        if(entries>COPY_LIMIT) throw new IOException("legacy capture exceeds aggregate copy allowance");',
        b'        if(entries>capture.copyAllowance) throw new IOException("legacy capture exceeds aggregate copy allowance");',
    ),
    (
        "CaptureData allowance field",
        b'        int groupOrdinal,extraCallbackUnits,copiedEntries,copyChunks,preCountWork;',
        b'        int groupOrdinal,extraCallbackUnits,copiedEntries,copyChunks,preCountWork;\n        int copyAllowance=COPY_LIMIT;',
    ),
    (
        "CaptureData meter inheritance",
        b'        List<String> netIds=Collections.emptyList();\n    }',
        b'''        List<String> netIds=Collections.emptyList();
        void inheritCopyAllowance(Meter meter) {
            copyAllowance=meter.copyAllowance;
        }
    }''',
    ),
    (
        "Meter effective allowance field",
        b'    long units,maxGroupNanos,groupStarted,copiedEntries;\n    int groups,groupUnits,maxGroupUnits,priorGroups,copyChunks;',
        b'    long units,maxGroupNanos,groupStarted,copiedEntries;\n    int copyAllowance=COPY_LIMIT;\n    int groups,groupUnits,maxGroupUnits,priorGroups,copyChunks;',
    ),
    (
        "Meter bounded allowance setter",
        b'''    Meter(long started) {
        this.started=started;
    }

    void beginGroup() throws IOException {''',
        b'''    Meter(long started) {
        this.started=started;
    }

    void setCopyAllowance(int allowance) {
        if(allowance<0 || allowance>COPY_LIMIT)
            throw new IllegalArgumentException("copy allowance is out of range");
        if(copiedEntries>allowance)
            throw new IllegalStateException("copy allowance is below already reserved entries");
        copyAllowance=allowance;
    }

    void beginGroup() throws IOException {''',
    ),
)


def has_reparse_component(path):
    absolute = Path(os.path.abspath(str(path)))
    for component in (absolute,) + tuple(absolute.parents):
        try:
            metadata = component.lstat()
        except FileNotFoundError:
            continue
        attributes = getattr(metadata, "st_file_attributes", 0)
        if stat.S_ISLNK(metadata.st_mode) or attributes & REPARSE_POINT_ATTRIBUTE:
            return True
    return False


def replace_once(source, label, old, new):
    occurrences = source.count(old)
    if occurrences != 1:
        raise ValueError(
            "{}: expected one exact source occurrence, found {}".format(label, occurrences)
        )
    return source.replace(old, new, 1)


def build_overlay(source):
    if hashlib.sha256(source).hexdigest() != BASE_SHA256:
        raise ValueError("source bytes do not match the pinned FailureCohortRoutingStudy parent")
    transformed = source
    for label, old, new in REPLACEMENTS:
        transformed = replace_once(transformed, label, old, new)
    return transformed


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, help="exact SHA-pinned parent Java source")
    parser.add_argument("--output-dir", required=True, help="existing empty task-owned output directory")
    args = parser.parse_args()

    source_path = Path(args.source)
    output_dir = Path(args.output_dir)
    if has_reparse_component(source_path) or not source_path.is_file():
        parser.error("--source must be a regular file with no reparse-point path component")
    if has_reparse_component(output_dir) or not output_dir.is_dir():
        parser.error("--output-dir must be an existing directory with no reparse-point path component")
    if any(output_dir.iterdir()):
        parser.error("--output-dir must be empty; refusing to overwrite or mix artifacts")

    source = source_path.read_bytes()
    try:
        transformed = build_overlay(source)
        before_text = source.decode("utf-8")
        after_text = transformed.decode("utf-8")
    except (UnicodeDecodeError, ValueError) as error:
        parser.error(str(error))

    diff_text = "".join(
        difflib.unified_diff(
            before_text.splitlines(keepends=True),
            after_text.splitlines(keepends=True),
            fromfile="immutable/" + SOURCE_NAME,
            tofile="native-overlay/" + SOURCE_NAME,
            n=3,
        )
    )
    if not diff_text:
        parser.error("the exact transformation unexpectedly produced no diff")

    outputs = (
        (output_dir / SOURCE_NAME, transformed),
        (output_dir / DIFF_NAME, diff_text.encode("utf-8")),
    )
    if has_reparse_component(output_dir):
        parser.error("--output-dir acquired a reparse-point path component")
    for target, content in outputs:
        if has_reparse_component(target):
            parser.error("output file path has a symlink or reparse-point component")
        with target.open("xb") as output:
            output.write(content)


if __name__ == "__main__":
    main()
