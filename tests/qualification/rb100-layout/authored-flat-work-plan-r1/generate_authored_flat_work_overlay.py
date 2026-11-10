"""Pure current-source R7 cleanup derivation for the authored flat work plan."""

from collections import namedtuple
import hashlib
import json
import re

GenerationResult = namedtuple("GenerationResult", "study_source provenance_json")

MAX_SOURCE_BYTES = 4 * 1024 * 1024
MAX_OUTPUT_BYTES = 4 * 1024 * 1024
MAX_PROVENANCE_BYTES = 1024 * 1024

CURRENT_SOURCE_NAME = "CURRENT_STUDY.java"
CURRENT_SOURCE_SHA256 = "9ac45b374c8aeee7a912a4e1452435b71b278aeae0a288e5a75dd82a149c006b"
CURRENT_SOURCE_BYTES = 234954
PRE_OWNERSHIP_SOURCE_SHA256 = "524318f4023fa3b884494657a24dffaac063352cc6e21b4c71bd84e41c0e69e4"
PRE_OWNERSHIP_SOURCE_BYTES = 234907
AUTHORED_MEMBERS_NAME = "AuthoredFlatWorkMembers.java"
AUTHORED_MEMBERS_SHA256 = "8c2c6428e8a4c5ca45d912cd22d76f344c6ab968c11031e6fd0417974b852bde"
AUTHORED_MEMBERS_BYTES = 22592
EXPECTED_OUTPUT_SHA256 = "c8b23f3939f2b866516dc4eaa7a869d22b4fd7e1ed24ef0bbeaf7f113fff4ba5"
EXPECTED_OUTPUT_BYTES = 234978

OWNERSHIP_INSERTION = "    CurrentBodyOwnership.validate(meter,body);\n"
OWNERSHIP_ANCHOR = "    /* One complete placeholder-body accounting pass before the Meter closes. */\n"
INSTALLER_LINE = "    CurrentAssemblyMetadata.installCleanup(meter,result,cleanupNanos);\n"
CAPTURE_MARKER = "static R7WorkPlan captureR7WorkPlan("
DETACHED_CAPTURE_ANCHOR = "    return captureDetachedR7WorkPlan(meter,started,result,\n"

class OverlayError(ValueError):
    """Path-free failure for invalid or unpinned source inputs."""


def _sha256(data):
    return hashlib.sha256(data).hexdigest()


def _source_input(value):
    if type(value) is not bytes:
        raise OverlayError("SOURCE_TYPE")
    if len(value) > MAX_SOURCE_BYTES or len(value) != CURRENT_SOURCE_BYTES:
        raise OverlayError("SOURCE_SIZE")
    if _sha256(value) != CURRENT_SOURCE_SHA256:
        raise OverlayError("SOURCE_PIN")
    if value.startswith(b"\xef\xbb\xbf"):
        raise OverlayError("SOURCE_BOM")
    try:
        decoded = value.decode("ascii")
    except UnicodeDecodeError:
        raise OverlayError("SOURCE_ASCII")
    if "\r" in decoded:
        raise OverlayError("SOURCE_NEWLINE")
    return decoded, {
        "role": CURRENT_SOURCE_NAME,
        "sha256": _sha256(value),
        "bytes": len(value),
        "encoding": "ASCII",
        "lineEndings": "LF",
    }


def _fragment_input(value):
    if type(value) is not bytes or len(value) > MAX_SOURCE_BYTES or not value:
        raise OverlayError("FRAGMENT_TYPE_OR_SIZE")
    if len(value) != AUTHORED_MEMBERS_BYTES:
        raise OverlayError("FRAGMENT_TYPE_OR_SIZE")
    if _sha256(value) != AUTHORED_MEMBERS_SHA256:
        raise OverlayError("FRAGMENT_PIN")
    if value.startswith(b"\xef\xbb\xbf"):
        raise OverlayError("FRAGMENT_BOM")
    try:
        decoded = value.decode("ascii")
    except UnicodeDecodeError:
        raise OverlayError("FRAGMENT_ASCII")
    if "\r" in decoded:
        raise OverlayError("FRAGMENT_NEWLINE")
    if not decoded.endswith("\n") or decoded.endswith("\n\n"):
        raise OverlayError("FRAGMENT_NEWLINE")
    if re.search(r"(?m)^[ \t]*(?:package|import)[ \t]", decoded):
        raise OverlayError("FRAGMENT_IMPORT_OR_PACKAGE")
    for forbidden in (
        "new ProcessBuilder(", "Runtime.getRuntime(", "System.exit(",
        "Class.forName(", "ClassLoader", "getDeclaredField(", "setAccessible(",
        "java.nio.file.Files", "FileInputStream(", "FileOutputStream(",
    ):
        if forbidden in decoded:
            raise OverlayError("FRAGMENT_AUTHORITY")
    return decoded, {
        "role": AUTHORED_MEMBERS_NAME,
        "sha256": _sha256(value),
        "bytes": len(value),
        "encoding": "ASCII",
        "lineEndings": "LF",
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


def _capture_span(text, code):
    return _member_span(text, CAPTURE_MARKER, code)


def _strip_current_ownership(text):
    if text.count(OWNERSHIP_INSERTION) != 1 or text.count(OWNERSHIP_ANCHOR) != 1:
        raise OverlayError("CURRENT_OWNERSHIP_INSERTION")
    insertion_at = text.index(OWNERSHIP_INSERTION)
    anchor_at = text.index(OWNERSHIP_ANCHOR)
    if insertion_at + len(OWNERSHIP_INSERTION) != anchor_at:
        raise OverlayError("CURRENT_OWNERSHIP_INSERTION")
    stripped = text[:insertion_at] + text[anchor_at:]
    stripped_bytes = stripped.encode("ascii")
    if (len(stripped_bytes) != PRE_OWNERSHIP_SOURCE_BYTES
            or _sha256(stripped_bytes) != PRE_OWNERSHIP_SOURCE_SHA256):
        raise OverlayError("CURRENT_PRE_OWNERSHIP_PIN")
    return stripped, insertion_at


def _canonical_provenance(value):
    return (
        json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True) + "\n"
    ).encode("ascii")


def derive_current_flat_work(*, current_source, authored_members_source):
    """Derive the pinned current Study Source using immutable bytes only."""
    current_text, current_meta = _source_input(current_source)
    authored_text, authored_meta = _fragment_input(authored_members_source)

    if INSTALLER_LINE in current_text:
        raise OverlayError("CURRENT_INSTALLER_ALREADY_PRESENT")
    source_without_ownership, ownership_offset = _strip_current_ownership(current_text)
    current_start, current_end = _capture_span(
        source_without_ownership, "CURRENT_CAPTURE_SPAN"
    )
    authored_start, authored_end = _capture_span(authored_text, "TEMPLATE_CAPTURE_SPAN")
    current_capture = source_without_ownership[current_start:current_end]
    authored_capture = authored_text[authored_start:authored_end]
    if authored_capture.count(INSTALLER_LINE) != 1:
        raise OverlayError("TEMPLATE_INSTALLER_PLACEMENT")
    installer_at = authored_capture.index(INSTALLER_LINE)
    if not authored_capture[installer_at + len(INSTALLER_LINE):].startswith(
            DETACHED_CAPTURE_ANCHOR):
        raise OverlayError("TEMPLATE_INSTALLER_PLACEMENT")
    old_authored_capture = (
        authored_capture[:installer_at]
        + authored_capture[installer_at + len(INSTALLER_LINE):]
    )
    if current_capture != old_authored_capture:
        raise OverlayError("CURRENT_CAPTURE_CORE_MISMATCH")

    current_declarations = _direct_declarations(current_capture, 0)
    authored_declarations = _direct_declarations(authored_capture, 0)
    if (current_declarations != authored_declarations
            or current_declarations[0] != ["captureR7WorkPlan"]
            or current_declarations[1] or current_declarations[2]):
        raise OverlayError("CAPTURE_DECLARATION_MISMATCH")

    result_text = _replace_spans(source_without_ownership, [{
        "start": current_start,
        "end": current_end,
        "after": authored_capture,
    }])
    try:
        result_bytes = result_text.encode("ascii")
    except UnicodeEncodeError:
        raise OverlayError("OUTPUT_ASCII")
    if len(result_bytes) > MAX_OUTPUT_BYTES:
        raise OverlayError("OUTPUT_SIZE")
    if (len(result_bytes) != EXPECTED_OUTPUT_BYTES
            or _sha256(result_bytes) != EXPECTED_OUTPUT_SHA256):
        raise OverlayError("OUTPUT_PIN")

    retained_before = (
        source_without_ownership[:current_start]
        + source_without_ownership[current_end:]
    )
    result_start, result_end = _capture_span(result_text, "OUTPUT_CAPTURE_SPAN")
    retained_after = result_text[:result_start] + result_text[result_end:]
    if retained_before != retained_after:
        raise OverlayError("OUTPUT_CONSERVATION")

    capture_core_bytes = current_capture.encode("ascii")
    authored_capture_bytes = authored_capture.encode("ascii")
    provenance = {
        "schema": "TSJ_CURRENT_R7_AUTHORED_SOURCE_R1",
        "status": "SOURCE_ONLY",
        "limits": {
            "maxSourceBytes": MAX_SOURCE_BYTES,
            "maxOutputBytes": MAX_OUTPUT_BYTES,
            "maxProvenanceBytes": MAX_PROVENANCE_BYTES,
        },
        "inputs": {
            "currentStudy": current_meta,
            "authoredMembers": authored_meta,
        },
        "sourceTransformation": {
            "ownershipInsertion": {
                "lineBytes": len(OWNERSHIP_INSERTION.encode("ascii")),
                "lineSha256": _sha256(OWNERSHIP_INSERTION.encode("ascii")),
                "strippedBeforePureDerivation": True,
                "sourceOffset": ownership_offset,
                "retainedForBodyEmitter": True,
            },
            "captureMethod": {
                "name": "captureR7WorkPlan",
                "currentCoreBytes": len(capture_core_bytes),
                "currentCoreSha256": _sha256(capture_core_bytes),
                "authoredBytes": len(authored_capture_bytes),
                "authoredSha256": _sha256(authored_capture_bytes),
                "installerLineBytes": len(INSTALLER_LINE.encode("ascii")),
                "installerLineSha256": _sha256(INSTALLER_LINE.encode("ascii")),
                "coreMatchesUnmodifiedTemplate": True,
                "replacementOnly": True,
            },
            "byteConservation": {
                "retainedBytes": len(retained_before.encode("ascii")),
                "unchangedOutsideCaptureMethod": True,
                "unchangedMembers": [
                    "main_HOLD_body",
                    "assemble_HOLD_body",
                    "captureDetachedR7WorkPlan_API_and_core",
                    "all_non_capture_methods_and_source_bytes",
                ],
            },
        },
        "output": {
            "role": "pre-ownership-current-study",
            "bytes": len(result_bytes),
            "sha256": _sha256(result_bytes),
            "encoding": "ASCII",
            "lineEndings": "LF",
        },
        "limitsAndGates": {
            "qualification": "SOURCE_ONLY",
            "runtimeBehavior": "NOT_CLAIMED",
            "genuineOriginalNullObserve": "UNSPENT_GATED",
        },
    }
    provenance_bytes = _canonical_provenance(provenance)
    if len(provenance_bytes) > MAX_PROVENANCE_BYTES:
        raise OverlayError("PROVENANCE_SIZE")
    return GenerationResult(result_bytes, provenance_bytes)
