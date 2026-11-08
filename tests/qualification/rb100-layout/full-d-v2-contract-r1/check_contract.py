#!/usr/bin/env python3
"""Read-only source-contract checker. It never opens allowlisted oracle data."""
from __future__ import annotations

import ast
import hashlib
import json
import os
import re
import stat
import sys
from pathlib import Path


MAX_SOURCE_BYTES = 4 * 1024 * 1024
MAX_CONTRACT_BYTES = 1024 * 1024
REPARSE_POINT = 0x400
HEX64 = re.compile(r"[0-9a-f]{64}\Z")
UUID36 = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\Z")
SEEDS = {
    "rootSeed": "9007199254740993",
    "placementSeed": "-6587958367974952701",
    "routingSeed": "8445301674744508279",
}

MARKER_KEYS = (
    "schema", "status", "runId", "sourceManifestSha256", "wireContractSha256",
    "inputContextSha256", "resultSha256", "resultBytes", "elapsedNanos",
    "finalExportNanos", "totalGroups", "totalUnits", "maxGroupNanos",
    "maximumUnitsPerGroup",
)
MARKER_CAPS = {
    "resultBytes": 1048576,
    "elapsedNanos": 90000000000,
    "finalExportNanos": 5000000000,
    "totalGroups": 640,
    "totalUnits": 9223372036854775807,
    "maxGroupNanos": 5000000000,
    "maximumUnitsPerGroup": 32768,
}
MARKER_SCHEMA = "tsj-cohort-terminal-marker-v2"
MARKER_STATUS = "COMPLETED_DIAGNOSTIC"
MARKER_BOUND = 656
TRANSPORT_BOUND = 722
ALLOWLIST_HELPER_SHA256 = "2cebcf6a26c61a3600c9ffcf5e1f1268e62bd966598a57e7d71587173d5b8724"

SOURCE_PINS = (
    ("authoritySource", "authority_source", "build_inputs_r2.py",
     "271db07f1b1dbd0a30e8016024b4fe87f89b3050447dd4bab64ff8a135751321"),
    ("rStudy", "r_study", "CohortTerminalTelemetryStudy.java",
     "88986781508aa34783d11e651082122f4bf8e0710a2263b39cc9b597fce9a8cf"),
    ("wStudy", "w_study", "CohortTerminalTelemetryStudy.java",
     "1ae31054a430c00bf926ff4deff8091892bae267f05257aa0d869000226f7bd1"),
    ("nReader", "n_reader", "read_failure_cohort.py",
     "84c8dbf2805ab5607f1e79f1d1fa67cc1a2e4ec06d3a22fdd44771ce48d03b52"),
    ("p07Reader", "p07_reader", "read_branch_order_study.py",
     "a314cdc732069bca17f73a724cd21295af334d9baead82fcdb13bf0600c5554e"),
    ("observationKernel", "observation_kernel", "observation_v2_kernel.py",
     "502ff2e1dec1c4d01ddb4e2841aae6f7e4073a62975c1cc9668a09cf5a52b89c"),
)

CONTRACT_NAMES = (
    "PLAN_R1.md", "WIRE_R1.md", "CONTEXT_R1.md", "PREPAYMENT_R1.md",
    "MARKER_BOUND_R1.json", "NORMAL_ORACLE_ALLOWLIST_R1.json",
)

CALL_ROWS = (
    ("call-owner", "N_PRIOR_CALL_OWNER_JSON", "native-observe-r3/owner.json", 547,
     "1559355863aae9b680eeab3334c35953b4d239f42ab774b83173053b25808e79"),
    ("call-parent", "N_PRIOR_CALL_PARENT_JSON", "native-observe-r3/parent.json", 1811,
     "8ac7a7ffc92dfb316be50a9da490d5ad51c494dd986fb18b7e821ca27e32c857"),
    ("call-process", "N_PRIOR_CALL_PROCESS_JSON", "native-observe-r3/process.json", 2049,
     "f07359e62cb1fb0b0dd51b4334b9cdc79d9a51db8f358e672ac2c5e86ee58aef"),
    ("call-request", "N_PRIOR_CALL_REQUEST_JSON", "native-observe-r3/request.json", 885,
     "29a725862bd01151b67ef3d839de066b3e05d845f1744acf2153a51754ca244a"),
    ("call-result", "N_PRIOR_CALL_RESULT_JSON", "native-observe-r3/result.json", 178,
     "c2dd1f6267219e8055cd043f803c1c836fcad7e51849de8862693fba5e089a7c"),
    ("call-stderr", "N_PRIOR_CALL_STDERR_TXT", "native-observe-r3/stderr.txt", 0,
     "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
    ("call-stdout", "N_PRIOR_CALL_STDOUT_TXT", "native-observe-r3/stdout.txt", 328,
     "42dacdb5c346b4595a2d4d07436723f4be8206ff778d31a233906318d484d0a5"),
    ("data-op-1", "N_PRIOR_OPERATION1", "native-data-r3/op-1.jsonl", 377138,
     "cc2463e37c9636f1407434fd2f0d69fda660699e4899059273d1b72a71e02e58"),
    ("data-result", "N_PRIOR_RESULT", "native-data-r3/result.json", 46323,
     "cd44ad1c0db81a8f05d8620f49b78a9e06c2274e6739066b438607f532cf1431"),
)

QUAL_NAMES = ("owner.json", "parent.json", "process.json", "request.json",
              "result.json", "stderr.txt", "stdout.txt")
QUAL_ROLES = (
    "D_PRIOR_QUALIFICATION_OWNER_JSON", "D_PRIOR_QUALIFICATION_PARENT_JSON",
    "D_PRIOR_QUALIFICATION_PROCESS_JSON", "D_PRIOR_QUALIFICATION_REQUEST_JSON",
    "D_PRIOR_QUALIFICATION_RESULT_JSON", "D_PRIOR_QUALIFICATION_STDERR_TXT",
    "D_PRIOR_QUALIFICATION_STDOUT_TXT",
)
CLOSURE_ROWS = (
    ("prior-run-manifest", "N_PRIOR_RUN_MANIFEST", "j8-failure-cohort-r3/run-manifest.json",
     274475, "N_RUN_SHA256", "1274185600f9da79412f133ba569fb727eba673c7dd672e53abd8dcc5fd28a56"),
    ("prior-source-manifest", "N_PRIOR_SOURCE_MANIFEST", "j8-failure-cohort-r3/source-manifest.json",
     273546, "N_SOURCE_SHA256", "c04a7579d042bcb8d4b2a9b4799c3e03ec3ad34eda228856205b7deaa1ba6bc4"),
    ("prior-class-inventory", "N_PRIOR_CLASS_INVENTORY", "j8-failure-cohort-r3/class-inventory.json",
     717708, "N_CLASS_SHA256", "753705026b76b13a6218eadd22bd8942d9fc5cc5cde47dae6a26038613217691"),
)


class ContractError(Exception):
    """Stable, path-free failure signal."""


def _need(condition, code="CONTRACT"):
    if not condition:
        raise ContractError(code)


def _snapshot(st):
    return (st.st_dev, st.st_ino, st.st_mode, st.st_size,
            getattr(st, "st_mtime_ns", int(st.st_mtime * 1e9)),
            getattr(st, "st_ctime_ns", int(st.st_ctime * 1e9)),
            getattr(st, "st_file_attributes", 0))


def _same_handle_identity(path_snapshot, handle_snapshot):
    # Windows may report a different ctime through an opened handle. Path ctime
    # is still compared before/after; handle checks use the stable file identity.
    indexes = (0, 1, 2, 3, 4, 6)
    return all(path_snapshot[i] == handle_snapshot[i] for i in indexes)


def _canonical_path(raw, directory=False):
    try:
        p = Path(raw)
        _need(p.is_absolute(), "PATH")
        before = os.lstat(p)
        _need(not stat.S_ISLNK(before.st_mode) and
              not (getattr(before, "st_file_attributes", 0) & REPARSE_POINT), "REPARSE")
        real = p.resolve(strict=True)
    except OSError:
        raise ContractError("PATH") from None
    _need(os.path.normcase(os.path.abspath(str(p))) == os.path.normcase(str(real)), "CANONICAL")
    if directory:
        _need(stat.S_ISDIR(before.st_mode), "DIRECTORY")
    else:
        _need(stat.S_ISREG(before.st_mode), "REGULAR")
    return p, real, _snapshot(before)


def _read_checked(raw, limit, directory=None):
    p, real, before = _canonical_path(raw)
    if directory is not None:
        _need(os.path.normcase(str(real.parent)) == os.path.normcase(str(directory)), "CONTRACT_CHILD")
    _need(before[3] <= limit, "SIZE")
    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        fd = os.open(str(real), flags)
    except OSError:
        raise ContractError("SOURCE_IO") from None
    try:
        opened = os.fstat(fd)
        _need(stat.S_ISREG(opened.st_mode) and
              _same_handle_identity(before, _snapshot(opened)), "FILE_CHANGED")
        chunks, remaining = [], limit + 1
        while remaining:
            block = os.read(fd, min(65536, remaining))
            if not block:
                break
            chunks.append(block)
            remaining -= len(block)
        data = b"".join(chunks)
        after_fd = os.fstat(fd)
    finally:
        os.close(fd)
    after = os.lstat(p)
    _need(_snapshot(after) == before and _same_handle_identity(before, _snapshot(after_fd)) and
          stat.S_ISREG(after.st_mode) and
          not (getattr(after, "st_file_attributes", 0) & REPARSE_POINT) and
          p.resolve(strict=True) == real and len(data) <= limit, "FILE_CHANGED")
    return data, real


def _pairs_no_duplicates(pairs):
    out = {}
    for key, value in pairs:
        _need(key not in out, "JSON_DUPLICATE")
        out[key] = value
    return out


def _strict_json(raw):
    _need(type(raw) is bytes and raw.isascii(), "JSON_ASCII")
    try:
        return json.loads(raw.decode("ascii"), object_pairs_hook=_pairs_no_duplicates,
                          parse_constant=lambda _: (_ for _ in ()).throw(ContractError("JSON_NUMBER")))
    except ContractError:
        raise
    except Exception as exc:
        raise ContractError("JSON") from exc


def _canonical_json(value):
    return json.dumps(value, ensure_ascii=True, separators=(",", ":"),
                      allow_nan=False).encode("ascii") + b"\n"


def validate_marker(marker):
    """Validate a synthetic marker value and return canonical bytes; no marker file is read."""
    _need(type(marker) is dict and tuple(marker.keys()) == MARKER_KEYS, "MARKER_ORDER")
    _need(marker["schema"] == MARKER_SCHEMA and marker["status"] == MARKER_STATUS, "MARKER_SCHEMA")
    _need(type(marker["runId"]) is str and UUID36.fullmatch(marker["runId"]) is not None,
          "MARKER_UUID")
    for key in ("sourceManifestSha256", "wireContractSha256", "inputContextSha256", "resultSha256"):
        _need(type(marker[key]) is str and HEX64.fullmatch(marker[key]) is not None, "MARKER_HASH")
    for key, cap in MARKER_CAPS.items():
        value = marker[key]
        _need(type(value) is int and 0 <= value <= cap, "MARKER_INTEGER")
    raw = _canonical_json(marker)
    _need(0 < len(raw) <= MARKER_BOUND and len(raw) <= TRANSPORT_BOUND, "MARKER_BOUND")
    return raw


def validate_marker_bytes(raw):
    _need(type(raw) is bytes and 0 < len(raw) <= MARKER_BOUND, "MARKER_BYTES")
    _need(raw.endswith(b"\n") and raw.count(b"\n") == 1 and raw.isascii(), "MARKER_LINE")
    value = _strict_json(raw[:-1])
    _need(validate_marker(value) == raw, "MARKER_CANONICAL")
    return value


def maximum_marker_fixture():
    value = {
        "schema": MARKER_SCHEMA, "status": MARKER_STATUS,
        "runId": "ffffffff-ffff-ffff-ffff-ffffffffffff",
        "sourceManifestSha256": "f" * 64, "wireContractSha256": "f" * 64,
        "inputContextSha256": "f" * 64, "resultSha256": "f" * 64,
    }
    value.update(MARKER_CAPS)
    return value


def _find_function(tree, name):
    found = [node for node in tree.body if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
             and node.name == name]
    _need(len(found) == 1, "AST_FUNCTION")
    return found[0]


def _assigned(node, name):
    for child in ast.walk(node):
        if isinstance(child, (ast.Assign, ast.AnnAssign)):
            targets = child.targets if isinstance(child, ast.Assign) else [child.target]
            if any(isinstance(t, ast.Name) and t.id == name for t in targets):
                return child.value
    raise ContractError("AST_ASSIGNMENT")


def _top_literal(tree, name):
    return ast.literal_eval(_assigned(tree, name))


def _ast_literal_or_constant(tree, node):
    if isinstance(node, ast.Name):
        return _top_literal(tree, node.id)
    return ast.literal_eval(node)


def _safe_expr(node, env=None):
    """Tiny AST literal evaluator for integer cap expressions and role conditions."""
    env = env or {}
    if isinstance(node, ast.Constant):
        return node.value
    if isinstance(node, ast.Name) and node.id in env:
        return env[node.id]
    if isinstance(node, (ast.Tuple, ast.List)):
        values = [_safe_expr(item, env) for item in node.elts]
        return tuple(values) if isinstance(node, ast.Tuple) else values
    if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Mult):
        left, right = _safe_expr(node.left, env), _safe_expr(node.right, env)
        _need(type(left) is int and type(right) is int, "AST_ARITHMETIC")
        return left * right
    if isinstance(node, ast.Compare) and len(node.ops) == len(node.comparators) == 1:
        left, right = _safe_expr(node.left, env), _safe_expr(node.comparators[0], env)
        if isinstance(node.ops[0], ast.In):
            return left in right
    if isinstance(node, ast.IfExp):
        return _safe_expr(node.body if _safe_expr(node.test, env) else node.orelse, env)
    raise ContractError("AST_EXPR")


def _specs(tree, function_name):
    node = _assigned(_find_function(tree, function_name), "specs")
    _need(isinstance(node, (ast.List, ast.Tuple)), "AST_SPECS")
    result = []
    for item in node.elts:
        _need(isinstance(item, (ast.List, ast.Tuple)) and len(item.elts) == 4, "AST_SPEC_ROW")
        role = ast.literal_eval(item.elts[0])
        size = ast.literal_eval(item.elts[2])
        digest = _ast_literal_or_constant(tree, item.elts[3])
        _need(type(role) is str and type(size) is int and type(digest) is str, "AST_SPEC_VALUE")
        result.append((role, size, digest, item))
    return result


def _call_cap(tree, function_name, method):
    calls = []
    for node in ast.walk(_find_function(tree, function_name)):
        named = (isinstance(node.func, ast.Name) and node.func.id == method) if isinstance(node, ast.Call) else False
        attribute = (isinstance(node.func, ast.Attribute) and node.func.attr == method) if isinstance(node, ast.Call) else False
        if isinstance(node, ast.Call) and (named or attribute):
            calls.append(node)
    _need(bool(calls), "AST_CALL")
    return calls


def _source_rows(tree):
    pin_dict = _top_literal(tree, "D_QUALIFICATION_SHA256")
    _need(type(pin_dict) is dict and tuple(pin_dict.keys()) == QUAL_NAMES, "QUAL_ORDER")
    _need(all(type(k) is str and type(v) is str and HEX64.fullmatch(v) for k, v in pin_dict.items()),
          "QUAL_HASH")

    pinned = _specs(tree, "_pinned_rows")
    role_order = tuple(row[0] for row in CALL_ROWS)
    _need(tuple(item[0] for item in pinned) == role_order, "PINNED_ROW_ORDER")
    limit_expr = _assigned(_find_function(tree, "_pinned_rows"), "limit")
    source_pinned = {role: (size, digest, _safe_expr(limit_expr, {"role": role}))
                     for role, size, digest, _ in pinned}

    qfunc = _find_function(tree, "_qualification_refs")
    qloops = [n for n in ast.walk(qfunc) if isinstance(n, ast.For) and isinstance(n.target, ast.Name)
              and n.target.id == "name" and isinstance(n.iter, (ast.Tuple, ast.List))]
    _need(len(qloops) == 1 and tuple(ast.literal_eval(qloops[0].iter)) == QUAL_NAMES,
          "QUAL_SOURCE_ORDER")
    qlimits = [node for node in ast.walk(qfunc) if isinstance(node, ast.Call) and
               isinstance(node.func, ast.Attribute) and node.func.attr == "_read_file"]
    _need(len(qlimits) == 1 and _safe_expr(qlimits[0].args[1]) == 512 * 1024,
          "QUAL_SOURCE_CAP")

    obs = _find_function(tree, "_observation_ref")
    obs_calls = [node for node in ast.walk(obs) if isinstance(node, ast.Call) and
                 isinstance(node.func, ast.Name) and node.func.id == "_read_ref"]
    _need(len(obs_calls) == 1, "OBSERVATION_SOURCE")
    obs_args = obs_calls[0].args
    _need(ast.literal_eval(obs_args[1]) == "qualification-projection" and
          ast.literal_eval(obs_args[3]) == 3916 and isinstance(obs_args[4], ast.Name) and
          obs_args[4].id == "PRIOR_OBSERVATION_SHA256" and _safe_expr(obs_args[5]) == 128 * 1024,
          "OBSERVATION_SOURCE")
    obs_sha = ast.literal_eval(_assigned(tree, "PRIOR_OBSERVATION_SHA256"))

    closure = _specs(tree, "_closure_refs")
    closure_caps = _call_cap(tree, "_closure_refs", "_read_ref")
    cap_values = [ _safe_expr(call.args[5]) for call in closure_caps if len(call.args) > 5 ]
    _need(closure and cap_values == [4 * 1024 * 1024], "CLOSURE_SOURCE_CAP")

    constants = {name: ast.literal_eval(_assigned(tree, name)) for name in
                 ("N_RUN_SHA256", "N_SOURCE_SHA256", "N_CLASS_SHA256", "N_READER_SHA256",
                  "PAD_HELPER_SOURCE_SHA256")}
    _need(constants["N_READER_SHA256"] ==
          "84c8dbf2805ab5607f1e79f1d1fa67cc1a2e4ec06d3a22fdd44771ce48d03b52",
          "N_READER_SOURCE_PIN")
    rows = {}
    for builder_role, allow_role, path, _known_size, _known_hash in CALL_ROWS:
        size, digest, cap = source_pinned[builder_role]
        _need(size == _known_size and digest == _known_hash, "PINNED_ROW_LITERAL")
        expected_cap = 8 * 1024 * 1024 if builder_role in ("data-op-1", "data-result") else 256 * 1024
        _need(cap == expected_cap, "PINNED_SOURCE_CAP")
        rows[allow_role] = ("N", path, size, cap, digest, None)
    for name, role in zip(QUAL_NAMES, QUAL_ROLES):
        rows[role] = ("D", "prior-input-qualify-r2/" + name, None, 512 * 1024,
                      pin_dict[name], "qualification")
    _need(len(closure) == 3, "CLOSURE_ROWS")
    for (builder_role, allow_role, path, expected_size, constant, expected_sha), item in zip(CLOSURE_ROWS, closure):
        role, size, digest, _ = item
        _need((role, size, digest) == (builder_role, expected_size, expected_sha) and
              constants[constant] == expected_sha, "CLOSURE_PIN")
        rows[allow_role] = ("N", path, size, 4 * 1024 * 1024, digest, None)
    _need(obs_sha == "d2dd656567031515aa520528f2cb8343c9252558b3108bcc4eb840c887e9edcb",
          "OBSERVATION_PIN")
    rows["D_PRIOR_OBSERVATION_CROSSCHECK"] = (
        "D", "PRIOR_INPUT_QUALIFICATION_R2.json", 3916, 128 * 1024, obs_sha, None)
    return rows, pin_dict, constants


def _expected_allowlist_rows(source_rows, pin_dict):
    result = []
    # Keep contract order fixed; this list is metadata only and is never used as a path to open.
    order = [row[1] for row in CALL_ROWS[:7]] + list(QUAL_ROLES) + [
        CALL_ROWS[7][1], CALL_ROWS[8][1], "D_PRIOR_OBSERVATION_CROSSCHECK"] + [row[1] for row in CLOSURE_ROWS]
    for role in order:
        root, path, size, cap, digest, kind = source_rows[role]
        row = {"role": role, "root": root, "path": path, "expectedBytes": size,
               "maxBytes": cap, "sha256": digest}
        if kind == "qualification":
            row["exactContentAuthority"] = "PINNED_SOURCE_HASH_NO_INITIAL_DATA_SNAPSHOT"
            row["summaryParseMaxBytes"] = 1024 if path.endswith("stdout.txt") else None
        result.append(row)
    return result


def _validate_allowlist(doc, authority_tree):
    source_rows, qual_pins, constants = _source_rows(authority_tree)
    _need(doc.get("schema") == "tsj-full-d-v2-normal-oracle-allowlist-v1" and
          doc.get("status") == "FROZEN_PINS_UNREAD_READER_GATES_REQUIRED" and
          type(doc.get("readingEnabled")) is bool and doc["readingEnabled"] is False and
          type(doc.get("roleCount")) is int and doc["roleCount"] == 20 and
          doc.get("authoritySourceSha256") == "271db07f1b1dbd0a30e8016024b4fe87f89b3050447dd4bab64ff8a135751321" and
          doc.get("helperSourceSha256") == ALLOWLIST_HELPER_SHA256,
          "ALLOWLIST_HEADER")
    for key in ("noRecursiveManifestFollowing", "oldDDescriptorResultGeneratedInputDenied",
                "protectedAndHeldOutDenied", "qualificationProjectionIsCrosscheckOnly",
                "rootPathsArePrivateAndExternallyFixed"):
        _need(type(doc.get(key)) is bool and doc[key] is True, "ALLOWLIST_POLICY")
    actual = doc.get("rows")
    expected = _expected_allowlist_rows(source_rows, qual_pins)
    _need(type(actual) is list and len(actual) == len(expected) == 20, "ALLOWLIST_ROWS")
    for got, want in zip(actual, expected):
        _need(type(got) is dict, "ALLOWLIST_ROW")
        _need(all(got.get(key) == value and type(got.get(key)) is type(value)
                  for key, value in want.items()), "ALLOWLIST_PIN")
        _need(set(got) == set(want), "ALLOWLIST_ROW_SHAPE")
        _need(type(got["maxBytes"]) is int and got["maxBytes"] > 0 and
              type(got["sha256"]) is str and HEX64.fullmatch(got["sha256"]) is not None,
              "ALLOWLIST_TYPES")
    return {"rolesCompared": len(actual), "oracleDataPathsOpened": 0}


def _java_block(source, marker):
    start = source.find(marker)
    _need(start >= 0, "JAVA_MARKER")
    brace = source.find("{", start)
    _need(brace >= 0, "JAVA_BLOCK")
    depth, i, state, escaped = 0, brace, "code", False
    while i < len(source):
        ch = source[i]
        nxt = source[i + 1] if i + 1 < len(source) else ""
        if state == "line":
            if ch == "\n": state = "code"
        elif state == "block":
            if ch == "*" and nxt == "/": state, i = "code", i + 1
        elif state in ("string", "char"):
            if escaped: escaped = False
            elif ch == "\\": escaped = True
            elif (state == "string" and ch == '"') or (state == "char" and ch == "'"):
                state = "code"
        else:
            if ch == "/" and nxt == "/": state, i = "line", i + 1
            elif ch == "/" and nxt == "*": state, i = "block", i + 1
            elif ch == '"': state = "string"
            elif ch == "'": state = "char"
            elif ch == "{": depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0: return source[brace:i + 1]
        i += 1
    raise ContractError("JAVA_UNCLOSED")


def _plan_field_counts(plan):
    # Only declarations before the pinned constructor are fields. Private long
    # methods later in the class may have commas and semicolons in their bodies.
    constructor = plan.find("private R7WorkPlan(")
    _need(constructor >= 0, "CTOR_REGION")
    fields = plan[:constructor]
    ref_fields = re.findall(r"^    private final (?:SetupMeter|R5Snapshot|java\.util\.Map<String,Object>) \w+;",
                            fields, re.M)
    long_decls = re.findall(r"^    private (?:final )?long ([^;]+);", fields, re.M)
    long_fields = sum(len([v for v in line.split(",") if v.strip()]) for line in long_decls)
    bool_decls = re.findall(r"^    private boolean ([^;]+);", fields, re.M)
    bool_fields = [name.strip() for line in bool_decls for name in line.split(",")]
    return len(ref_fields), long_fields, len(bool_fields)


def _java_inventory(r_source, w_source, n_source, context_text):
    plan = _java_block(r_source, "static final class R7WorkPlan implements R5WorkFinalizer")
    ref_fields, long_fields, bool_fields = _plan_field_counts(plan)
    ctor_start = plan.find("private R7WorkPlan(")
    ctor_brace = plan.find("{", ctor_start)
    _need(ctor_start >= 0 and ctor_brace > ctor_start, "CTOR_REGION")
    ctor_header = plan[ctor_start:ctor_brace]
    ctor_longs = re.findall(r"\blong\s+[A-Za-z_]\w*", ctor_header)
    capture_start = r_source.rfind("static R7WorkPlan captureR7WorkPlan", 0,
                                   r_source.find("R7WorkPlan plan=new R7WorkPlan"))
    capture_end = r_source.find("/** Flat detached counters", capture_start)
    _need(capture_start >= 0 and capture_end > capture_start, "CAPTURE_REGION")
    capture = r_source[capture_start:capture_end]
    freeze = _java_block(plan, "private void freezeAfterCapture")
    inventory = {
        "planFields": ref_fields + long_fields + bool_fields,
        "identityRefs": ref_fields, "longFields": long_fields,
        "lifecycleBooleans": bool_fields, "constructorLongs": len(ctor_longs),
        "captureCheckedAdds": len(re.findall(r"\br5Add\s*\(", capture)),
        "freezeCheckedSubtracts": len(re.findall(r"\br7Subtract\s*\(", freeze)),
        "freezeCheckedAdds": len(re.findall(r"\br5Add\s*\(", freeze)),
        "freezeMathMax": len(re.findall(r"\bMath\.max\s*\(", freeze)),
    }
    _need(inventory == {"planFields": 43, "identityRefs": 3, "longFields": 38,
                       "lifecycleBooleans": 2, "constructorLongs": 26,
                       "captureCheckedAdds": 11, "freezeCheckedSubtracts": 18,
                       "freezeCheckedAdds": 4, "freezeMathMax": 2}, "R_INVENTORY")

    snap = _java_block(w_source, "static final class R5Snapshot")
    closed = _java_block(snap, "void captureClosed(")
    read_count = (len(re.findall(r"\br5Flag\s*\(", closed)) +
                  len(re.findall(r"\br5Number\s*\(", closed)) +
                  len(re.findall(r"\bfield\s*\(", closed)) +
                  len(re.findall(r"\.size\s*\(", closed)))
    _need(read_count == 19 and "ownerAdopted=r5Flag" in closed and
          re.search(r"ownerStarted\s*&&\s*ownerAdopted", closed) is not None,
          "W_SNAPSHOT_INVENTORY")

    n_tree = ast.parse(n_source)
    n_seeds = {name: ast.literal_eval(_assigned(n_tree, const)) for name, const in (
        ("rootSeed", "ROOT_SEED"), ("placementSeed", "PLACEMENT_SEED"),
        ("routingSeed", "ROUTING_SEED"))}
    _need(n_seeds == SEEDS, "N_SEEDS")
    for source in (r_source, w_source):
        for key, value in SEEDS.items():
            _need('"' + value + '".equals(seeds.get("' + key + '"))' in source,
                  "JAVA_SEEDS")
    for key, value in SEEDS.items():
        _need(key + "=" + value in context_text, "CONTEXT_SEEDS")
    return inventory, {"closedSnapshotReads": read_count, "adoptedPredicate": True}


def _validate_marker_contract(bound_doc, wire_text, prepay_text, context_text):
    _need(type(bound_doc) is dict and bound_doc.get("schema") == "tsj-full-d-v2-marker-bound-v1" and
          bound_doc.get("status") == "FROZEN_SOURCE_CONTRACT_ONLY" and
          bound_doc.get("actualRuntimeProof") is False and
          bound_doc.get("requiresNativePreflightBounds") is True, "MARKER_CONTRACT")
    _need(tuple(bound_doc.get("orderedKeys", ())) == MARKER_KEYS, "MARKER_KEYS")
    expected_limits = [{"key": key, "maximum": str(cap)} for key, cap in MARKER_CAPS.items()]
    _need(bound_doc.get("limits") == expected_limits, "MARKER_LIMITS")
    _need(bound_doc.get("keyQuotedBytes") == 206 and
          bound_doc.get("fixedQuotedValueBytes") == 355 and
          bound_doc.get("numberDigitBytes") == 65 and
          bound_doc.get("punctuationAndLf") == 30 and
          206 + 355 + 65 + 30 == MARKER_BOUND and
          bound_doc.get("boundBytes") == MARKER_BOUND and
          bound_doc.get("transportCeilingBytes") == TRANSPORT_BOUND, "MARKER_FORMULA")
    marker_section = wire_text.split("## Marker", 1)[-1].split("## Admission", 1)[0]
    match = re.search(r"exact ordered14 keys:\s*(.*?)\.\n", marker_section, re.S)
    _need(match is not None and tuple(re.findall(r"[A-Za-z][A-Za-z0-9]*", match.group(1))) == MARKER_KEYS,
          "WIRE_MARKER_KEYS")
    for key, value in SEEDS.items():
        _need(key + "=" + value in context_text, "CONTEXT_SEEDS")
    design_terms = (
        "Original boundary | 9units:5 source inspections and4 checked arithmetic operations",
        "Capture sums | 11 checked additions",
        "Flat work construction | Copy35 logical entries",
        "The plan has44 fields:4 identity refs",
        "Plan | Allocate1unit, copy26 detached constructor longs",
        "Snapshot | Existing allocateR5Snapshot1unit and19 closed-native field/list inspections",
        "Freeze | 26units:18 checked subtractions,4 checked additions,2 Math.max operations",
        "Finalize | Copy34 numeric wrapper replacements. Prepay38units",
    )
    _need(all(term in prepay_text for term in design_terms), "PREPAYMENT_DESIGN")
    fixture = validate_marker(maximum_marker_fixture())
    _need(len(fixture) == MARKER_BOUND, "MARKER_MAX_FIXTURE")
    validate_marker_bytes(fixture)
    return {"maximumBytes": len(fixture), "transportCeilingBytes": TRANSPORT_BOUND,
            "negativeCasesImplemented": True}


def _parse_exact_pairs(argv):
    required = ("--contract-dir", "--authority-source", "--r-study", "--w-study",
                "--n-reader", "--p07-reader", "--observation-kernel")
    _need(len(argv) == len(required) * 2, "CLI_ARGUMENTS")
    values = {}
    i = 0
    while i < len(argv):
        key = argv[i]
        _need(key in required and key not in values and i + 1 < len(argv) and
              not argv[i + 1].startswith("--"), "CLI_ARGUMENTS")
        values[key] = argv[i + 1]
        i += 2
    _need(set(values) == set(required), "CLI_ARGUMENTS")
    return values


def check_contract(args):
    contract_path, contract_real, _ = _canonical_path(args["--contract-dir"], directory=True)
    docs, doc_meta = {}, []
    for name in CONTRACT_NAMES:
        raw, real = _read_checked(str(contract_path / name), MAX_CONTRACT_BYTES,
                                  directory=str(contract_real))
        digest = hashlib.sha256(raw).hexdigest()
        if name.endswith(".json"):
            docs[name], = (_strict_json(raw),)
        else:
            docs[name] = raw.decode("utf-8", "strict")
        doc_meta.append({"name": name, "bytes": len(raw), "sha256": digest})
    source_meta, source_bytes = [], {}
    for role, argname, basename, expected_sha in SOURCE_PINS:
        raw_path = args["--" + argname.replace("_", "-")]
        p = Path(raw_path)
        if basename is not None:
            _need(p.name == basename, "SOURCE_NAME")
        else:
            _need(p.suffix.lower() == ".java", "SOURCE_NAME")
        raw, _ = _read_checked(raw_path, MAX_SOURCE_BYTES)
        digest = hashlib.sha256(raw).hexdigest()
        _need(digest == expected_sha, "SOURCE_HASH")
        source_meta.append({"role": role, "bytes": len(raw), "sha256": digest})
        source_bytes[role] = raw
    _need(len({item["sha256"] for item in source_meta}) == len(source_meta), "SOURCE_DUPLICATE")

    authority_text = source_bytes["authoritySource"].decode("utf-8", "strict")
    authority_tree = ast.parse(authority_text, filename="<pinned-authority-source>", mode="exec")
    n_text = source_bytes["nReader"].decode("utf-8", "strict")
    p07_text = source_bytes["p07Reader"].decode("utf-8", "strict")
    ast.parse(p07_text, filename="<pinned-p07-source>", mode="exec")
    ast.parse(source_bytes["observationKernel"].decode("utf-8", "strict"),
              filename="<pinned-observation-source>", mode="exec")
    context_text = docs["CONTEXT_R1.md"]
    r_text = source_bytes["rStudy"].decode("utf-8", "strict")
    w_text = source_bytes["wStudy"].decode("utf-8", "strict")
    _need("source-only" in docs["PLAN_R1.md"].lower() and
          "no native execution" in docs["PLAN_R1.md"].lower(), "PLAN_SCOPE")
    allowlist = _validate_allowlist(docs["NORMAL_ORACLE_ALLOWLIST_R1.json"], authority_tree)
    marker = _validate_marker_contract(docs["MARKER_BOUND_R1.json"], docs["WIRE_R1.md"],
                                       docs["PREPAYMENT_R1.md"], context_text)
    r_inventory, w_inventory = _java_inventory(r_text, w_text, n_text, context_text)
    p07_sha = hashlib.sha256(source_bytes["p07Reader"]).hexdigest()
    _need(p07_sha == "a314cdc732069bca17f73a724cd21295af334d9baead82fcdb13bf0600c5554e",
          "P07_PIN")
    return {
        "status": "PASS_SOURCE_CONTRACT_ONLY",
        "sourceMetadata": source_meta,
        "contractMetadata": doc_meta,
        "allowlist": allowlist,
        "marker": marker,
        "sourceInventories": {"R": r_inventory, "W": w_inventory},
        "designFacts": {
            "status": "DESIGN_ONLY_NOT_RUNTIME_PROOF", "planFields": 44,
            "identityRefs": 4, "longFields": 38, "lifecycleBooleans": 2,
            "workCopies": 35, "constructorLongCopies": 26, "workSlots": 34,
            "freezeUnits": 26, "finalizeUnits": 38, "originalBoundaryUnits": 9,
        },
        "evidence": {"dataBodiesRead": 0, "nativeCalls": 0, "buildCalls": 0,
                      "sessionCalls": 0, "runtimeProof": False},
    }


def main(argv=None):
    try:
        _need(sys.flags.isolated and sys.dont_write_bytecode, "PYTHON_MODE")
        args = _parse_exact_pairs(list(sys.argv[1:] if argv is None else argv))
        result = check_contract(args)
        sys.stdout.write(json.dumps(result, sort_keys=True, separators=(",", ":")) + "\n")
        return 0
    except ContractError as exc:
        code = str(exc) if str(exc).isidentifier() else "CONTRACT"
        sys.stderr.write("FAIL_SOURCE_CONTRACT_ONLY " + code + "\n")
        return 2
    except Exception:
        sys.stderr.write("FAIL_SOURCE_CONTRACT_ONLY INTERNAL\n")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
