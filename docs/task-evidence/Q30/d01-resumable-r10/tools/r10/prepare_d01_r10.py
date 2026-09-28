from pathlib import Path, PurePosixPath
import hashlib, json, os

PREPARATION = Path(__file__).resolve().parent
SCRATCH = PREPARATION.parent.parent
R9_PREPARATION = PREPARATION.parent / "d01-r9-preparation"
R9_FIXTURE = R9_PREPARATION / "fixture"
R9_AUDIT = R9_PREPARATION / "source-audit.json"
R10_OVERLAY_DIR = PREPARATION.parent / "d01-resumable-draft" / "r10"
FIXTURE = PREPARATION / "fixture"
AUDIT_OUT = PREPARATION / "source-audit.json"
BASE_AUDIT_OUT = PREPARATION / "baseline-source-audit.json"
POINTER_OUT = PREPARATION / "source-path.txt"
EXPECTED_R9_AUDIT_SHA256 = "9df9e5c7ae41ec093469be2b5c576c6fab5940d65738715e0a2fdf52064d0adf"
OVERLAYS = {
    "src/com/lushprojects/circuitjs1/client/Q30DiagnosticAdmissionVerifier.java": {
        "sha256": "7180d16178a712b1a0fc35a9be70e2b693d1442a802b1c2e1be99b78233efe60",
        "source": "Q30DiagnosticAdmissionVerifier.java",
    },
    "src/com/lushprojects/circuitjs1/client/Q30D01HostBoundarySnapshot.java": {
        "sha256": "f7cded8104787967e36602872775e2ab7a0d6d316b15adbf8b7811881d257178",
        "source": "Q30D01HostBoundarySnapshot.java",
    },
}


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def safe_path(value):
    rel = PurePosixPath(value)
    require(value and "\\" not in value and not rel.is_absolute() and
            ".." not in rel.parts, "unsafe/noncanonical audited path: " + value)
    return rel


def index_rows(rows):
    result = {}
    for row in rows:
        path = row["path"]
        require(path not in result, "duplicate audited path: " + path)
        result[path] = dict(row)
    return result


def main():
    for path in (FIXTURE, AUDIT_OUT, BASE_AUDIT_OUT, POINTER_OUT):
        require(not path.exists(), "refusing to overwrite: " + str(path))
    raw_audit = R9_AUDIT.read_bytes()
    require(sha(raw_audit) == EXPECTED_R9_AUDIT_SHA256,
            "pinned r9 source audit hash changed")
    base = json.loads(raw_audit.decode("utf-8"))
    require(base.get("status") == "SOURCE_ONLY_FIXTURE_PREPARED_NOT_VALIDATED" and
            base.get("finalInputCount") == 1340 and
            base.get("baselineInputCount") == 1340 and
            base.get("warFileCount") == 29 and
            base.get("pinnedJarCount") == 9 and
            base.get("generatedWarCopied") is False and
            base.get("excludedGeneratedOrRuntimeFiles") is True,
            "pinned r9 audit scope/counts differ")
    rows = index_rows(base["inputs"])
    require(len(rows) == 1340, "r9 audit must declare exactly 1340 unique inputs")
    require(not any(".git" in safe_path(path).parts for path in rows),
            "git metadata must not appear in the audited fixture")

    # Preflight every audited file and both overlays before creating output.
    for path, row in rows.items():
        rel = safe_path(path)
        source = R9_FIXTURE.joinpath(*rel.parts)
        require(source.is_file() and not source.is_symlink(),
                "audited r9 input missing or linked: " + path)
        data = source.read_bytes()
        require(len(data) == row["bytes"] and sha(data) == row["sha256"],
                "r9 baseline input changed: " + path)
    overlay_data = {}
    for path, spec in OVERLAYS.items():
        require(path in rows, "r10 overlay path absent from r9 audited source set: " + path)
        source = R10_OVERLAY_DIR / spec["source"]
        data = source.read_bytes()
        require(sha(data) == spec["sha256"], "r10 overlay differs from frozen hash: " + path)
        require(sha((R9_FIXTURE / Path(*safe_path(path).parts)).read_bytes()) != spec["sha256"],
                "r10 overlay unexpectedly matches r9 baseline: " + path)
        overlay_data[path] = data

    FIXTURE.mkdir()
    for path, row in rows.items():
        rel = safe_path(path)
        data = R9_FIXTURE.joinpath(*rel.parts).read_bytes()
        dest = FIXTURE.joinpath(*rel.parts)
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(data)
    overlay_rows = []
    for path, spec in OVERLAYS.items():
        dest = FIXTURE.joinpath(*safe_path(path).parts)
        before = sha(dest.read_bytes())
        data = overlay_data[path]
        dest.write_bytes(data)
        rows[path] = {"path": path, "sha256": sha(data), "bytes": len(data)}
        overlay_rows.append({"path": path, "baselineR9Sha256": before,
                             "r10Sha256": sha(data), "bytes": len(data),
                             "source": spec["source"]})

    actual = {p.relative_to(FIXTURE).as_posix() for p in FIXTURE.rglob("*") if p.is_file()}
    require(actual == set(rows), "prepared fixture file set differs from r9 audit")
    for path in sorted(actual):
        data = FIXTURE.joinpath(*safe_path(path).parts).read_bytes()
        row = rows[path]
        require(len(data) == row["bytes"] and sha(data) == row["sha256"],
                "prepared r10 hash/size mismatch: " + path)
    changed = sorted(path for path in rows
                     if rows[path]["sha256"] != index_rows(base["inputs"])[path]["sha256"])
    require(changed == sorted(OVERLAYS), "fixture changed outside the exact two r10 overlays")
    war_count = sum(path.startswith("war/") for path in actual)
    jar_count = sum(path.lower().endswith(".jar") for path in actual)
    require(war_count == 29 and jar_count == 9, "web/JAR source counts differ")
    require(len(actual) == 1340, "prepared r10 file count differs")

    BASE_AUDIT_OUT.write_bytes(raw_audit)
    audit = {
        "status": "SOURCE_ONLY_FIXTURE_PREPARED_NOT_VALIDATED",
        "scope": "r9 audited 1340-input source fixture with exactly two frozen r10 D01 boundary overlays; no build/runtime validation",
        "baselineAuditPath": str(R9_AUDIT),
        "baselineAuditSha256": EXPECTED_R9_AUDIT_SHA256,
        "baselineFixturePath": str(R9_FIXTURE),
        "baselineInputCount": 1340,
        "finalInputCount": len(actual),
        "r10OverlayCount": 2,
        "r10Overlays": overlay_rows,
        "changedFromR9": changed,
        "generatedWarCopied": False,
        "warFileCount": war_count,
        "pinnedJarCount": jar_count,
        "excludedGeneratedOrRuntimeFiles": True,
        "inputs": [rows[path] for path in sorted(rows)],
    }
    AUDIT_OUT.write_text(json.dumps(audit, indent=2) + "\n", encoding="utf-8")
    POINTER_OUT.write_text(str(FIXTURE.resolve()) + "\n", encoding="utf-8")
    print(json.dumps({"status": audit["status"], "inputCount": len(actual),
                      "changedPaths": changed, "warCount": war_count, "jarCount": jar_count,
                      "baselineAuditSha256": EXPECTED_R9_AUDIT_SHA256,
                      "auditSha256": sha(AUDIT_OUT.read_bytes())}, sort_keys=True))


if __name__ == "__main__":
    main()
