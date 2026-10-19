"""Focused strict-reader and fail-closed canaries for measure.py."""

from __future__ import annotations

import copy
import sys

import measure


def _expect_rejected(label, action):
    try:
        action()
    except (OSError, ValueError):
        return
    raise AssertionError(f"canary was accepted: {label}")


def _fixture(parts=40, demand=1_000_000):
    rows = []
    for seed in measure.EXPECTED_ROOTS:
        score_rows = []
        for candidate in range(6):
            values = {
                "total": 1000 + candidate,
                "electrical": 100 + candidate,
                "region": 20 + candidate,
                "connector": 30 + candidate,
                "corridor": 40 + candidate,
                "outline": 500 + candidate,
                "courtyard": 200 + candidate,
                "demand": demand,
            }
            score_rows.append((candidate, values))
        rows.append(_render_row(seed, parts, score_rows))
    return {"status": "PINNED", "rows": rows}


def _render_row(seed, parts, score_rows, selected=0, ranked=None):
    ranked = list(range(6)) if ranked is None else list(ranked)
    score_text = []
    for candidate, values in score_rows:
        fields = ";".join(f"{field}={values[field]}" for field in measure.SCORE_FIELDS)
        score_text.append(f"{candidate}{{{fields}}}")
    canonical = (
        f"MEDIUM_BOARD@1;outcome=SUCCESS;selectedAttempt={selected};"
        f"ranked=[{', '.join(map(str, ranked))}];"
        f"placementScores=[{', '.join(score_text)}];selectedScore=1000"
    )
    return {
        "status": "PASS",
        "seed": seed,
        "version": 15,
        "density": 14,
        "parts": parts,
        "selectedAttempt": selected,
        "rankedPlacementAttempts": ranked,
        "statisticsCanonical": canonical,
    }


def _set_demand(data, row_index, candidate, demand):
    cloned = copy.deepcopy(data)
    row = cloned["rows"][row_index]
    scores, order = measure._parse_score_entries(row["statisticsCanonical"])
    ranked = measure._parse_ranked(row["statisticsCanonical"])
    score_rows = []
    for item in order:
        values = dict(scores[item])
        if item == candidate:
            values["demand"] = demand
        score_rows.append((item, values))
    row.update(_render_row(row["seed"], row["parts"], score_rows, row["selectedAttempt"], ranked))
    return cloned


def _candidate(result, candidate, seed="-1017"):
    return next(row for row in result["rows"] if row["seed"] == seed and row["candidate"] == candidate)


def _expect(condition, label):
    if not condition:
        raise AssertionError(f"canary failed: {label}")


def run_canaries():
    checks = 0

    # Positive floor, strict no-op, equality no-op, cap order, cutoff factors, and ceil parity.
    active = measure.measure_rows(_set_demand(_fixture(), 0, 4, 2_000_000))
    _expect(_candidate(active, 4)["floorAppliedStrictIncrease"], "strictly active floor")
    _expect(_candidate(active, 4)["flooredTarget"] == 5_000_000, "active floor target")
    checks += 1

    noop = measure.measure_rows(_set_demand(_fixture(), 0, 4, 2_250_001))
    _expect(not _candidate(noop, 4)["floorAppliedStrictIncrease"], "above-floor no-op")
    _expect(_candidate(noop, 4)["preFloor"] == 5_000_002, "above-floor arithmetic")
    checks += 1

    equality = measure.measure_rows(_set_demand(_fixture(), 0, 4, 2_250_000))
    _expect(_candidate(equality, 4)["preFloor"] == 5_000_000, "floor equality")
    _expect(not _candidate(equality, 4)["floorAppliedStrictIncrease"], "equality is not active")
    _expect(equality["floorCounts"]["equalityNoop"] == 1, "equality counted separately")
    checks += 1

    zero_components = _fixture()
    zero_components["rows"][0]["statisticsCanonical"] = zero_components["rows"][0]["statisticsCanonical"].replace(
        "0{total=1000;electrical=100;region=20;connector=30;corridor=40;outline=500;courtyard=200;demand=1000000}",
        "0{total=0;electrical=0;region=0;connector=0;corridor=0;outline=1;courtyard=1;demand=1}",
        1,
    )
    _expect(measure.measure_rows(zero_components)["candidateRowCount"] == 28, "canonical zero score components")
    checks += 1

    forty = measure.measure_rows(_set_demand(_fixture(parts=40), 0, 4, measure.MAX_DEMAND))
    forty_one = measure.measure_rows(_set_demand(_fixture(parts=41), 0, 4, measure.MAX_DEMAND))
    one_hundred = measure.measure_rows(_set_demand(_fixture(parts=100), 0, 5, measure.MAX_DEMAND))
    one_twenty_eight = measure.measure_rows(_set_demand(_fixture(parts=128), 0, 5, measure.MAX_DEMAND))
    _expect(_candidate(forty, 4)["finalCappedTarget"] == 63_000_000, "count 40 final cap")
    _expect(_candidate(forty_one, 4)["factor"] == 2.0, "candidate 4 large-inventory override")
    _expect(_candidate(forty_one, 4)["finalCappedTarget"] == 56_000_000, "count 41 large cap")
    _expect(_candidate(one_hundred, 5)["factor"] == 9.0, "count 100 spacing saturation")
    _expect(_candidate(one_twenty_eight, 5)["factor"] == 9.0, "count 128 spacing saturation")
    _expect(_candidate(one_twenty_eight, 5)["finalCappedTarget"] == 56_000_000, "count 128 large cap")
    checks += 1

    cutoff_40 = measure.measure_rows(_fixture(parts=40, demand=1))
    cutoff_41 = measure.measure_rows(_fixture(parts=41, demand=1))
    _expect(_candidate(cutoff_40, 5)["factorExact"] == "3/1", "candidate 5 at cutoff")
    _expect(_candidate(cutoff_41, 5)["factorExact"] == "31/10", "candidate 5 just above cutoff")
    _expect(_candidate(cutoff_41, 5)["ceilPythonIeee"] == 4, "fractional IEEE ceil")
    _expect(_candidate(cutoff_41, 5)["ceilExactRational"] == 4, "fractional rational ceil")
    _expect_rejected("ceil disagreement", lambda: measure._require_ceil_agreement(4, 3, "-1017", 5))
    checks += 1

    selected_middle = _fixture()
    selected_middle["rows"][0]["selectedAttempt"] = 2
    selected_middle["rows"][0]["statisticsCanonical"] = selected_middle["rows"][0]["statisticsCanonical"].replace(
        "selectedAttempt=0", "selectedAttempt=2", 1
    )
    _expect(_candidate(measure.measure_rows(selected_middle), 4)["selectedAttempt"] == 2,
            "selected attempt may be any ranked routed member")
    _expect_rejected(
        "actual arithmetic rounding disagreement",
        lambda: measure._measure_candidate("canary", 54, 0, 5, 25),
    )
    checks += 1

    # Strict grammar, root identity, typed values, ranges, ranking consistency, and provenance fail closed.
    malformed = _fixture()
    malformed["rows"][0]["statisticsCanonical"] = malformed["rows"][0]["statisticsCanonical"].replace(
        "placementScores=[", "placementScores=\n[", 1
    )
    _expect_rejected("malformed score grammar", lambda: measure.measure_rows(malformed))
    checks += 1

    missing = _fixture()
    missing["rows"][0]["statisticsCanonical"] = missing["rows"][0]["statisticsCanonical"].replace(
        ", 5{total=1005;electrical=105;region=25;connector=35;corridor=45;outline=505;courtyard=205;demand=1000000}", ""
    )
    _expect_rejected("missing candidate", lambda: measure.measure_rows(missing))
    duplicate = _fixture()
    duplicate["rows"][0]["statisticsCanonical"] = duplicate["rows"][0]["statisticsCanonical"].replace(
        ", 5{total=1005;", ", 4{total=1005;"
    )
    _expect_rejected("duplicate candidate", lambda: measure.measure_rows(duplicate))
    trailing = _fixture()
    trailing["rows"][0]["statisticsCanonical"] = trailing["rows"][0]["statisticsCanonical"].replace(
        "];selectedScore=", "]garbage;selectedScore="
    )
    _expect_rejected("trailing score garbage", lambda: measure.measure_rows(trailing))
    missing_selected = _fixture()
    missing_selected["rows"][0]["statisticsCanonical"] = missing_selected["rows"][0]["statisticsCanonical"].replace(
        "selectedAttempt=0;", "", 1
    )
    _expect_rejected("missing canonical selectedAttempt", lambda: measure.measure_rows(missing_selected))
    missing_ranked = _fixture()
    missing_ranked["rows"][0]["statisticsCanonical"] = missing_ranked["rows"][0]["statisticsCanonical"].replace(
        "ranked=[0, 1, 2, 3, 4, 5];", "", 1
    )
    _expect_rejected("missing canonical ranking", lambda: measure.measure_rows(missing_ranked))
    checks += 1

    for label, edit in (
        ("missing root", lambda data: data["rows"].pop()),
        ("duplicate root", lambda data: data["rows"].__setitem__(13, copy.deepcopy(data["rows"][0]))),
        ("foreign root", lambda data: data["rows"][0].update(seed="999")),
    ):
        invalid = _fixture()
        edit(invalid)
        _expect_rejected(label, lambda invalid=invalid: measure.measure_rows(invalid))
        checks += 1

    for label, field, value in (
        ("boolean version", "version", True),
        ("boolean parts", "parts", True),
        ("boolean selectedAttempt", "selectedAttempt", False),
        ("wrong version", "version", 14),
        ("below medium part minimum", "parts", 19),
        ("out-of-range parts", "parts", 129),
    ):
        invalid = _fixture()
        invalid["rows"][0][field] = value
        _expect_rejected(label, lambda invalid=invalid: measure.measure_rows(invalid))
        checks += 1

    for label, demand in (("zero demand", 0), ("demand above domain", measure.MAX_DEMAND + 1), ("float grammar", "1.5")):
        invalid = _set_demand(_fixture(), 0, 4, demand)
        _expect_rejected(label, lambda invalid=invalid: measure.measure_rows(invalid))
        checks += 1

    inconsistent = _fixture()
    inconsistent["rows"][0]["selectedAttempt"] = 1
    _expect_rejected("selectedAttempt disagreement", lambda: measure.measure_rows(inconsistent))
    inconsistent_rank = _fixture()
    inconsistent_rank["rows"][0]["statisticsCanonical"] = inconsistent_rank["rows"][0]["statisticsCanonical"].replace(
        "ranked=[0, 1, 2, 3, 4, 5]", "ranked=[1, 0, 2, 3, 4, 5]"
    )
    _expect_rejected("ranked order disagreement", lambda: measure.measure_rows(inconsistent_rank))
    inconsistent_json_rank = _fixture()
    inconsistent_json_rank["rows"][0]["rankedPlacementAttempts"] = [1, 0, 2, 3, 4, 5]
    _expect_rejected("JSON ranking disagreement", lambda: measure.measure_rows(inconsistent_json_rank))
    checks += 1

    _expect_rejected(
        "source pin mismatch",
        lambda: measure._verify_source_bindings(
            measure.REPO_ROOT,
            {**{path: "0" * 64 for path in measure.SOURCE_PATHS}},
            {path: "1" * 64 for path in measure.SOURCE_PATHS},
        ),
    )
    checks += 1

    sealed_bytes = b"unchanged sealed nonpinned owner"
    sealed_digest = measure._sha256(sealed_bytes)
    _expect(
        measure._verify_sealed_files(
            measure.REPO_ROOT,
            {"canary/nonpinned-owner.txt": sealed_digest},
            reader=lambda root, path: sealed_bytes,
        ) == 1,
        "nonpinned sealed owner hash",
    )
    _expect_rejected(
        "changed nonpinned sealed owner",
        lambda: measure._verify_sealed_files(
            measure.REPO_ROOT,
            {"canary/nonpinned-owner.txt": sealed_digest},
            reader=lambda root, path: b"changed sealed owner",
        ),
    )
    checks += 1

    valid_bytes = b"source-bound-canary"
    valid_hash = measure._sha256(valid_bytes)
    _expect(measure._require_sha256(valid_bytes, valid_hash, "canary") == valid_hash, "valid data hash")
    _expect_rejected("data hash mismatch", lambda: measure._require_sha256(valid_bytes, "0" * 64, "canary"))
    checks += 1

    _expect_rejected("duplicate JSON keys", lambda: measure._parse_json_bytes(b'{"a":1,"a":2}', "canary"))
    _expect_rejected("unsafe root path", lambda: measure._safe_repo_path(measure.REPO_ROOT, "../outside"))
    checks += 1

    return checks


def main():
    checks = run_canaries()
    baseline, provenance = measure.audit_repository(measure.REPO_ROOT)
    if (baseline.get("status") != "PINNED" or provenance["nativeRows"]["version15Rows"] != 14 or
            provenance["mainInputSeal"]["checkedFiles"] != measure.MAIN_SEAL_FILE_COUNT):
        raise AssertionError("pinned repository provenance audit returned an unexpected result")
    print(
        f"PASS measure.py canaryGroups={checks} sourceAudit=PASS sealFiles={measure.MAIN_SEAL_FILE_COUNT} "
        "nativeVersion15Rows=14; no census file written"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, ValueError) as exc:
        print(f"FAIL check_measure.py: {exc}", file=sys.stderr)
        raise SystemExit(2)
