"""Independent finite R12 qualification reader; never runs or replaces CircuitJS."""
import argparse
import hashlib
import json
import re
from pathlib import Path
from types import ModuleType
from decimal import Decimal

ROOT = Path(__file__).resolve().parents[3]
BASE = "bd846017be5c9f46b1fce3fc9fc90438318cac28"
PROFILE = ["-1017", "-1067", "-1022"]
DEVELOPMENT = ["-2230691771308388107", "6702341860553104274", "-8360266000248569150"]
HOLDOUT = ["5954648469308924264", "8746043109435501874", "-5914932438307118819",
           "-5623001022168249027", "3437424778755667517", "3219288467305141894",
           "-3242722890621869748", "3274142870939325193"]
REPLACEMENT_BLIND = ["6717106128883189437", "-7993534270425766476", "665993870941717295",
                     "-1740268088938250233", "-5491370319832732895", "-7785299080753913940",
                     "7817806517725629686", "-7945665765811446459"]
REPLACEMENT_BLIND_2 = ["-2157433570835955452", "1325853684799777255", "-3798151323707447735",
                       "1178227627326218914", "1467570129396149887", "-1964920481438493458",
                       "-1089711617266387266", "-4261205899415264525"]
PARTITIONS = {"profile": PROFILE, "development": DEVELOPMENT, "holdout": HOLDOUT,
              "replacement-blind": REPLACEMENT_BLIND,
              "replacement-blind-2": REPLACEMENT_BLIND_2}
EPOCHS = {"replay": "tsj-alpha/7", "dependency": "tsj-generation-dependencies-v26",
          "model": "circuitjs-source-load-model-inputs-no-transient-dump-v7",
          "layout": 25, "placement": 15, "plan": 1, "assessment": 3, "program": 2}
LIMITS = {"jobMillis": 90000, "jobWork": 640, "unitMillis": 5000, "maximumCandidates": 4}
MANIFEST_ORACLE = "tests/contracts/Rb56ManifestContractTest.java"
MANIFEST_HASH_PROTOCOL = "rb56-part-decimal-value/1"
REPLACEMENT_BLIND_PLAN = "docs/task-evidence/REL-B/rb56-medium-normal-r2/PLAN.md"
REPLACEMENT_BLIND_CAMPAIGN = "docs/task-evidence/REL-B/rb56-medium-normal-r2/campaign-plan.json"
REPLACEMENT_BLIND_RUNTIME_SCOPE = "docs/task-evidence/REL-B/rb56-medium-normal-r2/RUNTIME_SCOPE.md"
REPLACEMENT_BLIND_CAMPAIGN_SHA256 = "c0a176c477192fd4834e0ab216b7da39843a058ba4d5865687bd5fc64fdfa759"
REPLACEMENT_BLIND_2_PLAN = "docs/task-evidence/REL-B/rb56-medium-normal-r3/PLAN.md"
REPLACEMENT_BLIND_2_CAMPAIGN = "docs/task-evidence/REL-B/rb56-medium-normal-r3/campaign-plan.json"
REPLACEMENT_BLIND_2_RUNTIME_SCOPE = "docs/task-evidence/REL-B/rb56-medium-normal-r3/RUNTIME_SCOPE.md"
REPLACEMENT_BLIND_2_CAMPAIGN_SHA256 = "473ea73c8937f55c7a321ee71aff9cbc1f8c778c49668c8a33391fe42ba38c9d"
_PART_NUMERIC_VALUE = re.compile(
    r"(\[[A-Z][A-Z0-9_]*:[A-Z0-9_]+:[A-Z0-9_]+:[A-Z0-9_]+:)"
    r"(-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)(:)"
)


def need(value, message):
    if not value:
        raise ValueError(message)


def is_sha256(value):
    return type(value) is str and re.fullmatch(r"[0-9a-f]{64}", value) is not None


def oracle(name, relative, digest):
    path = ROOT / relative
    # Git's sealed logical bytes use LF; the Windows checkout uses CRLF.
    # Only that conversion is allowed. The plan separately pins physical bytes.
    logical = path.read_bytes().replace(b"\r\n", b"\n")
    need(hashlib.sha256(logical).hexdigest() == digest, "sealed oracle source changed")
    module = ModuleType(name)
    module.__file__ = str(path)
    exec(compile(logical, str(path), "exec"), module.__dict__)
    return module


FEATURES = oracle("r12_feature_math", "docs/task-evidence/U05/q60-calibration-r2/read_features.py",
                  "469ba33f119cff99d5311bc1572a72a46f4c87e456658ab7718f8a18bb4c6311")
WAITS = oracle("r12_wait_math", "docs/task-evidence/U05/q60-calibration-r6/read_waits.py",
               "6d3e93ed1dcc3a2ff544756f2c14636cd161e0095befb39ebdf096df396348dc")


def strict_json(path, maximum=16 * 1024 * 1024):
    raw = path.read_bytes()
    need(len(raw) <= maximum, "JSON exceeds declared size bound")
    def pairs(items):
        result = {}
        for key, value in items:
            need(key not in result, "duplicate JSON field")
            result[key] = value
        return result
    def constant(value):
        raise ValueError("nonfinite JSON constant")
    return json.loads(raw.decode("utf-8-sig"), object_pairs_hook=pairs,
                      parse_constant=constant), hashlib.sha256(raw).hexdigest()


def integer(value, lower, upper, label):
    need(type(value) is int and lower <= value <= upper, label)
    return value


def seed(value):
    need(type(value) is str and re.fullmatch(r"-?(0|[1-9][0-9]*)", value), "noncanonical seed")
    number = int(value)
    need(-(1 << 63) <= number < 1 << 63 and str(number) == value, "invalid signed-long seed")
    return number


def candidate(root, ordinal):
    value = (seed(root) + 0x9E3779B97F4A7C15 * ordinal) & ((1 << 64) - 1)
    return str(value - (1 << 64) if value >= 1 << 63 else value)


def _plain_decimal(token):
    need(len(token) <= 64, "numeric part value exceeds canonical decimal bound")
    value = Decimal(token)
    sign, digits, exponent = value.as_tuple()
    need(-400 <= exponent <= 400, "numeric part exponent exceeds canonical decimal bound")
    if not any(digits):
        return "0"
    digits = list(digits)
    while digits[-1] == 0:
        digits.pop()
        exponent += 1
    point = len(digits) + exponent
    # Keep an additional output bound even though token length and exponent are
    # already constrained above, so this stays safe if the accepted grammar moves.
    if abs(point) > 4096 or len(digits) + abs(exponent) > 4096:
        return token
    body = "".join(str(digit) for digit in digits)
    if point <= 0:
        body = "0." + "0" * (-point) + body
    elif point >= len(digits):
        body += "0" * (point - len(digits))
    else:
        body = body[:point] + "." + body[point:]
    return ("-" if sign else "") + body


def canonical_manifest(manifest):
    need(type(manifest) is str, "manifest text required")
    return _PART_NUMERIC_VALUE.sub(
        lambda match: match.group(1) + _plain_decimal(match.group(2)) + match.group(3), manifest)


def manifest_sha256(manifest):
    return hashlib.sha256(canonical_manifest(manifest).encode("utf-8")).hexdigest()


def binding(inputs):
    return hashlib.sha256("\n".join(
        name + "=" + inputs[name] for name in sorted(inputs)).encode("utf-8")).hexdigest()


def input_paths():
    paths = set()
    for path in (ROOT / "src").rglob("*"):
        if path.is_file() and "__pycache__" not in path.parts and path.suffix != ".pyc":
            paths.add(path)
    for path in (ROOT / "war").rglob("*"):
        relative = path.relative_to(ROOT / "war")
        if path.is_file() and relative.parts[0] not in ("circuitjs1", "WEB-INF"):
            paths.add(path)
    for path in (ROOT / "scripts").rglob("*"):
        if path.is_file() and path.suffix in (".ps1", ".psm1", ".py", ".js", ".cmd", ".bat"):
            paths.add(path)
    paths.update((ROOT / ".tools/gwt-2.7.0").glob("*.jar"))
    paths.update(Path(__file__).parent.glob("*.py"))
    paths.update(ROOT / name for name in (
        "docs/task-evidence/U05/q60-calibration-r2/read_features.py",
        "docs/task-evidence/U05/q60-calibration-r6/read_waits.py",
        "docs/task-evidence/REL-B/rb56-medium-normal-r1/PLAN.md",
        "docs/task-evidence/REL-B/rb56-medium-qualification-r1/campaign-plan.json",
        REPLACEMENT_BLIND_PLAN, REPLACEMENT_BLIND_CAMPAIGN,
        REPLACEMENT_BLIND_2_PLAN, REPLACEMENT_BLIND_2_CAMPAIGN,
        REPLACEMENT_BLIND_2_RUNTIME_SCOPE,
        MANIFEST_ORACLE, "tests/browser/compiled_attribute_acceptance.py",
        "tests/browser/quickplay_procedural_acceptance.py"))
    runtime_scope = ROOT / REPLACEMENT_BLIND_RUNTIME_SCOPE
    if runtime_scope.is_file():
        paths.add(runtime_scope)
    return {path.relative_to(ROOT).as_posix(): path for path in paths}


def output_file_records(files):
    need(type(files) is list and len(files) > 0, "complete output inventory required")
    paths = []
    seen = set()
    digests = {}
    for row in files:
        need(type(row) is dict and set(row) == {"Path", "Sha256", "Bytes"},
             "output inventory row fields differ")
        name = row.get("Path")
        digest = row.get("Sha256")
        size = row.get("Bytes")
        need(type(name) is str and name and "\\" not in name and not name.startswith("/") and
             all(part not in ("", ".", "..") for part in name.split("/")) and
             ":" not in name and "\x00" not in name,
             "unsafe output inventory path")
        try:
            name.encode("ascii")
        except UnicodeEncodeError:
            need(False, "output inventory paths must be ASCII")
        key = name.upper()
        need(key not in seen, "duplicate output inventory path")
        seen.add(key)
        need(is_sha256(digest),
             "invalid output inventory digest")
        integer(size, 0, (1 << 63) - 1, "invalid output inventory byte count")
        paths.append(name)
        digests[name] = digest
    return [name + "=" + digests[name]
            for name in sorted(paths, key=lambda item: item.replace("/", "\\").upper())]


def canonical_inventory(files):
    output_file_records(files)
    return sorted(files, key=lambda row: row["Path"].replace("/", "\\").upper())


def inventory_digest(files):
    return hashlib.sha256("\n".join(output_file_records(files)).encode("utf-8")).hexdigest()


def validate_manifest_plan(manifests):
    need(type(manifests) is dict and set(manifests) == set(PARTITIONS),
         "complete frozen request/candidate manifest census required")
    for partition, roots in PARTITIONS.items():
        rows = manifests[partition]
        need(type(rows) is list and len(rows) == len(roots),
             "manifest roots/order differ: " + partition)
        candidate_count = 1 if partition == "profile" else LIMITS["maximumCandidates"]
        for ordinal, (row, root) in enumerate(zip(rows, roots)):
            need(type(row) is dict and set(row) == {"root", "requestSha256", "candidateSha256"} and
                 row.get("root") == root and
                 is_sha256(row.get("requestSha256")) and
                 type(row.get("candidateSha256")) is list and
                 len(row["candidateSha256"]) == candidate_count and
                 all(is_sha256(digest) for digest in row["candidateSha256"]),
                 "manifest digest/order differs: " + partition + "/" + str(ordinal))


def validate_plan(plan, verify_files=True):
    need(type(plan) is dict and set(plan) == {
        "schema", "baseCommit", "partitions", "profilePackages", "epochs", "budgets", "manifests",
        "manifestHashProtocol", "replacementBlindCampaignSha256",
        "replacementBlind2CampaignSha256", "manifestOracleOutputSha256",
        "inputs", "sourceBinding", "developer", "player", "readerSha256"},
        "plan fields differ")
    need(plan.get("schema") == "tsj-r12-medium-plan/5" and plan.get("baseCommit") == BASE,
         "plan identity differs")
    need(plan.get("partitions") == PARTITIONS and plan.get("profilePackages") == [40, 55, 60],
         "frozen roots/order/package boundaries differ")
    need(plan.get("epochs") == EPOCHS and plan.get("budgets") == LIMITS, "epoch or budget differs")
    need(all(type(plan["epochs"][key]) is type(value) for key, value in EPOCHS.items()),
         "epoch value types differ")
    need(is_sha256(plan.get("manifestOracleOutputSha256")),
         "raw native manifest oracle output digest missing")
    need(plan.get("manifestHashProtocol") == MANIFEST_HASH_PROTOCOL,
         "manifest numeric hash protocol differs")
    need(plan.get("replacementBlindCampaignSha256") == REPLACEMENT_BLIND_CAMPAIGN_SHA256,
         "replacement-blind campaign digest differs")
    need(plan.get("replacementBlind2CampaignSha256") == REPLACEMENT_BLIND_2_CAMPAIGN_SHA256,
         "replacement-blind-2 campaign digest differs")
    inputs = plan.get("inputs")
    need(type(inputs) is dict and len(inputs) >= 1000, "complete source input manifest required")
    need(set(inputs) == set(input_paths()), "source manifest omitted or invented consumed inputs")
    need(binding(inputs) == plan.get("sourceBinding"), "source binding differs")
    need(MANIFEST_ORACLE in inputs, "native manifest identity oracle is not source-bound")
    need(inputs.get(REPLACEMENT_BLIND_CAMPAIGN) == REPLACEMENT_BLIND_CAMPAIGN_SHA256 and
         REPLACEMENT_BLIND_PLAN in inputs and
         inputs.get(REPLACEMENT_BLIND_2_CAMPAIGN) == REPLACEMENT_BLIND_2_CAMPAIGN_SHA256 and
         REPLACEMENT_BLIND_2_PLAN in inputs and REPLACEMENT_BLIND_2_RUNTIME_SCOPE in inputs,
         "both replacement-blind campaigns and the r3 scope are pinned in the source binding")
    for name, digest in inputs.items():
        need(type(name) is str and "\\" not in name and not Path(name).is_absolute() and
             all(part not in ("", ".", "..") for part in name.split("/")), "unsafe source path")
        need(is_sha256(digest), "invalid input digest")
        if verify_files:
            path = (ROOT / name).resolve()
            need(path.is_relative_to(ROOT.resolve()), "source input escaped repository")
            need(path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == digest,
                 "consumed source input changed: " + name)
    for variant in ("developer", "player"):
        artifact = plan.get(variant, {})
        need(type(artifact.get("builds")) is list and len(artifact["builds"]) == 5 and
             len(set(artifact["builds"])) == 5 and
             all(re.fullmatch(r"[A-F0-9]{32}", item) for item in artifact["builds"]),
             "five actual variant permutations required")
        for field in ("inputDigest", "outputDigest", "receiptSha256", "sizeSha256", "inventorySha256"):
            need(is_sha256(artifact.get(field)), "artifact digest missing")
        integer(artifact.get("inputFileCount"), 1, 100000, "artifact input count missing")
        integer(artifact.get("outputFileCount"), 1, 100000, "artifact output count missing")
        records = output_file_records(artifact.get("files"))
        need(artifact["files"] == canonical_inventory(artifact["files"]),
             "frozen output inventory is not canonically ordered")
        need(len(records) == artifact["outputFileCount"] and
             inventory_digest(artifact["files"]) == artifact["outputDigest"],
             "full output inventory differs from receipt aggregate")
        caches = [row["Path"] for row in artifact["files"] if row["Path"].endswith(".cache.js")]
        need(len(caches) == 5 and [name[:-9] for name in caches] == artifact["builds"],
             "cache census differs from full output inventory")
    validate_manifest_plan(plan.get("manifests"))
    need(is_sha256(plan.get("readerSha256")) and
         plan.get("readerSha256") == hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
         "reader bytes differ from frozen plan")


def check_capture(envelope, plan, partition, ordinal):
    need(set(envelope) == {"schema", "partition", "ordinal", "build", "sourceBinding", "report"},
         "capture fields differ")
    need(envelope["schema"] == "tsj-r12-medium-capture/1" and
         envelope["partition"] == partition and type(envelope["ordinal"]) is int and
         envelope["ordinal"] == ordinal, "capture partition/order differs")
    need(envelope["sourceBinding"] == plan["sourceBinding"] and
         envelope["build"] in plan["developer"]["builds"], "capture source/build binding differs")
    report = envelope["report"]
    need(type(report) is dict, "report object required")
    root = PARTITIONS[partition][ordinal]
    normal = partition != "profile"
    need(report.get("protocol") == ("TSJ-RB56-NORMAL-QUALIFICATION-1" if normal else
                                   "TSJ-RB56-QUALIFICATION-1"), "report protocol differs")
    need(report.get("scope") == ("NORMAL_MEDIUM_COLD_POPULATION" if normal else
                                "PRIVATE_SHARED_WORKBENCH"), "report scope differs")
    need(report.get("status") == "PASS" and report.get("phase") == "RESTORED" and
         report.get("jobOutcome") == "PASS", "operation did not finish successfully")
    need(report.get("family") == "RB56_CONTROL" and report.get("rootSeed") == root and
         report.get("normalRequest") is normal and report.get("normalCatalogEnabled") is True,
         "family/root/normal provenance differs")
    for field, expected in (("sourceEpoch", EPOCHS["dependency"]), ("modelEpoch", EPOCHS["model"]),
                            ("replayEpoch", EPOCHS["replay"]), ("layoutVersion", EPOCHS["layout"]),
                            ("planVersion", EPOCHS["plan"])):
        need(type(report.get(field)) is type(expected) and report[field] == expected,
             "current report epoch differs: " + field)
    for flag in FEATURES.REQUIRED_TRUE:
        need(report.get(flag) is True, "required proof/cleanup flag false: " + flag)
    for flag in ("cleanupPending", "forcedAfterPublish", "ordinaryCustomerRetestPassed", "proofWarmReuse"):
        need(report.get(flag) is False, "unexpected failure/retention/warm proof: " + flag)
    need("failure" not in report and "failurePhase" not in report, "failure preserved inside PASS")
    for key, expected in (("maxJobMillis", 90000), ("maxJobSteps", 640), ("maxUnitMillis", 5000),
                          ("generationStages", 6), ("hypotheses", 5)):
        need(type(report.get(key)) is int and report[key] == expected, "bound/census differs: " + key)
    elapsed = integer(report.get("generationMillis"), 0, 90000, "generation time outside cap")
    work = integer(report.get("generationWork"), 1, 640, "generation work outside cap")
    max_manual_unit = integer(report.get("maxManualUnitMillis"), 0, 5000,
                              "operation exceeded unchanged cap")
    manual_unit_max = integer(report.get("manualUnitMaxMillis"), 0, 5000,
                              "terminal operation exceeded cap")
    need(max_manual_unit == manual_unit_max, "manual operation maxima differ")
    integer(report.get("jobWork"), work, 640, "terminal job work differs")
    integer(report.get("jobMillis"), elapsed, 90000, "terminal job time outside cap")
    operation = integer(report.get("operationMillis"), 0, 1_000_000, "operation time missing")
    cleanup = integer(report.get("cleanupMillis"), 0, 1_000_000, "cleanup time missing")
    wall = integer(report.get("wallMillis"), 0, 2_000_000, "wall time missing")
    need(operation <= wall and cleanup <= wall and abs(operation + cleanup - wall) <= 10,
         "operation/cleanup wall accounting differs")
    for field in ("generationReceiptDigest", "diagnosticContextDigest", "diagnosticProgramDigest"):
        need(is_sha256(report.get(field)), "proof digest missing")
    packages = integer(report.get("packages"), 40, 60, "RB56 package bound differs")
    if not normal:
        need(packages == [40, 55, 60][ordinal], "curated package boundary differs")
    observations = report.get("calibrationObservations")
    proof_rows = report.get("proofRows")
    need(type(observations) is list and len(observations) == 5 and
         type(proof_rows) is list and len(proof_rows) == 5,
         "five ordered proof and calibration rows required")
    hypothesis_digests = set()
    first_seen_repair_classes = {}
    for index, (proof_row, observation) in enumerate(zip(proof_rows, observations)):
        need(type(proof_row) is dict and type(observation) is dict and
             set(observation) == {"ordinal", "repairClass", "hypothesisDigest",
                                  "repairSemanticsDigest", "samples"} and
             proof_row.get("ordinal") == index and observation.get("ordinal") == index,
             "proof row and calibration observation ordinal correspondence differs")
        hypothesis_digest = proof_row.get("hypothesisDigest")
        proof_semantics = proof_row.get("repairSemanticsDigest")
        need(is_sha256(hypothesis_digest) and hypothesis_digest not in hypothesis_digests and
             is_sha256(proof_semantics),
             "proof hypothesis identity or repair semantics digest missing/duplicated")
        hypothesis_digests.add(hypothesis_digest)
        expected_class = first_seen_repair_classes.setdefault(
            proof_semantics, len(first_seen_repair_classes))
        need(observation.get("hypothesisDigest") == hypothesis_digest and
             observation.get("repairSemanticsDigest") == proof_semantics and
             type(observation.get("repairClass")) is int and
             observation["repairClass"] == expected_class,
             "observation identity or first-seen repair class differs from proof row")
    feature_report = dict(report)
    feature_report["calibrationObservations"] = [
        {key: row[key] for key in ("ordinal", "repairClass", "samples")}
        for row in observations
    ]
    rows, inventory = FEATURES._validate_observations(feature_report)
    analysis = FEATURES._analyze(rows)
    facts = FEATURES._parse_features(report.get("difficultyFeatures"), packages, analysis)
    need(report.get("difficultyFeatureStatus") == "UNCALIBRATED" and
         type(report.get("difficultyFeatureVersion")) is int and
         report["difficultyFeatureVersion"] == 1, "calibration feature schema differs")
    need(report.get("normalProfileAssessmentVersion") == 3 and
         report.get("normalProfileValidated") == "MEDIUM" and
         report.get("normalPublicationGranted") is normal, "MEDIUM admission provenance differs")
    prefix = "tsj-difficulty-calibration/1;status=UNCALIBRATED"
    need(report.get("normalProfileAssessment") ==
         "difficulty/3;profile=MEDIUM" + report["difficultyFeatures"][len(prefix):],
         "assessment differs from independent feature aggregate")
    need(analysis["readings"] >= 2 and analysis["repairClasses"] >= 2 and facts["owners"] >= 2 and
         (facts["temporal"] > 0 or (facts["parallel"] == "true" and facts["modes"] >= 2)),
         "unchanged MEDIUM predicate not satisfied")
    waits = WAITS.rows(report)
    need(facts["temporal"] == len(report["calibrationTemporalWaits"][0]), "wait/feature census differs")
    need(report.get("solverSamples") == 5 * len(inventory["sampleIds"]), "solver sample census differs")
    accepted_seed = str(seed(report.get("seed")))
    for index, row in enumerate(proof_rows):
        need(type(row) is dict and set(row) == {"ordinal", "family", "seed", "repairReachable",
             "customerRetestPassed", "stateIsolated", "unaffectedFunctionRetested", "result",
             "rejection", "repairActions", "sampleIds", "hypothesisDigest", "repairSemanticsDigest"} and
             type(row.get("ordinal")) is int and row["ordinal"] == index and
             row.get("family") == "RB56_CONTROL" and row.get("seed") == accepted_seed,
             "proof owner/row order differs")
        for flag in ("repairReachable", "customerRetestPassed", "stateIsolated", "unaffectedFunctionRetested"):
            need(row.get(flag) is True, "incomplete production proof row")
        need(row.get("result") == "PASS" and row.get("rejection") == "NONE" and
             type(row.get("repairActions")) is list and len(row["repairActions"]) > 0 and
             all(type(action) is str and action for action in row["repairActions"]) and
             len(set(row["repairActions"])) == len(row["repairActions"]) and
             row.get("sampleIds") == inventory["sampleIds"], "repair or complete observation proof missing")
    attempts = report.get("candidateAttempts")
    accepted_ordinal = integer(report.get("acceptedOrdinal"), 0, 3, "accepted candidate missing") if normal else 0
    need(type(attempts) is list and len(attempts) == accepted_ordinal + 1, "candidate attempt census differs")
    need(report.get("proofCachePolicy") == ("BYPASS" if normal else "CLEARED_MISS"),
         "cold cache policy differs")
    for field, expected in (("proofCacheHitsDelta", 0), ("proofCacheMissesDelta", 0 if normal else 1),
                            ("planCacheHitsDelta", 0), ("planCacheMissesDelta", len(attempts))):
        integer(report.get(field), expected, expected, "cold cache counter differs: " + field)
    request_manifest = report.get("requestManifest")
    need(type(request_manifest) is str and len(request_manifest) > 0, "request manifest missing")
    manifest_pins = plan["manifests"][partition][ordinal]
    need(manifest_sha256(request_manifest) == manifest_pins["requestSha256"],
         "canonical request manifest differs from native oracle")
    if normal:
        need(request_manifest.startswith("tsj-generation-request/5;advanced-control@2;native;") and
             f";replay=tsj-alpha/7/MEDIUM/RB56_CONTROL/{root};quickPlay=false;layout=25;" in request_manifest and
             request_manifest.endswith(";search=true;difficulty=MEDIUM@1;assessment=3"),
             "normal request manifest differs")
    else:
        need(request_manifest.startswith("tsj-generation-request/4;native;qualification-only;"),
             "private request manifest differs")
    for index, attempt in enumerate(attempts):
        expected_seed = candidate(root, index) if normal else root
        need(type(attempt) is dict and set(attempt) == {"ordinal", "seed", "manifest", "stage",
             "outcome", "workUnits", "proofUnits"} and
             type(attempt.get("ordinal")) is int and attempt["ordinal"] == index and
             attempt.get("seed") == expected_seed and
             attempt.get("outcome") == ("PASS" if index == accepted_ordinal else "EXPECTED_REJECTION"),
             "candidate order/result/seed differs")
        candidate_work = integer(attempt.get("workUnits"), 1, 640, "candidate work missing")
        attempt_proof_work = integer(attempt.get("proofUnits"), 0, 640, "candidate proof work missing")
        need(attempt_proof_work <= candidate_work, "candidate proof work exceeds candidate work")
        need(attempt.get("stage") in ("RESOLVE", "HEALTHY", "PHYSICAL", "HYPOTHESES", "SYMPTOM", "PUBLISH") and
             (index != accepted_ordinal or attempt["stage"] == "PUBLISH"), "candidate terminal stage differs")
        manifest = attempt.get("manifest", "")
        need(manifest_sha256(manifest) == manifest_pins["candidateSha256"][index],
             "canonical candidate manifest differs from native oracle")
        if normal:
            need(manifest.startswith(f"candidate=0{index};tsj-generation-request/5;advanced-control@2;native;") and
                 f";replay=tsj-alpha/7/MEDIUM/RB56_CONTROL/{expected_seed};quickPlay=false;layout=25;" in manifest and
                 manifest.endswith(";search=false;difficulty=MEDIUM@1;assessment=3"),
                 "exact candidate manifest differs")
        else:
            need(manifest == request_manifest, "private candidate differs from exact request")
    need(sum(attempt["workUnits"] for attempt in attempts) == work, "candidate/global work accounting differs")
    proof_work = integer(report.get("proofWorkUnits"), 1, 640, "accepted proof work missing")
    required_proof_work = integer(report.get("requiredProofWorkUnits"), proof_work, 640,
                                  "declared proof work differs")
    stage_work = report.get("jobStageWork")
    need(type(stage_work) is dict and set(stage_work) ==
         {"RESOLVE", "HEALTHY", "PHYSICAL", "HYPOTHESES", "SYMPTOM", "PUBLISH"},
         "complete stage work census required")
    for count in stage_work.values():
        integer(count, 0, 640, "invalid stage work")
    need(sum(stage_work.values()) == report["jobWork"] and
         stage_work["HYPOTHESES"] == sum(attempt["proofUnits"] for attempt in attempts) and
         attempts[-1]["proofUnits"] == proof_work and proof_work <= required_proof_work and
         stage_work["HYPOTHESES"] <= report["jobWork"],
         "accepted/aggregate proof phase work differs")
    need(report.get("acceptedManifest") == attempts[-1]["manifest"] and
         accepted_seed == (candidate(root, accepted_ordinal) if normal else root), "accepted lineage differs")
    if normal:
        need(report.get("publicReplay") == f"tsj-alpha/7/MEDIUM/RB56_CONTROL/{accepted_seed}",
             "accepted public replay differs")
    else:
        need("publicReplay" not in report and "acceptedOrdinal" not in report, "private scope leaked public identity")
    return {"root": root, "seed": accepted_seed, "packages": packages, "readings": analysis["readings"],
            "repairClasses": analysis["repairClasses"], "generationMillis": elapsed, "generationWork": work,
            "maxUnitMillis": report["maxManualUnitMillis"], "operationMillis": operation, "cleanupMillis": cleanup,
            "attempts": len(attempts), "naturalWaitSeconds": waits}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", type=Path, required=True)
    parser.add_argument("--plan-sha256", required=True)
    parser.add_argument("--partition", choices=PARTITIONS, required=True)
    parser.add_argument("--captures", type=Path, nargs="+", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    plan, plan_hash = strict_json(args.plan, 4 * 1024 * 1024)
    need(is_sha256(args.plan_sha256) and
         plan_hash == args.plan_sha256, "frozen plan SHA-256 differs from external pin")
    need(not args.output.exists() and not args.output.is_symlink(),
         "refusing to replace an existing partition result")
    validate_plan(plan)
    need(len(args.captures) == len(PARTITIONS[args.partition]), "complete frozen partition required")
    cases = []
    for ordinal, path in enumerate(args.captures):
        capture, digest = strict_json(path)
        case = check_capture(capture, plan, args.partition, ordinal)
        case["captureSha256"] = digest
        cases.append(case)
    validate_plan(plan)
    with args.output.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps({"status": "PASS_FINITE_R12_PARTITION", "partition": args.partition,
            "planSha256": plan_hash, "sourceBinding": plan["sourceBinding"], "cases": cases,
            "normalReleaseAcceptance": False}, indent=2) + "\n")
    print("PASS: complete frozen R12 partition; independent observations, lineage, caps and cleanup")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, TypeError, IndexError) as failure:
        print("FAIL:", failure)
        raise SystemExit(2)
