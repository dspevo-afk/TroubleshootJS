"""Exercise the exact terminal-output correction against retained native evidence."""
import hashlib
import importlib.util
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("final_reader", HERE / "check_final.py")
reader = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reader)
manifest = reader.check_manifest()
original_read = reader.read_text
cases = []


def run(name, artifact=None, transform=None):
    def mutated(path):
        text = original_read(path)
        return transform(text) if path.name == artifact else text
    reader.read_text = mutated
    rejected = False
    try:
        reader.check_contracts(manifest)
    except reader.ValidationError:
        rejected = True
    finally:
        reader.read_text = original_read
    assert rejected == (artifact is not None), name
    cases.append({"name": name, "expected": "REJECT" if artifact else "PASS",
                  "result": "PASS"})


summary = "PASS: focused current contracts; 15 Java suites. Full matrix and independent oracles NOT RUN."
cleanup = "CLEANUP: current JVM contract classes and task-owned scratch removed."
run("unchanged actual fifteen-suite log and child receipt")
run("missing full-log summary", "contracts-log.txt", lambda s: s.replace(summary, ""))
run("wrong full-log suite count", "contracts-log.txt", lambda s: s.replace(summary, summary.replace("15", "14")))
run("duplicated full-log summary", "contracts-log.txt", lambda s: s + summary + "\n")
run("missing terminal cleanup", "contracts-log.txt", lambda s: s.replace(cleanup, ""))
run("child marker absent from log", "contracts-log.txt", lambda s: s.replace("PASS: A10 dependency contracts assertions=15", ""))
run("missing child suite marker", "contracts-receipt.txt", lambda s: s.replace("PASS: A10 dependency contracts assertions=15", ""))
run("changed candidate audit", "contracts-input-audit-after.txt", lambda s: s.replace("deltas=39", "deltas=38"))

before = json.loads((HERE / "reader-review-1/failure.json").read_text(encoding="utf-8"))
for section in ("nativeArtifacts", "unchangedNativeConsumedOwners"):
    for name, expected in before[section].items():
        assert hashlib.sha256((HERE / name).read_bytes()).hexdigest() == expected, name
assert hashlib.sha256((HERE.parent / "candidate-inputs.json").read_bytes()).hexdigest() == before["candidateInputSealSha256"]
result = {"status": "PASS", "cases": cases, "caseCount": len(cases),
          "positive": 1, "negative": 7, "nativeEvidenceReused": "Exact bytes and consumed owners unchanged",
          "readerSha256": hashlib.sha256((HERE / "check_final.py").read_bytes()).hexdigest(),
          "originalRejection": "reader-review-1/failure.json"}
target = HERE / "reader-qualification.json"
assert not target.exists(), "Refusing to overwrite qualification"
target.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
print("PASS final contracts reader qualification cases=8 negative=7; actual native bytes unchanged")
