"""Audit this source-only decision against the published TO92 input inventory."""
import hashlib
import json
import subprocess
import sys
from pathlib import Path

if not __debug__:
    raise RuntimeError("Assertion-enabled Python required")
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
PRIOR = HERE.parent / "to92-molded-detail-r2"


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def audit():
    review = read(HERE / "source-review.json")
    baseline = read(PRIOR / "baseline-inputs.json")
    candidate = read(PRIOR / "candidate-inputs.json")
    player = read(PRIOR / "player-inputs.json")
    expected = dict(baseline["files"], **candidate["changedSources"])
    assert len(expected) == 1482
    assert {n: sha(ROOT / n) for n in expected} == expected
    digest = hashlib.sha256(json.dumps(expected, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    assert digest == candidate["allCompilerInputsDigest"]
    java = {p.relative_to(ROOT).as_posix() for folder in ("src", "tests/contracts")
            for p in (ROOT / folder).rglob("*.java")}
    assert java == {n for n in expected if n.endswith(".java") and n.startswith(("src/", "tests/contracts/"))}
    assert len(baseline["machinery"]) == 24
    assert {n: sha(ROOT / n) for n in baseline["machinery"]} == baseline["machinery"]
    for seal, archive in ((candidate, "candidate-source"), (player, "player-source")):
        assert {n: sha(PRIOR / n) for n in seal["gates"]} == seal["gates"]
        assert {n: sha(PRIOR / archive / n) for n in seal["gates"]} == seal["gates"]
    assert sha(PRIOR / "candidate-inputs.json") == player["candidateSealSha256"]
    for phase, expected_hash in player["acceptedFinalGates"].items():
        path = PRIOR / (phase + "-assessment.json")
        assert sha(path) == expected_hash and read(path)["status"] == "PASS"
    for name, expected_hash in review["consumedSources"].items():
        assert sha(ROOT / "src/com/lushprojects/circuitjs1/client" / name) == expected_hash, name
    publication = read(PRIOR / "publication.json")
    assert publication["sha"] == review["baseCommit"] and publication["pushActualExecExitCode"] == 0
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT).decode().strip()
    assert subprocess.run(["git", "merge-base", "--is-ancestor", review["baseCommit"], head], cwd=ROOT).returncode == 0
    assert not subprocess.check_output(["git", "diff", "--name-only", "HEAD", "--", "src", "scripts", "tests", "war"], cwd=ROOT).strip()
    return {"status": "PASS_UNCHANGED_INPUT_AUDIT", "observedHead": head,
            "sourceReviewSha256": sha(HERE / "source-review.json"),
            "auditHelperSha256": sha(Path(__file__)), "compilerInputs": len(expected),
            "allCompilerInputsDigest": digest, "machineryInputs": 24,
            "gateHelpers": len(candidate["gates"]), "playerHelpers": len(player["gates"]),
            "reviewedSourceHashes": len(review["consumedSources"]),
            "priorPublicationSha256": sha(PRIOR / "publication.json"),
            "priorPostpublicationExecutionSha256": sha(PRIOR / "postpublication-execution.json"),
            "limit": "Input identity audit only; no native/build/browser rerun or profile cleanup"}


if __name__ == "__main__":
    result = audit()
    assert sys.argv[1:] in ([], ["--write"])
    if sys.argv[1:]:
        with (HERE / "input-audit.json").open("x", encoding="utf-8", newline="\n") as stream:
            json.dump(result, stream, indent=2)
            stream.write("\n")
    print(json.dumps(result, indent=2))
