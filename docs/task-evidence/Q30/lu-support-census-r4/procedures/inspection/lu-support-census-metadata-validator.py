#!/usr/bin/env python3
"""Independent, fail-closed reader for the private Q30 LU support census.

The producer serializes Java longs as canonical decimal strings.  This module
recomputes the graph partition, support conservation, and the component-label
replay from each successful sample's defensive ``ipvtPrefix``.  It does not
measure time, estimate work seconds, or turn structural candidate counts into
an optimization claim.
"""

import argparse
import copy
import json
from pathlib import Path


SELECTION_INTERVAL = 4096
MAX_SELECTED_SAMPLES = 256
MAX_MATRIX_SIZE = 512
LONG_MIN = -(1 << 63)
LONG_MAX = (1 << 63) - 1

SUPPORT_FIELDS = {
    "phase", "selectionInterval", "valid", "frozen", "scopeComplete",
    "completeCapture", "zeroPivotObservationAvailable",
    "eligibleFactorCalls", "selectedFactorCalls", "completedFactorCalls",
    "failedFactorCalls", "droppedSampleRecords", "scanErrors",
    "diagnosticErrors", "scanCellReads", "lifecycleErrors",
    "counterOverflow", "samples",
}
SAMPLE_FIELDS = {
    "ordinal", "matrixSize", "factorSucceeded", "ipvtTraceComplete",
    "ipvtPrefix", "zeroPivotFallbacks", "postFactorSentinelHits",
    "snapshot", "opportunity",
}
SNAPSHOT_FIELDS = {
    "matrixSize", "cellReads", "supportAdded", "supportRemoved",
    "supportUnchanged", "valueDifferences", "signedZeroDifferences",
    "currentCrossOriginalEdges", "stableOriginalComponents", "currentGraph",
    "originalGraph",
}
GRAPH_FIELDS = {
    "matrixSize", "edges", "nonfiniteEntries", "rowComponent",
    "columnComponent", "componentCount", "components",
}
COMPONENT_FIELDS = {"id", "edges", "rows", "columns"}
OPPORTUNITY_FIELDS = {
    "fullPivotCandidates", "localPivotCandidates", "fullLowerCandidates",
    "localLowerCandidates", "fullUpperCandidates", "localUpperCandidates",
    "replayComplete", "exact", "initialOrderOnly", "stableOriginalComponents",
    "crossGroupPivot", "zeroPivotRisk", "nonfiniteSupportData",
    "zeroPivotObservationAvailable", "postFactorZeroRisk",
    "zeroPivotFallbacks", "postFactorSentinelHits",
}

TOP_LONG_FIELDS = (
    "eligibleFactorCalls", "selectedFactorCalls", "completedFactorCalls",
    "failedFactorCalls", "droppedSampleRecords", "scanErrors",
    "diagnosticErrors", "scanCellReads", "lifecycleErrors", "counterOverflow",
)
SAMPLE_LONG_FIELDS = ("ordinal", "zeroPivotFallbacks", "postFactorSentinelHits")
SNAPSHOT_LONG_FIELDS = (
    "cellReads", "supportAdded", "supportRemoved", "supportUnchanged",
    "valueDifferences", "signedZeroDifferences", "currentCrossOriginalEdges",
)
GRAPH_LONG_FIELDS = ("edges", "nonfiniteEntries")
COMPONENT_LONG_FIELDS = ("edges",)
OPPORTUNITY_LONG_FIELDS = (
    "fullPivotCandidates", "localPivotCandidates", "fullLowerCandidates",
    "localLowerCandidates", "fullUpperCandidates", "localUpperCandidates",
    "zeroPivotFallbacks", "postFactorSentinelHits",
)


class ValidationError(ValueError):
    """Raised when the frozen support-census contract is not met."""


def _need(condition, message):
    if not condition:
        raise ValidationError(message)


def _keys(value, expected, label):
    _need(isinstance(value, dict), label + " must be an object")
    actual = set(value)
    missing = sorted(expected - actual)
    extra = sorted(actual - expected)
    _need(not missing, label + " is missing keys: " + ", ".join(missing))
    _need(not extra, label + " has unexpected keys: " + ", ".join(extra))


def _bool(value, label):
    _need(type(value) is bool, label + " must be a boolean")
    return value


def _int(value, label, minimum=0, maximum=None):
    _need(type(value) is int,
          label + " must be an integer; booleans are not integers")
    _need(value >= minimum, label + " is below its minimum")
    if maximum is not None:
        _need(value <= maximum, label + " is above its maximum")
    return value


def _long(value, label, minimum=0, maximum=LONG_MAX):
    """Read a canonical signed-long decimal string, normally nonnegative."""
    _need(isinstance(value, str), label + " must be a decimal string")
    _need(bool(value) and value.isascii(),
          label + " must be a nonempty ASCII decimal string")
    digits = value
    negative = value.startswith("-")
    if negative:
        digits = value[1:]
    _need(bool(digits) and digits.isascii() and digits.isdecimal(),
          label + " is not a canonical decimal string")
    if len(digits) > 1:
        _need(digits[0] != "0", label + " has a leading zero")
    if negative:
        _need(digits != "0", label + " must not use negative zero")
    try:
        result = int(value, 10)
    except (ValueError, OverflowError):
        raise ValidationError(label + " is outside signed-long range")
    _need(LONG_MIN <= result <= LONG_MAX,
          label + " is outside signed-long range")
    _need(minimum <= result <= maximum, label + " is outside its allowed range")
    return result


def _long_fields(value, fields, label):
    return {field: _long(value[field], label + "." + field) for field in fields}


def _same_partition(current, original):
    """Mirror the producer's two-direction component bijection check."""
    n = current["matrixSize"]
    if n != original["matrixSize"]:
        return False
    current_count = current["componentCount"]
    original_count = original["componentCount"]
    current_to_original = {}
    original_to_current = {}
    for vertex in range(2 * n):
        current_id = (current["rowComponent"][vertex] if vertex < n else
                      current["columnComponent"][vertex - n])
        original_id = (original["rowComponent"][vertex] if vertex < n else
                       original["columnComponent"][vertex - n])
        prior = current_to_original.setdefault(current_id, original_id)
        if prior != original_id:
            return False
        prior = original_to_current.setdefault(original_id, current_id)
        if prior != current_id:
            return False
    return (len(current_to_original) == current_count and
            len(original_to_current) == original_count)


def _validate_graph(graph, label):
    _keys(graph, GRAPH_FIELDS, label)
    n = _int(graph["matrixSize"], label + ".matrixSize", 0, MAX_MATRIX_SIZE)
    edges = _long(graph["edges"], label + ".edges")
    nonfinite = _long(graph["nonfiniteEntries"], label + ".nonfiniteEntries")
    _need(edges <= n * n, label + ".edges exceed matrix cells")
    _need(nonfinite <= edges, label + ".nonfiniteEntries exceed edges")

    component_count = _int(graph["componentCount"], label + ".componentCount",
                           0, 2 * n)
    _need((n == 0 and component_count == 0) or
          (n > 0 and component_count > 0),
          label + ".componentCount does not cover the graph vertices")
    rows = graph["rowComponent"]
    columns = graph["columnComponent"]
    _need(isinstance(rows, list) and len(rows) == n,
          label + ".rowComponent length differs from matrix size")
    _need(isinstance(columns, list) and len(columns) == n,
          label + ".columnComponent length differs from matrix size")
    for index, value in enumerate(rows):
        _int(value, label + ".rowComponent[{}]".format(index),
             0, component_count - 1)
    for index, value in enumerate(columns):
        _int(value, label + ".columnComponent[{}]".format(index),
             0, component_count - 1)

    components = graph["components"]
    _need(isinstance(components, list) and len(components) == component_count,
          label + ".components length differs from componentCount")
    expected_rows = [[] for _ in range(component_count)]
    expected_columns = [[] for _ in range(component_count)]
    for index, component_id in enumerate(rows):
        expected_rows[component_id].append(index)
    for index, component_id in enumerate(columns):
        expected_columns[component_id].append(index)
    total_edges = 0
    for index, component in enumerate(components):
        component_label = label + ".components[{}]".format(index)
        _keys(component, COMPONENT_FIELDS, component_label)
        _need(_int(component["id"], component_label + ".id", 0) == index,
              component_label + ".id must be its array index")
        component_edges = _long(component["edges"], component_label + ".edges")
        component_rows = component["rows"]
        component_columns = component["columns"]
        _need(isinstance(component_rows, list), component_label + ".rows must be an array")
        _need(isinstance(component_columns, list),
              component_label + ".columns must be an array")
        _need(component_rows or component_columns,
              component_label + ".rows and .columns cannot both be empty")
        for member_index, row in enumerate(component_rows):
            _int(row, component_label + ".rows[{}]".format(member_index),
                 0, n - 1)
        for member_index, column in enumerate(component_columns):
            _int(column, component_label + ".columns[{}]".format(member_index),
                 0, n - 1)
        _need(component_rows == expected_rows[index],
              component_label + ".rows do not match rowComponent")
        _need(component_columns == expected_columns[index],
              component_label + ".columns do not match columnComponent")
        # The producer counts each nonzero edge under its row component.  A
        # component cannot contain more edges than its row/column rectangle.
        _need(component_edges <= len(component_rows) * len(component_columns),
              component_label + ".edges exceed its component rectangle")
        total_edges += component_edges
    _need(total_edges == edges, label + ".component edges do not sum to graph edges")
    return {
        "matrixSize": n,
        "edges": edges,
        "nonfiniteEntries": nonfinite,
        "rowComponent": list(rows),
        "columnComponent": list(columns),
        "componentCount": component_count,
        "components": components,
    }


def _validate_snapshot(snapshot, n, label):
    _keys(snapshot, SNAPSHOT_FIELDS, label)
    _need(_int(snapshot["matrixSize"], label + ".matrixSize", 0,
               MAX_MATRIX_SIZE) == n,
          label + ".matrixSize differs from sample")
    counts = _long_fields(snapshot, SNAPSHOT_LONG_FIELDS, label)
    current = _validate_graph(snapshot["currentGraph"], label + ".currentGraph")
    original = _validate_graph(snapshot["originalGraph"], label + ".originalGraph")
    _need(current["matrixSize"] == n and original["matrixSize"] == n,
          label + " graph matrix size differs from snapshot")
    _need(counts["cellReads"] == n * n * 5,
          label + ".cellReads does not match the declared five-pass count")
    _need(counts["supportAdded"] + counts["supportUnchanged"] == current["edges"],
          label + " added/unchanged counts do not match current edges")
    _need(counts["supportRemoved"] + counts["supportUnchanged"] == original["edges"],
          label + " removed/unchanged counts do not match original edges")
    _need(counts["supportAdded"] + counts["supportRemoved"] +
          counts["supportUnchanged"] <= n * n,
          label + " support counts exceed matrix cells")
    _need(counts["supportAdded"] + counts["supportRemoved"] <=
          counts["valueDifferences"],
          label + " support changes are missing from value differences")
    _need(counts["signedZeroDifferences"] <= counts["valueDifferences"],
          label + ".signedZeroDifferences exceed valueDifferences")
    _need(counts["valueDifferences"] <= n * n,
          label + ".valueDifferences exceed matrix cells")
    _need(counts["currentCrossOriginalEdges"] <= current["edges"],
          label + ".currentCrossOriginalEdges exceed current edges")
    stable = _bool(snapshot["stableOriginalComponents"],
                   label + ".stableOriginalComponents")
    recomputed_stable = _same_partition(current, original)
    _need(stable == recomputed_stable,
          label + ".stableOriginalComponents disagrees with graph partitions")
    if stable:
        _need(counts["currentCrossOriginalEdges"] == 0,
              label + " stable partitions cannot have cross-original edges")
    # Valid complete capture rejects any nonfinite support data in the Java
    # producer, so retaining it here would accept a report marked invalid.
    _need(current["nonfiniteEntries"] == 0 and original["nonfiniteEntries"] == 0,
          label + " contains nonfinite support data")
    return {"counts": counts, "current": current, "original": original,
            "stable": stable}


def _replay(graph, ipvt):
    n = graph["matrixSize"]
    row_labels = list(graph["rowComponent"])
    local_pivot = 0
    local_lower = 0
    local_upper = 0
    cross = False
    zero_risk = False
    for k in range(n):
        pivot = ipvt[k]
        _need(k <= pivot < n,
              "ipvtPrefix[{}] is outside the allowed pivot domain".format(k))
        column_label = graph["columnComponent"][k]
        selected_label = row_labels[pivot]
        if selected_label != column_label:
            cross = True
        pivot_rows = 0
        for i in range(k, n):
            if row_labels[i] == column_label:
                local_pivot += 1
                pivot_rows += 1
                if i > k:
                    local_lower += 1
        if pivot_rows == 0:
            zero_risk = True
        for j in range(k + 1, n):
            if selected_label == graph["columnComponent"][j]:
                local_upper += 1
        if pivot != k:
            row_labels[k], row_labels[pivot] = row_labels[pivot], row_labels[k]
    return {
        "fullPivotCandidates": n * (n + 1) // 2,
        "localPivotCandidates": local_pivot,
        "fullLowerCandidates": n * (n - 1) // 2,
        "localLowerCandidates": local_lower,
        "fullUpperCandidates": n * (n - 1) // 2,
        "localUpperCandidates": local_upper,
        "replayComplete": True,
        "crossGroupPivot": cross,
        "zeroPivotRisk": zero_risk,
    }


def _validate_opportunity(opportunity, sample, snapshot, label):
    _keys(opportunity, OPPORTUNITY_FIELDS, label)
    values = _long_fields(opportunity, OPPORTUNITY_LONG_FIELDS, label)
    bools = {}
    for field in (
        "replayComplete", "exact", "initialOrderOnly",
        "stableOriginalComponents", "crossGroupPivot", "zeroPivotRisk",
        "nonfiniteSupportData", "zeroPivotObservationAvailable",
        "postFactorZeroRisk",
    ):
        bools[field] = _bool(opportunity[field], label + "." + field)

    n = sample["matrixSize"]
    replay = _replay(snapshot["current"], sample["ipvtPrefix"])
    for field in (
        "fullPivotCandidates", "localPivotCandidates", "fullLowerCandidates",
        "localLowerCandidates", "fullUpperCandidates", "localUpperCandidates",
    ):
        _need(values[field] == replay[field],
              label + "." + field + " disagrees with independent ipvt replay")
    _need(bools["replayComplete"] is True,
          label + ".replayComplete must be true for a complete capture")
    _need(bools["initialOrderOnly"] is False,
          label + ".initialOrderOnly cannot accompany a successful replay")
    _need(bools["crossGroupPivot"] == replay["crossGroupPivot"],
          label + ".crossGroupPivot disagrees with independent replay")
    _need(bools["zeroPivotRisk"] == replay["zeroPivotRisk"],
          label + ".zeroPivotRisk disagrees with independent replay")
    _need(bools["stableOriginalComponents"] == snapshot["stable"],
          label + ".stableOriginalComponents disagrees with snapshot")
    _need(bools["nonfiniteSupportData"] is False,
          label + ".nonfiniteSupportData must be false for valid capture")
    _need(bools["zeroPivotObservationAvailable"] is False,
          label + ".zeroPivotObservationAvailable must remain false")
    _need(bools["exact"] is False,
          label + ".exact cannot be claimed without the fallback observation hook")
    sample_fallbacks = _long(sample["zeroPivotFallbacks"],
                             label + " sample zeroPivotFallbacks")
    sample_sentinel_hits = _long(sample["postFactorSentinelHits"],
                                 label + " sample postFactorSentinelHits")
    _need(sample_fallbacks == 0,
          label + " reports fallback observations despite unavailable hook")
    _need(sample_sentinel_hits <= n,
          label + " sentinel hits exceed diagonal positions")
    _need(values["zeroPivotFallbacks"] == sample_fallbacks,
          label + ".zeroPivotFallbacks differs from sample")
    _need(values["postFactorSentinelHits"] == sample_sentinel_hits,
          label + ".postFactorSentinelHits differs from sample")
    _need(bools["postFactorZeroRisk"] == (sample_sentinel_hits > 0),
          label + ".postFactorZeroRisk disagrees with sentinel hits")
    return {"replay": replay, "values": values, "booleans": bools}


def _validate_support(support, phase):
    _keys(support, SUPPORT_FIELDS, phase + "SupportCensus")
    _need(support["phase"] == phase,
          phase + " support-census phase differs from requested phase")
    _int(support["selectionInterval"], phase + ".selectionInterval", 1)
    _need(support["selectionInterval"] == SELECTION_INTERVAL,
          phase + " selection interval changed from the private contract")
    for field in ("valid", "frozen", "scopeComplete", "completeCapture",
                  "zeroPivotObservationAvailable"):
        _bool(support[field], phase + "." + field)
    _need(support["valid"] is True, phase + " support census is not valid")
    _need(support["frozen"] is True, phase + " support census is not frozen")
    _need(support["scopeComplete"] is True,
          phase + " support census scope is incomplete")
    _need(support["completeCapture"] is True,
          phase + " support census capture is incomplete")
    _need(support["zeroPivotObservationAvailable"] is False,
          phase + " unexpectedly claims a zero-pivot observation hook")

    top = _long_fields(support, TOP_LONG_FIELDS, phase)
    eligible = top["eligibleFactorCalls"]
    selected = top["selectedFactorCalls"]
    _need(selected == eligible // SELECTION_INTERVAL,
          phase + " selected count does not match eligible cadence")
    _need(0 < selected <= MAX_SELECTED_SAMPLES,
          phase + " selected count is outside the bounded sample cap")
    _need(top["completedFactorCalls"] == selected,
          phase + " completed factor count differs from selected count")
    for field in (
        "failedFactorCalls", "droppedSampleRecords", "scanErrors",
        "diagnosticErrors", "lifecycleErrors", "counterOverflow",
    ):
        _need(top[field] == 0, phase + "." + field + " must be zero")

    samples = support["samples"]
    _need(isinstance(samples, list) and len(samples) == selected,
          phase + ".samples length differs from selected factor count")
    total_cell_reads = 0
    sizes = []
    stable_partition_samples = 0
    samples_with_support_drift = 0
    for index, sample in enumerate(samples):
        label = phase + ".samples[{}]".format(index)
        _keys(sample, SAMPLE_FIELDS, label)
        ordinal = _long(sample["ordinal"], label + ".ordinal", 1)
        _need(ordinal == index + 1, label + ".ordinal is not the selected order")
        n = _int(sample["matrixSize"], label + ".matrixSize", 0, MAX_MATRIX_SIZE)
        _need(_bool(sample["factorSucceeded"], label + ".factorSucceeded") is True,
              label + ".factorSucceeded must be true")
        _need(_bool(sample["ipvtTraceComplete"], label + ".ipvtTraceComplete") is True,
              label + ".ipvtTraceComplete must be true")
        ipvt = sample["ipvtPrefix"]
        _need(isinstance(ipvt, list) and len(ipvt) == n,
              label + ".ipvtPrefix length differs from matrix size")
        for k, pivot in enumerate(ipvt):
            _int(pivot, label + ".ipvtPrefix[{}]".format(k), k, n - 1)
        _long(sample["zeroPivotFallbacks"], label + ".zeroPivotFallbacks")
        _long(sample["postFactorSentinelHits"], label + ".postFactorSentinelHits")
        snapshot = _validate_snapshot(sample["snapshot"], n, label + ".snapshot")
        _validate_opportunity(sample["opportunity"], sample, snapshot,
                              label + ".opportunity")
        total_cell_reads += snapshot["counts"]["cellReads"]
        if snapshot["stable"]:
            stable_partition_samples += 1
        if (snapshot["counts"]["supportAdded"] != 0 or
                snapshot["counts"]["supportRemoved"] != 0):
            samples_with_support_drift += 1
        sizes.append(n)
    _need(top["scanCellReads"] == total_cell_reads,
          phase + ".scanCellReads differs from sample cell-read totals")
    support_drift_free = samples_with_support_drift == 0
    return {
        "status": "PASS",
        "metadataDisposition": (
            "PASS" if support_drift_free else
            "ACCEPTED_STRUCTURAL_METADATA_NOT_OPTIMIZATION"),
        "phase": phase,
        "selectedFactorCalls": selected,
        "sampleCount": len(samples),
        "matrixSizes": sorted(set(sizes)),
        "allFactorSucceeded": True,
        "frozen": True,
        "scopeComplete": True,
        "completeCapture": True,
        "exactEvidence": False,
        "countsAreStructural": True,
        "ipvtTraceComplete": True,
        "supportDriftFree": support_drift_free,
        "stablePartitionSamples": stable_partition_samples,
        "samplesWithSupportDrift": samples_with_support_drift,
        "unknownFallback": True,
    }


def _select_support(profile, phase):
    _need(isinstance(profile, dict), "profile must be an object")
    _need(isinstance(phase, str) and phase in ("cold", "warm"),
          "phase must be cold or warm")
    key = phase + "SupportCensus"
    if key in profile:
        _need(profile.get("supportCensusRequested") is True,
              "full report must declare supportCensusRequested=true")
        _need(profile.get("supportCensusQuery") == "tsjQ30SupportCensus=true",
              "full report support-census query marker is missing or changed")
        return profile[key]
    # This narrow extracted-object form is useful to a pair wrapper after it
    # has independently selected the full raw report.  It still receives the
    # exact same nested schema checks and cannot bypass any census invariant.
    if set(profile) == SUPPORT_FIELDS and profile.get("phase") == phase:
        return profile
    raise ValidationError("full report is missing " + key)


def validate_report(profile, phase):
    """Validate one cold or warm census from a full Q30 report.

    The input is never rewritten.  The returned values are structural counts
    and replay status only; they intentionally contain no elapsed-time or
    savings interpretation.
    """
    return _validate_support(_select_support(profile, phase), phase)


def _graph_fixture():
    # Two stable components, with row and column order aligned so identity
    # ipvt replay is complete and independently recomputable.
    return {
        "matrixSize": 3,
        "edges": "5",
        "nonfiniteEntries": "0",
        "rowComponent": [0, 1, 1],
        "columnComponent": [0, 1, 1],
        "componentCount": 2,
        "components": [
            {"id": 0, "edges": "1", "rows": [0], "columns": [0]},
            {"id": 1, "edges": "4", "rows": [1, 2], "columns": [1, 2]},
        ],
    }


def _sample_fixture(phase):
    graph = _graph_fixture()
    snapshot = {
        "matrixSize": 3,
        "cellReads": "45",
        "supportAdded": "0",
        "supportRemoved": "0",
        "supportUnchanged": "5",
        "valueDifferences": "0",
        "signedZeroDifferences": "0",
        "currentCrossOriginalEdges": "0",
        "stableOriginalComponents": True,
        "currentGraph": copy.deepcopy(graph),
        "originalGraph": copy.deepcopy(graph),
    }
    opportunity = {
        "fullPivotCandidates": "6",
        "localPivotCandidates": "4",
        "fullLowerCandidates": "3",
        "localLowerCandidates": "1",
        "fullUpperCandidates": "3",
        "localUpperCandidates": "1",
        "replayComplete": True,
        "exact": False,
        "initialOrderOnly": False,
        "stableOriginalComponents": True,
        "crossGroupPivot": False,
        "zeroPivotRisk": False,
        "nonfiniteSupportData": False,
        "zeroPivotObservationAvailable": False,
        "postFactorZeroRisk": False,
        "zeroPivotFallbacks": "0",
        "postFactorSentinelHits": "0",
    }
    return {
        "ordinal": "1",
        "matrixSize": 3,
        "factorSucceeded": True,
        "ipvtTraceComplete": True,
        "ipvtPrefix": [0, 1, 2],
        "zeroPivotFallbacks": "0",
        "postFactorSentinelHits": "0",
        "snapshot": snapshot,
        "opportunity": opportunity,
    }


def _support_fixture(phase):
    return {
        "phase": phase,
        "selectionInterval": SELECTION_INTERVAL,
        "valid": True,
        "frozen": True,
        "scopeComplete": True,
        "completeCapture": True,
        "zeroPivotObservationAvailable": False,
        "eligibleFactorCalls": "4096",
        "selectedFactorCalls": "1",
        "completedFactorCalls": "1",
        "failedFactorCalls": "0",
        "droppedSampleRecords": "0",
        "scanErrors": "0",
        "diagnosticErrors": "0",
        "scanCellReads": "45",
        "lifecycleErrors": "0",
        "counterOverflow": "0",
        "samples": [_sample_fixture(phase)],
    }


def _full_fixture():
    return {
        "schema": 2,
        "status": "PASS",
        "supportCensusRequested": True,
        "supportCensusQuery": "tsjQ30SupportCensus=true",
        "coldSupportCensus": _support_fixture("cold"),
        "warmSupportCensus": _support_fixture("warm"),
    }


def _support_drift_fixture():
    profile = _full_fixture()
    snapshot = profile["coldSupportCensus"]["samples"][0]["snapshot"]
    # The emitted report contains aggregate support counts rather than cell
    # coordinates.  This is a valid structural metadata shape with explicit
    # drift, and must remain measurable without becoming optimization proof.
    snapshot["supportAdded"] = "1"
    snapshot["supportRemoved"] = "1"
    snapshot["supportUnchanged"] = "4"
    snapshot["valueDifferences"] = "2"
    return profile


def selftest():
    """Run compact pure-Python positive and fail-closed mutation checks."""
    profile = _full_fixture()
    cold = validate_report(profile, "cold")
    warm = validate_report(profile, "warm")
    cases = []

    def rejects(name, mutate):
        candidate = copy.deepcopy(profile)
        mutate(candidate)
        try:
            validate_report(candidate, "cold")
        except (ValidationError, AssertionError, KeyError, TypeError, ValueError,
                OverflowError) as error:
            cases.append({"case": name, "status": "REJECTED_AS_EXPECTED",
                          "error": str(error)})
        else:
            raise AssertionError("support-census corruption was accepted: " + name)

    rejects("numeric long", lambda p: p["coldSupportCensus"].__setitem__(
        "selectedFactorCalls", 1))
    rejects("leading-zero long", lambda p: p["coldSupportCensus"].__setitem__(
        "eligibleFactorCalls", "04096"))
    rejects("invalid valid flag", lambda p: p["coldSupportCensus"].__setitem__(
        "valid", False))
    rejects("incomplete scope", lambda p: p["coldSupportCensus"].__setitem__(
        "scopeComplete", False))
    rejects("incomplete capture", lambda p: p["coldSupportCensus"].__setitem__(
        "completeCapture", False))
    rejects("failed factor", lambda p: p["coldSupportCensus"].__setitem__(
        "failedFactorCalls", "1"))
    rejects("sample count mismatch", lambda p: p["coldSupportCensus"][
        "samples"].clear())
    rejects("component member mutation", lambda p: p["coldSupportCensus"][
        "samples"][0]["snapshot"]["currentGraph"]["components"][1][
        "rows"].__setitem__(0, 0))
    rejects("boolean component index", lambda p: p["coldSupportCensus"][
        "samples"][0]["snapshot"]["currentGraph"]["components"][1][
        "rows"].__setitem__(0, True))
    rejects("float component index", lambda p: p["coldSupportCensus"][
        "samples"][0]["snapshot"]["originalGraph"]["components"][1][
        "columns"].__setitem__(0, 1.0))
    def add_empty_component(candidate):
        graph = candidate["coldSupportCensus"]["samples"][0][
            "snapshot"]["currentGraph"]
        graph["componentCount"] += 1
        graph["components"].append({"id": 2, "edges": "0",
                                     "rows": [], "columns": []})
    rejects("empty component", add_empty_component)
    rejects("pivot outside domain", lambda p: p["coldSupportCensus"][
        "samples"][0]["ipvtPrefix"].__setitem__(2, 1))
    rejects("replay metric mutation", lambda p: p["coldSupportCensus"][
        "samples"][0]["opportunity"].__setitem__("localUpperCandidates", "0"))
    rejects("stable partition mutation", lambda p: p["coldSupportCensus"][
        "samples"][0]["snapshot"].__setitem__("stableOriginalComponents", False))
    rejects("query marker mutation", lambda p: p.__setitem__(
        "supportCensusQuery", "tsjQ30SupportCensus=false"))
    rejects("unsupported exact claim", lambda p: p["coldSupportCensus"][
        "samples"][0]["opportunity"].__setitem__("exact", True))
    rejects("missing ipvt trace", lambda p: p["coldSupportCensus"][
        "samples"][0].__setitem__("ipvtTraceComplete", False))
    _need(cold["status"] == "PASS" and warm["status"] == "PASS",
          "positive cold/warm fixtures did not validate")
    drift = validate_report(_support_drift_fixture(), "cold")
    _need(drift["status"] == "PASS" and
          drift["metadataDisposition"] ==
          "ACCEPTED_STRUCTURAL_METADATA_NOT_OPTIMIZATION" and
          drift["supportDriftFree"] is False and
          drift["stablePartitionSamples"] == 1 and
          drift["samplesWithSupportDrift"] == 1 and
          drift["unknownFallback"] is True and
          drift["exactEvidence"] is False,
          "support drift was rejected or presented as optimization evidence")
    return {"status": "PASS", "positivePhases": ["cold", "warm"],
            "positiveDriftDisposition": drift["metadataDisposition"],
            "positiveDriftSummary": {
                "supportDriftFree": drift["supportDriftFree"],
                "stablePartitionSamples": drift["stablePartitionSamples"],
                "samplesWithSupportDrift": drift["samplesWithSupportDrift"],
                "unknownFallback": drift["unknownFallback"],
                "exactEvidence": drift["exactEvidence"],
            },
            "negativeCaseCount": len(cases), "negativeCases": cases}


def _read_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ValidationError("could not read UTF-8 JSON report: {}".format(error))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path, nargs="?",
                        help="raw Q30 report JSON; read-only")
    parser.add_argument("--phase", choices=("cold", "warm"), default="cold")
    parser.add_argument("--selftest", action="store_true")
    args = parser.parse_args(argv)
    try:
        if args.selftest:
            result = selftest()
        else:
            if args.report is None:
                raise ValidationError("report is required unless --selftest is used")
            result = validate_report(_read_json(args.report), args.phase)
    except (ValidationError, OSError, ValueError) as error:
        parser.error(str(error))
    print(json.dumps(result, sort_keys=True, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
