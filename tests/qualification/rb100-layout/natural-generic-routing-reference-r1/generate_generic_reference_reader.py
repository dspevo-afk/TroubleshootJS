"""Source-only derivation of the independent natural generic reference reader.

The pinned parent readers are parsed as source, never imported or executed.
Only the byte-pinned public checked-I/O loader/exporter may execute at generation.
"""
from __future__ import annotations

import ast
import hashlib
import json
import os
from pathlib import Path
import stat
import sys
import types

RUN_READER_SHA256 = "84c8dbf2805ab5607f1e79f1d1fa67cc1a2e4ec06d3a22fdd44771ce48d03b52"
TRANSPORT_READER_SHA256 = "685e18d44f322a61d36f1aafa430cb562a5a7f536e2aa9c2640be929881d646b"
FIXTURE_SPEC_SHA256 = "5d2b62c98bd229a73be0e54e4b6adcde4d5080fb87046d8bd770455f80c1224a"
WIRE_SHA256 = "dd738f0bbac0edc4f4cbaf4a0d2bba80ebeda9a581d79c052e29df8976e73dd6"
PLAN_SHA256 = "0dd298d8bb027d983d324806466a38cc9c589e4f699695b7abd3178b2d7db327"
CLARIFICATION_SHA256 = "2c403b4ad8d8b68219096b1dc271988f5cb8f7ed4fd767181deee26b4153585a"
BASE_SHA = "d11df617dc255257e3ac42cba45a9a8d78482f4fec9ea52f846a6f48f97ce1f0"
IO_SHA = "df9c297f4eb491117bade351ceedf26ccef5059e47ad9a7f36006000f1a10923"
MAX_SOURCE_BYTES = 4 * 1024 * 1024
MAX_BASE_BYTES = 256 * 1024
SCHEMA = "tsj-generic-routing-reference-reader-overlay-v1"
CLI_FLAGS = ("--run-reader", "--transport-reader", "--fixture-spec",
             "--base-generator", "--io-helper", "--output-dir")
OUTPUT_NAMES = ("read_generic_routing_reference_v1.py", "SOURCE_PROOF.json")
TRANSPORT_NAMES = ("ReaderError", "need", "integer", "keys", "_tree", "_pairs",
                   "_no_number", "canonical_line", "parse_packet", "_bootstrap_base",
                   "checked_read")
RUN_NAMES = ("exact_keys", "is_int", "stable_cohort_order", "_validate_terminal_failure")
RUN_CONSTANTS = ("MAX_GROUPS", "MAX_GROUP_NS", "MAX_GROUP_UNITS", "MAX_EXPANSIONS",
                 "MAX_VIAS", "MAX_TRANSITIONS", "MAX_COPY_ENTRIES", "COPY_CHUNK")


class OverlayError(ValueError):
    def __init__(self, code):
        self.code = code
        super().__init__(code)


def need(condition, code):
    if not condition:
        raise OverlayError(code)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def replace_once(raw, before, after):
    need(type(raw) is bytes and type(before) is bytes and type(after) is bytes
         and before and raw.count(before) == 1, "SPLICE_COUNT")
    return raw.replace(before, after, 1)


def _pinned(raw, expected, code):
    need(type(raw) is bytes and 0 < len(raw) <= MAX_SOURCE_BYTES, code + "_SIZE")
    need(sha(raw) == expected, code + "_HASH")
    need(b"\r" not in raw and raw.endswith(b"\n"), code + "_LINE_ENDINGS")


def _source_nodes(raw):
    """Top-level byte spans, including exact source spelling and final LF."""
    try:
        tree = ast.parse(raw.decode("utf-8"))
    except (ValueError, SyntaxError, UnicodeError):
        raise OverlayError("PARENT_SOURCE_PARSE") from None
    lines = raw.splitlines(keepends=True)
    result = {}
    for node in tree.body:
        names = []
        if isinstance(node, (ast.FunctionDef, ast.ClassDef)):
            need(not node.decorator_list, "SOURCE_DECORATORS")
            names = [node.name]
        elif isinstance(node, ast.Assign):
            names = [item.id for item in node.targets if isinstance(item, ast.Name)]
        for name in names:
            need(name not in result, "SOURCE_DUPLICATE_NAME")
            result[name] = b"".join(lines[node.lineno - 1:node.end_lineno])
    return result


def _adapt_run(raw):
    """Change only the expected-context seam; mechanical domain stays inherited."""
    result = replace_once(
        raw,
        b"def _validate_run(run, kind: str, captured: dict, cohort_ids) -> dict:\n",
        b"def validate_generic_fixture_run(run, fixture_context):\n"
        b"    _check_fixture_context(fixture_context)\n"
        b"    kind = FIXTURE_KIND\n"
        b"    cohort_ids = fixture_context['cohortNetIds']\n")
    result = replace_once(result, b'    owner = captured["owner"]\n',
                          b'    owner = fixture_context["initial"]\n')
    for before, after in (
        (b'captured["priorPromotedNetId"]', b'owner["priorPromotedNetId"]'),
        (b'initial["capturedOrderingStream"] is owner["orderingStream"]',
         b'initial["capturedOrderingStream"] is owner["capturedOrderingStream"]'),
        (b'initial["instrumentationModeDifference"] is owner["orderingStream"]',
         b'initial["instrumentationModeDifference"] is owner["instrumentationModeDifference"]'),
        (b'    captured_ids = captured["netIds"]\n',
         b'    captured_ids = fixture_context["computedNetIds"]\n'),
        (b'    expected = stable_cohort_order(captured_ids, cohort_ids)\n',
         b'    expected = stable_cohort_order(captured_ids, cohort_ids)\n'
         b'    need(expected == fixture_context["requestedNetIds"], "FIXTURE_REQUESTED_ORDER")\n'),
        (b'reason_net in captured["netIds"]', b'reason_net in fixture_context["computedNetIds"]'),
    ):
        result = replace_once(result, before, after)
    need(b'captured[' not in result and b'owner["orderingStream"]' not in result,
         "EXPECTED_CONTEXT_SEAM")
    # Ordinary-kind terminal accounting remains the inherited 11+traversed.
    anchor = b'    return {"expectedOrder": expected, "ordering": ordering, "outcome": outcome,\n'
    result = replace_once(result, anchor,
        b'    _natural_fixture_result(run, fixture_context, terminal)\n' + anchor)
    return result


def _adapt_tree(raw):
    """Preserve R bounds, tracking structural tokens for one geometry leaf.

    String dictionary keys never become list indices. Only enumerate on a real
    list produces an integer token, so dotted keys, bool/map indices and other
    containers cannot acquire the inherited 4096-character geometry allowance.
    """
    result = raw.replace(b"MAX_BYTES", b"MAX_TREE_VISITS")
    need(result != raw, "TREE_VISIT_SEAM")
    for before, after in (
        (b"    pending = [(value, 0)]\n", b"    pending = [(value, 0, (), False)]\n"),
        (b"        item, depth = pending.pop()\n", b"        item, depth, path, is_key = pending.pop()\n"),
        (b"        kind = type(item)\n        if kind is str:\n",
         b"        kind = type(item)\n"
         b'        geometry_leaf = (not is_key and len(path) == 7\n'
         b'                         and path[0] == "references"\n'
         b'                         and path[1] in ("OPEN", "BLOCKED")\n'
         b'                         and path[2:5] == ("run", "layout", "components")\n'
         b'                         and type(path[5]) is int and path[5] >= 0\n'
         b'                         and path[6] == "geometryCanonical")\n'
         b'        need(not geometry_leaf or kind is str, "GEOMETRY_TEXT_TYPE")\n'
         b"        if kind is str:\n"),
        (b'            need(len(item) <= 256 and all(32 <= ord(c) <= 126 for c in item), "TEXT")\n',
         b'            limit = 4096 if geometry_leaf else 256\n'
         b'            need(len(item) <= limit and all(32 <= ord(c) <= 126 for c in item), "TEXT")\n'),
        (b"                pending.append((key, depth + 1))\n",
         b"                pending.append((key, depth + 1, path, True))\n"),
        (b"                pending.append((child, depth + 1))\n",
         b"                pending.append((child, depth + 1, path + (key,), False))\n"),
        (b"            pending.extend((child, depth + 1) for child in item)\n",
         b"            pending.extend((child, depth + 1, path + (index,), False)\n"
         b"                           for index, child in enumerate(item))\n"),
    ):
        result = replace_once(result, before, after)
    return result


READER_HEADER = b'''"""Independent natural generic reference admission; no execution authentication.

The frozen source fixture is the oracle. This does not admit authored originals,
full D, R7, injected H1, electrical behavior, Player or population qualification.
The CLI is read only; the host owns source/class/process proof and accepted writes.
"""
from __future__ import annotations
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys
import time
import types

MAX_BYTES = 1048576
MAX_TREE_VISITS = 1000000
MAX_INT = 2147483647
MIN_INT = -2147483648
MAX_READER_NS = 180000000000
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\Z")
SHA = re.compile(r"[0-9a-f]{64}\\Z")
FIXTURE_KIND = "GENERIC_ROUTING_REFERENCE_V1"
PACKET_KEYS = ("schema", "status", "runId", "sourceManifestSha256",
               "wireContractSha256", "references", "sourceReview")
REFERENCE_KEYS = ("caseId", "fixtureContextSha256", "run", "cleanup", "construction", "sourceGeometry")
RUN_KEYS = ("kind", "initial", "ordering", "outcome", "terminalFailure", "finalizers", "layout", "work")
INITIAL_KEYS = ("pass", "passes", "expanded", "maximumExpansions", "maximumVias",
                "priorPromotedNetId", "policy", "viaCost", "policyTransitions",
                "policyVias", "branchOrderStrategy", "netOrderStrategy", "roleWidths",
                "capturedOrderingStream", "sessionOrderingStream",
                "instrumentationModeDifference", "routingRelevantSettingsMatch", "traceCount", "holeCount")
ORDERING_KEYS = ("capturedNetIds", "computedNetIds", "requestedCohortNetIds", "requestedNetIds",
                 "traversedNetIds", "orderMatchesCapture", "searchCount")
OUTCOME_KEYS = ("disposition", "reason", "complete", "ownerComplete", "pass", "passes",
                "expanded", "maximumExpansions", "errorClass", "errorMessage")
TERMINAL_KEYS = ("failedNetId", "searchIndex", "searchPass", "passBefore", "passAfter",
                 "advanceCallOrdinal", "ownerSearchCleared", "searchReleased", "ownerPromotedNetId", "ownerReason")
FINALIZER_KEYS = ("routingGeometry", "allNetConnectivity", "twoLayerRules", "routeQuality", "viaCap")
WORK_KEYS = ("advanceCalls", "failureObservationUnits", "terminalEvidenceUnits", "ownerUnits",
             "wrapperUnits", "copyEntries", "copyChunks", "groups", "maxGroupNanos", "maximumUnitsPerGroup")
REVIEW_KEYS = ("scope", "physicalOwnerSha256", "studyParentSha256", "runOwnerSha256",
               "fixtureSourceSha256", "fixtureOracleSha256", "sessionConstructorEntries")
CLI_FLAGS = ("--input", "--run-id", "--source-sha", "--wire-sha", "--fixture-spec", "--base-generator", "--io-helper")

'''


READER_DOMAIN = b'''def load_fixture_spec(raw):
    # Authenticate the entire source oracle before its first JSON parse.
    need(type(raw) is bytes and len(raw) <= MAX_BYTES
         and hashlib.sha256(raw).hexdigest() == FIXTURE_SPEC_SHA256, "FIXTURE_SPEC_HASH")
    try:
        value = json.loads(raw.decode("ascii"), object_pairs_hook=_pairs,
                           parse_float=_no_number, parse_constant=_no_number)
    except ReaderError:
        raise
    except (ValueError, TypeError, UnicodeError, RecursionError):
        raise ReaderError("FIXTURE_SPEC_JSON") from None
    _tree(value)
    need(value == SOURCE_FIXTURE_SPEC, "FIXTURE_SPEC_BINDING")
    return value


def fixture_context(spec, case_id):
    need(type(case_id) is str and case_id in ("OPEN", "BLOCKED")
         and spec == SOURCE_FIXTURE_SPEC, "FIXTURE_CONTEXT")
    return dict(spec["cases"][case_id], initial=spec["initial"], caseId=case_id)


def _check_fixture_context(context):
    need(type(context) is dict and context.get("caseId") in ("OPEN", "BLOCKED"), "FIXTURE_CONTEXT")
    expected = fixture_context(SOURCE_FIXTURE_SPEC, context["caseId"])
    # Canonical bytes keep bool distinct from int, including within nested rows.
    need(canonical_line(context) == canonical_line(expected), "FIXTURE_CONTEXT_BINDING")


def _wire_run_order(run):
    keys(run, RUN_KEYS, "RUN_ORDER")
    for name, expected in (("initial", INITIAL_KEYS), ("ordering", ORDERING_KEYS),
                           ("outcome", OUTCOME_KEYS), ("finalizers", FINALIZER_KEYS), ("work", WORK_KEYS)):
        keys(run[name], expected, "RUN_" + name.upper() + "_ORDER")
    if run["terminalFailure"] is not None:
        keys(run["terminalFailure"], TERMINAL_KEYS, "TERMINAL_ORDER")


def _natural_fixture_result(run, context, terminal):
    expected = context["expectedOutcome"]
    outcome, ordering = run["outcome"], run["ordering"]
    need(outcome["disposition"] == expected["disposition"]
         and outcome["reason"] == expected["reason"]
         and ordering["traversedNetIds"] == expected["traversedNetIds"]
         and is_int(ordering["searchCount"], expected["searchCount"], expected["searchCount"]),
         "NATURAL_FIXTURE_OUTCOME")
    if expected["terminalRequired"]:
        need(type(terminal) is dict, "NATURAL_NO_PATH_TERMINAL_REQUIRED")
        need(terminal["failedNetId"] == "NET_A"
             and terminal["searchIndex"] == len(ordering["traversedNetIds"])
             and ordering["traversedNetIds"][-1] == terminal["failedNetId"], "NATURAL_TERMINAL_FINAL_NET")
    else:
        need(terminal is None, "FOUND_TERMINAL_NULL")
    layout = run["layout"]
    if context["caseId"] == "BLOCKED":
        need(layout is None, "BLOCKED_LAYOUT_NULL")
        return
    need(type(layout) is dict, "OPEN_LAYOUT_REQUIRED")
    dimensions = context["layoutDimensions"]
    need(is_int(layout.get("width"), dimensions["width"], dimensions["width"])
         and is_int(layout.get("height"), dimensions["height"], dimensions["height"]), "FIXTURE_LAYOUT_DIMENSIONS")
    for name in ("outline", "partsTray"):
        expected_rect = [context[name][axis] for axis in ("x", "y", "width", "height")]
        rect = layout.get(name)
        need(type(rect) is list and len(rect) == 4
             and all(is_int(value, target, target) for value, target in zip(rect, expected_rect)), "FIXTURE_LAYOUT_RECTANGLE")
    # These rows come exclusively from the source oracle, not exported layout.
    try:
        validate_complete_layout(layout, context["pads"], context["roleByNet"], context["requestedNetIds"])
    except ConductorError as failure:
        raise ReaderError("CONDUCTOR_" + failure.code) from None
    expected_pads = {pad["padId"]: pad for pad in context["pads"]}
    for pad in layout["pads"]:
        source = expected_pads[pad["padId"]]
        need(source["access"] == "PTH" and pad["attachment"] == "PLATED_THROUGH_HOLE"
             and pad["topCopper"] is True and pad["bottomCopper"] is True,
             "FIXTURE_PTH_BINDING")


def validate_packet(value, expected_run_id, expected_source_sha, expected_wire_sha, fixture_spec_raw):
    """Pure semantic admission only; inputs do not confer execution provenance."""
    need(type(expected_run_id) is str and UUID.fullmatch(expected_run_id) is not None
         and type(expected_source_sha) is str and SHA.fullmatch(expected_source_sha) is not None
         and type(expected_wire_sha) is str and expected_wire_sha == WIRE_SHA256, "EXPECTED_CONTEXT")
    spec = load_fixture_spec(fixture_spec_raw)
    _tree(value)
    keys(value, PACKET_KEYS, "PACKET_KEYS")
    need(value["schema"] == "tsj-generic-routing-reference-v1"
         and value["status"] == "GENERIC_NATURAL_REFERENCES_RECORDED_ONLY"
         and value["runId"] == expected_run_id
         and value["sourceManifestSha256"] == expected_source_sha
         and value["wireContractSha256"] == expected_wire_sha, "PACKET_IDENTITY")
    references = value["references"]
    keys(references, ("OPEN", "BLOCKED"), "REFERENCES")
    for ordinal, case_id in enumerate(("OPEN", "BLOCKED")):
        reference = references[case_id]
        keys(reference, REFERENCE_KEYS, "REFERENCE_KEYS")
        need(reference["caseId"] == case_id
             and reference["fixtureContextSha256"] == FIXTURE_SPEC_SHA256, "REFERENCE_IDENTITY")
        _wire_run_order(reference["run"])
        validate_generic_fixture_run(reference["run"], fixture_context(spec, case_id))
        cleanup = reference["cleanup"]
        keys(cleanup, ("ownerReferencesDropped", "ownerCleanupNanos", "wrapperAliasesDropped"), "CLEANUP_KEYS")
        need(cleanup["ownerReferencesDropped"] is True and cleanup["wrapperAliasesDropped"] is True
             and is_int(cleanup["ownerCleanupNanos"], 0, 120000000000), "CLEANUP_BINDING")
        construction = reference["construction"]
        keys(construction, ("before", "after"), "CONSTRUCTION_KEYS")
        need(is_int(construction["before"], ordinal, ordinal)
             and is_int(construction["after"], ordinal + 1, ordinal + 1), "CONSTRUCTOR_BINDING")
        geometry = reference["sourceGeometry"]
        keys(geometry, ("beforeSha256", "afterSha256", "stable"), "SOURCE_GEOMETRY_KEYS")
        need(type(geometry["beforeSha256"]) is str and SHA.fullmatch(geometry["beforeSha256"]) is not None
             and geometry["beforeSha256"] == geometry["afterSha256"] and geometry["stable"] is True, "SOURCE_GEOMETRY_BINDING")
    review = value["sourceReview"]
    keys(review, REVIEW_KEYS, "REVIEW_KEYS")
    need(review["scope"] == "GENERIC_NATURAL_ROUTING_REFERENCE_ONLY"
         and review["physicalOwnerSha256"] == spec["physicalOwnerSha256"]
         and review["studyParentSha256"] == STUDY_PARENT_SHA256
         and review["runOwnerSha256"] == spec["runOwnerSha256"]
         and review["fixtureSourceSha256"] == spec["fixtureSourceSha256"]
         and review["fixtureOracleSha256"] == FIXTURE_SPEC_SHA256
         and is_int(review["sessionConstructorEntries"], 2, 2), "SOURCE_REVIEW_BINDING")
    return {"schema": "tsj-generic-routing-reference-reader-summary-v1",
            "status": "PASS_NATURAL_GENERIC_REFERENCE_ONLY", "runId": expected_run_id,
            "sourceManifestSha256": expected_source_sha, "wireContractSha256": expected_wire_sha,
            "references": 2, "found": 1, "noPath": 1, "newAuthoredInvocations": 0, "fullD": False}


def parse_args(argv):
    need(type(argv) in (list, tuple) and len(argv) == 2 * len(CLI_FLAGS), "CLI_ARGUMENTS")
    options = {}
    for at in range(0, len(argv), 2):
        flag, value = argv[at:at + 2]
        need(type(flag) is str and flag in CLI_FLAGS and flag not in options
             and type(value) is str and bool(value) and "\\x00" not in value
             and not value.startswith("--"), "CLI_ARGUMENTS")
        options[flag] = value
    need(set(options) == set(CLI_FLAGS), "CLI_ARGUMENTS")
    for flag in ("--input", "--fixture-spec", "--base-generator", "--io-helper"):
        value = options[flag]
        need(Path(value).is_absolute() and str(Path(value)) == value, "IO_PATH")
    need(UUID.fullmatch(options["--run-id"]) is not None
         and SHA.fullmatch(options["--source-sha"]) is not None
         and options["--wire-sha"] == WIRE_SHA256, "EXPECTED_CONTEXT")
    return options


def main(argv=None):
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        sys.stderr.write("SW_PYTHON_ISOLATION_REQUIRED\\n")
        return 2
    started = time.monotonic_ns()
    try:
        options = parse_args(sys.argv[1:] if argv is None else argv)
        fixture_raw = checked_read(options["--fixture-spec"], options["--base-generator"], options["--io-helper"])
        load_fixture_spec(fixture_raw)
        raw = checked_read(options["--input"], options["--base-generator"], options["--io-helper"])
        summary = validate_packet(parse_packet(raw), options["--run-id"], options["--source-sha"], options["--wire-sha"], fixture_raw)
        summary["inputSha256"] = hashlib.sha256(raw).hexdigest()
        summary["inputBytes"] = len(raw)
        line = canonical_line(summary)
        need(time.monotonic_ns() - started <= MAX_READER_NS, "READER_DEADLINE")
        output = sys.stdout.buffer
        need(output.write(line) == len(line), "STDOUT_WRITE")
        output.flush()
        return 0
    except ReaderError as failure:
        sys.stderr.write(failure.code + "\\n")
        return 2
    except Exception:
        sys.stderr.write("SW_INTERNAL\\n")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
'''


def derive(run_reader, transport_reader, fixture_raw):
    """Extract only pure source; returning source/proof does not run a validator."""
    for raw, expected, code in ((run_reader, RUN_READER_SHA256, "RUN_READER"),
                                 (transport_reader, TRANSPORT_READER_SHA256, "TRANSPORT_READER"),
                                 (fixture_raw, FIXTURE_SPEC_SHA256, "FIXTURE_SPEC")):
        _pinned(raw, expected, code)
    spec = json.loads(fixture_raw.decode("ascii"))  # pin checked above
    n, r = _source_nodes(run_reader), _source_nodes(transport_reader)
    sections, extracted = [], []

    def take(parent, name, replacement=None):
        source = parent[name]
        result = source if replacement is None else replacement
        extracted.append({"parent": "RUN" if parent is n else "TRANSPORT", "name": name,
                          "sourceSha256": sha(source), "sourceBytes": len(source),
                          "derivedSha256": sha(result), "derivedBytes": len(result),
                          "byteEqual": source == result})
        sections.append(result + b"\n\n")

    for name in TRANSPORT_NAMES:
        replacement = None
        if name == "_tree":
            replacement = _adapt_tree(r[name])
        take(r, name, replacement)
    for name in RUN_CONSTANTS + RUN_NAMES:
        take(n, name)
    take(n, "_validate_run", _adapt_run(n["_validate_run"]))
    start = run_reader.index(b"class ConductorError(ValueError):\n")
    finish = run_reader.index(b"def _checked_complete_layout(", start)
    conductor = run_reader[start:finish]
    need(conductor.count(b"def validate_complete_layout(") == 1, "CONDUCTOR_CLOSURE")
    for name, source in n.items():
        if name == "ConductorError" or name.startswith("_CONDUCTOR_") or name.startswith("_conductor_") or name == "validate_complete_layout":
            need(source in conductor, "CONDUCTOR_DEPENDENCY")
            extracted.append({"parent": "RUN", "name": name, "sourceSha256": sha(source),
                              "sourceBytes": len(source), "derivedSha256": sha(source),
                              "derivedBytes": len(source), "byteEqual": True})
    bindings = ("BASE_SHA = " + repr(BASE_SHA) + "\nIO_SHA = " + repr(IO_SHA)
                + "\nFIXTURE_SPEC_SHA256 = " + repr(FIXTURE_SPEC_SHA256)
                + "\nWIRE_SHA256 = " + repr(WIRE_SHA256)
                + "\nSTUDY_PARENT_SHA256 = '88986781508aa34783d11e651082122f4bf8e0710a2263b39cc9b597fce9a8cf'\n"
                + "SOURCE_FIXTURE_SPEC = " + repr(spec) + "\n\n").encode("ascii")
    result = READER_HEADER + bindings + b"".join(sections) + conductor + READER_DOMAIN
    need(0 < len(result) <= MAX_SOURCE_BYTES, "DERIVED_SIZE")
    tree = ast.parse(result.decode("ascii"))  # syntax only, never compile/exec
    # This census rejects accidental loader/sys.path/semantic imports in output.
    allowed = {"__future__", "hashlib", "json", "os", "pathlib", "re", "stat", "sys", "time", "types"}
    for item in ast.walk(tree):
        if isinstance(item, ast.Import):
            need(all(alias.name.split(".")[0] in allowed for alias in item.names), "DERIVED_IMPORTS")
        elif isinstance(item, ast.ImportFrom):
            need(item.level == 0 and item.module in allowed, "DERIVED_IMPORTS")
    need(b"sys.path" not in result and result.count(conductor) == 1, "DERIVED_CLOSURE")
    return result, {"extracted": extracted, "conductorClosureSha256": sha(conductor),
                    "conductorClosureBytes": len(conductor), "conductorClosureByteEqual": True}


def load_base_generator(path_value):
    """Identity-checked fixed-pin bootstrap; subsequent I/O belongs to P."""
    try:
        need(type(path_value) is str and "\0" not in path_value, "BASE_PATH")
        path = Path(path_value)
        need(path.is_absolute() and str(path) == path_value, "BASE_PATH")
        for part in (path, *path.parents):
            info = part.lstat()
            need(not stat.S_ISLNK(info.st_mode)
                 and not (getattr(info, "st_file_attributes", 0) & 0x400), "BASE_REPARSE")
        need(str(path.resolve(strict=True)) == str(path), "BASE_CANONICAL")
        before = path.lstat()
        need(stat.S_ISREG(before.st_mode) and 0 < before.st_size <= MAX_BASE_BYTES, "BASE_FILE")
        flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0)
        descriptor = os.open(str(path), flags)
        try:
            opened = os.fstat(descriptor)
            need(stat.S_ISREG(opened.st_mode)
                 and (opened.st_dev, opened.st_ino) == (before.st_dev, before.st_ino), "BASE_IDENTITY")
            with os.fdopen(descriptor, "rb") as stream:
                descriptor = None
                raw = stream.read(MAX_BASE_BYTES + 1)
        finally:
            if descriptor is not None:
                os.close(descriptor)
        need(0 < len(raw) <= MAX_BASE_BYTES and sha(raw) == BASE_SHA, "BASE_HASH")
        module = types.ModuleType("_pinned_generic_reference_public_loader")
        module.__file__ = "<pinned-generic-reference-public-loader>"
        exec(compile(raw, module.__file__, "exec"), module.__dict__)
        return module
    except OverlayError:
        raise
    except Exception:
        raise OverlayError("BASE_READ") from None


def parse_args(argv):
    need(type(argv) in (list, tuple) and len(argv) == 2 * len(CLI_FLAGS), "CLI_ARGUMENTS")
    values = {}
    for at in range(0, len(argv), 2):
        flag, value = argv[at:at + 2]
        need(type(flag) is str and flag in CLI_FLAGS and flag not in values
             and type(value) is str and value and "\0" not in value
             and not value.startswith("--"), "CLI_ARGUMENTS")
        values[flag] = value
    need(set(values) == set(CLI_FLAGS), "CLI_ARGUMENTS")
    return values


def generate(options):
    need(type(options) is dict and set(options) == set(CLI_FLAGS), "GENERATOR_OPTIONS")
    for value in options.values():
        need(type(value) is str and value and "\0" not in value
             and Path(value).is_absolute() and str(Path(value)) == value, "INPUT_CANONICAL")
    base = load_base_generator(options["--base-generator"])
    need(base.IO_HELPER_SHA256 == IO_SHA, "IO_HELPER_PIN")
    io = base.load_io(options["--io-helper"])
    output = io._checked_directory(options["--output-dir"], "OUTPUT_DIRECTORY")
    need(not io._directory_names(output, "OUTPUT_DIRECTORY"), "OUTPUT_NOT_EMPTY")
    run_raw = io._read_regular(options["--run-reader"], MAX_SOURCE_BYTES, "RUN_READER")
    transport_raw = io._read_regular(options["--transport-reader"], MAX_SOURCE_BYTES, "TRANSPORT_READER")
    fixture_raw = io._read_regular(options["--fixture-spec"], MAX_SOURCE_BYTES, "FIXTURE_SPEC")
    reader, facts = derive(run_raw, transport_raw, fixture_raw)
    proof = {"schema": SCHEMA, "status": "GENERATED_SOURCE_ONLY_NOT_EXECUTED",
             "runReaderSha256": sha(run_raw), "transportReaderSha256": sha(transport_raw),
             "fixtureSpecSha256": sha(fixture_raw), "wireContractSha256": WIRE_SHA256,
             "planSha256": PLAN_SHA256, "contractClarificationSha256": CLARIFICATION_SHA256,
             "baseGeneratorSha256": BASE_SHA, "ioHelperSha256": IO_SHA,
             "derivedSha256": sha(reader), "derivedBytes": len(reader),
             "transformations": ["FROZEN_GENERIC_EXPECTED_CONTEXT_SEAM", "NATURAL_NO_PATH_TERMINAL_REQUIRED",
                                 "WIRE_INSERTION_ORDER", "SOURCE_FROZEN_PAD_AND_RECTANGLE_BINDING",
                                 "TRANSPORT_TREE_VISITS_EXACTLY_1000000",
                                 "TRANSPORT_EXACT_STRUCTURAL_COMPONENT_GEOMETRY_4096_ORDINARY_256",
                                 "NEW_READ_ONLY_ENVELOPE_AND_CLI"],
             "treeTextPolicy": {"ordinaryMaximum": 256, "geometryMaximum": 4096,
                                "geometryPath": ["references", "OPEN_OR_BLOCKED", "run", "layout",
                                                 "components", "ACTUAL_LIST_INDEX", "geometryCanonical"],
                                "geometryValueType": "STRING", "indexOrigin": "ENUMERATE_REAL_LIST_ONLY",
                                "keys": "ORDINARY_256", "pathTokens": "TUPLE_NOT_JOINED_TEXT"},
             "authoredInvocations": 0, "fullD": False, "budgets": "UNCHANGED", **facts}
    proof_raw = (json.dumps(proof, sort_keys=True, indent=2) + "\n").encode("ascii")
    files = dict(zip(OUTPUT_NAMES, (reader, proof_raw)))
    for name, raw in files.items():
        output = io._checked_directory(output, "OUTPUT_DIRECTORY")
        io._write_exclusive(output / name, raw, MAX_SOURCE_BYTES, "OUTPUT_FILE")
    output = io._checked_directory(output, "OUTPUT_DIRECTORY")
    names = io._directory_names(output, "OUTPUT_DIRECTORY")
    need(len(names) == 2 and set(names) == set(OUTPUT_NAMES), "OUTPUT_CLOSURE")
    for name, raw in files.items():
        need(io._read_regular(output / name, MAX_SOURCE_BYTES, "OUTPUT_READBACK") == raw, "OUTPUT_READBACK_CHANGED")
    return proof


def main(argv=None):
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        sys.stderr.write("GENERIC_REFERENCE_READER_GENERATOR_FAIL PYTHON_ISOLATION\n")
        return 2
    try:
        proof = generate(parse_args(sys.argv[1:] if argv is None else argv))
    except Exception as failure:
        code = getattr(failure, "code", "INTERNAL_ERROR")
        if type(code) is not str or not code or any(char not in "ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789" for char in code):
            code = "INTERNAL_ERROR"
        sys.stderr.write("GENERIC_REFERENCE_READER_GENERATOR_FAIL " + code + "\n")
        return 2
    sys.stdout.write(json.dumps({"schema": SCHEMA, "status": proof["status"],
                                "derivedSha256": proof["derivedSha256"], "files": 2}, separators=(",", ":")) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
