#!/usr/bin/env python3
"""Pinned-source derivation for the native V2 export component.

This script reads only the five named Java snapshots plus their source-input
manifest. It parses no Java classes, imports no project code, and never runs a
source builder. All output is prepared in memory before an empty output
directory is populated.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


SOURCE_ROWS = {
    "R_STUDY.java": (
        229578,
        "88986781508aa34783d11e651082122f4bf8e0710a2263b39cc9b597fce9a8cf",
    ),
    "W_STUDY.java": (
        239584,
        "1ae31054a430c00bf926ff4deff8091892bae267f05257aa0d869000226f7bd1",
    ),
    "CURRENT_FLAT_STUDY.java": (
        229972,
        "0a7e31b20dabd80a33e5dc5886507817cbc138ce96a37eb20ecc54a6e2ff93e0",
    ),
    "CURRENT_RECEIPT_BINDING.java": (
        14372,
        "7f271ed213d109ea6e185d8418dafff2791e7809c805922ef0f1de44bedc65b9",
    ),
    "CurrentV2CanonicalCodec.java": (
        18580,
        "76fb7bbc8af73e41fd8d79a26823a0687f61e2a812e4b9de251bc953c04c7522",
    ),
}

MAX_SOURCE_BYTES = 4 * 1024 * 1024
PROOF_NAME = "NATIVE_V2_EXPORT_SOURCE_PROOF_R1.json"
STUDY_NAME = "CohortTerminalTelemetryStudy.java"
RECEIPT_NAME = "ReceiptContentBinding.java"


class DerivationError(ValueError):
    """A pinned input, anchor, or transform did not match the reviewed source."""


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _read_pinned_inputs(source_dir: Path, manifest_path: Path) -> tuple[dict[str, bytes], bytes]:
    if not source_dir.is_dir() or source_dir.is_symlink():
        raise DerivationError("SOURCE_DIRECTORY")
    if not manifest_path.is_file() or manifest_path.is_symlink():
        raise DerivationError("SOURCE_MANIFEST")
    manifest_bytes = manifest_path.read_bytes()
    if len(manifest_bytes) > 1024 * 1024:
        raise DerivationError("SOURCE_MANIFEST_SIZE")
    try:
        manifest = json.loads(manifest_bytes.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as failure:
        raise DerivationError("SOURCE_MANIFEST_FORMAT") from failure
    if not isinstance(manifest, dict) or manifest.get("status") != "PINNED_SOURCE_ONLY_NO_DATA":
        raise DerivationError("SOURCE_MANIFEST_STATUS")
    rows = manifest.get("rows")
    if not isinstance(rows, list):
        raise DerivationError("SOURCE_MANIFEST_ROWS")
    manifest_rows: dict[str, dict[str, Any]] = {}
    for row in rows:
        if not isinstance(row, dict) or not isinstance(row.get("name"), str):
            raise DerivationError("SOURCE_MANIFEST_ROW")
        name = row["name"]
        if name in manifest_rows:
            raise DerivationError("SOURCE_MANIFEST_DUPLICATE")
        manifest_rows[name] = row

    loaded: dict[str, bytes] = {}
    for name, (expected_bytes, expected_sha) in SOURCE_ROWS.items():
        row = manifest_rows.get(name)
        if not isinstance(row, dict):
            raise DerivationError("SOURCE_PIN_MISSING")
        if row.get("bytes") != expected_bytes or row.get("sha256") != expected_sha:
            raise DerivationError("SOURCE_MANIFEST_PIN")
        path = source_dir / name
        if not path.is_file() or path.is_symlink():
            raise DerivationError("SOURCE_FILE")
        data = path.read_bytes()
        if len(data) > MAX_SOURCE_BYTES:
            raise DerivationError("SOURCE_SIZE")
        if len(data) != expected_bytes or _sha256(data) != expected_sha:
            raise DerivationError("SOURCE_PIN_MISMATCH")
        loaded[name] = data
    return loaded, manifest_bytes


def _source_text(data: bytes, name: str) -> str:
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as failure:
        raise DerivationError("SOURCE_ENCODING_" + name) from failure
    text = text.replace("\r\n", "\n")
    if "\r" in text or not text.endswith("\n"):
        raise DerivationError("SOURCE_NEWLINES_" + name)
    return text


def _matching_brace(text: str, opening: int) -> int:
    if opening < 0 or opening >= len(text) or text[opening] != "{":
        raise DerivationError("JAVA_DECLARATION_OPEN")
    depth = 0
    state = "code"
    escaped = False
    index = opening
    while index < len(text):
        char = text[index]
        nxt = text[index + 1] if index + 1 < len(text) else ""
        if state == "code":
            if char == "/" and nxt == "/":
                state = "line"
                index += 2
                continue
            if char == "/" and nxt == "*":
                state = "block"
                index += 2
                continue
            if char == '"':
                state = "string"
                index += 1
                continue
            if char == "'":
                state = "char"
                index += 1
                continue
            if char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
                if depth == 0:
                    return index
                if depth < 0:
                    raise DerivationError("JAVA_DECLARATION_BALANCE")
        elif state == "line":
            if char == "\n":
                state = "code"
        elif state == "block":
            if char == "*" and nxt == "/":
                state = "code"
                index += 2
                continue
        elif state in ("string", "char"):
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif (state == "string" and char == '"') or (state == "char" and char == "'"):
                state = "code"
        index += 1
    raise DerivationError("JAVA_DECLARATION_UNCLOSED")


def _member(text: str, anchor: str) -> str:
    if text.count(anchor) != 1:
        raise DerivationError("ANCHOR_NOT_UNIQUE")
    start = text.index(anchor)
    opening = text.find("{", start, start + len(anchor))
    if opening < 0:
        opening = text.find("{", start + len(anchor))
    if opening < 0:
        raise DerivationError("DECLARATION_BODY_MISSING")
    closing = _matching_brace(text, opening)
    return text[start : closing + 1]


def _replace_once(text: str, old: str, new: str, reason: str) -> str:
    if not old or text.count(old) != 1:
        raise DerivationError("REPLACE_NOT_UNIQUE_" + reason)
    return text.replace(old, new, 1)


def _replace_member(text: str, old_member: str, new_member: str, reason: str) -> str:
    if text.count(old_member) != 1:
        raise DerivationError("MEMBER_REPLACE_NOT_UNIQUE_" + reason)
    return text.replace(old_member, new_member, 1)


def _method_hash(text: str, anchor: str) -> str:
    return _sha256(_member(text, anchor).encode("utf-8"))


def _replace_method_body(
    text: str, anchor: str, change, reason: str
) -> tuple[str, str, str]:
    old = _member(text, anchor)
    new = change(old)
    if old == new:
        raise DerivationError("EMPTY_TRANSFORM_" + reason)
    return _replace_member(text, old, new, reason), _sha256(old.encode("utf-8")), _sha256(new.encode("utf-8"))


def _two_file_output_class(text: str) -> tuple[str, str, str]:
    anchor = "static final class R5Output {"
    old = _member(text, anchor)
    new = old
    new = _replace_once(
        new,
        "    final java.nio.file.Path directory,result;\n",
        "    final java.nio.file.Path directory,operation,result;\n",
        "R5_OUTPUT_FIELDS",
    )
    new = _replace_once(
        new,
        "    final R5FileIdentity directoryIdentity;\n",
        "    final R5FileIdentity directoryIdentity,operationIdentity;\n",
        "R5_OUTPUT_IDENTITIES",
    )
    new = _replace_once(
        new,
        '        this.directory=directory; result=directory.resolve("result.json");\n',
        '        this.directory=directory; operation=directory.resolve("op-1.jsonl");\n'
        '        result=directory.resolve("result.json");\n',
        "R5_OUTPUT_PATHS",
    )
    new = _replace_once(
        new,
        "        directoryIdentity=new R5FileIdentity(directory,true);\n"
        "        closure(false,-1);\n",
        "        directoryIdentity=new R5FileIdentity(directory,true);\n"
        "        operationIdentity=new R5FileIdentity(operation,false);\n"
        "        closure(false,-1);\n",
        "R5_OUTPUT_INITIAL_OPERATION",
    )
    old_closure = """    void closure(boolean hasResult,long resultBytes) throws Exception {
        directoryIdentity.verify(directory,true,false);
        int count=0;
        try (java.nio.file.DirectoryStream<java.nio.file.Path> entries=
                java.nio.file.Files.newDirectoryStream(directory)) {
            for (java.nio.file.Path entry:entries) {
                count++;
                r5Require(count<=(hasResult?1:0) && hasResult && entry.equals(result),
                    "R5_OUTPUT_EXTRA_FILE");
                r5Require("result.json".equals(entry.getFileName().toString()),
                    "R5_OUTPUT_NAME_ALIAS");
            }
        }
        r5Require(count==(hasResult?1:0),"R5_OUTPUT_CLOSURE");
        if (!hasResult) r5Require(!java.nio.file.Files.exists(result,
            java.nio.file.LinkOption.NOFOLLOW_LINKS),"R5_RESULT_EXISTS");
        else {
            r5Require(resultIdentity!=null,"R5_RESULT_IDENTITY_MISSING");
            resultIdentity.verify(result,false,true);
            r5Require(resultIdentity.size==resultBytes && java.nio.file.Files.size(result)==resultBytes,
                "R5_RESULT_SIZE");
        }
    }"""
    new_closure = """    void closure(boolean hasResult,long resultBytes) throws Exception {
        directoryIdentity.verify(directory,true,false);
        operationIdentity.verify(operation,false,true);
        int count=0;
        boolean operationSeen=false,resultSeen=false;
        try (java.nio.file.DirectoryStream<java.nio.file.Path> entries=
                java.nio.file.Files.newDirectoryStream(directory)) {
            for (java.nio.file.Path entry:entries) {
                count++;
                if (entry.equals(operation)) {
                    r5Require(!operationSeen &&
                        "op-1.jsonl".equals(entry.getFileName().toString()),
                        "R5_OUTPUT_OPERATION");
                    operationSeen=true;
                } else if (entry.equals(result)) {
                    r5Require(hasResult && !resultSeen &&
                        "result.json".equals(entry.getFileName().toString()),
                        "R5_OUTPUT_RESULT");
                    resultSeen=true;
                } else {
                    throw new java.io.IOException("R5_OUTPUT_EXTRA_FILE");
                }
            }
        }
        r5Require(operationSeen && count==(hasResult?2:1) &&
            resultSeen==hasResult,"R5_OUTPUT_CLOSURE");
        if (!hasResult) r5Require(!java.nio.file.Files.exists(result,
            java.nio.file.LinkOption.NOFOLLOW_LINKS),"R5_RESULT_EXISTS");
        else {
            r5Require(resultIdentity!=null,"R5_RESULT_IDENTITY_MISSING");
            resultIdentity.verify(result,false,true);
            r5Require(resultIdentity.size==resultBytes && java.nio.file.Files.size(result)==resultBytes,
                "R5_RESULT_SIZE");
        }
    }"""
    new = _replace_once(new, old_closure, new_closure, "R5_OUTPUT_CLOSURE")
    return _replace_member(text, old, new, "R5_OUTPUT_CLASS"), _sha256(old.encode("utf-8")), _sha256(new.encode("utf-8"))


def _derive_export_method(r_text: str) -> tuple[str, str, str]:
    anchor = (
        "static R5ExportMetrics exportR5(Arguments args,java.util.Map<String,Object> result,\n"
        "        SetupMeter meter,long started,R5Snapshot pre,R5WorkFinalizer finalizer,\n"
        "        R5AfterResultWrite hook) throws Exception {"
    )
    old = _member(r_text, anchor)
    new = old
    new_header = (
        "static R5ExportMetrics exportV2(String runId,String sourceSha,String wireSha,String contextSha,\n"
        "        java.nio.file.Path outputDirectory,java.util.Map<String,Object> result,\n"
        "        SetupMeter meter,long started,R5Snapshot pre,R5WorkFinalizer finalizer,\n"
        "        ReceiptContentBinding receipt,R5AfterResultWrite hook) throws Exception {"
    )
    new = _replace_once(new, anchor, new_header, "EXPORT_API")
    new = _replace_once(
        new,
        '    r5Require(args!=null && result!=null && meter!=null && pre!=null && finalizer!=null,"R5_ARGUMENT");\n',
        '    r5Require(result!=null && meter!=null && pre!=null && finalizer!=null &&\n'
        '        outputDirectory!=null && receipt!=null,"R5_ARGUMENT");\n',
        "EXPORT_ARGUMENTS",
    )
    new = _replace_once(
        new,
        '    r5Charge(meter,started,exportStarted); // fixed 1: schema/owned-output finalization\n'
        '    R5Output output=new R5Output(args.data);\n',
        '    r5Charge(meter,started,exportStarted); // fixed 1: bounded header/output admission\n'
        '    r5Require(runId!=null && runId.length()==36 && sourceSha!=null && sourceSha.length()==64 &&\n'
        '        wireSha!=null && wireSha.length()==64 && contextSha!=null && contextSha.length()==64 &&\n'
        '        runId.equals(result.get("runId")) &&\n'
        '        sourceSha.equals(result.get("sourceManifestSha256")) &&\n'
        '        wireSha.equals(result.get("wireContractSha256")) &&\n'
        '        contextSha.equals(result.get("inputContextSha256")),"R5_HEADER_IDENTITY");\n'
        '    R5Output output=new R5Output(outputDirectory);\n',
        "EXPORT_HEADER_IDENTITY",
    )
    new = _replace_once(
        new,
        "    r5WorkShape(work);\n"
        "    finalizer.finalizeWork(exportStarted);\n",
        "    r5WorkShape(work);\n"
        "    finalizer.verifyBound(meter,pre,result,work);\n"
        "    finalizer.finalizeWork(exportStarted);\n",
        "EXPORT_FINALIZER_BINDING",
    )
    for old_write, new_write, tag in (
        ("writeCanonicalJson(result,count)", "CurrentV2CanonicalCodec.writeBody(result,count)", "COUNT"),
        ("writeCanonicalJson(result,writer)", "CurrentV2CanonicalCodec.writeBody(result,writer)", "WRITE"),
        ("writeCanonicalJson(result,reader)", "CurrentV2CanonicalCodec.writeBody(result,reader)", "COMPARE"),
    ):
        new = _replace_once(new, old_write, new_write, "EXPORT_CODEC_" + tag)
    new = _replace_once(
        new,
        "        digest=reader.digest;\n"
        "    }\n"
        "    exportCopyBlock(meter,32); // fixed 2, before digest's returned byte array\n",
        "        digest=reader.digest;\n"
        "    }\n"
        "    r5Deadline(meter,started,exportStarted);\n"
        "    receipt.verifyFinal(meter,started,output.operation);\n"
        "    r5Deadline(meter,started,exportStarted);\n"
        "    exportCopyBlock(meter,32); // fixed 2, before digest's returned byte array\n",
        "EXPORT_FINAL_RECEIPT",
    )
    new = _replace_once(
        new,
        "    r5MarkerPreflight(args);\n"
        "    pre.unchanged(meter,fee,0,true,exportStarted,pre.copies+2*resultBytes+32,\n",
        "    pre.unchanged(meter,fee,0,true,exportStarted,pre.copies+2*resultBytes+32,\n",
        "EXPORT_REMOVE_LEGACY_PREFLIGHT",
    )
    marker_start = "    R5MarkerSink markerCount=new R5MarkerSink(null);\n"
    marker_end = "    token.afterPublication(markerBytes); // failure propagates; never a second marker\n"
    if new.count(marker_start) != 1 or new.count(marker_end) != 1:
        raise DerivationError("EXPORT_MARKER_ANCHORS")
    start = new.index(marker_start)
    end = new.index(marker_end, start) + len(marker_end)
    marker_code = """    CurrentV2CanonicalCodec.Marker markerValue=new CurrentV2CanonicalCodec.Marker(
        runId,sourceSha,wireSha,contextSha,sha,resultBytes,elapsed,finalExportNanos,
        totalGroups,totalUnits,maxNanos,maxUnits);
    int markerBytes=CurrentV2CanonicalCodec.countMarker(markerValue);
    r5Require(markerBytes>=1 && markerBytes<=R5_MARKER_BOUND,"R5_MARKER_BOUND");
    token.admit(markerBytes);
    byte[] marker=new byte[markerBytes]; // exact-size target after same-Meter admission
    CurrentV2CanonicalCodec.writeMarker(markerValue,marker);
    r5Deadline(meter,started,exportStarted);
    java.io.PrintStream stdout=System.out;
    r5Require(!stdout.checkError(),"R5_STDOUT_PRIOR_ERROR");
    stdout.write(marker,0,markerBytes); stdout.flush();
    r5Require(!stdout.checkError(),"R5_STDOUT_ERROR");
    token.afterPublication(markerBytes); // failure propagates; never a second marker
"""
    new = new[:start] + marker_code + new[end:]
    new = "/* Caller keeps the borrowed result tree and marker digest exclusively stable through all passes and publication. */\n" + new
    return new, _sha256(old.encode("utf-8")), _sha256(new.encode("utf-8"))


def _patch_work_validation(flat: str) -> tuple[str, str, str]:
    anchor = "static void r5ValidateWork(java.util.Map<?,?> work,R5Snapshot pre,long exportStarted) throws Exception {"

    def change(method: str) -> str:
        return _replace_once(
            method,
            '        r5WorkNumber(work,"groupsBeforeExport")==pre.groups+pre.prior &&\n',
            '        r5WorkNumber(work,"groupsBeforeExport")==pre.groups+pre.prior &&\n'
            '        r5WorkNumber(work,"wrapperGroupsBeforeExport")==pre.groups &&\n',
            "WORK_WRAPPER_GROUP",
        )

    return _replace_method_body(flat, anchor, change, "WORK_VALIDATION")


def _patch_receipt(receipt_text: str) -> tuple[str, str, str]:
    anchor = (
        "    synchronized void verifyFinal(\n"
        "            CohortTerminalTelemetryStudy.SetupMeter suppliedMeter,\n"
        "            long suppliedProducerStarted) {"
    )
    old = _member(receipt_text, anchor)
    new = _replace_once(
        old,
        anchor,
        "    synchronized void verifyFinal(\n"
        "            CohortTerminalTelemetryStudy.SetupMeter suppliedMeter,\n"
        "            long suppliedProducerStarted,\n"
        "            Path suppliedReceipt) {",
        "RECEIPT_FINAL_API",
    )
    new = _replace_once(
        new,
        "        if (suppliedMeter != meter || suppliedProducerStarted != producerStarted)\n"
        '            throw failure("FINAL_AUTHORITY_MISMATCH");\n',
        "        if (suppliedMeter != meter || suppliedProducerStarted != producerStarted ||\n"
        "                !receipt.equals(suppliedReceipt))\n"
        '            throw failure("FINAL_AUTHORITY_MISMATCH");\n',
        "RECEIPT_FINAL_PATH_BINDING",
    )
    return _replace_member(receipt_text, old, new, "RECEIPT_FINAL"), _sha256(old.encode("utf-8")), _sha256(new.encode("utf-8"))


def derive(source_dir: Path, manifest_path: Path, output_dir: Path) -> None:
    inputs, manifest_bytes = _read_pinned_inputs(source_dir, manifest_path)
    source_text = {
        name: _source_text(data, name) for name, data in inputs.items()
    }
    if not output_dir.is_dir() or output_dir.is_symlink():
        raise DerivationError("OUTPUT_DIRECTORY")
    try:
        next(output_dir.iterdir())
    except StopIteration:
        pass
    else:
        raise DerivationError("OUTPUT_NOT_EMPTY")

    flat = source_text["CURRENT_FLAT_STUDY.java"]
    r_text = source_text["R_STUDY.java"]
    receipt_text = source_text["CURRENT_RECEIPT_BINDING.java"]

    preserved_anchors = {
        "main": "    public static void main(String[] args) throws Exception {",
        "assemble": "    static Comparison assemble(Arguments args,long started) throws Exception {",
        "legacyExportFive": "static R5ExportMetrics exportR5(GenericR7Arguments args,java.util.Map<String,Object> result,\n        SetupMeter meter,long started,R5Snapshot pre,R5WorkFinalizer finalizer) throws Exception {",
        "legacyExportSix": "static R5ExportMetrics exportR5(GenericR7Arguments args,java.util.Map<String,Object> result,\n        SetupMeter meter,long started,R5Snapshot pre,R5WorkFinalizer finalizer,\n        R5AfterResultWrite hook) throws Exception {",
    }
    preserved_before = {key: _method_hash(flat, anchor) for key, anchor in preserved_anchors.items()}

    flat, work_before, work_after = _patch_work_validation(flat)
    flat, output_before, output_after = _two_file_output_class(flat)
    flat = _replace_once(
        flat,
        "static final int R5_BLOCK=4096, R5_RESULT_LIMIT=1048576, R5_MARKER_BOUND=666;\n",
        "static final int R5_BLOCK=4096, R5_RESULT_LIMIT=1048576, R5_MARKER_BOUND=656;\n",
        "MARKER_BOUND",
    )
    marker_begin = "/* Marker is emitted directly in its fixed 14-key order."
    marker_end = "/* This token carries lifecycle, not a second budget."
    if flat.count(marker_begin) != 1 or flat.count(marker_end) != 1:
        raise DerivationError("LEGACY_MARKER_BLOCK_ANCHORS")
    marker_start = flat.index(marker_begin)
    marker_stop = flat.index(marker_end, marker_start)
    flat = flat[:marker_start] + "/* V2 marker output is delegated to CurrentV2CanonicalCodec. */\n" + flat[marker_stop:]

    export_method, export_before, export_after = _derive_export_method(r_text)
    insertion_anchor = "private static final int R7_PLAN_FIELD_COPIES=26;"
    flat = _replace_once(
        flat,
        insertion_anchor,
        export_method + "\n\n" + insertion_anchor,
        "EXPORT_METHOD_INSERTION",
    )

    preserved_after = {key: _method_hash(flat, anchor) for key, anchor in preserved_anchors.items()}
    if preserved_before != preserved_after:
        raise DerivationError("PRESERVED_DECLARATION_CHANGED")
    if flat.count("R5_EXPORT_DISABLED") != 2:
        raise DerivationError("LEGACY_EXPORTS_NOT_DISABLED")
    if "R5MarkerSink" in flat or "r5MarkerPreflight" in flat or "R5_MARKER_BOUND=666" in flat:
        raise DerivationError("LEGACY_MARKER_REMAINS")
    if flat.count("CurrentV2CanonicalCodec.writeBody(result,") != 3:
        raise DerivationError("CODEC_PASS_COUNT")
    if flat.count("receipt.verifyFinal(meter,started,output.operation);") != 1:
        raise DerivationError("RECEIPT_EXPORT_BINDING")
    if flat.count("finalizer.verifyBound(meter,pre,result,work);") != 1:
        raise DerivationError("FINALIZER_BINDING")
    if flat.count('r5WorkNumber(work,"wrapperGroupsBeforeExport")==pre.groups') != 1:
        raise DerivationError("WRAPPER_GROUP_VALIDATION")

    receipt_out, receipt_before, receipt_after = _patch_receipt(receipt_text)
    outputs = {
        STUDY_NAME: (flat.rstrip("\n") + "\n").encode("utf-8"),
        RECEIPT_NAME: (receipt_out.rstrip("\n") + "\n").encode("utf-8"),
    }
    proof = {
        "schema": "TSJ_NATIVE_V2_EXPORT_SOURCE_PROOF_R1",
        "status": "DERIVED_SOURCE_ONLY_NOT_EXECUTED",
        "sourceManifest": {"name": "SOURCE_INPUT_R1.json", "bytes": len(manifest_bytes), "sha256": _sha256(manifest_bytes)},
        "parent": "F87c2069b5f5fef2ad6455822d071e1fbc33e465f",
        "inputs": [
            {"name": name, "bytes": len(inputs[name]), "sha256": _sha256(inputs[name])}
            for name in SOURCE_ROWS
        ],
        "pinnedCodecDependency": {
            "name": "CurrentV2CanonicalCodec.java",
            "bytes": len(inputs["CurrentV2CanonicalCodec.java"]),
            "sha256": _sha256(inputs["CurrentV2CanonicalCodec.java"]),
            "outputCopiedUnchanged": False,
        },
        "lineageSnapshots": [
            {"name": "R_STUDY.java", "role": "authored R5 exporter method source"},
            {"name": "W_STUDY.java", "role": "adopted-only Meter snapshot lineage pin"},
        ],
        "transforms": [
            {
                "name": "Java output EOF normalization",
                "operation": "rstrip trailing LF characters, then append exactly one LF",
                "appliedTo": ["CohortTerminalTelemetryStudy.java", "ReceiptContentBinding.java"],
                "guarantee": "UTF-8 LF source with exactly one final newline",
            },
            {
                "name": "R5 work validation",
                "sourceDeclarationSha256": work_before,
                "derivedDeclarationSha256": work_after,
                "change": "require wrapperGroupsBeforeExport == pre.groups",
            },
            {
                "name": "R5 output closure",
                "sourceDeclarationSha256": output_before,
                "derivedDeclarationSha256": output_after,
                "change": "require sibling op-1.jsonl initially and op-1.jsonl plus result.json after write",
            },
            {
                "name": "R5 marker bound",
                "change": "replace legacy 666 byte bound with codec maximum 656 and remove legacy marker serializer",
            },
            {
                "name": "V2 export method",
                "sourceDeclarationSha256": export_before,
                "derivedDeclarationSha256": export_after,
                "source": "R_STUDY.java",
                "api": "exportV2(runId,sourceSha,wireSha,contextSha,outputDirectory,result,meter,started,pre,finalizer,receipt,hook)",
                "changes": [
                    "bind supplied identities to body header inside fixed first schema/output operation",
                    "caller holds body tree and marker digest exclusively stable through count, write, compare, and publication",
                    "verifyBound immediately before finalizeWork",
                    "CurrentV2CanonicalCodec.writeBody for count, write, and read-compare",
                    "verify final receipt after readback and before finishGroup, with deadline checks on both sides",
                    "CurrentV2CanonicalCodec typed 656-byte marker count and exact-size write",
                ],
            },
            {
                "name": "Receipt final authority",
                "sourceDeclarationSha256": receipt_before,
                "derivedDeclarationSha256": receipt_after,
                "change": "verifyFinal(meter,started,suppliedReceipt) adds receipt.equals(suppliedReceipt) to the same bounded authority predicate",
                "failureCode": "FINAL_AUTHORITY_MISMATCH",
            },
        ],
        "preservedDeclarations": [
            {"name": key, "sha256": preserved_before[key]}
            for key in preserved_before
        ],
        "accounting": {
            "exportFee": "5*C+3",
            "copiedEntriesDelta": "2*R+32+M",
            "copyChunksDelta": "2*C+2",
            "groupsDelta": 1,
            "receiptPreparePrepayment": "116+10*P+6*B",
            "receiptFinalAuthorityControl": "one bounded equality within the already prepaid 17 fixed final operations",
            "receiptExportMeterCharges": 0,
        },
        "outputs": [
            {"name": name, "bytes": len(data), "sha256": _sha256(data)}
            for name, data in outputs.items()
        ],
        "limits": {
            "sourceMaximumBytes": MAX_SOURCE_BYTES,
            "sourceInputsRead": list(SOURCE_ROWS),
            "normalSixArgumentEntry": "not implemented",
            "legacyGenericExports": "two overloads remain R5_EXPORT_DISABLED",
            "dataOrJournalReads": 0,
            "compilationOrTests": "not run by deriver",
        },
    }
    proof_bytes = (json.dumps(proof, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")
    outputs[PROOF_NAME] = proof_bytes

    if not output_dir.is_dir() or output_dir.is_symlink():
        raise DerivationError("OUTPUT_DIRECTORY_CHANGED")
    try:
        next(output_dir.iterdir())
    except StopIteration:
        pass
    else:
        raise DerivationError("OUTPUT_NOT_EMPTY")
    for name, data in outputs.items():
        if len(data) > MAX_SOURCE_BYTES and name.endswith(".java"):
            raise DerivationError("OUTPUT_SOURCE_SIZE")
    for name, data in outputs.items():
        path = output_dir / name
        with path.open("xb") as stream:
            stream.write(data)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    options = parser.parse_args()
    derive(options.source_dir, options.manifest, options.output_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
