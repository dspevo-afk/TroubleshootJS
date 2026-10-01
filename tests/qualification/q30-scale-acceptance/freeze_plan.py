"""Freeze plan-4 manifests from the compiled application's actual Java export."""
import argparse
import hashlib
import json
import re
from pathlib import Path
import sys


PACKAGE = Path(__file__).resolve().parent
MANIFEST_RELATIVE = "docs/task-evidence/Q30/scale-plan-4/manifest-proposal.json"
EXPECTED_LIMITS = {"jobMillis": 90000, "sharedWork": 640,
                   "activeOperationMillis": 5000}
LONG_MIN = -(1 << 63)
LONG_MAX = (1 << 63) - 1
HASH_RE = re.compile(r"^[0-9a-f]{64}$")


class Invalid(ValueError):
    pass


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise Invalid("duplicate JSON key: " + key)
        result[key] = value
    return result


def bad_constant(value):
    raise Invalid("non-finite JSON constant: " + value)


def load_object(path, label):
    raw = Path(path).read_bytes()
    try:
        value = json.loads(raw.decode("utf-8"), object_pairs_hook=duplicate_keys,
                           parse_constant=bad_constant)
    except (UnicodeError, json.JSONDecodeError) as error:
        raise Invalid(label + " is invalid UTF-8 JSON: " + str(error)) from error
    if not isinstance(value, dict):
        raise Invalid(label + " must be an object")
    return value, raw


def need(ok, message):
    if not ok:
        raise Invalid(message)


def canonical_long(value, label):
    need(isinstance(value, str) and bool(value), label + " must be signed-long decimal text")
    try:
        parsed = int(value, 10)
    except ValueError as error:
        raise Invalid(label + " is not decimal") from error
    need(LONG_MIN <= parsed <= LONG_MAX and str(parsed) == value,
         label + " is not canonical signed-long text")
    return parsed


def plan_identity(canonical, seed, packages=None, channels=None):
    need(isinstance(canonical, str) and
         canonical.startswith("rb30-plan@4;seed=" + seed + ";"),
         "plan-4 canonical identity does not match its seed")
    package_match = re.search(r"(?:^|;)packages=(\d+)(?:;|$)", canonical)
    channel_match = re.search(r"(?:^|;)channels=(\d+)(?:;|$)", canonical)
    need(package_match is not None and channel_match is not None,
         "plan-4 canonical identity omits package or channel count")
    package_count, channel_count = int(package_match.group(1)), int(channel_match.group(1))
    need(20 <= package_count <= 40 and channel_count in (1, 2),
         "plan-4 identity is outside the frozen 20..40 / one-or-two-channel envelope")
    if packages is not None:
        need(type(packages) is int and packages == package_count,
             "Java package count differs from plan canonical identity")
    if channels is not None:
        need(type(channels) is int and channels == channel_count,
             "Java channel count differs from plan canonical identity")
    return package_count, channel_count


def verify_source_identity(receipt, receipt_path):
    need(receipt.get("status") == "PREPARED", "source export is not a prepared candidate")
    source_files = receipt.get("sourceFiles")
    need(isinstance(source_files, list) and bool(source_files),
         "source export lacks per-file identity hashes")
    paths = []
    for row in source_files:
        need(isinstance(row, dict) and isinstance(row.get("path"), str) and
             HASH_RE.fullmatch(row.get("sha256", "")) and
             type(row.get("size")) is int and row["size"] >= 0,
             "source file identity row is malformed")
        paths.append(row["path"])
    need(len(set(paths)) == len(paths), "source file identity paths are duplicated")
    payload = {"schema": 1, "baseHead": receipt.get("baseHead"), "files": source_files}
    identity = sha256(json.dumps(payload, sort_keys=True, separators=(",", ":"),
                                ensure_ascii=False).encode("utf-8"))
    need(identity == receipt.get("sourceIdentity"), "source identity does not match its file hashes")
    snapshot_path = Path(receipt_path).parent / "source-snapshot.json"
    _, snapshot_raw = load_object(snapshot_path, "source snapshot")
    need(sha256(snapshot_raw) == receipt.get("sourceSnapshotSha256"),
         "source snapshot raw-byte hash differs from preparation receipt")
    return identity


def capture_prepared_app_identity(app_path):
    app = Path(app_path).resolve(strict=True)
    rows = []
    for root_name in ("src", "war"):
        root = app / root_name
        need(root.is_dir() and not root.is_symlink(),
             "prepared app is missing a regular " + root_name + " tree")
        for directory, directory_names, file_names in __import__("os").walk(
                root, topdown=True, followlinks=False):
            base = Path(directory)
            kept = []
            for name in directory_names:
                child = base / name
                need(not child.is_symlink(), "prepared app contains a symlink directory")
                kept.append(name)
            directory_names[:] = kept
            for name in file_names:
                path = base / name
                need(not path.is_symlink() and path.is_file(),
                     "prepared app contains a symlink or non-file input")
                rows.append({"path": path.relative_to(app).as_posix(),
                             "size": path.stat().st_size,
                             "sha256": sha256(path.read_bytes())})
    rows.sort(key=lambda row: row["path"])

    def aggregate(prefix):
        selected = [row for row in rows if row["path"].startswith(prefix + "/")]
        return sha256(json.dumps(selected, sort_keys=True, separators=(",", ":")).encode("utf-8"))

    canonical = json.dumps(rows, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return {
        "fileCount": len(rows),
        "sourceFileCount": sum(row["path"].startswith("src/") for row in rows),
        "webFileCount": sum(row["path"].startswith("war/") for row in rows),
        "combinedSha256": sha256(canonical),
        "sourceSha256": aggregate("src"),
        "webSha256": aggregate("war"),
    }


def validate_export_row(row, expected, index):
    need(isinstance(row, dict) and row.get("manifestOrder") == index,
         "compiled Java root row order mismatch at %d" % index)
    seed = expected.get("seed")
    canonical_long(seed, "frozen plan seed")
    need(row.get("seed") == seed and row.get("planCanonical") == expected.get("canonical"),
         "compiled Java plan identity differs from frozen root " + str(seed))
    plan_identity(row.get("planCanonical"), seed, expected.get("packages"),
                  expected.get("channels"))
    candidates = row.get("candidates")
    need(isinstance(candidates, list) and len(candidates) == 4,
         "compiled Java export must contain four candidates for root " + seed)
    seen_seeds = set()
    for ordinal, candidate in enumerate(candidates):
        need(isinstance(candidate, dict) and candidate.get("ordinal") == ordinal,
             "compiled Java candidate order mismatch for root " + seed)
        candidate_seed = candidate.get("seed")
        canonical_long(candidate_seed, "candidate seed")
        need(candidate_seed not in seen_seeds, "duplicate candidate seed for root " + seed)
        seen_seeds.add(candidate_seed)
        packages, channels = plan_identity(candidate.get("planCanonical"), candidate_seed,
                                           candidate.get("packages"), candidate.get("channels"))
        manifest = candidate.get("manifest")
        expected_prefix = "candidate=%02d;" % ordinal
        need(isinstance(manifest, str) and manifest.startswith(expected_prefix),
             "compiled Java candidate manifest ordinal mismatch")
        need("seed=" + candidate_seed + ";" in manifest,
             "compiled Java manifest seed differs from candidate plan")
        need("plan=rb30-plan@4;" in manifest,
             "compiled Java candidate manifest does not identify plan epoch 4")
        need("packages=" + str(packages) + ";" in manifest or
             manifest.endswith("packages=" + str(packages)),
             "compiled Java candidate manifest package count differs from its plan")
        need("channels=" + str(channels) + ";" in manifest or
             manifest.endswith("channels=" + str(channels)),
             "compiled Java candidate manifest channel count differs from its plan")
    request_canonical = row.get("requestCanonical")
    first_exact = candidates[0]["manifest"].split(";", 1)[1]
    need(isinstance(request_canonical, str) and
         first_exact.replace(";search=false;", ";search=true;", 1) == request_canonical,
         "compiled Java root request is not the ordinary four-candidate search")


def validate_manifest_export_host(export_raw, host, host_raw):
    need(host.get("outcome") == "PASS" and host.get("errors") == [],
         "compiled manifest-export host record did not pass cleanly")
    host_cases = host.get("cases")
    need(isinstance(host_cases, list) and host.get("caseCount") == len(host_cases),
         "manifest-export host case count is malformed")
    matching = [case for case in host_cases if isinstance(case, dict) and
                case.get("reportSha256") == sha256(export_raw)]
    need(len(matching) == 1,
         "host record must contain exactly one case bound to the compiled export bytes")
    match = matching[0]
    need(match.get("outcome") == "PASS" and
         match.get("reportObserved") is True and match.get("reportJsonValid") is True and
         match.get("reportMatch") is True and match.get("stateMatch") is True and
         match.get("terminalReached") is True and
         "tsjNormalMode=manifest-export" in match.get("url", ""),
         "host record does not prove observation of this compiled Java export")
    need(isinstance(host.get("startedUtc"), str) and host.get("startedUtc") and
         isinstance(host.get("finishedUtc"), str) and host.get("finishedUtc"),
         "manifest-export host record lacks capture timestamps")
    return {
        "hostRecordSha256": sha256(host_raw),
        "startedUtc": host["startedUtc"],
        "finishedUtc": host["finishedUtc"],
        "outcome": host["outcome"],
        "reportSha256": sha256(export_raw),
        "caseCount": len(host_cases),
        "caseName": match.get("name"),
        "caseStartedUtc": match.get("startedUtc"),
        "caseFinishedUtc": match.get("finishedUtc"),
    }


def freeze(repository, plan_path, receipt_path, export_path, prepared_app_path, output_path,
           export_host_path=None):
    repository = Path(repository).resolve(strict=True)
    plan, plan_raw = load_object(plan_path, "acceptance plan")
    receipt, _ = load_object(receipt_path, "preparation receipt")
    export, export_raw = load_object(export_path, "compiled Java manifest export")
    export_evidence = None
    if export_host_path is not None:
        host, host_raw = load_object(export_host_path, "manifest-export host record")
        export_evidence = validate_manifest_export_host(export_raw, host, host_raw)
    manifest_path = repository / MANIFEST_RELATIVE
    manifest_raw = manifest_path.read_bytes()
    manifest_sha = sha256(manifest_raw)
    acceptance_sha = sha256(plan_raw)
    source_identity = verify_source_identity(receipt, receipt_path)
    prepared_input_identity = capture_prepared_app_identity(prepared_app_path)
    need(receipt.get("acceptancePlanSha256") == acceptance_sha,
         "preparation receipt does not bind the bundled acceptance plan")
    need(Path(prepared_app_path).resolve(strict=True) ==
         (Path(receipt_path).resolve(strict=True).parent / "app").resolve(strict=True),
         "prepared app is not the app directory bound by this preparation receipt")

    need(plan.get("schema") == 1 and plan.get("planEpoch") == 4,
         "unsupported acceptance plan schema/epoch")
    need(plan.get("baseHead") == receipt.get("baseHead"), "acceptance plan source HEAD mismatch")
    need(plan.get("frozenManifest") == MANIFEST_RELATIVE and
         plan.get("manifestSha256") == manifest_sha == receipt.get("manifestSha256"),
         "frozen plan-4 manifest raw-byte hash mismatch")
    need(plan.get("limits") == EXPECTED_LIMITS == receipt.get("limits"),
         "authorized normal generation budgets changed")
    cases = plan.get("cases")
    order = plan.get("coldOrder")
    need(isinstance(cases, list) and len(cases) == 77 and
         isinstance(order, list) and len(order) == 77,
         "frozen plan must preserve all 77 roots and execution slots")
    seeds = [row.get("seed") if isinstance(row, dict) else None for row in cases]
    for seed in seeds:
        canonical_long(seed, "frozen plan seed")
    need(len(set(seeds)) == 77 and len(set(order)) == 77 and set(order) == set(seeds),
         "frozen execution order is not a permutation of all plan roots")
    need(receipt.get("rootCount") == 77 and receipt.get("coldOrder") == order,
         "preparation receipt does not bind the 77-root cold order")
    need((export.get("schema"), export.get("kind"), export.get("sourceCommit"),
          export.get("sourceIdentity"), export.get("manifestSha256"),
          export.get("planVersion"), export.get("rootCount")) ==
         (1, "Q30_PLAN4_MANIFEST_EXPORT", receipt.get("baseHead"), source_identity,
          manifest_sha, 4, 77),
         "compiled Java export source, manifest, or plan epoch mismatch")
    export_rows = export.get("rows")
    need(isinstance(export_rows, list) and len(export_rows) == 77,
         "compiled Java export is missing frozen roots")
    frozen_cases = []
    for index, (source_row, java_row) in enumerate(zip(cases, export_rows)):
        validate_export_row(java_row, source_row, index)
        frozen_cases.append({
            "manifestOrder": index,
            "cohort": source_row.get("cohort"),
            "seed": source_row["seed"],
            "canonical": source_row["canonical"],
            "packages": source_row["packages"],
            "channels": source_row["channels"],
            "requestCanonical": java_row["requestCanonical"],
            "candidates": java_row["candidates"],
        })
    case_by_seed = {row["seed"]: row for row in frozen_cases}
    need(all(isinstance(seed, str) and seed in case_by_seed for seed in order),
         "execution order names a root outside the frozen manifest")

    wrapper = load_object(PACKAGE / "overlay-manifest.json", "wrapper package manifest")[0]
    need(wrapper.get("wrapperIdentity") == receipt.get("wrapperIdentity"),
         "wrapper identity differs from the preparation receipt")
    frozen = {
        "schema": 1,
        "status": "PLANNED",
        "baseHead": receipt["baseHead"],
        "planEpoch": 4,
        "manifest": MANIFEST_RELATIVE,
        "manifestSha256": manifest_sha,
        "acceptancePlanSha256": acceptance_sha,
        "compiledJavaExportSha256": sha256(export_raw),
        "manifestExportEvidence": export_evidence,
        "sourceIdentity": source_identity,
        "sourceSnapshotSha256": receipt["sourceSnapshotSha256"],
        "sourceFileCount": receipt["sourceFileCount"],
        "sourceFiles": receipt["sourceFiles"],
        "wrapperIdentity": wrapper.get("wrapperIdentity"),
        "hostRunnerSha256": receipt["hostRunnerSha256"],
        "baselinePatchSha256": receipt["baselinePatchSha256"],
        "preparedInputIdentity": prepared_input_identity,
        "limits": EXPECTED_LIMITS,
        "rootCount": 77,
        "pilotSeeds": order[:3],
        "coldOrder": order,
        "serviceAndSensitivitySeeds": plan.get("serviceAndSensitivitySeeds"),
        "cases": frozen_cases,
    }
    output = Path(output_path)
    if output.exists():
        raise Invalid("refusing to replace an existing frozen plan output")
    output.parent.mkdir(parents=True, exist_ok=True)
    raw = json.dumps(frozen, sort_keys=True, indent=2, ensure_ascii=False,
                     allow_nan=False) + "\n"
    output.write_text(raw, encoding="utf-8", newline="")
    return frozen


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repository", type=Path)
    parser.add_argument("prepare_receipt", type=Path)
    parser.add_argument("compiled_java_export", type=Path)
    parser.add_argument("prepared_app", type=Path)
    parser.add_argument("output_plan", type=Path)
    parser.add_argument("--manifest-export-host-record", type=Path)
    parser.add_argument("--acceptance-plan", type=Path, default=PACKAGE / "acceptance-plan.json")
    args = parser.parse_args(argv)
    result = freeze(args.repository, args.acceptance_plan, args.prepare_receipt,
                    args.compiled_java_export, args.prepared_app, args.output_plan,
                    args.manifest_export_host_record)
    print("PASS: frozen 77 roots and 308 plan-4 candidate manifests from compiled Java")
    print("sourceIdentity=" + result["sourceIdentity"])
    print("plan=" + str(args.output_plan))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, Invalid, KeyError, TypeError) as error:
        print("ERROR: " + str(error), file=sys.stderr)
        raise SystemExit(2)
