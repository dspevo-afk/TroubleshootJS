from pathlib import Path
s=Path(__file__).resolve().parent
t=(s/'run_owned_zero_row_sequence.py').read_text(encoding='utf-8')
t=t.replace('owned-zero-row','sticky-finite-r2')
t=t.replace('sticky-finite-r2-r2-source-pair-path.txt','sticky-finite-r2-source-pair-path.txt')
t=t.replace('sticky-finite-r2-gates-r2.json','sticky-finite-r2-gates.json')
t=t.replace('a95259c25ac3845d784f45fc78a4137f88426dd5bf0dd42aedd5c5041795e553','727f2e9f4a63a74b0846ac242c1a2cd34f40f9f3b8bf8aed2b7f6cbb6e3ab0a9')
t=t.replace('e285675d6f8f689cd7e73d09543f6cd37de8987f','57929125daf6322006fe7da871b2edc7cca9590d')
t=t.replace('f836bfc58bdfb87d67f25184ebcd51e60d9eb820d6cc5acb966bcde98a64296b','deb593ff2471904a72690cf0332a53bc968a4c93f6f2ac28e3140fa59dc1e242')
old='CANDIDATE_CIRSIM_SHA256 = "deb593ff2471904a72690cf0332a53bc968a4c93f6f2ac28e3140fa59dc1e242"'
assert old in t
t=t.replace(old,'CANDIDATE_CIRSIM_SHA256 = "2658e94560fd9e1f0e9157a54be773bd9568d9350fa6ce56c39acaa7cf87e0b1"')
t=t.replace('"Accept only a valid attributable intermediate improvement."','"Accept only valid attributable intermediate gain"')
start=t.index('def load_fixture():')
end=t.index('\n\ndef validate_gate_receipt():',start)
t=t[:start]+'''def load_fixture():
    fixture = Path(PAIR_POINTER.read_text(encoding="utf-8").strip()).resolve()
    require(fixture.parent == SCRATCH.resolve() and fixture.name.startswith("trial-sticky-finite-r2-"), "foreign fixture")
    raw = (fixture / "source-audit.json").read_bytes()
    require(sha256(raw) == "80110e21c0d5f4baf31460d3e0a12e12d9fc5cdecc1e83d49319fbc226a3c82a", "source audit changed")
    audit = json.loads(raw)
    cir = "src/com/lushprojects/circuitjs1/client/CirSim.java"
    checks = "src/com/lushprojects/circuitjs1/client/LuFactorizationChecks.java"
    require(audit["status"] == "SOURCE_ONLY_PAIR_PREPARED_NOT_VALIDATED" and audit["head"] == EXPECTED_HEAD and audit["generatedWarCopied"] is False, "wrong source fixture")
    require(audit["differences"] == [cir] and audit["sharedTestChanges"] == [checks] and len(audit["rootInputs"]) == 1339, "incorrect change or input scope")
    for arm, cir_digest in (("control", BASELINE_CIRSIM_SHA256), ("candidate", CANDIDATE_CIRSIM_SHA256)):
        require(sha256((fixture / arm / cir).read_bytes()) == cir_digest, "CirSim changed")
        require(sha256((fixture / arm / checks).read_bytes()) == "d558d7d10706cb1da22d20f795ece6448e714be5d16315ad51a125c70b429c6c", "shared independent checks changed")
        require(len(audit["armInputs"][arm]) == 1339, "missing source census")
        for entry in audit["armInputs"][arm]:
            data = (fixture / arm / entry["path"]).read_bytes()
            require(sha256(data) == entry["sha256"] and len(data) == entry["bytes"], "source changed: " + arm + "/" + entry["path"])
    return fixture, audit, sha256(raw)
'''+t[end:]
t=t.replace('    return receipt, sha256(raw)','''    require(receipt.get("sourceAuditSha256") == "80110e21c0d5f4baf31460d3e0a12e12d9fc5cdecc1e83d49319fbc226a3c82a" and receipt.get("planSha256") == PLAN_SHA256, "gate binding mismatch")
    return receipt, sha256(raw)''')
t=t.replace('            label, arm, seed = row["label"], row["arm"], row["seed"]','            load_fixture()\n            label, arm, seed = row["label"], row["arm"], row["seed"]')
t=t.replace('            summary_path = SCRATCH / (label + "-summary.json")','            load_fixture()\n            summary_path = SCRATCH / (label + "-summary.json")')
target=s/'run_sticky_finite_r2_sequence.py'
assert not target.exists()
target.write_text(t,encoding='utf-8',newline='\n')
print(target)

