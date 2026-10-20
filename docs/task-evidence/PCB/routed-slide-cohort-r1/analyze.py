"""Derive the measured informed14 cohort chains after strict assessment."""

import hashlib
import json
from pathlib import Path

import receipt_order_reader as assess


HERE = Path(__file__).resolve().parent
ROOTS = tuple(assess.ROOTS)


def _read(path):
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise AssertionError("cannot read sealed analysis input: " + str(path)) from exc


def _sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _need(condition, message):
    if not condition:
        raise AssertionError(message)


def _phase_inputs():
    contract = assess._load_contract()
    prior = assess._load_prior_contract(contract)

    unit_summary, unit_inputs, unit_manifest = assess._read_phase_files(
        "unit", contract, prior)
    unit_path = HERE / "unit-validation.json"
    unit = _read(unit_path)
    expected_unit = assess._unit_validation(
        contract, unit_summary, unit_inputs, unit_manifest)
    unit_sha = assess._validate_unit_prerequisite(unit_path.read_bytes(), expected_unit)
    _need(assess._typed_equal(unit, expected_unit),
          "unit-validation.json differs from the fresh assessed unit phase")

    route_summary, route_inputs, route_manifest = assess._read_phase_files(
        "routes", contract, prior)
    assessment = assess._routes_assessment(
        contract, route_summary, route_inputs, route_manifest, unit_sha)
    assessment_path = HERE / "assessment.json"
    recorded = _read(assessment_path)
    _need(assess._typed_equal(recorded, assessment),
          "assessment.json differs from the fresh assessed routes phase")

    raw = (HERE / "routes.txt").read_bytes()
    text = raw.decode("utf-8", errors="strict")
    reports = assess._parse_report_lines(text, contract["protocol"])
    return contract, prior, reports, assessment, unit, unit_sha


def _metric_guard(metrics, reference):
    return {
        "area": metrics["area"] < reference["area"],
        "copperLength": metrics["copperLength"] <= reference["copperLength"],
        "vias": metrics["vias"] <= reference["vias"],
        "largestProtectedEmptyRectangleArea": (
            metrics["largestProtectedEmptyRectangleArea"] <=
            reference["largestProtectedEmptyRectangleArea"]),
    }


def _guard_comparison(metrics, references):
    result = {name: _metric_guard(metrics, value)
              for name, value in references.items()}
    result["allFour"] = all(all(values.values()) for values in result.values())
    return result


def _accepted_row(step):
    index = step["acceptedRowIndex"]
    if index is None:
        return None
    _need(type(index) is int and not isinstance(index, bool),
          "accepted row index is not an integer after assessment")
    rows = step["rows"]
    _need(0 <= index < len(rows), "accepted row index is outside its step")
    row = rows[index]
    _need(row["feasibility"]["disposition"] == assess.PHYS.DISPOSITION_VALIDATED,
          "accepted row is not a physically validated row")
    _need(row["compaction"] is not None,
          "accepted row has no compaction result")
    return row


def _root_analysis(report):
    initial = report["sourceMetrics"]
    initial_control = report["initialControl"]["metrics"]
    sequence = []
    for step in report["steps"]:
        row = _accepted_row(step)
        if row is None:
            continue
        move = row["feasibility"]["move"]
        candidate = row["feasibility"]["candidate"]
        metrics = row["compaction"]["metrics"]
        references = {
            "currentSource": step["sourceMetrics"],
            "currentControl": step["control"]["metrics"],
            "initialSource": initial,
            "initialControl": initial_control,
        }
        guards = _guard_comparison(metrics, references)
        _need(guards["allFour"],
              "accepted row does not independently satisfy all four metric guards")
        _need(row["compaction"]["strictImprovement"] is True,
              "accepted row does not declare strict improvement")
        _need(move["analyticCopperDelta"] == move["actualCopperDelta"],
              "accepted move analytic and actual copper deltas differ")
        candidate_delta = candidate["copperLength"] - step["source"]["copperLength"]
        _need(candidate_delta == move["actualCopperDelta"],
              "physical candidate copper delta differs from the move delta")
        sequence.append({
            "step": step["index"],
            "acceptedRowIndex": step["acceptedRowIndex"],
            "move": move,
            "analyticCopperDelta": move["analyticCopperDelta"],
            "actualCopperDelta": move["actualCopperDelta"],
            "candidateCopperDelta": candidate_delta,
            "attemptedRows": len(step["rows"]),
            "sourceMetrics": step["sourceMetrics"],
            "controlMetrics": step["control"]["metrics"],
            "metrics": metrics,
            "guardComparisons": guards,
        })

    final = report["finalMetrics"]
    _need(len(sequence) == report["aggregate"]["acceptedSteps"],
          "accepted sequence does not match the assessed aggregate")
    initial_area = initial["area"]
    area_reduction = initial_area - final["area"]
    return {
        "seed": report["seed"],
        "termination": report["status"],
        "initialMetrics": initial,
        "initialControlMetrics": initial_control,
        "finalMetrics": final,
        "areaReduction": area_reduction,
        "areaReductionPercent": 100.0 * area_reduction / initial_area,
        "copperReduction": initial["copperLength"] - final["copperLength"],
        "viasReduction": initial["vias"] - final["vias"],
        "protectedVoidReduction": (
            initial["largestProtectedEmptyRectangleArea"] -
            final["largestProtectedEmptyRectangleArea"]),
        "aggregate": report["aggregate"],
        "acceptedSequence": sequence,
        "callbackElapsedMillis": report["elapsedMillis"],
    }


def result():
    contract, _prior, reports, assessment, _unit, _unit_sha = _phase_inputs()
    summaries = [row for prefix, row, _line in reports
                 if prefix == contract["protocol"]["summaryPrefix"]]
    _need([row["seed"] for row in summaries] == list(ROOTS),
          "cohort summary order changed during analysis")
    roots = [_root_analysis(report) for report in summaries]
    any_accepted = any(root["acceptedSequence"] for root in roots)
    return {
        "analysisStatus": "COMPLETE",
        "integrityDisposition": "PASS; strict reader and artifact replay passed",
        "scope": contract["protocol"]["scope"],
        "assessmentSha256": _sha(HERE / "assessment.json"),
        "unitValidationSha256": _sha(HERE / "unit-validation.json"),
        "routesLogSha256": _sha(HERE / "routes.txt"),
        "roots": roots,
        "measurementDisposition": (
            "MEASURED_PRIVATE_CHAINS" if any_accepted else "NO_STRICT_IMPROVEMENT"),
        "productionChange": "NONE",
        "fitnessDisposition": "NOT_EVALUATED",
        "boardShrinkageAcceptance": "NOT_CLAIMED",
        "limits": list(contract["protocol"]["limitsDisclosure"]),
    }


def main():
    value = result()
    target = HERE / "descent-analysis.json"
    if target.exists():
        _need(_read(target) == value,
              "existing descent-analysis.json differs; preserve it")
    else:
        target.write_text(json.dumps(value, indent=2) + "\n",
                          encoding="utf-8", newline="\n")
    print("COMPLETE", [(row["seed"], row["aggregate"]["acceptedSteps"],
                         row["areaReduction"], row["termination"])
                        for row in value["roots"]])


if __name__ == "__main__":
    main()
