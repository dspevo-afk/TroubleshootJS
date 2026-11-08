"""Pure current-only R7 result/marker admission kernel.

This source is embedded after the frozen generic-routing T reader definitions.
It intentionally has no CLI, loader, filesystem, producer, or execution-authentication
path. T ReaderError failures retain their SW_* owner; errors introduced here use
GR7_ReaderError with GR7_* codes.
"""

GR7_RESULT_SCHEMA = "tsj-generic-routing-r7-result-v1"
GR7_RESULT_STATUS = "GENERIC_NATURAL_R7_REFERENCE_RECORDED_ONLY"
GR7_MARKER_SCHEMA = "tsj-generic-routing-r7-marker-v1"
GR7_MARKER_STATUS = "GENERIC_NATURAL_R7_REFERENCE_EXPORTED"
GR7_SUMMARY_SCHEMA = "tsj-generic-routing-r7-reader-summary-v1"
GR7_SUMMARY_STATUS = "PASS_GENERIC_R7_CANONICAL_AND_DERIVED_DEMAND_ONLY"
GR7_SCOPE = "GENERIC_NATURAL_ROUTING_R7_REFERENCE_ONLY"
GR7_STUDY_PARENT_SHA256 = "8581ca447ca5d7fde0c56970daa9ea7fc38a1f2ea97eeb8b911621f3ccedfa14"
GR7_MAX_BYTES = 1_048_576
GR7_MAX_MARKER_BYTES = 666
GR7_MAX_PRODUCER_NS = 90_000_000_000
GR7_MAX_GROUPS = 640
GR7_MAX_GROUP_NS = 5_000_000_000
GR7_MAX_GROUP_UNITS = 32_768
GR7_MAX_COPY_ENTRIES = 1_048_576
GR7_COPY_CHUNK = 4096
GR7_PHASE_KEYS = ("units", "groups", "copiedEntries", "copyChunks")
GR7_WORK_KEYS = ("setup", "open", "blocked", "metadata", "preExport", "limits")
GR7_PRE_EXPORT_KEYS = ("units", "groups", "copiedEntries", "copyChunks",
                       "maxGroupNanos", "maximumUnitsPerGroup",
                       "sessionConstructorEntries", "elapsedNanos")
GR7_LIMIT_KEYS = ("producerNanos", "maximumGroups", "maximumGroupNanos",
                  "maximumUnitsPerGroup", "maximumCopiedEntries", "copyChunkSize")
GR7_LIMIT_VALUES = (("producerNanos", GR7_MAX_PRODUCER_NS),
                    ("maximumGroups", GR7_MAX_GROUPS),
                    ("maximumGroupNanos", GR7_MAX_GROUP_NS),
                    ("maximumUnitsPerGroup", GR7_MAX_GROUP_UNITS),
                    ("maximumCopiedEntries", GR7_MAX_COPY_ENTRIES),
                    ("copyChunkSize", GR7_COPY_CHUNK))
GR7_RESULT_KEYS = ("schema", "status", "runId", "sourceManifestSha256",
                   "wireContractSha256", "references", "sourceReview", "work")
GR7_REFERENCE_IDS = ("OPEN", "BLOCKED")
GR7_REFERENCE_KEYS = ("caseId", "fixtureContextSha256", "run", "cleanup",
                      "construction", "sourceGeometry")
GR7_CLEANUP_KEYS = ("ownerReferencesDropped", "ownerCleanupNanos",
                    "wrapperAliasesDropped")
GR7_CONSTRUCTION_KEYS = ("before", "after")
GR7_GEOMETRY_KEYS = ("beforeSha256", "afterSha256", "stable")
GR7_SOURCE_REVIEW_KEYS = ("scope", "physicalOwnerSha256", "studyParentSha256",
                          "runOwnerSha256", "fixtureSourceSha256",
                          "fixtureOracleSha256", "sessionConstructorEntries")
GR7_MARKER_KEYS = ("schema", "status", "runId", "sourceManifestSha256",
                   "wireContractSha256", "fixtureOracleSha256", "resultSha256",
                   "resultBytes", "elapsedNanos", "finalExportNanos", "totalGroups",
                   "totalUnits", "maxGroupNanos", "maximumUnitsPerGroup")


class GR7_ReaderError(ValueError):
    """Path-free kernel rejection; T's separate ReaderError keeps SW_* ownership."""
    def __init__(self, code):
        self.code = "GR7_" + code
        super().__init__(self.code)


def gr7_need(condition, code):
    if not condition:
        raise GR7_ReaderError(code)


def gr7_keys(value, expected, code):
    gr7_need(type(value) is dict and tuple(value) == expected, code)


def gr7_is_int(value, low=0, high=2**63 - 1):
    return type(value) is int and low <= value <= high


def gr7_export_demand(result_bytes, marker_bytes):
    """Return only the formula-derived export demand for canonical byte lengths."""
    gr7_need(gr7_is_int(result_bytes, 1, GR7_MAX_BYTES)
             and gr7_is_int(marker_bytes, 1, GR7_MAX_MARKER_BYTES),
             "EXPORT_LENGTH")
    chunks = (result_bytes + GR7_COPY_CHUNK - 1) // GR7_COPY_CHUNK
    export_units = 5 * chunks + 3
    export_copies = 2 * result_bytes + 32 + marker_bytes
    export_chunks = 2 * chunks + 2
    return {"units": export_units, "copiedEntries": export_copies,
            "copyChunks": export_chunks, "groups": 1}


def gr7_validate_phase(value, phase_name):
    gr7_keys(value, GR7_PHASE_KEYS, "WORK_" + phase_name.upper() + "_KEYS")
    for name in GR7_PHASE_KEYS:
        gr7_need(gr7_is_int(value[name]),
                 "WORK_" + phase_name.upper() + "_INTEGER")
    gr7_need(value["groups"] <= GR7_MAX_GROUPS
             and value["units"] <= GR7_MAX_GROUPS * GR7_MAX_GROUP_UNITS
             and value["units"] <= value["groups"] * GR7_MAX_GROUP_UNITS
             and value["copiedEntries"] <= GR7_MAX_COPY_ENTRIES
             and value["copyChunks"] <= GR7_MAX_COPY_ENTRIES
             and value["copyChunks"] <= value["copiedEntries"],
             "WORK_" + phase_name.upper() + "_BOUNDS")
    if value["copiedEntries"]:
        minimum_chunks = (value["copiedEntries"] + GR7_COPY_CHUNK - 1) // GR7_COPY_CHUNK
        gr7_need(value["copyChunks"] >= minimum_chunks,
                 "WORK_" + phase_name.upper() + "_CHUNK_COVERAGE")


def gr7_validate_run_delta(phase, phase_name, run_work):
    gr7_need(type(run_work) is dict, "WORK_RUN_SHAPE")
    fields = ("ownerUnits", "wrapperUnits", "groups", "copyEntries", "copyChunks",
              "maxGroupNanos", "maximumUnitsPerGroup")
    gr7_need(all(name in run_work and gr7_is_int(run_work[name]) for name in fields),
             "WORK_RUN_COUNTERS")
    gr7_need(run_work["groups"] <= GR7_MAX_GROUPS
             and run_work["ownerUnits"] + run_work["wrapperUnits"]
                 <= run_work["groups"] * GR7_MAX_GROUP_UNITS
             and run_work["copyEntries"] <= GR7_MAX_COPY_ENTRIES
             and run_work["copyChunks"] <= GR7_MAX_COPY_ENTRIES,
             "WORK_RUN_BOUNDS")
    gr7_need(phase["units"] == run_work["ownerUnits"] + run_work["wrapperUnits"]
             and phase["groups"] == run_work["groups"]
             and phase["copiedEntries"] == run_work["copyEntries"]
             and phase["copyChunks"] == run_work["copyChunks"],
             "WORK_" + phase_name.upper() + "_RUN_DELTA")


def gr7_validate_work_ledger(work, open_run_work, blocked_run_work,
                             constructor_entries):
    """Validate the four measured phase maps and the pre-export scalar ledger.

    Run-work mappings are the preserved T per-run facts. Producer-wide totals are
    accepted only from the four explicit phase maps and their exact preExport sum.
    """
    gr7_keys(work, GR7_WORK_KEYS, "WORK_KEYS")
    phases = {}
    for phase_name in ("setup", "open", "blocked", "metadata"):
        phase = work[phase_name]
        gr7_validate_phase(phase, phase_name)
        phases[phase_name] = phase
    gr7_validate_run_delta(phases["open"], "open", open_run_work)
    gr7_validate_run_delta(phases["blocked"], "blocked", blocked_run_work)

    gr7_keys(work["preExport"], GR7_PRE_EXPORT_KEYS, "WORK_PRE_EXPORT_KEYS")
    pre = work["preExport"]
    gr7_need(all(gr7_is_int(pre[name]) for name in
                 ("units", "groups", "copiedEntries", "copyChunks")),
             "WORK_PRE_EXPORT_COUNTERS")
    gr7_need(gr7_is_int(pre["maxGroupNanos"], 1, GR7_MAX_GROUP_NS)
             and gr7_is_int(pre["maximumUnitsPerGroup"], 1, GR7_MAX_GROUP_UNITS)
             and gr7_is_int(pre["sessionConstructorEntries"], 2, 2)
             and gr7_is_int(pre["elapsedNanos"], 1, GR7_MAX_PRODUCER_NS),
             "WORK_PRE_EXPORT_SCALARS")
    gr7_need(gr7_is_int(constructor_entries, 2, 2)
             and pre["sessionConstructorEntries"] == constructor_entries,
             "WORK_CONSTRUCTOR_BINDING")

    gr7_need(pre["units"] == sum(phases[name]["units"] for name in GR7_WORK_KEYS[:4])
             and pre["groups"] == sum(phases[name]["groups"] for name in GR7_WORK_KEYS[:4])
             and pre["copiedEntries"] == sum(
                 phases[name]["copiedEntries"] for name in GR7_WORK_KEYS[:4])
             and pre["copyChunks"] == sum(
                 phases[name]["copyChunks"] for name in GR7_WORK_KEYS[:4]),
             "WORK_PHASE_SUM")
    gr7_need(pre["groups"] <= GR7_MAX_GROUPS
             and pre["units"] <= GR7_MAX_GROUPS * GR7_MAX_GROUP_UNITS
             and pre["units"] <= pre["groups"] * GR7_MAX_GROUP_UNITS
             and pre["copiedEntries"] <= GR7_MAX_COPY_ENTRIES
             and pre["copyChunks"] <= GR7_MAX_COPY_ENTRIES
             and pre["copyChunks"] <= pre["copiedEntries"],
             "WORK_PRE_EXPORT_BOUNDS")
    gr7_need(pre["units"] <= pre["groups"] * pre["maximumUnitsPerGroup"],
             "WORK_PRE_EXPORT_MAXIMUM_CAPACITY")
    if pre["copiedEntries"]:
        minimum_chunks = (pre["copiedEntries"] + GR7_COPY_CHUNK - 1) // GR7_COPY_CHUNK
        gr7_need(pre["copyChunks"] >= minimum_chunks,
                 "WORK_PRE_EXPORT_CHUNK_COVERAGE")
    gr7_need(pre["maxGroupNanos"] >= max(open_run_work["maxGroupNanos"],
                                          blocked_run_work["maxGroupNanos"])
             and pre["maximumUnitsPerGroup"] >= max(
                 open_run_work["maximumUnitsPerGroup"],
                 blocked_run_work["maximumUnitsPerGroup"]),
             "WORK_PRE_EXPORT_MAXIMA")

    gr7_keys(work["limits"], GR7_LIMIT_KEYS, "WORK_LIMIT_KEYS")
    for name, expected in GR7_LIMIT_VALUES:
        gr7_need(gr7_is_int(work["limits"][name], expected, expected),
                 "WORK_LIMIT_VALUE")
    return {name: pre[name] for name in GR7_PRE_EXPORT_KEYS}


def gr7_validate_result_value(value, expected_run_id, expected_source_sha,
                              expected_wire_sha, spec):
    gr7_keys(value, GR7_RESULT_KEYS, "RESULT_KEYS")
    gr7_need(value["schema"] == GR7_RESULT_SCHEMA
             and value["status"] == GR7_RESULT_STATUS
             and value["runId"] == expected_run_id
             and value["sourceManifestSha256"] == expected_source_sha
             and value["wireContractSha256"] == expected_wire_sha,
             "RESULT_IDENTITY")

    references = value["references"]
    gr7_keys(references, GR7_REFERENCE_IDS, "REFERENCES_KEYS")
    for ordinal, case_id in enumerate(GR7_REFERENCE_IDS):
        reference = references[case_id]
        gr7_keys(reference, GR7_REFERENCE_KEYS, "REFERENCE_KEYS")
        gr7_need(reference["caseId"] == case_id
                 and reference["fixtureContextSha256"] == FIXTURE_SPEC_SHA256,
                 "REFERENCE_IDENTITY")
        run = reference["run"]
        _wire_run_order(run)
        validate_generic_fixture_run(run, fixture_context(spec, case_id))

        cleanup = reference["cleanup"]
        gr7_keys(cleanup, GR7_CLEANUP_KEYS, "CLEANUP_KEYS")
        gr7_need(cleanup["ownerReferencesDropped"] is True
                 and cleanup["wrapperAliasesDropped"] is True
                 and gr7_is_int(cleanup["ownerCleanupNanos"], 0, 120_000_000_000),
                 "CLEANUP_BINDING")

        construction = reference["construction"]
        gr7_keys(construction, GR7_CONSTRUCTION_KEYS, "CONSTRUCTION_KEYS")
        gr7_need(gr7_is_int(construction["before"], ordinal, ordinal)
                 and gr7_is_int(construction["after"], ordinal + 1, ordinal + 1),
                 "CONSTRUCTION_BINDING")

        geometry = reference["sourceGeometry"]
        gr7_keys(geometry, GR7_GEOMETRY_KEYS, "SOURCE_GEOMETRY_KEYS")
        gr7_need(type(geometry["beforeSha256"]) is str
                 and SHA.fullmatch(geometry["beforeSha256"]) is not None
                 and geometry["beforeSha256"] == geometry["afterSha256"]
                 and geometry["stable"] is True,
                 "SOURCE_GEOMETRY_BINDING")

    review = value["sourceReview"]
    gr7_keys(review, GR7_SOURCE_REVIEW_KEYS, "SOURCE_REVIEW_KEYS")
    gr7_need(review["scope"] == GR7_SCOPE
             and review["physicalOwnerSha256"] == spec["physicalOwnerSha256"]
             and review["studyParentSha256"] == GR7_STUDY_PARENT_SHA256
             and review["runOwnerSha256"] == spec["runOwnerSha256"]
             and review["fixtureSourceSha256"] == spec["fixtureSourceSha256"]
             and review["fixtureOracleSha256"] == FIXTURE_SPEC_SHA256
             and gr7_is_int(review["sessionConstructorEntries"], 2, 2),
             "SOURCE_REVIEW_BINDING")
    return gr7_validate_work_ledger(
        value["work"], references["OPEN"]["run"]["work"],
        references["BLOCKED"]["run"]["work"],
        review["sessionConstructorEntries"])


def gr7_validate_marker_scalars(marker, marker_raw, result_raw, expected_run_id,
                                expected_source_sha, expected_wire_sha,
                                pre_export):
    gr7_keys(marker, GR7_MARKER_KEYS, "MARKER_KEYS")
    gr7_need(type(marker_raw) is bytes and len(marker_raw) <= GR7_MAX_MARKER_BYTES,
             "MARKER_SIZE")
    gr7_need(marker["schema"] == GR7_MARKER_SCHEMA
             and marker["status"] == GR7_MARKER_STATUS
             and marker["runId"] == expected_run_id
             and marker["sourceManifestSha256"] == expected_source_sha
             and marker["wireContractSha256"] == expected_wire_sha
             and marker["fixtureOracleSha256"] == FIXTURE_SPEC_SHA256,
             "MARKER_IDENTITY")

    result_sha = hashlib.sha256(result_raw).hexdigest()
    gr7_need(type(marker["resultSha256"]) is str
             and SHA.fullmatch(marker["resultSha256"]) is not None
             and marker["resultSha256"] == result_sha
             and gr7_is_int(marker["resultBytes"], 2, GR7_MAX_BYTES)
             and marker["resultBytes"] == len(result_raw),
             "MARKER_RESULT_BINDING")
    gr7_need(gr7_is_int(marker["elapsedNanos"], 1, GR7_MAX_PRODUCER_NS)
             and marker["elapsedNanos"] >= pre_export["elapsedNanos"]
             and gr7_is_int(marker["finalExportNanos"], 1, GR7_MAX_GROUP_NS)
             and marker["elapsedNanos"] >= marker["finalExportNanos"],
             "MARKER_DURATION")

    demand = gr7_export_demand(len(result_raw), len(marker_raw))
    expected_groups = pre_export["groups"] + demand["groups"]
    expected_units = pre_export["units"] + demand["units"]
    expected_copies = pre_export["copiedEntries"] + demand["copiedEntries"]
    expected_chunks = pre_export["copyChunks"] + demand["copyChunks"]
    gr7_need(gr7_is_int(marker["maximumUnitsPerGroup"], 1, GR7_MAX_GROUP_UNITS),
             "MARKER_MAXIMA")
    gr7_need(gr7_is_int(marker["totalGroups"], 1, GR7_MAX_GROUPS)
             and marker["totalGroups"] == expected_groups
             and gr7_is_int(marker["totalUnits"], 0,
                            GR7_MAX_GROUPS * GR7_MAX_GROUP_UNITS)
             and marker["totalUnits"] == expected_units
             and expected_units <= expected_groups * GR7_MAX_GROUP_UNITS
             and expected_units <= expected_groups * marker["maximumUnitsPerGroup"],
             "MARKER_TOTAL_WORK")
    gr7_need(expected_copies <= GR7_MAX_COPY_ENTRIES
             and expected_chunks <= GR7_MAX_COPY_ENTRIES
             and expected_chunks <= expected_copies,
             "MARKER_COPY_CAPACITY")
    expected_max_group = max(pre_export["maxGroupNanos"],
                             marker["finalExportNanos"])
    expected_max_units = max(pre_export["maximumUnitsPerGroup"], demand["units"])
    gr7_need(gr7_is_int(marker["maxGroupNanos"], 1, GR7_MAX_GROUP_NS)
             and marker["maxGroupNanos"] == expected_max_group
             and gr7_is_int(marker["maximumUnitsPerGroup"], 1, GR7_MAX_GROUP_UNITS)
             and marker["maximumUnitsPerGroup"] == expected_max_units,
             "MARKER_MAXIMA")
    return {"derivedExportUnits": demand["units"],
            "derivedExportCopyEntries": demand["copiedEntries"],
            "derivedExportCopyChunks": demand["copyChunks"],
            "derivedExportGroups": demand["groups"]}


def validate_result_and_marker(result_raw, marker_raw, expected_run_id,
                               expected_source_sha, expected_wire_sha,
                               fixture_spec_raw):
    """Admit canonical semantics and derived demand, never native counter proof.

    The caller must separately bind real source/class/tool/argv/receipts, require
    successful zero exit, and verify unsuppressed native after-publication checks
    before asserting actual R7 counter proof.
    """
    gr7_need(type(expected_run_id) is str and UUID.fullmatch(expected_run_id) is not None
             and type(expected_source_sha) is str
             and SHA.fullmatch(expected_source_sha) is not None
             and type(expected_wire_sha) is str
             and SHA.fullmatch(expected_wire_sha) is not None,
             "EXPECTED_CONTEXT")
    spec = load_fixture_spec(fixture_spec_raw)
    result = parse_packet(result_raw)
    marker = parse_packet(marker_raw)
    pre_export = gr7_validate_result_value(
        result, expected_run_id, expected_source_sha, expected_wire_sha, spec)
    derived = gr7_validate_marker_scalars(
        marker, marker_raw, result_raw, expected_run_id, expected_source_sha,
        expected_wire_sha, pre_export)
    return {"schema": GR7_SUMMARY_SCHEMA,
            "status": GR7_SUMMARY_STATUS,
            "runId": expected_run_id,
            "sourceManifestSha256": expected_source_sha,
            "wireContractSha256": expected_wire_sha,
            "fixtureOracleSha256": FIXTURE_SPEC_SHA256,
            "resultSha256": hashlib.sha256(result_raw).hexdigest(),
            "resultBytes": len(result_raw),
            "markerBytes": len(marker_raw),
            "references": 2,
            "found": 1,
            "noPath": 1,
            "derivedExportUnits": derived["derivedExportUnits"],
            "derivedExportCopyEntries": derived["derivedExportCopyEntries"],
            "derivedExportCopyChunks": derived["derivedExportCopyChunks"],
            "derivedExportGroups": derived["derivedExportGroups"],
            "fullD": False,
            "R7actualCounterProof": False}
