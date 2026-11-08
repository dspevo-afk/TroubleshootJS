import hashlib
import json
import os
import re
import stat
import sys
import time
from pathlib import Path


ADMISSION_CONTEXT_SCHEMA = "tsj-source-bound-generic-r7-host-context-v1"
ADMISSION_SUMMARY_SCHEMA = "tsj-source-bound-generic-r7-host-summary-v1"
ADMISSION_SUMMARY_STATUS = "PASS_SOURCE_BOUND_GENERIC_R7_REFERENCE_ONLY"
ADMISSION_MAX_READER_NS = 180_000_000_000
ADMISSION_PACKET_BYTES = 1_048_576
ADMISSION_MARKER_BYTES = 666
ADMISSION_COMPILE_STDERR = (
    "warning: [options] bootstrap class path not set in conjunction with -source 1.7\r\n"
    "1 warning\r\n"
)
ADMISSION_CONTEXT_KEYS = (
    "schema", "runId", "sourceManifestSha256", "wireContractSha256",
    "fixtureOracleSha256", "producerManifest", "sourcePaths", "sourceList",
    "emptySourcepath", "classesRoot", "classInventory", "classInventorySha256",
    "inheritedClassesRoot", "inheritedClassInventory", "inheritedClassInventorySha256",
    "legacyInventory", "inheritedSourcePaths", "sourceProof", "sourceGenerator",
    "gwtUser", "gwtDev", "java", "javac", "powershell", "rootCall",
    "processModule", "compileCall", "nativeCall", "nativeOutput", "fixtureSpec",
    "markerBound", "expectedNativeCall", "returnedCapture",
)
ADMISSION_WIRE_SHA256 = "4142410d8719f2e3b73edc9481ba9a2a40f0476f19f242844e97b42bde366741"
ADMISSION_FIXTURE_SHA256 = "5d2b62c98bd229a73be0e54e4b6adcde4d5080fb87046d8bd770455f80c1224a"
ADMISSION_MARKER_BOUND_SHA256 = "8f92dd8e71dfc20974b08b51e407634a5e57d0c3e2882ecae1042cd73e683f9e"
ADMISSION_LEGACY_INVENTORY_SHA256 = "7fe14c6f95ae1c99ddfb3f81d975ff7f9ee2a6e1851b09e32d9ca8f0d47889f9"
ADMISSION_PARENT_COMMIT = "2ea50ea301b7348722bff958aa0e52909e9601d7"
ADMISSION_OWNER_SHA256 = "7bf4ab57198ff9444381ec5d677c1a2bc309bfa3e7f01b188c631ffe5f2b966c"
ADMISSION_RUN_OWNER_SHA256 = "4ed1c8561adf1619a8b7ffbd21e23fdf2544dcaafe541af4e5d2b2b1e95cabe4"
ADMISSION_FIXTURE_SOURCE_SHA256 = "dd333ff9c448ce012c3c1a4e603be39b70e1ac1d8d7ef79509d12ab21bd17619"
ADMISSION_TOOL_SHA256 = {
    "java": "e71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce",
    "javac": "61fa6463ee5b59004719b9833a16b148740cfa9f2d1f5a95ca598cc90be04c64",
    "powershell": "60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f",
    "rootCall": "28aab617e686d2a4eec074a46b2e2861f6fa7c008544b33f7b070f0c6d77ea1d",
    "processModule": "fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c",
    "gwtUser": "d3721bcdd7c6855a524801212dd74548967d8fb65cbc212a20890732ff2c6948",
    "gwtDev": "c7321e367fc24e5c7ad97b74d1cf2980c45fbd7cbe1fd412f8a8ba128f685135",
}
ADMISSION_TOOL_LIMITS = {
    "java": 64 * 1024 * 1024,
    "javac": 64 * 1024 * 1024,
    "powershell": 32 * 1024 * 1024,
    "rootCall": 256 * 1024,
    "processModule": 1024 * 1024,
    "gwtUser": 32_024_115,
    "gwtDev": 38_276_750,
}
ADMISSION_TOOL_ORDER = ("java", "javac", "powershell", "rootCall", "processModule")
ADMISSION_SOURCE_ROWS = (
    ("PcbLayerRoutingPrototype.java", "owner", 4 * 1024 * 1024),
    ("CohortTerminalTelemetryStudy.java", "study", 4 * 1024 * 1024),
    ("GenericRoutingReferenceCanary.java", "driver", 4 * 1024 * 1024),
)
ADMISSION_INHERITED_SOURCES = (
    ("SHARED_RUN_OWNER", "runOwner", ADMISSION_RUN_OWNER_SHA256),
    ("GENERIC_FIXTURE", "fixtureSource", ADMISSION_FIXTURE_SOURCE_SHA256),
)
ADMISSION_INHERITED_CLASS_COUNT = 4_288
ADMISSION_INHERITED_CLASS_BYTES = 19_644_833
ADMISSION_RECEIPT_NAMES = (
    "owner.json", "parent.json", "request.json", "process.json",
    "result.json", "stdout.txt", "stderr.txt",
)
ADMISSION_CALL_NAMES = ADMISSION_RECEIPT_NAMES + ("wrapper.stdout", "wrapper.stderr")
ADMISSION_ACCEPTED_NAMES = ("marker.txt", "result.json")
ADMISSION_RECEIPT_LIMITS = {
    "owner.json": 64 * 1024,
    "parent.json": 256 * 1024,
    "request.json": 64 * 1024,
    "process.json": 256 * 1024,
    "result.json": 64 * 1024,
    "stdout.txt": 8 * 1024 * 1024,
    "stderr.txt": 8 * 1024 * 1024,
    "wrapper.stdout": 64 * 1024,
    "wrapper.stderr": 64 * 1024,
}
ADMISSION_NATIVE_EXPECTED_SCHEMA = "tsj-generic-r7-trusted-before-call-v1"
ADMISSION_NATIVE_EXPECTED_STATUS = "TRUSTED_ROOT_FROZEN_BEFORE_EXECUTION_NO_SEMANTIC_ADMISSION"
ADMISSION_CAPTURE_SCHEMA = "tsj-generic-r7-trusted-returned-capture-v1"
ADMISSION_CAPTURE_STATUS = "RAW_RETURNED_PINS_BEFORE_SEMANTIC_ADMISSION"


def admission_need(condition, code):
    if not condition:
        raise ReaderError("ADMISSION_" + code)


def admission_lexical_path(value, code):
    admission_need(type(value) is str and bool(value) and "\x00" not in value, code)
    try:
        path = Path(value)
        raw_parts = re.split(r"[\\/]", value)
        admission_need(path.is_absolute() and str(path) == value
                       and not any(part in (".", "..") for part in raw_parts), code)
    except (OSError, RuntimeError, TypeError, ValueError):
        raise ReaderError("ADMISSION_" + code) from None
    return path


def admission_canonical_path(value, code, kind=None):
    admission_lexical_path(value, code)
    try:
        resolved = RH__canonical(value, "ADMISSION_" + code)
    except RH_ReaderError:
        raise
    admission_need(str(resolved) == value, code)
    if kind == "file":
        RH__lstat_regular(resolved, "ADMISSION_" + code)
    elif kind == "directory":
        RH__lstat_directory(resolved, "ADMISSION_" + code)
    return resolved


def admission_identity(path, kind, code):
    try:
        info = Path(path).lstat()
    except OSError:
        raise ReaderError("ADMISSION_" + code) from None
    attributes = getattr(info, "st_file_attributes", 0)
    admission_need(not stat.S_ISLNK(info.st_mode) and not (attributes & 0x400), code)
    if kind == "file":
        admission_need(stat.S_ISREG(info.st_mode), code)
        return (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns,
                info.st_ctime_ns, attributes)
    admission_need(stat.S_ISDIR(info.st_mode), code)
    return (info.st_dev, info.st_ino, info.st_mode, attributes)


def admission_load_io(base_value, io_value):
    base_path = admission_canonical_path(base_value, "BASE_PATH", "file")
    io_path = admission_canonical_path(io_value, "IO_HELPER_PATH", "file")
    try:
        base = _bootstrap_base(str(base_path))
        io = base.load_io(str(io_path))
    except ReaderError:
        raise
    except Exception:
        raise ReaderError("ADMISSION_CHECKED_IO_BOOTSTRAP") from None
    admission_need(getattr(io, "_sha", None) is not None, "CHECKED_IO_API")
    return io, admission_identity(base_path, "file", "BASE_IDENTITY"), \
        admission_identity(io_path, "file", "IO_IDENTITY")


def admission_read_file(io, path, limit, code):
    before = admission_identity(path, "file", code)
    try:
        if Path(path).lstat().st_size == 0:
            raw = RH__read(Path(path), limit, "ADMISSION_" + code)
            admission_need(raw == b"", code + "_EMPTY_STREAM_CHANGED")
        else:
            raw = io._read_regular(str(path), limit, "ADMISSION_" + code)
    except (ReaderError, RH_ReaderError):
        raise
    except Exception:
        raise ReaderError("ADMISSION_" + code) from None
    admission_need(type(raw) is bytes and len(raw) <= limit, code)
    after = admission_identity(path, "file", code)
    admission_need(before == after, code + "_IDENTITY")
    return raw, after


def admission_file_record(path, raw, identity):
    return (str(path), len(raw), hashlib.sha256(raw).hexdigest(), identity)


def admission_check_file(io, raw_path, limit, role, expected_sha=None, expected_bytes=None):
    path = admission_canonical_path(raw_path, role + "_PATH", "file")
    raw, identity = admission_read_file(io, path, limit, role + "_READ")
    if expected_bytes is not None:
        admission_need(len(raw) == expected_bytes, role + "_BYTES")
    if expected_sha is not None:
        admission_need(hashlib.sha256(raw).hexdigest() == expected_sha, role + "_HASH")
    return raw, admission_file_record(path, raw, identity)


def admission_check_empty_directory(raw_path, role):
    path = admission_canonical_path(raw_path, role + "_PATH", "directory")
    identity = admission_identity(path, "directory", role + "_IDENTITY")
    try:
        names = list(path.iterdir())
    except OSError:
        raise ReaderError("ADMISSION_" + role + "_READ") from None
    admission_need(not names, role + "_NOT_EMPTY")
    return path, identity


def admission_context_paths(context):
    RH_exact_keys(context, ADMISSION_CONTEXT_KEYS, "ADMISSION_CONTEXT_KEYS")
    admission_need(context["schema"] == ADMISSION_CONTEXT_SCHEMA, "CONTEXT_SCHEMA")
    admission_need(type(context["runId"]) is str and UUID.fullmatch(context["runId"]) is not None,
                   "CONTEXT_RUN_ID")
    admission_need(RH_is_digest(context["sourceManifestSha256"])
                   and context["wireContractSha256"] == ADMISSION_WIRE_SHA256
                   and context["fixtureOracleSha256"] == ADMISSION_FIXTURE_SHA256,
                   "CONTEXT_SOURCE_PROFILE")
    admission_need(RH_is_digest(context["classInventorySha256"])
                   and RH_is_digest(context["inheritedClassInventorySha256"]),
                   "CONTEXT_INVENTORY_HASH")
    RH_exact_keys(context["sourcePaths"], ("owner", "study", "driver"),
                  "ADMISSION_SOURCE_PATH_KEYS")
    RH_exact_keys(context["inheritedSourcePaths"], ("runOwner", "fixtureSource"),
                  "ADMISSION_INHERITED_SOURCE_PATH_KEYS")
    path_fields = (
        "producerManifest", "sourceList", "emptySourcepath", "classesRoot", "classInventory",
        "inheritedClassesRoot", "inheritedClassInventory", "legacyInventory", "sourceProof",
        "sourceGenerator", "gwtUser", "gwtDev", "java", "javac", "powershell", "rootCall",
        "processModule", "compileCall", "nativeCall", "nativeOutput", "fixtureSpec",
        "markerBound", "expectedNativeCall", "returnedCapture",
    )
    paths = []
    for field in path_fields:
        paths.append((field, admission_lexical_path(context[field], "CONTEXT_" + field.upper())))
    for role in ("owner", "study", "driver"):
        paths.append(("sourcePaths." + role,
                      admission_lexical_path(context["sourcePaths"][role], "CONTEXT_SOURCE_PATH")))
    for role in ("runOwner", "fixtureSource"):
        paths.append(("inheritedSourcePaths." + role,
                      admission_lexical_path(context["inheritedSourcePaths"][role],
                                             "CONTEXT_INHERITED_SOURCE_PATH")))
    keys = [os.path.normcase(str(path)) for _role, path in paths]
    admission_need(len(keys) == len(set(keys)), "CONTEXT_PATH_ALIAS")
    admission_need(Path(context["compileCall"]) != Path(context["nativeCall"])
                   and Path(context["nativeOutput"]) != Path(context["nativeCall"]),
                   "CONTEXT_CALL_OUTPUT_ALIAS")
    admission_need(Path(context["classesRoot"]) != Path(context["inheritedClassesRoot"]),
                   "CONTEXT_CLASS_ROOT_ALIAS")
    return context


def admission_paths_overlap(left, right):
    left = Path(os.path.normcase(os.path.abspath(str(left))))
    right = Path(os.path.normcase(os.path.abspath(str(right))))
    try:
        right.relative_to(left)
        return True
    except ValueError:
        pass
    try:
        left.relative_to(right)
        return True
    except ValueError:
        return False


def admission_validate_output_location(context, raw_output, external_files=()):
    output = admission_canonical_path(raw_output, "OUTPUT_DIRECTORY", "directory")
    consumed_roots = (
        context["classesRoot"], context["inheritedClassesRoot"], context["emptySourcepath"],
        context["compileCall"], context["nativeCall"], context["nativeOutput"],
    )
    consumed_files = (
        context["producerManifest"], context["sourceList"], context["classInventory"],
        context["inheritedClassInventory"], context["legacyInventory"], context["sourceProof"],
        context["sourceGenerator"], context["fixtureSpec"], context["markerBound"],
        context["expectedNativeCall"], context["returnedCapture"], context["java"],
        context["javac"], context["powershell"], context["rootCall"], context["processModule"],
        context["gwtUser"], context["gwtDev"],
        *context["sourcePaths"].values(), *context["inheritedSourcePaths"].values(),
    )
    for candidate in consumed_roots + consumed_files + tuple(external_files):
        admission_need(not admission_paths_overlap(output, candidate), "OUTPUT_INPUT_OVERLAP")
    return output


def admission_parse_context(raw):
    admission_need(type(raw) is bytes and 0 < len(raw) <= 1024 * 1024, "CONTEXT_BYTES")
    return admission_context_paths(RH_host_document(raw, 1024 * 1024))


def admission_validate_manifest(raw, context):
    manifest = RH_host_document(raw, RH_MAX_JSON)
    RH_exact_keys(manifest, (
        "schema", "scope", "parentCommit", "wireContractSha256", "fixtureOracleSha256",
        "sources", "inheritedSources", "sourceProofSha256", "generatorSha256", "classes",
        "classCount", "classBytes", "ownerClassCount", "ownerByteEqualQualifiedP",
        "namespace", "inheritedClosure", "compile", "classpathOrder", "newAuthoredInvocations",
    ), "ADMISSION_MANIFEST_KEYS")
    admission_need(manifest["schema"] == "tsj-generic-r7-native-producer-manifest-v1"
                   and manifest["scope"] == "GENERIC_NATURAL_R7_REFERENCE_ONLY"
                   and manifest["parentCommit"] == ADMISSION_PARENT_COMMIT
                   and manifest["wireContractSha256"] == ADMISSION_WIRE_SHA256
                   and manifest["fixtureOracleSha256"] == ADMISSION_FIXTURE_SHA256
                   and manifest["classpathOrder"] == "FRESH_THEN_PINNED_D_GWT"
                   and type(manifest["newAuthoredInvocations"]) is int
                   and manifest["newAuthoredInvocations"] == 0
                   and manifest["ownerByteEqualQualifiedP"] is True,
                   "MANIFEST_IDENTITY")
    admission_need(RH_is_digest(manifest["sourceProofSha256"])
                   and RH_is_digest(manifest["generatorSha256"]), "MANIFEST_SOURCE_PROOF_HASHES")
    expected_source_roles = tuple(role for role, _key, _limit in ADMISSION_SOURCE_ROWS)
    admission_need(type(manifest["sources"]) is list
                   and len(manifest["sources"]) == len(expected_source_roles),
                   "MANIFEST_SOURCE_COUNT")
    source_rows = []
    for row, expected_role in zip(manifest["sources"], expected_source_roles):
        RH_exact_keys(row, ("role", "bytes", "sha256"), "ADMISSION_SOURCE_ROW_KEYS")
        admission_need(row["role"] == expected_role
                       and RH_is_int(row["bytes"], 1, 4 * 1024 * 1024)
                       and RH_is_digest(row["sha256"]), "MANIFEST_SOURCE_ROW")
        source_rows.append(row)
    admission_need(source_rows[0]["sha256"] == ADMISSION_OWNER_SHA256,
                   "MANIFEST_OWNER_PIN")
    admission_need(type(manifest["classes"]) is list
                   and RH_is_int(manifest["classCount"], 1, RH_MAX_CLASS_COUNT)
                   and manifest["classCount"] == len(manifest["classes"])
                   and RH_is_int(manifest["classBytes"], 1, RH_MAX_CLASS_BYTES),
                   "MANIFEST_CLASS_COUNTS")
    prior = None
    total = 0
    owner_count = 0
    for row in manifest["classes"]:
        RH_exact_keys(row, ("path", "bytes", "sha256"), "ADMISSION_CLASS_ROW_KEYS")
        admission_need(type(row["path"]) is str and row["path"].endswith(".class")
                       and (prior is None or prior < row["path"])
                       and RH_is_int(row["bytes"], 1, RH_MAX_CLASS_BYTES)
                       and RH_is_digest(row["sha256"]), "MANIFEST_CLASS_ROW")
        prior = row["path"]
        total += row["bytes"]
        if row["path"].rsplit("/", 1)[-1].startswith("PcbLayerRoutingPrototype"):
            owner_count += 1
    admission_need(total == manifest["classBytes"]
                   and type(manifest["ownerClassCount"]) is int
                   and manifest["ownerClassCount"] == owner_count == 46,
                   "MANIFEST_CLASS_TOTALS")
    namespace = manifest["namespace"]
    RH_exact_keys(namespace, (
        "status", "freshClassCount", "explicitReferencesChecked",
        "allFreshNamespaceReferencesResolveFresh", "classMajor", "limitation",
    ), "ADMISSION_NAMESPACE_KEYS")
    admission_need(namespace["status"] == "PASS_EXPLICIT_CONSTANT_CLASS_CLOSURE"
                   and namespace["freshClassCount"] == manifest["classCount"]
                   and RH_is_int(namespace["explicitReferencesChecked"], 0, 2**63 - 1)
                   and namespace["allFreshNamespaceReferencesResolveFresh"] is True
                   and namespace["classMajor"] == 51
                   and namespace["limitation"]
                   == "EXPLICIT_CONSTANT_CLASS_ONLY_NO_REFLECTION_CLOSURE_CLAIM",
                   "MANIFEST_NAMESPACE")
    inherited_sources = manifest["inheritedSources"]
    admission_need(type(inherited_sources) is list
                   and len(inherited_sources) == len(ADMISSION_INHERITED_SOURCES),
                   "MANIFEST_INHERITED_SOURCE_COUNT")
    for row, (expected_role, _key, expected_sha) in zip(inherited_sources, ADMISSION_INHERITED_SOURCES):
        RH_exact_keys(row, ("role", "bytes", "sha256"), "ADMISSION_INHERITED_SOURCE_KEYS")
        admission_need(row["role"] == expected_role
                       and RH_is_int(row["bytes"], 1, 4 * 1024 * 1024)
                       and row["sha256"] == expected_sha,
                       "MANIFEST_INHERITED_SOURCE")
    inherited = manifest["inheritedClosure"]
    RH_exact_keys(inherited, ("classBytes", "classFilesRehashed", "inventorySha256", "jars",
                              "toolsRehashed"), "ADMISSION_INHERITED_CLOSURE_KEYS")
    admission_need(inherited["classBytes"] == ADMISSION_INHERITED_CLASS_BYTES
                   and inherited["classFilesRehashed"] == ADMISSION_INHERITED_CLASS_COUNT
                   and inherited["inventorySha256"] == ADMISSION_LEGACY_INVENTORY_SHA256
                   and inherited["toolsRehashed"] is True
                   and type(inherited["jars"]) is list and len(inherited["jars"]) == 2,
                   "MANIFEST_INHERITED_CLOSURE")
    expected_jars = (
        ("GWT_DEPENDENCY", ADMISSION_TOOL_LIMITS["gwtUser"], ADMISSION_TOOL_SHA256["gwtUser"]),
        ("GWT_DEPENDENCY", ADMISSION_TOOL_LIMITS["gwtDev"], ADMISSION_TOOL_SHA256["gwtDev"]),
    )
    observed_jars = []
    for row in inherited["jars"]:
        RH_exact_keys(row, ("role", "bytes", "sha256"), "ADMISSION_JAR_ROW_KEYS")
        observed_jars.append((row["role"], row["bytes"], row["sha256"]))
    admission_need(tuple(observed_jars) == expected_jars, "MANIFEST_JAR_PINS")
    compile_meta = manifest["compile"]
    RH_exact_keys(compile_meta, (
        "phase", "status", "childExitCode", "processElapsedMs", "wrapperTotalElapsedMs",
        "terminationProven", "ownedRootAndLogsAbsent", "isolatedCleanupDurationMs", "receipts",
    ), "ADMISSION_COMPILE_KEYS")
    admission_need(type(compile_meta["phase"]) is str and compile_meta["phase"]
                   and compile_meta["status"] == "PASS"
                   and type(compile_meta["childExitCode"]) is int
                   and compile_meta["childExitCode"] == 0
                   and compile_meta["terminationProven"] is True
                   and compile_meta["ownedRootAndLogsAbsent"] is True
                   and compile_meta["isolatedCleanupDurationMs"] is None
                   and RH__is_finite_number(compile_meta["processElapsedMs"])
                   and 0 < compile_meta["processElapsedMs"] <= 60_000
                   and RH__is_finite_number(compile_meta["wrapperTotalElapsedMs"])
                   and compile_meta["wrapperTotalElapsedMs"] >= compile_meta["processElapsedMs"],
                   "MANIFEST_COMPILE_PROFILE")
    RH_exact_keys(compile_meta["receipts"], ADMISSION_RECEIPT_NAMES,
                  "ADMISSION_COMPILE_RECEIPT_KEYS")
    for digest in compile_meta["receipts"].values():
        admission_need(RH_is_digest(digest), "MANIFEST_COMPILE_RECEIPT_HASH")
    return manifest


def admission_validate_legacy_inventory(raw):
    inventory = RH_host_document(raw, 4 * 1024 * 1024)
    RH_exact_keys(inventory, ("schema", "entries", "classCount", "totalBytes", "sourceManifestSha256"),
                  "ADMISSION_LEGACY_INVENTORY_KEYS")
    admission_need(inventory["schema"] == "tsj-failure-cohort-classes-v1"
                   and type(inventory["entries"]) is list
                   and type(inventory["classCount"]) is int
                   and inventory["classCount"] == ADMISSION_INHERITED_CLASS_COUNT
                   and type(inventory["totalBytes"]) is int
                   and inventory["totalBytes"] == ADMISSION_INHERITED_CLASS_BYTES
                   and RH_is_digest(inventory["sourceManifestSha256"])
                   and len(inventory["entries"]) == inventory["classCount"],
                   "LEGACY_INVENTORY_PROFILE")
    previous = None
    for row in inventory["entries"]:
        RH_exact_keys(row, ("path", "bytes", "sha256"), "ADMISSION_LEGACY_ENTRY_KEYS")
        admission_need(type(row["path"]) is str and row["path"].endswith(".class")
                       and (previous is None or previous < row["path"])
                       and RH_is_int(row["bytes"], 1, RH_MAX_CLASS_BYTES)
                       and RH_is_digest(row["sha256"]), "LEGACY_INVENTORY_ENTRY")
        previous = row["path"]
    return inventory


def admission_inventory_bytes(files, count, total):
    value = {"schema": RH_CLASS_SCHEMA, "files": files, "count": count, "bytes": total}
    return (json.dumps(value, ensure_ascii=True, sort_keys=True, separators=(",", ":"),
                       allow_nan=False) + "\n").encode("ascii")


def admission_tree_snapshot(root):
    RH__lstat_directory(root, "ADMISSION_CLASS_ROOT")
    root_identity = admission_identity(root, "directory", "CLASS_ROOT_IDENTITY")
    rows = []
    entries = [("", "D", root_identity)]
    count = 0
    total = 0
    for current, directories, files in os.walk(root, topdown=True, followlinks=False):
        directories.sort()
        files.sort()
        base = Path(current)
        for name in directories:
            path = base / name
            try:
                info = path.lstat()
            except OSError:
                raise ReaderError("ADMISSION_CLASS_TREE_READ") from None
            attributes = getattr(info, "st_file_attributes", 0)
            admission_need(stat.S_ISDIR(info.st_mode) and not stat.S_ISLNK(info.st_mode)
                           and not (attributes & 0x400), "CLASS_TREE_REPARSE")
            entries.append((path.relative_to(root).as_posix(), "D",
                            admission_identity(path, "directory", "CLASS_TREE_READ")))
        for name in files:
            path = base / name
            try:
                info = path.lstat()
            except OSError:
                raise ReaderError("ADMISSION_CLASS_TREE_READ") from None
            attributes = getattr(info, "st_file_attributes", 0)
            admission_need(stat.S_ISREG(info.st_mode) and not stat.S_ISLNK(info.st_mode)
                           and not (attributes & 0x400) and path.suffix == ".class",
                           "CLASS_TREE_FOREIGN_FILE")
            count += 1
            total += info.st_size
            admission_need(count <= RH_MAX_CLASS_COUNT and total <= RH_MAX_CLASS_BYTES,
                           "CLASS_TREE_BOUND")
            rows.append((path.relative_to(root).as_posix(), info.st_size))
            entries.append((path.relative_to(root).as_posix(), "F",
                            admission_identity(path, "file", "CLASS_TREE_READ")))
    entries.sort(key=lambda row: (row[0], row[1]))
    rows.sort()
    return {"rows": tuple(rows), "entries": tuple(entries), "count": count, "bytes": total}


def admission_validate_tree(root_value, inventory_path_value, inventory_raw, inventory_sha,
                            expected_rows, expected_count, expected_bytes):
    root = admission_canonical_path(root_value, "CLASS_ROOT_PATH", "directory")
    inventory_path = admission_canonical_path(inventory_path_value, "CLASS_INVENTORY_PATH", "file")
    admission_need(hashlib.sha256(inventory_raw).hexdigest() == inventory_sha,
                   "CLASS_INVENTORY_CONTEXT_HASH")
    RH_strict_document(inventory_raw, 4 * 1024 * 1024)
    admission_need(inventory_raw == admission_inventory_bytes(expected_rows, expected_count, expected_bytes),
                   "CLASS_INVENTORY_EXPECTED_BYTES")
    before = admission_tree_snapshot(root)
    actual = RH__validate_class_tree(
        root, inventory_path, inventory_raw, {"classInventorySha256": inventory_sha})
    after = admission_tree_snapshot(root)
    admission_need(before == after, "CLASS_TREE_CHANGED_DURING_CHECK")
    admission_need(actual == expected_rows and after["count"] == expected_count
                   and after["bytes"] == expected_bytes,
                   "CLASS_TREE_EXPECTED_ROWS")
    return after


def admission_source_list_bytes(context):
    source_paths = [context["sourcePaths"][key] for _name, key, _limit in ADMISSION_SOURCE_ROWS]
    return "".join('"' + path.replace("\\", "/") + '"\n'
                   for path in source_paths).encode("utf-8")


def admission_expected_invocations(context):
    classpath = ";".join((context["classesRoot"], context["inheritedClassesRoot"],
                           context["gwtUser"], context["gwtDev"]))
    compile_args = [
        "-source", "7", "-target", "7", "-encoding", "UTF-8",
        "-classpath", classpath, "-sourcepath", context["emptySourcepath"],
        "-d", context["classesRoot"], "@" + context["sourceList"],
    ]
    native_args = [
        "-Xmx1024m", "-cp", classpath,
        "com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy",
        "GENERIC_ROUTING_R7_REFERENCE_V1", context["runId"],
        context["sourceManifestSha256"], context["wireContractSha256"],
        context["fixtureOracleSha256"], context["nativeOutput"],
    ]
    return {
        "compile": (context["javac"], compile_args, 60_000, ADMISSION_COMPILE_STDERR),
        "native": (context["java"], native_args, 480_000, ""),
    }


def admission_validate_capture_proof(capture_raw, capture_sha, context):
    admission_need(hashlib.sha256(capture_raw).hexdigest() == capture_sha,
                   "CAPTURE_EXTERNAL_HASH")
    capture = RH_host_document(capture_raw, RH_MAX_JSON)
    RH_exact_keys(capture, (
        "schema", "expectedBeforeCallSha256", "call", "nativeDirectory", "outputNames",
        "outputFiles", "status", "newAuthoredInvocations",
    ), "CAPTURE_KEYS")
    admission_need(capture["schema"] == ADMISSION_CAPTURE_SCHEMA
                   and capture["status"] == ADMISSION_CAPTURE_STATUS
                   and type(capture["newAuthoredInvocations"]) is int
                   and capture["newAuthoredInvocations"] == 0
                   and capture["nativeDirectory"] == context["nativeOutput"]
                   and capture["outputNames"] == ["result.json"]
                   and RH_is_digest(capture["expectedBeforeCallSha256"]),
                   "CAPTURE_IDENTITY")
    admission_need(type(capture["outputFiles"]) is list and len(capture["outputFiles"]) == 1,
                   "CAPTURE_OUTPUT_FILES")
    row = capture["outputFiles"][0]
    RH_exact_keys(row, ("role", "bytes", "sha256"), "CAPTURE_OUTPUT_ROW_KEYS")
    admission_need(row["role"] == "result.json"
                   and RH_is_int(row["bytes"], 1, ADMISSION_PACKET_BYTES)
                   and RH_is_digest(row["sha256"]), "CAPTURE_OUTPUT_ROW")
    call = capture["call"]
    RH_exact_keys(call, (
        "phase", "status", "childExitCode", "processElapsedMs", "wrapperTotalElapsedMs",
        "terminationProven", "ownedRootAndLogsAbsent", "isolatedCleanupDurationMs", "receipts",
    ), "CAPTURE_CALL_KEYS")
    admission_need(type(call["phase"]) is str and call["phase"]
                   and call["status"] == "PASS"
                   and type(call["childExitCode"]) is int and call["childExitCode"] == 0
                   and RH__is_finite_number(call["processElapsedMs"])
                   and 0 < call["processElapsedMs"] <= 480_000
                   and RH__is_finite_number(call["wrapperTotalElapsedMs"])
                   and call["wrapperTotalElapsedMs"] >= call["processElapsedMs"]
                   and call["terminationProven"] is True
                   and call["ownedRootAndLogsAbsent"] is True
                   and call["isolatedCleanupDurationMs"] is None,
                   "CAPTURE_CALL_PROFILE")
    RH_exact_keys(call["receipts"], ADMISSION_RECEIPT_NAMES, "CAPTURE_RECEIPT_KEYS")
    for digest in call["receipts"].values():
        admission_need(RH_is_digest(digest), "CAPTURE_RECEIPT_HASH")
    return capture


def admission_validate_expected_call(expected_raw, expected_sha, context, manifest, tool_rows):
    admission_need(hashlib.sha256(expected_raw).hexdigest() == expected_sha,
                   "EXPECTED_CALL_HASH")
    expected = RH_host_document(expected_raw, RH_MAX_JSON)
    RH_exact_keys(expected, (
        "schema", "context", "executable", "arguments", "sourceManifestSha256",
        "tools", "sources", "classes", "status",
    ), "EXPECTED_CALL_KEYS")
    expected_context = expected["context"]
    RH_exact_keys(expected_context, (
        "runId", "sourceManifestSha256", "wireContractSha256", "fixtureOracleSha256", "nativeOutput",
    ), "EXPECTED_CALL_CONTEXT_KEYS")
    executable, arguments, _limit, _stderr = admission_expected_invocations(context)["native"]
    admission_need(expected["schema"] == ADMISSION_NATIVE_EXPECTED_SCHEMA
                   and expected["status"] == ADMISSION_NATIVE_EXPECTED_STATUS
                   and expected_context == {
                       "runId": context["runId"],
                       "sourceManifestSha256": context["sourceManifestSha256"],
                       "wireContractSha256": context["wireContractSha256"],
                       "fixtureOracleSha256": context["fixtureOracleSha256"],
                       "nativeOutput": context["nativeOutput"],
                   }
                   and expected["sourceManifestSha256"] == context["sourceManifestSha256"]
                   and expected["executable"] == executable
                   and expected["arguments"] == arguments
                   and expected["tools"] == list(tool_rows)
                   and expected["sources"] == manifest["sources"]
                   and expected["classes"] == manifest["classes"],
                   "EXPECTED_CALL_BINDING")
    return expected


def admission_snapshot_call(io, context, call_role, expected_file, expected_args, limit_ms,
                            expected_stderr, expected_receipts, expected_phase):
    call_path = admission_canonical_path(context[call_role], call_role.upper() + "_PATH", "directory")
    admission_need(call_path.name == expected_phase, call_role.upper() + "_PHASE_NAME")
    directory_identity = admission_identity(call_path, "directory", call_role + "_IDENTITY")
    try:
        names = sorted(entry.name for entry in os.scandir(call_path))
    except OSError:
        raise ReaderError("ADMISSION_" + call_role.upper() + "_DIRECTORY_READ") from None
    admission_need(names == sorted(ADMISSION_CALL_NAMES), call_role.upper() + "_EXACT_NINE_FILES")
    raw_by_name = {}
    file_records = {}
    for name in ADMISSION_CALL_NAMES:
        raw, record = admission_check_file(
            io, str(call_path / name), ADMISSION_RECEIPT_LIMITS[name],
            call_role.upper() + "_" + name.replace(".", "_").upper())
        raw_by_name[name] = raw
        file_records[name] = record
    admission_need(raw_by_name["wrapper.stderr"] == b"", call_role.upper() + "_WRAPPER_STDERR")
    result_record = RH_host_document(raw_by_name["result.json"], 64 * 1024)
    wrapper_record = RH_host_document(raw_by_name["wrapper.stdout"], 64 * 1024)
    admission_need(wrapper_record == result_record, call_role.upper() + "_WRAPPER_RESULT")
    admission_need(type(result_record.get("exitCode")) is int and result_record["exitCode"] == 0,
                   call_role.upper() + "_RESULT_EXIT_CODE_TYPE")
    for name in ADMISSION_RECEIPT_NAMES:
        admission_need(hashlib.sha256(raw_by_name[name]).hexdigest() == expected_receipts[name],
                       call_role.upper() + "_RECEIPT_PIN_" + name.replace(".", "_").upper())
    owner = RH_host_document(raw_by_name["owner.json"], 64 * 1024)
    parent = RH_host_document(raw_by_name["parent.json"], 256 * 1024)
    request = RH_host_document(raw_by_name["request.json"], 64 * 1024)
    process = RH_host_document(raw_by_name["process.json"], 256 * 1024)
    admission_need(type(owner) is dict
                   and RH__same_host_path(owner.get("executable"), context["powershell"],
                                          "ADMISSION_OWNER_EXECUTABLE")
                   and RH__same_host_path(owner.get("script"), context["rootCall"],
                                          "ADMISSION_OWNER_SCRIPT")
                   and RH__same_host_path(owner.get("request"), call_path / "request.json",
                                          "ADMISSION_OWNER_REQUEST"),
                   call_role.upper() + "_OWNER_BINDING")
    RH_exact_keys(request, ("FilePath", "Arguments", "LimitMs"), "ADMISSION_REQUEST_KEYS")
    admission_need(request["FilePath"] == expected_file
                   and request["Arguments"] == expected_args
                   and type(request["LimitMs"]) is int and request["LimitMs"] == limit_ms,
                   call_role.upper() + "_INDEPENDENT_REQUEST")
    if call_role == "compileCall":
        admission_need(raw_by_name["stdout.txt"] == b"", "COMPILE_STDOUT_EMPTY")
    if call_role == "nativeCall":
        admission_need(raw_by_name["stderr.txt"] == b"", "NATIVE_STDERR_EMPTY")
    kernel_result = RH__validate_owner_call(
        call_path, call_path / "parent.json", parent, request, process,
        expected_file, expected_args, limit_ms, Path(context["processModule"]),
        expected_stderr=expected_stderr)
    admission_need(kernel_result == result_record, call_role.upper() + "_KERNEL_RESULT")
    admission_need(parent.get("process") == process
                   and process.get("FilePath") == expected_file
                   and process.get("Arguments") == expected_args,
                   call_role.upper() + "_PROCESS_BINDING")
    for field in ("StdoutLog", "StderrLog", "ProcessRoot"):
        admission_resource_absent(process.get(field), call_role.upper() + "_" + field.upper())
    admission_need(directory_identity == admission_identity(
        call_path, "directory", call_role + "_IDENTITY"), call_role.upper() + "_DIRECTORY_CHANGED")
    snapshots = {
        "directory": (str(call_path), directory_identity),
        "files": tuple((name, file_records[name]) for name in ADMISSION_CALL_NAMES),
    }
    return {"snapshot": snapshots, "parent": parent, "result": result_record,
            "process": process, "stdout": raw_by_name["stdout.txt"]}


def admission_resource_absent(value, role):
    admission_lexical_path(value, role + "_PATH")
    try:
        remains = os.path.lexists(value)
    except (OSError, ValueError):
        raise ReaderError("ADMISSION_" + role + "_PATH") from None
    admission_need(not remains, role + "_REMAINS")


def admission_output_directory(io, output_dir, expected_names):
    try:
        checked = Path(io._checked_directory(str(output_dir), "OUTPUT_DIRECTORY"))
        names = io._directory_names(checked, "OUTPUT_DIRECTORY")
    except (ReaderError, RH_ReaderError):
        raise
    except Exception:
        raise ReaderError("ADMISSION_OUTPUT_DIRECTORY_CHECK") from None
    checked = admission_canonical_path(str(checked), "OUTPUT_DIRECTORY", "directory")
    admission_need(sorted(names) == expected_names, "OUTPUT_DIRECTORY_CLOSURE")
    for name in names:
        admission_canonical_path(str(checked / name), "OUTPUT_FILE", "file")
    return checked, admission_identity(checked, "directory", "OUTPUT_DIRECTORY_IDENTITY")


def admission_output_write(io, output_dir, result_raw, marker_raw):
    try:
        checked = Path(io._checked_directory(str(output_dir), "OUTPUT_DIRECTORY"))
        admission_need(not io._directory_names(checked, "OUTPUT_DIRECTORY"), "OUTPUT_NOT_EMPTY")
        io._write_exclusive(checked / "result.json", result_raw,
                            ADMISSION_PACKET_BYTES, "OUTPUT_RESULT")
        checked = Path(io._checked_directory(str(output_dir), "OUTPUT_DIRECTORY"))
        admission_need(sorted(io._directory_names(checked, "OUTPUT_DIRECTORY")) == ["result.json"],
                       "OUTPUT_RESULT_CLOSURE")
        io._write_exclusive(checked / "marker.txt", marker_raw,
                            ADMISSION_MARKER_BYTES, "OUTPUT_MARKER")
    except (ReaderError, RH_ReaderError):
        raise
    except Exception:
        raise ReaderError("ADMISSION_EXCLUSIVE_ACCEPTED_WRITE") from None
    return Path(output_dir) / "result.json", Path(output_dir) / "marker.txt"


def admission_tool_record(io, context, role, file_snapshots):
    raw, record = admission_check_file(
        io, context[role], ADMISSION_TOOL_LIMITS[role], "TOOL_" + role.upper(),
        ADMISSION_TOOL_SHA256[role],
        ADMISSION_TOOL_LIMITS[role] if role in ("gwtUser", "gwtDev") else None)
    file_snapshots["tool." + role] = record
    return {"role": Path(context[role]).name, "bytes": len(raw),
            "sha256": hashlib.sha256(raw).hexdigest()}


def admission_validate_capture_and_expected(io, context, capture_sha, manifest,
                                            file_snapshots):
    admission_canonical_path(context["nativeOutput"], "NATIVE_OUTPUT", "directory")
    expected_raw, expected_record = admission_check_file(
        io, context["expectedNativeCall"], RH_MAX_JSON, "EXPECTED_NATIVE_CALL")
    capture_raw, capture_record = admission_check_file(
        io, context["returnedCapture"], RH_MAX_JSON, "RETURNED_CAPTURE", capture_sha)
    capture = admission_validate_capture_proof(capture_raw, capture_sha, context)
    expected_sha = hashlib.sha256(expected_raw).hexdigest()
    admission_need(capture["expectedBeforeCallSha256"] == expected_sha,
                   "CAPTURE_PRECALL_LINK")
    tool_rows = file_snapshots["expectedCallTools"]
    expected = admission_validate_expected_call(expected_raw, expected_sha, context, manifest, tool_rows)
    file_snapshots["expectedNativeCall"] = expected_record
    file_snapshots["returnedCapture"] = capture_record
    native_call = capture["call"]
    admission_need(Path(context["nativeCall"]).name == native_call["phase"],
                   "CAPTURE_NATIVE_PHASE")
    return capture, expected


def admission_native_output(io, context, capture, file_snapshots, directory_snapshots):
    output_dir = admission_canonical_path(context["nativeOutput"], "NATIVE_OUTPUT", "directory")
    directory_identity = admission_identity(output_dir, "directory", "NATIVE_OUTPUT_IDENTITY")
    try:
        names = sorted(entry.name for entry in os.scandir(output_dir))
    except OSError:
        raise ReaderError("ADMISSION_NATIVE_OUTPUT_DIRECTORY") from None
    admission_need(names == ["result.json"], "NATIVE_OUTPUT_EXACT_RESULT")
    result_path = admission_canonical_path(str(output_dir / "result.json"), "NATIVE_RESULT_PATH", "file")
    captured_row = capture["outputFiles"][0]
    raw, result_record = admission_check_file(
        io, str(result_path), ADMISSION_PACKET_BYTES, "NATIVE_RESULT",
        captured_row["sha256"], captured_row["bytes"])
    admission_need(directory_identity == admission_identity(
        output_dir, "directory", "NATIVE_OUTPUT_IDENTITY"), "NATIVE_OUTPUT_DIRECTORY_CHANGED")
    file_snapshots["nativeResult"] = result_record
    directory_snapshots["nativeOutput"] = (str(output_dir), directory_identity)
    return raw


def admission_validate_marker_bound(raw):
    bound = RH_host_document(raw, 16 * 1024)
    RH_exact_keys(bound, ("includesLf", "maximumMarkerBytes", "maximumValues",
                          "selfReferentialCopyFields", "status"), "MARKER_BOUND_KEYS")
    admission_need(bound["includesLf"] is True and bound["maximumMarkerBytes"] == ADMISSION_MARKER_BYTES
                   and bound["selfReferentialCopyFields"] is False
                   and bound["status"] == "FROZEN_SOURCE_BOUND_DIGIT_WIDTH_UPPER_BOUND_NOT_RUNTIME_OBSERVATION",
                   "MARKER_BOUND_PROFILE")
    return bound


def admission_validate_material(io, context, context_raw, context_path, capture_sha,
                                file_snapshots, directory_snapshots):
    current_context_raw, context_record = admission_check_file(
        io, str(context_path), 1024 * 1024, "CONTEXT")
    admission_need(current_context_raw == context_raw, "CONTEXT_REPLACED")
    if "context" in file_snapshots:
        admission_need(file_snapshots["context"] == context_record, "CONTEXT_IDENTITY_CHANGED")
    context = admission_parse_context(current_context_raw)
    file_snapshots["context"] = context_record

    manifest_raw, manifest_record = admission_check_file(
        io, context["producerManifest"], RH_MAX_JSON, "PRODUCER_MANIFEST",
        context["sourceManifestSha256"])
    manifest = admission_validate_manifest(manifest_raw, context)
    file_snapshots["producerManifest"] = manifest_record

    expected_tool_rows = []
    for role in ADMISSION_TOOL_ORDER:
        expected_tool_rows.append(admission_tool_record(io, context, role, file_snapshots))
    # The two GWT jars are inherited dependencies: exact size and digest are pinned here.
    for role in ("gwtUser", "gwtDev"):
        admission_tool_record(io, context, role, file_snapshots)
    file_snapshots["expectedCallTools"] = tuple(expected_tool_rows)

    source_rows = []
    for role_name, key, limit in ADMISSION_SOURCE_ROWS:
        path = context["sourcePaths"][key]
        raw, record = admission_check_file(
            io, path, limit, "SOURCE_" + key.upper(), expected_bytes=None)
        source_rows.append({"role": role_name, "bytes": len(raw),
                            "sha256": hashlib.sha256(raw).hexdigest()})
        file_snapshots["source." + key] = record
    admission_need(source_rows == manifest["sources"], "SOURCE_MANIFEST_BINDING")

    for role_name, key, expected_sha in ADMISSION_INHERITED_SOURCES:
        raw, record = admission_check_file(
            io, context["inheritedSourcePaths"][key], 4 * 1024 * 1024,
            "INHERITED_SOURCE_" + key.upper(), expected_sha)
        manifest_row = next(row for row in manifest["inheritedSources"] if row["role"] == role_name)
        admission_need(len(raw) == manifest_row["bytes"], "INHERITED_SOURCE_BYTES")
        file_snapshots["inheritedSource." + key] = record

    source_proof_raw, source_proof_record = admission_check_file(
        io, context["sourceProof"], 4 * 1024 * 1024, "SOURCE_PROOF",
        manifest["sourceProofSha256"])
    source_generator_raw, source_generator_record = admission_check_file(
        io, context["sourceGenerator"], 4 * 1024 * 1024, "SOURCE_GENERATOR",
        manifest["generatorSha256"])
    file_snapshots["sourceProof"] = source_proof_record
    file_snapshots["sourceGenerator"] = source_generator_record

    fixture_raw, fixture_record = admission_check_file(
        io, context["fixtureSpec"], 1024 * 1024, "FIXTURE_SPEC", ADMISSION_FIXTURE_SHA256)
    load_fixture_spec(fixture_raw)
    file_snapshots["fixtureSpec"] = fixture_record
    marker_bound_raw, marker_bound_record = admission_check_file(
        io, context["markerBound"], 16 * 1024, "MARKER_BOUND", ADMISSION_MARKER_BOUND_SHA256)
    admission_validate_marker_bound(marker_bound_raw)
    file_snapshots["markerBound"] = marker_bound_record

    legacy_raw, legacy_record = admission_check_file(
        io, context["legacyInventory"], 4 * 1024 * 1024,
        "LEGACY_INVENTORY", ADMISSION_LEGACY_INVENTORY_SHA256)
    legacy = admission_validate_legacy_inventory(legacy_raw)
    file_snapshots["legacyInventory"] = legacy_record

    fresh_sidecar_raw, fresh_sidecar_record = admission_check_file(
        io, context["classInventory"], 4 * 1024 * 1024,
        "FRESH_CLASS_INVENTORY", context["classInventorySha256"])
    inherited_sidecar_raw, inherited_sidecar_record = admission_check_file(
        io, context["inheritedClassInventory"], 4 * 1024 * 1024,
        "INHERITED_CLASS_INVENTORY", context["inheritedClassInventorySha256"])
    admission_need(fresh_sidecar_raw == admission_inventory_bytes(
        manifest["classes"], manifest["classCount"], manifest["classBytes"]),
        "FRESH_INVENTORY_CONTENT")
    admission_need(inherited_sidecar_raw == admission_inventory_bytes(
        legacy["entries"], legacy["classCount"], legacy["totalBytes"]),
        "INHERITED_INVENTORY_CONTENT")
    fresh_tree = admission_validate_tree(
        context["classesRoot"], context["classInventory"], fresh_sidecar_raw,
        context["classInventorySha256"], manifest["classes"],
        manifest["classCount"], manifest["classBytes"])
    inherited_tree = admission_validate_tree(
        context["inheritedClassesRoot"], context["inheritedClassInventory"],
        inherited_sidecar_raw, context["inheritedClassInventorySha256"],
        legacy["entries"], legacy["classCount"], legacy["totalBytes"])
    file_snapshots["freshClassInventory"] = fresh_sidecar_record
    file_snapshots["inheritedClassInventory"] = inherited_sidecar_record

    source_list_raw, source_list_record = admission_check_file(
        io, context["sourceList"], 2 * 1024 * 1024, "SOURCE_LIST")
    admission_need(source_list_raw == admission_source_list_bytes(context),
                   "SOURCE_LIST_ORDER_AND_BYTES")
    file_snapshots["sourceList"] = source_list_record
    empty_source, empty_identity = admission_check_empty_directory(
        context["emptySourcepath"], "EMPTY_SOURCEPATH")
    directory_snapshots["emptySourcepath"] = (str(empty_source), empty_identity)

    capture, expected_call = admission_validate_capture_and_expected(
        io, context, capture_sha, manifest, file_snapshots)
    invocations = admission_expected_invocations(context)
    compile_file, compile_args, compile_limit, compile_stderr = invocations["compile"]
    compile_call = admission_snapshot_call(
        io, context, "compileCall", compile_file, compile_args, compile_limit,
        compile_stderr, manifest["compile"]["receipts"], manifest["compile"]["phase"])
    admission_need(compile_call["parent"]["elapsedMs"] == manifest["compile"]["processElapsedMs"]
                   and compile_call["result"]["totalElapsedMs"]
                   == manifest["compile"]["wrapperTotalElapsedMs"],
                   "COMPILE_TIMING_BINDING")
    native_file, native_args, native_limit, native_stderr = invocations["native"]
    native_call = admission_snapshot_call(
        io, context, "nativeCall", native_file, native_args, native_limit,
        native_stderr, capture["call"]["receipts"], capture["call"]["phase"])
    admission_need(native_call["parent"]["elapsedMs"] == capture["call"]["processElapsedMs"]
                   and native_call["result"]["totalElapsedMs"]
                   == capture["call"]["wrapperTotalElapsedMs"],
                   "NATIVE_TIMING_BINDING")
    marker_raw = native_call["stdout"]
    admission_need(0 < len(marker_raw) <= ADMISSION_MARKER_BYTES,
                   "NATIVE_MARKER_STREAM_BOUNDS")
    result_raw = admission_native_output(io, context, capture, file_snapshots, directory_snapshots)
    kernel_summary = validate_result_and_marker(
        result_raw, marker_raw, context["runId"], context["sourceManifestSha256"],
        context["wireContractSha256"], fixture_raw)
    call_snapshots = {"compile": compile_call["snapshot"], "native": native_call["snapshot"]}
    tree_snapshots = {"fresh": fresh_tree, "inherited": inherited_tree}
    return {
        "context": context, "capture": capture, "expectedCall": expected_call,
        "manifest": manifest, "resultRaw": result_raw, "markerRaw": marker_raw,
        "kernelSummary": kernel_summary, "files": file_snapshots,
        "directories": directory_snapshots, "trees": tree_snapshots,
        "calls": call_snapshots,
    }


def admission_compare_material(left, right):
    admission_need(left["files"] == right["files"], "CONSUMED_FILE_REPLACED")
    admission_need(left["directories"] == right["directories"], "CONSUMED_DIRECTORY_REPLACED")
    admission_need(left["trees"] == right["trees"], "CLASS_TREE_REPLACED")
    admission_need(left["calls"] == right["calls"], "RECEIPT_REPLACED")
    admission_need(left["resultRaw"] == right["resultRaw"]
                   and left["markerRaw"] == right["markerRaw"], "NATIVE_OUTPUT_REPLACED")


def admission_write_accepted(io, output_dir, result_raw, marker_raw):
    try:
        checked = Path(io._checked_directory(str(output_dir), "OUTPUT_DIRECTORY"))
        admission_need(not io._directory_names(checked, "OUTPUT_DIRECTORY"), "OUTPUT_NOT_EMPTY")
        io._write_exclusive(checked / "result.json", result_raw,
                            ADMISSION_PACKET_BYTES, "OUTPUT_RESULT")
        checked = Path(io._checked_directory(str(output_dir), "OUTPUT_DIRECTORY"))
        admission_need(sorted(io._directory_names(checked, "OUTPUT_DIRECTORY")) == ["result.json"],
                       "OUTPUT_RESULT_CLOSURE")
        io._write_exclusive(checked / "marker.txt", marker_raw,
                            ADMISSION_MARKER_BYTES, "OUTPUT_MARKER")
    except (ReaderError, RH_ReaderError):
        raise
    except Exception:
        raise ReaderError("ADMISSION_ACCEPTED_EXCLUSIVE_WRITE") from None
    return Path(output_dir) / "result.json", Path(output_dir) / "marker.txt"


def admission_read_accepted(io, output_dir, expected_names):
    path, identity = admission_output_directory(io, output_dir, expected_names)
    result, result_identity = admission_read_file(
        io, path / "result.json", ADMISSION_PACKET_BYTES, "ACCEPTED_RESULT")
    marker, marker_identity = admission_read_file(
        io, path / "marker.txt", ADMISSION_MARKER_BYTES, "ACCEPTED_MARKER")
    return {"directory": (str(path), identity),
            "result": admission_file_record(path / "result.json", result, result_identity),
            "marker": admission_file_record(path / "marker.txt", marker, marker_identity),
            "resultRaw": result, "markerRaw": marker}


def parse_args(argv=None):
    if argv is None:
        argv = sys.argv[1:]
    flags = ("--context", "--capture-proof-sha", "--base-generator", "--io-helper", "--output-dir")
    admission_need(type(argv) in (list, tuple) and len(argv) == 2 * len(flags), "CLI_ARGUMENTS")
    options = {}
    for at in range(0, len(argv), 2):
        flag, value = argv[at:at + 2]
        admission_need(type(flag) is str and flag in flags and flag not in options
                       and type(value) is str and bool(value) and "\x00" not in value
                       and not value.startswith("--"), "CLI_ARGUMENTS")
        options[flag] = value
    admission_need(set(options) == set(flags), "CLI_ARGUMENTS")
    admission_need(RH_is_digest(options["--capture-proof-sha"]), "CAPTURE_SHA_ARGUMENT")
    for flag in ("--context", "--base-generator", "--io-helper", "--output-dir"):
        admission_lexical_path(options[flag], "CLI_PATH")
    return options


def admission_main(options):
    context_path = admission_canonical_path(options["--context"], "CONTEXT_PATH", "file")
    output_dir = admission_canonical_path(options["--output-dir"], "OUTPUT_DIRECTORY", "directory")
    output_identity = admission_identity(output_dir, "directory", "OUTPUT_DIRECTORY_IDENTITY")
    try:
        admission_need(not list(output_dir.iterdir()), "OUTPUT_NOT_EMPTY")
    except OSError:
        raise ReaderError("ADMISSION_OUTPUT_DIRECTORY_READ") from None
    io, base_identity, io_identity = admission_load_io(
        options["--base-generator"], options["--io-helper"])
    base_path = admission_canonical_path(options["--base-generator"], "BASE_PATH", "file")
    io_path = admission_canonical_path(options["--io-helper"], "IO_HELPER_PATH", "file")
    context_raw, context_record = admission_check_file(io, str(context_path), 1024 * 1024, "CONTEXT")
    context = admission_parse_context(context_raw)
    admission_validate_output_location(
        context, str(output_dir), (str(context_path), str(base_path), str(io_path)))
    initial_files = {"context": context_record}
    initial_dirs = {"acceptedOutput": (str(output_dir), output_identity)}
    initial = admission_validate_material(
        io, context, context_raw, context_path, options["--capture-proof-sha"],
        initial_files, initial_dirs)
    accepted_result_path, accepted_marker_path = admission_write_accepted(
        io, output_dir, initial["resultRaw"], initial["markerRaw"])
    admission_canonical_path(str(accepted_result_path), "ACCEPTED_RESULT_PATH", "file")
    admission_canonical_path(str(accepted_marker_path), "ACCEPTED_MARKER_PATH", "file")
    accepted_first = admission_read_accepted(io, output_dir, list(ADMISSION_ACCEPTED_NAMES))
    admission_need(accepted_first["resultRaw"] == initial["resultRaw"]
                   and accepted_first["markerRaw"] == initial["markerRaw"],
                   "ACCEPTED_OUTPUT_READBACK")
    admission_need((accepted_first["directory"][1][0], accepted_first["directory"][1][1])
                   == (output_identity[0], output_identity[1]), "ACCEPTED_OUTPUT_DIRECTORY_IDENTITY")

    io_after, base_identity_after, io_identity_after = admission_load_io(
        options["--base-generator"], options["--io-helper"])
    admission_need(base_identity_after == base_identity and io_identity_after == io_identity,
                   "CHECKED_IO_REPLACED")
    final_files = {}
    final_dirs = {"acceptedOutput": (str(output_dir), output_identity)}
    final = admission_validate_material(
        io_after, context, context_raw, context_path, options["--capture-proof-sha"],
        final_files, final_dirs)
    admission_compare_material(initial, final)
    io_final, base_identity_final, io_identity_final = admission_load_io(
        options["--base-generator"], options["--io-helper"])
    admission_need(base_identity_final == base_identity and io_identity_final == io_identity,
                   "CHECKED_IO_CHANGED_DURING_RECHECK")
    accepted_final = admission_read_accepted(io_final, output_dir, list(ADMISSION_ACCEPTED_NAMES))
    admission_need(accepted_final["resultRaw"] == initial["resultRaw"]
                   and accepted_final["markerRaw"] == initial["markerRaw"]
                   and accepted_final["directory"] == accepted_first["directory"]
                   and accepted_final["result"] == accepted_first["result"]
                   and accepted_final["marker"] == accepted_first["marker"],
                   "ACCEPTED_OUTPUT_FINAL_READBACK")
    kernel_summary = final["kernelSummary"]
    summary = {
        "schema": ADMISSION_SUMMARY_SCHEMA,
        "status": ADMISSION_SUMMARY_STATUS,
        "captureProofSha256": options["--capture-proof-sha"],
        "runId": context["runId"],
        "sourceManifestSha256": context["sourceManifestSha256"],
        "wireContractSha256": context["wireContractSha256"],
        "fixtureOracleSha256": context["fixtureOracleSha256"],
        "resultSha256": hashlib.sha256(initial["resultRaw"]).hexdigest(),
        "resultBytes": len(initial["resultRaw"]),
        "markerSha256": hashlib.sha256(initial["markerRaw"]).hexdigest(),
        "markerBytes": len(initial["markerRaw"]),
        "references": kernel_summary["references"],
        "found": kernel_summary["found"],
        "noPath": kernel_summary["noPath"],
        "derivedExportUnits": kernel_summary["derivedExportUnits"],
        "derivedExportCopyEntries": kernel_summary["derivedExportCopyEntries"],
        "derivedExportCopyChunks": kernel_summary["derivedExportCopyChunks"],
        "derivedExportGroups": kernel_summary["derivedExportGroups"],
        "sourceClassesToolsBound": True,
        "compileAndNativeReceiptsBound": True,
        "nativeResultAndMarkerBound": True,
        "exclusiveWriteReadback": True,
        "consumedInputsRechecked": True,
        "nativeAfterPublicationAssertionsBound": True,
        "R7actualCounterProof": True,
        "newNativeProducerInvocations": 0,
        "newAuthoredInvocations": 0,
        "fullD": False,
    }
    return canonical_line(summary)


def main(argv=None):
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        sys.stderr.write("SW_PYTHON_ISOLATION_REQUIRED\n")
        return 2
    started = time.monotonic_ns()
    try:
        options = parse_args(sys.argv[1:] if argv is None else argv)
        line = admission_main(options)
        need(time.monotonic_ns() - started <= ADMISSION_MAX_READER_NS, "READER_DEADLINE")
        output = sys.stdout.buffer
        admission_need(output.write(line) == len(line), "STDOUT_WRITE")
        output.flush()
        return 0
    except (ReaderError, RH_ReaderError, GR7_ReaderError) as failure:
        code = getattr(failure, "code", "ADMISSION_FAILURE")
        if type(code) is not str or re.fullmatch(r"[A-Z0-9_]{1,64}", code) is None:
            code = "ADMISSION_FAILURE"
        sys.stderr.write(code + "\n")
        return 2
    except Exception:
        sys.stderr.write("ADMISSION_INTERNAL\n")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
