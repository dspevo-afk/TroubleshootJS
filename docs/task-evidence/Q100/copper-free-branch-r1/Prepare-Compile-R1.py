"""Prepare a fresh native compile, authenticating the unchanged accepted closure."""
import sys
if not (sys.flags.isolated and sys.dont_write_bytecode):
    raise SystemExit("ISOLATION_REQUIRED")
import hashlib
import importlib.util
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
P9 = ROOT.parent / "tsj-q100-reservation-provenance-9f553163342745b1aade3f8b3b60fefd"
AUDITOR = P9 / "canonical-reservation-provenance-r1/audit_inputs.py"
AUDITOR_SHA = "f2080fa993046bb24627233403a3e499ce843044c7403e8bad5bdfe09ea56998"
PINS = P9 / "inputs-r15/input-provenance-pins.json"
PINS_SHA = "2d717040c9b78244cf591857af96cba51bb52366af6c270e70b741db0468bf08"


def sha(body):
    return hashlib.sha256(body).hexdigest()


def data(value):
    return (json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False) + "\n").encode()


def main():
    if len(sys.argv) not in (2, 3) or len(sys.argv) == 3 and sys.argv[2] not in ("--probe", "--initial-snapshot"):
        raise SystemExit("Expected unique pre-created empty stage directory [--probe|--initial-snapshot]")
    stage = Path(sys.argv[1]).resolve(strict=True)
    if stage.parent != ROOT or stage.is_symlink() or any(stage.iterdir()):
        raise SystemExit("STAGE_OWNERSHIP_OR_NOT_EMPTY")
    raw = AUDITOR.read_bytes()
    if sha(raw) != AUDITOR_SHA:
        raise SystemExit("AUDITOR_HASH")
    module = importlib.util.module_from_spec(importlib.util.spec_from_file_location("bf_parent_auditor", AUDITOR))
    sys.modules[module.__name__] = module
    exec(compile(raw, str(AUDITOR), "exec"), module.__dict__)
    context = module.validate_inputs(str(PINS), PINS_SHA)
    for bridge in context["bridges"]:
        body = context["_reads"].read(bridge["sourcePath"], expected=bridge)
        target = stage / bridge["name"]
        with target.open("xb") as stream:
            stream.write(body)
        if target.read_bytes() != body:
            raise RuntimeError("BRIDGE_COPY")
    classes = stage / "classes"
    classes.mkdir()
    empty = stage / "empty-sourcepath"
    empty.mkdir()
    old_sources = module.expected_compiler_sources(context, str(stage))
    if len(old_sources) != 1085:
        raise RuntimeError("OLD_SOURCE_COUNT")
    mode = sys.argv[2] if len(sys.argv) == 3 else ""
    probe = mode == "--probe"
    source_name = "source/CopperFreeSeamProbe.java" if probe else "source/CopperFreeBranchStudy.java"
    if mode == "--initial-snapshot":
        source_name = "source-revisions/native-initial-r1/CopperFreeBranchStudy.java"
    new_sources = [ROOT / source_name]
    if not mode:
        for name in ("CopperFreeBranchFixtures.java", "CopperFreeBranchCanary.java"):
            extra = ROOT / "source" / name
            if extra.exists():
                new_sources.append(extra)
    sources = [Path(path).resolve(strict=True) for path in old_sources] + new_sources
    records = []
    for source in sources:
        if source.is_symlink():
            raise RuntimeError("SOURCE_SYMLINK")
        body = source.read_bytes()
        records.append({"path": str(source), "bytes": len(body), "sha256": sha(body)})
    source_raw = "".join('"' + str(path).replace("\\", "/") + '"\n' for path in sources).encode()
    source_list = stage / "compiler-sources.txt"
    source_list.write_bytes(source_raw)
    repository = Path(context["pins"]["repositoryRoot"])
    jars = [repository / ".tools/gwt-2.7.0" / name for name in ("gwt-user-2.7.0.jar", "gwt-dev-2.7.0.jar")]
    request = {"FilePath": context["tools"]["javacExe"], "Arguments": ["-source", "7", "-target", "7", "-encoding", "UTF-8", "-classpath", ";".join(str(path) for path in [classes, *jars]), "-sourcepath", str(empty), "-d", str(classes), "@" + str(source_list)], "LimitMs": 60000}
    (stage / "request.json").write_bytes(data(request))
    tools = []
    for path in [Path(context["tools"]["javaExe"]), Path(context["tools"]["javacExe"]), *jars]:
        body = path.read_bytes()
        tools.append({"path": str(path), "bytes": len(body), "sha256": sha(body)})
    plan = (ROOT / "PLAN_R1.md").read_bytes()
    manifest = {"schema": "tsj-copper-free-branch-source-v1", "parentPinsPath": str(PINS), "parentPinsSha256": PINS_SHA, "parentAuditorSha256": AUDITOR_SHA, "sourceParent": "59a11b0ef37a582b85fa157c5803df42ecd69ab2", "planSha256": sha(plan), "compilerSourceCount": len(sources), "compilerListSha256": sha(source_raw), "sources": records, "tools": tools}
    manifest_raw = data(manifest)
    (stage / "source-manifest.json").write_bytes(manifest_raw)
    module.recheck_inputs(context)
    for record in records + tools:
        body = Path(record["path"]).read_bytes()
        if len(body) != record["bytes"] or sha(body) != record["sha256"]:
            raise RuntimeError("FINAL_INPUT_CHANGED")
    print(json.dumps({"status": "PREPARED_ONLY", "sources": len(sources), "manifestSha256": sha(manifest_raw), "sourceListSha256": sha(source_raw)}, separators=(",", ":")))


if __name__ == "__main__":
    main()
