"""Independent source-only oracle for the bounded export composition record.

This module performs no filesystem, process, environment, or producer access. Its
fixture values and equations are literal and independent of the production
serializer and native canary.
"""

import hashlib
import json
import re


MAX_JAVA_LONG = 9223372036854775807
MAX_PATH_ENTRIES = 4096
MIN_PATH_ENTRIES = 2
MAX_RESULT_BYTES = 32768
MAX_MARKER_BYTES = 656
MAX_ELAPSED_NANOS = 90000000000
MAX_MEASURE_NANOS = 5000000000
MAX_NEGATIVE_AFTER_CUTOFF_NANOS = 5000000000
RECEIPT_BYTES = 4096
RECEIPT_HASH_BLOCKS = 1
SETUP_UNITS = 10007
SETUP_COPIES = 478
SETUP_CHUNKS = 3
COPY_CHUNK_LIMIT = 4096
COPIED_ENTRY_LIMIT = 1048576

SMOKE_NAME = "current_export_composition"
SUITE_NAME = "receipt_changed_before_export_close"
MODE_NAMES = {"smoke": SMOKE_NAME, "suite": SUITE_NAME}

TOP_KEYS = ("mode", "status", "names", "cases")
STRING_FIELDS = (
    "name", "status", "phase", "reason", "cleanupDisposition", "error",
    "cleanupError", "expectedFailure", "resultHex", "markerHex",
)
BOOLEAN_FIELDS = (
    "meterOpen", "stdoutRestored", "metricsReturned", "meterDiscarded",
    "receiptMutationApplied",
)
INTEGER_FIELDS = (
    "operationNanos", "cleanupNanos", "pathEntries", "receiptBytes",
    "receiptHashBlocks", "units", "copiedEntries", "copyChunks",
    "wrapperGroups", "priorGroups", "groupUnits", "maxGroupUnits",
    "maxGroupNanos", "closedGroupMeasures", "workFinalizedSlots",
    "exportCutoffNanos", "beforeExportElapsedNanos",
    "afterExportElapsedNanos", "setupMeasureUnits", "setupMeasureNanos",
    "metadataMeasureUnits", "metadataMeasureNanos", "exportMeasureUnits",
    "exportMeasureNanos", "resultBytesObserved", "metricsResultBytes",
    "metricsMarkerBytes", "metricsExportUnits", "metricsFinalExportNanos",
    "metricsElapsedNanos", "metricsTotalUnits", "metricsTotalGroups",
    "metricsMaxGroupNanos", "metricsMaximumUnitsPerGroup",
    "metricsCopiedEntriesDelta", "metricsCopyChunksDelta",
)
CASE_FIELDS = frozenset(STRING_FIELDS + BOOLEAN_FIELDS + INTEGER_FIELDS)

WORK_KEYS = (
    "elapsedCutoffNanos", "cleanupNanos", "groupsBeforeExport",
    "wrapperGroupsBeforeExport", "maxGroupNanosBeforeExport",
    "maximumUnitsPerGroupBeforeExport", "unitsBeforeExport", "setupUnits",
    "setupGroups", "setupCopiedEntries", "setupCopyChunks",
    "originalCaptureOwnerUnits", "originalCaptureWrapperUnits",
    "originalCaptureCallbackUnits", "originalCaptureDiagnosticUnits",
    "originalCaptureGroups", "originalCaptureCopiedEntries",
    "originalCaptureCopyChunks", "nullOwnerUnits", "nullWrapperUnits",
    "nullObserverUnits", "observeOwnerUnits", "observeWrapperUnits",
    "observeObserverUnits", "observerUnits", "meterUnits", "metadataUnits",
    "metadataGroups", "metadataCopiedEntries", "metadataCopyChunks",
    "copiedEntries", "copyChunks", "copyChunkLimit", "copiedEntryLimit",
)

_HEX64 = re.compile(r"[0-9a-f]{64}\Z")
_LOWER_HEX = re.compile(r"[0-9a-f]*\Z")


class OracleFailure(ValueError):
    """A bounded validation failure with a stable command-line code."""

    def __init__(self, code, message):
        super().__init__(message)
        self.code = code


def _fail(code, message):
    raise OracleFailure(code, message)


def _require_exact_int(value, label, code="ORACLE_SCHEMA"):
    if type(value) is not int or value < 0 or value > MAX_JAVA_LONG:
        _fail(code, label + " must be an exact nonnegative Java long")
    return value


def _require_path_entries(value):
    if type(value) is not int or not MIN_PATH_ENTRIES <= value <= MAX_PATH_ENTRIES:
        _fail("ORACLE_PATH", "expected path entries outside the admitted range")
    return value


def _require_printable_ascii(value, label):
    if type(value) is not str or any(ord(char) < 32 or ord(char) > 126 for char in value):
        _fail("ORACLE_SCHEMA", label + " must contain printable ASCII only")


def _validate_json_value(value):
    kind = type(value)
    if kind is dict:
        for key, child in value.items():
            _require_printable_ascii(key, "JSON object key")
            _validate_json_value(child)
    elif kind is list or kind is tuple:
        for child in value:
            _validate_json_value(child)
    elif kind is str:
        _require_printable_ascii(value, "JSON string")
    elif kind is bool:
        return
    elif kind is int:
        if value < -MAX_JAVA_LONG - 1 or value > MAX_JAVA_LONG:
            _fail("ORACLE_SCHEMA", "JSON integer outside signed Java long range")
    else:
        _fail("ORACLE_SCHEMA", "unsupported JSON value type")


def encode_compact_ascii_json(value):
    """Encode printable-ASCII JSON with compact separators and no trailing LF."""
    _validate_json_value(value)
    try:
        return json.dumps(
            value, ensure_ascii=True, separators=(",", ":"), allow_nan=False
        ).encode("ascii")
    except (TypeError, ValueError, UnicodeError):
        _fail("ORACLE_SCHEMA", "JSON value cannot be encoded canonically")


def canonical_json_line(value):
    """Encode printable-ASCII compact JSON followed by exactly one LF."""
    return encode_compact_ascii_json(value) + b"\n"


def _literal_prior():
    """Return the Source-derived prior in its independent insertion order."""
    trial = {
        "status": "RUN",
        "reason": "TRIAL_EXECUTED",
        "capturedFailedNetId": "A_COIL_LOW",
        "controlFailedNetId": "B_COIL_LOW",
        "cohortNetIds": ["A_COIL_LOW", "B_COIL_LOW"],
        "requestedNetIds": [
            "NET_000", "NET_001", "NET_002", "NET_003", "NET_004",
            "NET_005", "NET_006", "NET_007", "NET_008", "NET_009",
            "D_SENSE", "NET_011", "NET_012", "NET_013", "NET_014",
            "NET_015", "NET_016", "NET_017", "NET_018", "NET_019",
            "NET_020", "NET_021", "NET_022", "NET_023", "NET_024",
            "NET_025", "NET_026", "NET_027", "NET_028", "NET_029",
            "NET_030", "NET_031", "NET_032", "NET_033", "NET_034",
            "NET_035", "NET_036", "NET_037", "NET_038", "NET_039",
            "NET_040", "NET_041", "NET_042", "A_COIL_LOW", "NET_044",
            "NET_045", "NET_046", "NET_047", "NET_048", "NET_049",
            "B_COIL_LOW", "NET_051", "NET_052", "NET_053", "NET_054",
            "NET_055", "NET_056", "NET_057", "NET_058", "NET_059",
        ],
        "orderChanged": False,
        "feedbackValidated": True,
    }
    return {
        "schema": "tsj-cohort-terminal-qualified-prior-v2",
        "inputContextSha256": "ca2d5b88b686845889e97de4eea96c4d86f80c1c5ec720aa0d912ba5b675666f",
        "sourceParent": "dddddddddddddddddddddddddddddddddddddddd",
        "manifestSha256": "1111111111111111111111111111111111111111111111111111111111111111",
        "sourceManifestSha256": "2222222222222222222222222222222222222222222222222222222222222222",
        "readerSha256": "3333333333333333333333333333333333333333333333333333333333333333",
        "resultSha256": "4444444444444444444444444444444444444444444444444444444444444444",
        "operation1Sha256": "5555555555555555555555555555555555555555555555555555555555555555",
        "parentReceiptSha256": "6666666666666666666666666666666666666666666666666666666666666666",
        "priorRunId": "12345678-1234-4234-8234-123456789abc",
        "priorOriginalReceipt": {
            "receiptPath": "op-1.jsonl",
            "receiptBytes": 4096,
            "receiptSha256": "5555555555555555555555555555555555555555555555555555555555555555",
            "receiptOrdinal": 307,
            "lineageOrdinal": 77,
        },
        "trialDisposition": trial,
    }


def _ledger_values(path_entries, cutoff_nanos, setup_nanos, metadata_nanos):
    p = _require_path_entries(path_entries)
    cutoff = _require_exact_int(cutoff_nanos, "cutoffNanos", "ORACLE_CLOCK")
    setup_time = _require_exact_int(setup_nanos, "setupNanos", "ORACLE_CLOCK")
    metadata_time = _require_exact_int(metadata_nanos, "metadataNanos", "ORACLE_CLOCK")
    if cutoff > MAX_ELAPSED_NANOS:
        _fail("ORACLE_CLOCK", "cutoff outside the ninety-second bound")
    if cutoff == 0:
        _fail("ORACLE_CLOCK", "R7 finalization requires a positive elapsed cutoff")
    if setup_time > MAX_MEASURE_NANOS or metadata_time > MAX_MEASURE_NANOS:
        _fail("ORACLE_CLOCK", "setup or metadata measure exceeds five seconds")

    units_before = 28712 + 10 * p + 6
    metadata_units = units_before - SETUP_UNITS
    if metadata_units > 32768:
        _fail("ORACLE_PATH", "P exceeds the admitted two-group 32768-unit profile")
    metadata_copies = 2 * p + 140
    metadata_chunks = 20
    copies_before = 618 + 2 * p
    chunks_before = 23
    max_units_before = max(SETUP_UNITS, metadata_units)
    max_nanos_before = max(setup_time, metadata_time)

    work = {
        "elapsedCutoffNanos": cutoff,
        "cleanupNanos": 0,
        "groupsBeforeExport": 3,
        "wrapperGroupsBeforeExport": 2,
        "maxGroupNanosBeforeExport": max_nanos_before,
        "maximumUnitsPerGroupBeforeExport": max_units_before,
        "unitsBeforeExport": units_before,
        "setupUnits": SETUP_UNITS,
        "setupGroups": 1,
        "setupCopiedEntries": SETUP_COPIES,
        "setupCopyChunks": SETUP_CHUNKS,
        "originalCaptureOwnerUnits": 0,
        "originalCaptureWrapperUnits": 0,
        "originalCaptureCallbackUnits": 0,
        "originalCaptureDiagnosticUnits": 0,
        "originalCaptureGroups": 1,
        "originalCaptureCopiedEntries": 0,
        "originalCaptureCopyChunks": 0,
        "nullOwnerUnits": 0,
        "nullWrapperUnits": 0,
        "nullObserverUnits": 0,
        "observeOwnerUnits": 0,
        "observeWrapperUnits": 0,
        "observeObserverUnits": 0,
        "observerUnits": 0,
        "meterUnits": units_before,
        "metadataUnits": metadata_units,
        "metadataGroups": 1,
        "metadataCopiedEntries": metadata_copies,
        "metadataCopyChunks": metadata_chunks,
        "copiedEntries": copies_before,
        "copyChunks": chunks_before,
        "copyChunkLimit": COPY_CHUNK_LIMIT,
        "copiedEntryLimit": COPIED_ENTRY_LIMIT,
    }
    if tuple(work) != WORK_KEYS:
        _fail("ORACLE_SCHEMA", "independent work ledger order is inconsistent")
    return {
        "pathEntries": p,
        "unitsBefore": units_before,
        "metadataUnits": metadata_units,
        "copiesBefore": copies_before,
        "chunksBefore": chunks_before,
        "work": work,
        "maxUnitsBefore": max_units_before,
        "maxNanosBefore": max_nanos_before,
    }


def expected_body_bytes(path_entries, cutoff_nanos, setup_nanos, metadata_nanos):
    """Build the independent expected 13-key pre-export body for pure tests."""
    ledger = _ledger_values(path_entries, cutoff_nanos, setup_nanos, metadata_nanos)
    body = {
        "schema": "tsj-cohort-terminal-result-v2",
        "mode": "OBSERVED_COHORT_TERMINAL_TELEMETRY",
        "status": "COMPLETED_DIAGNOSTIC_ONLY",
        "runId": "00000000-0000-0000-0000-000000000001",
        "sourceManifestSha256": "1111111111111111111111111111111111111111111111111111111111111111",
        "wireContractSha256": "1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed",
        "inputContextSha256": "ca2d5b88b686845889e97de4eea96c4d86f80c1c5ec720aa0d912ba5b675666f",
        "qualifiedPrior": _literal_prior(),
        "originalCapture": {},
        "cohort": {},
        "arms": {},
        "work": ledger["work"],
        "cleanup": {},
    }
    if tuple(body) != (
        "schema", "mode", "status", "runId", "sourceManifestSha256",
        "wireContractSha256", "inputContextSha256", "qualifiedPrior",
        "originalCapture", "cohort", "arms", "work", "cleanup",
    ):
        _fail("ORACLE_SCHEMA", "independent result body order is inconsistent")
    return canonical_json_line(body)


def expected_marker_bytes(result_sha256, result_bytes, elapsed_nanos,
                          final_export_nanos, total_units, max_group_nanos,
                          maximum_units_per_group):
    """Build the independent ordered fourteen-key terminal marker for tests."""
    if type(result_sha256) is not str or not _HEX64.fullmatch(result_sha256):
        _fail("ORACLE_MARKER_BYTES", "expected result digest is not lowercase SHA-256")
    marker = {
        "schema": "tsj-cohort-terminal-marker-v2",
        "status": "COMPLETED_DIAGNOSTIC",
        "runId": "00000000-0000-0000-0000-000000000001",
        "sourceManifestSha256": "1111111111111111111111111111111111111111111111111111111111111111",
        "wireContractSha256": "1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed",
        "inputContextSha256": "ca2d5b88b686845889e97de4eea96c4d86f80c1c5ec720aa0d912ba5b675666f",
        "resultSha256": result_sha256,
        "resultBytes": result_bytes,
        "elapsedNanos": elapsed_nanos,
        "finalExportNanos": final_export_nanos,
        "totalGroups": 4,
        "totalUnits": total_units,
        "maxGroupNanos": max_group_nanos,
        "maximumUnitsPerGroup": maximum_units_per_group,
    }
    for name in (
        "resultBytes", "elapsedNanos", "finalExportNanos", "totalUnits",
        "maxGroupNanos", "maximumUnitsPerGroup",
    ):
        _require_exact_int(marker[name], name, "ORACLE_MARKER_BYTES")
    if tuple(marker) != (
        "schema", "status", "runId", "sourceManifestSha256",
        "wireContractSha256", "inputContextSha256", "resultSha256",
        "resultBytes", "elapsedNanos", "finalExportNanos", "totalGroups",
        "totalUnits", "maxGroupNanos", "maximumUnitsPerGroup",
    ):
        _fail("ORACLE_SCHEMA", "independent marker order is inconsistent")
    return canonical_json_line(marker)


def _validate_top(value, mode):
    if type(mode) is not str or mode not in MODE_NAMES:
        _fail("ORACLE_SCHEMA", "mode must be smoke or suite")
    if type(value) is not dict or set(value) != set(TOP_KEYS):
        _fail("ORACLE_SCHEMA", "top-level keys do not match the export summary schema")
    if value["mode"] != mode.upper() or value["status"] != "PASS":
        _fail("ORACLE_SCHEMA", "top-level mode or status mismatch")
    expected_name = MODE_NAMES[mode]
    if type(value["names"]) is not list or value["names"] != [expected_name]:
        _fail("ORACLE_SCHEMA", "top-level names must contain the single expected case")
    if type(value["cases"]) is not list or len(value["cases"]) != 1:
        _fail("ORACLE_SCHEMA", "summary must contain exactly one case record")
    return expected_name, value["cases"][0]


def _validate_record_shape(record):
    if type(record) is not dict or set(record) != CASE_FIELDS:
        _fail("ORACLE_SCHEMA", "case record keys do not match the 51-field schema")
    for field in STRING_FIELDS:
        _require_printable_ascii(record[field], field)
    for field in BOOLEAN_FIELDS:
        if type(record[field]) is not bool:
            _fail("ORACLE_SCHEMA", field + " must be a JSON boolean")
    for field in INTEGER_FIELDS:
        _require_exact_int(record[field], field)


def _expect_equal(record, expected, code="ORACLE_COUNTERS"):
    for field, value in expected.items():
        if record[field] != value or type(record[field]) is not type(value):
            _fail(code, field + " does not match the independent expectation")


def _decode_hex_field(record, field, limit_bytes, code):
    text = record[field]
    if len(text) > limit_bytes * 2 or len(text) % 2 or not _LOWER_HEX.fullmatch(text):
        _fail(code, field + " is not bounded lowercase hexadecimal")
    try:
        return bytes.fromhex(text)
    except ValueError:
        _fail(code, field + " is not valid hexadecimal")


def verify_summary(value, mode, expected_path_entries):
    """Verify one bounded smoke/suite record and return its expected math/digests.

    Dynamic input is limited to independently observed clock facts and the
    independently supplied canonical path-entry count. Every other counter,
    body byte, digest, and marker byte is derived here from the literal fixture
    and source-audited equations.
    """
    p_expected = _require_path_entries(expected_path_entries)
    expected_name, record = _validate_top(value, mode)
    _validate_record_shape(record)

    if record["pathEntries"] != p_expected:
        _fail("ORACLE_PATH", "reported path-entry count differs from independent P")

    cutoff = record["exportCutoffNanos"]
    before = record["beforeExportElapsedNanos"]
    after = record["afterExportElapsedNanos"]
    setup_nanos = record["setupMeasureNanos"]
    metadata_nanos = record["metadataMeasureNanos"]
    positive = mode == "smoke"
    export_nanos = record["exportMeasureNanos"]
    elapsed_nanos = record["metricsElapsedNanos"]

    if cutoff > MAX_ELAPSED_NANOS or before > cutoff or cutoff > after or after > MAX_ELAPSED_NANOS:
        _fail("ORACLE_CLOCK", "export cutoff is outside its measured elapsed interval")
    if setup_nanos > MAX_MEASURE_NANOS or metadata_nanos > MAX_MEASURE_NANOS:
        _fail("ORACLE_CLOCK", "setup or metadata measure exceeds five seconds")
    if not positive and after - cutoff > MAX_NEGATIVE_AFTER_CUTOFF_NANOS:
        _fail("ORACLE_CLOCK", "negative close attempt exceeds five seconds after cutoff")
    if positive:
        if export_nanos > MAX_MEASURE_NANOS:
            _fail("ORACLE_CLOCK", "export measure exceeds five seconds")
        if elapsed_nanos < cutoff + export_nanos or elapsed_nanos > after:
            _fail("ORACLE_CLOCK", "returned elapsed metric is outside the closed prefix")
        if elapsed_nanos > MAX_ELAPSED_NANOS:
            _fail("ORACLE_CLOCK", "returned elapsed metric exceeds ninety seconds")
    elif elapsed_nanos != 0:
        _fail("ORACLE_CLOCK", "failed ticket must not return a closed elapsed metric")

    ledger = _ledger_values(p_expected, cutoff, setup_nanos, metadata_nanos)
    expected_body = expected_body_bytes(p_expected, cutoff, setup_nanos, metadata_nanos)
    if len(expected_body) > MAX_RESULT_BYTES:
        _fail("ORACLE_RESULT_BYTES", "expected result exceeds the admitted fixture bound")
    result_sha256 = hashlib.sha256(expected_body).hexdigest()
    result_blocks = (len(expected_body) + RECEIPT_BYTES - 1) // RECEIPT_BYTES
    fee_units = 5 * result_blocks + (3 if positive else 1)
    fee_chunks = (2 * result_blocks + 2) if positive else 2 * result_blocks

    export_units = fee_units if positive else 0
    max_group_units = max(SETUP_UNITS, ledger["metadataUnits"], export_units)
    max_group_nanos = max(setup_nanos, metadata_nanos, export_nanos if positive else 0)
    if positive:
        expected_marker = expected_marker_bytes(
            result_sha256, len(expected_body), elapsed_nanos, export_nanos,
            ledger["unitsBefore"] + fee_units, max_group_nanos, max_group_units,
        )
        if len(expected_marker) > MAX_MARKER_BYTES:
            _fail("ORACLE_MARKER_BYTES", "expected marker exceeds the admitted bound")
        expected_marker_sha256 = hashlib.sha256(expected_marker).hexdigest()
        marker_bytes = len(expected_marker)
    else:
        expected_marker = b""
        expected_marker_sha256 = ""
        marker_bytes = 0
    fee_copies = (
        2 * len(expected_body) + 32 + marker_bytes
        if positive else 2 * len(expected_body)
    )
    expected_record = {
        "name": expected_name,
        "status": "PASS",
        "phase": "complete",
        "reason": "OK",
        "cleanupDisposition": (
            "ALREADY_CLOSED_AND_REMOVED" if positive else
            "EXPECTED_PARTIAL_EXPORT_METER_DISCARDED_AND_REMOVED"
        ),
        "error": "",
        "cleanupError": "",
        "expectedFailure": "" if positive else "FINAL_SHA_MISMATCH",
        "meterOpen": not positive,
        "stdoutRestored": True,
        "metricsReturned": positive,
        "meterDiscarded": not positive,
        "receiptMutationApplied": not positive,
        "receiptBytes": RECEIPT_BYTES,
        "receiptHashBlocks": RECEIPT_HASH_BLOCKS,
        "units": ledger["unitsBefore"] + fee_units,
        "copiedEntries": ledger["copiesBefore"] + fee_copies,
        "copyChunks": ledger["chunksBefore"] + fee_chunks,
        "wrapperGroups": 3 if positive else 2,
        "priorGroups": 1,
        "groupUnits": fee_units,
        "maxGroupUnits": max_group_units,
        "maxGroupNanos": max_group_nanos,
        "closedGroupMeasures": 3 if positive else 2,
        "workFinalizedSlots": 34,
        "setupMeasureUnits": SETUP_UNITS,
        "metadataMeasureUnits": ledger["metadataUnits"],
        "exportMeasureUnits": fee_units if positive else 0,
        "exportMeasureNanos": export_nanos if positive else 0,
        "resultBytesObserved": len(expected_body),
        "metricsResultBytes": len(expected_body) if positive else 0,
        "metricsMarkerBytes": marker_bytes,
        "metricsExportUnits": fee_units if positive else 0,
        "metricsFinalExportNanos": export_nanos if positive else 0,
        "metricsElapsedNanos": elapsed_nanos if positive else 0,
        "metricsTotalUnits": ledger["unitsBefore"] + fee_units if positive else 0,
        "metricsTotalGroups": 4 if positive else 0,
        "metricsMaxGroupNanos": max_group_nanos if positive else 0,
        "metricsMaximumUnitsPerGroup": max_group_units if positive else 0,
        "metricsCopiedEntriesDelta": fee_copies if positive else 0,
        "metricsCopyChunksDelta": fee_chunks if positive else 0,
    }
    _expect_equal(record, expected_record)

    actual_result = _decode_hex_field(record, "resultHex", MAX_RESULT_BYTES, "ORACLE_RESULT_BYTES")
    if actual_result != expected_body:
        _fail("ORACLE_RESULT_BYTES", "actual result bytes differ from the independent body")

    actual_marker = _decode_hex_field(record, "markerHex", MAX_MARKER_BYTES, "ORACLE_MARKER_BYTES")
    if actual_marker != expected_marker:
        _fail("ORACLE_MARKER_BYTES", "actual marker bytes differ from the independent marker")

    return {
        "mode": mode.upper(),
        "name": expected_name,
        "expectedR": len(expected_body),
        "resultSha256": result_sha256,
        "resultBlocks": result_blocks,
        "expectedM": marker_bytes,
        "markerSha256": expected_marker_sha256,
        "expectedFeeUnits": fee_units,
        "expectedFeeCopiedEntries": fee_copies,
        "expectedFeeCopyChunks": fee_chunks,
        "exportCutoffNanos": cutoff,
    }
