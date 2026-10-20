"""Freeze and audit the exact normal-packing r2 compiler inputs.

The r1 baseline is an immutable external reference for this continuation.  A
candidate seal contains the old compiler source, the ten r1 contract classes,
and the four r2 source additions.  The archive stores every byte that differs
from the old compiler seal so an audit can detect both source drift and an
incomplete handoff.
"""
import hashlib
import json
import stat
import subprocess
import sys
from pathlib import Path, PurePosixPath


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
R1 = HERE.parent / "normal-packing-r1"
BASE_SEAL = ROOT / "docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json"
BASE_SHA = "9ff8de2abae0d3e555bc57c84ff7231d2aa8abd5dfdc2383a65398915074da9a"
BASE_COMMIT = "2a73b949a02de718bd968c2920ba6045d7406c5a"
SOURCE_COUNT = 1481
R1_UNIT_NAMES = (
    "PcbConnectedPocketPackingContractTest",
    "PcbNormalPackingIdentityContractTest",
)
R1_ROUTE_NAMES = (
    "PcbNormalPackingCensusContractTest",
    "PcbNormalPackingCensusContractTestGroup2",
    "PcbNormalPackingCensusContractTestGroup3",
    "PcbNormalPackingCensusContractTestGroup4",
    "PcbNormalPackingCensusContractTestGroup5",
    "PcbNormalPackingCensusContractTestGroup6",
    "PcbNormalPackingCensusContractTestGroup7",
    "PcbNormalPackingCensusContractTestGroup8",
)
R2_ADDITIONS = (
    "src/com/lushprojects/circuitjs1/client/PcbPackingMetrics.java",
    "src/com/lushprojects/circuitjs1/client/PcbPackingProbeReceipt.java",
    "tests/contracts/PcbPackingMetricsContractTest.java",
    "tests/contracts/PcbPackingProbeReceiptContractTest.java",
)
GATE_FILES = (
    "scope.md",
    "protocol.json",
    "inputs.py",
    "unit-suites.json",
    "routes-suites.json",
    "run_native.ps1",
    "check_process_cleanup.ps1",
    "sanitize.py",
    "suite_transport.ps1",
    "check_suite_transport.ps1",
    "suite-transport-canaries.json",
    "assess.py",
    "check_assessment.py",
    "assessment-canary-results.json",
)


def _read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def _sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _safe_relative(relative):
    if not isinstance(relative, str) or "\\" in relative:
        raise RuntimeError("input path is not slash-relative")
    pure = PurePosixPath(relative)
    if (pure.is_absolute() or not pure.parts or
            any(part in ("", ".", "..") for part in pure.parts)):
        raise RuntimeError("input path is not safely contained")
    return pure


def _is_reparse(path):
    info = path.lstat()
    return stat.S_ISLNK(info.st_mode) or bool(
        getattr(info, "st_file_attributes", 0) & stat.FILE_ATTRIBUTE_REPARSE_POINT)


def _safe_file(root, relative):
    pure = _safe_relative(relative)
    root = root.resolve(strict=True)
    cursor = root
    for part in pure.parts:
        cursor = cursor / part
        if _is_reparse(cursor):
            raise RuntimeError("input path crosses a reparse point: " + relative)
    target = root.joinpath(*pure.parts)
    resolved = target.resolve(strict=True)
    try:
        resolved.relative_to(root)
    except ValueError as exc:
        raise RuntimeError("input path escapes its root: " + relative) from exc
    if not target.is_file():
        raise RuntimeError("input path is not a regular file: " + relative)
    return target


def _load_base():
    if _sha(BASE_SEAL) != BASE_SHA:
        raise RuntimeError("old compiler base seal changed")
    seal = _read(BASE_SEAL)
    base = {}
    for item in seal.get("files", []):
        relative = item.get("path")
        if (isinstance(relative, str) and
                not relative.startswith("docs/task-evidence/PCB/silkscreen-scale-r1/")):
            base[relative] = item.get("sha256")
    if len(base) != 1467:
        raise RuntimeError("old compiler base source count changed")
    return base


def _manifest_names(directory, name, expected):
    manifest = _read(directory / name)
    if (not isinstance(manifest, list) or
            [row[0] for row in manifest] != list(expected) or
            any(not isinstance(row, list) or len(row) != 2 or
                not isinstance(row[1], str) for row in manifest)):
        raise RuntimeError("r1 manifest/order changed: " + name)
    return [row[0] for row in manifest]


def _reference_file(relative):
    pure = _safe_relative(relative)
    candidates = []
    root_candidate = ROOT.joinpath(*pure.parts)
    if root_candidate.exists():
        candidates.append(root_candidate)
    r1_candidate = R1.joinpath(*pure.parts)
    if r1_candidate.exists():
        candidates.append(r1_candidate)
    if pure.parts[:1] == ("normal-packing-r1",):
        packet_candidate = R1.parent.joinpath(*pure.parts)
        if packet_candidate.exists():
            candidates.append(packet_candidate)
    if not candidates:
        raise RuntimeError("baseline reference is unavailable: " + relative)
    for candidate in candidates:
        try:
            candidate.resolve(strict=True).relative_to(ROOT.resolve(strict=True))
        except ValueError as exc:
            raise RuntimeError("baseline reference escapes repository: " + relative) from exc
        cursor = candidate.resolve(strict=True).parent
        if _is_reparse(candidate) or _is_reparse(cursor):
            raise RuntimeError("baseline reference crosses a reparse point: " + relative)
    return candidates[0]


def _audit_baseline_reference(protocol):
    reference = protocol.get("baselineReference")
    if not isinstance(reference, dict):
        raise RuntimeError("r2 protocol baselineReference is missing")
    pins = reference.get("fileSha256")
    if not isinstance(pins, dict) or not pins:
        raise RuntimeError("r2 protocol baselineReference.fileSha256 is missing")
    for relative, expected in pins.items():
        if (not isinstance(relative, str) or not isinstance(expected, str) or
                len(expected) != 64 or expected.lower() != expected):
            raise RuntimeError("invalid baseline reference pin")
        actual = _sha(_reference_file(relative))
        if actual != expected:
            raise RuntimeError("baseline reference changed: " + relative)


def _audit_r1_baseline():
    command = [sys.executable, "-X", "utf8", "-B",
               str(R1 / "inputs.py"), "audit-baseline"]
    result = subprocess.run(command, cwd=str(ROOT), check=False)
    if result.returncode != 0:
        raise RuntimeError("immutable r1 baseline audit failed")


def _candidate_inputs():
    base = _load_base()
    protocol_path = HERE / "protocol.json"
    protocol = _read(protocol_path)
    if protocol.get("baseCommit") != BASE_COMMIT:
        raise RuntimeError("r2 protocol base commit changed")
    _audit_baseline_reference(protocol)
    _manifest_names(R1, "unit-suites.json", R1_UNIT_NAMES)
    _manifest_names(R1, "routes-suites.json", R1_ROUTE_NAMES)
    _manifest_names(HERE, "unit-suites.json", R1_UNIT_NAMES + (
        "PcbPackingMetricsContractTest", "PcbPackingProbeReceiptContractTest"))
    _manifest_names(HERE, "routes-suites.json", R1_ROUTE_NAMES)
    additions = {"tests/contracts/" + name + ".java" for name in R1_UNIT_NAMES}
    additions.update("tests/contracts/" + name + ".java" for name in R1_ROUTE_NAMES)
    additions.update(R2_ADDITIONS)
    if len(additions) != 14 or additions & set(base):
        raise RuntimeError("r2 source additions are not the declared fourteen files")
    names = sorted(set(base) | additions)
    files = {name: _sha(_safe_file(ROOT, name)) for name in names}
    if len(files) != SOURCE_COUNT:
        raise RuntimeError("r2 candidate source count changed")
    expected_java = {name for name in files
                     if name.endswith(".java") and
                     (name.startswith("src/") or name.startswith("tests/contracts/"))}
    found_java = set()
    for directory in (ROOT / "src", ROOT / "tests" / "contracts"):
        for path in directory.rglob("*.java"):
            relative = path.relative_to(ROOT).as_posix()
            found_java.add(relative)
    if found_java != expected_java:
        raise RuntimeError("compiler Java inventory differs from r2 sealed inputs")
    helper_sha = protocol.get("sourceHelperSha256")
    if helper_sha is not None:
        if (not isinstance(helper_sha, str) or len(helper_sha) != 64 or
                helper_sha.lower() != helper_sha or
                files["tests/contracts/PcbNormalPackingCensusContractTest.java"] != helper_sha):
            raise RuntimeError("r2 source helper hash changed")
    return base, protocol, files


def _gate_hashes():
    return {name: _sha(_safe_file(HERE, name)) for name in GATE_FILES}


def _record(protocol, files):
    return {
        "schema": 1,
        "phase": "candidate",
        "baseCommit": BASE_COMMIT,
        "protocolSha256": _sha(HERE / "protocol.json"),
        "baseMainSealSha256": BASE_SHA,
        "sourceCount": len(files),
        "files": [{"path": name, "sha256": files[name]}
                  for name in sorted(files)],
        "gateOwners": _gate_hashes(),
    }


def _archive_delta(root, archive, base, files):
    delta = {name for name in files
             if name not in base or base[name] != files[name]}
    if archive.exists():
        raise RuntimeError("candidate source archive already exists")
    for name in sorted(delta):
        destination = archive / Path(*PurePosixPath(name).parts)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(_safe_file(root, name).read_bytes())
    return delta


def _verify_archive(archive, delta, files):
    if not archive.is_dir():
        raise RuntimeError("candidate source archive is missing")
    archived = {}
    for path in archive.rglob("*"):
        if _is_reparse(path):
            raise RuntimeError("candidate source archive crosses a reparse point")
        if path.is_file():
            archived[path.relative_to(archive).as_posix()] = _sha(path)
    expected = {name: files[name] for name in sorted(delta)}
    if archived != expected:
        raise RuntimeError("candidate source archive differs from the sealed delta")


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in ("freeze-candidate", "audit-candidate"):
        raise RuntimeError("usage: inputs.py freeze-candidate|audit-candidate")
    action = sys.argv[1]
    _audit_r1_baseline()
    base, protocol, files = _candidate_inputs()
    record = _record(protocol, files)
    seal = HERE / "candidate-inputs.json"
    archive = HERE / "candidate-source"
    delta = {name for name in files
             if name not in base or base[name] != files[name]}
    if action == "freeze-candidate":
        if seal.exists() or archive.exists():
            raise RuntimeError("preserve the previous frozen candidate attempt")
        _archive_delta(ROOT, archive, base, files)
        seal.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    if not seal.is_file():
        raise RuntimeError("candidate input seal is missing")
    if _read(seal) != record:
        raise RuntimeError("candidate source or gate owner changed")
    _verify_archive(archive, delta, files)
    print("PASS " + action + " exact sources=" + str(len(files)) +
          " deltas=" + str(len(delta)) + " gates=" + str(len(GATE_FILES)))


if __name__ == "__main__":
    main()
