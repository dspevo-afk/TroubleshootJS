"""Derive the frozen native-only V2 observation/materialization wire overlay.

Only byte-pinned Q is a derivation input. The pinned public P generator and
exporter's checked I/O are reused; no old whole-study source is loaded.
"""
from __future__ import annotations

import hashlib
import json
import stat
import sys
import types
from pathlib import Path

STUDY_SHA256 = "d6485b0b0f9c518431d440fcb57e477143d50cae78302c29598d12aaa4601dbd"
BASE_GENERATOR_SHA256 = "d11df617dc255257e3ac42cba45a9a8d78482f4fec9ea52f846a6f48f97ce1f0"
IO_HELPER_SHA256 = "df9c297f4eb491117bade351ceedf26ccef5059e47ad9a7f36006000f1a10923"
PHYSICAL_OWNER_SHA256 = "7bf4ab57198ff9444381ec5d677c1a2bc309bfa3e7f01b188c631ffe5f2b966c"
WIRE_SHA256 = "bebf6775a66a8702862d421fd9fd180364b8c0fcb393b8e9f34b1369e052dc80"
MAX_SOURCE_BYTES = 4 * 1024 * 1024
MAX_BASE_BYTES = 256 * 1024
SCHEMA = "tsj-native-selected-observation-wire-overlay-v2"
CHANGES = (
    "SELECTED_WIRE_MAIN", "NULL_OBSERVATION_V2", "SHARED_MATERIALIZER",
    "EXISTING_CALLER_MATERIALIZER", "VALIDATE_V2_SCHEMA", "VALIDATE_HEAD_STATE",
    "DENY_AUTHORED_ASSEMBLE", "TERMINAL_RESULT_WRITER", "HEAD_RESULT_WRITER", "SEALED_OBSERVATION_V2",
    "WIRE_HEADER_SCHEMA", "WIRE_HEADER_STATUS", "DENY_OLD_R9_ASSEMBLER",
)


class OverlayError(ValueError):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


def need(condition: bool, code: str) -> None:
    if not condition:
        raise OverlayError(code)


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def replace_once(raw: bytes, before: bytes, after: bytes) -> bytes:
    need(type(raw) is bytes and type(before) is bytes and type(after) is bytes, "SPLICE_TYPES")
    need(bool(before) and raw.count(before) == 1, "SPLICE_COUNT")
    return raw.replace(before, after, 1)


def replace_region(raw: bytes, start: bytes, end: bytes, replacement: bytes, code: str) -> bytes:
    need(type(raw) is bytes and type(start) is bytes and type(end) is bytes and
         type(replacement) is bytes and bool(start) and bool(end), "SPLICE_TYPES")
    need(raw.count(start) == 1, code + "_START")
    need(raw.count(end) == 1, code + "_END_COUNT")
    begin = raw.index(start)
    finish = raw.find(end, begin + len(start))
    need(finish >= 0, code + "_END")
    return replace_once(raw, raw[begin:finish], replacement)


MAIN = b'''    public static void main(String[] args) throws Exception {
        long started=System.nanoTime();
        if (args==null || args.length!=4 ||
                !"SELECTED_OBSERVATION_CANARIES_V2".equals(args[0]) ||
                args[1]==null || !UUID.matcher(args[1]).matches() ||
                args[2]==null || !SHA.matcher(args[2]).matches() ||
                args[3]==null || !SHA.matcher(args[3]).matches())
            throw new IllegalStateException("SELECTED_WIRE_CANARY_ONLY");
        SelectedObservationWireCanary.run(args,started);
    }

'''

NULL_OBSERVATION = b'''    static Map<String,Object> nullObservation(SetupMeter m) throws Exception {
        m.copy(18);
        Map<String,Object> out=new java.util.LinkedHashMap<String,Object>();
        out.put("schema","tsj-cohort-terminal-observation-v2");
        out.put("mode","NULL"); out.put("state","NOT_ENABLED");
        out.put("callbackCount",Integer.valueOf(0));
        out.put("foundCallbackCount",Integer.valueOf(0));
        out.put("nonFoundCallbackCount",Integer.valueOf(0));
        out.put("firstNetOrdinal",null); out.put("lastNetOrdinal",null);
        out.put("lastCallbackTotalExpanded",null); out.put("selectedCallbackOrdinal",null);
        out.put("transientTupleCopies",Integer.valueOf(0));
        out.put("transientFieldCopies",Integer.valueOf(0));
        out.put("validationCount",Integer.valueOf(0));
        out.put("observerUnits",Long.valueOf(0));
        out.put("copiedEntries",Long.valueOf(0)); out.put("copyChunks",Integer.valueOf(0));
        out.put("selectedSnapshot",null); out.put("selectedHead",null);
        return out;
    }

'''

MATERIALIZER = b'''    /** Shared metadata owner; callers seal the observer before materialization. */
    static Map<String,Object> materializeObservation(TerminalSink sink,SetupMeter m)
            throws Exception {
        if (sink==null) return nullObservation(m);
        m.copy(18);
        m.charge(1); // selected-reference inspection, outside observer units
        if (field(sink.observer,"selected")!=null) m.copy(33);
        m.charge(1); // head-reference inspection, outside observer units
        if (field(sink.observer,"selectedHead")!=null) m.copy(10);
        m.charge(1); // sealed result-map operation, outside observer units
        Map<String,Object> observerResult=sink.observer.resultMap();
        m.copy(18);
        m.charge(1); // mutable outer-map construction, outside observer units
        return new java.util.LinkedHashMap<String,Object>(observerResult);
    }

'''

OLD_MATERIALIZATION = b'''    Map<String,Object> observation;
    if (sink==null) observation=nullObservation(m);
    else {
        m.copy(17);
        if (field(sink.observer,"selected")!=null) m.copy(33);
        Map<String,Object> observerResult=sink.observer.resultMap();
        m.copy(17);
        observation=new java.util.LinkedHashMap<String,Object>(observerResult);
    }
'''

V2_SCHEMA_GUARD = b'''        if (test(m,new Guard() { public boolean evaluate() throws Exception {
                return obs==null || obs.size()!=18 ||
                    !"tsj-cohort-terminal-observation-v2".equals(obs.get("schema")) ||
                    !obs.containsKey("selectedHead");
            } })) throw new IOException("OBSERVER_V2_SCHEMA");
'''

HEAD_STATE_GUARD = b'''        m.charge(1); // selected-head reference inspection
        final Object selectedHead=obs.get("selectedHead");
        if (test(m,new Guard() { public boolean evaluate() throws Exception {
                return selectedHead!=null && (!(selectedHead instanceof Map) ||
                    ((Map<?,?>)selectedHead).size()!=10);
            } }) || test(m,new Guard() { public boolean evaluate() throws Exception {
                return (selectedHead!=null)!=
                    ("FIRST_NON_FOUND".equals(state) && selected instanceof Map &&
                     "BRANCH_SEARCH_LIMIT".equals(((Map<?,?>)selected).get("terminalReason")));
            } })) throw new IOException("OBSERVER_SELECTED_HEAD_STATE");
'''

TERMINAL_WRITER = b'''    void writeResult(java.util.Map<String, Object> out, String p) {
        out.put(p+"pass",Integer.valueOf(pass));
        out.put(p+"netId",netId);
        out.put(p+"startPadId",startPadId);
        out.put(p+"rootPadId",rootPadId);
        out.put(p+"startX",Integer.valueOf(startX));
        out.put(p+"startY",Integer.valueOf(startY));
        out.put(p+"rootX",Integer.valueOf(rootX));
        out.put(p+"rootY",Integer.valueOf(rootY));
        out.put(p+"gridWidth",Integer.valueOf(gridWidth));
        out.put(p+"gridHeight",Integer.valueOf(gridHeight));
        out.put(p+"goalsTop",Integer.valueOf(goalsTop));
        out.put(p+"goalsBottom",Integer.valueOf(goalsBottom));
        out.put(p+"prefixViaCount",Integer.valueOf(prefixViaCount));
        out.put(p+"reachedPadCount",Integer.valueOf(reachedPadCount));
        out.put(p+"initialManhattanDistance",Integer.valueOf(initialManhattanDistance));
        out.put(p+"minimumExploredManhattanDistance",Integer.valueOf(minimumExploredManhattanDistance));
        out.put(p+"branchExpanded",Integer.valueOf(branchExpanded));
        out.put(p+"totalExpanded",Integer.valueOf(totalExpanded));
        out.put(p+"expandedTop",Integer.valueOf(expandedTop));
        out.put(p+"expandedBottom",Integer.valueOf(expandedBottom));
        out.put(p+"frontierCount",Integer.valueOf(frontierCount));
        out.put(p+"bestStateCount",Integer.valueOf(bestStateCount));
        out.put(p+"terminalReason",terminalReason);
        out.put(p+"relaxedBoundStarted",Boolean.valueOf(relaxedBoundStarted));
        out.put(p+"relaxedBoundComplete",Boolean.valueOf(relaxedBoundComplete));
        out.put(p+"relaxedBoundObservationChecked",Boolean.valueOf(relaxedBoundObservationChecked));
        out.put(p+"relaxedBoundLiveLayoutStable",Boolean.valueOf(relaxedBoundLiveLayoutStable));
        out.put(p+"relaxedBoundRouterStateStable",Boolean.valueOf(relaxedBoundRouterStateStable));
        out.put(p+"relaxedBoundRouterEffectsStable",Boolean.valueOf(relaxedBoundRouterEffectsStable));
        out.put(p+"relaxedBoundWorkUnits",Integer.valueOf(relaxedBoundWorkUnits));
        out.put(p+"relaxedBoundOrdinaryProbes",Integer.valueOf(relaxedBoundOrdinaryProbes));
        out.put(p+"relaxedBoundViaProbes",Integer.valueOf(relaxedBoundViaProbes));
        out.put(p+"relaxedBoundRefreshes",Integer.valueOf(relaxedBoundRefreshes));
    }
'''

HEAD_WRITER = b'''    void writeResult(java.util.Map<String,Object> out) {
        out.put("branchOrdinal",Integer.valueOf(branchOrdinal));
        out.put("cell",Integer.valueOf(cell));
        out.put("layer",Integer.valueOf(layer));
        out.put("direction",Integer.valueOf(direction));
        out.put("transitions",Integer.valueOf(transitions));
        out.put("cost",Integer.valueOf(cost));
        out.put("storedHeuristic",Integer.valueOf(storedHeuristic));
        out.put("serial",Integer.valueOf(serial));
        out.put("key",Integer.valueOf(key));
        out.put("storedPriority",Integer.valueOf(storedPriority));
    }
'''

RESULT_MAP = b'''    synchronized java.util.Map<String, Object> resultMap() {
        if (!sealed) throw new IllegalStateException("Seal observer before result access");
        if (poisoned) throw new IllegalStateException("Observer request had an active rejection");
        // headCopies is the sole H counter; tuple copies include both DTOs in V2.
        if (inCallback || dispatches<0 || dispatches>spec.maxCallbacks ||
                accepted!=dispatches || found<0 || failures<0 ||
                found+failures!=dispatches || failures>1 || headCopies<0 || headCopies>1 ||
                copiedSlots!=33L*dispatches+10L*headCopies ||
                copyChunks!=dispatches+headCopies || headReleases!=headCopies ||
                releases!=dispatches || selections!=dispatches || inspections!=copiedSlots ||
                checks<dispatches || checks>94L*dispatches+12L*headCopies ||
                unitsCharged!=69L*dispatches+checks+21L*headCopies ||
                unitsMeasured!=unitsCharged || groupSamples!=dispatches ||
                (failures==0 && selected!=null) || (failures==1 && selected==null) ||
                (headCopies==1)!=(selectedHead!=null) ||
                (selectedHead!=null)!=(selected!=null &&
                    "BRANCH_SEARCH_LIMIT".equals(selected.terminalReason)))
            throw new IllegalStateException("Observer counters or selection are incomplete");
        if ((failures==1 && selectedCallbackOrdinal!=dispatches) ||
                (failures==0 && selectedCallbackOrdinal!=0))
            throw new IllegalStateException("Selected failure is not the final callback");

        java.util.Map<String,Object> out=new java.util.LinkedHashMap<String,Object>();
        out.put("schema","tsj-cohort-terminal-observation-v2");
        out.put("mode","OBSERVE");
        out.put("state",failures==0?"NO_FAILURE":"FIRST_NON_FOUND");
        out.put("callbackCount",Integer.valueOf((int)dispatches));
        out.put("foundCallbackCount",Integer.valueOf((int)found));
        out.put("nonFoundCallbackCount",Integer.valueOf((int)failures));
        out.put("firstNetOrdinal",accepted==0?null:Integer.valueOf(firstOrdinal));
        out.put("lastNetOrdinal",accepted==0?null:Integer.valueOf(lastOrdinal));
        out.put("lastCallbackTotalExpanded",accepted==0?null:Integer.valueOf(lastTotal));
        out.put("selectedCallbackOrdinal",failures==0?null:Integer.valueOf(selectedCallbackOrdinal));
        out.put("transientTupleCopies",Integer.valueOf((int)copyChunks));
        out.put("transientFieldCopies",Integer.valueOf((int)copiedSlots));
        out.put("validationCount",Integer.valueOf((int)checks));
        out.put("observerUnits",Integer.valueOf((int)unitsCharged));
        out.put("copiedEntries",Integer.valueOf((int)copiedSlots));
        out.put("copyChunks",Integer.valueOf((int)copyChunks));
        java.util.Map<String,Object> snapshot=null;
        if (selected!=null) {
            snapshot=new java.util.LinkedHashMap<String,Object>();
            selected.writeResult(snapshot,"");
            snapshot=java.util.Collections.unmodifiableMap(snapshot);
        }
        out.put("selectedSnapshot",snapshot);
        java.util.Map<String,Object> head=null;
        if (selectedHead!=null) {
            head=new java.util.LinkedHashMap<String,Object>();
            selectedHead.writeResult(head);
            head=java.util.Collections.unmodifiableMap(head);
        }
        out.put("selectedHead",head);
        return java.util.Collections.unmodifiableMap(out);
    }

'''


def derive(source: bytes) -> tuple[bytes, tuple[str, ...]]:
    need(type(source) is bytes and 0 < len(source) <= MAX_SOURCE_BYTES, "SOURCE_SIZE")
    need(sha(source) == STUDY_SHA256, "STUDY_HASH")
    need(b"\r" not in source, "STUDY_LINE_ENDINGS")
    result = source
    changes = []

    def region(name: str, start: bytes, end: bytes, replacement: bytes) -> None:
        nonlocal result
        result = replace_region(result, start, end, replacement, name)
        changes.append(name)

    def splice(name: str, before: bytes, after: bytes) -> None:
        nonlocal result
        result = replace_once(result, before, after)
        changes.append(name)

    region("SELECTED_WIRE_MAIN", b"    public static void main(String[] args) {\n",
           b"    static final class Arguments {\n", MAIN)
    region("NULL_OBSERVATION_V2", b"    static Map<String,Object> nullObservation(SetupMeter m) throws Exception {\n",
           b"    /** Adapter for the observer's direct, non-reserving in-owner meter port. */\n",
           NULL_OBSERVATION)
    marker = b"    /** Adapter for the observer's direct, non-reserving in-owner meter port. */\n"
    splice("SHARED_MATERIALIZER", marker, MATERIALIZER + marker)
    splice("EXISTING_CALLER_MATERIALIZER", OLD_MATERIALIZATION,
           b"    Map<String,Object> observation=materializeObservation(sink,m);\n")
    marker = b"            final List<String> requested,SetupMeter m) throws Exception {\n"
    splice("VALIDATE_V2_SCHEMA", marker, marker + V2_SCHEMA_GUARD)
    marker = b'        final Object selected=obs.get("selectedSnapshot");\n'
    splice("VALIDATE_HEAD_STATE", marker, marker + HEAD_STATE_GUARD)
    region("DENY_AUTHORED_ASSEMBLE", b"    static Comparison assemble(Arguments args,long started) throws Exception {\n",
           b"    /** Structural minimum and separately metered logical traversal work. */\n",
           b'''    static Comparison assemble(Arguments args,long started) throws Exception {
        throw new IllegalStateException("SELECTED_WIRE_CANARY_ONLY");
    }

''')
    region("TERMINAL_RESULT_WRITER", b"    void writeResult(java.util.Map<String, Object> out, String p) {\n",
           b"static final class DetachedHead {\n", TERMINAL_WRITER + b"}\n\n")
    marker = b'''        this.storedPriority=storedPriority;
    }
}

/** No owner references; callbackGroupSamples counts samples, not unique parent groups. */
'''
    splice("HEAD_RESULT_WRITER", marker,
           b"        this.storedPriority=storedPriority;\n    }\n" + HEAD_WRITER +
           b"}\n\n/** No owner references; callbackGroupSamples counts samples, not unique parent groups. */\n")
    # Q's unit body is inline. Do not assume a newline after its opening brace.
    region("SEALED_OBSERVATION_V2", b"    synchronized java.util.Map<String, Object> resultMap() {\n",
           b"    private void unit() {", RESULT_MAP)
    splice("WIRE_HEADER_SCHEMA",
           b'            r5Require("tsj-cohort-terminal-canary-v1".equals(value),"R9_CANARY_SCHEMA");\n',
           b'            r5Require("tsj-selected-observation-wire-canary-v2".equals(value),"R9_CANARY_SCHEMA");\n')
    splice("WIRE_HEADER_STATUS",
           b'            r5Require("OWNER_CANARIES_RECORDED_ONLY".equals(value),"R9_CANARY_STATUS");\n',
           b'            r5Require("GENERIC_OBSERVATIONS_RECORDED_ONLY".equals(value),"R9_CANARY_STATUS");\n')
    # This is the final member in pinned Q; retain the closing class brace.
    region("DENY_OLD_R9_ASSEMBLER", b"static void runR9OwnerCanaries(Arguments args,long started) throws Exception {\n",
           b"\n}\n\n}\n",
           b'''static void runR9OwnerCanaries(Arguments args,long started) throws Exception {
    throw new IllegalStateException("SELECTED_WIRE_CANARY_ONLY");''')
    need(tuple(changes) == CHANGES and len(result) <= MAX_SOURCE_BYTES, "DERIVED_SOURCE_SIZE")
    return result, tuple(changes)


def load_base_generator(path_value: str) -> types.ModuleType:
    """Small bootstrap for the same byte-pinned public checked-I/O owner."""
    need(type(path_value) is str and path_value and "\0" not in path_value, "BASE_GENERATOR_PATH")
    path = Path(path_value)
    need(path.is_absolute(), "BASE_GENERATOR_ABSOLUTE")
    try:
        for item in (path, *path.parents):
            info = item.lstat()
            if stat.S_ISLNK(info.st_mode) or (getattr(info, "st_file_attributes", 0) & 0x400):
                raise OverlayError("BASE_GENERATOR_REPARSE")
        need(path.resolve(strict=True) == path, "BASE_GENERATOR_CANONICAL")
        info = path.lstat()
        need(stat.S_ISREG(info.st_mode) and 0 < info.st_size <= MAX_BASE_BYTES, "BASE_GENERATOR_FILE")
        with path.open("rb") as stream:
            raw = stream.read(MAX_BASE_BYTES + 1)
    except OverlayError:
        raise
    except (OSError, RuntimeError):
        raise OverlayError("BASE_GENERATOR_READ") from None
    need(0 < len(raw) <= MAX_BASE_BYTES and sha(raw) == BASE_GENERATOR_SHA256, "BASE_GENERATOR_HASH")
    module = types.ModuleType("_pinned_selected_wire_base")
    module.__file__ = "<pinned-selected-wire-base>"
    try:
        exec(compile(raw, module.__file__, "exec"), module.__dict__)
    except Exception:
        raise OverlayError("BASE_GENERATOR_LOAD") from None
    return module


def parse_args(argv) -> dict[str, str]:
    keys = {"--study", "--base-generator", "--io-helper", "--output-dir"}
    need(type(argv) in (list, tuple) and len(argv) == 8, "CLI_ARGUMENTS")
    values = {}
    for at in range(0, 8, 2):
        key, value = argv[at:at + 2]
        need(type(key) is str and key in keys and key not in values and
             type(value) is str and value and "\0" not in value and not value.startswith("--"),
             "CLI_ARGUMENTS")
        values[key] = value
    need(set(values) == keys, "CLI_ARGUMENTS")
    return values


def generate(options: dict[str, str]) -> dict[str, object]:
    need(type(options) is dict and set(options) == {
        "--study", "--base-generator", "--io-helper", "--output-dir"
    }, "GENERATOR_OPTIONS")
    need(all(type(value) is str and value and "\0" not in value for value in options.values()),
         "GENERATOR_OPTIONS")
    # Reject lexical aliases before Path discards redundant separators or dot
    # components. The pinned owners still check identity, reparse and real path.
    for option, code in (("--study", "OWNER_SOURCE"), ("--base-generator", "BASE_GENERATOR"),
                         ("--io-helper", "IO_HELPER"), ("--output-dir", "OUTPUT_DIRECTORY")):
        need(str(Path(options[option])) == options[option], code + "_CANONICAL")
    base = load_base_generator(options["--base-generator"])
    need(base.IO_HELPER_SHA256 == IO_HELPER_SHA256, "IO_HELPER_PIN")
    io = base.load_io(options["--io-helper"])
    output = io._checked_directory(options["--output-dir"], "OUTPUT_DIRECTORY")
    need(not io._directory_names(output, "OUTPUT_DIRECTORY"), "OUTPUT_NOT_EMPTY")
    original = io._read_regular(options["--study"], MAX_SOURCE_BYTES, "OWNER_SOURCE")
    derived, changes = derive(original)
    proof = {
        "schema": SCHEMA,
        "status": "GENERATED_NATIVE_ONLY_NOT_COMPILED",
        "studyParentSha256": sha(original), "studyParentBytes": len(original),
        "physicalOwnerSha256": PHYSICAL_OWNER_SHA256,
        "baseGeneratorSha256": BASE_GENERATOR_SHA256, "ioHelperSha256": IO_HELPER_SHA256,
        "wireContractSha256": WIRE_SHA256,
        "derivedSha256": sha(derived), "derivedBytes": len(derived),
        "changes": list(changes), "observationSchema": "tsj-cohort-terminal-observation-v2",
        "observationKeys": 18,
        "transientTupleCopies": "TERMINAL_AND_HEAD_DTOS",
        "materializer": "SHARED_18_PLUS_33S_PLUS_10H_PLUS_18_OBSERVE_OR_18_NULL",
        "observerCore": "QUALIFIED_Q_UNCHANGED", "productionOwner": "UNCHANGED",
        "budgets": "UNCHANGED", "authoredInvocations": 0,
    }
    proof_raw = (json.dumps(proof, sort_keys=True, indent=2) + "\n").encode("ascii")
    files = {"CohortTerminalTelemetryStudy.java": derived, "SOURCE_PROOF.json": proof_raw}
    for name, raw in files.items():
        output = io._checked_directory(output, "OUTPUT_DIRECTORY")
        io._write_exclusive(output / name, raw, MAX_SOURCE_BYTES, "OUTPUT_FILE")
    output = io._checked_directory(output, "OUTPUT_DIRECTORY")
    names = io._directory_names(output, "OUTPUT_DIRECTORY")
    need(len(names) == 2 and set(names) == set(files), "OUTPUT_CLOSURE")
    for name, raw in files.items():
        need(io._read_regular(output / name, MAX_SOURCE_BYTES, "OUTPUT_READBACK") == raw,
             "OUTPUT_READBACK_CHANGED")
    return proof


def main(argv=None) -> int:
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        sys.stderr.write("SELECTED_WIRE_OVERLAY_FAIL PYTHON_ISOLATION\n")
        return 2
    try:
        result = generate(parse_args(sys.argv[1:] if argv is None else argv))
    except Exception as failure:
        code = getattr(failure, "code", "INTERNAL_ERROR")
        if type(code) is not str or not code or any(c not in "ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789" for c in code):
            code = "INTERNAL_ERROR"
        sys.stderr.write("SELECTED_WIRE_OVERLAY_FAIL " + code + "\n")
        return 2
    sys.stdout.write(json.dumps({
        "schema": SCHEMA, "status": result["status"],
        "derivedSha256": result["derivedSha256"], "files": 2,
    }, separators=(",", ":")) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
