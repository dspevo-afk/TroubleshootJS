import hashlib
import json
import os
import re
import stat
import sys
import time
from pathlib import Path


ADMISSION_CONTEXT_SCHEMA = "tsj-natural-reference-host-context-v1"
ADMISSION_SUMMARY_SCHEMA = "tsj-source-bound-natural-reference-summary-v1"
ADMISSION_SUMMARY_STATUS = "PASS_SOURCE_BOUND_NATURAL_REFERENCE_ONLY"
ADMISSION_MAX_READER_NS = 180_000_000_000
ADMISSION_PACKET_BYTES = 1_048_576
ADMISSION_COMPILE_STDERR = (
    "warning: [options] bootstrap class path not set in conjunction with -source 1.7\r\n"
    "1 warning\r\n"
)
ADMISSION_CONTEXT_KEYS = (
    "schema", "runId", "sourceManifestSha256", "wireContractSha256",
    "producerManifest", "sourcePaths", "classesRoot", "classInventory",
    "classInventorySha256", "inheritedClassesRoot", "inheritedClassInventory",
    "inheritedClassInventorySha256", "legacyInventory", "gwtUser", "gwtDev",
    "java", "javac", "powershell", "rootCall", "processModule", "compileCall",
    "sourceList", "emptySourcepath", "nativeCall", "fixtureSpec",
    "nativeReceiptProof",
)
ADMISSION_SOURCE_MANIFEST_SHA256 = "df27c7750f61e4d4c959d60988a3381d64b6e94737da785e2b5ff76eaa811ada"
ADMISSION_WIRE_SHA256 = "dd738f0bbac0edc4f4cbaf4a0d2bba80ebeda9a581d79c052e29df8976e73dd6"
ADMISSION_FIXTURE_SHA256 = "5d2b62c98bd229a73be0e54e4b6adcde4d5080fb87046d8bd770455f80c1224a"
ADMISSION_LEGACY_INVENTORY_SHA256 = "7fe14c6f95ae1c99ddfb3f81d975ff7f9ee2a6e1851b09e32d9ca8f0d47889f9"
ADMISSION_NATIVE_PROOF_SHA256 = "aa88f01a93f7a1408268bbec37b9e271b87a7719df304b8e54a991e3d10c208a"
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
ADMISSION_SOURCE_ROWS = (
    ("PcbLayerRoutingPrototype.java", "owner", 172_568,
     "7bf4ab57198ff9444381ec5d677c1a2bc309bfa3e7f01b188c631ffe5f2b966c"),
    ("CohortTerminalTelemetryStudy.java", "study", 229_656,
     "8581ca447ca5d7fde0c56970daa9ea7fc38a1f2ea97eeb8b911621f3ccedfa14"),
    ("GenericRoutingReferenceCanary.java", "driver", 22_088,
     "68c78e29148d062969943b6ddf77bba15e3ca352ce03b16000eb9dfcded9e9c0"),
)
ADMISSION_FRESH_CLASS_COUNT = 173
ADMISSION_FRESH_CLASS_BYTES = 563_848
ADMISSION_INHERITED_CLASS_COUNT = 4_288
ADMISSION_INHERITED_CLASS_BYTES = 19_644_833
ADMISSION_COMPILE_RECEIPT_NAMES = (
    "owner.json", "parent.json", "request.json", "process.json",
    "result.json", "stdout.txt", "stderr.txt",
)
ADMISSION_CALL_NAMES = ADMISSION_COMPILE_RECEIPT_NAMES + ("wrapper.stdout", "wrapper.stderr")
ADMISSION_CALL_NAME_SET = frozenset(ADMISSION_CALL_NAMES)
ADMISSION_OUTPUT_NAME = "GENERIC_NATURAL_REFERENCES_V1.json"
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
    path = admission_lexical_path(value, code)
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
            # The exporter intentionally admits nonempty assets only. Empty
            # host streams belong to the existing host reader; retain the
            # surrounding regular-file and before/after identity fences.
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


def admission_validate_context_values(context):
    RH_exact_keys(context, ADMISSION_CONTEXT_KEYS, "ADMISSION_CONTEXT_KEYS")
    admission_need(context["schema"] == ADMISSION_CONTEXT_SCHEMA, "CONTEXT_SCHEMA")
    admission_need(type(context["runId"]) is str and UUID.fullmatch(context["runId"]) is not None,
                   "CONTEXT_RUN_ID")
    admission_need(context["sourceManifestSha256"] == ADMISSION_SOURCE_MANIFEST_SHA256
                   and context["wireContractSha256"] == ADMISSION_WIRE_SHA256,
                   "CONTEXT_SOURCE_PROFILE")
    for field in ("classInventorySha256", "inheritedClassInventorySha256"):
        admission_need(RH_is_digest(context[field]), "CONTEXT_INVENTORY_HASH")
    RH_exact_keys(context["sourcePaths"], ("owner", "study", "driver"),
                  "ADMISSION_SOURCE_PATH_KEYS")
    path_fields = (
        "producerManifest", "classesRoot", "classInventory", "inheritedClassesRoot",
        "inheritedClassInventory", "legacyInventory", "gwtUser", "gwtDev", "java",
        "javac", "powershell", "rootCall", "processModule", "compileCall", "sourceList",
        "emptySourcepath", "nativeCall", "fixtureSpec", "nativeReceiptProof",
    )
    paths = []
    for field in path_fields:
        paths.append((field, admission_lexical_path(context[field], "CONTEXT_" + field.upper())))
    for role in ("owner", "study", "driver"):
        paths.append(("sourcePaths." + role,
                      admission_lexical_path(context["sourcePaths"][role], "CONTEXT_SOURCE_PATH")))
    keys = [os.path.normcase(str(path)) for _field, path in paths]
    admission_need(len(keys) == len(set(keys)), "CONTEXT_PATH_ALIAS")
    admission_need(Path(context["compileCall"]) != Path(context["nativeCall"]), "CONTEXT_CALL_ALIAS")
    admission_need(Path(context["classesRoot"]) != Path(context["inheritedClassesRoot"]),
                   "CONTEXT_CLASS_ROOT_ALIAS")
    return context


def admission_parse_context(raw, expected_sha):
    admission_need(type(raw) is bytes and hashlib.sha256(raw).hexdigest() == expected_sha,
                   "CONTEXT_EXTERNAL_HASH")
    context = RH_host_document(raw, 1024 * 1024)
    return admission_validate_context_values(context)


def admission_validate_manifest(raw):
    manifest = RH_host_document(raw, RH_MAX_JSON)
    RH_exact_keys(manifest, (
        "classBytes", "classCount", "classes", "classpathOrder", "compile",
        "fixtureOracleSha256", "generatorSha256", "inheritedClosure", "inheritedSources",
        "newAuthoredInvocations", "ownerByteEqualQualifiedP", "ownerClassCount",
        "parentCommit", "schema", "scope", "sourceProofSha256", "sources",
        "wireContractSha256",
    ), "ADMISSION_MANIFEST_KEYS")
    admission_need(manifest["schema"] == "tsj-natural-generic-reference-producer-manifest-v1"
                   and manifest["scope"] == "GENERIC_NATURAL_ROUTING_REFERENCE_ONLY"
                   and manifest["wireContractSha256"] == ADMISSION_WIRE_SHA256
                   and manifest["fixtureOracleSha256"] == ADMISSION_FIXTURE_SHA256
                   and type(manifest["newAuthoredInvocations"]) is int
                   and manifest["newAuthoredInvocations"] == 0
                   and manifest["ownerByteEqualQualifiedP"] is True,
                   "MANIFEST_IDENTITY")
    admission_need(type(manifest["classCount"]) is int
                   and manifest["classCount"] == ADMISSION_FRESH_CLASS_COUNT
                   and type(manifest["classBytes"]) is int
                   and manifest["classBytes"] == ADMISSION_FRESH_CLASS_BYTES
                   and type(manifest["classes"]) is list,
                   "MANIFEST_CLASS_COUNTS")
    expected_sources = tuple((role, size, digest) for role, _key, size, digest in ADMISSION_SOURCE_ROWS)
    admission_need(type(manifest["sources"]) is list and len(manifest["sources"]) == 3,
                   "MANIFEST_SOURCE_COUNT")
    observed_sources = []
    for row in manifest["sources"]:
        RH_exact_keys(row, ("role", "bytes", "sha256"), "ADMISSION_MANIFEST_SOURCE_KEYS")
        observed_sources.append((row["role"], row["bytes"], row["sha256"]))
    admission_need(tuple(observed_sources) == expected_sources, "MANIFEST_SOURCE_PROFILE")
    prior = None
    for row in manifest["classes"]:
        RH_exact_keys(row, ("path", "bytes", "sha256"), "ADMISSION_MANIFEST_CLASS_KEYS")
        admission_need(type(row["path"]) is str and row["path"].endswith(".class")
                       and (prior is None or prior < row["path"])
                       and RH_is_int(row["bytes"], 1, RH_MAX_CLASS_BYTES)
                       and RH_is_digest(row["sha256"]), "MANIFEST_CLASS_ROW")
        prior = row["path"]
    inherited = manifest["inheritedClosure"]
    RH_exact_keys(inherited, ("classBytes", "classFilesRehashed", "inventorySha256", "jars",
                              "toolsRehashed"), "ADMISSION_INHERITED_MANIFEST_KEYS")
    admission_need(inherited["classBytes"] == ADMISSION_INHERITED_CLASS_BYTES
                   and inherited["classFilesRehashed"] == ADMISSION_INHERITED_CLASS_COUNT
                   and inherited["inventorySha256"] == ADMISSION_LEGACY_INVENTORY_SHA256
                   and inherited["toolsRehashed"] is True
                   and type(inherited["jars"]) is list and len(inherited["jars"]) == 2,
                   "MANIFEST_INHERITED_PROFILE")
    expected_jars = (
        ("GWT_DEPENDENCY", ADMISSION_TOOL_LIMITS["gwtUser"], ADMISSION_TOOL_SHA256["gwtUser"]),
        ("GWT_DEPENDENCY", ADMISSION_TOOL_LIMITS["gwtDev"], ADMISSION_TOOL_SHA256["gwtDev"]),
    )
    observed_jars = []
    for row in inherited["jars"]:
        RH_exact_keys(row, ("bytes", "role", "sha256"), "ADMISSION_MANIFEST_JAR_KEYS")
        observed_jars.append((row["role"], row["bytes"], row["sha256"]))
    admission_need(tuple(observed_jars) == expected_jars, "MANIFEST_JAR_PROFILE")
    compile_meta = manifest["compile"]
    RH_exact_keys(compile_meta, (
        "childExitCode", "isolatedCleanupDurationMs", "ownedRootAndLogsAbsent", "phase",
        "processElapsedMs", "receipts", "status", "terminationProven", "wrapperTotalElapsedMs",
    ), "ADMISSION_COMPILE_MANIFEST_KEYS")
    admission_need(compile_meta["phase"] == "compile-producer-r1"
                   and compile_meta["status"] == "PASS"
                   and type(compile_meta["childExitCode"]) is int and compile_meta["childExitCode"] == 0
                   and compile_meta["terminationProven"] is True
                   and compile_meta["ownedRootAndLogsAbsent"] is True
                   and compile_meta["isolatedCleanupDurationMs"] is None
                   and RH__is_finite_number(compile_meta["processElapsedMs"])
                   and 0 < compile_meta["processElapsedMs"] <= 60_000
                   and RH__is_finite_number(compile_meta["wrapperTotalElapsedMs"])
                   and compile_meta["wrapperTotalElapsedMs"] >= compile_meta["processElapsedMs"],
                   "MANIFEST_COMPILE_PROFILE")
    RH_exact_keys(compile_meta["receipts"], ADMISSION_COMPILE_RECEIPT_NAMES,
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
    prior = None
    for row in inventory["entries"]:
        RH_exact_keys(row, ("path", "bytes", "sha256"), "ADMISSION_LEGACY_CLASS_KEYS")
        admission_need(type(row["path"]) is str and row["path"].endswith(".class")
                       and (prior is None or prior < row["path"])
                       and RH_is_int(row["bytes"], 1, RH_MAX_CLASS_BYTES)
                       and RH_is_digest(row["sha256"]), "LEGACY_INVENTORY_ROW")
        prior = row["path"]
    return inventory


def admission_inventory_bytes(files, count, total):
    value = {"schema": RH_CLASS_SCHEMA, "files": files, "count": count, "bytes": total}
    return (json.dumps(value, ensure_ascii=True, sort_keys=True, separators=(",", ":"),
                       allow_nan=False) + "\n").encode("ascii")


def admission_tree_snapshot(root):
    RH__lstat_directory(root, "ADMISSION_CLASS_ROOT")
    root_identity = admission_identity(root, "directory", "CLASS_ROOT_IDENTITY")
    rows = []
    count = 0
    total = 0
    entries = [("", "D", root_identity)]
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
            rel = path.relative_to(root).as_posix()
            entries.append((rel, "D", admission_identity(path, "directory", "CLASS_TREE_READ")))
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
    entries.sort(key=lambda item: (item[0], item[1]))
    rows.sort()
    return {"rows": tuple(rows), "entries": tuple(entries), "count": count, "bytes": total}


def admission_validate_tree(root_value, inventory_path_value, inventory_raw, inventory_sha,
                            expected_rows, expected_count, expected_bytes):
    root = admission_canonical_path(root_value, "CLASS_ROOT_PATH", "directory")
    inventory_path = admission_canonical_path(inventory_path_value, "CLASS_INVENTORY_PATH", "file")
    admission_need(hashlib.sha256(inventory_raw).hexdigest() == inventory_sha,
                   "CLASS_INVENTORY_CONTEXT_HASH")
    sidecar = RH_strict_document(inventory_raw, 4 * 1024 * 1024)
    admission_need(inventory_raw == admission_inventory_bytes(expected_rows, expected_count, expected_bytes),
                   "CLASS_INVENTORY_EXPECTED_BYTES")
    before = admission_tree_snapshot(root)
    actual = RH__validate_class_tree(
        root, inventory_path, inventory_raw, {"classInventorySha256": inventory_sha})
    after = admission_tree_snapshot(root)
    admission_need(before == after, "CLASS_TREE_CHANGED_DURING_CHECK")
    admission_need(actual == expected_rows and before["count"] == expected_count
                   and before["bytes"] == expected_bytes,
                   "CLASS_TREE_EXPECTED_ROWS")
    return {"snapshot": after, "inventory": sidecar}


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
        "GENERIC_ROUTING_REFERENCE_V1", context["runId"],
        context["sourceManifestSha256"], context["wireContractSha256"],
    ]
    return {
        "compile": (context["javac"], compile_args, 60_000, ADMISSION_COMPILE_STDERR),
        "native": (context["java"], native_args, 480_000, ""),
    }


def admission_snapshot_call(io, context, call_role, expected_file, expected_args, limit_ms,
                            expected_stderr, expected_receipts):
    call_path = admission_canonical_path(context[call_role], call_role.upper() + "_PATH", "directory")
    directory_identity = admission_identity(call_path, "directory", call_role + "_IDENTITY")
    try:
        names = sorted(entry.name for entry in os.scandir(call_path))
    except OSError:
        raise ReaderError("ADMISSION_" + call_role.upper() + "_DIRECTORY_READ") from None
    admission_need(tuple(names) == tuple(sorted(ADMISSION_CALL_NAMES)),
                   call_role.upper() + "_EXACT_NINE_FILES")
    raw_by_name = {}
    file_records = {}
    for name in ADMISSION_CALL_NAMES:
        raw, record = admission_check_file(
            io, str(call_path / name), ADMISSION_RECEIPT_LIMITS[name],
            call_role.upper() + "_" + name.replace(".", "_").upper())
        raw_by_name[name] = raw
        file_records[name] = record
    result = admission_validate_call_envelope(call_role, raw_by_name, expected_receipts)
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
    admission_need(raw_by_name["stdout.txt"] == b"" if call_role == "compileCall" else True,
                   "COMPILE_STDOUT_EMPTY")
    kernel_result = RH__validate_owner_call(
        call_path, call_path / "parent.json", parent, request, process,
        expected_file, expected_args, limit_ms, Path(context["processModule"]),
        expected_stderr=expected_stderr)
    admission_need(kernel_result == result, call_role.upper() + "_KERNEL_RESULT")
    admission_need(process.get("FilePath") == expected_file
                   and process.get("Arguments") == expected_args,
                   call_role.upper() + "_PROCESS_ARGUMENTS")
    admission_need(parent.get("process") == process, call_role.upper() + "_PARENT_PROCESS")
    for field in ("StdoutLog", "StderrLog", "ProcessRoot"):
        admission_resource_absent(process.get(field), call_role.upper() + "_" + field.upper())
    admission_need(directory_identity == admission_identity(call_path, "directory", call_role + "_IDENTITY"),
                   call_role.upper() + "_DIRECTORY_CHANGED")
    snapshots = {
        "directory": (str(call_path), directory_identity),
        "files": tuple((name, file_records[name]) for name in ADMISSION_CALL_NAMES),
    }
    return {"snapshot": snapshots, "parent": parent, "result": result,
            "process": process, "stdout": raw_by_name["stdout.txt"]}


def admission_validate_call_envelope(call_role, raw_by_name, expected_receipts):
    admission_need(raw_by_name["wrapper.stderr"] == b"", call_role.upper() + "_WRAPPER_STDERR")
    result = RH_host_document(raw_by_name["result.json"], 64 * 1024)
    wrapper_result = RH_host_document(raw_by_name["wrapper.stdout"], 64 * 1024)
    admission_need(wrapper_result == result, call_role.upper() + "_WRAPPER_RESULT")
    admission_need(type(result.get("exitCode")) is int and result["exitCode"] == 0,
                   call_role.upper() + "_RESULT_EXIT_CODE_TYPE")
    RH_exact_keys(expected_receipts, ADMISSION_COMPILE_RECEIPT_NAMES,
                  "ADMISSION_EXPECTED_RECEIPT_KEYS")
    for name in ADMISSION_COMPILE_RECEIPT_NAMES:
        admission_need(hashlib.sha256(raw_by_name[name]).hexdigest() == expected_receipts[name],
                       call_role.upper() + "_RECEIPT_PIN_" + name.replace(".", "_").upper())
    return result


def admission_resource_absent(value, role):
    admission_lexical_path(value, role + "_PATH")
    try:
        remains = os.path.lexists(value)
    except (OSError, ValueError):
        raise ReaderError("ADMISSION_" + role + "_PATH") from None
    admission_need(not remains, role + "_REMAINS")


def admission_validate_native_proof(raw, context):
    proof = RH_host_document(raw, RH_MAX_JSON)
    try:
        acceptance = proof["gates"]["ACCEPTANCE_R1"]
        proof_context = acceptance["context"]
        native = acceptance["native"]
        receipts = native["receipts"]
    except (KeyError, TypeError):
        raise ReaderError("ADMISSION_NATIVE_PROOF_SHAPE") from None
    admission_need(type(proof_context) is dict
                   and proof_context.get("runId") == context["runId"]
                   and proof_context.get("sourceSha256") == context["sourceManifestSha256"]
                   and proof_context.get("wireSha256") == context["wireContractSha256"],
                   "NATIVE_PROOF_CONTEXT")
    RH_exact_keys(receipts, ADMISSION_COMPILE_RECEIPT_NAMES, "ADMISSION_NATIVE_PROOF_RECEIPTS")
    for digest in receipts.values():
        admission_need(RH_is_digest(digest), "NATIVE_PROOF_RECEIPT_HASH")
    return receipts


def admission_tree_context(io, context, manifest, legacy, file_snapshots):
    fresh_raw, fresh_record = admission_check_file(
        io, context["classInventory"], 4 * 1024 * 1024, "FRESH_INVENTORY",
        context["classInventorySha256"])
    inherited_raw, inherited_record = admission_check_file(
        io, context["inheritedClassInventory"], 4 * 1024 * 1024, "INHERITED_INVENTORY",
        context["inheritedClassInventorySha256"])
    admission_need(fresh_raw == admission_inventory_bytes(
        manifest["classes"], ADMISSION_FRESH_CLASS_COUNT, ADMISSION_FRESH_CLASS_BYTES),
        "FRESH_INVENTORY_CONTENT")
    admission_need(inherited_raw == admission_inventory_bytes(
        legacy["entries"], ADMISSION_INHERITED_CLASS_COUNT, ADMISSION_INHERITED_CLASS_BYTES),
        "INHERITED_INVENTORY_CONTENT")
    fresh = admission_validate_tree(
        context["classesRoot"], context["classInventory"], fresh_raw,
        context["classInventorySha256"], manifest["classes"],
        ADMISSION_FRESH_CLASS_COUNT, ADMISSION_FRESH_CLASS_BYTES)
    inherited = admission_validate_tree(
        context["inheritedClassesRoot"], context["inheritedClassInventory"], inherited_raw,
        context["inheritedClassInventorySha256"], legacy["entries"],
        ADMISSION_INHERITED_CLASS_COUNT, ADMISSION_INHERITED_CLASS_BYTES)
    file_snapshots["freshInventory"] = fresh_record
    file_snapshots["inheritedInventory"] = inherited_record
    return fresh, inherited


def admission_validate_material(io, context, expected_context_raw, context_path,
                                context_sha, file_snapshots, directory_snapshots):
    context_raw, context_record = admission_check_file(
        io, str(context_path), 1024 * 1024, "CONTEXT", context_sha)
    admission_need(context_raw == expected_context_raw, "CONTEXT_RECHECK")
    if "context" in file_snapshots:
        admission_need(file_snapshots["context"] == context_record, "CONTEXT_REPLACED_BEFORE_CHECK")
    context = admission_parse_context(context_raw, context_sha)
    file_snapshots["context"] = context_record

    manifest_raw, manifest_record = admission_check_file(
        io, context["producerManifest"], RH_MAX_JSON, "PRODUCER_MANIFEST",
        ADMISSION_SOURCE_MANIFEST_SHA256)
    manifest = admission_validate_manifest(manifest_raw)
    file_snapshots["producerManifest"] = manifest_record

    proof_raw, proof_record = admission_check_file(
        io, context["nativeReceiptProof"], RH_MAX_JSON, "NATIVE_RECEIPT_PROOF",
        ADMISSION_NATIVE_PROOF_SHA256)
    native_receipts = admission_validate_native_proof(proof_raw, context)
    file_snapshots["nativeReceiptProof"] = proof_record

    fixture_raw, fixture_record = admission_check_file(
        io, context["fixtureSpec"], 1024 * 1024, "FIXTURE_SPEC", ADMISSION_FIXTURE_SHA256)
    load_fixture_spec(fixture_raw)
    file_snapshots["fixtureSpec"] = fixture_record

    legacy_raw, legacy_record = admission_check_file(
        io, context["legacyInventory"], 4 * 1024 * 1024, "LEGACY_INVENTORY",
        ADMISSION_LEGACY_INVENTORY_SHA256)
    legacy = admission_validate_legacy_inventory(legacy_raw)
    file_snapshots["legacyInventory"] = legacy_record

    fresh, inherited = admission_tree_context(io, context, manifest, legacy, file_snapshots)
    tree_snapshots = {"fresh": fresh["snapshot"], "inherited": inherited["snapshot"]}

    for role, key, size, digest in ADMISSION_SOURCE_ROWS:
        path = context["sourcePaths"][key]
        _raw, record = admission_check_file(io, path, size, "SOURCE_" + key.upper(), digest, size)
        file_snapshots["source." + key] = record

    for role, limit in ADMISSION_TOOL_LIMITS.items():
        expected_bytes = limit if role in ("gwtUser", "gwtDev") else None
        _raw, record = admission_check_file(
            io, context[role], limit, "TOOL_" + role.upper(),
            ADMISSION_TOOL_SHA256[role], expected_bytes)
        file_snapshots["tool." + role] = record

    source_paths = [context["sourcePaths"][key] for _role, key, _size, _digest in ADMISSION_SOURCE_ROWS]
    expected_source_list = "".join('"' + path.replace("\\", "/") + '"\n'
                                   for path in source_paths).encode("utf-8")
    source_list_raw, source_list_record = admission_check_file(
        io, context["sourceList"], 2 * 1024 * 1024, "SOURCE_LIST")
    admission_need(source_list_raw == expected_source_list, "SOURCE_LIST_ORDER_AND_BYTES")
    file_snapshots["sourceList"] = source_list_record

    empty_source, empty_source_identity = admission_check_empty_directory(
        context["emptySourcepath"], "EMPTY_SOURCEPATH")
    directory_snapshots["emptySourcepath"] = (str(empty_source), empty_source_identity)

    invocations = admission_expected_invocations(context)
    compile_file, compile_args, compile_limit, compile_stderr = invocations["compile"]
    compile_result = admission_snapshot_call(
        io, context, "compileCall", compile_file, compile_args, compile_limit,
        compile_stderr, manifest["compile"]["receipts"])
    admission_need(compile_result["parent"]["elapsedMs"] == manifest["compile"]["processElapsedMs"]
                   and compile_result["result"]["totalElapsedMs"]
                   == manifest["compile"]["wrapperTotalElapsedMs"],
                   "COMPILE_TIMING_BINDING")
    native_file, native_args, native_limit, native_stderr = invocations["native"]
    native_result = admission_snapshot_call(
        io, context, "nativeCall", native_file, native_args, native_limit,
        native_stderr, native_receipts)
    call_snapshots = {"compile": compile_result["snapshot"], "native": native_result["snapshot"]}
    packet_raw = native_result["stdout"]
    packet = parse_packet(packet_raw)
    semantic = validate_packet(packet, context["runId"], context["sourceManifestSha256"],
                               context["wireContractSha256"], fixture_raw)
    admission_need(semantic["references"] == 2 and semantic["found"] == 1
                   and semantic["noPath"] == 1 and semantic["newAuthoredInvocations"] == 0
                   and semantic["fullD"] is False,
                   "NATURAL_PACKET_SUMMARY")
    return {
        "context": context, "packetRaw": packet_raw, "packet": packet,
        "semantic": semantic, "files": file_snapshots,
        "directories": directory_snapshots, "trees": tree_snapshots,
        "calls": call_snapshots,
    }


def admission_compare_material(left, right):
    admission_need(left["files"] == right["files"], "CONSUMED_FILE_REPLACED")
    admission_need(left["directories"] == right["directories"], "CONSUMED_DIRECTORY_REPLACED")
    admission_need(left["trees"] == right["trees"], "CLASS_TREE_REPLACED")
    admission_need(left["calls"] == right["calls"], "RECEIPT_REPLACED")
    admission_need(left["packetRaw"] == right["packetRaw"], "PACKET_REPLACED")


def admission_output_state(io, output_dir, expected_names):
    try:
        path = Path(io._checked_directory(str(output_dir), "OUTPUT_DIRECTORY"))
        names = io._directory_names(path, "OUTPUT_DIRECTORY")
    except (ReaderError, RH_ReaderError):
        raise
    except Exception:
        raise ReaderError("ADMISSION_OUTPUT_DIRECTORY_CHECK") from None
    path = admission_canonical_path(str(path), "OUTPUT_DIRECTORY", "directory")
    identity = admission_identity(path, "directory", "OUTPUT_DIRECTORY_IDENTITY")
    admission_need(sorted(names) == expected_names, "OUTPUT_DIRECTORY_CLOSURE")
    for name in names:
        admission_canonical_path(str(path / name), "OUTPUT_ENTRY", "file")
    return path, identity


def admission_write_exclusive(io, output_dir, raw):
    output_dir = admission_canonical_path(str(output_dir), "OUTPUT_DIRECTORY", "directory")
    output = output_dir / ADMISSION_OUTPUT_NAME
    admission_lexical_path(str(output), "OUTPUT_FILE_PATH")
    try:
        checked = Path(io._checked_directory(str(output_dir), "OUTPUT_DIRECTORY"))
        admission_need(not io._directory_names(checked, "OUTPUT_DIRECTORY"), "OUTPUT_NOT_EMPTY")
        io._write_exclusive(checked / ADMISSION_OUTPUT_NAME, raw,
                            ADMISSION_PACKET_BYTES, "OUTPUT_FILE")
    except (ReaderError, RH_ReaderError):
        raise
    except Exception:
        raise ReaderError("ADMISSION_OUTPUT_EXCLUSIVE_WRITE") from None
    return output


def parse_args(argv=None):
    if argv is None:
        argv = sys.argv[1:]
    flags = ("--context", "--context-sha", "--output-dir", "--base-generator", "--io-helper")
    admission_need(type(argv) in (list, tuple) and len(argv) == 2 * len(flags), "CLI_ARGUMENTS")
    options = {}
    for at in range(0, len(argv), 2):
        flag, value = argv[at:at + 2]
        admission_need(type(flag) is str and flag in flags and flag not in options
                       and type(value) is str and bool(value) and "\x00" not in value
                       and not value.startswith("--"), "CLI_ARGUMENTS")
        options[flag] = value
    admission_need(set(options) == set(flags), "CLI_ARGUMENTS")
    admission_need(RH_is_digest(options["--context-sha"]), "CONTEXT_SHA_ARGUMENT")
    for flag in ("--context", "--output-dir", "--base-generator", "--io-helper"):
        admission_lexical_path(options[flag], "CLI_PATH")
    return options


def admission_main(options):
    context_path = admission_canonical_path(options["--context"], "CONTEXT_PATH", "file")
    output_dir, output_identity = admission_check_empty_directory(options["--output-dir"], "OUTPUT_DIRECTORY")
    io, base_identity, io_identity = admission_load_io(options["--base-generator"], options["--io-helper"])
    context_raw, context_record = admission_check_file(
        io, str(context_path), 1024 * 1024, "CONTEXT", options["--context-sha"])
    context = admission_parse_context(context_raw, options["--context-sha"])
    initial_files = {"context": context_record}
    initial_dirs = {"outputDir": (str(output_dir), output_identity)}
    initial = admission_validate_material(
        io, context, context_raw, context_path, options["--context-sha"], initial_files, initial_dirs)
    raw_packet = initial["packetRaw"]
    output = admission_write_exclusive(io, output_dir, raw_packet)
    admission_canonical_path(str(output), "OUTPUT_FILE_PATH", "file")
    output_read, output_record = admission_read_file(
        io, output, ADMISSION_PACKET_BYTES, "OUTPUT_READBACK")
    admission_need(output_read == raw_packet, "OUTPUT_READBACK_BYTES")
    expected_output_names = [ADMISSION_OUTPUT_NAME]
    output_dir_after, output_identity_after = admission_output_state(io, output_dir, expected_output_names)
    admission_need(str(output_dir_after) == str(output_dir)
                   and (output_identity_after[0], output_identity_after[1])
                   == (output_identity[0], output_identity[1]), "OUTPUT_DIRECTORY_IDENTITY")

    io_after, base_identity_after, io_identity_after = admission_load_io(
        options["--base-generator"], options["--io-helper"])
    admission_need(base_identity_after == base_identity and io_identity_after == io_identity,
                   "CHECKED_IO_REPLACED")
    final_files = {}
    final_dirs = {"outputDir": (str(output_dir), output_identity)}
    final = admission_validate_material(
        io_after, context, context_raw, context_path, options["--context-sha"], final_files, final_dirs)
    admission_compare_material(initial, final)
    io_final, base_identity_final, io_identity_final = admission_load_io(
        options["--base-generator"], options["--io-helper"])
    admission_need(base_identity_final == base_identity and io_identity_final == io_identity,
                   "CHECKED_IO_CHANGED_DURING_RECHECK")
    final_output, final_output_identity = admission_output_state(io_final, output_dir, expected_output_names)
    admission_need(str(final_output) == str(output_dir)
                   and (final_output_identity[0], final_output_identity[1])
                   == (output_identity[0], output_identity[1]), "OUTPUT_DIRECTORY_REPLACED")
    output_read_after, final_output_record = admission_read_file(
        io_final, output, ADMISSION_PACKET_BYTES, "OUTPUT_FINAL_READBACK")
    admission_need(output_read_after == raw_packet and output_record == final_output_record,
                   "OUTPUT_FINAL_READBACK_BYTES")
    semantic = final["semantic"]
    summary = {
        "schema": ADMISSION_SUMMARY_SCHEMA,
        "status": ADMISSION_SUMMARY_STATUS,
        "contextSha256": options["--context-sha"],
        "runId": context["runId"],
        "sourceManifestSha256": context["sourceManifestSha256"],
        "wireContractSha256": context["wireContractSha256"],
        "references": semantic["references"],
        "found": semantic["found"],
        "noPath": semantic["noPath"],
        "inputSha256": hashlib.sha256(raw_packet).hexdigest(),
        "inputBytes": len(raw_packet),
        "sourceClassesToolsBound": True,
        "compileAndNativeReceiptsBound": True,
        "ownedRootsAndLogsAbsent": True,
        "exclusiveWriteReadback": True,
        "consumedInputsRechecked": True,
        "newNativeProducerInvocations": 0,
        "newAuthoredInvocations": 0,
        "fullD": False,
        "R7": False,
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
    except (ReaderError, RH_ReaderError) as failure:
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
