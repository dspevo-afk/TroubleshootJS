"""Root-only fresh synthetic thirteen-key body constructor build."""

from pathlib import Path
import hashlib
import json
import os
import re
import stat
import sys
import types


assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
assert sys.argv[1:] == []
A = Path(os.path.abspath(__file__)).parent
SOURCE_CAP = 4 * 1024 * 1024
CLASS_COUNT_CAP = 10000
CLASS_BYTES_CAP = 268435456
MAX_MAJOR = 51
SOURCE_COUNT = 861
ROLE_HEX = re.compile(r"[0-9a-f]{64}\Z")
REPARSE_POINT = 0x400
SOURCE_INPUT_SHA256 = "b425e75b68349611b7471c98f9562b47adc4b05209faeaec570d17dccb4fef7c"
NATIVE_SOURCE_INPUT_SHA256 = "a03674e7d7f9d9db2491b9ac7bca5899da7eb937f1cade24d6a600472152bfe2"
BASE_COMMIT = "1b22c5edb55b0009ad03037370a5fef72a87112b"
AUDIT_BASE_COMMIT = "1b22c5edb55b0009ad03037370a5fef72a87112b"
SOURCE_NAMES = (
    "CURRENT_STUDY.java", "CurrentV2CanonicalCodec.java", "CurrentAssemblyMetadata.java",
    "CurrentV2InputSetup.java", "CurrentBodyOwnership.java", "context_codec.py",
    "WIRE_R1.md", "PREPAYMENT_R1.md", "OWNERSHIP_CONTRACT_R1.md",
)
NATIVE_SOURCE_NAMES = (
    "SHARED_OWNER.java", "NATIVE_PRIVATE_PCB.java", "CURRENT_RECEIPT_BINDING.java",
    "build_body_ownership_r1.py", "run_body_ownership_r1.py",
    "generate_body_ownership.py", "CurrentV2CodecOracle.java",
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
    "source/CurrentV2CodecOracle.java":
        "644cf35bb681d75dd6c97c1f881f12383c53f8eb3fb4fcfbcc4bccd5d529d4ab",
}
REQUIRED_FREEZE_ROLES = set(FIXED_SOURCE_PINS) | {
    "emitted/CurrentV2CanonicalCodec.java", "RootBodyConstructorCanary.java",
    "build_root_body_r1.py",
}
ALLOWED_FREEZE_ROLES = REQUIRED_FREEZE_ROLES
HELPER_SHA256 = "224cf971a7142be7ea3679555f051ec65c4b2a2e6afab00c329dd9eb0d7372e3"
OLD_HELPER_SHA256 = "4f414bc2a8823e539c8f6ea7aaa0a2ff09d5bd04c092e617f14ff7bb7de78a8b"
PUBLIC_INPUTS = (
    ("AuthoredFlatWorkCanary.java",
     "38def52bfe4d365b3f821099cef0d847eb0819aa69d9f0158d01e4cec999ff42"),
    ("CohortTerminalTelemetryInput.java",
     "bc0ff7ab787537f16e211ccfc4d787059aefef86dea3554d27f063b88e67660f"),
    ("ActualGeneratorBranchOrderStudy.java",
     "855ad071798106d0d419d6382af3251240ad2c342d1542eca27dec7d7e4fc2cd"),
    ("PhysicalSpecificationDeveloperVerifier.java",
     "1e1c4e0517aedad06dd0cea1d75aa8b3ce23abf735ec8a607d014ad1f77827a0"),
    ("FailedFirstFixtures.java",
     "a49ec2911e7f9b36c66f3b9be56841707cd1afc127e3b5d716b0d689d137542c"),
)
JAR_INPUTS = (
    ("gwt-user-2.7.0.jar", 32024115,
     "d3721bcdd7c6855a524801212dd74548967d8fb65cbc212a20890732ff2c6948"),
    ("gwt-dev-2.7.0.jar", 38276750,
     "c7321e367fc24e5c7ad97b74d1cf2980c45fbd7cbe1fd412f8a8ba128f685135"),
)


def _snapshot(st):
    return (st.st_dev, st.st_ino, st.st_mode, st.st_nlink, st.st_size,
            getattr(st, "st_mtime_ns", int(st.st_mtime * 1000000000)),
            getattr(st, "st_ctime_ns", int(st.st_ctime * 1000000000)),
            getattr(st, "st_file_attributes", 0))


def _handle_identity(snapshot):
    return (snapshot[0], snapshot[1], stat.S_IFMT(snapshot[2]), snapshot[4],
            snapshot[5], snapshot[7])


def _assert_safe_path(path):
    absolute = Path(os.path.abspath(str(path)))
    try:
        resolved = absolute.resolve(strict=True)
    except (OSError, RuntimeError, ValueError):
        raise AssertionError("SOURCE_PATH") from None
    assert os.path.normcase(str(absolute)) == os.path.normcase(str(resolved))
    for current in list(reversed(absolute.parents)) + [absolute]:
        try:
            item = os.lstat(str(current))
        except OSError:
            raise AssertionError("SOURCE_PATH") from None
        assert not stat.S_ISLNK(item.st_mode)
        assert not (getattr(item, "st_file_attributes", 0) & REPARSE_POINT)
    return absolute


def _read_stable(path, cap):
    path = _assert_safe_path(path)
    before = os.lstat(str(path))
    before_snapshot = _snapshot(before)
    assert stat.S_ISREG(before.st_mode) and 0 < before.st_size <= cap
    try:
        with path.open("rb") as source:
            opened = _snapshot(os.fstat(source.fileno()))
            assert _handle_identity(before_snapshot) == _handle_identity(opened)
            raw = source.read(cap + 1)
            after_handle = _snapshot(os.fstat(source.fileno()))
    except OSError:
        raise AssertionError("SOURCE_READ") from None
    after_snapshot = _snapshot(os.lstat(str(path)))
    assert before_snapshot == after_snapshot
    assert _handle_identity(opened) == _handle_identity(after_handle)
    assert len(raw) == before.st_size and len(raw) <= cap
    _assert_safe_path(path)
    return raw


def _file_digest(path, cap):
    path = _assert_safe_path(path)
    before = os.lstat(str(path))
    before_snapshot = _snapshot(before)
    assert stat.S_ISREG(before.st_mode) and 0 < before.st_size <= cap
    digest = hashlib.sha256()
    header = bytearray()
    total = 0
    try:
        with path.open("rb") as source:
            opened = _snapshot(os.fstat(source.fileno()))
            assert _handle_identity(before_snapshot) == _handle_identity(opened)
            while True:
                block = source.read(min(1024 * 1024, cap - total + 1))
                if not block:
                    break
                total += len(block)
                assert total <= cap
                if len(header) < 8:
                    header.extend(block[:8 - len(header)])
                digest.update(block)
            after_handle = _snapshot(os.fstat(source.fileno()))
    except OSError:
        raise AssertionError("SOURCE_READ") from None
    assert before_snapshot == _snapshot(os.lstat(str(path)))
    assert _handle_identity(opened) == _handle_identity(after_handle)
    assert total == before.st_size
    _assert_safe_path(path)
    return total, digest.hexdigest(), bytes(header)


def _strict_json(raw):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            assert key not in result, "JSON_DUPLICATE"
            result[key] = value
        return result

    return json.loads(raw.decode("utf-8"), object_pairs_hook=unique,
                      parse_constant=lambda _value: (_ for _ in ()).throw(
                          AssertionError("JSON_CONSTANT")))


def _pinned_source(path, digest):
    raw = _read_stable(path, SOURCE_CAP)
    assert hashlib.sha256(raw).hexdigest() == digest, "SOURCE_PIN"
    return raw


def _source_info(path, role):
    raw = _read_stable(path, SOURCE_CAP)
    return {"role": role, "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}


_assert_safe_path(A)
driver_path = _assert_safe_path(Path(os.path.abspath(__file__)))
helper = A.parent / "tsj-q100-flat-work-plan-24224fbba26b4bee90da24689ccc59a6/root_h_gates_r3.py"
old_helper = A.parent / "tsj-q100-selected-head-wire-096f9160b79240859a393749151619f2/root_gates_r1.py"
helper_raw = _pinned_source(helper, HELPER_SHA256)
_pinned_source(old_helper, OLD_HELPER_SHA256)
root = types.ModuleType("_root_body_build_root")
root.__file__ = str(helper)
exec(compile(helper_raw, str(helper), "exec"), root.__dict__)
g = root.g
root.H = A
g.R = A
F = root.F
assert Path(root.OLD_HELPER) == old_helper
assert len(root.TOOLS) == 6


def _tools_pinned():
    # The historical g.tools_pinned() includes unrelated old Sources: never call it.
    root.tools_pinned()
    _pinned_source(helper, HELPER_SHA256)
    _pinned_source(old_helper, OLD_HELPER_SHA256)
    for path, expected in root.TOOLS:
        _size, digest, _header = _file_digest(path, CLASS_BYTES_CAP)
        assert digest == expected, "TOOL_PIN"


def _environment_pinned():
    for name in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "JDK_JAVAC_OPTIONS"):
        assert not os.environ.get(name), name


def _freeze_roles():
    raw = _read_stable(A / "FROZEN_SOURCE_R1.json", SOURCE_CAP)
    value = _strict_json(raw)
    assert type(value) is dict
    assert value.get("status") == "ROOT_ACCEPTED_FINAL_SOURCE_FOR_SYNTHETIC_GATES"
    rows = value.get("files")
    assert type(rows) is list and 0 < len(rows) <= len(ALLOWED_FREEZE_ROLES)
    pins = {}
    for row in rows:
        assert type(row) is dict and set(row) == {"role", "sha256"}
        role, digest = row["role"], row["sha256"]
        assert type(role) is str and role in ALLOWED_FREEZE_ROLES
        assert type(digest) is str and ROLE_HEX.fullmatch(digest)
        assert role not in pins
        if role in FIXED_SOURCE_PINS:
            assert digest == FIXED_SOURCE_PINS[role]
        path = A.joinpath(*role.split("/"))
        _pinned_source(path, digest)
        pins[role] = (path, digest)
    assert REQUIRED_FREEZE_ROLES.issubset(pins)
    return raw, pins


def _source_input(filename, expected_digest, base, scope, names):
    raw = _pinned_source(A / filename, expected_digest)
    value = _strict_json(raw)
    assert type(value) is dict and set(value) == {"status", "base", "scope", "rows", "genuineEnvelope"}
    assert value["status"] == "PINNED_SOURCE_ONLY_NO_DATA"
    assert value["base"] == base and value["scope"] == scope
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
        assert type(digest) is str and ROLE_HEX.fullmatch(digest)
        path = A / "source" / name
        data = _pinned_source(path, digest)
        assert len(data) == size
        if "source/" + name in FIXED_SOURCE_PINS:
            assert digest == FIXED_SOURCE_PINS["source/" + name]
        pins.append((path, digest))
    return raw, tuple(pins)

_tools_pinned()
_environment_pinned()
freeze_raw, freeze_pins = _freeze_roles()
source_input_raw, source_input_pins = _source_input(
    "SOURCE_INPUT_R1.json", SOURCE_INPUT_SHA256, AUDIT_BASE_COMMIT,
    "CURRENT_THIRTEEN_KEY_ROOT_BODY_SOURCE_AUDIT", SOURCE_NAMES)
native_source_input_raw, native_source_input_pins = _source_input(
    "SOURCE_INPUT_R2.json", NATIVE_SOURCE_INPUT_SHA256, BASE_COMMIT,
    "ROOT_CONSTRUCTOR_NATIVE_AND_ORACLE_SOURCE", NATIVE_SOURCE_NAMES)

client = F / "src/com/lushprojects/circuitjs1/client"
excluded = {"PcbLayerRoutingPrototype.java", "PhysicalSpecificationDeveloperVerifier.java"}


def _production_sources():
    _assert_safe_path(client)
    result = sorted((path for path in client.iterdir()
                     if path.suffix == ".java" and path.name not in excluded),
                    key=lambda path: path.name)
    assert len(result) == 848
    for path in result:
        _assert_safe_path(path)
    return result


production_sources = _production_sources()
public_root = F / "tests/qualification/rb100-layout/authored-flat-work-plan-r1"
public_sources = []
for name, digest in PUBLIC_INPUTS:
    path = public_root / name
    _pinned_source(path, digest)
    public_sources.append(path)

# Exclusive creation prevents stale class reuse or overwriting a prior attempt.
work = A / "native-build-r1"
work.mkdir()
_assert_safe_path(work)
classes = work / "classes"
empty_sourcepath = work / "empty-sourcepath"
staged = work / "source"
for directory in (classes, empty_sourcepath, staged):
    directory.mkdir()
    _assert_safe_path(directory)
    assert not any(directory.iterdir())


def _stage_exact(source, name):
    target = staged / name
    raw = _read_stable(source, SOURCE_CAP)
    with target.open("xb") as out:
        assert out.write(raw) == len(raw)
    assert _read_stable(target, SOURCE_CAP) == raw
    return target, hashlib.sha256(raw).hexdigest()


staged_pins = (
    _stage_exact(A / "source/SHARED_OWNER.java", "FailureCohortRoutingStudy.java"),
    _stage_exact(A / "source/NATIVE_PRIVATE_PCB.java", "PcbLayerRoutingPrototype.java"),
    _stage_exact(A / "source/CURRENT_STUDY.java", "CohortTerminalTelemetryStudy.java"),
    _stage_exact(A / "source/CURRENT_RECEIPT_BINDING.java", "ReceiptContentBinding.java"),
)
staged_shared, staged_pcb, staged_study, staged_receipt = (path for path, _sha in staged_pins)
codec_source = A / "emitted/CurrentV2CanonicalCodec.java"
ownership_source = A / "source/CurrentBodyOwnership.java"
root_canary = A / "RootBodyConstructorCanary.java"
codec_oracle = A / "source/CurrentV2CodecOracle.java"
compile_sources = (production_sources + [staged_shared, staged_pcb, staged_study] +
                   public_sources + [codec_source, staged_receipt,
                                     ownership_source, root_canary, codec_oracle])
assert (len(production_sources), len(public_sources)) == (848, 5)
assert len(compile_sources) == SOURCE_COUNT
assert len({path.name for path in compile_sources}) == SOURCE_COUNT
assert len({os.path.normcase(str(path)) for path in compile_sources}) == SOURCE_COUNT
source_pins = {path: hashlib.sha256(_read_stable(path, SOURCE_CAP)).hexdigest()
               for path in compile_sources}
for path, digest in staged_pins:
    assert source_pins[path] == digest

jar_root = g.E / ".tools/gwt-2.7.0"
jars = [(jar_root / name, size, digest) for name, size, digest in JAR_INPUTS]


def _jars_pinned():
    for path, expected_size, expected_digest in jars:
        size, digest, _header = _file_digest(path, 64 * 1024 * 1024)
        assert size == expected_size and digest == expected_digest


_jars_pinned()
# Only the initially empty output and the two explicit GWT jars are on classpath.
classpath_entries = [classes] + [path for path, _size, _sha in jars]
classpath = os.pathsep.join(map(str, classpath_entries))
assert len(classpath_entries) == 3
assert os.path.normcase(str(g.D)) not in os.path.normcase(classpath)
argfile = work / "compiler-sources.txt"
assert all(not any(c in str(path) for c in ('"', '\r', '\n')) for path in compile_sources)
arg_raw = ("\n".join('"' + str(path).replace("\\", "/") + '"'
                     for path in compile_sources) + "\n").encode("utf-8")
with argfile.open("xb") as out:
    assert out.write(arg_raw) == len(arg_raw)
assert _read_stable(argfile, SOURCE_CAP) == arg_raw
argv = ["-source", "7", "-target", "7", "-encoding", "UTF-8",
        "-classpath", classpath, "-sourcepath", str(empty_sourcepath),
        "-d", str(classes), "@" + str(argfile)]


def _role(path):
    for base, label in ((A, "A"), (F, "F")):
        try:
            return label + "/" + path.relative_to(base).as_posix()
        except ValueError:
            pass
    raise AssertionError("SOURCE_ROLE")


tool_rows = []
for index, (path, expected) in enumerate(root.TOOLS):
    size, digest, _header = _file_digest(path, CLASS_BYTES_CAP)
    assert digest == expected
    tool_rows.append({"role": ("SELECTED_PYTHON", "SELECTED_POWERSHELL", "SELECTED_JDK8_JAVAC",
                              "SELECTED_JDK8_JAVA", "ROOT_PROCESS_WRAPPER", "VERIFIER_ISOLATION")[index],
                      "bytes": size, "sha256": digest})
dependencies = [{"role": name, "bytes": size, "sha256": digest}
                for name, size, digest in JAR_INPUTS]
build_input = {
    "scope": "FRESH_NATIVE_ROOT_BODY_SYNTHETIC_ONLY",
    "sourceFiles": [_source_info(path, _role(path)) for path in compile_sources],
    "sourceInputFiles": [_source_info(path, _role(path)) for path, _sha in source_input_pins],
    "nativeSourceInputFiles": [_source_info(path, _role(path))
                               for path, _sha in native_source_input_pins],
    "sourceFreezeSha256": hashlib.sha256(freeze_raw).hexdigest(),
    "sourceInputSha256": hashlib.sha256(source_input_raw).hexdigest(),
    "nativeSourceInputSha256": hashlib.sha256(native_source_input_raw).hexdigest(),
    "dependencies": dependencies, "tools": tool_rows,
    "driver": _source_info(driver_path, "ROOT_FRESH_ROOT_BODY_BUILD_DRIVER"),
    "sourceList": _source_info(argfile, "EXPLICIT_SOURCE_LIST"),
    "argvRoles": ["-source", "7", "-target", "7", "-encoding", "UTF-8",
                  "-classpath", "FRESH_CLASSES;GWT_USER;GWT_DEV", "-sourcepath", "EMPTY_SOURCEPATH",
                  "-d", "FRESH_CLASSES", "@EXPLICIT_SOURCE_LIST"],
    "sourceCount": SOURCE_COUNT, "productionSourceCount": 848,
    "replacementOwnerStudySourceCount": 3, "publicCanaryDenyFixtureSourceCount": 5,
    "componentSourceCount": 5, "sourceInputCount": 9, "nativeSourceInputCount": 7,
    "baseCommit": BASE_COMMIT,
    "sourceCapBytes": SOURCE_CAP, "classCountCap": CLASS_COUNT_CAP,
    "classBytesCap": CLASS_BYTES_CAP, "targetMajor": MAX_MAJOR, "compileLimitMs": 60000,
    "actualArgvLocation": "PRIVATE_COMPILE_REQUEST_AND_EXPLICIT_SOURCE_LIST",
    "actualPriorDataReads": 0, "oldDataClassReads": 0, "protectedHeldoutReads": 0,
    "nativeProducerCalls": 0, "nativeRuntime": "NOT_RUN", "normalHostAcceptance": "NONE",
    "genuineEnvelope": "UNSPENT_GATED",
}
build_input_path = A / "NATIVE_BUILD_INPUT_R1.json"
g.write(build_input_path, build_input)
build_input_raw = _read_stable(build_input_path, SOURCE_CAP)
build_input_sha = hashlib.sha256(build_input_raw).hexdigest()


def _recheck_final_inputs():
    _environment_pinned()
    _tools_pinned()
    assert _read_stable(A / "FROZEN_SOURCE_R1.json", SOURCE_CAP) == freeze_raw
    assert _read_stable(A / "SOURCE_INPUT_R1.json", SOURCE_CAP) == source_input_raw
    assert _read_stable(A / "SOURCE_INPUT_R2.json", SOURCE_CAP) == native_source_input_raw
    assert _production_sources() == production_sources
    for _role_name, (path, digest) in freeze_pins.items():
        _pinned_source(path, digest)
    for path, digest in source_input_pins + native_source_input_pins + tuple(source_pins.items()) + staged_pins:
        _pinned_source(path, digest)
    for name, digest in PUBLIC_INPUTS:
        _pinned_source(public_root / name, digest)
    assert _read_stable(argfile, SOURCE_CAP) == arg_raw
    assert _read_stable(build_input_path, SOURCE_CAP) == build_input_raw
    _jars_pinned()
    _assert_safe_path(empty_sourcepath)
    assert not any(empty_sourcepath.iterdir())


_recheck_final_inputs()
assert not any(classes.iterdir())
call = root.call("native-root-body-fresh-jdk8-compile-r1", g.JDK / "javac.exe", argv,
                 limit=60000)
_recheck_final_inputs()


def _class_inventory():
    result = []
    total = 0
    pending = [classes]
    while pending:
        directory = pending.pop()
        _assert_safe_path(directory)
        for path in directory.iterdir():
            _assert_safe_path(path)
            item = os.lstat(str(path))
            if stat.S_ISDIR(item.st_mode):
                pending.append(path)
                continue
            assert stat.S_ISREG(item.st_mode) and path.suffix == ".class"
            assert len(result) < CLASS_COUNT_CAP
            size, digest, header = _file_digest(path, CLASS_BYTES_CAP)
            total += size
            assert total <= CLASS_BYTES_CAP
            assert len(header) == 8 and header[:4] == b"\xca\xfe\xba\xbe"
            major = int.from_bytes(header[6:8], "big")
            assert major == MAX_MAJOR
            result.append({"path": path.relative_to(classes).as_posix(), "bytes": size,
                           "sha256": digest, "major": major})
    result.sort(key=lambda row: row["path"])
    assert result and len({row["path"] for row in result}) == len(result)
    return result, total


class_inventory, class_bytes = _class_inventory()
class_names = {row["path"] for row in class_inventory}
prefix = "com/lushprojects/circuitjs1/client/"
required_classes = {prefix + name + ".class" for name in (
    "FailureCohortRoutingStudy$Meter", "CohortTerminalTelemetryStudy",
    "CohortTerminalTelemetryStudy$SetupMeter", "CohortTerminalTelemetryStudy$SetupResult",
    "CurrentV2CanonicalCodec", "ReceiptContentBinding", "CurrentBodyOwnership",
    "RootBodyConstructorCanary", "CurrentV2CodecOracle",
)}
assert required_classes.issubset(class_names)
for stem in ("ActualGeneratorBranchOrderStudy$Study", "ActualGeneratorBranchOrderStudy$Mode",
             "ActualGeneratorBranchOrderStudy$ArmRun", "CohortTerminalTelemetryStudy$OriginalArm",
             "CurrentV2InputSetup", "CurrentAssemblyMetadata", "CohortTerminalTelemetryInputV2"):
    assert not any(name == prefix + stem + ".class" or name.startswith(prefix + stem + "$")
                   for name in class_names)
# The immutable detached carrier is allowed; no genuine OriginalArm is fabricated.
carrier = next(row for row in class_inventory
               if row["path"] == prefix + "CohortTerminalTelemetryStudy$ArmResult.class")
assert carrier["bytes"] == 811 and carrier["major"] == MAX_MAJOR
assert carrier["sha256"] == "7d9cbb2c16132df342829c8174eb8565e41d33efb45ae120e0f011f4f1fc6ca0"
assert _class_inventory() == (class_inventory, class_bytes)
_recheck_final_inputs()

inventory_path = A / "NATIVE_CLASS_INVENTORY_R1.json"
g.write(inventory_path, {
    "classes": class_inventory, "classCount": len(class_inventory), "classBytes": class_bytes,
    "allMajor51": True, "buildInputSha256": build_input_sha,
    "oldDataClassReads": 0, "nativeProducerImplementationAbsent": True,
    "currentBodyOwnershipClassPresent": True, "rootBodyConstructorCanaryClassPresent": True,
    "codecOracleClassPresent": True,
    "normalHostAcceptance": "NONE",
})
inventory_raw = _read_stable(inventory_path, SOURCE_CAP)
manifest = {
    "status": "PASS_FRESH_NATIVE_ROOT_BODY_COMPILE_ONLY",
    "scope": build_input["scope"], "sourceCount": SOURCE_COUNT,
    "sourceFiles": build_input["sourceFiles"], "dependencies": dependencies, "tools": tool_rows,
    "buildInputSha256": build_input_sha, "sourceFreezeSha256": build_input["sourceFreezeSha256"],
    "sourceInputSha256": SOURCE_INPUT_SHA256,
    "nativeSourceInputSha256": NATIVE_SOURCE_INPUT_SHA256,
    "classInventorySha256": hashlib.sha256(inventory_raw).hexdigest(),
    "classCount": len(class_inventory), "classBytes": class_bytes, "allMajor51": True,
    "nativeProducerImplementationAbsent": True, "currentBodyOwnershipClassPresent": True,
    "rootBodyConstructorCanaryClassPresent": True, "codecOracleClassPresent": True,
    "compile": call, "runtime": "NOT_RUN", "actualPriorDataReads": 0,
    "oldDataClassReads": 0, "protectedHeldoutReads": 0, "nativeProducerCalls": 0,
    "normalHostAcceptance": "NONE", "genuineEnvelope": "UNSPENT_GATED",
    "isolatedCleanupDurationMs": None,
}
g.write(A / "BUILD_MANIFEST_R1.json", manifest)
g.write(A / "NATIVE_COMPILE_GATE_R1.json", {
    "status": manifest["status"], "call": call, "sourceCount": SOURCE_COUNT,
    "classCount": len(class_inventory), "classBytes": class_bytes, "allMajor51": True,
    "inputSha256": build_input_sha,
    "manifestSha256": hashlib.sha256(_read_stable(A / "BUILD_MANIFEST_R1.json", SOURCE_CAP)).hexdigest(),
    "inventorySha256": hashlib.sha256(inventory_raw).hexdigest(),
    "runtime": "NOT_RUN", "normalHostAcceptance": "NONE", "genuineEnvelope": "UNSPENT_GATED",
    "isolatedCleanupDurationMs": None,
})
print(json.dumps({"status": manifest["status"], "sourceCount": SOURCE_COUNT,
                  "classCount": len(class_inventory), "classBytes": class_bytes,
                  "normalHostAcceptance": "NONE"}, separators=(",", ":")))
