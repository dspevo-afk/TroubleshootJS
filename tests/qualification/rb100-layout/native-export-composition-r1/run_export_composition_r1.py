"""Root-frozen synthetic current-result export runner; Source authoring never executes it."""

from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import stat
import sys
import types


assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
assert sys.platform == "win32" and os.name == "nt"
parser = argparse.ArgumentParser()
parser.add_argument("--case", required=True, choices=("smoke", "suite"))
options = parser.parse_args()
MODE = options.case
A = Path(os.path.abspath(__file__)).parent
SOURCE_CAP = 4 * 1024 * 1024
REPORT_CAP = 1024 * 1024
CLASS_COUNT_CAP = 10000
CLASS_BYTES_CAP = 268435456
TOOL_CAP = 512 * 1024 * 1024
MAX_MAJOR = 51
REPARSE_POINT = 0x400
HEX = re.compile(r"[0-9a-f]{64}\Z")
SOURCE_INPUT_SHA256 = "d623430cfdb5aa0ba45d7f85b32dbdb0a8994b8a68d2ffb881e14ab634cabc6b"
NATIVE_SOURCE_INPUT_SHA256 = "99bd8f9be9d074aef15735e212f0947541728906198b1f89845cd6b3107213b4"
SOURCE_FINDINGS_SHA256 = "737a3b2b796518d1ad1e233b83bc5b0c952409cbb981e9bb83c20e9f10424be1"
EXPORT_CONTRACT_SHA256 = "986377506b007a15e920d1abb852d1b49212ef835fd8f80b3ba8f4638dce533a"
BASE_COMMIT = "2ab3e1cf60b24c5ee12459ff39ca2b3c0e0d8182"
AUDIT_BASE_COMMIT = BASE_COMMIT
SOURCE_NAMES = (
    "CURRENT_STUDY.java", "CurrentV2CanonicalCodec.java", "CURRENT_RECEIPT_BINDING.java",
    "SHARED_OWNER.java", "CurrentBodyOwnership.java", "CurrentAssemblyMetadata.java",
    "CurrentV2InputSetup.java", "COMPOSITION_CONTRACT_R1.md",
    "INDEPENDENT_CENSUS_R1.json", "ComposedMetadataCanary.java",
    "EXPORT_CONTRACT_AUDIT_R1.md", "EXPORT_SOURCE_WORKSHEET_R1.md",
    "CurrentV2NativeExportCanary.java", "WIRE_R1.md", "PREPAYMENT_R1.md",
)
NATIVE_SOURCE_NAMES = (
    "context_codec.py", "test_context_codec.py", "derive_synthetic_fixture_r1.py",
    "build_composed_metadata_r1.py", "run_composed_metadata_r1.py",
    "root_protocol_gates_r1.py", "NATIVE_PRIVATE_PCB.java",
)
FIXED_SOURCE_PINS = {
    "source/SHARED_OWNER.java":
        "99993fc4f2c32e38fc85d4cea79b7662841a744f35816b95b0b2e8579483915b",
    "source/NATIVE_PRIVATE_PCB.java":
        "7bf4ab57198ff9444381ec5d677c1a2bc309bfa3e7f01b188c631ffe5f2b966c",
    "source/CURRENT_RECEIPT_BINDING.java":
        "1df7e06085165d025ff79bdd0cdaa7f603c110cc26b6cc6f280d73f52e8d2e41",
    "source/CURRENT_STUDY.java":
        "9ac45b374c8aeee7a912a4e1452435b71b278aeae0a288e5a75dd82a149c006b",
    "source/CurrentBodyOwnership.java":
        "8a7a5b0dfe82d4e230adcd1d531e6942664afbd96563dc5d21f40167b41c3832",
    "source/CurrentV2CanonicalCodec.java":
        "04b016afa690d2154f0b970640bc30b7c4887b450622444f8f18329bc04c3672",
    "source/CurrentV2InputSetup.java":
        "7baab4caf45741f00d311e3b3854c794204356bfa03c0f3271399d44087df6a4",
    "source/CurrentAssemblyMetadata.java":
        "e2b6b05bc95b9295d15aba5305a188c1eb98cb39db4b689747e2f126a6835198",
    "emitted/CohortTerminalTelemetryInputV2.java":
        "c8a25f88ccf523e030e7e1c14073255b78b9358687ac65088339a237bea80089",
}
REQUIRED_FREEZE_ROLES = set(FIXED_SOURCE_PINS) | {
    "ExportCompositionCanary.java", "build_export_composition_r1.py",
}
ALLOWED_FREEZE_ROLES = REQUIRED_FREEZE_ROLES
HELPER_SHA256 = "224cf971a7142be7ea3679555f051ec65c4b2a2e6afab00c329dd9eb0d7372e3"
NESTED_HELPER_SHA256 = "4f414bc2a8823e539c8f6ea7aaa0a2ff09d5bd04c092e617f14ff7bb7de78a8b"
PHASE = "native-export-composition-" + MODE + "-r1"
PACKAGE = "com.lushprojects.circuitjs1.client."
CASE_CLASSES = {"smoke": PACKAGE + "ExportCompositionCanary",
                "suite": PACKAGE + "ExportCompositionCanary"}
CASE_NAMES = {
    "smoke": ["current_export_composition"],
    "suite": ["receipt_changed_before_export_close"],
}
RECORD_INTEGER_KEYS = {
    "operationNanos", "cleanupNanos", "pathEntries", "receiptBytes",
    "receiptHashBlocks", "units", "copiedEntries", "copyChunks",
    "wrapperGroups", "priorGroups", "groupUnits", "maxGroupUnits",
    "maxGroupNanos", "closedGroupMeasures", "workFinalizedSlots",
    "exportCutoffNanos", "beforeExportElapsedNanos", "afterExportElapsedNanos",
    "setupMeasureUnits", "setupMeasureNanos", "metadataMeasureUnits",
    "metadataMeasureNanos", "exportMeasureUnits", "exportMeasureNanos",
    "resultBytesObserved", "metricsResultBytes", "metricsMarkerBytes",
    "metricsExportUnits", "metricsFinalExportNanos", "metricsElapsedNanos",
    "metricsTotalUnits", "metricsTotalGroups", "metricsMaxGroupNanos",
    "metricsMaximumUnitsPerGroup", "metricsCopiedEntriesDelta",
    "metricsCopyChunksDelta",
}
RECORD_STRING_KEYS = {
    "name", "status", "phase", "reason", "cleanupDisposition", "error",
    "cleanupError", "expectedFailure", "resultHex", "markerHex",
}
RECORD_BOOLEAN_KEYS = {
    "meterOpen", "stdoutRestored", "metricsReturned", "meterDiscarded",
    "receiptMutationApplied",
}
ABSENT_NEGATIVE_METRICS = {
    "metricsResultBytes", "metricsMarkerBytes", "metricsExportUnits",
    "metricsFinalExportNanos", "metricsElapsedNanos", "metricsTotalUnits",
    "metricsTotalGroups", "metricsMaxGroupNanos",
    "metricsMaximumUnitsPerGroup", "metricsCopiedEntriesDelta",
    "metricsCopyChunksDelta",
}
JAR_INPUTS = (
    ("gwt-user-2.7.0.jar", 32024115,
     "d3721bcdd7c6855a524801212dd74548967d8fb65cbc212a20890732ff2c6948"),
    ("gwt-dev-2.7.0.jar", 38276750,
     "c7321e367fc24e5c7ad97b74d1cf2980c45fbd7cbe1fd412f8a8ba128f685135"),
)
PUBLIC_SOURCE_PINS = {
    "AuthoredFlatWorkCanary.java":
        "38def52bfe4d365b3f821099cef0d847eb0819aa69d9f0158d01e4cec999ff42",
    "CohortTerminalTelemetryInput.java":
        "bc0ff7ab787537f16e211ccfc4d787059aefef86dea3554d27f063b88e67660f",
    "ActualGeneratorBranchOrderStudy.java":
        "855ad071798106d0d419d6382af3251240ad2c342d1542eca27dec7d7e4fc2cd",
    "PhysicalSpecificationDeveloperVerifier.java":
        "1e1c4e0517aedad06dd0cea1d75aa8b3ce23abf735ec8a607d014ad1f77827a0",
    "FailedFirstFixtures.java":
        "a49ec2911e7f9b36c66f3b9be56841707cd1afc127e3b5d716b0d689d137542c",
}


def _snapshot(item):
    return (item.st_dev, item.st_ino, item.st_mode, item.st_nlink, item.st_size,
            getattr(item, "st_mtime_ns", int(item.st_mtime * 1000000000)),
            getattr(item, "st_ctime_ns", int(item.st_ctime * 1000000000)),
            getattr(item, "st_file_attributes", 0))


def _handle_identity(snapshot):
    return (snapshot[0], snapshot[1], stat.S_IFMT(snapshot[2]), snapshot[4],
            snapshot[5], snapshot[7])


def _safe(path):
    path = Path(path)
    absolute = Path(os.path.abspath(str(path)))
    try:
        resolved = path.resolve(strict=True)
    except (OSError, RuntimeError, ValueError):
        raise AssertionError("INPUT_PATH") from None
    assert os.path.normcase(str(absolute)) == os.path.normcase(str(resolved))
    for current in list(reversed(absolute.parents)) + [absolute]:
        item = os.lstat(str(current))
        assert not stat.S_ISLNK(item.st_mode)
        assert not (getattr(item, "st_file_attributes", 0) & REPARSE_POINT)
    return absolute


def _read(path, cap, *, allow_empty=False):
    path = _safe(path)
    before = os.lstat(str(path))
    before_snapshot = _snapshot(before)
    assert stat.S_ISREG(before.st_mode)
    assert (0 if allow_empty else 1) <= before.st_size <= cap
    with path.open("rb") as stream:
        opened = _snapshot(os.fstat(stream.fileno()))
        assert _handle_identity(before_snapshot) == _handle_identity(opened)
        raw = stream.read(cap + 1)
        after_handle = _snapshot(os.fstat(stream.fileno()))
    assert before_snapshot == _snapshot(os.lstat(str(path)))
    assert _handle_identity(opened) == _handle_identity(after_handle)
    assert len(raw) == before.st_size and len(raw) <= cap
    _safe(path)
    return raw


def _digest(path, cap):
    path = _safe(path)
    before = os.lstat(str(path))
    before_snapshot = _snapshot(before)
    assert stat.S_ISREG(before.st_mode) and 0 < before.st_size <= cap
    digest = hashlib.sha256()
    header = bytearray()
    total = 0
    with path.open("rb") as stream:
        opened = _snapshot(os.fstat(stream.fileno()))
        assert _handle_identity(before_snapshot) == _handle_identity(opened)
        while True:
            block = stream.read(min(1024 * 1024, cap - total + 1))
            if not block:
                break
            total += len(block)
            assert total <= cap
            if len(header) < 8:
                header.extend(block[:8 - len(header)])
            digest.update(block)
        after_handle = _snapshot(os.fstat(stream.fileno()))
    assert before_snapshot == _snapshot(os.lstat(str(path)))
    assert _handle_identity(opened) == _handle_identity(after_handle)
    assert total == before.st_size
    _safe(path)
    return total, digest.hexdigest(), bytes(header)


def _sha(raw):
    return hashlib.sha256(raw).hexdigest()


def _json(raw):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            assert key not in result, "JSON_DUPLICATE"
            result[key] = value
        return result

    def reject_constant(_value):
        raise AssertionError("JSON_CONSTANT")

    return json.loads(raw.decode("utf-8"), object_pairs_hook=unique,
                      parse_constant=reject_constant)


def _relative(name):
    assert type(name) is str and "\\" not in name and ":" not in name
    assert not name.startswith("/")
    assert all(part not in ("", ".", "..") for part in name.split("/"))
    return A.joinpath(*name.split("/"))


def _write(path, value):
    assert path.parent == run_directory
    raw = (json.dumps(value, sort_keys=True, indent=2) + "\n").encode("ascii")
    assert len(raw) <= REPORT_CAP
    with path.open("xb") as stream:
        assert stream.write(raw) == len(raw)
    assert _read(path, REPORT_CAP) == raw
    return _sha(raw)


_safe(A)
helper = A.parent / "tsj-q100-flat-work-plan-24224fbba26b4bee90da24689ccc59a6/root_h_gates_r3.py"
nested_helper = A.parent / "tsj-q100-selected-head-wire-096f9160b79240859a393749151619f2/root_gates_r1.py"
helper_raw = _read(helper, SOURCE_CAP)
nested_helper_raw = _read(nested_helper, SOURCE_CAP)
assert _sha(helper_raw) == HELPER_SHA256
assert _sha(nested_helper_raw) == NESTED_HELPER_SHA256
root = types.ModuleType("_export_composition_run_root")
root.__file__ = str(helper)
exec(compile(helper_raw, str(helper), "exec"), root.__dict__)
g = root.g
root.H = A
g.R = A
assert Path(root.OLD_HELPER) == nested_helper
assert len(root.TOOLS) == 6
root.tools_pinned()  # The trusted minimal six tools only, never g.tools_pinned.

for option in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "JDK_JAVAC_OPTIONS"):
    assert not os.environ.get(option), option


def _freeze():
    raw = _read(A / "FROZEN_SOURCE_R1.json", SOURCE_CAP)
    value = _json(raw)
    assert type(value) is dict
    assert value.get("status") == "ROOT_ACCEPTED_FINAL_SOURCE_FOR_SYNTHETIC_GATES"
    rows = value.get("files")
    assert type(rows) is list and 0 < len(rows) <= len(ALLOWED_FREEZE_ROLES)
    pins = {}
    for row in rows:
        assert type(row) is dict and set(row) == {"role", "sha256"}
        role, digest = row["role"], row["sha256"]
        assert type(role) is str and role in ALLOWED_FREEZE_ROLES
        assert type(digest) is str and HEX.fullmatch(digest)
        assert role not in pins
        path = _relative(role)
        assert _sha(_read(path, SOURCE_CAP)) == digest
        pins[role] = (path, digest)
    assert REQUIRED_FREEZE_ROLES.issubset(pins)
    for role, digest in FIXED_SOURCE_PINS.items():
        assert pins[role][1] == digest
    return value, raw, pins


def _source_inputs(filename, expected_digest, base, scope, names, *, envelope):
    raw = _read(A / filename, SOURCE_CAP)
    assert _sha(raw) == expected_digest
    value = _json(raw)
    required = {"status", "base", "scope", "rows"} | ({"genuineEnvelope"} if envelope else set())
    assert type(value) is dict and set(value) == required
    assert value["status"] == "PINNED_SOURCE_ONLY_NO_DATA"
    assert value["base"] == base and value["scope"] == scope
    if envelope:
        assert value["genuineEnvelope"] == "UNSPENT_GATED"
    rows = value["rows"]
    assert type(rows) is list and len(rows) == len(names)
    assert all(type(row) is dict for row in rows)
    assert tuple(row.get("name") for row in rows) == names
    pins = []
    for row in rows:
        assert set(row) == {"name", "bytes", "sha256"}
        name, size, digest = row["name"], row["bytes"], row["sha256"]
        assert type(size) is int and 0 < size <= SOURCE_CAP
        assert type(digest) is str and HEX.fullmatch(digest)
        path = A / "source" / name
        data = _read(path, SOURCE_CAP)
        assert _sha(data) == digest
        assert len(data) == size
        if "source/" + name in FIXED_SOURCE_PINS:
            assert digest == FIXED_SOURCE_PINS["source/" + name]
        pins.append((path, digest))
    return value, raw, tuple(pins)

def _runtime_control(freeze_raw):
    path = A / "RUNTIME_CONTROL_R1.json"
    raw = _read(path, SOURCE_CAP)
    value = _json(raw)
    assert type(value) is dict and set(value) == {
        "status", "originalSourceFreezeSha256", "unchangedCompiledSources", "files"
    }
    assert value["status"] == "ROOT_ACCEPTED_RUNTIME_CONTROL_ONLY"
    assert value["originalSourceFreezeSha256"] == _sha(freeze_raw)
    assert value["unchangedCompiledSources"] is True
    rows = value["files"]
    assert type(rows) is list and len(rows) == 2
    pins = {}
    for row in rows:
        assert type(row) is dict and set(row) == {"role", "sha256"}
        role, digest = row["role"], row["sha256"]
        assert role in {"run_export_composition_r1.py", "CASE_MANIFEST_R1.json"}
        assert role not in pins and type(digest) is str and HEX.fullmatch(digest)
        source = _relative(role)
        assert _sha(_read(source, SOURCE_CAP)) == digest
        pins[role] = (source, digest)
    assert Path(__file__).name == "run_export_composition_r1.py"
    return raw, (path, _sha(raw)), pins


def _scalar(value):
    return value is None or type(value) in (str, int, bool)


def _case_manifest():
    raw = _read(A / "CASE_MANIFEST_R1.json", SOURCE_CAP)
    value = _json(raw)
    assert type(value) is dict and set(value) == {"status", "cases"}
    assert value["status"] == "ROOT_ACCEPTED_NATIVE_CASE_MANIFEST"
    rows = value["cases"]
    assert type(rows) is list and len(rows) == 2
    assert all(type(row) is dict for row in rows)
    assert [row.get("role") for row in rows] == ["smoke", "suite"]
    cases = {}
    for row in rows:
        required = {"role", "class", "args", "status", "names", "caseShape"}
        assert required <= set(row) <= required | {"expectedScalars"}
        role = row["role"]
        assert row["class"] == CASE_CLASSES[role]
        expected_args = {"smoke": ["SMOKE", "TASK_RUN_ROOT"],
                         "suite": ["SUITE", "TASK_RUN_ROOT"]}[role]
        assert row["args"] == expected_args
        assert row["status"] == "PASS"
        assert row["caseShape"] == "records"
        names = row["names"]
        assert names == CASE_NAMES[role]
        assert type(names) is list and 0 < len(names) <= 4096
        assert all(type(name) is str and 0 < len(name) <= 256 for name in names)
        assert len(set(names)) == len(names)
        expected = row.get("expectedScalars", {})
        assert type(expected) is dict
        assert not ({"status", "names", "cases", "assertions"} & set(expected))
        assert all(type(key) is str and re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*", key)
                   and _scalar(item) for key, item in expected.items())
        cases[role] = row
    return raw, cases


def _classes(classes, inventory):
    classes = _safe(classes)
    assert stat.S_ISDIR(os.lstat(str(classes)).st_mode)
    rows = inventory.get("classes")
    assert type(rows) is list and 0 < len(rows) <= CLASS_COUNT_CAP
    assert type(inventory.get("classCount")) is int
    assert type(inventory.get("classBytes")) is int
    assert len(rows) == inventory["classCount"]
    assert 0 < inventory["classBytes"] <= CLASS_BYTES_CAP
    expected = {}
    expected_total = 0
    for row in rows:
        assert type(row) is dict and set(row) == {"path", "bytes", "sha256", "major"}
        name = row["path"]
        _relative(name)
        assert name.endswith(".class") and name not in expected
        assert type(row["bytes"]) is int and row["bytes"] > 0
        expected_total += row["bytes"]
        assert expected_total <= CLASS_BYTES_CAP
        assert type(row["sha256"]) is str and HEX.fullmatch(row["sha256"])
        assert type(row["major"]) is int and row["major"] == MAX_MAJOR
        expected[name] = row
    assert expected_total == inventory["classBytes"]
    actual = {}
    walk_total = 0
    pending = [classes]
    while pending:
        directory = pending.pop()
        _safe(directory)
        for path in directory.iterdir():
            item = os.lstat(str(path))
            assert not stat.S_ISLNK(item.st_mode)
            assert not (getattr(item, "st_file_attributes", 0) & REPARSE_POINT)
            if stat.S_ISDIR(item.st_mode):
                pending.append(path)
                continue
            assert stat.S_ISREG(item.st_mode) and path.suffix == ".class"
            name = path.relative_to(classes).as_posix()
            assert name in expected and name not in actual and len(actual) < CLASS_COUNT_CAP
            assert item.st_size == expected[name]["bytes"]
            size, digest, header = _digest(path, CLASS_BYTES_CAP)
            walk_total += size
            assert walk_total <= CLASS_BYTES_CAP
            assert len(header) == 8 and header[:4] == b"\xca\xfe\xba\xbe"
            actual[name] = (size, digest, int.from_bytes(header[6:8], "big"))
    assert set(actual) == set(expected)
    total = 0
    for name, (size, digest, major) in actual.items():
        row = expected[name]
        assert (size, digest, major) == (row["bytes"], row["sha256"], row["major"])
        total += size
        assert total <= CLASS_BYTES_CAP
    assert total == inventory["classBytes"]
    package = "com/lushprojects/circuitjs1/client/"
    for name in ("ExportCompositionCanary.class", "CurrentBodyOwnership.class",
                 "CurrentV2InputSetup.class", "CurrentV2InputSetup$Factory.class",
                 "CurrentV2InputSetup$State.class", "CurrentAssemblyMetadata.class",
                 "CohortTerminalTelemetryInputV2.class",
                 "CohortTerminalTelemetryStudy.class", "CurrentV2CanonicalCodec.class",
                 "CohortTerminalTelemetryStudy$SetupMeter.class", "CohortTerminalTelemetryStudy$SetupResult.class",
                 "ReceiptContentBinding.class", "FailureCohortRoutingStudy$Meter.class"):
        assert package + name in actual
    for stem in ("ActualGeneratorBranchOrderStudy$Study", "ActualGeneratorBranchOrderStudy$Mode",
                 "ActualGeneratorBranchOrderStudy$ArmRun", "CohortTerminalTelemetryStudy$OriginalArm"):
        assert not any(name == package + stem + ".class" or name.startswith(package + stem + "$")
                       for name in actual)
    # The public legacy input symbol is a pinned denyfixture, not actual input.
    carrier = actual[package + "CohortTerminalTelemetryStudy$ArmResult.class"]
    assert carrier == (811, "7d9cbb2c16132df342829c8174eb8565e41d33efb45ae120e0f011f4f1fc6ca0", 51)
    return len(actual), total


def _recheck(pins, jars, classes, inventory, tools):
    for option in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "JDK_JAVAC_OPTIONS"):
        assert not os.environ.get(option), option
    root.tools_pinned()
    for path, digest in pins:
        assert _sha(_read(path, SOURCE_CAP)) == digest
    assert _sha(_read(helper, SOURCE_CAP)) == HELPER_SHA256
    assert _sha(_read(nested_helper, SOURCE_CAP)) == NESTED_HELPER_SHA256
    for path, size, digest in jars + tools:
        observed_size, observed_digest, _header = _digest(path, TOOL_CAP)
        assert (observed_size, observed_digest) == (size, digest)
    assert {production_prefix + path.name for path in _safe(client).iterdir()
            if path.suffix == ".java" and path.name not in {
                "PcbLayerRoutingPrototype.java", "PhysicalSpecificationDeveloperVerifier.java"
            }} == production_roles
    assert not any(_safe(A / "native-build-r1/empty-sourcepath").iterdir())
    return _classes(classes, inventory)


freeze, freeze_raw, freeze_pins = _freeze()
runtime_control_raw, runtime_control_pin, runtime_pins = _runtime_control(freeze_raw)
runtime_driver_pin = runtime_pins["run_export_composition_r1.py"]
case_manifest_raw, case_definitions = _case_manifest()
assert _sha(case_manifest_raw) == runtime_pins["CASE_MANIFEST_R1.json"][1]
source_input, source_input_raw, source_input_pins = _source_inputs(
    "SOURCE_INPUT_R1.json", SOURCE_INPUT_SHA256, AUDIT_BASE_COMMIT,
    "SYNTHETIC_CURRENT_RESULT_EXPORT_COMPOSITION_SOURCE_AUDIT", SOURCE_NAMES, envelope=True)
native_source_input, native_source_input_raw, native_source_input_pins = _source_inputs(
    "SOURCE_INPUT_R2.json", NATIVE_SOURCE_INPUT_SHA256, BASE_COMMIT,
    "SYNTHETIC_CURRENT_EXPORT_COMPOSITION_SOURCE_REFERENCES", NATIVE_SOURCE_NAMES, envelope=False)
manifest_path = A / "BUILD_MANIFEST_R1.json"
manifest_raw = _read(manifest_path, SOURCE_CAP)
manifest = _json(manifest_raw)
assert type(manifest) is dict
assert manifest.get("status") == "export_composition_compile_pass"
assert manifest.get("scope") == "SYNTHETIC_CURRENT_RESULT_EXPORT_COMPOSITION"
assert manifest.get("sourceCount") == 863 and type(manifest["sourceCount"]) is int
assert manifest.get("sourceFreezeSha256") == _sha(freeze_raw)
assert manifest.get("sourceInputSha256") == SOURCE_INPUT_SHA256
assert manifest.get("sourceFindingsSha256") == SOURCE_FINDINGS_SHA256
assert manifest.get("exportContractSha256") == EXPORT_CONTRACT_SHA256
assert manifest.get("runtime") == "NOT_RUN"
assert manifest.get("allMajor51") is True
assert manifest.get("nativeProducerImplementationAbsent") is True
assert manifest.get("currentBodyOwnershipClassPresent") is True
assert manifest.get("exportCompositionCanaryClassPresent") is True
assert manifest.get("inputSetupFactoryMetadataClassesPresent") is True
assert manifest.get("normalHostAcceptance") == "NONE"
assert manifest.get("genuineEnvelope") == "UNSPENT_GATED"
for key in ("actualPriorDataReads", "oldDataClassReads", "protectedHeldoutReads", "nativeProducerCalls"):
    assert type(manifest.get(key)) is int and manifest[key] == 0

build_path = A / "NATIVE_BUILD_INPUT_R1.json"
build_raw = _read(build_path, SOURCE_CAP)
assert _sha(build_raw) == manifest.get("buildInputSha256")
build = _json(build_raw)
assert type(build) is dict and build.get("scope") == manifest["scope"]
assert build.get("sourceCount") == 863 and build.get("productionSourceCount") == 848
assert build.get("sourceFreezeSha256") == _sha(freeze_raw)
assert build.get("sourceInputSha256") == SOURCE_INPUT_SHA256
assert build.get("sourceFindingsSha256") == SOURCE_FINDINGS_SHA256
assert build.get("exportContractSha256") == EXPORT_CONTRACT_SHA256
assert build.get("sourceFiles") == manifest.get("sourceFiles")
assert build.get("targetMajor") == 51 and build.get("compileLimitMs") == 60000
assert build.get("sourceInputCount") == 15 and build.get("baseCommit") == BASE_COMMIT
assert build.get("replacementOwnerStudySourceCount") == 3
assert build.get("publicCanaryDenyFixtureSourceCount") == 5
assert build.get("componentSourceCount") == 7
assert build.get("sourceCapBytes") == SOURCE_CAP
assert build.get("classCountCap") == CLASS_COUNT_CAP
assert build.get("classBytesCap") == CLASS_BYTES_CAP
assert build.get("argvRoles") == [
    "-source", "7", "-target", "7", "-encoding", "UTF-8",
    "-classpath", "FRESH_CLASSES;GWT_USER;GWT_DEV", "-sourcepath", "EMPTY_SOURCEPATH",
    "-d", "FRESH_CLASSES", "@EXPLICIT_SOURCE_LIST",
]
assert build.get("sourceInputFiles") == [
    {"role": "A/source/" + row["name"], "bytes": row["bytes"], "sha256": row["sha256"]}
    for row in source_input["rows"]
]
assert build.get("nativeSourceInputFiles") == [
    {"role": "A/source/" + row["name"], "bytes": row["bytes"], "sha256": row["sha256"]}
    for row in native_source_input["rows"]
]
assert build.get("nativeSourceInputCount") == 7
assert build.get("nativeSourceInputSha256") == manifest.get("nativeSourceInputSha256") == NATIVE_SOURCE_INPUT_SHA256

inventory_path = A / "NATIVE_CLASS_INVENTORY_R1.json"
inventory_raw = _read(inventory_path, SOURCE_CAP)
assert _sha(inventory_raw) == manifest.get("classInventorySha256")
inventory = _json(inventory_raw)
assert type(inventory) is dict
assert inventory.get("buildInputSha256") == _sha(build_raw)
for key in ("classCount", "classBytes", "allMajor51", "nativeProducerImplementationAbsent",
            "currentBodyOwnershipClassPresent", "exportCompositionCanaryClassPresent",
            "inputSetupFactoryMetadataClassesPresent",
            "oldDataClassReads", "normalHostAcceptance"):
    assert inventory.get(key) == manifest.get(key)
assert inventory.get("allMajor51") is True
assert inventory.get("nativeProducerImplementationAbsent") is True
assert inventory.get("currentBodyOwnershipClassPresent") is True
assert inventory.get("exportCompositionCanaryClassPresent") is True
assert inventory.get("inputSetupFactoryMetadataClassesPresent") is True
compile_gate_path = A / "NATIVE_COMPILE_GATE_R1.json"
compile_gate_raw = _read(compile_gate_path, SOURCE_CAP)
compile_gate = _json(compile_gate_raw)
assert type(compile_gate) is dict and compile_gate.get("status") == manifest["status"]
assert compile_gate.get("manifestSha256") == _sha(manifest_raw)
assert compile_gate.get("inventorySha256") == _sha(inventory_raw)
assert compile_gate.get("inputSha256") == _sha(build_raw)
assert compile_gate.get("call") == manifest.get("compile")
assert compile_gate.get("allMajor51") is True
for key in ("classCount", "classBytes", "allMajor51", "sourceCount"):
    assert compile_gate.get(key) == manifest.get(key)

pins = [(A / "FROZEN_SOURCE_R1.json", _sha(freeze_raw)),
        (A / "SOURCE_INPUT_R1.json", SOURCE_INPUT_SHA256),
        (A / "SOURCE_INPUT_R2.json", NATIVE_SOURCE_INPUT_SHA256),
        (A / "SOURCE_FINDINGS_R1.md", SOURCE_FINDINGS_SHA256),
        (A / "EXPORT_COMPOSITION_CONTRACT_R1.md", EXPORT_CONTRACT_SHA256),
        (manifest_path, _sha(manifest_raw)), (build_path, _sha(build_raw)),
        (inventory_path, _sha(inventory_raw)), (compile_gate_path, _sha(compile_gate_raw))]
pins.extend(freeze_pins.values())
pins.extend(source_input_pins)
pins.extend(native_source_input_pins)
pins.append(runtime_control_pin)
pins.extend(runtime_pins.values())
classes = A / "native-build-r1/classes"
argfile = A / "native-build-r1/compiler-sources.txt"
arg_raw = _read(argfile, SOURCE_CAP)
assert build.get("sourceList") == {
    "role": "EXPLICIT_SOURCE_LIST", "bytes": len(arg_raw), "sha256": _sha(arg_raw)
}
pins.append((argfile, _sha(arg_raw)))
lines = arg_raw.decode("utf-8").splitlines()
source_rows = manifest["sourceFiles"]
assert type(source_rows) is list and len(source_rows) == len(lines) == 863
assert arg_raw.endswith(b"\n")
F = _safe(root.F)
client = F / "src/com/lushprojects/circuitjs1/client"
public_prefix = "F/tests/qualification/rb100-layout/authored-flat-work-plan-r1/"
production_prefix = "F/src/com/lushprojects/circuitjs1/client/"
a_source_roles = {
    "A/native-build-r1/source/FailureCohortRoutingStudy.java",
    "A/native-build-r1/source/PcbLayerRoutingPrototype.java",
    "A/native-build-r1/source/CohortTerminalTelemetryStudy.java",
    "A/native-build-r1/source/ReceiptContentBinding.java",
    "A/source/CurrentV2CanonicalCodec.java", "A/source/CurrentBodyOwnership.java",
    "A/ExportCompositionCanary.java", "A/source/CurrentV2InputSetup.java",
    "A/source/CurrentAssemblyMetadata.java", "A/emitted/CohortTerminalTelemetryInputV2.java",
}
compiled_roles = set()
compiled_basenames = set()
production_roles = set()
for line, row in zip(lines, source_rows):
    assert type(row) is dict and set(row) == {"role", "bytes", "sha256"}
    role, size, digest = row["role"], row["bytes"], row["sha256"]
    assert type(role) is str and role not in compiled_roles
    assert type(size) is int and size > 0
    assert type(digest) is str and HEX.fullmatch(digest)
    assert role.startswith(("A/", "F/"))
    relative = role[2:]
    _relative(relative)
    base = A if role.startswith("A/") else F
    path = base.joinpath(*relative.split("/"))
    assert path.name.endswith(".java") and path.name not in compiled_basenames
    if role.startswith(production_prefix):
        assert role[len(production_prefix):] == path.name
        assert path.name not in {"PcbLayerRoutingPrototype.java", "PhysicalSpecificationDeveloperVerifier.java"}
        production_roles.add(role)
    elif role.startswith(public_prefix):
        assert role[len(public_prefix):] == path.name
        assert PUBLIC_SOURCE_PINS.get(path.name) == digest
    else:
        assert role in a_source_roles
    assert line.startswith('"') and line.endswith('"') and len(line) > 2
    assert line == '"' + str(path).replace("\\", "/") + '"'
    source_raw = _read(path, SOURCE_CAP)
    assert (len(source_raw), _sha(source_raw)) == (size, digest)
    compiled_roles.add(role)
    compiled_basenames.add(path.name)
    pins.append((path, digest))
assert len(production_roles) == 848
assert compiled_roles == (production_roles | a_source_roles |
                          {public_prefix + name for name in PUBLIC_SOURCE_PINS})
assert {production_prefix + path.name for path in _safe(client).iterdir()
        if path.suffix == ".java" and path.name not in {
            "PcbLayerRoutingPrototype.java", "PhysicalSpecificationDeveloperVerifier.java"
        }} == production_roles
for emitted, original in (
        ("native-build-r1/source/FailureCohortRoutingStudy.java", "source/SHARED_OWNER.java"),
        ("native-build-r1/source/PcbLayerRoutingPrototype.java", "source/NATIVE_PRIVATE_PCB.java"),
        ("native-build-r1/source/CohortTerminalTelemetryStudy.java", "source/CURRENT_STUDY.java"),
        ("native-build-r1/source/ReceiptContentBinding.java", "source/CURRENT_RECEIPT_BINDING.java")):
    assert _read(A / emitted, SOURCE_CAP) == _read(A / original, SOURCE_CAP)
driver_raw = _read(A / "build_export_composition_r1.py", SOURCE_CAP)
assert build.get("driver") == {"role": "ROOT_FRESH_EXPORT_COMPOSITION_BUILD_DRIVER",
                               "bytes": len(driver_raw), "sha256": _sha(driver_raw)}

jar_root = g.E / ".tools/gwt-2.7.0"
jars = [(jar_root / name, size, digest) for name, size, digest in JAR_INPUTS]
dependency_rows = [{"role": name, "bytes": size, "sha256": digest}
                   for name, size, digest in JAR_INPUTS]
assert build.get("dependencies") == manifest.get("dependencies") == dependency_rows
tools = []
tool_rows = []
for (path, expected), role in zip(root.TOOLS, (
        "SELECTED_PYTHON", "SELECTED_POWERSHELL", "SELECTED_JDK8_JAVAC", "SELECTED_JDK8_JAVA",
        "ROOT_PROCESS_WRAPPER", "VERIFIER_ISOLATION")):
    size, digest, _header = _digest(path, TOOL_CAP)
    assert digest == expected
    tools.append((path, size, digest))
    tool_rows.append({"role": role, "bytes": size, "sha256": digest})
assert build.get("tools") == manifest.get("tools") == tool_rows
tools = tuple(tools)
jars = tuple(jars)
class_count, class_bytes = _recheck(tuple(pins), jars, classes, inventory, tools)
assert (class_count, class_bytes) == (manifest["classCount"], manifest["classBytes"])
classpath_entries = [classes] + [path for path, _size, _digest_value in jars]
classpath = os.pathsep.join(map(str, classpath_entries))
assert len(classpath_entries) == 3
assert os.path.normcase(str(g.D)) not in os.path.normcase(classpath)
compile_directory = A / "native-export-composition-fresh-jdk8-compile-r1"
compile_argv = ["-source", "7", "-target", "7", "-encoding", "UTF-8", "-classpath", classpath,
                "-sourcepath", str(A / "native-build-r1/empty-sourcepath"),
                "-d", str(classes), "@" + str(argfile)]
COMPILE_STDERR = (
    b"warning: [options] bootstrap class path not set in conjunction with -source 1.7\r\n"
    b"Note: Some input files use unchecked or unsafe operations.\r\n"
    b"Note: Recompile with -Xlint:unchecked for details.\r\n1 warning\r\n",
    b"warning: [options] bootstrap class path not set in conjunction with -source 1.7\r\n1 warning\r\n",
)


def _request(directory, exe, argv, *, required=True):
    request_path = directory / "request.json"
    if not os.path.lexists(request_path):
        assert not required, "PROCESS_REQUEST_MISSING"
        return
    expected = {"FilePath": str(exe), "Arguments": argv, "LimitMs": 60000}
    assert _json(_read(request_path, SOURCE_CAP)) == expected
    process_path = directory / "process.json"
    if os.path.lexists(process_path):
        process = _json(_read(process_path, SOURCE_CAP))
        assert type(process) is dict
        assert process.get("FilePath") == str(exe) and process.get("Arguments") == argv


def _audit_compile():
    _request(compile_directory, g.JDK / "javac.exe", compile_argv)
    assert g.audit_call(compile_directory, 0) == manifest["compile"]
    assert _read(compile_directory / "stdout.txt", SOURCE_CAP, allow_empty=True) == b""
    assert _read(compile_directory / "stderr.txt", SOURCE_CAP) in COMPILE_STDERR


def _summary(raw, definition, fixture_root):
    value = _json(raw)
    assert type(value) is dict
    canonical = json.dumps(value, separators=(",", ":")).encode("ascii")
    assert raw in (canonical + b"\n", canonical + b"\r\n"), "EXACT_ONE_JSON_LINE"
    expected = definition.get("expectedScalars", {})
    required = {"status", "mode", "names", "cases"} | set(expected)
    assert set(value) == required
    assert list(value)[:4] == ["mode", "status", "names", "cases"]
    assert value["status"] == definition["status"]
    assert value["names"] == definition["names"]
    assert type(value["names"]) is list and len(set(value["names"])) == len(value["names"])
    for key, item in expected.items():
        assert type(value[key]) is type(item) and value[key] == item
    assert value.get("mode") == ("SMOKE" if definition["role"] == "smoke" else "SUITE")
    records = value["cases"]
    assert type(records) is list and len(records) == len(definition["names"])
    for record, name in zip(records, definition["names"]):
        assert type(record) is dict
        assert set(record) == RECORD_INTEGER_KEYS | RECORD_STRING_KEYS | RECORD_BOOLEAN_KEYS
        assert all(type(record[key]) is str for key in RECORD_STRING_KEYS)
        assert all(type(record[key]) is int and 0 <= record[key] <= 9223372036854775807
                   for key in RECORD_INTEGER_KEYS)
        assert all(type(record[key]) is bool for key in RECORD_BOOLEAN_KEYS)
        assert record["name"] == name and record["status"] == "PASS"
        assert record["phase"] == "complete" and record["reason"] == "OK"
        assert record["error"] == record["cleanupError"] == ""
        assert record["stdoutRestored"] is True
        assert record["workFinalizedSlots"] == 34
        assert record["receiptBytes"] == 4096
        assert 0 < record["resultBytesObserved"] <= 32768
        assert len(record["resultHex"]) == 2 * record["resultBytesObserved"] <= 65536
        assert re.fullmatch(r"(?:[0-9a-f]{2})*", record["resultHex"])
        assert len(record["markerHex"]) <= 1312
        assert re.fullmatch(r"(?:[0-9a-f]{2})*", record["markerHex"])
        assert record["pathEntries"] == len((fixture_root / name / "op-1.jsonl").parts)
        if definition["role"] == "smoke":
            assert record["expectedFailure"] == ""
            assert record["cleanupDisposition"] == "ALREADY_CLOSED_AND_REMOVED"
            assert record["metricsReturned"] is True
            assert record["meterOpen"] is record["meterDiscarded"] is record["receiptMutationApplied"] is False
            assert record["markerHex"] and record["metricsMarkerBytes"] <= 656
            assert len(record["markerHex"]) == 2 * record["metricsMarkerBytes"]
        else:
            assert record["expectedFailure"] == "FINAL_SHA_MISMATCH"
            assert record["cleanupDisposition"] == "EXPECTED_PARTIAL_EXPORT_METER_DISCARDED_AND_REMOVED"
            assert record["meterOpen"] is record["meterDiscarded"] is record["receiptMutationApplied"] is True
            assert record["metricsReturned"] is False
            assert record["markerHex"] == ""
            assert record["exportMeasureUnits"] == record["exportMeasureNanos"] == 0
            assert all(record[key] == 0 for key in ABSENT_NEGATIVE_METRICS)
    return value


def _empty_fixture(path):
    path = _safe(path)
    assert stat.S_ISDIR(os.lstat(str(path)).st_mode)
    assert not any(path.iterdir()), "FIXTURE_ROOT_NOT_EMPTY"
    return path


def _accepted_smoke():
    directory = A / "native-run-smoke-r1"
    gate_path, input_path = directory / "gate.json", directory / "input.json"
    gate_raw, input_raw = _read(gate_path, REPORT_CAP), _read(input_path, REPORT_CAP)
    gate, prior_input = _json(gate_raw), _json(input_raw)
    assert type(gate) is dict and type(prior_input) is dict
    assert gate.get("caseRole") == prior_input.get("caseRole") == "smoke"
    assert prior_input.get("scope") == "SYNTHETIC_CURRENT_RESULT_EXPORT_COMPOSITION"
    assert prior_input.get("caseDefinition") == case_definitions["smoke"]
    for key, digest in (("sourceFreezeSha256", _sha(freeze_raw)),
                        ("sourceInputSha256", SOURCE_INPUT_SHA256),
                        ("nativeSourceInputSha256", NATIVE_SOURCE_INPUT_SHA256),
                        ("sourceFindingsSha256", SOURCE_FINDINGS_SHA256),
                        ("exportContractSha256", EXPORT_CONTRACT_SHA256),
                        ("buildManifestSha256", _sha(manifest_raw)),
                        ("compileGateSha256", _sha(compile_gate_raw)),
                        ("runtimeControlSha256", _sha(runtime_control_raw)),
                        ("caseManifestSha256", _sha(case_manifest_raw)),
                        ("classInventorySha256", _sha(inventory_raw))):
        assert gate.get(key) == prior_input.get(key) == digest
    assert gate.get("runInputSha256") == _sha(input_raw)
    assert gate.get("allSourceToolJarClassPinsRechecked") is True
    assert gate.get("unchangedCompiledSources") is prior_input.get("unchangedCompiledSources") is True
    assert gate.get("classCount") == prior_input.get("classCount") == class_count
    assert gate.get("classBytes") == prior_input.get("classBytes") == class_bytes
    assert gate.get("driverSha256") == prior_input.get("driverSha256") == runtime_driver_pin[1]
    smoke_fixture = _empty_fixture(directory / "fixture-root")
    assert prior_input.get("fixtureRootEmptyBefore") is True
    assert gate.get("fixtureRootEmptyAfter") is True
    assert gate.get("taskRunRootRole") == prior_input.get("taskRunRootRole") == "TASK_RUN_ROOT"
    smoke_argv = ["-ea", "-Xmx512m", "-cp", classpath, CASE_CLASSES["smoke"],
                  "SMOKE", str(smoke_fixture)]
    assert prior_input.get("argv") == smoke_argv and prior_input.get("limitMs") == 60000
    phase = A / "native-export-composition-smoke-r1"
    _request(phase, g.JDK / "java.exe", smoke_argv)
    assert g.audit_call(phase, 0) == gate.get("call")
    out_path, err_path = phase / "stdout.txt", phase / "stderr.txt"
    out, err = _read(out_path, REPORT_CAP), _read(err_path, REPORT_CAP, allow_empty=True)
    assert err == b"" and gate.get("stdoutSha256") == _sha(out) and gate.get("stderrSha256") == _sha(err)
    summary = _summary(out, case_definitions["smoke"], smoke_fixture)
    assert gate.get("summary") == summary and gate.get("status") == summary["status"]
    return [(gate_path, _sha(gate_raw)), (input_path, _sha(input_raw)),
            (out_path, _sha(out))]


_audit_compile()
if MODE == "suite":
    pins.extend(_accepted_smoke())  # Authenticated actual-Meter smoke precedes the suite JVM.
definition = case_definitions[MODE]
run_directory = A / ("native-run-" + MODE + "-r1")
assert not os.path.lexists(A / PHASE)
assert not os.path.lexists(A / ("FAIL_" + PHASE + ".json"))
run_directory.mkdir()
_safe(run_directory)
fixture_root = run_directory / "fixture-root"
fixture_root.mkdir()
_empty_fixture(fixture_root)
arguments = [definition["args"][0], str(fixture_root)]
java_argv = ["-ea", "-Xmx512m", "-cp", classpath, definition["class"]] + arguments
run_input = {
    "scope": "SYNTHETIC_CURRENT_RESULT_EXPORT_COMPOSITION", "caseRole": MODE,
    "sourceFreezeSha256": _sha(freeze_raw), "sourceInputSha256": SOURCE_INPUT_SHA256,
    "nativeSourceInputSha256": NATIVE_SOURCE_INPUT_SHA256,
    "sourceFindingsSha256": SOURCE_FINDINGS_SHA256,
    "exportContractSha256": EXPORT_CONTRACT_SHA256,
    "runtimeControlSha256": _sha(runtime_control_raw), "unchangedCompiledSources": True,
    "caseManifestSha256": _sha(case_manifest_raw), "caseDefinition": definition,
    "buildManifestSha256": _sha(manifest_raw), "classInventorySha256": _sha(inventory_raw),
    "compileGateSha256": _sha(compile_gate_raw), "classCount": class_count,
    "classBytes": class_bytes, "allMajor51": True, "java": tool_rows[3],
    "argv": java_argv, "limitMs": 60000,
    "taskRunRootRole": "TASK_RUN_ROOT", "fixtureRootEmptyBefore": True,
    "driverSha256": runtime_driver_pin[1], "nativeProducerCalls": 0,
    "actualPriorDataReads": 0, "oldDataClassReads": 0, "protectedHeldoutReads": 0,
    "normalHostAcceptance": "NONE", "genuineEnvelope": "UNSPENT_GATED",
}
run_input_path = run_directory / "input.json"
pins.append((run_input_path, _write(run_input_path, run_input)))
assert _recheck(tuple(pins), jars, classes, inventory, tools) == (class_count, class_bytes)
_audit_compile()
if MODE == "suite":
    _accepted_smoke()
_empty_fixture(fixture_root)
try:
    call = root.call(PHASE, g.JDK / "java.exe", java_argv, limit=60000)
    _request(A / PHASE, g.JDK / "java.exe", java_argv)
    assert g.audit_call(A / PHASE, 0) == call
    stdout = _read(A / PHASE / "stdout.txt", REPORT_CAP)
    stderr = _read(A / PHASE / "stderr.txt", REPORT_CAP, allow_empty=True)
    assert stderr == b"", "NATIVE_STDERR"
    summary = _summary(stdout, definition, fixture_root)
finally:
    # Failures preserve their existing request/receipts/markers and receive no success gate.
    assert _recheck(tuple(pins), jars, classes, inventory, tools) == (class_count, class_bytes)
    _audit_compile()
    _request(A / PHASE, g.JDK / "java.exe", java_argv, required=False)
    if MODE == "suite":
        _accepted_smoke()
    _empty_fixture(fixture_root)

assert g.audit_call(A / PHASE, 0) == call
assert _read(A / PHASE / "stdout.txt", REPORT_CAP) == stdout
assert _read(A / PHASE / "stderr.txt", REPORT_CAP, allow_empty=True) == stderr
assert _recheck(tuple(pins), jars, classes, inventory, tools) == (class_count, class_bytes)
_audit_compile()
assert g.audit_call(A / PHASE, 0) == call
_empty_fixture(fixture_root)
_write(run_directory / "gate.json", {
    "status": summary["status"], "caseRole": MODE, "summary": summary, "call": call,
    "runInputSha256": _sha(_read(run_input_path, REPORT_CAP)),
    "stdoutSha256": _sha(stdout), "stderrSha256": _sha(stderr),
    "sourceFreezeSha256": _sha(freeze_raw), "buildManifestSha256": _sha(manifest_raw),
    "sourceInputSha256": SOURCE_INPUT_SHA256,
    "nativeSourceInputSha256": NATIVE_SOURCE_INPUT_SHA256, "compileGateSha256": _sha(compile_gate_raw),
    "sourceFindingsSha256": SOURCE_FINDINGS_SHA256,
    "exportContractSha256": EXPORT_CONTRACT_SHA256,
    "runtimeControlSha256": _sha(runtime_control_raw), "caseManifestSha256": _sha(case_manifest_raw),
    "driverSha256": runtime_driver_pin[1], "unchangedCompiledSources": True,
    "classInventorySha256": _sha(inventory_raw), "allSourceToolJarClassPinsRechecked": True,
    "classCount": class_count, "classBytes": class_bytes,
    "taskRunRootRole": "TASK_RUN_ROOT", "fixtureRootEmptyAfter": True,
    "actualPriorDataReads": 0, "oldDataClassReads": 0, "protectedHeldoutReads": 0,
    "nativeProducerCalls": 0, "normalHostAcceptance": "NONE", "genuineEnvelope": "UNSPENT_GATED",
    "isolatedCleanupDurationMs": None,
})
print(json.dumps(summary, separators=(",", ":")))
