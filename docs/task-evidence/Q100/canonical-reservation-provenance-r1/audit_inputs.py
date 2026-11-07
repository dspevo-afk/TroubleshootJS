"""Closed P9 input/source auditor; no compile, native call or output mutation.

The initial caller reviews and hashes both input scripts before execution.
Later callers trust an externally authenticated pins SHA and authenticate script
bytes before launch. A self row is a consistency check, not self-authentication.
"""
from __future__ import annotations

import sys
if not (sys.flags.isolated and sys.dont_write_bytecode):
    sys.stderr.buffer.write(b"P9_RESERVATION_INPUT_REJECT P9_INPUT_TOOL_IDENTITY\n")
    sys.stderr.buffer.flush()
    raise SystemExit(1)

import ctypes
import hashlib
import json
import math
import os
from pathlib import Path, PurePosixPath
import re
import stat

PARENT = "4623f1e5680503527a4423c2ca31fb09ede3cbb8"
P8_PARENT = "0891474cadc4b9e00d7911e81c124721a11efb87"
LEDGER_SHA = "e5e5325644128e2f989e161e31eb1dcb669bfa2e3bfc74fe7f05821cf67bcf2d"
Q_BASENAME = "tsj-q100-fixed-prefix-capture-840a8ef24dbb448f830d2786a95a1591"
SOURCE33_BASENAME = "geometric-prefix-source-gates-r33-80fb23ced3575719833471100b325a1d"
SOURCE33_SHA = "b3bca1391fcb8e229e6f34dddddfc52ae77447a02a064d9e7eb8a30e7bc8ba7a"
P8_INVENTORY_SHA = "dba31be32c8d0d838fa8a1859c2a37a62a619b517515714ef519e59ce4a02de8"
CANARY_SHA = "11b3fa459e9bf6950c7ee3d3e804a3b37638bb23cc6c322aaa8ef94549462a5a"
CANARY_QUAL_SHA = "755f921b068f325c9e81315b88f38c4bc8b7196dd61d65f8c8ecf61e26e43276"
P6_SHA = "b640587f9cb1ac3f3ec8feff85940f4b7a9c8fda37570b50bfa30c39fd067771"
P5_SHA = "1e570d555c919755d82e5e0f0c728425c7aa313c3bd2c123ebfd874832a7b636"
P4_SHA = "ca06e1ab3c1a7bcb11dab90cba7b64dc9d95b031b09d76678f408d9fa0f0f7e0"
PYTHON_SHA = "ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec"
JAVA_SHA = "e71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce"
JAVAC_SHA = "61fa6463ee5b59004719b9833a16b148740cfa9f2d1f5a95ca598cc90be04c64"
PS_SHA = "60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f"
MODULE_SHA = "fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c"
ROUTER_SHA = "3bc847e015e1b379dcafcb1e39eb4964e65e7a57d6ef06ed2c710cca1ff48f2f"
P8_READER_SHA = "0372a850b058f1fe37f37d80dcbd0f467ee60c1b50b63ddbd22394c21416eba9"
MAX_FILE = 100 * 1024 * 1024
MAX_INVENTORY = 1024 * 1024
MAX_PINS = 4 * 1024 * 1024
MAX_LINE = 65536
INPUT_SCHEMA = "tsj-q100-reservation-provenance-inputs-v1"
SOURCE_SCHEMA = "tsj-q100-reservation-provenance-source-v1"
COUNTS = {"existing": 3437, "direct": 17, "derived": 2, "total": 3456}
EXISTING_BYTES = 283692817
FILE_KEYS = {"path", "bytes", "sha256"}
ROW_KEYS = FILE_KEYS | {"kind", "role", "origin"}
PINS_KEYS = {"schema", "sourceParent", "repositoryRoot", "p8Prerequisite", "publishedP8", "sourceInventory", "roles", "toolPaths", "files"}
CONTRACTS = ("PLAN_R1.md", "RAW_OPERANDS_AND_ORACLE_R1.md", "NATIVE_WORK_R1.md", "SOURCE_INPUTS_R1.md", "HOST_R1.md", "READER_R1.md", "CONTROLS_R1.md")
DIRECT = {name: "CONTRACT" for name in (*CONTRACTS, "CONTRACT_SET_R1.json")}
DIRECT.update({name: "INPUT_TOOL" for name in ("build_overlay.py", "make_inputs.py", "audit_inputs.py")})
DIRECT.update({"read_provenance.py": "READER", "test_provenance.py": "TEST", "run-provenance.ps1": "HOST", "run-controls.ps1": "CONTROLS", "source/CanonicalReservationProvenanceStudy.java": "NATIVE_SOURCE", "source/CanonicalReservationProvenanceCanary.java": "NATIVE_SOURCE"})
DERIVED = {"overlays-r1/PcbNetRouter.java": "ROUTER_OVERLAY", "overlays-r1/overlay-manifest.json": "OVERLAY_PROOF"}
PUBLICATION = {
    "QUALIFICATION_R1.json": (5732, "ef2b90b7ca3b682cd47a07c30a37a408cc9719080bcfcbd7fd920cc91e0c85d7"),
    "SOURCE_COPIES_R1.json": (9459, "5455335c6af44a8ed8e09d869a2846506e14db88cf196f71f32ba398984c77ab"),
    "OBSERVATIONS_R1.json": (39703, "721a78f7fe2d7d6447797b20145c24c0dcf1657f5670a99e0f501a08dbd6c184"),
    "SEMANTIC_CONTROLS_R1.json": (1801, "18416fbed1b9189abf764a5840924422a8af6a465970d410ed8d75912fb8a78f"),
}
MODE_ROLES = ("router", "support", "writer", "adapter", "blocker", "queries", "stageInspector", "netRouterOverlay", "rulesOverlay", "reasonCanary")
BRIDGES = ("CirSim.java", "ChipElm.java", "PhysicalSpecificationDeveloperVerifier.java")
EXCLUDED = {"src/com/lushprojects/circuitjs1/client/" + n for n in (*BRIDGES, "PcbLayerRoutingPrototype.java", "PcbNetRouter.java", "PcbTwoLayerRules.java")}
HEX = re.compile(r"[0-9a-f]{64}\Z")
PROTECTED = re.compile(r"hard|psychotic|full[-_]?30", re.I)
ATTACH = b"fixedGraphReasonObserverForStudy=CanonicalReservationProvenanceStudy.decorateForStudy(this,observer);"
DETACH = b"            CanonicalReservationProvenanceStudy.detachForStudy(fixedGraphReasonObserverForStudy);\n"
SNAPSHOT = (b"        CanonicalReservationProvenanceStudy.RouterOperands snapshotReservationOperandsForStudy() {\n"
            b"            return new CanonicalReservationProvenanceStudy.RouterOperands(layer,layout,board,outline,minX,minY,gridWidth,gridHeight,checkProfiledPadTouches,emptyComponentFace,activeNetId,activeTraceWidth,pads,padNets,padWidths,padBounds,escapeCorridors);\n"
            b"        }\n")
EDITS = (("ATTACH_DECORATOR", 70923, 42, ATTACH), ("DETACH_RELEASE", 71035, 0, DETACH), ("PASSIVE_SNAPSHOT", 80109, 0, SNAPSHOT))


class Reject(Exception):
    def __init__(self, code):
        self.code = code
        super().__init__(code)


def need(ok, code):
    if not ok:
        raise Reject(code)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def normpath(path):
    return os.path.normcase(os.path.abspath(str(path))).casefold()


def exact_obj(value, keys, code):
    need(type(value) is dict and set(value) == set(keys), code)
    return value


def integer(value, code, maximum=MAX_FILE):
    need(type(value) is int and 0 <= value <= maximum, code)
    return value


def digest(value, code):
    need(type(value) is str and HEX.fullmatch(value) is not None, code)
    return value


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=True, sort_keys=True, separators=(",", ":"), allow_nan=False) + "\n").encode("ascii")


def _pairs(items):
    result = {}
    for key, value in items:
        need(key not in result, "P9_INPUT_JSON")
        result[key] = value
    return result


def parse_json(raw, code="P9_INPUT_JSON", canonical=False):
    try:
        need(type(raw) is bytes and not raw.startswith(b"\xef\xbb\xbf"), code)
        value = json.loads(raw.decode("ascii"), object_pairs_hook=_pairs,
                           parse_constant=lambda _v: (_ for _ in ()).throw(Reject(code)))
        def finite(v):
            if type(v) is float:
                need(math.isfinite(v), code)
            elif type(v) is dict:
                for x in v.values(): finite(x)
            elif type(v) is list:
                for x in v: finite(x)
        finite(value)
        if canonical:
            need(raw == json_bytes(value), code)
        return value
    except Reject:
        raise
    except (UnicodeError, ValueError, TypeError, RecursionError, OverflowError):
        raise Reject(code) from None


def _stat_identity(info):
    return (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns,
            getattr(info, "st_birthtime_ns", info.st_ctime_ns), getattr(info, "st_file_attributes", 0))


def no_reparse(path, code="P9_INPUT_ROOT_BINDING"):
    for part in (path, *path.parents):
        try:
            info = part.lstat()
        except OSError:
            raise Reject(code) from None
        need(not stat.S_ISLNK(info.st_mode) and not (getattr(info, "st_file_attributes", 0) & 0x400), code)


def canonical(value, kind="file", code="P9_INPUT_ROOT_BINDING", exact=False):
    need(isinstance(value, (str, Path)) and str(value) and str(value).isascii(), code)
    path = Path(value)
    need(path.is_absolute() and re.match(r"^[A-Za-z]:[\\/]", str(value)) is not None and ":" not in str(value)[2:] and ".." not in path.parts, code)
    no_reparse(path, code)
    try:
        result = path.resolve(strict=True)
        info = result.stat()
    except OSError:
        raise Reject(code) from None
    need((kind == "file" and stat.S_ISREG(info.st_mode)) or (kind == "dir" and stat.S_ISDIR(info.st_mode)), code)
    if exact:
        need(str(value) == str(result), code)
    return result


def below(path, root):
    try:
        Path(path).relative_to(Path(root))
        return True
    except ValueError:
        return False


class Reads:
    def __init__(self):
        self.consumed = {}

    def read(self, path, limit=MAX_FILE, expected=None, code="P9_INPUT_FILE_HASH"):
        path = canonical(path, code=code)
        key = normpath(path)
        if key in self.consumed:
            limit = min(limit, self.consumed[key]["limit"])
        # Owner-excluded source/data paths are rejected before opening their body.
        need(not any(PROTECTED.search(p) for p in path.parts), "P9_INPUT_PROTECTED_PATH")
        try:
            before = path.stat()
            need(before.st_size <= limit, code)
            with path.open("rb") as stream:
                opened = os.fstat(stream.fileno())
                need(_stat_identity(opened) == _stat_identity(before), code)
                raw = stream.read(before.st_size + 1)
                ended = os.fstat(stream.fileno())
            no_reparse(path, code)
            after = path.stat()
            need(len(raw) <= limit and _stat_identity(before) == _stat_identity(ended) == _stat_identity(after) and len(raw) == before.st_size, code)
        except OSError:
            raise Reject(code) from None
        row = {"path": str(path), "bytes": len(raw), "sha256": sha(raw)}
        if expected is not None:
            need(row["bytes"] == expected["bytes"] and row["sha256"] == expected["sha256"], code)
        observation = {**row, "identity": _stat_identity(after), "limit": limit}
        if key in self.consumed:
            need(all(self.consumed[key][k] == observation[k] for k in ("path", "bytes", "sha256", "identity")), code)
        self.consumed[key] = observation
        return raw

    def manifest(self, path, expected_sha, expected_bytes=None):
        raw = self.read(path, MAX_PINS, code="P9_INPUT_DEPENDENCY_CLOSURE")
        need(sha(raw) == expected_sha and (expected_bytes is None or len(raw) == expected_bytes), "P9_INPUT_DEPENDENCY_CLOSURE")
        return parse_json(raw), raw

    def recheck(self):
        for row in list(self.consumed.values()):
            self.read(row["path"], row["limit"], expected=row)


def actual_image():
    need(os.name == "nt" and sys.flags.isolated and sys.dont_write_bytecode, "P9_INPUT_TOOL_IDENTITY")
    fn = ctypes.WinDLL("kernel32", use_last_error=True).GetModuleFileNameW
    fn.argtypes = (ctypes.c_void_p, ctypes.c_wchar_p, ctypes.c_uint32)
    fn.restype = ctypes.c_uint32
    buf = ctypes.create_unicode_buffer(32768)
    size = fn(None, buf, len(buf))
    need(0 < size < len(buf), "P9_INPUT_TOOL_IDENTITY")
    return canonical(buf.value, code="P9_INPUT_TOOL_IDENTITY")


def os_temp():
    need(os.name == "nt", "P9_INPUT_ROOT_BINDING")
    fn = ctypes.WinDLL("kernel32", use_last_error=True).GetTempPathW
    fn.argtypes = (ctypes.c_uint32, ctypes.c_wchar_p)
    fn.restype = ctypes.c_uint32
    buf = ctypes.create_unicode_buffer(32768)
    size = fn(len(buf), buf)
    need(0 < size < len(buf), "P9_INPUT_ROOT_BINDING")
    return canonical(buf.value, "dir")


def file_map(rows, code="P9_INPUT_DEPENDENCY_CLOSURE"):
    need(type(rows) is list, code)
    result = {}
    for row in rows:
        exact_obj(row, FILE_KEYS, code)
        path = canonical(row["path"], code=code, exact=True)
        integer(row["bytes"], code)
        digest(row["sha256"], code)
        key = normpath(path)
        need(key not in result, code)
        result[key] = dict(row)
    return result


def source_map(rows):
    need(type(rows) is list and len(rows) == 1084, "P9_INPUT_DEPENDENCY_CLOSURE")
    result = {}
    for row in rows:
        exact_obj(row, FILE_KEYS | {"role"}, "P9_INPUT_DEPENDENCY_CLOSURE")
        rel = relative(row["path"])
        need(rel not in result and type(row["role"]) is str and row["role"], "P9_INPUT_DEPENDENCY_CLOSURE")
        integer(row["bytes"], "P9_INPUT_DEPENDENCY_CLOSURE")
        digest(row["sha256"], "P9_INPUT_DEPENDENCY_CLOSURE")
        result[rel] = row
    need(sum(r["bytes"] for r in rows) == 88031179 and sum(p.startswith("src/") for p in result) == 850, "P9_INPUT_DEPENDENCY_CLOSURE")
    return result


def relative(value):
    need(type(value) is str and value and value.isascii() and "\\" not in value and ":" not in value and not value.startswith("/"), "P9_INPUT_ROOT_BINDING")
    path = PurePosixPath(value)
    need(all(x not in ("", ".", "..") for x in value.split("/")) and path.as_posix() == value, "P9_INPUT_ROOT_BINDING")
    need(PROTECTED.search(value) is None, "P9_INPUT_PROTECTED_PATH")
    return value


def contract_inputs(root, reads):
    raw = reads.read(root / "CONTRACT_SET_R1.json", MAX_LINE)
    need(sha(raw) == LEDGER_SHA, "P9_INPUT_CONTRACT_SET")
    ledger = parse_json(raw, canonical=True)
    exact_obj(ledger, {"schema", "status", "sourceParent", "contracts"}, "P9_INPUT_CONTRACT_SET")
    need(ledger["schema"] == "tsj-q100-reservation-contract-set-v1" and ledger["status"] == "FROZEN" and ledger["sourceParent"] == PARENT, "P9_INPUT_CONTRACT_SET")
    need(type(ledger["contracts"]) is list and len(ledger["contracts"]) == 7, "P9_INPUT_CONTRACT_SET")
    need([r.get("path") for r in ledger["contracts"]] == sorted(CONTRACTS), "P9_INPUT_CONTRACT_SET")
    for row in ledger["contracts"]:
        exact_obj(row, FILE_KEYS, "P9_INPUT_CONTRACT_SET")
        reads.read(root / relative(row["path"]), expected=row, code="P9_INPUT_CONTRACT_SET")
    return ledger


def load_existing(reads, p8_pins_path, repository=None):
    # The caller supplies the sole bootstrap path. A basename is not authority:
    # the raw digest and its embedded original paths bind the genuine location.
    source33 = canonical(p8_pins_path, exact=True, code="P9_INPUT_DEPENDENCY_CLOSURE")
    need(source33.name == "input-study-pins.json" and source33.parent.name == SOURCE33_BASENAME and source33.parent.parent.name == Q_BASENAME, "P9_INPUT_DEPENDENCY_CLOSURE")
    q = source33.parent.parent
    p8, raw = reads.manifest(source33, SOURCE33_SHA, 10497)
    need(p8.get("parent") == P8_PARENT and p8.get("schema") == "tsj-q100-geometric-prefix-study-inputs-v1", "P9_INPUT_DEPENDENCY_CLOSURE")
    need(p8["sourceInventory"] == {"path": str(source33.parent / "native-source-inventory.jsonl"), "sha256": P8_INVENTORY_SHA} and p8["canaryPrerequisite"]["root"] == str(q / "canonical-geometric-reasons-r1"), "P9_INPUT_DEPENDENCY_CLOSURE")
    repo = canonical(p8["repositoryRoot"], "dir")
    if repository is not None:
        need(str(repo) == str(canonical(repository, "dir", exact=True)), "P9_INPUT_ROOT_BINDING")
    p8rows = file_map(p8["files"])
    need(len(p8rows) == 25 and sum(r["bytes"] for r in p8rows.values()) == 2541232, "P9_INPUT_DEPENDENCY_CLOSURE")
    can_path = q / "canonical-geometric-reasons-r1/input-canary-pins-r9.json"
    p6_path = q / "canonical-prefix-rejection-stages-r1/input-pins-r6.json"
    need(p8["canaryPrerequisite"]["pinsPath"] == str(canonical(can_path)) and p8["canaryPrerequisite"]["pinsSha256"] == CANARY_SHA and p8["p6Context"] == {"pinsPath": str(canonical(p6_path)), "pinsSha256": P6_SHA}, "P9_INPUT_DEPENDENCY_CLOSURE")
    canary, _ = reads.manifest(can_path, CANARY_SHA, 263422)
    p6, _ = reads.manifest(p6_path, P6_SHA, 276765)
    need(canary["parent"] == P8_PARENT and normpath(canary["repositoryRoot"]) == normpath(repo) and p6["parent"] == "831d69a41d9504bfb845caef6942e9ef58c9d6d2", "P9_INPUT_DEPENDENCY_CLOSURE")
    baseline = source_map(canary["sourceRows"])
    need(source_map(p6["sourceRows"]) == baseline, "P9_INPUT_DEPENDENCY_CLOSURE")
    can_rows = file_map(canary["files"])
    p6rows = file_map(p6["files"])
    need(len(can_rows) == 46 and sum(r["bytes"] for r in can_rows.values()) == 3378741 and len(p6rows) == 84 and sum(r["bytes"] for r in p6rows.values()) == 15691400, "P9_INPUT_DEPENDENCY_CLOSURE")
    p5_path = q / "canonical-prefix-blockers-r1/input-pins-r7.json"
    p4_path = q / "canonical-prefix-capture-r1/input-pins-r4.json"
    need(p6["p5InputPinsPath"] == str(p5_path), "P9_INPUT_DEPENDENCY_CLOSURE")
    p5, _ = reads.manifest(p5_path, P5_SHA, 260237)
    need(p5["p4InputPinsPath"] == str(p4_path), "P9_INPUT_DEPENDENCY_CLOSURE")
    p4, _ = reads.manifest(p4_path, P4_SHA, 256979)
    need(source_map(p5["sourceRows"]) == baseline, "P9_INPUT_DEPENDENCY_CLOSURE")
    need(p5["repositoryRoot"] == p6["historicalRoot"], "P9_INPUT_DEPENDENCY_CLOSURE")
    for row in file_map(p5["files"]).values():
        need(p6rows.get(normpath(row["path"])) == row, "P9_INPUT_DEPENDENCY_CLOSURE")
    qual_path = q / "canonical-geometric-reasons-r1/CANARY_QUALIFICATION_R1.json"
    qualification, _ = reads.manifest(qual_path, CANARY_QUAL_SHA, 2198)
    need(p8["canaryPrerequisite"]["qualificationPath"] == str(canonical(qual_path)) and p8["canaryPrerequisite"]["qualificationSha256"] == CANARY_QUAL_SHA, "P9_INPUT_DEPENDENCY_CLOSURE")
    inv_path = Path(p8["sourceInventory"]["path"])
    inv = reads.read(inv_path, MAX_INVENTORY, p8rows[normpath(inv_path)])
    need(sha(inv) == P8_INVENTORY_SHA == p8["sourceInventory"]["sha256"], "P9_INPUT_DEPENDENCY_CLOSURE")
    records = parse_inventory_lines(inv)
    need(records[0] == {"kind": "header", "schema": "tsj-q100-geometric-prefix-source-v1", "sourceParent": P8_PARENT, "rowCount": 1084, "productionCount": 850} and records[-1] == {"kind": "footer", "rowCount": 1084}, "P9_INPUT_DEPENDENCY_CLOSURE")
    need(records[1:-1] == [{"kind": "source", **baseline[k]} for k in sorted(baseline)], "P9_INPUT_DEPENDENCY_CLOSURE")
    roots = {"E": str(repo), "P6_REPO": str(canonical(p6["repositoryRoot"], "dir")), "P5_REPO": str(canonical(p6["historicalRoot"], "dir")), "SOURCE33": str(source33.parent), "P8": str(canonical(q / "canonical-geometric-prefix-study-r1", "dir")), "CANARY": str(canonical(q / "canonical-geometric-reasons-r1", "dir")), "P6": str(canonical(q / "canonical-prefix-rejection-stages-r1", "dir")), "P5": str(canonical(q / "canonical-prefix-blockers-r1", "dir")), "P4": str(canonical(q / "canonical-prefix-capture-r1", "dir")), "BRIDGE": str(canonical(canary["bridgeRoot"], "dir")), "BASE_OVERLAY": str(canonical(q / "prefix-via-ramp-r1/overlays-r5/BASE", "dir")), "PUB": str(canonical(repo / "docs/task-evidence/Q100", "dir")), "PYTHON": str(canonical(Path(p8["toolPaths"]["pythonExe"]).parent, "dir")), "JDK": str(canonical(p8["toolPaths"]["javaHome"], "dir")), "PS": str(canonical(Path(p8["toolPaths"]["powershellExe"]).parent, "dir"))}
    need(normpath(roots["BRIDGE"]) == normpath(q / "outf-graph-r1/host-prep-r1/host-authored-v6-r2/driver"), "P9_INPUT_ROOT_BINDING")
    existing = {}
    def add(path, size, hash_value, origin=None, rel=None, role="INPUT", kind="dependency"):
        path = canonical(path)
        key = normpath(path)
        if key in existing:
            need(existing[key]["bytes"] == size and existing[key]["sha256"] == hash_value, "P9_INPUT_DEPENDENCY_CLOSURE")
            return
        if origin is None:
            candidates = [(name, Path(value)) for name, value in roots.items() if below(path, value)]
            need(bool(candidates), "P9_INPUT_ROOT_BINDING")
            origin, root = max(candidates, key=lambda pair: len(str(pair[1])))
            rel = path.relative_to(root).as_posix()
        existing[key] = {"kind": kind, "path": relative(rel), "bytes": integer(size, "P9_INPUT_DEPENDENCY_CLOSURE"), "sha256": digest(hash_value, "P9_INPUT_DEPENDENCY_CLOSURE"), "role": role, "origin": origin}
    for origin in ("E", "P6_REPO", "P5_REPO"):
        for rel, row in baseline.items():
            add(Path(roots[origin]).joinpath(*rel.split("/")), row["bytes"], row["sha256"], origin, rel, row["role"], "source")
    for row in (*p8rows.values(), *can_rows.values(), *p6rows.values()):
        add(row["path"], row["bytes"], row["sha256"])
    add(source33, len(raw), SOURCE33_SHA)
    need(len(existing) == 3386 and sum(r["bytes"] for r in existing.values()) == 282230788, "P9_INPUT_DEPENDENCY_CLOSURE")
    pub_root = Path(roots["PUB"]) / "canonical-geometric-prefix-study-r1"
    pub = {}
    for name, (size, hash_value) in PUBLICATION.items():
        value, _ = reads.manifest(pub_root / name, hash_value, size)
        pub[name] = value
    qualification = pub["QUALIFICATION_R1.json"]
    need(qualification["runtimeInputSha256"] == SOURCE33_SHA and qualification["sourceCopiesSha256"] == PUBLICATION["SOURCE_COPIES_R1.json"][1] and qualification["observationsSha256"] == PUBLICATION["OBSERVATIONS_R1.json"][1] and qualification["semanticControlsSha256"] == PUBLICATION["SEMANTIC_CONTROLS_R1.json"][1], "P9_INPUT_DEPENDENCY_CLOSURE")
    ledger = pub["SOURCE_COPIES_R1.json"]
    need(ledger["pathBase"] == ".." and ledger["fileCount"] == 47 and len(ledger["files"]) == 47 and sum(r["bytes"] for r in ledger["files"]) == 1405334, "P9_INPUT_DEPENDENCY_CLOSURE")
    publication_only = {"canonical-geometric-prefix-study-r1/run-study-outer-r1.ps1", "canonical-geometric-reasons-r1/BASE_SEMANTIC_SEAMS_R1.md"}
    pairs = 0
    for row in ledger["files"]:
        exact_obj(row, FILE_KEYS, "P9_INPUT_DEPENDENCY_CLOSURE")
        rel = relative(row["path"])
        prefix, suffix = rel.split("/", 1)
        need(prefix in ("canonical-geometric-prefix-study-r1", "canonical-geometric-reasons-r1"), "P9_INPUT_DEPENDENCY_CLOSURE")
        runtime = Path(roots["P8" if prefix == "canonical-geometric-prefix-study-r1" else "CANARY"]) / suffix
        if rel not in publication_only:
            match = existing.get(normpath(runtime))
            need(match is not None and (match["bytes"], match["sha256"]) == (row["bytes"], row["sha256"]), "P9_INPUT_DEPENDENCY_CLOSURE")
            pairs += 1
        add(Path(roots["PUB"]) / rel, row["bytes"], row["sha256"])
    need(pairs == 45, "P9_INPUT_DEPENDENCY_CLOSURE")
    for name, (size, hash_value) in PUBLICATION.items():
        add(pub_root / name, size, hash_value)
    need(len(existing) == 3437 and sum(r["bytes"] for r in existing.values()) == EXISTING_BYTES, "P9_INPUT_DEPENDENCY_CLOSURE")
    tools = {**p8["toolPaths"], "javaExe": str(Path(roots["JDK"]) / "bin/java.exe"), "javacExe": str(Path(roots["JDK"]) / "bin/javac.exe"), "gwtUser": str(repo / ".tools/gwt-2.7.0/gwt-user-2.7.0.jar"), "gwtDev": str(repo / ".tools/gwt-2.7.0/gwt-dev-2.7.0.jar")}
    return {"repositoryRoot": str(repo), "roots": roots, "qRoot": str(q), "p8PinsPath": str(source33), "existing": existing, "tools": tools, "p8Pins": p8, "p8PinsRaw": raw, "p8Rows": p8rows, "p8SourceRows": list(canary["sourceRows"]), "p8SourceInventoryRaw": inv, "canaryPins": canary, "p6Pins": p6, "publicationRoot": str(pub_root)}


def p9_root(path, roots):
    root = canonical(path, "dir", exact=True)
    need(root.name == "canonical-reservation-provenance-r1" and below(root, os_temp()) and not below(root, Path(roots["SOURCE33"]).parent) and all(not below(root, old) and not below(old, root) for old in roots.values()), "P9_INPUT_ROOT_BINDING")
    return root


def roles_for(root, repository):
    return {"native": {"study": str(root / "source/CanonicalReservationProvenanceStudy.java"), "canary": str(root / "source/CanonicalReservationProvenanceCanary.java"), "routerOverlay": str(root / "overlays-r1/PcbNetRouter.java")}, "process": {"helper": str(Path(repository) / "scripts/VerifierIsolation.psm1"), "host": str(root / "run-provenance.ps1"), "controls": str(root / "run-controls.ps1"), "reader": str(root / "read_provenance.py")}, "input": {"builder": str(root / "build_overlay.py"), "maker": str(root / "make_inputs.py"), "auditor": str(root / "audit_inputs.py"), "test": str(root / "test_provenance.py")}}


def publication_for(context):
    result = {}
    for name, stem in (("QUALIFICATION_R1.json", "qualification"), ("SOURCE_COPIES_R1.json", "sourceCopies"), ("OBSERVATIONS_R1.json", "observations"), ("SEMANTIC_CONTROLS_R1.json", "semanticControls")):
        result[stem + "Path"] = str(Path(context["publicationRoot"]) / name)
        result[stem + "Sha256"] = PUBLICATION[name][1]
    return result


def top_paths(context, inventory_path):
    root = Path(context["p9Root"])
    paths = [root / name for name in DIRECT]
    paths += [Path(inventory_path), Path(context["p8PinsPath"])]
    paths += [Path(context["tools"][key]) for key in ("pythonExe", "javaExe", "javacExe", "powershellExe")]
    paths += [Path(context["publicationRoot"]) / name for name in PUBLICATION]
    need(len(paths) == len({normpath(p) for p in paths}) == 27, "P9_INPUT_TOP_CENSUS")
    return sorted((str(p) for p in paths), key=str.lower)


def parse_inventory_lines(raw):
    need(type(raw) is bytes and len(raw) <= MAX_INVENTORY and raw.endswith(b"\n") and b"\r" not in raw and not raw.startswith(b"\xef\xbb\xbf"), "P9_INPUT_INVENTORY_ARITHMETIC")
    lines = raw.splitlines(keepends=True)
    need(all(1 < len(line) <= MAX_LINE and line.endswith(b"\n") for line in lines), "P9_INPUT_INVENTORY_ARITHMETIC")
    return [parse_json(line, canonical=True) for line in lines]


def inventory_bytes(roots, rows):
    rows = sorted(rows, key=lambda r: (r["origin"], r["path"]))
    counts = {"existing": 0, "direct": 0, "derived": 0, "total": len(rows)}
    sizes = {"existing": 0, "direct": 0, "derived": 0, "total": 0}
    for row in rows:
        category = row["kind"] if row["kind"] in ("direct", "derived") else "existing"
        counts[category] += 1
        sizes[category] += row["bytes"]
        sizes["total"] += row["bytes"]
    header = {"kind": "header", "schema": SOURCE_SCHEMA, "sourceParent": PARENT, "roots": roots, "rowCounts": counts, "byteCounts": sizes}
    footer = {"kind": "footer", "rowCounts": counts, "byteCounts": sizes}
    return b"".join(json_bytes(row) for row in (header, *rows, footer))


def _ref(row, path, raw, code):
    exact_obj(row, FILE_KEYS, code)
    integer(row["bytes"], code)
    digest(row["sha256"], code)
    need(row == {"path": path, "bytes": len(raw), "sha256": sha(raw)}, code)


def validate_overlay_proof(root, reads, context):
    source_path = Path(context["roots"]["CANARY"]) / "overlays-r1/PcbNetRouter.java"
    source = reads.read(source_path, expected={"bytes": 142148, "sha256": ROUTER_SHA})
    output = reads.read(root / "overlays-r1/PcbNetRouter.java")
    proof_raw = reads.read(root / "overlays-r1/overlay-manifest.json", MAX_INVENTORY)
    proof = parse_json(proof_raw, canonical=True)
    need(type(proof) is dict and type(proof.get("outputs")) is list and len(proof["outputs"]) == 1 and type(proof["outputs"][0]) is dict and proof["outputs"][0].get("path") == "P9/overlays-r1/PcbNetRouter.java", "P9_INPUT_DERIVED_CENSUS")
    exact_obj(proof, {"schema", "sourceParent", "builder", "contractSet", "input", "outputs", "edits", "preserved", "claims"}, "P9_OVERLAY_EDIT_SET")
    need(proof["schema"] == "tsj-q100-reservation-overlay-v1" and proof["sourceParent"] == PARENT, "P9_OVERLAY_EDIT_SET")
    _ref(proof["builder"], "P9/build_overlay.py", reads.read(root / "build_overlay.py"), "P9_OVERLAY_EDIT_SET")
    _ref(proof["contractSet"], "P9/CONTRACT_SET_R1.json", reads.read(root / "CONTRACT_SET_R1.json"), "P9_OVERLAY_EDIT_SET")
    _ref(proof["input"], "CANARY/overlays-r1/PcbNetRouter.java", source, "P9_OVERLAY_EDIT_SET")
    _ref(proof["outputs"][0], "P9/overlays-r1/PcbNetRouter.java", output, "P9_OVERLAY_EDIT_SET")
    expected_edits = []
    preserved = []
    pieces = []
    cursor = 0
    output_cursor = 0
    for name, offset, remove, insert in EDITS:
        if offset > cursor:
            part = source[cursor:offset]
            preserved.append({"inputOffset": cursor, "outputOffset": output_cursor, "bytes": len(part), "sha256": sha(part)})
            pieces.append(part)
            output_cursor += len(part)
        expected_edits.append({"id": name, "sourceOffset": offset, "removeBytes": remove, "removedSha256": sha(source[offset:offset + remove]), "insertBytes": len(insert), "insertSha256": sha(insert)})
        pieces.append(insert)
        output_cursor += len(insert)
        cursor = offset + remove
    if cursor < len(source):
        part = source[cursor:]
        preserved.append({"inputOffset": cursor, "outputOffset": output_cursor, "bytes": len(part), "sha256": sha(part)})
        pieces.append(part)
    need(json_bytes(proof["edits"]) == json_bytes(expected_edits), "P9_OVERLAY_EDIT_SET")
    need(json_bytes(proof["preserved"]) == json_bytes(preserved) and output == b"".join(pieces), "P9_OVERLAY_PRESERVATION")
    need(json_bytes(proof["claims"]) == json_bytes({key: True for key in ("observerOnly", "predicatesUnchanged", "cacheWritesUnchanged", "copyAndConstructorUnchanged", "p8ForwardingOnce")}), "P9_OVERLAY_PRESERVATION")
    return proof


def _bridge_directory_path(value):
    """Validate destination names after owned scratch removal; admits no file bytes."""
    code = "P9_INPUT_COMPILE_SET"
    need(isinstance(value, (str, Path)) and str(value) and str(value).isascii(), code)
    path = Path(value)
    need(path.is_absolute() and re.match(r"^[A-Za-z]:[\\/]", str(value)) is not None and ":" not in str(value)[2:] and ".." not in path.parts and str(value) == str(path), code)
    need(all(not part.endswith((".", " ")) for part in path.parts), code)
    try:
        result = path.resolve(strict=False)
        need(str(value) == str(result), code)
        for part in (result, *result.parents):
            try:
                info = part.lstat()
            except FileNotFoundError:
                continue
            need(stat.S_ISDIR(info.st_mode) and not stat.S_ISLNK(info.st_mode) and not (getattr(info, "st_file_attributes", 0) & 0x400), code)
    except OSError:
        raise Reject(code) from None
    return result


def expected_compiler_sources(context, bridge_directory=None):
    repo = Path(context["repositoryRoot"])
    root = Path(context["p9Root"])
    canary = context["canaryPins"]
    paths = [str(repo / row["path"]) for row in context["p8SourceRows"] if row["role"] == "BASE_SOURCE" and row["path"].endswith(".java") and row["path"] not in EXCLUDED]
    need(set(canary["modeFiles"]) == set(MODE_ROLES), "P9_INPUT_COMPILE_SET")
    for role in MODE_ROLES:
        paths.append(str(root / "overlays-r1/PcbNetRouter.java") if role == "netRouterOverlay" else canary["modeFiles"][role])
    for bridge in context["bridges"]:
        if bridge_directory is None:
            paths.append(bridge["sourcePath"])
        else:
            directory = _bridge_directory_path(bridge_directory)
            paths.append(str(directory / bridge["name"]))
    paths += [context["p8Pins"]["roles"]["native"][key] for key in ("study", "inspector")]
    paths += [str(root / name) for name, role in DIRECT.items() if role == "NATIVE_SOURCE"]
    need(len(paths) == len({normpath(p) for p in paths}) == 1085, "P9_INPUT_COMPILE_SET")
    return sorted(paths)


def java_top_types(raw):
    """Lexical FQN census, excluding comments/literals and nested classes."""
    try:
        text = raw.decode("utf-8")
    except UnicodeError:
        raise Reject("P9_INPUT_COMPILE_SET") from None
    text = re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m[1], 16)), text)
    text = re.sub(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'', ' ', text, flags=re.S)
    packages = re.findall(r"\bpackage\s+([\w.]+)\s*;", text)
    need(len(packages) <= 1, "P9_INPUT_COMPILE_SET")
    prefix = packages[0] + "." if packages else ""
    depth = 0
    result = []
    for token in re.finditer(r"[{}]|\b(?:class|interface|enum)\s+([A-Za-z_$][\w$]*)", text):
        if token[0] == "{":
            depth += 1
        elif token[0] == "}":
            depth -= 1
            need(depth >= 0, "P9_INPUT_COMPILE_SET")
        elif depth == 0:
            result.append(prefix + token[1])
    need(depth == 0, "P9_INPUT_COMPILE_SET")
    return result


def check_new_java(root, reads):
    for name in ("CanonicalReservationProvenanceStudy", "CanonicalReservationProvenanceCanary"):
        raw = reads.read(root / ("source/" + name + ".java"))
        need(java_top_types(raw) == ["com.lushprojects.circuitjs1.client." + name], "P9_INPUT_COMPILE_SET")


def _prepare(context, root, reads):
    context = dict(context)
    root = p9_root(root, context["roots"])
    context.update({"p9Root": str(root), "auditPath": str(root / "audit_inputs.py")})
    context["roots"] = {**context["roots"], "P9": str(root)}
    contract_inputs(root, reads)
    files_data = {}
    new_rows = []
    for kind, entries in (("direct", DIRECT), ("derived", DERIVED)):
        for relative_path, role in entries.items():
            path = root / relative_path
            raw = reads.read(path)
            files_data[relative_path] = raw
            row = {"kind": kind, "path": relative_path, "bytes": len(raw), "sha256": sha(raw), "role": role, "origin": "P9"}
            need(len(json_bytes(row)) <= 512, "P9_INPUT_INVENTORY_ARITHMETIC")
            new_rows.append(row)
    validate_overlay_proof(root, reads, context)
    check_new_java(root, reads)
    bridge_raw = reads.read(context["canaryPins"]["bridgeManifest"], MAX_LINE)
    bridge_rows = parse_json(bridge_raw)
    need(type(bridge_rows) is list and len(bridge_rows) == 3 and {r["path"] for r in bridge_rows} == set(BRIDGES), "P9_INPUT_COMPILE_SET")
    context["bridges"] = []
    for row in bridge_rows:
        exact_obj(row, FILE_KEYS, "P9_INPUT_COMPILE_SET")
        source_path = Path(context["roots"]["BRIDGE"]) / ("bridge-" + row["path"])
        existing = context["existing"].get(normpath(source_path))
        need(existing is not None and (existing["bytes"], existing["sha256"]) == (row["bytes"], row["sha256"]), "P9_INPUT_COMPILE_SET")
        context["bridges"].append({"name": row["path"], "sourcePath": str(source_path), "bytes": row["bytes"], "sha256": row["sha256"]})
    context["filesData"] = files_data
    context["sourceRows"] = sorted([*context["existing"].values(), *new_rows], key=lambda r: (r["origin"], r["path"]))
    context["rows"] = {normpath(Path(context["roots"][r["origin"]]) / r["path"]): {**r, "path": str(Path(context["roots"][r["origin"]]) / r["path"])} for r in context["sourceRows"]}
    need(len(context["rows"]) == 3456, "P9_INPUT_DEPENDENCY_CLOSURE")
    context["compilerSources"] = expected_compiler_sources(context)
    wire = [row for row in context["p8SourceRows"] if row["role"] == "WIRE"]
    need(len(wire) == 1, "P9_INPUT_DEPENDENCY_CLOSURE")
    context["p8WireSha"] = wire[0]["sha256"]
    return context


def _attest_tools(context, reads, top_rows=None):
    tools = context["tools"]
    image = actual_image()
    need(normpath(image) == normpath(tools["pythonExe"]), "P9_INPUT_TOOL_IDENTITY")
    for key, expected_sha in (("pythonExe", PYTHON_SHA), ("javaExe", JAVA_SHA), ("javacExe", JAVAC_SHA), ("powershellExe", PS_SHA)):
        path = canonical(tools[key], code="P9_INPUT_TOOL_IDENTITY")
        if top_rows is not None:
            row = top_rows.get(normpath(path))
            need(row is not None and row["sha256"] == expected_sha, "P9_INPUT_TOOL_IDENTITY")
        raw = reads.read(path, code="P9_INPUT_TOOL_IDENTITY")
        need(sha(raw) == expected_sha, "P9_INPUT_TOOL_IDENTITY")


def recheck_inputs(context):
    reads = context.get("_reads")
    need(isinstance(reads, Reads), "P9_INPUT_DEPENDENCY_CLOSURE")
    reads.recheck()
    return True


def validate_inputs(pins_path, pins_sha):
    """Authenticate actual P9 closure. Returns native Python objects, not JSON text."""
    need(sys.flags.isolated and sys.dont_write_bytecode, "P9_INPUT_TOOL_IDENTITY")
    digest(pins_sha, "P9_INPUT_PINS_HASH")
    reads = Reads()
    pins_path = canonical(pins_path, exact=True)
    pins_raw = reads.read(pins_path, MAX_PINS, code="P9_INPUT_PINS_HASH")
    need(sha(pins_raw) == pins_sha, "P9_INPUT_PINS_HASH")
    pins = parse_json(pins_raw, canonical=True)
    exact_obj(pins, PINS_KEYS, "P9_INPUT_ROOT_BINDING")
    need(pins["schema"] == INPUT_SCHEMA and pins["sourceParent"] == PARENT, "P9_INPUT_ROOT_BINDING")
    top = file_map(pins["files"], "P9_INPUT_TOP_CENSUS")
    self_path = canonical(Path(__file__))
    self_raw = reads.read(self_path)
    self_row = top.get(normpath(self_path))
    need(self_row is not None and self_row["sha256"] == sha(self_raw) and self_row["bytes"] == len(self_raw), "P9_INPUT_SELF_PIN")
    prerequisite = exact_obj(pins["p8Prerequisite"], {"pinsPath", "pinsSha256", "sourceInventorySha256"}, "P9_INPUT_DEPENDENCY_CLOSURE")
    need(prerequisite["pinsSha256"] == SOURCE33_SHA and prerequisite["sourceInventorySha256"] == P8_INVENTORY_SHA, "P9_INPUT_DEPENDENCY_CLOSURE")
    context = load_existing(reads, prerequisite["pinsPath"], pins["repositoryRoot"])
    root = p9_root(self_path.parent, context["roots"])
    expected_roots = {**context["roots"], "P9": str(root)}
    exact_obj(pins["sourceInventory"], {"path", "sha256"}, "P9_INPUT_ROOT_BINDING")
    inventory_path = canonical(pins["sourceInventory"]["path"], exact=True)
    old_roots = (*expected_roots.values(), context["qRoot"])
    need(inventory_path.name == "native-source-inventory.jsonl" and pins_path.name == "input-provenance-pins.json" and inventory_path.parent == pins_path.parent and not any(below(inventory_path, old) for old in old_roots), "P9_INPUT_ROOT_BINDING")
    parent = inventory_path.parent
    need(below(parent, os_temp()) and all(not below(parent, old) and not below(old, parent) for old in old_roots), "P9_INPUT_ROOT_BINDING")
    context["p9Root"] = str(root)
    need(set(top) == {normpath(p) for p in top_paths(context, inventory_path)} and len(top) == 27, "P9_INPUT_TOP_CENSUS")
    need([normpath(r["path"]) for r in pins["files"]] == sorted(top), "P9_INPUT_TOP_CENSUS")
    need(pins["roles"] == roles_for(root, context["repositoryRoot"]) and pins["toolPaths"] == context["p8Pins"]["toolPaths"], "P9_INPUT_ROOT_BINDING")
    need(pins["p8Prerequisite"] == {"pinsPath": context["p8PinsPath"], "pinsSha256": SOURCE33_SHA, "sourceInventorySha256": P8_INVENTORY_SHA} and pins["publishedP8"] == publication_for(context), "P9_INPUT_DEPENDENCY_CLOSURE")
    _attest_tools(context, reads, top)
    for row in top.values():
        reads.read(row["path"], expected=row)
    inventory_raw = reads.read(inventory_path, MAX_INVENTORY, top[normpath(inventory_path)])
    need(sha(inventory_raw) == pins["sourceInventory"]["sha256"], "P9_INPUT_FILE_HASH")
    parsed = parse_inventory_lines(inventory_raw)
    need(len(parsed) >= 2, "P9_INPUT_INVENTORY_ARITHMETIC")
    header, footer = parsed[0], parsed[-1]
    exact_obj(header, {"kind", "schema", "sourceParent", "roots", "rowCounts", "byteCounts"}, "P9_INPUT_ROOT_BINDING")
    need(header["kind"] == "header" and header["schema"] == SOURCE_SCHEMA and header["sourceParent"] == PARENT and header["roots"] == expected_roots, "P9_INPUT_ROOT_BINDING")
    context = _prepare(context, root, reads)
    actual_rows = parsed[1:-1]
    for row in actual_rows:
        exact_obj(row, ROW_KEYS, "P9_INPUT_DEPENDENCY_CLOSURE")
        relative(row["path"])
        integer(row["bytes"], "P9_INPUT_INVENTORY_ARITHMETIC")
        digest(row["sha256"], "P9_INPUT_DEPENDENCY_CLOSURE")
    actual_existing = [r for r in actual_rows if r["kind"] in ("source", "dependency")]
    need(actual_existing == [r for r in context["sourceRows"] if r["kind"] in ("source", "dependency")], "P9_INPUT_DEPENDENCY_CLOSURE")
    need([r for r in actual_rows if r["kind"] == "derived"] == [r for r in context["sourceRows"] if r["kind"] == "derived"], "P9_INPUT_DERIVED_CENSUS")
    need(actual_rows == context["sourceRows"], "P9_INPUT_FILE_HASH")
    need(inventory_raw == inventory_bytes(expected_roots, context["sourceRows"]), "P9_INPUT_INVENTORY_ARITHMETIC")
    compiler_keys = {normpath(p) for p in context["compilerSources"]}
    fqns = set()
    for row in context["rows"].values():
        body = reads.read(row["path"], expected=row)
        if normpath(row["path"]) in compiler_keys:
            for name in java_top_types(body):
                need(name not in fqns, "P9_INPUT_COMPILE_SET")
                fqns.add(name)
    need(reads.consumed[normpath(pins["roles"]["process"]["helper"])]["sha256"] == MODULE_SHA, "P9_INPUT_DEPENDENCY_CLOSURE")
    p8reader = Path(context["p8Pins"]["roles"]["process"]["reader"])
    p8readerraw = reads.read(p8reader, expected=context["p8Rows"][normpath(p8reader)])
    need(sha(p8readerraw) == P8_READER_SHA, "P9_INPUT_DEPENDENCY_CLOSURE")
    reads.recheck()
    summary = {"status": "PASS_Q100_RESERVATION_INPUTS_ONLY", "pinsSha256": pins_sha, "sourceInventorySha256": sha(inventory_raw), "existingFiles": 3437, "directFiles": 17, "derivedFiles": 2, "totalFiles": 3456, "productionFiles": 850, "topFiles": 27}
    context.update({"pins": pins, "pinsRaw": pins_raw, "pinsPath": str(pins_path), "inventoryPath": str(inventory_path), "sourceInventoryRaw": inventory_raw, "sourceSha": sha(inventory_raw), "p8ReaderPath": str(p8reader), "p8ReaderRaw": p8readerraw, "summary": summary, "consumed": list(reads.consumed.values()), "_reads": reads})
    return context


def main():
    try:
        need(len(sys.argv) == 3, "P9_INPUT_USAGE")
        result = validate_inputs(sys.argv[1], sys.argv[2])
        sys.stdout.buffer.write(b"PASS_Q100_RESERVATION_INPUTS_ONLY " + json_bytes(result["summary"]))
        sys.stdout.buffer.flush()
        return 0
    except Reject as error:
        code = error.code
    except Exception:
        code = "P9_INPUT_UNEXPECTED_FAILURE"
    sys.stderr.buffer.write(("P9_RESERVATION_INPUT_REJECT " + code + "\n").encode("ascii"))
    sys.stderr.buffer.flush()
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
