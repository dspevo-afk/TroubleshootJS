"""Independent complete-population check for the compiled Q30 service receipt."""
import itertools
import json
import math
import re
import sys

SEEDS = {"0", "37"}
FAULTS = {"DREV_OPEN", "REN_OPEN", "SENSOR_A_OPEN", "DRIVE_A_OPEN", "RELAY_B_COIL_OPEN"}
EXPECTED_IDS = [f"SENSORS_{state}_{point}"
                for state in ("LOW", "A_ONLY", "B_ONLY", "HIGH")
                for point in ("DREV_K", "REN_2", "RAIL5", "RSA_2", "RDA_1",
                              "RDA_2", "KB_A2", "OUTPUT_A", "OUTPUT_B")] + ["MAIN_12V"]


def check(path):
    data = json.load(open(path, encoding="utf-8"))
    rows = data["rows"]
    expected = set(itertools.product(SEEDS, FAULTS))
    assert len(rows) == len(expected), "missing or extra hypothesis attempts"
    actual = set()
    observations = {}
    schema = None
    build_identity = None
    for row in rows:
        key = (row["seed"], row["fault"])
        assert key in expected and key not in actual, "foreign/duplicate hypothesis"
        actual.add(key)
        bench, service = row["workbench"], row["service"]
        identity = (row["previewSourceDigest"], row["previewWebDigest"])
        assert all(re.fullmatch(r"[0-9a-f]{64}", part) for part in identity)
        if build_identity is None:
            build_identity = identity
        assert identity == build_identity, "mixed preview builds"
        assert bench["status"] == service["status"] == "PASS", key
        assert bench["schema"] == 1 and bench["family"] == "RB30_CONTROL"
        assert bench["visualPass"] is True and bench["prototypeRetained"] is True
        assert bench["solverAdaptive"] is True
        assert bench["solverMaxTimeStep"] == 5e-6 and bench["solverMinTimeStep"] == 50e-12
        assert bench["ownerRestored"] is False
        assert bench["route"] == bench["selectedRoutePolicy"] == "P07_FULLER_TWO_LAYER"
        assert all(bench[field] == 82 for field in
                   ("topInspectable", "topTargets", "bottomInspectable", "bottomTargets"))
        assert bench["normalAdmission"] is False and service["normalAdmission"] is False
        assert service["d01Admission"] is False
        assert bench["hypothesisCount"] == service["hypotheses"] == 5
        assert bench["seed"] == service["seed"] == row["seed"]
        assert bench["selectedFault"] == service["fault"] == row["fault"]
        assert bench["packages"] == 33 and bench["pads"] == 82
        assert bench["physicalPolicy"] == "MEDIUM_BOARD@1"
        assert service["unrepairedRetest"] is False and service["originalRetest"] is False
        assert service["repairedRetest"] is True
        assert service["leadService"] == ("NOT_SUPPORTED" if row["fault"] == "RELAY_B_COIL_OPEN" else "PASS")
        samples = service["samples"]
        ids = [sample["id"] for sample in samples]
        assert ids and len(ids) == len(set(ids)), "empty/duplicate measurements"
        assert ids == EXPECTED_IDS, "measurement census differs from independent recipe"
        if schema is None:
            schema = ids
        assert ids == schema, "hypothesis-dependent observation recipe"
        for sample in samples:
            assert type(sample["value"]) in (int, float) and math.isfinite(sample["value"])
            assert type(sample["tolerance"]) in (int, float) and math.isfinite(sample["tolerance"])
            expected_tolerance = max(0.01, abs(sample["value"]) * 0.02)
            assert math.isclose(sample["tolerance"], expected_tolerance, rel_tol=1e-12, abs_tol=1e-12), "DC tolerance differs from independent expectation"
        measured = {sample["id"]: sample["value"] for sample in samples}
        assert len(measured) == 37
        assert 11.4 <= measured["MAIN_12V"] <= 12.6
        for state, input_a, input_b in [("LOW", False, False), ("A_ONLY", True, False),
                                       ("B_ONLY", False, True), ("HIGH", True, True)]:
            expected_a = input_a and row["fault"] == "RELAY_B_COIL_OPEN"
            expected_b = input_b and row["fault"] in {"SENSOR_A_OPEN", "DRIVE_A_OPEN"}
            for channel, on in [("A", expected_a), ("B", expected_b)]:
                value = measured[f"SENSORS_{state}_OUTPUT_{channel}"]
                assert (10.8 <= value <= 12.6) if on else (abs(value) <= 0.05), (key, state, channel, value)
        observations[key] = samples
    assert actual == expected
    comparisons = 0
    for seed in sorted(SEEDS):
        for first, second in itertools.combinations(sorted(FAULTS), 2):
            a, b = observations[(seed, first)], observations[(seed, second)]
            assert any(abs(x["value"] - y["value"]) > x["tolerance"] + y["tolerance"]
                       for x, y in zip(a, b)), (seed, first, second, "indistinguishable")
            comparisons += 1
    print(f"PASS: compiled Q30 complete population rows={len(rows)} samplesPerRow={len(schema)} distinctPairs={comparisons}; normal/D01 admission=false")


if __name__ == "__main__":
    check(sys.argv[1])
