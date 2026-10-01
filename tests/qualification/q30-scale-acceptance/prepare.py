"""Prepare the current dirty Q30 source in a unique OS-temp export.

Usage: python prepare.py REPOSITORY NEW_OS_TEMP_DIRECTORY GWT_JAR_DIRECTORY
       [--plan ACCEPTANCE_PLAN_JSON] [--manifest PLAN4_MANIFEST_JSON]

The checkout is read only. This command snapshots working-tree bytes for every
tracked src/war/scripts/tests input, plus an untracked acceptance verifier only
when the captured CirSim still names it. It applies the frozen normal-screen
authorization overlay and copies pinned GWT jars; it does not build or run a
browser.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import stat
import subprocess
import sys
import tempfile


ROOTS = ("src", "war", "scripts", "tests")
EXCLUDED_COMPONENTS = {
    ".git", ".tools", "build", "target", "node_modules", "__pycache__",
    ".pytest_cache", ".mypy_cache", ".ruff_cache", ".cache",
}
VERIFIER = "src/com/lushprojects/circuitjs1/client/Q30NormalAcceptanceVerifier.java"
CIRSIM = "src/com/lushprojects/circuitjs1/client/CirSim.java"
EXPECTED_LIMITS = {"jobMillis": 90000, "sharedWork": 640,
                   "activeOperationMillis": 5000}
PATCHED_FILES = (
    "src/com/lushprojects/circuitjs1/circuitjs1.gwt.xml",
    "src/com/lushprojects/circuitjs1/client/GenerationCoordinator.java",
    "src/com/lushprojects/circuitjs1/client/GenerationJob.java",
    "scripts/verify-current-contracts.ps1",
)


def sha256_bytes(data):
    return hashlib.sha256(data).hexdigest()


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def reject_reparse(path):
    reparse = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
    cursor = path
    while True:
        if cursor.exists() and cursor.lstat().st_file_attributes & reparse:
            raise ValueError("Reparse point is not allowed: " + str(cursor))
        if cursor.parent == cursor:
            break
        cursor = cursor.parent


def safe_relative(path_text):
    path = PurePosixPath(path_text)
    if path.is_absolute() or not path.parts or any(part in ("", ".", "..") for part in path.parts):
        raise ValueError("Unsafe repository path: " + path_text)
    if any(part in EXCLUDED_COMPONENTS for part in path.parts):
        return None
    return path


def ensure_inside(path, parent):
    try:
        path.relative_to(parent)
    except ValueError as error:
        raise ValueError("Resolved path escaped its owner: " + str(path)) from error


def read_json(path, label):
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ValueError(label + " is not valid UTF-8 JSON: " + str(error)) from error
    if not isinstance(value, dict):
        raise ValueError(label + " must be a JSON object")
    return value


def validate_frozen_plan(plan_path, manifest_path, repository, head):
    plan = read_json(plan_path, "acceptance plan")
    manifest_raw = manifest_path.read_bytes()
    manifest_sha = sha256_bytes(manifest_raw)
    if plan.get("schema") != 1 or plan.get("baseHead") != head:
        raise ValueError("Acceptance plan does not belong to the current HEAD")
    if plan.get("planEpoch") != 4 or plan.get("manifestSha256") != manifest_sha:
        raise ValueError("Acceptance plan or plan-4 manifest identity changed")
    if plan.get("frozenManifest") != "docs/task-evidence/Q30/scale-plan-4/manifest-proposal.json":
        raise ValueError("Acceptance plan names an unexpected frozen manifest")
    if plan.get("limits") != EXPECTED_LIMITS:
        raise ValueError("Acceptance plan changed the authorized job limits")
    cases = plan.get("cases")
    order = plan.get("coldOrder")
    if not isinstance(cases, list) or len(cases) != 77:
        raise ValueError("Acceptance plan must retain exactly 77 roots")
    if not isinstance(order, list) or len(order) != 77:
        raise ValueError("Cold execution order must contain all 77 roots")
    seeds = []
    for row in cases:
        if not isinstance(row, dict):
            raise ValueError("Acceptance plan case is malformed")
        seed = row.get("seed")
        if not isinstance(seed, str) or not seed or not seed.lstrip("-").isdigit():
            raise ValueError("Acceptance plan seed is not decimal text")
        parsed = int(seed)
        if parsed < -(1 << 63) or parsed > (1 << 63) - 1 or str(parsed) != seed:
            raise ValueError("Acceptance plan seed is not canonical signed-long text")
        if row.get("canonical", "").startswith("rb30-plan@4;seed=" + seed + ";") is False:
            raise ValueError("Acceptance plan canonical identity disagrees with seed")
        if type(row.get("packages")) is not int or not 20 <= row["packages"] <= 40:
            raise ValueError("Acceptance plan package count is outside 20..40")
        if row.get("channels") not in (1, 2):
            raise ValueError("Acceptance plan channel count is not one or two")
        seeds.append(seed)
    if len(set(seeds)) != 77 or set(order) != set(seeds) or len(set(order)) != 77:
        raise ValueError("Acceptance plan roots/order are not a distinct permutation")
    if len(plan.get("serviceAndSensitivitySeeds", [])) != 21:
        raise ValueError("Acceptance plan lost its 21 service/sensitivity roots")
    return plan, manifest_sha, cases


def tracked_paths(repository):
    raw = subprocess.check_output(
        ["git", "-C", str(repository), "ls-files", "-z", "--", *ROOTS])
    paths = []
    for item in raw.split(b"\0"):
        if not item:
            continue
        text = item.decode("utf-8")
        relative = safe_relative(text)
        if relative is not None:
            paths.append((text, relative))
    return sorted(paths, key=lambda row: row[0])


def copy_regular_file(repository, app, path_text, relative):
    source = repository.joinpath(*relative.parts)
    reject_reparse(source)
    if source.is_symlink() or not source.is_file():
        raise ValueError("Tracked input is missing or not a regular file: " + path_text)
    resolved = source.resolve(strict=True)
    ensure_inside(resolved, repository)
    destination = app.joinpath(*relative.parts)
    destination.parent.mkdir(parents=True, exist_ok=True)
    with source.open("rb") as incoming, destination.open("xb") as outgoing:
        shutil.copyfileobj(incoming, outgoing)
    data_hash = sha256_file(destination)
    return {"path": path_text, "sha256": data_hash, "size": destination.stat().st_size}


def replace_java_tokens(path, replacements):
    text = path.read_text(encoding="utf-8")
    for token, value in replacements.items():
        if text.count(token) != 1:
            raise ValueError("Expected exactly one Java token: " + token)
        text = text.replace(token, value)
    path.write_text(text, encoding="utf-8", newline="")


def java_string_contents(value):
    return (value.replace("\\", "\\\\").replace('"', '\\"')
            .replace("\r", "\\r").replace("\n", "\\n"))


def verify_package(package):
    manifest = read_json(package / "overlay-manifest.json", "wrapper file manifest")
    files = manifest.get("packageFiles")
    if not isinstance(files, dict) or not files:
        raise ValueError("Wrapper package file hashes are missing")
    for relative, expected in files.items():
        safe = safe_relative(relative)
        if safe is None:
            raise ValueError("Wrapper manifest may not hash excluded output: " + relative)
        path = package.joinpath(*safe.parts)
        reject_reparse(path)
        if not path.is_file() or sha256_file(path) != expected:
            raise ValueError("Wrapper package input hash mismatch: " + relative)
    return manifest


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repository", type=Path)
    parser.add_argument("target", type=Path)
    parser.add_argument("gwt_jar_directory", type=Path)
    parser.add_argument("--plan", type=Path)
    parser.add_argument("--manifest", type=Path)
    args = parser.parse_args(argv)

    repository = args.repository.resolve(strict=True)
    package = Path(__file__).resolve().parent
    target = args.target.absolute()
    jars = args.gwt_jar_directory.resolve(strict=True)
    reject_reparse(target)
    reject_reparse(repository)
    if target.exists():
        raise ValueError("OS-temp destination must be new")
    temporary = Path(tempfile.gettempdir()).resolve(strict=True)
    ensure_inside(target.parent.resolve(strict=True), temporary)
    if target.resolve().is_relative_to(repository):
        raise ValueError("OS-temp export may not be inside the repository")

    head = subprocess.check_output(
        ["git", "-C", str(repository), "rev-parse", "HEAD"], text=True).strip()
    default_plan = package / "acceptance-plan.json"
    plan_path = (args.plan or default_plan).resolve(strict=True)
    manifest_path = (args.manifest or
        repository / "docs/task-evidence/Q30/scale-plan-4/manifest-proposal.json").resolve(strict=True)
    plan, manifest_sha, rows = validate_frozen_plan(
        plan_path, manifest_path, repository, head)
    wrapper = verify_package(package)

    target.mkdir(parents=True)
    app = target / "app"
    app.mkdir()
    snapshot_rows = []
    for path_text, relative in tracked_paths(repository):
        snapshot_rows.append(copy_regular_file(repository, app, path_text, relative))

    captured_cir = (app / CIRSIM).read_text(encoding="utf-8")
    verifier_file = app / VERIFIER
    verifier_in_tracked = any(row["path"] == VERIFIER for row in snapshot_rows)
    verifier_required = "Q30NormalAcceptanceVerifier" in captured_cir
    if verifier_required and not verifier_in_tracked:
        source = repository / VERIFIER
        if not source.is_file():
            raise ValueError("Captured CirSim references a missing Q30 acceptance verifier")
        reject_reparse(source)
        relative = PurePosixPath(VERIFIER)
        snapshot_rows.append(copy_regular_file(repository, app, VERIFIER, relative))

    snapshot_rows.sort(key=lambda row: row["path"])
    identity_payload = {
        "schema": 1,
        "baseHead": head,
        "files": snapshot_rows,
    }
    identity_bytes = json.dumps(identity_payload, sort_keys=True, separators=(",", ":"),
                                ensure_ascii=False).encode("utf-8")
    source_identity = sha256_bytes(identity_bytes)
    snapshot_receipt = dict(identity_payload)
    snapshot_receipt["sourceIdentity"] = source_identity
    (target / "source-snapshot.json").write_text(
        json.dumps(snapshot_receipt, sort_keys=True, indent=2) + "\n", encoding="utf-8")

    patch = package / "baseline.patch"
    subprocess.run(["git", "apply", "--check", str(patch)], cwd=app, check=True)
    subprocess.run(["git", "apply", str(patch)], cwd=app, check=True)

    overlay_root = package / "overlay"
    overlay_records = []
    for source in sorted(overlay_root.rglob("*")):
        if not source.is_file():
            continue
        relative = source.relative_to(overlay_root)
        destination = app / relative
        if destination.exists():
            raise ValueError("Overlay would replace a captured source file: " + str(relative))
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)
        overlay_records.append({
            "path": relative.as_posix(), "sha256": sha256_file(destination),
            "size": destination.stat().st_size,
        })

    java_harness = app / "src/com/lushprojects/circuitjs1/client/Q30NormalScreenHarness.java"
    frozen_roots = "\n".join(row["seed"] + "|" + row["canonical"] for row in rows)
    replace_java_tokens(java_harness, {
        "__Q30_SOURCE_HEAD__": head,
        "__Q30_SOURCE_IDENTITY__": source_identity,
        "__Q30_MANIFEST_SHA256__": manifest_sha,
        "__Q30_FROZEN_ROOTS__": java_string_contents(frozen_roots),
    })
    overlay_records = [
        {"path": row["path"], "sha256": sha256_file(app / row["path"]),
         "size": (app / row["path"]).stat().st_size}
        for row in overlay_records
    ]

    jar_manifest = read_json(package / "gwt-jars.json", "GWT dependency manifest")
    jar_hashes = jar_manifest.get("gwtJars")
    if not isinstance(jar_hashes, dict) or not jar_hashes:
        raise ValueError("Pinned GWT dependency hashes are missing")
    dependency_dir = app / ".tools/gwt-2.7.0"
    dependency_dir.mkdir(parents=True)
    for name, expected in sorted(jar_hashes.items()):
        source = jars / name
        reject_reparse(source)
        if not source.is_file() or sha256_file(source) != expected:
            raise ValueError("Pinned GWT jar hash mismatch: " + name)
        shutil.copyfile(source, dependency_dir / name)

    host = target / "host"
    host.mkdir()
    host_runner = package / "normal_screen_host.py"
    shutil.copyfile(host_runner, host / host_runner.name)
    receipt = {
        "schema": 1,
        "status": "PREPARED",
        "baseHead": head,
        "branch": subprocess.check_output(
            ["git", "-C", str(repository), "branch", "--show-current"], text=True).strip(),
        "sourceIdentity": source_identity,
        "sourceFileCount": len(snapshot_rows),
        "sourceFiles": snapshot_rows,
        "sourceSnapshotSha256": sha256_file(target / "source-snapshot.json"),
        "frozenManifest": plan["frozenManifest"],
        "manifestSha256": manifest_sha,
        "acceptancePlanSha256": sha256_file(plan_path),
        "limits": plan["limits"],
        "rootCount": len(rows),
        "coldOrder": plan["coldOrder"],
        "requiredUntrackedVerifierIncluded": bool(verifier_required and not verifier_in_tracked),
        "baselinePatchSha256": sha256_file(patch),
        "overlayFiles": overlay_records,
        "hostRunnerSha256": sha256_file(host / host_runner.name),
        "wrapperIdentity": wrapper.get("wrapperIdentity"),
    }
    (target / "prepare-receipt.json").write_text(
        json.dumps(receipt, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print("PASS: exact current source snapshot and isolated Q30 scale overlay prepared")
    print("sourceIdentity=" + source_identity)
    print("tracked/supplemental files=" + str(len(snapshot_rows)))
    print("manifest roots=" + str(len(rows)) + "; cold jobs=" + str(len(plan["coldOrder"])))
    print("export=" + str(target))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print("ERROR: " + str(error), file=sys.stderr)
        raise SystemExit(2)
