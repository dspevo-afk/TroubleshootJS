"""Deterministic, in-memory overlay for the frozen authored flat work-plan source."""

from collections import namedtuple
import hashlib
import json
import re

GenerationResult = namedtuple("GenerationResult", "study_source provenance_json")

MAX_SOURCE_BYTES = 4 * 1024 * 1024
MAX_OUTPUT_BYTES = 4 * 1024 * 1024

SOURCE_PINS = {
    "w": ("W_STUDY.java", "1ae31054a430c00bf926ff4deff8091892bae267f05257aa0d869000226f7bd1", 239584),
    "r": ("R_STUDY.java", "88986781508aa34783d11e651082122f4bf8e0710a2263b39cc9b597fce9a8cf", 229578),
    "q": ("Q_AUTHORED_STUDY.java", "d6485b0b0f9c518431d440fcb57e477143d50cae78302c29598d12aaa4601dbd", 233880),
    "shared": ("SHARED_OWNER.java", "4ed1c8561adf1619a8b7ffbd21e23fdf2544dcaafe541af4e5d2b2b1e95cabe4", 182535),
}

_FRAGMENT_MARKERS = (
    ("// @@R5_FLAT_SHAPE_BEGIN@@", "// @@R5_FLAT_SHAPE_END@@"),
    ("// @@R7_FLAT_PLAN_BEGIN@@", "// @@R7_FLAT_PLAN_END@@"),
)
_EXPORT_SIGNATURES = (
    "static R5ExportMetrics exportR5(GenericR7Arguments args,java.util.Map<String,Object> result,\n"
    "        SetupMeter meter,long started,R5Snapshot pre,R5WorkFinalizer finalizer) throws Exception",
    "static R5ExportMetrics exportR5(GenericR7Arguments args,java.util.Map<String,Object> result,\n"
    "        SetupMeter meter,long started,R5Snapshot pre,R5WorkFinalizer finalizer,\n"
    "        R5AfterResultWrite hook) throws Exception",
)
_DENIED_BODY = '{ throw new java.io.IOException("R5_EXPORT_DISABLED"); }'

class OverlayError(ValueError):
    """Path-free failure for invalid or unpinned source inputs."""


def _sha256(data):
    return hashlib.sha256(data).hexdigest()


def _source_input(role, value):
    if type(value) is not bytes:
        raise OverlayError("SOURCE_TYPE")
    name, expected_hash, expected_size = SOURCE_PINS[role]
    if len(value) > MAX_SOURCE_BYTES or len(value) != expected_size:
        raise OverlayError("SOURCE_SIZE")
    if _sha256(value) != expected_hash:
        raise OverlayError("SOURCE_PIN")
    if value.startswith(b"\xef\xbb\xbf"):
        raise OverlayError("SOURCE_BOM")
    try:
        decoded = value.decode("ascii")
    except UnicodeDecodeError:
        raise OverlayError("SOURCE_ASCII")
    crlf = decoded.count("\r\n")
    without_crlf = decoded.replace("\r\n", "")
    if "\r" in without_crlf:
        raise OverlayError("SOURCE_NEWLINE")
    normalized = decoded.replace("\r\n", "\n")
    return name, normalized, {
        "sha256": expected_hash,
        "bytes": expected_size,
        "normalizedSha256": _sha256(normalized.encode("ascii")),
        "newlineCanonicalization": "CRLF_TO_LF" if crlf else "UNCHANGED_LF",
    }


def _fragment_input(value):
    if type(value) is not bytes or len(value) > MAX_SOURCE_BYTES or not value:
        raise OverlayError("FRAGMENT_TYPE_OR_SIZE")
    if value.startswith(b"\xef\xbb\xbf"):
        raise OverlayError("FRAGMENT_BOM")
    try:
        decoded = value.decode("ascii")
    except UnicodeDecodeError:
        raise OverlayError("FRAGMENT_ASCII")
    if "\r" in decoded.replace("\r\n", ""):
        raise OverlayError("FRAGMENT_NEWLINE")
    normalized = decoded.replace("\r\n", "\n")
    if not normalized.endswith("\n"):
        normalized += "\n"
    if re.search(r"(?m)^[ \t]*(?:package|import)[ \t]", normalized):
        raise OverlayError("FRAGMENT_IMPORT_OR_PACKAGE")
    for forbidden in (
        "new ProcessBuilder(", "Runtime.getRuntime(", "System.exit(",
        "Class.forName(", "ClassLoader", "getDeclaredField(", "setAccessible(",
        "java.nio.file.Files", "FileInputStream(", "FileOutputStream(",
    ):
        if forbidden in normalized:
            raise OverlayError("FRAGMENT_AUTHORITY")
    return normalized, {
        "sha256": _sha256(value),
        "bytes": len(value),
        "normalizedSha256": _sha256(normalized.encode("ascii")),
        "newlineCanonicalization": (
            "CRLF_TO_LF" if "\r\n" in decoded else "UNCHANGED_LF"
        ),
    }


def _unique_index(text, marker, code):
    index = text.find(marker)
    if index < 0 or text.find(marker, index + len(marker)) >= 0:
        raise OverlayError(code)
    return index


def _line_start(text, index):
    newline = text.rfind("\n", 0, index)
    return newline + 1


def _opening_brace(text, index, code):
    state = "normal"
    escaped = False
    i = index
    while i < len(text):
        c = text[i]
        n = text[i + 1] if i + 1 < len(text) else ""
        if state == "line":
            if c == "\n":
                state = "normal"
        elif state == "block":
            if c == "*" and n == "/":
                state = "normal"
                i += 1
        elif state == "string" or state == "char":
            if escaped:
                escaped = False
            elif c == "\\":
                escaped = True
            elif (state == "string" and c == '"') or (state == "char" and c == "'"):
                state = "normal"
        else:
            if c == "/" and n == "/":
                state = "line"
                i += 1
            elif c == "/" and n == "*":
                state = "block"
                i += 1
            elif c == '"':
                state = "string"
            elif c == "'":
                state = "char"
            elif c == "{":
                return i
        i += 1
    raise OverlayError(code)


def _matching_brace(text, open_index, code):
    state = "normal"
    escaped = False
    depth = 0
    i = open_index
    while i < len(text):
        c = text[i]
        n = text[i + 1] if i + 1 < len(text) else ""
        if state == "line":
            if c == "\n":
                state = "normal"
        elif state == "block":
            if c == "*" and n == "/":
                state = "normal"
                i += 1
        elif state == "string" or state == "char":
            if escaped:
                escaped = False
            elif c == "\\":
                escaped = True
            elif (state == "string" and c == '"') or (state == "char" and c == "'"):
                state = "normal"
        else:
            if c == "/" and n == "/":
                state = "line"
                i += 1
            elif c == "/" and n == "*":
                state = "block"
                i += 1
            elif c == '"':
                state = "string"
            elif c == "'":
                state = "char"
            elif c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    return i + 1
        i += 1
    raise OverlayError(code)


def _member_span(text, marker, code):
    marker_index = _unique_index(text, marker, code)
    open_index = _opening_brace(text, marker_index + len(marker), code)
    return _line_start(text, marker_index), _matching_brace(text, open_index, code)


def _marked_fragment(text, begin, end, code):
    begin_at = _unique_index(text, begin, code)
    end_at = _unique_index(text, end, code)
    if end_at <= begin_at:
        raise OverlayError(code)
    body_start = text.find("\n", begin_at)
    if body_start < 0 or body_start >= end_at:
        raise OverlayError(code)
    body_start += 1
    body_end = _line_start(text, end_at)
    body = text[body_start:body_end]
    if not body.strip():
        raise OverlayError(code)
    return body.strip("\n")


def _replace_spans(text, replacements):
    ordered = sorted(replacements, key=lambda item: item["start"])
    previous_end = -1
    for item in ordered:
        if item["start"] < previous_end or item["end"] <= item["start"]:
            raise OverlayError("REPLACEMENT_OVERLAP")
        previous_end = item["end"]
    result = text
    for item in reversed(ordered):
        result = result[:item["start"]] + item["after"] + result[item["end"]:]
    return result


def _preserved_span(text, marker, code):
    start, end = _member_span(text, marker, code)
    return text[start:end]


def _class_span(text, class_marker, code):
    return _member_span(text, class_marker, code)


def _code_view(text):
    output = list(text)
    state = "normal"
    escaped = False
    i = 0
    while i < len(text):
        c = text[i]
        n = text[i + 1] if i + 1 < len(text) else ""
        if state == "line":
            if c == "\n":
                state = "normal"
            elif c != "\r":
                output[i] = " "
        elif state == "block":
            if c == "*" and n == "/":
                output[i] = " "
                output[i + 1] = " "
                state = "normal"
                i += 1
            elif c != "\n" and c != "\r":
                output[i] = " "
        elif state == "string" or state == "char":
            if c != "\n" and c != "\r":
                output[i] = " "
            if escaped:
                escaped = False
            elif c == "\\":
                escaped = True
            elif (state == "string" and c == '"') or (state == "char" and c == "'"):
                state = "normal"
        else:
            if c == "/" and n == "/":
                output[i] = " "
                output[i + 1] = " "
                state = "line"
                i += 1
            elif c == "/" and n == "*":
                output[i] = " "
                output[i + 1] = " "
                state = "block"
                i += 1
            elif c == '"':
                output[i] = " "
                state = "string"
            elif c == "'":
                output[i] = " "
                state = "char"
        i += 1
    return "".join(output)


def _direct_declarations(text, depth_target):
    code = _code_view(text)
    method_pattern = re.compile(
        r"^[ \t]*(?:(?:public|protected|private|static|final|synchronized|native|abstract|strictfp)[ \t]+)*"
        r"(?:[A-Za-z_$][\w.$<>\[\], ?]*[ \t]+)+([A-Za-z_$][\w$]*)[ \t]*\("
    )
    type_pattern = re.compile(
        r"^[ \t]*(?:(?:public|protected|private|static|final|abstract)[ \t]+)*"
        r"(?:class|interface|enum)[ \t]+([A-Za-z_$][\w$]*)"
    )
    constant_pattern = re.compile(
        r"^[ \t]*(?:(?:public|protected|private)[ \t]+)?static[ \t]+final[ \t]+"
        r"(?:byte|short|int|long|String)[ \t]+([A-Za-z_$][\w$]*)[ \t]*(?:=|;)"
    )
    methods, types, constants = [], [], []
    depth = 0
    for line in code.splitlines():
        if depth == depth_target:
            match = method_pattern.match(line)
            if match:
                methods.append(match.group(1))
            match = type_pattern.match(line)
            if match:
                types.append(match.group(1))
            match = constant_pattern.match(line)
            if match:
                constants.append(match.group(1))
        for c in line:
            if c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth < 0:
                    raise OverlayError("DECLARATION_BRACE")
    if depth != 0:
        raise OverlayError("DECLARATION_BRACE")
    return methods, types, constants


def _assert_unique_declarations(fragment, retained):
    fragment_methods, fragment_types, fragment_constants = _direct_declarations(fragment, 0)
    fragment_names = fragment_methods + fragment_types + fragment_constants
    if len(fragment_names) != len(set(fragment_names)):
        raise OverlayError("FRAGMENT_DUPLICATE_DECLARATION")
    plan_start, plan_end = _member_span(
        fragment,
        "static final class R7WorkPlan implements R5WorkFinalizer",
        "FRAGMENT_PLAN_CLASS",
    )
    plan_methods, plan_types, plan_constants = _direct_declarations(
        fragment[plan_start:plan_end], 1
    )
    plan_names = plan_methods + plan_types + plan_constants
    if len(plan_names) != len(set(plan_names)):
        raise OverlayError("FRAGMENT_PLAN_DUPLICATE_DECLARATION")
    retained_methods, retained_types, retained_constants = _direct_declarations(retained, 1)
    retained_names = set(retained_methods + retained_types + retained_constants)
    if retained_names.intersection(fragment_names):
        raise OverlayError("FRAGMENT_NAME_COLLISION")
    return {
        "fragmentMethods": sorted(fragment_methods),
        "fragmentTypes": sorted(fragment_types),
        "fragmentConstants": sorted(fragment_constants),
        "planMethods": sorted(plan_methods),
        "planTypes": sorted(plan_types),
        "planConstants": sorted(plan_constants),
        "retainedMethodCount": len(retained_methods),
        "retainedTypeCount": len(retained_types),
        "retainedConstantCount": len(retained_constants),
    }


def _source_closure(r_text):
    shape_start = _unique_index(
        r_text,
        "/* Fixed order, without an application-owned key array or copied map. */",
        "R_SHAPE_START",
    )
    shape_end_start, shape_end = _member_span(
        r_text,
        "static void r5ValidateWork(java.util.Map<?,?> work,R5Snapshot pre,long exportStarted)",
        "R_SHAPE_END",
    )
    shape = r_text[shape_start:shape_end]
    plan_start = _unique_index(
        r_text,
        "private static final int R7_PLAN_FIELD_COPIES=26;",
        "R_PLAN_START",
    )
    plan_class_start, plan_class_end = _member_span(
        r_text,
        "static final class R7WorkPlan implements R5WorkFinalizer",
        "R_PLAN_CLASS",
    )
    if plan_class_start <= plan_start:
        raise OverlayError("R_PLAN_ORDER")
    plan = r_text[plan_start:plan_class_end]
    return shape, plan


def derive_authored_flat_work(*, w_source, r_source, q_source, shared_source,
                              authored_members_source):
    """Overlay pinned source bytes without filesystem, import, or process access."""
    w_name, w_text, w_meta = _source_input("w", w_source)
    r_name, r_text, r_meta = _source_input("r", r_source)
    q_name, q_text, q_meta = _source_input("q", q_source)
    shared_name, shared_text, shared_meta = _source_input("shared", shared_source)
    fragment_text, fragment_meta = _fragment_input(authored_members_source)

    shape = _marked_fragment(
        fragment_text, _FRAGMENT_MARKERS[0][0], _FRAGMENT_MARKERS[0][1],
        "FRAGMENT_SHAPE_MARKER",
    )
    flat_plan = _marked_fragment(
        fragment_text, _FRAGMENT_MARKERS[1][0], _FRAGMENT_MARKERS[1][1],
        "FRAGMENT_PLAN_MARKER",
    )
    r_shape, r_plan = _source_closure(r_text)
    if shape != r_shape.strip("\n"):
        raise OverlayError("R5_SHAPE_CLOSURE_MISMATCH")

    replacements = []
    helper_start = _unique_index(
        w_text, "private static String r7RootKey(int i) {", "W_SHAPE_START"
    )
    helper_end_start, helper_end = _member_span(
        w_text,
        "static void r5ValidateWork(java.util.Map<?,?> work,R5Snapshot pre,long exportStarted)",
        "W_SHAPE_END",
    )
    replacements.append({
        "id": "flat_work_shape_and_equations",
        "start": _line_start(w_text, helper_start),
        "end": helper_end,
        "after": shape + "\n",
    })

    plan_comment = (
        "/* Generic successful-run ledger. Each of four immediate points retains 19"
    )
    plan_start = _unique_index(w_text, plan_comment, "W_GENERIC_PLAN_START")
    plan_end_marker = (
        "/* Nested Java7 members. Prospective R9 transport only; not R5 publication."
    )
    plan_end = _unique_index(w_text, plan_end_marker, "W_R9_BOUNDARY")
    plan_start = _line_start(w_text, plan_start)
    if plan_end <= plan_start:
        raise OverlayError("W_GENERIC_PLAN_SPAN")
    generic_plan = w_text[plan_start:plan_end]
    for sentinel in (
        "private static final int R7_PHASE_COUNTER_READS=19;",
        "static final class R7MeterPoint {",
        "static final class R7MeterLedger {",
        "static final class R7WorkPlan implements R5WorkFinalizer {",
        "static R7WorkPlan captureR7WorkPlan(R7MeterLedger ledger,SetupMeter meter,long started,",
    ):
        if generic_plan.count(sentinel) != 1:
            raise OverlayError("W_GENERIC_PLAN_CONTENT")
    replacements.append({
        "id": "authored_flat_work_plan_and_real_capture",
        "start": plan_start,
        "end": plan_end,
        "after": flat_plan + "\n",
    })

    for index, signature in enumerate(_EXPORT_SIGNATURES):
        open_start = _unique_index(w_text, signature, "W_EXPORT_OVERLOAD")
        open_index = _opening_brace(w_text, open_start + len(signature), "W_EXPORT_BODY")
        close_end = _matching_brace(w_text, open_index, "W_EXPORT_BODY")
        replacements.append({
            "id": "deny_generic_export_r5_overload_" + str(index + 1),
            "start": open_index,
            "end": close_end,
            "after": _DENIED_BODY,
        })

    original_replacements = []
    for item in sorted(replacements, key=lambda part: part["start"]):
        before = w_text[item["start"]:item["end"]]
        original_replacements.append({
            "id": item["id"],
            "start": item["start"],
            "end": item["end"],
            "beforeSha256": _sha256(before.encode("ascii")),
            "beforeBytes": len(before.encode("ascii")),
            "afterSha256": _sha256(item["after"].encode("ascii")),
            "afterBytes": len(item["after"].encode("ascii")),
        })

    preserved_markers = (
        ("parent", "static final class Parent {"),
        ("setupMeter", "static final class SetupMeter {"),
        ("adopted_snapshot_allocation_and_class", "/* Allocate during measured metadata."),
    )
    preserved_before = {}
    for label, marker in preserved_markers:
        if label == "adopted_snapshot_allocation_and_class":
            allocate_start = _unique_index(w_text, marker, "W_SNAPSHOT_ALLOC")
            class_start, class_end = _member_span(
                w_text, "static final class R5Snapshot {", "W_SNAPSHOT_CLASS"
            )
            start = _line_start(w_text, allocate_start)
            if class_start <= start:
                raise OverlayError("W_SNAPSHOT_ORDER")
            preserved_before[label] = w_text[start:class_end]
        else:
            start, end = _class_span(w_text, marker, "W_PRESERVED_CLASS")
            preserved_before[label] = w_text[start:end]

    main_start, main_end = _member_span(w_text, "public static void main(String[] args) throws Exception", "W_MAIN")
    main_source = w_text[main_start:main_end]
    if "throw new IllegalStateException(\"ADOPTED_SNAPSHOT_COMPONENT_ONLY\");" not in main_source:
        raise OverlayError("W_MAIN_NOT_DENIED")
    preserved_before["denied_whole_study_main"] = main_source

    namespace_replacements = [
        dict(item, after="")
        for item in replacements
        if item["id"] in (
            "flat_work_shape_and_equations",
            "authored_flat_work_plan_and_real_capture",
        )
    ]
    if len(namespace_replacements) != 2:
        raise OverlayError("NAMESPACE_REPLACEMENT_SET")
    retained_namespace = _replace_spans(w_text, namespace_replacements)
    namespace_declarations = _assert_unique_declarations(
        shape + "\n" + flat_plan, retained_namespace
    )
    namespace_audit = {
        "retainedSourceSha256": _sha256(retained_namespace.encode("ascii")),
        "retainedSourceBytes": len(retained_namespace.encode("ascii")),
        "removedReplacementIds": [
            "flat_work_shape_and_equations",
            "authored_flat_work_plan_and_real_capture",
        ],
        "comparison": "fragment-direct-members-versus-original-W-direct-members-after-only-replaced-declaration-blocks-removed",
        "declarations": namespace_declarations,
    }
    result = _replace_spans(w_text, replacements)
    if not result.endswith("\n"):
        result += "\n"
    try:
        result_bytes = result.encode("ascii")
    except UnicodeEncodeError:
        raise OverlayError("EMITTED_ASCII")
    if len(result_bytes) > MAX_OUTPUT_BYTES:
        raise OverlayError("EMITTED_SIZE")
    for obsolete in (
        "R7MeterPoint", "R7MeterLedger", "r7RootKey(", "r7PhaseKey(",
        "r7PreExportKey(", "r7LimitKey(", "r7OrderedShape(",
    ):
        if obsolete in result:
            raise OverlayError("GENERIC_PLAN_REMAINS")
    if result.count(_DENIED_BODY) != 2:
        raise OverlayError("EXPORT_DENIALS")

    preserved_after = {}
    for label, marker in preserved_markers:
        if label == "adopted_snapshot_allocation_and_class":
            allocate_start = _unique_index(result, marker, "EMITTED_SNAPSHOT_ALLOC")
            class_start, class_end = _member_span(
                result, "static final class R5Snapshot {", "EMITTED_SNAPSHOT_CLASS"
            )
            start = _line_start(result, allocate_start)
            preserved_after[label] = result[start:class_end]
        else:
            start, end = _class_span(result, marker, "EMITTED_PRESERVED_CLASS")
            preserved_after[label] = result[start:end]
        if preserved_after[label] != preserved_before[label]:
            raise OverlayError("PRESERVED_ADAPTER_CHANGED")

    main_after_start, main_after_end = _member_span(
        result, "public static void main(String[] args) throws Exception", "EMITTED_MAIN"
    )
    main_after = result[main_after_start:main_after_end]
    if main_after != main_source:
        raise OverlayError("PRESERVED_MAIN_CHANGED")
    preserved_after["denied_whole_study_main"] = main_after

    r_shape_hash = _sha256(r_shape.encode("ascii"))
    r_plan_hash = _sha256(r_plan.encode("ascii"))
    if not q_text or not shared_text:
        raise OverlayError("EMPTY_PINNED_SOURCE")

    provenance = {
        "schema": "tsj-authored-flat-work-overlay-v1",
        "status": "SOURCE_DERIVATION_ONLY",
        "limits": {
            "maxSourceBytes": MAX_SOURCE_BYTES,
            "maxOutputBytes": MAX_OUTPUT_BYTES,
        },
        "inputs": {
            "w": dict({"role": w_name}, **w_meta),
            "r": dict({"role": r_name}, **r_meta),
            "q": dict({"role": q_name}, **q_meta),
            "shared": dict({"role": shared_name}, **shared_meta),
            "authoredMembers": dict({"role": "AuthoredFlatWorkMembers.java"}, **fragment_meta),
        },
        "sourceClosures": {
            "rFlatShapeAndEquations": {
                "sha256": r_shape_hash,
                "bytes": len(r_shape.encode("ascii")),
                "identicalToAuthoredFragment": True,
            },
            "rFlatPlanAndCapture": {
                "sha256": r_plan_hash,
                "bytes": len(r_plan.encode("ascii")),
                "transformations": [
                    "retain40charged_source_inspections_and11_checked_additions",
                    "delegate_detached_values_to_shared_plan_owner",
                    "add_body_identity_reference_and_single_body_work_check",
                    "prepay_work35_snapshot1_plan1_copy26_closed19_freeze26_finalize38_copy34",
                    "restrict_reflected_numeric_values_to_Integer_or_Long",
                ],
            },
        },
        "flatPlanContract": {
            "detachedApi": {
                "name": "captureDetachedR7WorkPlan",
                "visibility": "package-private static",
                "fixedPrefix": ["meter", "started", "body"],
                "scalarOrder": [
                    "setupUnits", "setupGroups", "setupCopies", "setupChunks",
                    "originalOwnerUnits", "originalWrapperUnits", "originalCallbackUnits",
                    "originalDiagnosticUnits", "originalGroups", "originalOwnerGroups",
                    "originalCopies", "originalChunks", "nullOwnerUnits", "nullWrapperUnits",
                    "nullObserverUnits", "observeOwnerUnits", "observeWrapperUnits",
                    "observeObserverUnits", "observerUnits", "cleanupNanos", "freshGroups",
                    "freshCopies", "freshChunks", "originalMaxNanos", "originalMaxUnits",
                ],
            },
            "instanceFields": {
                "identityReferences": 4,
                "constructorLongs": 26,
                "derivedLongs": 12,
                "lifecycleBooleans": 2,
                "total": 44,
            },
            "prepayment": {
                "sourceInspections": 40,
                "captureAdditions": 11,
                "workEntryCopies": 35,
                "snapshotAllocation": 1,
                "planAllocation": 1,
                "planScalarCopies": 26,
                "closedSnapshotInspections": 19,
                "freezeUnits": 26,
                "finalizeUnits": 38,
                "finalizeEntryCopies": 34,
            },
        },
        "preservedWSourceSpans": {
            label: {
                "sha256": _sha256(span.encode("ascii")),
                "bytes": len(span.encode("ascii")),
                "unchangedAfterOverlay": True,
            }
            for label, span in preserved_before.items()
        },
        "retainedNamespaceAudit": namespace_audit,
        "replacements": original_replacements,
        "emission": {
            "parentSha256": w_meta["sha256"],
            "parentNormalizedSha256": w_meta["normalizedSha256"],
            "sha256": _sha256(result_bytes),
            "bytes": len(result_bytes),
            "encoding": "UTF-8",
            "lineEndings": "LF",
            "genericR5ExportsDenied": 2,
            "wholeStudyMain": "PRESERVED_DENIED",
        },
        "limitsAndGates": {
            "actualSourceDetachment": "SOURCE_ONLY_40_INSPECTIONS_11_ADDITIONS",
            "qOriginalWrapperBoundary": "PINNED_ONLY_5_READS_4_ARITHMETIC_OPS_PORT_GATED",
            "detachedCanaryDemand": "SYNTHETIC_ONLY",
            "r5ExportMigration": "DENIED",
            "genuineOriginalNullObserve": "UNSPENT_GATED",
            "playable100": 0,
            "full30": "EXCLUDED",
            "hardPsychotic": "UNAVAILABLE_UNRUN",
            "beta": "HELD",
        },
    }
    provenance_bytes = (
        json.dumps(provenance, sort_keys=True, separators=(",", ":"), ensure_ascii=True) + "\n"
    ).encode("ascii")
    if len(provenance_bytes) > MAX_OUTPUT_BYTES:
        raise OverlayError("PROVENANCE_SIZE")
    return GenerationResult(result_bytes, provenance_bytes)
