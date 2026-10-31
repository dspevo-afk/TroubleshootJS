"""Reader falsifiers only: synthetic observations are not simulator evidence."""
import copy
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
import freeze_plan
import read_qualification as reader


def artifact():
    files = [{"Path": letter * 32 + ".cache.js", "Sha256": digest * 64, "Bytes": 10 + index}
             for index, (letter, digest) in enumerate(zip("ABCDE", "abcde"))]
    return {"builds": [letter * 32 for letter in "ABCDE"],
            "inputDigest": "b" * 64, "inputFileCount": 1001,
            "outputDigest": reader.inventory_digest(files), "outputFileCount": len(files),
            "receiptSha256": "d" * 64, "sizeSha256": "e" * 64,
            "inventorySha256": "f" * 64, "files": files}


def normal_manifest(seed, search):
    return ("tsj-generation-request/5;advanced-control@2;native;fixture;"
            f"replay=tsj-alpha/7/MEDIUM/RB56_CONTROL/{seed};quickPlay=false;layout=25;"
            "plan=fixture;"
            "parts=[R1:RESISTOR:PASSIVE:RES_10K:0.0:channel:input|pads]"
            "[C1:CAPACITOR:PASSIVE:CAP_1UF:4.7E-5:channel:input|pads]"
            "[D1:DIODE:SEMICONDUCTOR:DIODE_STD:1e-7:channel:input|pads];"
            f"search={search};difficulty=MEDIUM@1;assessment=3")


def manifest_plan():
    result = {}
    for partition, roots in reader.PARTITIONS.items():
        rows = []
        for root in roots:
            if partition == "profile":
                request = "tsj-generation-request/4;native;qualification-only;fixture;seed=" + root
                candidates = [request]
            else:
                request = normal_manifest(root, "true")
                candidates = [f"candidate=0{index};" + normal_manifest(
                    reader.candidate(root, index), "false") for index in range(4)]
            rows.append({"root": root,
                "requestSha256": reader.manifest_sha256(request),
                "candidateSha256": [reader.manifest_sha256(value)
                                    for value in candidates]})
        result[partition] = rows
    return result


def capture(normal=False, partition=None, partition_ordinal=0):
    partition = partition or ("development" if normal else "profile")
    root = reader.PARTITIONS[partition][partition_ordinal]
    accepted_ordinal = 1 if normal else 0
    accepted = reader.candidate(root, accepted_ordinal) if normal else root
    report = reader.FEATURES._synthetic_report(accepted)
    for row in report["calibrationObservations"]:
        for column, sample in enumerate(row["samples"]):
            value = float(row["ordinal"] // 3 if column == 0 else row["ordinal"] % 3)
            sample.update(value=value, tolerance=reader.FEATURES._expected_tolerance(value))
    if normal:
        request = normal_manifest(root, "true")
        attempts = []
        for index in range(accepted_ordinal + 1):
            candidate_seed = reader.candidate(root, index)
            attempts.append({"ordinal": index, "seed": candidate_seed,
                "manifest": f"candidate=0{index};" + normal_manifest(candidate_seed, "false"),
                "stage": "PUBLISH" if index == accepted_ordinal else "HYPOTHESES",
                "outcome": "PASS" if index == accepted_ordinal else "EXPECTED_REJECTION",
                "workUnits": 298 if index == accepted_ordinal else 2,
                "proofUnits": 200 if index == accepted_ordinal else 1})
    else:
        request = "tsj-generation-request/4;native;qualification-only;fixture;seed=" + root
        attempts = [{"ordinal": 0, "seed": root, "manifest": request, "stage": "PUBLISH",
                     "outcome": "PASS", "workUnits": 300, "proofUnits": 200}]
    observations = report["calibrationObservations"]
    proof_rows = []
    for index, observation in enumerate(observations):
        hypothesis_digest = hashlib.sha256(f"hypothesis-{index}".encode()).hexdigest()
        semantics_digest = hashlib.sha256(f"repair-semantics-{index}".encode()).hexdigest()
        observation.update(hypothesisDigest=hypothesis_digest,
                           repairSemanticsDigest=semantics_digest)
        proof_rows.append({"ordinal": index, "family": "RB56_CONTROL", "seed": accepted,
            "repairReachable": True, "customerRetestPassed": True, "stateIsolated": True,
            "unaffectedFunctionRetested": True, "result": "PASS", "rejection": "NONE",
            # These generic executed operations do not encode the full repair semantics.
            "repairActions": ["REMOVE", "CATALOG_INSTALL"],
            "sampleIds": [sample["id"] for sample in observation["samples"]],
            "hypothesisDigest": hypothesis_digest, "repairSemanticsDigest": semantics_digest})
    rows, inventory = reader.FEATURES._validate_observations({
        "calibrationObservationFormat": 1,
        "calibrationObservations": [{key: row[key] for key in ("ordinal", "repairClass", "samples")}
                                     for row in observations]})
    analysis = reader.FEATURES._analyze(rows)
    packages = 47 if normal else 40
    features = reader.FEATURES._feature_string(packages, analysis).replace(";temporal=3;", ";temporal=4;")
    report.update(protocol="TSJ-RB56-NORMAL-QUALIFICATION-1" if normal else "TSJ-RB56-QUALIFICATION-1",
        scope="NORMAL_MEDIUM_COLD_POPULATION" if normal else "PRIVATE_SHARED_WORKBENCH",
        phase="RESTORED", rootSeed=root, normalRequest=normal, normalCatalogEnabled=True,
        sourceEpoch=reader.EPOCHS["dependency"], modelEpoch=reader.EPOCHS["model"],
        replayEpoch=reader.EPOCHS["replay"], layoutVersion=25, packages=packages,
        requestManifest=request, acceptedManifest=attempts[-1]["manifest"], candidateAttempts=attempts,
        difficultyFeatures=features, normalProfileAssessment=features.replace(
            "tsj-difficulty-calibration/1;status=UNCALIBRATED", "difficulty/3;profile=MEDIUM"),
        normalProfileAssessmentVersion=3, normalProfileValidated="MEDIUM", normalPublicationGranted=normal,
        diagnosticProgramVersion=2, calibrationTemporalWaits=[
            ["HIGH_INPUT_SETTLED@0.0", "SERVICE_DECAY_1@1.0", "SERVICE_DECAY_2@0.4", "SERVICE_DECAY_3@0.0"]
            for _ in range(5)], hypotheses=5, proofWarmReuse=False,
        proofCachePolicy="BYPASS" if normal else "CLEARED_MISS", proofCacheHitsDelta=0,
        proofCacheMissesDelta=0 if normal else 1, planCacheHitsDelta=0, planCacheMissesDelta=len(attempts),
        jobWork=300, jobMillis=1003, operationMillis=1010, cleanupMillis=15, wallMillis=1025,
        proofWorkUnits=200, requiredProofWorkUnits=220,
        jobStageWork={"RESOLVE": 2 if normal else 1, "HEALTHY": 46, "PHYSICAL": 49 if normal else 51,
                      "HYPOTHESES": 201 if normal else 200, "SYMPTOM": 1, "PUBLISH": 1},
        generationReceiptDigest="a" * 64, diagnosticContextDigest="b" * 64, diagnosticProgramDigest="c" * 64,
        proofRows=proof_rows)
    report["solverSamples"] = 5 * len(inventory["sampleIds"])
    if normal:
        report.update(acceptedOrdinal=accepted_ordinal,
                      publicReplay=f"tsj-alpha/7/MEDIUM/RB56_CONTROL/{accepted}")
    plan = {"sourceBinding": "d" * 64, "developer": artifact(), "manifests": manifest_plan()}
    return {"schema": "tsj-r12-medium-capture/1", "partition": partition,
            "ordinal": partition_ordinal,
            "build": "A" * 32, "sourceBinding": plan["sourceBinding"], "report": report}, plan


def frozen_plan():
    inputs = {name: hashlib.sha256(path.read_bytes()).hexdigest()
              for name, path in reader.input_paths().items()}
    return {"schema": "tsj-r12-medium-plan/5", "baseCommit": reader.BASE,
        "partitions": reader.PARTITIONS, "profilePackages": [40, 55, 60], "epochs": reader.EPOCHS,
        "budgets": reader.LIMITS, "manifests": manifest_plan(),
        "manifestHashProtocol": reader.MANIFEST_HASH_PROTOCOL,
        "replacementBlindCampaignSha256": reader.REPLACEMENT_BLIND_CAMPAIGN_SHA256,
        "replacementBlind2CampaignSha256": reader.REPLACEMENT_BLIND_2_CAMPAIGN_SHA256,
        "manifestOracleOutputSha256": "c" * 64, "inputs": inputs,
        "sourceBinding": reader.binding(inputs), "developer": artifact(), "player": artifact(),
        "readerSha256": hashlib.sha256(Path(reader.__file__).read_bytes()).hexdigest()}


class ReaderContracts(unittest.TestCase):
    def test_literal_signed_long_vectors(self):
        self.assertEqual([reader.candidate("0", i) for i in range(4)],
            ["0", "-7046029254386353131", "4354685564936845354", "-2691343689449507777"])
        self.assertEqual([reader.candidate("9223372036854775807", i) for i in range(4)],
            ["9223372036854775807", "2177342782468422676", "-4868686471917930455", "6532028347405268030"])

    def test_cross_runtime_part_decimal_hash_protocol(self):
        equivalent = (
            ("[R1:RESISTOR:PASSIVE:RES_10K:0.0:channel:input|pads]",
             "[R1:RESISTOR:PASSIVE:RES_10K:0:channel:input|pads]"),
            ("[C1:CAPACITOR:PASSIVE:CAP_1UF:4.7E-5:channel:input|pads]",
             "[C1:CAPACITOR:PASSIVE:CAP_1UF:0.000047:channel:input|pads]"),
            ("[D1:DIODE:SEMICONDUCTOR:DIODE_STD:1e-7:channel:input|pads]",
             "[D1:DIODE:SEMICONDUCTOR:DIODE_STD:0.0000001:channel:input|pads]"),
            ("[R2:RESISTOR:PASSIVE:RES_10K:-0.0:channel:input|pads]",
             "[R2:RESISTOR:PASSIVE:RES_10K:0:channel:input|pads]"),
            ("[R3:RESISTOR:PASSIVE:RES_10K:123456789012345678901234567890.1200:channel:input|pads]",
             "[R3:RESISTOR:PASSIVE:RES_10K:123456789012345678901234567890.12:channel:input|pads]"),
        )
        for native, gwt in equivalent:
            self.assertEqual(reader.canonical_manifest(native), reader.canonical_manifest(gwt))
            self.assertEqual(reader.manifest_sha256(native), reader.manifest_sha256(gwt))
        self.assertEqual(reader.canonical_manifest(equivalent[1][0]),
                         "[C1:CAPACITOR:PASSIVE:CAP_1UF:0.000047:channel:input|pads]")

        native = "request;part=" + equivalent[1][0] + ";sensorCommand=0.0;value=4.7E-5"
        altered = "request;part=" + equivalent[1][1] + ";sensorCommand=0;value=0.000047"
        self.assertNotEqual(reader.manifest_sha256(native), reader.manifest_sha256(altered))
        for invalid in (
            "[C1:CAPACITOR:PASSIVE:CAP_1UF:04.7E-5:channel:input|pads]",
            "[C1:CAPACITOR:4.7E-5:CAP_1UF:4.7E-5:channel:input|pads]",
            "[C1:CAPACITOR:PASSIVE:CAP_1UF:NaN:channel:input|pads]",
        ):
            self.assertEqual(reader.canonical_manifest(invalid), invalid)
        for oversized in (
            "[C1:CAPACITOR:PASSIVE:CAP_1UF:1e401:channel:input|pads]",
            "[C1:CAPACITOR:PASSIVE:CAP_1UF:1e-401:channel:input|pads]",
            "[C1:CAPACITOR:PASSIVE:CAP_1UF:" + "1" * 65 + ":channel:input|pads]",
        ):
            with self.assertRaises(ValueError):
                reader.canonical_manifest(oversized)
        self.assertEqual(reader.MANIFEST_HASH_PROTOCOL, "rb56-part-decimal-value/1")

    def test_positive_and_mutually_exclusive_provenance(self):
        cases = [capture(False)] + [capture(True, partition) for partition in
                                    ("development", "holdout", "replacement-blind",
                                     "replacement-blind-2")]
        for item, plan in cases:
            result = reader.check_capture(item, plan, item["partition"], 0)
            self.assertEqual(result["readings"], 2)
            self.assertEqual(result["repairClasses"], 5)
            if item["partition"] != "profile":
                self.assertEqual(item["report"]["proofRows"][0]["repairActions"],
                                 item["report"]["proofRows"][1]["repairActions"])
            with self.assertRaises(ValueError):
                reader.check_capture(item, plan,
                                     "profile" if item["partition"] != "profile" else "replacement-blind", 0)

    def test_finite_partition_roots_and_replacement_blind_identity(self):
        self.assertEqual(list(reader.PARTITIONS),
                         ["profile", "development", "holdout", "replacement-blind",
                          "replacement-blind-2"])
        self.assertEqual(reader.PARTITIONS["profile"], ["-1017", "-1067", "-1022"])
        self.assertEqual(reader.PARTITIONS["development"], [
            "-2230691771308388107", "6702341860553104274", "-8360266000248569150"])
        self.assertEqual(reader.PARTITIONS["holdout"], [
            "5954648469308924264", "8746043109435501874", "-5914932438307118819",
            "-5623001022168249027", "3437424778755667517", "3219288467305141894",
            "-3242722890621869748", "3274142870939325193"])
        self.assertEqual(reader.PARTITIONS["replacement-blind"], [
            "6717106128883189437", "-7993534270425766476", "665993870941717295",
            "-1740268088938250233", "-5491370319832732895", "-7785299080753913940",
            "7817806517725629686", "-7945665765811446459"])
        self.assertEqual(reader.PARTITIONS["replacement-blind-2"], [
            "-2157433570835955452", "1325853684799777255", "-3798151323707447735",
            "1178227627326218914", "1467570129396149887", "-1964920481438493458",
            "-1089711617266387266", "-4261205899415264525"])
        all_roots = [root for roots in reader.PARTITIONS.values() for root in roots]
        self.assertEqual(len(all_roots), 30)
        self.assertEqual(len(set(all_roots)), 30)
        self.assertTrue(all(str(reader.seed(root)) == root for root in all_roots))

        item, plan = capture(True, "replacement-blind", 7)
        self.assertEqual(item["report"]["rootSeed"], reader.REPLACEMENT_BLIND[7])
        self.assertEqual(reader.check_capture(item, plan, "replacement-blind", 7)["seed"],
                         reader.candidate(reader.REPLACEMENT_BLIND[7], 1))
        item, plan = capture(True, "replacement-blind-2", 7)
        self.assertEqual(item["report"]["rootSeed"], reader.REPLACEMENT_BLIND_2[7])
        self.assertEqual(reader.check_capture(item, plan, "replacement-blind-2", 7)["seed"],
                         reader.candidate(reader.REPLACEMENT_BLIND_2[7], 1))
        for alter in (
            lambda report: report.update(rootSeed=reader.REPLACEMENT_BLIND[0]),
            lambda report: report.update(publicReplay="tsj-alpha/7/MEDIUM/RB56_CONTROL/1"),
            lambda report: report["candidateAttempts"][0].update(seed=reader.REPLACEMENT_BLIND[0]),
        ):
            bad = copy.deepcopy(item)
            alter(bad["report"])
            with self.assertRaises(ValueError):
                reader.check_capture(bad, plan, "replacement-blind", 7)

    def test_adversarial_report_mutations(self):
        cases = [capture(False), capture(True, "development"),
                 capture(True, "replacement-blind"), capture(True, "replacement-blind-2")]
        for item, plan in cases:
            normal = item["partition"] != "profile"
            mutations = {
                "status": "FAIL", "jobOutcome": "RUNNING", "phase": "CLEANUP_PENDING",
                "rootSeed": "0", "seed": "1", "normalRequest": not normal, "normalCatalogEnabled": False,
                "sourceEpoch": "tsj-generation-dependencies-v18", "modelEpoch": "obsolete", "layoutVersion": 19,
                "planVersion": True, "proofWarmReuse": True, "proofCacheHitsDelta": 1,
                "proofCacheMissesDelta": 1 if normal else 0, "planCacheMissesDelta": 0,
                "generationMillis": 90001, "generationWork": 641, "maxManualUnitMillis": 5001,
                "manualUnitMaxMillis": 5001, "maxJobMillis": 300000, "jobMillis": 90001,
                "jobWork": 641, "hypotheses": 4, "generationStages": 5, "solverSamples": 1,
                "normalProfileValidated": "HARD", "normalPublicationGranted": not normal,
                "cleanupPending": True, "candidateDisposed": False, "ownerRestored": False,
                "temporaryMeterClean": False, "coldProof": False, "fullProductionD01": False,
                "normalProfileAssessment": "difficulty/3;profile=MEDIUM;invented",
                "proofWorkUnits": 201, "requiredProofWorkUnits": 1, "jobStageWork": {},
                "cleanupMillis": -1, "operationMillis": -1, "wallMillis": 1, "generationReceiptDigest": "",
            }
            for key, value in mutations.items():
                bad = copy.deepcopy(item); bad["report"][key] = value
                with self.subTest(normal=normal, key=key), self.assertRaises(ValueError):
                    reader.check_capture(bad, plan, item["partition"], 0)
            for key, value in (("sourceEpoch", "tsj-generation-dependencies-v25"),
                               ("sourceEpoch", "tsj-generation-dependencies-v24"),
                               ("sourceEpoch", "tsj-generation-dependencies-v23"),
                               ("sourceEpoch", "tsj-generation-dependencies-v22"),
                               ("sourceEpoch", "tsj-generation-dependencies-v21"),
                               ("layoutVersion", 24), ("layoutVersion", 23), ("layoutVersion", 22),
                               ("layoutVersion", 21), ("layoutVersion", 20)):
                bad = copy.deepcopy(item); bad["report"][key] = value
                with self.subTest(normal=normal, prior_epoch_field=key), self.assertRaises(ValueError):
                    reader.check_capture(bad, plan, item["partition"], 0)
            for alter in (
                lambda r: r["proofRows"].pop(),
                lambda r: r["proofRows"][0].update(stateIsolated=False),
                lambda r: r["proofRows"][0].update(seed="999"),
                lambda r: r["proofRows"][0].update(sampleIds=[]),
                lambda r: r["proofRows"][0].update(repairActions=[]),
                lambda r: r["proofRows"][1].update(hypothesisDigest=r["proofRows"][0]["hypothesisDigest"]),
                lambda r: r["proofRows"][1].update(repairSemanticsDigest=r["proofRows"][0]["repairSemanticsDigest"]),
                lambda r: r["proofRows"][0].update(ordinal=1),
                lambda r: (r["proofRows"][1].update(
                    repairSemanticsDigest=r["proofRows"][0]["repairSemanticsDigest"]),
                    r["calibrationObservations"][1].update(
                    repairSemanticsDigest=r["calibrationObservations"][0]["repairSemanticsDigest"])),
                lambda r: r["candidateAttempts"][-1].update(ordinal=3),
                lambda r: r["candidateAttempts"][-1].update(workUnits=1),
                lambda r: r["candidateAttempts"][-1].update(proofUnits=500),
                lambda r: r["candidateAttempts"][-1].update(manifest="foreign"),
                lambda r: r.update(requestManifest=r["requestManifest"] + ";tampered"),
                lambda r: r.update(manualUnitMaxMillis=1),
                lambda r: r["calibrationObservations"][0]["samples"][0].update(tolerance=1),
                lambda r: r["calibrationObservations"][0].update(repairClass=4),
                lambda r: r["calibrationObservations"][0].update(hypothesisDigest="0" * 64),
                lambda r: r["calibrationObservations"][0].update(ordinal=2),
                lambda r: r["calibrationTemporalWaits"][0].__setitem__(3, "SERVICE_DECAY_3@0.1"),
            ):
                bad = copy.deepcopy(item); alter(bad["report"])
                with self.assertRaises(ValueError):
                    reader.check_capture(bad, plan, item["partition"], 0)
            bad_plan = copy.deepcopy(plan)
            bad_plan["manifests"][item["partition"]][0]["candidateSha256"][0] = "0" * 64
            with self.assertRaises(ValueError):
                reader.check_capture(item, bad_plan, item["partition"], 0)
            for key, value in (("build", "F" * 32), ("sourceBinding", "e" * 64), ("ordinal", 1), ("extra", True)):
                bad = copy.deepcopy(item); bad[key] = value
                with self.assertRaises(ValueError):
                    reader.check_capture(bad, plan, item["partition"], 0)

    def test_real_selected_source_manifest(self):
        inputs = {name: hashlib.sha256(path.read_bytes()).hexdigest()
                  for name, path in reader.input_paths().items()}
        self.assertIn("src/com/lushprojects/circuitjs1/client/PlayerFamilyCatalog.java", inputs)
        self.assertIn("war/tsj-workbench-ui.js", inputs)
        self.assertIn(reader.MANIFEST_ORACLE, inputs)
        self.assertIn("tests/browser/compiled_attribute_acceptance.py", inputs)
        self.assertIn("tests/browser/quickplay_procedural_acceptance.py", inputs)
        self.assertIn(reader.REPLACEMENT_BLIND_PLAN, inputs)
        self.assertIn(reader.REPLACEMENT_BLIND_CAMPAIGN, inputs)
        self.assertIn(reader.REPLACEMENT_BLIND_2_PLAN, inputs)
        self.assertIn(reader.REPLACEMENT_BLIND_2_CAMPAIGN, inputs)
        self.assertIn(reader.REPLACEMENT_BLIND_2_RUNTIME_SCOPE, inputs)
        runtime_scope = reader.ROOT / reader.REPLACEMENT_BLIND_RUNTIME_SCOPE
        if runtime_scope.is_file():
            self.assertIn(reader.REPLACEMENT_BLIND_RUNTIME_SCOPE, inputs)
        self.assertEqual(inputs[reader.REPLACEMENT_BLIND_CAMPAIGN],
                         reader.REPLACEMENT_BLIND_CAMPAIGN_SHA256)
        campaign, campaign_sha256 = reader.strict_json(
            reader.ROOT / reader.REPLACEMENT_BLIND_CAMPAIGN)
        self.assertEqual(campaign_sha256, reader.REPLACEMENT_BLIND_CAMPAIGN_SHA256)
        self.assertEqual(campaign.get("partition"), "replacement-blind-MEDIUM")
        self.assertEqual(campaign.get("roots"), reader.REPLACEMENT_BLIND)
        self.assertEqual(inputs[reader.REPLACEMENT_BLIND_2_CAMPAIGN],
                         reader.REPLACEMENT_BLIND_2_CAMPAIGN_SHA256)
        campaign2, campaign2_sha256 = reader.strict_json(
            reader.ROOT / reader.REPLACEMENT_BLIND_2_CAMPAIGN)
        self.assertEqual(campaign2_sha256, reader.REPLACEMENT_BLIND_2_CAMPAIGN_SHA256)
        self.assertEqual(campaign2.get("partition"), "replacement-blind-2-MEDIUM")
        self.assertEqual(campaign2.get("roots"), reader.REPLACEMENT_BLIND_2)
        self.assertEqual(campaign2.get("statusAtFreeze"), "NOT_RUN")
        self.assertEqual(campaign2.get("protectedHard"), "not read or run")
        plan = frozen_plan()
        reader.validate_plan(plan)
        missing = copy.deepcopy(plan)
        del missing["inputs"]["war/tsj-workbench-ui.js"]
        missing["sourceBinding"] = reader.binding(missing["inputs"])
        with self.assertRaises(ValueError):
            reader.validate_plan(missing)
        old_schema = copy.deepcopy(plan)
        old_schema["schema"] = "tsj-r12-medium-plan/4"
        with self.assertRaises(ValueError):
            reader.validate_plan(old_schema)
        older_schema = copy.deepcopy(plan)
        older_schema["schema"] = "tsj-r12-medium-plan/3"
        with self.assertRaises(ValueError):
            reader.validate_plan(older_schema)
        stale = copy.deepcopy(plan)
        stale["inputs"]["war/tsj-workbench-ui.js"] = "0" * 64
        stale["sourceBinding"] = reader.binding(stale["inputs"])
        with self.assertRaises(ValueError):
            reader.validate_plan(stale)
        bad_inventory = copy.deepcopy(plan)
        bad_inventory["developer"]["files"][0]["Bytes"] = True
        with self.assertRaises(ValueError):
            reader.validate_plan(bad_inventory)
        bad_manifest = copy.deepcopy(plan)
        bad_manifest["manifests"]["holdout"].pop()
        with self.assertRaises(ValueError):
            reader.validate_plan(bad_manifest)
        missing_blind = copy.deepcopy(plan)
        del missing_blind["manifests"]["replacement-blind"]
        with self.assertRaises(ValueError):
            reader.validate_plan(missing_blind)
        reordered_blind = copy.deepcopy(plan)
        reordered_blind["partitions"]["replacement-blind"].reverse()
        with self.assertRaises(ValueError):
            reader.validate_plan(reordered_blind)
        wrong_blind_campaign = copy.deepcopy(plan)
        wrong_blind_campaign["replacementBlindCampaignSha256"] = "0" * 64
        with self.assertRaises(ValueError):
            reader.validate_plan(wrong_blind_campaign)
        wrong_blind2_campaign = copy.deepcopy(plan)
        wrong_blind2_campaign["replacementBlind2CampaignSha256"] = "0" * 64
        with self.assertRaises(ValueError):
            reader.validate_plan(wrong_blind2_campaign)
        extra_plan_field = copy.deepcopy(plan)
        extra_plan_field["unfrozen"] = True
        with self.assertRaises(ValueError):
            reader.validate_plan(extra_plan_field)
        omitted_campaign = copy.deepcopy(plan)
        del omitted_campaign["inputs"][reader.REPLACEMENT_BLIND_CAMPAIGN]
        omitted_campaign["sourceBinding"] = reader.binding(omitted_campaign["inputs"])
        with self.assertRaises(ValueError):
            reader.validate_plan(omitted_campaign)
        omitted_campaign2 = copy.deepcopy(plan)
        del omitted_campaign2["inputs"][reader.REPLACEMENT_BLIND_2_CAMPAIGN]
        omitted_campaign2["sourceBinding"] = reader.binding(omitted_campaign2["inputs"])
        with self.assertRaises(ValueError):
            reader.validate_plan(omitted_campaign2)

        bad_rows = copy.deepcopy(plan["manifests"])
        bad_rows["replacement-blind"][7]["root"] = reader.REPLACEMENT_BLIND[0]
        with self.assertRaises(ValueError):
            reader.validate_manifest_plan(bad_rows)
        bad_rows = copy.deepcopy(plan["manifests"])
        bad_rows["replacement-blind"].reverse()
        with self.assertRaises(ValueError):
            reader.validate_manifest_plan(bad_rows)
        bad_rows = copy.deepcopy(plan["manifests"])
        bad_rows["replacement-blind-2"][7]["root"] = reader.REPLACEMENT_BLIND_2[0]
        with self.assertRaises(ValueError):
            reader.validate_manifest_plan(bad_rows)
        bad_rows = copy.deepcopy(plan["manifests"])
        bad_rows["replacement-blind-2"].reverse()
        with self.assertRaises(ValueError):
            reader.validate_manifest_plan(bad_rows)

    def test_output_inventory_canonicalization_and_invalid_paths(self):
        files = artifact()["files"]
        shuffled = list(reversed(files))
        self.assertEqual(reader.output_file_records(shuffled), reader.output_file_records(files))
        self.assertEqual(reader.canonical_inventory(shuffled), files)
        for alter in (
            lambda rows: rows[0].update(Path="../escape"),
            lambda rows: rows[0].update(Bytes=True),
            lambda rows: rows[0].update(Sha256="Z" * 64),
            lambda rows: rows.append(dict(rows[0], Path=rows[0]["Path"].lower())),
        ):
            bad = copy.deepcopy(files)
            alter(bad)
            with self.assertRaises(ValueError):
                reader.output_file_records(bad)

    def test_native_manifest_metadata_real_json_and_adversarial_rows(self):
        source_rows = []
        for partition, roots in reader.PARTITIONS.items():
            for ordinal, root in enumerate(roots):
                row = manifest_plan()[partition][ordinal]
                source_rows.append({"partition": partition, "ordinal": ordinal, **row})
                source_rows[-1]["manifestHashProtocol"] = reader.MANIFEST_HASH_PROTOCOL
        with tempfile.TemporaryDirectory(prefix="tsj-r12-reader-") as folder:
            path = Path(folder) / "manifests.json"
            self.assertEqual(len(source_rows), 30)
            path.write_text(json.dumps(source_rows), encoding="utf-8", newline="")
            parsed = freeze_plan.parse_manifest_oracle(path)
            self.assertEqual(parsed, manifest_plan())
            self.assertEqual(len(parsed["replacement-blind"]), 8)
            self.assertEqual(len(parsed["replacement-blind-2"]), 8)
            self.assertTrue(all(len(row["candidateSha256"]) == 4
                                for partition in ("replacement-blind", "replacement-blind-2")
                                for row in parsed[partition]))
            for bad_rows in (
                source_rows[:-1],
                source_rows[:-len(reader.REPLACEMENT_BLIND_2)],
                [source_rows[1], source_rows[0], *source_rows[2:]],
                [*source_rows[:-1], {**source_rows[-1], "candidateSha256": ["0" * 64]}],
                [*source_rows[:-1], {**source_rows[-1], "root": "0"}],
                [*source_rows[:14], {**source_rows[14], "partition": "holdout"}, *source_rows[15:]],
                [*source_rows[:14], *reversed(source_rows[14:])],
                [*source_rows[:-1], {**source_rows[-1], "extra": True}],
                [*source_rows[:-1], {**source_rows[-1], "manifestHashProtocol": "unknown/0"}],
                [{key: value for key, value in row.items() if key != "manifestHashProtocol"}
                 for row in source_rows],
            ):
                path.write_text(json.dumps(bad_rows), encoding="utf-8", newline="")
                with self.assertRaises(ValueError):
                    freeze_plan.parse_manifest_oracle(path)
            path.write_bytes(b'[{"partition":"profile","partition":"profile"}]')
            with self.assertRaises(ValueError):
                freeze_plan.parse_manifest_oracle(path)
            path.write_bytes(b"[NaN]")
            with self.assertRaises(ValueError):
                freeze_plan.parse_manifest_oracle(path)
            path.write_bytes(b"\xef\xbb\xbf" + json.dumps(source_rows).encode("utf-8"))
            self.assertEqual(freeze_plan.parse_manifest_oracle(path), manifest_plan())
            path.write_bytes(b"\xff[]")
            with self.assertRaises(ValueError):
                freeze_plan.parse_manifest_oracle(path)

    def test_real_json_and_external_plan_sha_cli_canaries(self):
        with tempfile.TemporaryDirectory(prefix="tsj-r12-reader-") as folder:
            directory = Path(folder)
            data = directory / "fixture.json"
            for text in ('{"a":1,"a":2}', '{"a":NaN}'):
                data.write_text(text, encoding="utf-8")
                with self.assertRaises(ValueError):
                    reader.strict_json(data)
            plan = directory / "plan.json"
            plan.write_text(json.dumps(frozen_plan()), encoding="utf-8", newline="")
            output = directory / "result.json"
            command = [sys.executable, str(Path(reader.__file__)), "--plan", str(plan),
                       "--plan-sha256", "0" * 64, "--partition", "profile",
                       "--captures", str(data), "--output", str(output)]
            result = subprocess.run(command, text=True, capture_output=True, check=False)
            self.assertEqual(result.returncode, 2)
            self.assertIn("frozen plan SHA-256 differs", result.stdout)
            self.assertFalse(output.exists())
            expected = hashlib.sha256(plan.read_bytes()).hexdigest()
            command[command.index("0" * 64)] = expected
            result = subprocess.run(command, text=True, capture_output=True, check=False)
            self.assertEqual(result.returncode, 2)
            self.assertIn("complete frozen partition required", result.stdout)
            self.assertFalse(output.exists())
            command[command.index("profile")] = "replacement-blind"
            result = subprocess.run(command, text=True, capture_output=True, check=False)
            self.assertEqual(result.returncode, 2)
            self.assertIn("complete frozen partition required", result.stdout)
            self.assertFalse(output.exists())
            output.write_text("preserve", encoding="utf-8")
            result = subprocess.run(command, text=True, capture_output=True, check=False)
            self.assertEqual(result.returncode, 2)
            self.assertIn("refusing to replace", result.stdout)
            self.assertEqual(output.read_text(encoding="utf-8"), "preserve")


if __name__ == "__main__":
    unittest.main()
