"""Audit the tested implementation plus the separately reviewed browser consumer.

The original candidate/native seals are immutable. The only later source edit is
an unexecuted Python browser wrapper, outside native compilation and src/scripts/
war execution provenance. This audit does not certify that browser wrapper.
"""
import ast
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
OVERLAY = "tests/browser/quickplay_procedural_acceptance.py"
TESTED_SEAL = "7edd32d8af521b19a3247715821a315cf5384292777af8875987cf8044a42e8c"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def audit():
    seal_path = HERE / "candidate-inputs.json"
    require(sha(seal_path) == TESTED_SEAL, "tested candidate seal changed")
    seal = read(seal_path)
    files = {row["path"]: row["sha256"] for row in seal["files"]}
    require(len(files) == len(seal["files"]) == 1481, "source inventory changed")
    manifest = read(HERE / "publication-inputs.json")
    require(manifest["testedCandidateSha256"] == TESTED_SEAL, "wrong tested reference")
    overlay = manifest["overlay"]
    require(overlay["path"] == OVERLAY, "only the declared browser consumer may differ")
    require(overlay["priorSha256"] == files[OVERLAY], "wrong prior consumer identity")
    require(sha(HERE / "browser-consumer-addendum/prior-helper.py") == files[OVERLAY],
            "prior consumer archive changed")
    changed = []
    for relative, expected in files.items():
        path = ROOT / relative
        require(path.is_file() and not path.is_symlink(), "missing/linked input: " + relative)
        actual = sha(path)
        if actual != expected:
            changed.append(relative)
            require(relative == OVERLAY and actual == overlay["currentSha256"],
                    "unqualified input changed: " + relative)
    require(changed == [OVERLAY], "publication must have exactly one declared overlay")
    require(len(seal["gateOwners"]) == 14, "gate inventory changed")
    for relative, expected in seal["gateOwners"].items():
        require(sha(HERE / relative) == expected, "tested gate owner changed: " + relative)
    base_seal = ROOT / "docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json"
    require(sha(base_seal) == seal["baseMainSealSha256"], "old compiler seal changed")
    base = {row["path"]: row["sha256"] for row in read(base_seal)["files"]}
    delta = {name: digest for name, digest in files.items() if base.get(name) != digest}
    archived = {p.relative_to(HERE / "candidate-source").as_posix(): sha(p)
                for p in (HERE / "candidate-source").rglob("*") if p.is_file()}
    require(len(delta) == 39 and archived == delta, "tested implementation archive changed")
    preserved = 0
    for attempt in sorted(HERE.glob("native-attempt-*")):
        for relative, expected in read(attempt / "failure.json")["files"].items():
            require(sha(attempt / relative) == expected, "prior native artifact changed")
            preserved += 1
    require(preserved == 390, "prior native archive inventory changed")
    helper = ROOT / OVERLAY
    text = helper.read_text(encoding="utf-8")
    tree = ast.parse(text)
    compile(tree, OVERLAY, "exec")
    catalog = next(ast.literal_eval(node.value) for node in tree.body
                   if isinstance(node, ast.Assign) and any(
                       isinstance(t, ast.Name) and t.id == "EXPECTED_CATALOG" for t in node.targets))
    expected = (
        ("LED_INDICATOR", "EASY"), ("DIODE_PROTECTED_INDICATOR", "EASY"),
        ("PARALLEL_DUAL_INDICATOR", "EASY"), ("RC_DELAY", "EASY"),
        ("NPN_LOW_SIDE_SWITCH", "EASY"), ("NMOS_LOW_SIDE_SWITCH", "EASY"),
        ("RELAY_OUTPUT", "EASY"), ("SENSOR_CONTROL", "EASY"),
        ("RB15_CONTROL", "EASY"), ("COMPOSED_CONTROLLED_INDICATOR", "MEDIUM"),
        ("RB30_CONTROL", "MEDIUM"))
    require(catalog == expected, "browser catalog differs from active11")
    return {"status": "PASS", "testedCandidateSha256": TESTED_SEAL,
            "sourceCount": 1481, "unchangedTestedInputs": 1480,
            "overlay": OVERLAY, "gateOwnersUnchanged": 14,
            "testedSourceDeltasArchived": 39, "priorNativeFilesUnchanged": preserved,
            "browserConsumerSyntaxAndCatalog": "PASS",
            "browserConsumerExecution": "NOT_RUN; selected Edge host unavailable",
            "scope": "Exact reuse boundary only; normal player evidence is separate"}


if __name__ == "__main__":
    print(json.dumps(audit(), indent=2))
