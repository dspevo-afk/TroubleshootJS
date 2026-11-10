"""Independent Source/API/CLI contract tests for current arm read metering.

These tests never execute Java or invoke an owner producer. Runtime Meter/List
fixtures belong to the native canary and root-owned gates.
"""
from __future__ import annotations

import argparse
import ast
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
import types
import unittest


HERE = Path(__file__).resolve().parent
GENERATOR_NAME = "generate_current_arm_reads.py"
GENERATOR_PATH = HERE / GENERATOR_NAME
FRAGMENT_NAME = "CurrentArmReadMetering.java.fragment"
FRAGMENT_PATH = HERE / FRAGMENT_NAME

SOURCE_NAME = "CohortTerminalTelemetryStudy.java"
IO_SOURCE_NAME = "generate_original_dto_metering.py"
OUTPUT_NAME = SOURCE_NAME

SOURCE_BYTES = 235950
SOURCE_SHA256 = "f25010169adb2e4934a1303dc076cb948ba5f0c7160603a1fecd6b060e916541"
IO_SOURCE_BYTES = 20512
IO_SOURCE_SHA256 = "d4d7c547037f466d2b2fc0217c0048584e20fed06ff035414b0dbe4fd6fc69a6"
GENERATOR_BYTES = 26616
GENERATOR_SHA256 = "9c5a73879ea63e2b0a252f963ec0168aba59080261fc94b5b10f481d2a4f0749"

# Root filled these pins after the independent static splice review. Keep the
# explicit pending guard so no later edit can silently run an unpinned build.
ROOT_PIN_PENDING = False
FRAGMENT_BYTES = 14125
FRAGMENT_SHA256 = "5f5a569b10f3579de8f700e84496c8762450531e986e732e338ec79133c60222"
OUTPUT_BYTES = 236944
OUTPUT_SHA256 = "90f4c227d125587a7d1c8d3b324fe7fd375fd89af7a8a34a83a65a3c0d310bad"
ROOT_STATIC_EMISSION_SHA256 = "90f4c227d125587a7d1c8d3b324fe7fd375fd89af7a8a34a83a65a3c0d310bad"

SECTION_NAMES = (
    "executeArmWithProfile",
    "containsMetered",
    "armListRead",
    "validateObservation",
    "r9CanaryArmDto",
)
SECTION_PREFIX = b"// TSJ_ARM_READ_SECTION "
SECTION_END = b"// TSJ_ARM_READ_END"

SOURCE_SPAN_PINS = {
    "executeArmWithProfile": {
        "start": 61964,
        "end": 65145,
        "bytes": 3181,
        "sha256": "fa54d5b0bf1c8657a731a8085978d2d29306dcc935c4a9997cea2b87b385e527",
        "trailingLf": 2,
        "startMarker": (
            b'@SuppressWarnings("unchecked")\n'
            b'private static ArmResult executeArmWithProfile('
        ),
        "endMarker": b"static final class R9FixtureLegacy {",
        "signature": b"private static ArmResult executeArmWithProfile(",
    },
    "containsMetered": {
        "start": 76827,
        "end": 77130,
        "bytes": 303,
        "sha256": "dc5205492d20d0a9240236b1bbd511130f086307501a367f434d7b15f9985b45",
        "trailingLf": 1,
        "startMarker": b"    static boolean containsMetered(",
        "endMarker": (
            b'    @SuppressWarnings("unchecked")\n'
            b'    static void validateObservation('
        ),
        "signature": b"static boolean containsMetered(",
    },
    "validateObservation": {
        "start": 77130,
        "end": 85525,
        "bytes": 8395,
        "sha256": "c0d3ba45d1c3f5198934e088a2e472dfcda1360b133342566d0d41658f845cba",
        "trailingLf": 1,
        "startMarker": (
            b'    @SuppressWarnings("unchecked")\n'
            b'    static void validateObservation('
        ),
        "endMarker": b"    static boolean equalMetered(",
        "signature": b"static void validateObservation(",
    },
    "r9CanaryArmDto": {
        "start": 234018,
        "end": 235043,
        "bytes": 1025,
        "sha256": "44155741db6a61d8cfd6bda0431fe30c5b20fff651af3eb645fcc5c3349856bc",
        "trailingLf": 1,
        "startMarker": b"private static Map<String,Object> r9CanaryArmDto(",
        "endMarker": b"static void runR9OwnerCanaries(",
        "signature": b"private static Map<String,Object> r9CanaryArmDto(",
    },
}

RAW_SECTION_BYTES = {
    "executeArmWithProfile": 3506,
    "containsMetered": 399,
    "validateObservation": 8722,
    "r9CanaryArmDto": 1104,
}
REPLACEMENT_PINS = {
    "executeArmWithProfile": (
        3506,
        "07706ed71864e101762939f1e6686e90ee7c2da66024b4b8f57efb9b933a7049",
    ),
    "containsMetered": (
        568,
        "072c3e0b8f2dc2c65f01f8112a280d46696a9e38629caf487ac9f98a0f85b70d",
    ),
    "validateObservation": (
        8721,
        "2f119eb664aaa8caecf276a925a27b42b3ba73265491a50bb025b0e8270a9cee",
    ),
    "r9CanaryArmDto": (
        1103,
        "541018d6b77e07cda128cb6ed67f385c0e76c19b22fa44b003267687f8b4e878",
    ),
}

EXPECTED_EXECUTE_FIELD_READS = Counter(
    [
        (b"legacy", b"board"),
        (b"legacy", b"template"),
        (b"cohort", b"requested"),
        (b"cohort", b"requested"),
        (b"cohort", b"requested"),
        (b"cohort", b"netIds"),
        (b"cohort", b"netIds"),
        (b"fresh", b"referencesDropped"),
        (b"fresh", b"referencesDropped"),
        (b"fresh", b"json"),
        (b"fresh", b"cleanupNanos"),
    ]
)
EXPECTED_EXECUTE_MAP_READS = Counter(
    [
        (b"run", b"outcome"),
        (b"outcome", b"disposition"),
        (b"observation", b"state"),
    ]
)
EXPECTED_VALIDATOR_MAP_READS = Counter(
    [
        (b"run", b"outcome"),
        (b"run", b"work"),
        (b"run", b"ordering"),
        (b"obs", b"callbackCount"),
        (b"obs", b"state"),
        (b"outcome", b"disposition"),
        (b"ordering", b"traversedNetIds"),
        (b"obs", b"firstNetOrdinal"),
        (b"obs", b"lastNetOrdinal"),
        (b"obs", b"lastCallbackTotalExpanded"),
        (b"obs", b"selectedSnapshot"),
        (b"outcome", b"reason"),
        (b"run", b"terminalFailure"),
        (b"snapshot", b"netId"),
        (b"snapshot", b"terminalReason"),
        (b"obs", b"lastNetOrdinal"),
        (b"outcome", b"reason"),
        (b"run", b"terminalFailure"),
    ]
)
EXPECTED_R9_FIELD_READS = Counter(
    [
        (b"arm", b"fresh"),
        (b"fresh", b"json"),
        (b"arm", b"observation"),
        (b"arm", b"cleanup"),
    ]
)

_SECTION_RE = re.compile(rb"(?m)^// TSJ_ARM_READ_SECTION ([A-Za-z0-9_]+)\n")
_R7_READ_RE = re.compile(
    rb'r7ReadObject\(m,([A-Za-z_$][A-Za-z0-9_$]*),"([A-Za-z0-9_$]+)"\)'
)
_MAP_READ_RE = re.compile(
    rb'originalDtoMapRead\(m,([A-Za-z_$][A-Za-z0-9_$]*),"([A-Za-z0-9_$]+)"\)'
)
_LIST_READ_RE = re.compile(
    rb'armListRead\(m,requested,([A-Za-z0-9_+\-]+)\)'
)


def _sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _unique_index(raw: bytes, token: bytes, description: str) -> int:
    first = raw.find(token)
    if first < 0 or raw.find(token, first + 1) >= 0:
        raise AssertionError("expected one " + description)
    return first


def _matching_brace_end(raw: bytes, opening: int) -> int:
    if opening < 0 or raw[opening:opening + 1] != b"{":
        raise AssertionError("expected opening Java brace")
    depth = 0
    state = 0
    index = opening
    while index < len(raw):
        current = raw[index]
        following = raw[index + 1] if index + 1 < len(raw) else 0
        if state == 1:
            if current == 0x0A:
                state = 0
        elif state == 2:
            if current == 0x2A and following == 0x2F:
                state = 0
                index += 1
        elif state in (3, 4):
            quote = 0x22 if state == 3 else 0x27
            if current == 0x5C:
                index += 1
            elif current == quote:
                state = 0
        else:
            if current == 0x2F and following == 0x2F:
                state = 1
                index += 1
            elif current == 0x2F and following == 0x2A:
                state = 2
                index += 1
            elif current == 0x22:
                state = 3
            elif current == 0x27:
                state = 4
            elif current == 0x7B:
                depth += 1
            elif current == 0x7D:
                depth -= 1
                if depth == 0:
                    return index + 1
                if depth < 0:
                    raise AssertionError("unbalanced Java braces")
        index += 1
    raise AssertionError("unterminated Java method")


def _method_end(raw: bytes, signature: bytes) -> int:
    declaration = _unique_index(raw, signature, "method signature")
    opening = raw.find(b"{", declaration + len(signature))
    return _matching_brace_end(raw, opening)


def _read_old_spans(study_source: bytes) -> dict:
    spans = {}
    for name, pin in SOURCE_SPAN_PINS.items():
        if study_source.count(pin["startMarker"]) != 1:
            raise AssertionError("expected one start marker for " + name)
        start = study_source.find(pin["startMarker"])
        end = _unique_index(study_source, pin["endMarker"], name + " end marker")
        old = study_source[start:end]
        if (start != pin["start"] or end != pin["end"]
                or len(old) != pin["bytes"] or _sha256(old) != pin["sha256"]):
            raise AssertionError("baseline span pin mismatch for " + name)
        method_end = _method_end(old, pin["signature"])
        tail = old[method_end:]
        if tail != b"\n" * pin["trailingLf"]:
            raise AssertionError("unexpected old span tail for " + name)
        spans[name] = {
            "start": start,
            "end": end,
            "old": old,
            "method": old[:method_end],
            "tail": tail,
            "trailingLf": pin["trailingLf"],
        }
    return spans


def _parse_fragment_independently(fragment: bytes) -> dict:
    markers = list(_SECTION_RE.finditer(fragment))
    names = tuple(match.group(1).decode("ascii") for match in markers)
    if names != SECTION_NAMES:
        raise AssertionError("fragment section order or names changed")
    if fragment.count(SECTION_PREFIX) != len(SECTION_NAMES):
        raise AssertionError("fragment contains extra section markers")
    if fragment.count(SECTION_END) != 1 or not fragment.endswith(SECTION_END):
        raise AssertionError("fragment end marker or trailing bytes changed")
    end_at = len(fragment) - len(SECTION_END)
    sections = {}
    for index, marker in enumerate(markers):
        end = markers[index + 1].start() if index + 1 < len(markers) else end_at
        sections[names[index]] = fragment[marker.end():end]
    return sections


SECTION_SIGNATURES = {
    "executeArmWithProfile": b"private static ArmResult executeArmWithProfile(",
    "containsMetered": b"static boolean containsMetered(",
    "armListRead": b"private static <T> T armListRead(",
    "validateObservation": b"static void validateObservation(",
    "r9CanaryArmDto": b"private static Map<String,Object> r9CanaryArmDto(",
}
SECTION_DECLARATION_PREFIXES = {
    "executeArmWithProfile": (
        b'@SuppressWarnings("unchecked")\n'
        b"private static ArmResult executeArmWithProfile("
    ),
    "containsMetered": b"    static boolean containsMetered(",
    "armListRead": b"    private static <T> T armListRead(",
    "validateObservation": (
        b'    @SuppressWarnings("unchecked")\n'
        b"    static void validateObservation("
    ),
    "r9CanaryArmDto": b"private static Map<String,Object> r9CanaryArmDto(",
}


def _section_methods(sections: dict) -> dict:
    methods = {}
    for name in SECTION_NAMES:
        raw = sections[name]
        body = raw.rstrip(b"\n")
        prefix = SECTION_DECLARATION_PREFIXES[name]
        signature = SECTION_SIGNATURES[name]
        if not body.startswith(prefix) or body.count(signature) != 1:
            raise AssertionError("section declaration mismatch for " + name)
        if _method_end(body, signature) != len(body):
            raise AssertionError("unexpected bytes after section method " + name)
        methods[name] = body
    return methods


def _replacement_records(spans: dict, methods: dict) -> tuple[dict, list, bytes]:
    replacements = {}
    for name in (
        "executeArmWithProfile",
        "validateObservation",
        "r9CanaryArmDto",
    ):
        replacements[name] = methods[name] + spans[name]["tail"]
    replacements["containsMetered"] = (
        methods["containsMetered"]
        + b"\n\n"
        + methods["armListRead"]
        + spans["containsMetered"]["tail"]
    )

    for name, (expected_bytes, expected_sha) in REPLACEMENT_PINS.items():
        replacement = replacements[name]
        if len(replacement) != expected_bytes or _sha256(replacement) != expected_sha:
            raise AssertionError("reviewed replacement pin mismatch for " + name)

    ordered = sorted(spans.items(), key=lambda item: item[1]["start"])
    return replacements, [name for name, _span in ordered], b""


def _expected_output(study_source: bytes, spans: dict, replacements: dict) -> tuple[bytes, list, bytes]:
    ordered = sorted(spans.items(), key=lambda item: item[1]["start"])
    parts = []
    segments = []
    cursor = 0
    for name, span in ordered:
        outside = study_source[cursor:span["start"]]
        segments.append({
            "beforeMethod": name,
            "bytes": len(outside),
            "sha256": _sha256(outside),
        })
        parts.extend((outside, replacements[name]))
        cursor = span["end"]
    final = study_source[cursor:]
    segments.append({
        "afterMethod": ordered[-1][0],
        "bytes": len(final),
        "sha256": _sha256(final),
    })
    parts.append(final)
    outside_bytes = b"".join(
        [study_source[:ordered[0][1]["start"]]]
        + [
            study_source[left[1]["end"]:right[1]["start"]]
            for left, right in zip(ordered, ordered[1:])
        ]
        + [final]
    )
    return b"".join(parts), segments, outside_bytes


def _expected_provenance(study_source: bytes, fragment: bytes,
                         spans: dict, methods: dict,
                         replacements: dict, segments: list,
                         outside_bytes: bytes) -> dict:
    method_records = {}
    for name in (
        "executeArmWithProfile",
        "containsMetered",
        "validateObservation",
        "r9CanaryArmDto",
    ):
        old = spans[name]
        after = methods[name]
        replacement = replacements[name]
        method_records[name] = {
            "before": {
                "bytes": len(old["method"]),
                "sha256": _sha256(old["method"]),
            },
            "after": {
                "bytes": len(after),
                "sha256": _sha256(after),
            },
            "oldSpanBytes": len(old["old"]),
            "oldSpanSha256": _sha256(old["old"]),
            "oldTrailingLf": old["trailingLf"],
            "replacementBytes": len(replacement),
            "replacementSha256": _sha256(replacement),
        }

    helper = methods["armListRead"]
    return {
        "format": "TSJ_CURRENT_ARM_READS_SOURCE_PROVENANCE_R1",
        "status": "PASS_SOURCE_TRANSFORM_ONLY",
        "inputs": [
            {
                "role": "current_study_source",
                "bytes": SOURCE_BYTES,
                "sha256": SOURCE_SHA256,
            },
            {
                "role": "metered_fragment",
                "bytes": FRAGMENT_BYTES,
                "sha256": FRAGMENT_SHA256,
            },
        ],
        "methods": method_records,
        "helper": {
            "name": "armListRead",
            "bytes": len(helper),
            "sha256": _sha256(helper),
        },
        "conservedOutside": {
            "bytes": len(outside_bytes),
            "sha256": _sha256(outside_bytes),
            "segments": segments,
        },
        "sourceOnlyPrice": {
            "executeArmWithProfile": {
                "NULL_fieldAndCohortReads": 7,
                "OBSERVE_fieldAndCohortReads": 9,
                "OBSERVE_mapReads_nonQuality": 2,
                "OBSERVE_mapReads_quality": 3,
                "successfulStateReplacement": {
                    "addedUnits": 2,
                    "copyEntries": 1,
                    "copyChunks": 1,
                },
                "deeperCalleesIncluded": False,
            },
            "validateObservation": {
                "successfulDirectReadTotals": {
                    "NO_FAILURE": 8,
                    "NON_BRANCH_REJECTION": 10,
                    "FIRST_NON_FOUND": 13,
                },
                "callbacksPositiveAdditionalMapReads": 3,
                "callbacksPositiveAdditionalListArgumentReads": 2,
                "callbacksPositiveAdditionalReadsTotal": 5,
                "guardPredicateAndSelectedHeadReadsIncluded": False,
            },
            "containsMetered": {
                "prepaidTraversalUnits": "2*n+3",
                "sizeObservations": 1,
                "iteratorCreations": 1,
                "hasNextProbes": "n+1",
                "nextAdvances": "n",
                "comparisonGuardUnitsPerVisitedEntry": 1,
                "fullBoundPrepaidOnEarlyMatch": True,
            },
            "r9CanaryArmDto": {
                "sourceOnlyAdditionalUnits": 3,
                "freshFieldReads": 1,
                "observationCleanupFieldReads": 2,
            },
        },
        "output": {
            "role": OUTPUT_NAME,
            "bytes": OUTPUT_BYTES,
            "sha256": OUTPUT_SHA256,
        },
    }


def _guard_blocks(method: bytes) -> list:
    blocks = []
    cursor = 0
    marker = b"new Guard()"
    while True:
        start = method.find(marker, cursor)
        if start < 0:
            return blocks
        opening = method.find(b"{", start + len(marker))
        end = _matching_brace_end(method, opening)
        blocks.append(method[start:end])
        cursor = end


def _extract_unchanged_method(source: bytes, signature: bytes,
                              start_marker: bytes | None = None) -> bytes:
    signature_at = _unique_index(source, signature, "unchanged method signature")
    if start_marker is None:
        start = source.rfind(b"\n", 0, signature_at) + 1
    else:
        start = _unique_index(source, start_marker, "unchanged method start")
    end = _method_end(source[start:], signature)
    return source[start:start + end]


def _load_private_generator(path: Path):
    raw = path.read_bytes()
    if len(raw) != GENERATOR_BYTES or _sha256(raw) != GENERATOR_SHA256:
        raise AssertionError("current arm generator source pin mismatch")
    module = types.ModuleType("_private_current_arm_read_generator_r1")
    module.__file__ = str(path)
    module.__package__ = ""
    sys.modules[module.__name__] = module
    exec(compile(raw, str(path), "exec", dont_inherit=True), module.__dict__)
    return module, raw


def _make_cli_roots(root: Path):
    source_root = root / "source-root"
    component_root = root / "component-root"
    io_root = root / "io-source-root"
    output_root = root / "output-root"
    for path in (source_root, component_root, io_root, output_root):
        path.mkdir()
    return source_root, component_root, io_root, output_root


def _write_cli_inputs(source_root: Path, component_root: Path,
                      io_root: Path, study: bytes, fragment: bytes,
                      io_source: bytes):
    (source_root / SOURCE_NAME).write_bytes(study)
    (component_root / FRAGMENT_NAME).write_bytes(fragment)
    (io_root / IO_SOURCE_NAME).write_bytes(io_source)


def _run_cli(source_root: Path, component_root: Path,
             io_root: Path, output_root: Path):
    return subprocess.run(
        [
            sys.executable,
            "-I",
            "-B",
            str(GENERATOR_PATH),
            "--source-root",
            str(source_root),
            "--component-root",
            str(component_root),
            "--io-source-root",
            str(io_root),
            "--output-root",
            str(output_root),
        ],
        cwd=str(output_root.parent),
        capture_output=True,
        check=False,
        timeout=30,
    )


def _is_reparse(entry) -> bool:
    attributes = getattr(entry, "st_file_attributes", 0) or 0
    return stat.S_ISLNK(entry.st_mode) or bool(attributes & 0x400)


def _assert_contained(root: Path, path: Path, *, resolve: bool):
    owned = Path(root).resolve(strict=True)
    candidate = Path(path).resolve(strict=True) if resolve else Path(os.path.abspath(path))
    try:
        common = os.path.commonpath([str(owned), str(candidate)])
    except ValueError as exc:
        raise AssertionError("input link escaped its owned temporary directory") from exc
    if os.path.normcase(common) != os.path.normcase(str(owned)):
        raise AssertionError("input link escaped its owned temporary directory")


def _create_directory_junction(junction: Path, target: Path):
    return subprocess.run(
        ["cmd.exe", "/d", "/c", "mklink", "/J", str(junction), str(target)],
        cwd=str(junction.parent),
        capture_output=True,
        text=True,
        check=False,
        timeout=15,
    )


class CurrentArmReadMeteringTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if (ROOT_PIN_PENDING or FRAGMENT_SHA256 == "ROOT_PIN_PENDING"
                or OUTPUT_SHA256 == "ROOT_PIN_PENDING"
                or ROOT_STATIC_EMISSION_SHA256 == "ROOT_PIN_PENDING"):
            raise RuntimeError("root-pinned fragment/output/static-emission values are pending")
        if sys.flags.optimize:
            raise RuntimeError("source canary must run with optimization disabled")
        study_path = globals().get("STUDY_SOURCE_PATH")
        io_source_root = globals().get("IO_SOURCE_ROOT")
        if study_path is None or io_source_root is None:
            raise RuntimeError("pass --study-source and --io-source-root")

        cls.study_path = Path(study_path)
        cls.io_source_root = Path(io_source_root)
        cls.study_source = cls.study_path.read_bytes()
        cls.fragment = FRAGMENT_PATH.read_bytes()
        if len(cls.study_source) != SOURCE_BYTES or _sha256(cls.study_source) != SOURCE_SHA256:
            raise AssertionError("study source does not match the accepted baseline pin")
        if len(cls.fragment) != FRAGMENT_BYTES or _sha256(cls.fragment) != FRAGMENT_SHA256:
            raise AssertionError("arm-read fragment does not match the root-reviewed pin")

        cls.io_source_path = cls.io_source_root / IO_SOURCE_NAME
        cls.io_source = cls.io_source_path.read_bytes()
        if (len(cls.io_source) != IO_SOURCE_BYTES
                or _sha256(cls.io_source) != IO_SOURCE_SHA256):
            raise AssertionError("published IO source does not match the contract pin")

        # Authenticate the pure transform source before compiling it in a private module.
        cls.generator, cls.generator_source = _load_private_generator(GENERATOR_PATH)
        cls.result = cls.generator.derive_current_arm_reads(
            study_source=cls.study_source,
            metered_fragment_source=cls.fragment,
        )
        cls.spans = _read_old_spans(cls.study_source)
        cls.raw_sections = _parse_fragment_independently(cls.fragment)
        cls.methods = _section_methods(cls.raw_sections)
        cls.replacements, cls.splice_order, _unused = _replacement_records(
            cls.spans, cls.methods
        )
        cls.expected_source, cls.outside_segments, cls.outside_bytes = _expected_output(
            cls.study_source, cls.spans, cls.replacements
        )

    def _assert_api_error(self, code, **kwargs):
        with self.assertRaises(self.generator.GenerationError) as caught:
            self.generator.derive_current_arm_reads(**kwargs)
        self.assertEqual(caught.exception.code, code)
        self.assertEqual(str(caught.exception), code)

    def _assert_cli_error(self, completed, code, output_root):
        self.assertEqual(completed.returncode, 2)
        self.assertEqual(completed.stdout, b"")
        self.assertEqual(completed.stderr, (code + os.linesep).encode("ascii"))
        self.assertEqual(list(output_root.iterdir()), [])

    def test_result_namedtuple_and_pinned_output(self):
        self.assertEqual(ROOT_STATIC_EMISSION_SHA256, OUTPUT_SHA256)
        self.assertTrue(isinstance(self.result, tuple))
        self.assertEqual(
            getattr(type(self.result), "_fields", None),
            ("study_source", "provenance_json"),
        )
        self.assertEqual(type(self.result).__name__, "GenerationResult")
        self.assertIs(type(self.result.study_source), bytes)
        self.assertIs(type(self.result.provenance_json), bytes)
        self.assertEqual(len(self.result.study_source), OUTPUT_BYTES)
        self.assertEqual(_sha256(self.result.study_source), OUTPUT_SHA256)

    def test_pure_repeatability_immutability_and_no_filesystem_effects(self):
        original_study = self.study_source
        original_fragment = self.fragment
        with tempfile.TemporaryDirectory(prefix="tsj-current-arm-pure-") as scratch:
            previous = os.getcwd()
            try:
                os.chdir(scratch)
                first = self.generator.derive_current_arm_reads(
                    study_source=original_study,
                    metered_fragment_source=original_fragment,
                )
                second = self.generator.derive_current_arm_reads(
                    study_source=original_study,
                    metered_fragment_source=original_fragment,
                )
            finally:
                os.chdir(previous)
            self.assertEqual(list(Path(scratch).iterdir()), [])
        self.assertEqual(first, second)
        self.assertEqual(first, self.result)
        self.assertEqual(self.study_path.read_bytes(), original_study)
        self.assertEqual(FRAGMENT_PATH.read_bytes(), original_fragment)
        self.assertEqual(original_study, self.study_source)
        self.assertEqual(original_fragment, self.fragment)

    def test_independent_fragment_parse_and_exact_four_span_splice(self):
        self.assertEqual(self.splice_order, [
            "executeArmWithProfile",
            "containsMetered",
            "validateObservation",
            "r9CanaryArmDto",
        ])
        for name, expected_size in RAW_SECTION_BYTES.items():
            self.assertEqual(len(self.raw_sections[name]), expected_size)
        self.assertTrue(self.fragment.endswith(SECTION_END))
        self.assertFalse(self.fragment.endswith(b"\n"))
        self.assertNotIn(SECTION_PREFIX, self.result.study_source)
        self.assertNotIn(SECTION_END, self.result.study_source)
        self.assertEqual(len(self.expected_source), OUTPUT_BYTES)
        self.assertEqual(_sha256(self.expected_source), OUTPUT_SHA256)
        self.assertEqual(self.result.study_source, self.expected_source)

        # Explicit segment equality proves all other Source bytes were conserved.
        source_cursor = 0
        output_cursor = 0
        for name in self.splice_order:
            span = self.spans[name]
            untouched = self.study_source[source_cursor:span["start"]]
            self.assertEqual(
                self.result.study_source[output_cursor:output_cursor + len(untouched)],
                untouched,
            )
            output_cursor += len(untouched) + len(self.replacements[name])
            source_cursor = span["end"]
        self.assertEqual(self.result.study_source[output_cursor:],
                         self.study_source[source_cursor:])

    def test_unmodified_original_r7_helpers_and_main_assemble_holds(self):
        unchanged = (
            (
                b"static Map<String,Object> originalDto(",
                b'    @SuppressWarnings("unchecked")\n'
                b"    static Map<String,Object> originalDto(",
            ),
            (
                b"private static Object r7ReadObject(SetupMeter meter,Object source,String name) throws Exception",
                None,
            ),
            (
                b"private static Object originalDtoMapRead(SetupMeter m,java.util.Map<?,?> source,String key) throws Exception",
                None,
            ),
            (b"public static void main(String[] args) throws Exception", None),
            (b"static Comparison assemble(Arguments args,long started) throws Exception", None),
        )
        for signature, start_marker in unchanged:
            before = _extract_unchanged_method(self.study_source, signature, start_marker)
            after = _extract_unchanged_method(self.result.study_source, signature, start_marker)
            self.assertEqual(before, after)

    def test_all_four_business_methods_recover_exactly_after_inverse_metering(self):
        for name in ("executeArmWithProfile", "containsMetered",
                     "validateObservation", "r9CanaryArmDto"):
            old_method = self.spans[name]["method"]
            new_method = self.methods[name]
            if name == "executeArmWithProfile":
                r7_reads = list(_R7_READ_RE.finditer(new_method))
                self.assertEqual(len(r7_reads), 11)
                self.assertEqual(Counter((m.group(1), m.group(2)) for m in r7_reads),
                                 EXPECTED_EXECUTE_FIELD_READS)
                recovered = re.sub(
                    rb'\(List<String>\)r7ReadObject\(m,cohort,"(requested|netIds)"\)',
                    lambda m: b"cohort." + m.group(1),
                    new_method,
                )
                recovered = _R7_READ_RE.sub(
                    lambda m: b'field(' + m.group(1) + b',"' + m.group(2) + b'")',
                    recovered,
                )
                map_reads = list(_MAP_READ_RE.finditer(recovered))
                self.assertEqual(len(map_reads), 3)
                self.assertEqual(Counter((m.group(1), m.group(2)) for m in map_reads),
                                 EXPECTED_EXECUTE_MAP_READS)
                recovered = _MAP_READ_RE.sub(
                    lambda m: m.group(1) + b'.get("' + m.group(2) + b'")',
                    recovered,
                )
                metered_replacement = (
                    b'        if ("QUALITY_REJECTED".equals(outcome.get("disposition")) &&\n'
                    b'                "NO_FAILURE".equals(observation.get("state"))) {\n'
                    b'            m.copy(1);\n'
                    b'            m.charge(1);\n'
                    b'            observation.put("state","NON_BRANCH_REJECTION");\n'
                    b'        }'
                )
                legacy_replacement = (
                    b'        if ("QUALITY_REJECTED".equals(outcome.get("disposition")) &&\n'
                    b'                "NO_FAILURE".equals(observation.get("state")))\n'
                    b'            observation.put("state","NON_BRANCH_REJECTION");'
                )
                self.assertEqual(recovered.count(metered_replacement), 1)
                recovered = recovered.replace(metered_replacement, legacy_replacement, 1)
            elif name == "containsMetered":
                prepaid = (
                    b"        m.charge(1);\n"
                    b"        final int entries=values.size();\n"
                    b"        m.charge(2L*entries+2L);\n"
                )
                self.assertEqual(new_method.count(prepaid), 1)
                recovered = new_method.replace(prepaid, b"", 1)
            elif name == "validateObservation":
                map_reads = list(_MAP_READ_RE.finditer(new_method))
                list_reads = list(_LIST_READ_RE.finditer(new_method))
                self.assertEqual(len(map_reads), 18)
                self.assertEqual(Counter((m.group(1), m.group(2)) for m in map_reads),
                                 EXPECTED_VALIDATOR_MAP_READS)
                self.assertEqual(
                    [m.group(1) for m in list_reads], [b"first-1", b"last-1"]
                )
                recovered = _MAP_READ_RE.sub(
                    lambda m: m.group(1) + b'.get("' + m.group(2) + b'")',
                    new_method,
                )
                recovered = _LIST_READ_RE.sub(
                    lambda m: b"requested.get(" + m.group(1) + b")", recovered
                )
            else:
                fresh_pair = (
                    b'        Object fresh=r7ReadObject(m,arm,"fresh");\n'
                    b'        Object run=r7ReadObject(m,fresh,"json");'
                )
                prior_pair = b'        m.charge(1); Object run=field(arm.fresh,"json");'
                self.assertEqual(new_method.count(fresh_pair), 1)
                recovered = new_method.replace(fresh_pair, prior_pair, 1)
                recovered = recovered.replace(
                    b'r7ReadObject(m,arm,"observation")', b"arm.observation", 1
                )
                recovered = recovered.replace(
                    b'r7ReadObject(m,arm,"cleanup")', b"arm.cleanup", 1
                )
                self.assertEqual(recovered.count(b"r7ReadObject(m,"), 0)
            self.assertEqual(recovered, old_method, name)

    def test_read_census_guard_bodies_lazy_paths_and_list_prepayment(self):
        execute = self.methods["executeArmWithProfile"]
        validate = self.methods["validateObservation"]
        contains = self.methods["containsMetered"]
        helper = self.methods["armListRead"]
        r9 = self.methods["r9CanaryArmDto"]

        execute_fields = list(_R7_READ_RE.finditer(execute))
        self.assertEqual(Counter((m.group(1), m.group(2)) for m in execute_fields),
                         EXPECTED_EXECUTE_FIELD_READS)
        self.assertEqual(sum(EXPECTED_EXECUTE_FIELD_READS.values()), 11)
        self.assertEqual(2 + 1 + 1 + 1 + 1 + 1, 7)  # NULL path field/cohort reads
        self.assertEqual(7 + 2, 9)  # OBSERVE adds sink and validation borrows
        self.assertEqual(Counter((m.group(1), m.group(2))
                                 for m in _MAP_READ_RE.finditer(execute)),
                         EXPECTED_EXECUTE_MAP_READS)
        self.assertEqual(execute.count(b'm.charge(1);'), 1)
        self.assertEqual(execute.count(b'm.copy(1);'), 1)
        self.assertIn(
            b'if (fresh==null || !Boolean.TRUE.equals(r7ReadObject(m,fresh,"referencesDropped")))',
            execute,
        )
        self.assertEqual(execute.count(b'r7ReadObject(m,fresh,"referencesDropped")'), 2)
        self.assertEqual(execute.count(b'r7ReadObject(m,cohort,"netIds")'), 2)
        self.assertEqual(execute.count(b'r7ReadObject(m,cohort,"requested")'), 3)
        self.assertIn(
            b'"QUALITY_REJECTED".equals(originalDtoMapRead(m,outcome,"disposition")) &&\n'
            b'                "NO_FAILURE".equals(originalDtoMapRead(m,observation,"state"))',
            execute,
        )
        self.assertEqual(execute.count(b"m.finish();"), 5)
        self.assertIn(b"m.copy(10);", execute)
        self.assertIn(b"return new ArmResult(fresh,observation,cleanup);", execute)

        self.assertEqual(Counter((m.group(1), m.group(2))
                                 for m in _MAP_READ_RE.finditer(validate)),
                         EXPECTED_VALIDATOR_MAP_READS)
        self.assertEqual(len(list(_MAP_READ_RE.finditer(validate))), 18)
        self.assertEqual(len(list(_LIST_READ_RE.finditer(validate))), 2)
        self.assertEqual(
            [len(_guard_blocks(self.spans["containsMetered"]["method"])),
             len(_guard_blocks(self.spans["validateObservation"]["method"]))],
            [len(_guard_blocks(contains)), len(_guard_blocks(validate))],
        )
        self.assertEqual(
            len(_guard_blocks(self.spans["containsMetered"]["method"]))
            + len(_guard_blocks(self.spans["validateObservation"]["method"])),
            41,
        )
        self.assertEqual(
            [len(_guard_blocks(self.spans["containsMetered"]["method"])),
             len(_guard_blocks(self.spans["validateObservation"]["method"]))],
            [1, 40],
        )
        self.assertEqual(
            _guard_blocks(self.spans["containsMetered"]["method"]),
            _guard_blocks(contains),
        )
        self.assertEqual(
            _guard_blocks(self.spans["validateObservation"]["method"]),
            _guard_blocks(validate),
        )
        selected_head = (
            b'        m.charge(1); // selected-head reference inspection\n'
            b'        final Object selectedHead=obs.get("selectedHead");'
        )
        self.assertEqual(validate.count(selected_head), 1)
        self.assertLess(
            validate.index(b"last>requested.size();"),
            validate.index(b"armListRead(m,requested,first-1)"),
        )
        self.assertLess(
            validate.index(b"armListRead(m,requested,first-1)"),
            validate.index(b"armListRead(m,requested,last-1)"),
        )

        self.assertEqual(helper.count(b"m.charge(1);"), 1)
        self.assertEqual(helper.count(b"source.get(index)"), 1)
        self.assertLess(helper.index(b"m.charge(1);"), helper.index(b"source.get(index)"))
        self.assertNotIn(b"source==null", helper)
        self.assertNotIn(b"index<", helper)
        traversal_prepayment = (
            b"m.charge(1);\n"
            b"        final int entries=values.size();\n"
            b"        m.charge(2L*entries+2L);"
        )
        self.assertEqual(contains.count(traversal_prepayment), 1)
        self.assertLess(contains.index(traversal_prepayment), contains.index(b"for (final Object item:values)"))
        self.assertEqual(contains.count(b"m.copy("), 0)
        self.assertEqual(contains.count(b"m.finish("), 0)

        self.assertEqual(Counter((m.group(1), m.group(2))
                                 for m in _R7_READ_RE.finditer(r9)),
                         EXPECTED_R9_FIELD_READS)
        self.assertEqual(r9.count(b"m.charge(1);"), 1)
        self.assertIn(b'm.charge(1); Object run=', self.spans["r9CanaryArmDto"]["method"])
        self.assertIn(b"m.copy(5);", r9)
        self.assertIn(b"m.finish();", r9)

    def test_canonical_independent_provenance_record(self):
        expected = _expected_provenance(
            self.study_source,
            self.fragment,
            self.spans,
            self.methods,
            self.replacements,
            self.outside_segments,
            self.outside_bytes,
        )
        canonical = (
            json.dumps(expected, ensure_ascii=True, sort_keys=True, separators=(",", ":"))
            + "\n"
        ).encode("ascii")
        self.assertEqual(self.result.provenance_json, canonical)
        self.assertTrue(self.result.provenance_json.endswith(b"\n"))
        self.assertNotIn(b"\r", self.result.provenance_json)
        self.assertEqual(json.loads(self.result.provenance_json), expected)

    def test_generator_is_private_same_directory_source_with_pure_api(self):
        self.assertEqual(Path(self.generator.__file__).resolve(), GENERATOR_PATH.resolve())
        tree = ast.parse(self.generator_source.decode("utf-8", "strict"))
        imported_roots = set()
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                imported_roots.update(alias.name.split(".", 1)[0] for alias in node.names)
            elif isinstance(node, ast.ImportFrom):
                if node.level:
                    imported_roots.add("relative-import")
                elif node.module:
                    imported_roots.add(node.module.split(".", 1)[0])
        self.assertTrue(imported_roots <= {
            "dataclasses", "hashlib", "json", "os", "pathlib", "stat", "sys", "types", "typing"
        })
        derive = next(
            node for node in tree.body
            if isinstance(node, ast.FunctionDef) and node.name == "derive_current_arm_reads"
        )
        forbidden_calls = []
        for node in ast.walk(derive):
            if isinstance(node, ast.Call):
                if isinstance(node.func, ast.Name) and node.func.id in {
                    "open", "exec", "eval", "__import__"
                }:
                    forbidden_calls.append(node.func.id)
                if isinstance(node.func, ast.Attribute) and node.func.attr in {
                    "read_bytes", "read_text", "write_bytes", "write_text", "unlink"
                }:
                    forbidden_calls.append(node.func.attr)
        self.assertEqual(forbidden_calls, [])

    def test_api_rejects_types_sizes_and_mutated_pins_before_decode(self):
        self._assert_api_error(
            "SOURCE_TYPE",
            study_source=bytearray(self.study_source),
            metered_fragment_source=self.fragment,
        )
        self._assert_api_error(
            "FRAGMENT_TYPE",
            study_source=self.study_source,
            metered_fragment_source=memoryview(self.fragment),
        )
        self._assert_api_error(
            "SOURCE_SIZE",
            study_source=b"x" * (4 * 1024 * 1024 + 1),
            metered_fragment_source=self.fragment,
        )
        self._assert_api_error(
            "FRAGMENT_SIZE",
            study_source=self.study_source,
            metered_fragment_source=b"x" * (64 * 1024 + 1),
        )
        invalid_study = b"\xff" + self.study_source[1:]
        self._assert_api_error(
            "SOURCE_SHA256",
            study_source=invalid_study,
            metered_fragment_source=self.fragment,
        )
        invalid_fragment = b"\xff" + self.fragment[1:]
        self._assert_api_error(
            "FRAGMENT_SHA256",
            study_source=self.study_source,
            metered_fragment_source=invalid_fragment,
        )

    def test_cli_positive_emits_only_after_closed_output_readback(self):
        with tempfile.TemporaryDirectory(prefix="tsj-current-arm-cli-") as temporary:
            root = Path(temporary)
            source_root, component_root, io_root, output_root = _make_cli_roots(root)
            _write_cli_inputs(
                source_root, component_root, io_root,
                self.study_source, self.fragment, self.io_source,
            )
            self.assertEqual((source_root / SOURCE_NAME).resolve().parent,
                             source_root.resolve())
            self.assertEqual((component_root / FRAGMENT_NAME).resolve().parent,
                             component_root.resolve())
            self.assertEqual((io_root / IO_SOURCE_NAME).resolve().parent, io_root.resolve())
            stable_inputs = tuple(
                (path, path.read_bytes())
                for path in (
                    source_root / SOURCE_NAME,
                    component_root / FRAGMENT_NAME,
                    io_root / IO_SOURCE_NAME,
                )
            )
            completed = _run_cli(source_root, component_root, io_root, output_root)
            self.assertEqual(completed.returncode, 0)
            self.assertEqual(completed.stderr, b"")
            self.assertEqual(completed.stdout, self.result.provenance_json)
            output = output_root / OUTPUT_NAME
            self.assertTrue(output.is_file())
            self.assertEqual(output.read_bytes(), self.result.study_source)
            self.assertEqual(_sha256(output.read_bytes()), OUTPUT_SHA256)
            self.assertEqual([item.name for item in output_root.iterdir()], [OUTPUT_NAME])
            for path, before in stable_inputs:
                self.assertEqual(path.read_bytes(), before)

    def test_cli_rejects_corrupt_fixed_source_fragment_and_io_files(self):
        cases = (
            ("source", "SOURCE_SHA256"),
            ("fragment", "FRAGMENT_SHA256"),
            ("io", "IO_SOURCE_SHA256"),
        )
        for role, expected_code in cases:
            with self.subTest(role=role), tempfile.TemporaryDirectory(
                prefix="tsj-current-arm-corrupt-"
            ) as temporary:
                root = Path(temporary)
                source_root, component_root, io_root, output_root = _make_cli_roots(root)
                study = self.study_source
                fragment = self.fragment
                io_source = self.io_source
                if role == "source":
                    study = b"\xff" + study[1:]
                elif role == "fragment":
                    fragment = b"\xff" + fragment[1:]
                else:
                    io_source = b"\xff" + io_source[1:]
                _write_cli_inputs(source_root, component_root, io_root,
                                  study, fragment, io_source)
                completed = _run_cli(source_root, component_root, io_root, output_root)
                self._assert_cli_error(completed, expected_code, output_root)

    def test_cli_rejects_missing_fixed_source_fragment_and_io_leafs(self):
        for role, expected_code in (
            ("source", "SOURCE_MISSING"),
            ("fragment", "FRAGMENT_MISSING"),
            ("io", "IO_SOURCE_MISSING"),
        ):
            with self.subTest(role=role), tempfile.TemporaryDirectory(
                prefix="tsj-current-arm-missing-"
            ) as temporary:
                root = Path(temporary)
                source_root, component_root, io_root, output_root = _make_cli_roots(root)
                if role != "source":
                    (source_root / SOURCE_NAME).write_bytes(self.study_source)
                if role != "fragment":
                    (component_root / FRAGMENT_NAME).write_bytes(self.fragment)
                if role != "io":
                    (io_root / IO_SOURCE_NAME).write_bytes(self.io_source)
                completed = _run_cli(source_root, component_root, io_root, output_root)
                self._assert_cli_error(completed, expected_code, output_root)

    def test_cli_rejects_oversized_source_fragment_and_io_inputs(self):
        cases = (
            ("source", "SOURCE_SIZE"),
            ("fragment", "FRAGMENT_SIZE"),
            ("io", "IO_SOURCE_SIZE"),
        )
        for role, expected_code in cases:
            with self.subTest(role=role), tempfile.TemporaryDirectory(
                prefix="tsj-current-arm-size-"
            ) as temporary:
                root = Path(temporary)
                source_root, component_root, io_root, output_root = _make_cli_roots(root)
                study = self.study_source
                fragment = self.fragment
                io_source = self.io_source
                if role == "source":
                    study = b"x" * (4 * 1024 * 1024 + 1)
                elif role == "fragment":
                    fragment = b"x" * (64 * 1024 + 1)
                else:
                    io_source = b"x" * (4 * 1024 * 1024 + 1)
                _write_cli_inputs(source_root, component_root, io_root,
                                  study, fragment, io_source)
                completed = _run_cli(source_root, component_root, io_root, output_root)
                self._assert_cli_error(completed, expected_code, output_root)

    def test_cli_rejects_wrong_input_leaf_names(self):
        for role, expected_code in (
            ("source", "SOURCE_MISSING"),
            ("fragment", "FRAGMENT_MISSING"),
            ("io", "IO_SOURCE_MISSING"),
        ):
            with self.subTest(role=role), tempfile.TemporaryDirectory(
                prefix="tsj-current-arm-leaf-"
            ) as temporary:
                root = Path(temporary)
                source_root, component_root, io_root, output_root = _make_cli_roots(root)
                if role == "source":
                    (source_root / "wrong-study-name.java").write_bytes(self.study_source)
                else:
                    (source_root / SOURCE_NAME).write_bytes(self.study_source)
                if role == "fragment":
                    (component_root / "wrong-fragment-name.fragment").write_bytes(self.fragment)
                else:
                    (component_root / FRAGMENT_NAME).write_bytes(self.fragment)
                if role == "io":
                    (io_root / "wrong-io-source.py").write_bytes(self.io_source)
                else:
                    (io_root / IO_SOURCE_NAME).write_bytes(self.io_source)
                completed = _run_cli(source_root, component_root, io_root, output_root)
                self._assert_cli_error(completed, expected_code, output_root)

    def test_cli_preserves_existing_output(self):
        with tempfile.TemporaryDirectory(prefix="tsj-current-arm-output-exists-") as temporary:
            root = Path(temporary)
            source_root, component_root, io_root, output_root = _make_cli_roots(root)
            _write_cli_inputs(source_root, component_root, io_root,
                              self.study_source, self.fragment, self.io_source)
            output = output_root / OUTPUT_NAME
            sentinel = b"owned preexisting output sentinel\n"
            output.write_bytes(sentinel)
            completed = _run_cli(source_root, component_root, io_root, output_root)
            self.assertEqual(completed.returncode, 2)
            self.assertEqual(completed.stdout, b"")
            self.assertEqual(completed.stderr, ("OUTPUT_EXISTS" + os.linesep).encode("ascii"))
            self.assertEqual(output.read_bytes(), sentinel)
            self.assertEqual([item.name for item in output_root.iterdir()], [OUTPUT_NAME])

    def test_cli_rejects_unknown_and_duplicate_root_arguments(self):
        with tempfile.TemporaryDirectory(prefix="tsj-current-arm-args-") as temporary:
            root = Path(temporary)
            args = [
                "--source-root", str(root),
                "--source-root", str(root),
                "--component-root", str(root),
                "--io-source-root", str(root),
                "--output-root", str(root),
            ]
            completed = subprocess.run(
                [sys.executable, "-I", "-B", str(GENERATOR_PATH), *args],
                cwd=str(root), capture_output=True, check=False, timeout=30,
            )
            self.assertEqual(completed.returncode, 2)
            self.assertEqual(completed.stdout, b"")
            self.assertEqual(completed.stderr, ("CLI_ARGUMENTS" + os.linesep).encode("ascii"))
            self.assertEqual(list(root.iterdir()), [])

    def _run_link_rejection(self, role: str, link_kind: str):
        expected_code = role.upper() + "_LINKED" if link_kind == "hardlink" else role.upper() + "_REPARSE"
        with tempfile.TemporaryDirectory(
            prefix="tsj-current-arm-" + role.lower() + "-" + link_kind + "-"
        ) as temporary:
            root = Path(temporary)
            source_root, component_root, io_root, output_root = _make_cli_roots(root)
            source_fixture = self.study_source
            fragment_fixture = self.fragment
            io_fixture = self.io_source
            fixture_by_role = {
                "SOURCE": (source_root, SOURCE_NAME, source_fixture),
                "FRAGMENT": (component_root, FRAGMENT_NAME, fragment_fixture),
                "IO_SOURCE": (io_root, IO_SOURCE_NAME, io_fixture),
            }
            if role == "SOURCE":
                (component_root / FRAGMENT_NAME).write_bytes(fragment_fixture)
                (io_root / IO_SOURCE_NAME).write_bytes(io_fixture)
            elif role == "FRAGMENT":
                (source_root / SOURCE_NAME).write_bytes(source_fixture)
                (io_root / IO_SOURCE_NAME).write_bytes(io_fixture)
            else:
                (source_root / SOURCE_NAME).write_bytes(source_fixture)
                (component_root / FRAGMENT_NAME).write_bytes(fragment_fixture)

            input_root, leaf_name, payload = fixture_by_role[role]
            link_path = input_root / leaf_name
            target_file = root / (role.lower() + "-link-target.bin")
            target_file.write_bytes(payload)
            _assert_contained(root, input_root, resolve=True)
            _assert_contained(root, target_file, resolve=True)
            _assert_contained(root, link_path, resolve=False)

            if link_kind == "hardlink":
                try:
                    os.link(target_file, link_path)
                except (OSError, NotImplementedError) as exc:
                    self.fail(
                        "required hard-link canary unavailable for " + role + ": "
                        + type(exc).__name__ + ": " + str(exc)
                    )
                completed = _run_cli(source_root, component_root, io_root, output_root)
                self._assert_cli_error(completed, expected_code, output_root)
                return

            target_dir = None
            file_symlink = False
            junction_link = False
            try:
                try:
                    os.symlink(target_file, link_path)
                    file_symlink = True
                    _assert_contained(root, link_path, resolve=True)
                except (OSError, NotImplementedError) as exc:
                    if os.name != "nt":
                        self.fail(
                            "required symbolic-link canary unavailable for " + role + ": "
                            + type(exc).__name__ + ": " + str(exc)
                        )
                    if os.path.lexists(link_path):
                        partial = os.lstat(link_path)
                        self.assertTrue(_is_reparse(partial))
                        _assert_contained(root, link_path, resolve=False)
                        os.unlink(link_path)

                    target_dir = root / (role.lower() + "-junction-target")
                    target_dir.mkdir()
                    (target_dir / leaf_name).write_bytes(payload)
                    _assert_contained(root, target_dir, resolve=True)
                    input_root.rmdir()
                    try:
                        junction_result = _create_directory_junction(input_root, target_dir)
                    except (OSError, subprocess.SubprocessError) as exc2:
                        if os.path.lexists(input_root):
                            possible = os.lstat(input_root)
                            if _is_reparse(possible):
                                junction_link = True
                        self.fail(
                            "required directory-junction canary unavailable for " + role + ": "
                            + type(exc2).__name__ + ": " + str(exc2)
                        )
                    if os.path.lexists(input_root):
                        possible = os.lstat(input_root)
                        junction_link = _is_reparse(possible)
                    self.assertEqual(
                        junction_result.returncode,
                        0,
                        "required directory-junction command failed: "
                        + junction_result.stdout + junction_result.stderr,
                    )
                    self.assertTrue(junction_link)
                    _assert_contained(root, input_root, resolve=False)
                    _assert_contained(root, input_root, resolve=True)

                completed = _run_cli(source_root, component_root, io_root, output_root)
                self._assert_cli_error(completed, expected_code, output_root)
            finally:
                # Inspect the exact owned path even if creation raised after making a link.
                if os.path.lexists(link_path):
                    entry = os.lstat(link_path)
                    if _is_reparse(entry):
                        _assert_contained(root, link_path, resolve=False)
                        _assert_contained(root, link_path, resolve=True)
                        if file_symlink:
                            os.unlink(link_path)
                        else:
                            os.rmdir(link_path)
                        self.assertTrue(target_file.is_file())
                if os.path.lexists(input_root) and junction_link:
                    entry = os.lstat(input_root)
                    self.assertTrue(_is_reparse(entry))
                    _assert_contained(root, input_root, resolve=False)
                    _assert_contained(root, input_root, resolve=True)
                    os.rmdir(input_root)
                    self.assertTrue((target_dir / leaf_name).is_file())

    def test_cli_rejects_hardlinked_source_file(self):
        self._run_link_rejection("SOURCE", "hardlink")

    def test_cli_rejects_hardlinked_fragment_file(self):
        self._run_link_rejection("FRAGMENT", "hardlink")

    def test_cli_rejects_hardlinked_io_source_file(self):
        self._run_link_rejection("IO_SOURCE", "hardlink")

    def test_cli_rejects_symlinked_source_or_windows_junction(self):
        self._run_link_rejection("SOURCE", "symlink")

    def test_cli_rejects_symlinked_fragment_or_windows_junction(self):
        self._run_link_rejection("FRAGMENT", "symlink")

    def test_cli_rejects_symlinked_io_source_or_windows_junction(self):
        self._run_link_rejection("IO_SOURCE", "symlink")


def _parse_test_arguments(argv):
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--study-source", required=True)
    parser.add_argument("--io-source-root", required=True)
    return parser.parse_known_args(argv)


if __name__ == "__main__":
    _arguments, _unittest_arguments = _parse_test_arguments(sys.argv[1:])
    STUDY_SOURCE_PATH = Path(_arguments.study_source)
    IO_SOURCE_ROOT = Path(_arguments.io_source_root)
    unittest.main(argv=[sys.argv[0], *_unittest_arguments], verbosity=2)
