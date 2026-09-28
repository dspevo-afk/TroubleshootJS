from pathlib import Path
import hashlib, json

PREP = Path(__file__).resolve().parent
SCRATCH = PREP.parent.parent
R9_RUNNER = SCRATCH / "run_d01_r9_native_gwt_gates.py"
R10_RUNNER = PREP / "run_d01_r10_native_gwt_gates.py"
R10_AUDIT = PREP / "source-audit.json"
R10_SOURCE_PATH = PREP / "source-path.txt"
R9_RUNNER_SHA256 = "557f1d859bfb567fe041c5fc1f8eacf31d7cc1f5de66df77964a6ffb0dbc3cf7"
R10_AUDIT_SHA256 = "3e802932fd1df9177371b0e3e2ae211b9837fddda66039d36847be5ad298c4c3"
EXPECTED_OVERLAYS = {
    "src/com/lushprojects/circuitjs1/client/Q30DiagnosticAdmissionVerifier.java":
        "7180d16178a712b1a0fc35a9be70e2b693d1442a802b1c2e1be99b78233efe60",
    "src/com/lushprojects/circuitjs1/client/Q30D01HostBoundarySnapshot.java":
        "f7cded8104787967e36602872775e2ab7a0d6d316b15adbf8b7811881d257178",
}


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def main():
    if R10_RUNNER.exists():
        raise RuntimeError("refusing to overwrite r10 gate runner")
    source = R9_RUNNER.read_bytes()
    if sha(source) != R9_RUNNER_SHA256:
        raise RuntimeError("pinned r9 gate runner changed")
    audit_raw = R10_AUDIT.read_bytes()
    if sha(audit_raw) != R10_AUDIT_SHA256:
        raise RuntimeError("prepared r10 source audit changed")
    audit = json.loads(audit_raw.decode("utf-8"))
    if audit.get("finalInputCount") != 1340 or len(audit.get("inputs", [])) != 1340:
        raise RuntimeError("r10 audit input count is not exactly 1340")
    if audit.get("r10OverlayCount") != 2:
        raise RuntimeError("r10 audit does not declare exactly two overlays")
    if sorted(audit.get("changedFromR9", [])) != sorted(EXPECTED_OVERLAYS):
        raise RuntimeError("r10 audit changed paths differ from the frozen pair")
    observed = {row["path"]: row["r10Sha256"] for row in audit.get("r10Overlays", [])}
    if observed != EXPECTED_OVERLAYS:
        raise RuntimeError("r10 overlay hashes differ from the frozen pair")
    if audit.get("warFileCount") != 29 or audit.get("pinnedJarCount") != 9:
        raise RuntimeError("r10 audit web/JAR counts differ")
    fixture = Path(R10_SOURCE_PATH.read_text(encoding="utf-8").strip()).resolve()
    if fixture != (PREP / "fixture").resolve():
        raise RuntimeError("source-path pointer does not identify the prepared r10 fixture")

    for helper in ("native-d01-r5.ps1", "build-run.ps1"):
        if not (SCRATCH / helper).is_file():
            raise RuntimeError("resolved task-root gate helper missing: " + helper)
    text = source.decode("utf-8")
    replacements = [
        ("prep=Path('$TASKROOT/scratch/d01-r9-preparation')",
         "prep=Path('$TASKROOT/scratch/d01-r10-preparation')"),
        ("'9df9e5c7ae41ec093469be2b5c576c6fab5940d65738715e0a2fdf52064d0adf'",
         "'3e802932fd1df9177371b0e3e2ae211b9837fddda66039d36847be5ad298c4c3'"),
        ("d01-r9-native-gwt-gates.json", "d01-r10-native-gwt-gates.json"),
        ('s=Path(__file__).resolve().parent', 's=Path(__file__).resolve().parents[2]'),
        ("native-d01-r9", "native-d01-r10"),
        ("d01-r9-gwt", "d01-r10-gwt"),
        ("assert len(audit['inputs'])==1340\n",
         "assert len(audit['inputs'])==1340\n"
         "assert audit['r10OverlayCount']==2\n"
         "assert sorted(audit['changedFromR9'])==sorted(["
         "'src/com/lushprojects/circuitjs1/client/Q30DiagnosticAdmissionVerifier.java',"
         "'src/com/lushprojects/circuitjs1/client/Q30D01HostBoundarySnapshot.java'])\n"
         "assert {x['path']:x['r10Sha256'] for x in audit['r10Overlays']}=={"
         "'src/com/lushprojects/circuitjs1/client/Q30DiagnosticAdmissionVerifier.java':"
         "'7180d16178a712b1a0fc35a9be70e2b693d1442a802b1c2e1be99b78233efe60',"
         "'src/com/lushprojects/circuitjs1/client/Q30D01HostBoundarySnapshot.java':"
         "'f7cded8104787967e36602872775e2ab7a0d6d316b15adbf8b7811881d257178'}\n"),
    ]
    for old, new in replacements:
        if old.startswith("assert len(audit['inputs'])"):
            old = old.replace("\n", "\r\n")
            new = new.replace("\n", "\r\n")
        expected_count = 2 if old in ("native-d01-r9", "d01-r9-gwt") else 1
        if text.count(old) != expected_count:
            raise RuntimeError("unexpected source-chain replacement count: " + old[:80])
        text = text.replace(old, new)
    if "d01-r9-preparation" in text or "d01-r9-native" in text or "d01-r9-gwt" in text:
        raise RuntimeError("stale r9 preparation path or gate label remains")
    compile(text, str(R10_RUNNER), "exec")
    R10_RUNNER.write_text(text, encoding="utf-8", newline="\n")
    print(json.dumps({"status": "PREPARED_NOT_RUN", "runner": str(R10_RUNNER),
                      "runnerSha256": sha(R10_RUNNER.read_bytes()),
                      "sourceTemplateSha256": R9_RUNNER_SHA256,
                      "sourceAuditSha256": R10_AUDIT_SHA256,
                      "nativeLabel": "native-d01-r10", "gwtLabel": "d01-r10-gwt",
                      "invocation": "python \"" + str(R10_RUNNER) + "\""}, sort_keys=True))


if __name__ == "__main__":
    main()
