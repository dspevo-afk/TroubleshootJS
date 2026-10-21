"""Generate private current/no-hook LED diversity diagnostic inputs."""
import argparse
import difflib
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
BASE = "6426b3ff702c8197324dd996b5b4ede899bb9139"
MATRIX = "tests/contracts/QuickPlayPhysicalMatrixContractTest.java"
GENERATOR = "src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java"
BASELINE_ADAPTER_SHA = "a079fd62c07d9790a73884ae8f284e60ff60e7122e4b92711033a64d502c4247"
EXPORTER_SHA = "fb0f1caea0d5d341e1fc5ea04ee3f0b82b8d7d4ac0664e5cca81722e51a9ef6a"
FAMILY = "LED_INDICATOR"
BATCH_START_ORDINAL = "0"


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def replace_once(source, old, new, label, changes):
    newline = "\r\n" if "\r\n" in source else "\n"
    old = old.replace("\n", newline)
    new = new.replace("\n", newline)
    count = source.count(old)
    if count != 1:
        raise AssertionError("expected one guarded replacement: " + label)
    changes.append({
        "label": label,
        "replacements": 1,
        "oldSha256": sha(old.encode("utf-8")),
        "newSha256": sha(new.encode("utf-8")),
    })
    return source.replace(old, new)


def write_diff(path, before, after, before_name, after_name):
    lines = difflib.unified_diff(
        before.splitlines(), after.splitlines(), fromfile=before_name,
        tofile=after_name, lineterm="")
    path.write_bytes(("\n".join(lines) + "\n").encode("utf-8"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline-adapter", type=Path, required=True)
    parser.add_argument("--output-directory", type=Path, required=True)
    args = parser.parse_args()
    if not args.output_directory.is_dir() or any(args.output_directory.iterdir()):
        raise AssertionError("output directory must exist and be empty")

    baseline = json.loads((HERE / "baseline-inputs.json").read_text(encoding="utf-8-sig"))
    if baseline["baseCommit"] != BASE:
        raise AssertionError("unexpected diagnostic baseline commit")
    if len(baseline["files"]) != 1482 or len(baseline["machinery"]) != 24:
        raise AssertionError("frozen source or machinery inventory changed")

    matrix_bytes = (ROOT / MATRIX).read_bytes()
    generator_bytes = (ROOT / GENERATOR).read_bytes()
    if sha(matrix_bytes) != baseline["files"][MATRIX]:
        raise AssertionError("matrix source differs from frozen baseline")
    if sha(generator_bytes) != baseline["files"][GENERATOR]:
        raise AssertionError("published generator differs from frozen diagnostic baseline")
    if sha(args.baseline_adapter.read_bytes()) != BASELINE_ADAPTER_SHA:
        raise AssertionError("paired-pocket baseline adapter hash mismatch")

    exporter = HERE / "export-source" / "SparseNormalBoardDiagnostic.java"
    exporter_bytes = exporter.read_bytes()
    if sha(exporter_bytes) != EXPORTER_SHA:
        raise AssertionError("task diagnostic source hash mismatch")

    published_generator = generator_bytes.decode("utf-8")
    private_generator = published_generator
    owner_changes = []
    private_generator = replace_once(
        private_generator,
        "        session.packingPlan = PcbPlacementCompactor.compactPairedVacancies(\n"
        "            session.board, constraints, session.packingPlan);\n",
        "",
        "remove only the two paired-compactor orchestration call lines",
        owner_changes)
    if len(owner_changes) != 1:
        raise AssertionError("unexpected private generator change count")

    baseline_adapter_bytes = args.baseline_adapter.read_bytes()
    published_adapter = baseline_adapter_bytes.decode("utf-8")
    adapter_changes = []
    current_adapter = replace_once(
        published_adapter,
        "ValidateSet('PARALLEL_DUAL_INDICATOR')",
        "ValidateSet('LED_INDICATOR')",
        "restrict the current diagnostic adapter to LED_INDICATOR",
        adapter_changes)
    current_adapter = replace_once(
        current_adapter,
        "sparse normal board diagnostic ",
        "paired diversity diagnostic batch ",
        "use the batch-specific receipt marker",
        adapter_changes)
    if len(adapter_changes) != 2:
        raise AssertionError("unexpected current adapter change count")

    nohook_changes = []
    nohook_adapter = replace_once(
        current_adapter,
        "    [Parameter(Mandatory=$true)][string]$TaskExportSource,",
        "    [Parameter(Mandatory=$true)][string]$TaskExportSource,\n"
        "    [Parameter(Mandatory=$true)][ValidateScript({Test-Path -LiteralPath $_ -PathType Leaf})][string]$TaskGeneratorSource,",
        "require the private no-hook generator source",
        nohook_changes)
    nohook_adapter = replace_once(
        nohook_adapter,
        "        Where-Object { $_.Name -ne 'PhysicalSpecificationDeveloperVerifier.java' -and",
        "        Where-Object { $_.Name -ne 'SeededPcbLayoutGenerator.java' -and $_.Name -ne 'PhysicalSpecificationDeveloperVerifier.java' -and",
        "exclude only the checked-in generator from the no-hook native compile",
        nohook_changes)
    nohook_adapter = replace_once(
        nohook_adapter,
        "    $sourcePaths += $stub",
        "    $sourcePaths += $stub\n"
        "    $sourcePaths += (Resolve-Path -LiteralPath $TaskGeneratorSource).Path",
        "compile the private same-owner generator copy",
        nohook_changes)
    if len(nohook_changes) != 3:
        raise AssertionError("unexpected no-hook adapter change count")

    private_generator_bytes = private_generator.encode("utf-8")
    current_adapter_bytes = current_adapter.encode("utf-8")
    nohook_adapter_bytes = nohook_adapter.encode("utf-8")
    (args.output_directory / "SeededPcbLayoutGenerator.java").write_bytes(
        private_generator_bytes)
    (args.output_directory / "current-adapter.ps1").write_bytes(
        current_adapter_bytes)
    (args.output_directory / "nohook-adapter.ps1").write_bytes(
        nohook_adapter_bytes)
    write_diff(args.output_directory / "owner-diff.txt", published_generator,
        private_generator, "published/SeededPcbLayoutGenerator.java",
        "private-nohook/SeededPcbLayoutGenerator.java")
    write_diff(args.output_directory / "adapter-diff.txt", published_adapter,
        nohook_adapter, "paired-pocket/baseline-adapter.ps1",
        "private-nohook/nohook-adapter.ps1")

    manifest = {
        "status": "GENERATED_TASK_ONLY_LED_DIVERSITY_DIAGNOSTIC",
        "baseCommit": BASE,
        "family": FAMILY,
        "batchStartOrdinal": BATCH_START_ORDINAL,
        "sourceMatrixSha256": sha(matrix_bytes),
        "exporterSha256": sha(exporter_bytes),
        "baselineAdapterSha256": sha(baseline_adapter_bytes),
        "currentAdapterSha256": sha(current_adapter_bytes),
        "nohookAdapterSha256": sha(nohook_adapter_bytes),
        "publishedGeneratorSha256": sha(generator_bytes),
        "privateGeneratorSha256": sha(private_generator_bytes),
        "guardedChanges": {
            "publishedGenerator": owner_changes,
            "currentAdapter": adapter_changes,
            "nohookAdapter": nohook_changes,
        },
        "limits": (
            "The copied physical matrix runs LED_INDICATOR only with its original "
            "zero-based root sequence, target 20, maximum 48 roots, and all "
            "original generation, exact-replay, physical/novelty uniqueness, "
            "placement, routing, copper, macro, and outline assertions. The "
            "diagnostic row is passive and precedes the unchanged physical "
            "duplicate assertion. The current adapter compiles checked-in WIP; "
            "the no-hook adapter substitutes one private generator source with "
            "only the two paired-compactor call lines removed."
        ),
    }
    (args.output_directory / "private-input-manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print("PASS generated private LED current/no-hook diagnostic adapters")


if __name__ == "__main__":
    main()
