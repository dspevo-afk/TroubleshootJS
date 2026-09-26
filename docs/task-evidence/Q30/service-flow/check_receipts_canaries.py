"""Qualify the independent reader using one real passing compiled census."""
import copy
import json
from pathlib import Path
import sys
import tempfile

from check_receipts import check


def main(path):
    check(path)
    source = json.loads(Path(path).read_text(encoding="utf-8"))

    def reject(label, mutate):
        changed = copy.deepcopy(source)
        mutate(changed)
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", suffix=".json",
                                         prefix="q30-reader-canary-", delete=False) as file:
            json.dump(changed, file)
            scratch = Path(file.name)
        try:
            try:
                check(scratch)
            except AssertionError:
                print(f"PASS: reader rejects {label}")
            else:
                raise RuntimeError(f"Reader accepted invalid {label}")
        finally:
            scratch.unlink()

    reject("missing case", lambda d: d["rows"].pop())
    reject("duplicate case", lambda d: d["rows"].__setitem__(-1, copy.deepcopy(d["rows"][0])))
    reject("boolean measurement", lambda d: d["rows"][0]["service"]["samples"][0].update(value=True))
    reject("understated tolerance", lambda d: d["rows"][0]["service"]["samples"][0].update(tolerance=0))
    reject("mixed build", lambda d: d["rows"][0].update(previewWebDigest="0" * 64))

    def wrong_output(data):
        row = next(r for r in data["rows"] if r["fault"] == "DREV_OPEN")
        sample = next(s for s in row["service"]["samples"] if s["id"] == "SENSORS_LOW_OUTPUT_A")
        sample.update(value=12, tolerance=.24)
    reject("wrong output truth", wrong_output)

    def indistinguishable(data):
        diode = next(r for r in data["rows"] if r["seed"] == "37" and r["fault"] == "DREV_OPEN")
        enable = next(r for r in data["rows"] if r["seed"] == "37" and r["fault"] == "REN_OPEN")
        enable["service"]["samples"] = copy.deepcopy(diode["service"]["samples"])
    reject("indistinguishable hypotheses", indistinguishable)
    print("PASS: seven negative reader canaries; exact task-owned scratch files removed")


if __name__ == "__main__":
    main(sys.argv[1])
