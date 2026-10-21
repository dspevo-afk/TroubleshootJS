"""Strictly qualify the finite paired-packing normal-player observations."""
import hashlib
import json
import re
import runpy
from pathlib import Path

if not __debug__:
    raise RuntimeError("Assertion-enabled Python required")

HERE = Path(__file__).resolve().parent
REQUIRED = (
    "random-new-board-request", "random-first-generated-identity",
    "random-original-fail-off", "random-top-view",
    "dual-exact-replay-request", "dual-public-identity",
    "dual-original-fail-off", "dual-top-view", "dual-bottom-view",
    "led-original-fail-off", "led-isolated-ol", "led-shop-installed",
    "led-off-330", "led-fresh-customer-pass", "led-final-off",
)
KNOWN_NORMAL_FAMILIES = {
    "LED_INDICATOR", "DIODE_PROTECTED_INDICATOR",
    "PARALLEL_DUAL_INDICATOR", "RC_DELAY", "NPN_LOW_SIDE_SWITCH",
    "NMOS_LOW_SIDE_SWITCH", "RELAY_OUTPUT", "SENSOR_CONTROL",
    "RB15_CONTROL", "COMPOSED_CONTROLLED_INDICATOR", "RB30_CONTROL",
}
FORBIDDEN = (
    "normal-packing@", "protectedVoid", "generationRoutingExpansions",
    "Injected fault:", "Hidden fault:", "Answer key:", "tsjDebug=",
)


def read(name):
    return json.loads((HERE / name).read_text(encoding="utf-8-sig"))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def replay_parts(value):
    fields = value.split("/")
    assert len(fields) == 5 and fields[:2] == ["tsj-alpha", "6"]
    assert fields[2] in ("EASY", "MEDIUM") and fields[3]
    seed = fields[4]
    assert str(int(seed)) == seed and -(1 << 63) <= int(seed) < (1 << 63)
    return fields[2], fields[3]


def main():
    assert runpy.run_path(str(HERE / "player_inputs.py"))["record"]() == read("player-inputs.json")
    expected = read("player-baseline-execution.json")
    assert set(expected) == {"Protocol", "SourceDigest", "ScriptDigest", "WebDigest", "Digest", "FileCount"}
    assert expected == read("post-player-provenance.json") == read("served-execution-before.json") == read("served-execution-after.json")

    plan = read("player-plan.json")
    assert plan["port"] == 8911
    families = plan["normalFamilies"]
    assert len(families) == 11 and len(set(families)) == 11 and set(families) == KNOWN_NORMAL_FAMILIES
    assert plan["replays"]["dual"] == "tsj-alpha/6/EASY/PARALLEL_DUAL_INDICATOR/-9223372036854775808"
    assert plan["replays"]["repair"] == "tsj-alpha/6/EASY/LED_INDICATOR/5577758334702827054"
    for side in ("before", "after"):
        audit = (HERE / ("player-input-audit-" + side + ".txt")).read_text(encoding="utf-8-sig").strip()
        assert audit == "PASS player final packing4 source1482 machinery24 five accepted gates and ten sealed helpers"
        owner = read("preview-owner-" + side + ".json")
        assert owner["status"] == "PASS" and owner["rootScriptCommandBirthExact"] is True
        assert owner["servedIdentityEqualsIndependentProjection"] is True and owner["http200"] is True
        assert owner["port"] == plan["port"]
    before, after = read("preview-owner-before.json"), read("preview-owner-after.json")
    assert (before["pid"], before["startTicks"]) == (after["pid"], after["startTicks"])

    events = read("player-events.json")
    assert events
    for event in events:
        base_fields = {"name", "status", "startedUtc", "elapsedMillis"}
        assert event["status"] in ("PASS", "FAIL") and isinstance(event["startedUtc"], str) and event["startedUtc"]
        assert type(event["elapsedMillis"]) is int and event["elapsedMillis"] >= 0
        if event["status"] == "PASS":
            assert set(event) == base_fields | {"details"}
            assert event["details"] is None or type(event["details"]) is dict
        else:
            assert set(event) == base_fields | {"error", "privateErrorSha256"}
            assert type(event["error"]) is str and event["error"]
            assert re.fullmatch(r"[a-f0-9]{64}", event["privateErrorSha256"])
    for name in REQUIRED:
        assert sum(e["name"] == name and e["status"] == "PASS" for e in events) == 1, name
    assert sum(e["name"] == "random-new-board-request" for e in events) == 1
    random_request = next(e for e in events if e["name"] == "random-new-board-request")
    assert random_request["details"]["seedEntered"] is False

    identities = {name: read(name + "-public-identity.json") for name in ("random", "dual", "led")}
    for identity in identities.values():
        assert set(identity) == {"replay", "build"}
    assert identities["dual"]["replay"] == plan["replays"]["dual"]
    assert identities["led"]["replay"] == plan["replays"]["repair"]
    random_profile, random_family = replay_parts(identities["random"]["replay"])
    assert random_family in families and random_family == plan["randomFamily"]
    assert random_profile == plan["randomProfile"]
    assert random_request["details"]["family"] == random_family
    assert random_request["details"]["profile"] == random_profile
    assert replay_parts(identities["dual"]["replay"]) == ("EASY", "PARALLEL_DUAL_INDICATOR")
    assert replay_parts(identities["led"]["replay"]) == ("EASY", "LED_INDICATOR")

    builds = {identity["build"] for identity in identities.values()}
    assert len(builds) == 1
    build_match = re.fullmatch(r"tsj-alpha/6 / ([A-F0-9]{32})", builds.pop())
    assert build_match
    modules_doc = read("production-modules.json")
    modules = modules_doc["modules"]
    assert modules_doc["schema"] == "paired-production-modules@1" and len(modules) == 5
    assert all(set(m) == {"strongName", "sha256", "bytes"} and
               re.fullmatch(r"[A-F0-9]{32}", m["strongName"]) and
               re.fullmatch(r"[a-f0-9]{64}", m["sha256"]) and
               type(m["bytes"]) is int and m["bytes"] > 0 for m in modules)
    assert len({m["strongName"] for m in modules}) == len({m["sha256"] for m in modules}) == 5
    assert build_match.group(1) in {m["strongName"] for m in modules}

    artifacts = read("player-artifacts.json")
    assert artifacts["bodies"]
    for record in artifacts["bodies"]:
        file = HERE / record["file"]
        raw = file.read_bytes()
        assert file.name == record["file"] and sha(file) == record["sha256"] and len(raw) == record["bytes"]
        assert record["file"].endswith("-public.txt")
        assert not any(token in raw.decode("utf-8") for token in FORBIDDEN), record["file"]
    planned = plan["primaryImages"]
    assert 2 <= len(planned) <= 5 and len(set(planned)) == len(planned)
    assert [item["file"] for item in artifacts["images"]] == planned
    reviewed = read("visual-review.json")
    assert reviewed["status"] == "PASS" and set(reviewed["images"]) == set(planned)
    for record in artifacts["images"]:
        file = HERE / record["file"]
        raw = file.read_bytes()
        assert file.name == record["file"] and sha(file) == record["sha256"] and len(raw) == record["bytes"]
        assert file.suffix.lower() in (".jpg", ".jpeg") and raw.startswith(b"\xff\xd8\xff")
        assert reviewed["images"][record["file"]]["sha256"] == record["sha256"]
        assert reviewed["images"][record["file"]]["judgment"] == "PASS"

    cleanup = read("player-resource-cleanup.json")
    assert cleanup["status"] == "PASS"
    assert cleanup["browser"]["apiClose"] is True and cleanup["browser"]["processExitCode"] == 0
    assert cleanup["browser"]["profileAbsent"] is True and cleanup["browser"]["recordedBirthExited"] is True
    assert cleanup["preview"]["stateAbsent"] is True and cleanup["preview"]["portReleased"] is True
    assert cleanup["preview"]["recordedBirthExited"] is True
    for prefix in ("preview", "preview-stop"):
        launch_path = HERE / (prefix + "-process-launch.json")
        launch, closed = json.loads(launch_path.read_text(encoding="utf-8-sig")), read(prefix + "-process-cleanup.json")
        assert closed["status"] == "PASS" and type(closed["actualExecExitCode"]) is int and closed["actualExecExitCode"] == 0
        assert closed["launchSha256"] == sha(launch_path) and len(launch["drivers"]) == len(closed["checks"]) == 2
        assert all(check["recordedBirthExited"] is True for check in closed["checks"])
        assert [{k: c[k] for k in ("pid", "startTicks", "executable")} for c in closed["checks"]] == launch["drivers"]

    target = HERE / "player-assessment.json"
    assert not target.exists()
    failures = [event for event in events if event["status"] == "FAIL"]
    result = {
        "status": "PASS_FINAL_PRODUCTION_PAIRED_PACKING_PLAYER",
        "inputSealSha256": sha(HERE / "player-inputs.json"),
        "productionModulesSha256": sha(HERE / "production-modules.json"),
        "events": len(events), "preservedObserverFailures": failures,
        "publicIdentities": identities, "browserStrongName": build_match.group(1),
        "moduleCount": len(modules), "images": planned,
        "bodyCount": len(artifacts["bodies"]), "execution": expected,
        "limits": "Finite actual normal-player requests, exact replays, repair and raw screenshots with root visual review and verified owned-resource cleanup. No all-seed, manufacturing, optimality, or broader performance claim.",
    }
    target.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("PASS final production paired-packing player/identity/artifacts/owned cleanup; observer failures=" + str(len(failures)))


if __name__ == "__main__":
    main()
