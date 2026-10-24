"""Check the finite native readiness receipt; propose no release profile."""
import argparse
import hashlib
import json
import math
from pathlib import Path

SEEDS = ["-1017", "-1067", "-1022"]
PREFIX = "RB56_RDA_OFF_READINESS "
SUMMARY = "RB56_RDA_READINESS_SWEEP "
FLAGS = ("poweredHigh", "sourcesOff", "freshBoundaryReceipt", "meterCleanup",
         "candidateDisposed", "clean")
FIELDS = {"seed", "faultOrdinal", "faultClass", "firstReadySeconds",
          "verifiedWholeBoundarySeconds", "readingOhms", "testCurrentAmps",
          "phase", "elapsedSeconds", "status", *FLAGS}


def need(value, message):
    if not value:
        raise ValueError(message)


def pairs(items):
    result = {}
    for key, value in items:
        need(key not in result, "duplicate JSON key")
        result[key] = value
    return result


def decode(value):
    return json.loads(value, object_pairs_hook=pairs,
                      parse_constant=lambda _: (_ for _ in ()).throw(ValueError("nonfinite JSON")))


def numeric(value):
    return type(value) in (int, float) and math.isfinite(value)


def reduce_rows(rows, summary):
    need(len(rows) == 15, "all fifteen seed/fault cases required")
    for index, row in enumerate(rows):
        need(type(row) is dict and set(row) == FIELDS, "unexpected readiness row schema")
        need(row["seed"] == SEEDS[index // 5] and type(row["seed"]) is str,
             "exact signed-long seed order differs")
        need(type(row["faultOrdinal"]) is int and row["faultOrdinal"] == index % 5,
             "fault ordinal/order differs")
        need(row["faultClass"] == ("RESISTOR_VALUE" if index % 5 < 4 else "RELAY_COIL"),
             "fault class differs")
        need(row["status"] == "PASS" and row["phase"] == "PASS", "native case did not pass")
        need(all(row[key] is True for key in FLAGS), "readiness or cleanup proof failed")
        first, boundary = row["firstReadySeconds"], row["verifiedWholeBoundarySeconds"]
        need(numeric(first) and 0 < first <= 3 and
             abs(first * 10 - round(first * 10)) < 1e-8, "invalid natural readiness sample")
        need(type(boundary) is int and boundary in (1, 2, 3) and
             boundary == min(3, math.floor(first) + 1), "unverified whole boundary")
        need(numeric(row["readingOhms"]) and 0 <= row["readingOhms"] <= 10_000_000,
             "real resistance reading unavailable")
        need(numeric(row["testCurrentAmps"]) and row["testCurrentAmps"] > 0,
             "no actual meter current")
        need(numeric(row["elapsedSeconds"]) and row["elapsedSeconds"] > 0,
             "case elapsed time missing")
    maximum = max(row["verifiedWholeBoundarySeconds"] for row in rows)
    need(summary == {"cases": 15, "seeds": 3, "faultOrdinalsPerSeed": 5,
                     "maximumVerifiedBoundarySeconds": maximum,
                     "naturalStepSeconds": 0.1, "maximumNaturalWaitSeconds": 3},
         "aggregate disagrees with individual cases")
    return {"schema": "tsj-rb56-native-readiness-assessment/1",
            "status": "PASS_NATIVE_READINESS_BOUNDARY_ONLY", "cases": rows,
            "proposedFixedWaitSeconds": maximum, "shorterCandidateEstablished": maximum < 3,
            "scope": "Fixed-step direct native CircuitJS model; no cold routing or adaptive compiled proof",
            "profileAssigned": False, "publicAdmissionGranted": False}


def self_test():
    rows = []
    for seed in SEEDS:
        for ordinal in range(5):
            rows.append({"seed": seed, "faultOrdinal": ordinal,
                         "faultClass": "RESISTOR_VALUE" if ordinal < 4 else "RELAY_COIL",
                         "firstReadySeconds": 0.3, "verifiedWholeBoundarySeconds": 1,
                         "readingOhms": 990.1, "testCurrentAmps": 0.0005,
                         "phase": "PASS", "status": "PASS", "elapsedSeconds": 1.0,
                         **{flag: True for flag in FLAGS}})
    summary = {"cases": 15, "seeds": 3, "faultOrdinalsPerSeed": 5,
               "maximumVerifiedBoundarySeconds": 1,
               "naturalStepSeconds": 0.1, "maximumNaturalWaitSeconds": 3}
    need(reduce_rows(rows, summary)["proposedFixedWaitSeconds"] == 1, "positive canary")
    mutations = (("seed", "-1022"), ("clean", False), ("firstReadySeconds", 3.1),
                 ("verifiedWholeBoundarySeconds", 2), ("readingOhms", float("nan")),
                 ("phase", "NATURAL_READINESS_SWEEP"))
    for key, value in mutations:
        bad = [dict(row) for row in rows]
        bad[0][key] = value
        try:
            reduce_rows(bad, summary)
        except ValueError:
            continue
        raise AssertionError("negative canary accepted: " + key)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--receipt", type=Path)
    parser.add_argument("--gate", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        print("PASS: native readiness reader self-test; six negative canaries")
        return
    need(args.receipt and args.gate and args.output, "receipt/gate/output required")
    gate_bytes = args.gate.read_bytes()
    gate = decode(gate_bytes.decode("utf-8"))
    need(gate.get("actualExitCode") == 0 and gate.get("inputsUnchanged") is True,
         "native gate failed or inputs changed")
    receipt = args.receipt.read_bytes()
    need(len(receipt) < 16 * 1024 * 1024, "receipt too large")
    lines = receipt.decode("utf-8").splitlines()
    rows = [decode(line[len(PREFIX):]) for line in lines if line.startswith(PREFIX)]
    summaries = [decode(line[len(SUMMARY):]) for line in lines if line.startswith(SUMMARY)]
    need(len(summaries) == 1 and
         any(line.startswith("PASS: RB56 instrument admission contracts ") for line in lines),
         "final native suite marker/aggregate missing")
    result = reduce_rows(rows, summaries[0])
    result["receiptSha256"] = hashlib.sha256(receipt).hexdigest()
    result["gateSha256"] = hashlib.sha256(gate_bytes).hexdigest()
    result["readerSha256"] = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("PASS: native readiness boundary only; proposed fixed wait", result["proposedFixedWaitSeconds"])


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, AssertionError) as error:
        print("FAIL: native readiness reader:", error)
        raise SystemExit(2)
