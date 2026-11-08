"""Derive the frozen native-only selected-head observer/Meter overlay."""
from __future__ import annotations

import hashlib
import json
import os
import stat
import sys
import types
from pathlib import Path

STUDY_SHA256 = "8f7c3775c1479d86798464f75fb107c4e5bd1e5bda6ab93f4e39b14beaf6b221"
BASE_GENERATOR_SHA256 = "d11df617dc255257e3ac42cba45a9a8d78482f4fec9ea52f846a6f48f97ce1f0"
MAX_SOURCE_BYTES = 4 * 1024 * 1024
MAX_BASE_BYTES = 256 * 1024
SCHEMA = "tsj-native-selection-consumer-overlay-v1"
CHANGES = (
    "DENY_MAIN",
    "DENY_RESULT_MAP",
    "SELECTED_MODE_SINK",
    "DETACHED_HEAD_DTO",
    "OBSERVER_HEAD_STATE",
    "OBSERVER_HEAD_COUNTERS",
    "FOUR_ARGUMENT_OBSERVER_CORE",
    "STALE_BOTH_ARGUMENTS",
    "HEAD_COPY_AND_VALIDATION",
    "HEAD_VALIDATION",
    "ATOMIC_HEAD_COMMIT",
    "CAPACITY_AND_COUNTER_BOUNDS",
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
    need(raw.count(start) == 1, code + "_START")
    need(raw.count(end) == 1, code + "_END_COUNT")
    begin = raw.index(start)
    finish = raw.find(end, begin + len(start))
    need(finish >= 0, code + "_END")
    original = raw[begin:finish]
    return replace_once(raw, original, replacement)

MAIN = b'''    public static void main(String[] args) {
        throw new IllegalStateException("SELECTION_CONSUMER_COMPONENT_ONLY");
    }

'''
SINK = b'''    static final class TerminalSink implements PcbLayerRoutingPrototype.BranchLimitSelectionDiagnostics {
        final ObserverMeter meter;
        final TerminalTelemetryObserver observer;
        final String requestId;
        TerminalSink(ObserverMeter meter,TerminalTelemetryObserver observer,String requestId) {
            this.meter=meter; this.observer=observer; this.requestId=requestId;
        }
        private void dispatch(PcbLayerRoutingPrototype.BranchSnapshot snapshot,
                PcbLayerRoutingPrototype.BranchLimitSelection selection) {
            if (meter.insideOwner) throw new IllegalStateException("OBSERVER_REENTRANT");
            meter.insideOwner=true;
            try {
                if (selection==null) observer.observe(requestId,snapshot);
                else observer.observeSelected(requestId,snapshot,selection);
            } finally { meter.insideOwner=false; }
        }
        public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
            dispatch(snapshot,null);
        }
        public void onBranchLimitSelected(PcbLayerRoutingPrototype.BranchSnapshot snapshot,
                PcbLayerRoutingPrototype.BranchLimitSelection selection) {
            dispatch(snapshot,selection);
        }
    }

'''
HEAD_DTO = b'''static final class DetachedHead {
    final int branchOrdinal,cell,layer,direction,transitions,cost,storedHeuristic,
        serial,key,storedPriority;
    DetachedHead(int branchOrdinal,int cell,int layer,int direction,int transitions,int cost,
            int storedHeuristic,int serial,int key,int storedPriority) {
        this.branchOrdinal=branchOrdinal; this.cell=cell; this.layer=layer;
        this.direction=direction; this.transitions=transitions; this.cost=cost;
        this.storedHeuristic=storedHeuristic; this.serial=serial; this.key=key;
        this.storedPriority=storedPriority;
    }
}

/** No owner references; callbackGroupSamples counts samples, not unique parent groups. */
'''
OBSERVER_CORE = b'''    synchronized void observe(String requestId, PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
        observe(requestId,snapshot,null,null);
    }

    synchronized void observe(String requestId, PcbLayerRoutingPrototype.BranchSnapshot snapshot,
            TerminalObserverMeter staleRejectionMeter) {
        observe(requestId,snapshot,null,staleRejectionMeter);
    }

    synchronized void observeSelected(String requestId,
            PcbLayerRoutingPrototype.BranchSnapshot snapshot,
            PcbLayerRoutingPrototype.BranchLimitSelection head) {
        observe(requestId,snapshot,head,null);
    }

    synchronized void observeSelected(String requestId,
            PcbLayerRoutingPrototype.BranchSnapshot snapshot,
            PcbLayerRoutingPrototype.BranchLimitSelection head,
            TerminalObserverMeter staleRejectionMeter) {
        observe(requestId,snapshot,head,staleRejectionMeter);
    }

    private synchronized void observe(String requestId, PcbLayerRoutingPrototype.BranchSnapshot snapshot,
            PcbLayerRoutingPrototype.BranchLimitSelection suppliedHead,
            TerminalObserverMeter staleRejectionMeter) {
        if (sealed) { rejectStale(snapshot,suppliedHead,staleRejectionMeter); return; }
        PcbLayerRoutingPrototype.BranchSnapshot borrowed=snapshot;
        PcbLayerRoutingPrototype.BranchLimitSelection borrowedHead=suppliedHead;
        DetachedTerminal tuple=null, oldSelected=selected;
        DetachedHead headTuple=null, oldSelectedHead=selectedHead;
        Throwable failure=null;
        boolean committed=false, entered=false;
        int requiredFields=FIELD_COUNT+(borrowedHead==null?0:10);
        int requiredChunks=borrowedHead==null?1:2;
        long groupBefore=-1, unitsBefore=-1, oldAccepted=accepted, oldFound=found, oldFailures=failures;
        int oldOrdinal=lastOrdinal, oldTotal=lastTotal;
        int oldFirstOrdinal=firstOrdinal, oldSelectedOrdinal=selectedCallbackOrdinal;
        try {
            if (!meter.isOpen()) throw new IllegalStateException("Observer meter is closed");
            unitsBefore=meter.units(); groupBefore=meter.groupUnits();
            unit(); dispatches++;
            checkOpen();
            validation(); if (sealed) throw new IllegalStateException("Observer is sealed");
            validation(); if (borrowed == null) throw new IllegalArgumentException("Null terminal snapshot");
            validation(); if (countersNearOverflow(requiredFields,requiredChunks))
                throw new IllegalStateException("Observer counter overflow");
            validation(); if (dispatches > spec.maxCallbacks) throw new IllegalStateException("Callback limit exceeded");
            validation();
            long group=groupBefore;
            if (unitsBefore < 0 || group < 0 || group > GROUP_CAPACITY-CALLBACK_CEILING-POST_CALL_BOUND)
                throw new IllegalStateException("Insufficient callback group capacity");
            validation(); if (FIELD_COUNT != 33 || requiredFields > 4096)
                throw new IllegalStateException("Invalid tuple chunk size");
            validation();
            long copies=meter.copiedEntries();
            if (copies < 0 || copies > COPY_CAPACITY-requiredFields)
                throw new IllegalStateException("Aggregate copy capacity exceeded");
            validation();
            long chunks=meter.copyChunks();
            if (chunks < 0 || chunks > Integer.MAX_VALUE-requiredChunks ||
                    copyChunks < 0 || copyChunks > Long.MAX_VALUE-requiredChunks)
                throw new IllegalStateException("Copy chunk counter overflow");
            validation(); if (copiedSlots < 0 || copiedSlots > Long.MAX_VALUE-requiredFields)
                throw new IllegalStateException("Copied slot counter overflow");
            validation(); if (inCallback) throw new IllegalStateException("Nested callback");
            inCallback=true; entered=true;
            copyChunks++; meter.beginCopyChunk(FIELD_COUNT);
            tuple=detach(borrowed);
            if (borrowedHead!=null) {
                copyChunks++; meter.beginCopyChunk(10);
                headTuple=detachHead(borrowedHead);
                headCopies++;
            }
            int ordinal=validateTuple(tuple,requestId);
            validateSelection(tuple,headTuple);
            commit(tuple,ordinal,headTuple); committed=true;
        } catch (Throwable t) {
            failure=t;
        } finally {
            if (borrowed != null) {
                try { releases++; unit(); } catch (Throwable t) { failure=append(failure,t); }
                borrowed=null;
            }
            if (borrowedHead != null) {
                try { headReleases++; unit(); } catch (Throwable t) { failure=append(failure,t); }
                borrowedHead=null;
            }
            tuple=null; headTuple=null;
            if (entered) inCallback=false;
            if (unitsBefore >= 0 && groupBefore >= 0) {
                try {
                    long du=meter.units()-unitsBefore, dg=meter.groupUnits()-groupBefore;
                    if (du < 0 || dg < 0) throw new IllegalStateException("Meter moved backwards");
                    groupSamples++; unitsMeasured+=du;
                    if (du > maxCallbackUnits) maxCallbackUnits=du;
                    if (dg > maxGroupUnits) maxGroupUnits=dg;
                    lastGroupBefore=groupBefore; lastGroupAfter=groupBefore+dg;
                } catch (Throwable t) { failure=append(failure,t); }
            }
            if (failure != null) { poisoned=true; sealed=true; }
            if (failure != null && committed) {
                accepted=oldAccepted; found=oldFound; failures=oldFailures;
                firstOrdinal=oldFirstOrdinal; selectedCallbackOrdinal=oldSelectedOrdinal;
                lastOrdinal=oldOrdinal; lastTotal=oldTotal;
                selected=oldSelected; selectedHead=oldSelectedHead;
            }
        }
        if (failure != null) throwComponent(failure);
    }

'''
REJECT_STALE = b'''    private void rejectStale(PcbLayerRoutingPrototype.BranchSnapshot snapshot,
            PcbLayerRoutingPrototype.BranchLimitSelection head,
            TerminalObserverMeter rejectionMeter) {
        PcbLayerRoutingPrototype.BranchSnapshot borrowed=snapshot;
        PcbLayerRoutingPrototype.BranchLimitSelection borrowedHead=head;
        Throwable primary=new IllegalStateException("Sealed observer rejects stale callback");
        try {
            if (rejectionMeter == null || !rejectionMeter.isOpen())
                throw new IllegalStateException("Stale callback requires separate rejection meter");
            rejectionMeter.charge(1);
        } catch (Throwable t) { primary=append(primary,t); }
        finally {
            if (borrowed != null) {
                try {
                    if (rejectionMeter == null || !rejectionMeter.isOpen())
                        throw new IllegalStateException("Cannot meter stale snapshot release");
                    rejectionMeter.charge(1);
                } catch (Throwable t) { primary=append(primary,t); }
                borrowed=null;
            }
            if (borrowedHead != null) {
                try {
                    if (rejectionMeter == null || !rejectionMeter.isOpen())
                        throw new IllegalStateException("Cannot meter stale head release");
                    rejectionMeter.charge(1);
                } catch (Throwable t) { primary=append(primary,t); }
                borrowedHead=null;
            }
        }
        throwComponent(primary);
    }

'''
RESULT_DENIAL = b'''    synchronized java.util.Map<String, Object> resultMap() {
        throw new IllegalStateException("SELECTION_CONSUMER_NO_WIRE");
    }

'''
DETACH_HEAD = b'''    private DetachedTerminal detach(PcbLayerRoutingPrototype.BranchSnapshot s) {
        return new DetachedTerminal(readInt(s,0), readString(s,1), readString(s,2), readString(s,3),
            readInt(s,4), readInt(s,5), readInt(s,6), readInt(s,7), readInt(s,8), readInt(s,9),
            readInt(s,10), readInt(s,11), readInt(s,12), readInt(s,13), readInt(s,14), readInt(s,15),
            readInt(s,16), readInt(s,17), readInt(s,18), readInt(s,19), readInt(s,20), readInt(s,21),
            readString(s,22), readBoolean(s,23), readBoolean(s,24), readBoolean(s,25),
            readBoolean(s,26), readBoolean(s,27), readBoolean(s,28), readInt(s,29), readInt(s,30),
            readInt(s,31), readInt(s,32));
    }

    private int readHead(PcbLayerRoutingPrototype.BranchLimitSelection s,int f) {
        chargeReadCopy();
        switch (f) {
        case 0: return s.branchOrdinal;
        case 1: return s.cell;
        case 2: return s.layer;
        case 3: return s.direction;
        case 4: return s.transitions;
        case 5: return s.cost;
        case 6: return s.storedHeuristic;
        case 7: return s.serial;
        case 8: return s.key;
        case 9: return s.storedPriority;
        default: throw new IllegalArgumentException("Invalid selected-head field");
        }
    }

    private DetachedHead detachHead(PcbLayerRoutingPrototype.BranchLimitSelection s) {
        return new DetachedHead(readHead(s,0),readHead(s,1),readHead(s,2),readHead(s,3),
            readHead(s,4),readHead(s,5),readHead(s,6),readHead(s,7),readHead(s,8),readHead(s,9));
    }

'''
VALIDATE_SELECTION = b'''    private void validateSelection(DetachedTerminal t,DetachedHead h) {
        validation(); boolean branchLimit="BRANCH_SEARCH_LIMIT".equals(t.terminalReason);
        validation(); if (branchLimit!=(h!=null)) throw invalid("selected head presence mismatch");
        if (h==null) return;
        validation(); if (h.branchOrdinal<1) throw invalid("selected branch ordinal below range");
        int cells=t.gridWidth*t.gridHeight;
        validation(); if (h.cell<0 || h.cell>=cells) throw invalid("selected cell outside grid");
        validation(); if (h.layer<0 || h.layer>1) throw invalid("selected layer outside range");
        validation(); if (h.direction<0 || h.direction>4) throw invalid("selected direction outside range");
        validation(); if (h.transitions<0 || h.transitions>48-t.prefixViaCount)
            throw invalid("selected transitions outside range");
        validation(); if (h.cost<0) throw invalid("selected cost below range");
        validation(); if (h.storedHeuristic<0) throw invalid("selected heuristic below range");
        validation(); if (h.serial<0) throw invalid("selected serial below range");
        validation(); if (h.key!=(((h.transitions*2+h.layer)*5+h.direction)*cells)+h.cell)
            throw invalid("selected key mismatch");
        validation(); if (h.storedPriority!=h.cost+h.storedHeuristic)
            throw invalid("selected stored priority mismatch");
        validation(); if (t.branchExpanded!=100000 || t.totalExpanded>=1000000)
            throw invalid("selected branch cap counters mismatch");
        validation(); if (t.bestStateCount<1) throw invalid("selected best-state count below range");
    }

'''
COMMIT = b'''    private void commit(DetachedTerminal tuple,int ordinal,DetachedHead head) {
        unit(); selections++;
        if (accepted == 0) firstOrdinal=ordinal;
        accepted++; lastOrdinal=ordinal; lastTotal=tuple.totalExpanded;
        if ("FOUND".equals(tuple.terminalReason)) found++;
        else {
            failures++; selected=tuple; selectedHead=head;
            selectedCallbackOrdinal=(int)dispatches;
        }
    }

'''
COUNTERS = b'''    private boolean countersNearOverflow(int requiredFields,int requiredChunks) {
        return dispatches > Long.MAX_VALUE-512 || inspections > Long.MAX_VALUE-512 ||
            copiedSlots > Long.MAX_VALUE-requiredFields ||
            copyChunks > Long.MAX_VALUE-requiredChunks ||
            checks > Long.MAX_VALUE-512 || selections > Long.MAX_VALUE-512 ||
            releases > Long.MAX_VALUE-512 || headCopies > Long.MAX_VALUE-512 ||
            headReleases > Long.MAX_VALUE-512 || accepted > Long.MAX_VALUE-512 ||
            found > Long.MAX_VALUE-512 || failures > Long.MAX_VALUE-512 ||
            unitsCharged > Long.MAX_VALUE-512;
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

    region(
        "DENY_MAIN",
        b"    public static void main(String[] args) {\n",
        b"    static final class Arguments {\n",
        MAIN,
    )
    region(
        "DENY_RESULT_MAP",
        b"    synchronized java.util.Map<String, Object> resultMap() {\n",
        b"    private void unit() {",
        RESULT_DENIAL,
    )
    region(
        "SELECTED_MODE_SINK",
        b"    static final class TerminalSink implements PcbLayerRoutingPrototype.TerminalOnlyDiagnostics {\n",
        b"/* Replacement sink factory members",
        SINK,
    )
    splice(
        "DETACHED_HEAD_DTO",
        b"/** No owner references; callbackGroupSamples counts samples, not unique parent groups. */\n",
        HEAD_DTO,
    )
    splice(
        "OBSERVER_HEAD_STATE",
        b"    private DetachedTerminal selected;\n",
        b"    private DetachedTerminal selected;\n    private DetachedHead selectedHead;\n",
    )
    splice(
        "OBSERVER_HEAD_COUNTERS",
        b"    private long dispatches, inspections, copiedSlots, copyChunks, checks, selections, releases;\n",
        b"    private long dispatches, inspections, copiedSlots, copyChunks, checks, selections, releases;\n"
        b"    private long headCopies, headReleases;\n",
    )
    region(
        "FOUR_ARGUMENT_OBSERVER_CORE",
        b"    synchronized void observe(String requestId, PcbLayerRoutingPrototype.BranchSnapshot snapshot) {\n",
        b"    private void checkOpen() {\n",
        OBSERVER_CORE,
    )
    region(
        "STALE_BOTH_ARGUMENTS",
        b"    private void rejectStale(PcbLayerRoutingPrototype.BranchSnapshot snapshot,\n",
        b"    synchronized void seal() { sealed=true; }\n",
        REJECT_STALE,
    )
    region(
        "HEAD_COPY_AND_VALIDATION",
        b"    private DetachedTerminal detach(PcbLayerRoutingPrototype.BranchSnapshot s) {\n",
        b"    private int validateTuple(DetachedTerminal t, String requestId) {\n",
        DETACH_HEAD,
    )
    splice(
        "HEAD_VALIDATION",
        b"    private void checkId(String value) {\n",
        VALIDATE_SELECTION + b"    private void checkId(String value) {\n",
    )
    region(
        "ATOMIC_HEAD_COMMIT",
        b"    private void commit(DetachedTerminal tuple, int ordinal) {\n",
        b"    private static IllegalArgumentException invalid(String message) {\n",
        COMMIT,
    )
    region(
        "CAPACITY_AND_COUNTER_BOUNDS",
        b"    private boolean countersNearOverflow() {\n",
        b"    private void chargeReadCopy() {\n",
        COUNTERS,
    )
    need(tuple(changes) == CHANGES and len(result) <= MAX_SOURCE_BYTES, "DERIVED_SOURCE_SIZE")
    return result, tuple(changes)

def load_base_generator(path_value: str) -> types.ModuleType:
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
    need(0 < len(raw) <= MAX_BASE_BYTES and sha(raw) == BASE_GENERATOR_SHA256,
        "BASE_GENERATOR_HASH")
    module = types.ModuleType("_pinned_selection_consumer_base")
    module.__file__ = "<pinned-selection-consumer-base>"
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
    base = load_base_generator(options["--base-generator"])
    io = base.load_io(options["--io-helper"])
    output = io._checked_directory(options["--output-dir"], "OUTPUT_DIRECTORY")
    need(not io._directory_names(output, "OUTPUT_DIRECTORY"), "OUTPUT_NOT_EMPTY")
    original = io._read_regular(options["--study"], MAX_SOURCE_BYTES, "OWNER_SOURCE")
    derived, changes = derive(original)
    proof = {
        "schema": SCHEMA,
        "status": "GENERATED_NATIVE_ONLY_NOT_COMPILED",
        "studyParentSha256": sha(original),
        "studyParentBytes": len(original),
        "baseGeneratorSha256": BASE_GENERATOR_SHA256,
        "ioHelperSha256": base.IO_HELPER_SHA256,
        "derivedSha256": sha(derived),
        "derivedBytes": len(derived),
        "changes": list(changes),
        "detachedHeadFields": [
            "branchOrdinal", "cell", "layer", "direction", "transitions",
            "cost", "storedHeuristic", "serial", "key", "storedPriority"
        ],
        "observerOperation": "ATOMIC_SELECTED_HEAD_WITH_EXISTING_TERMINAL",
        "wire": "DENIED",
        "productionOwner": "UNCHANGED",
        "budgets": "UNCHANGED",
        "authoredInvocations": 0,
    }
    proof_raw = (json.dumps(proof, sort_keys=True, indent=2) + "\n").encode("ascii")
    files = {
        "CohortTerminalTelemetryStudy.java": derived,
        "SOURCE_PROOF.json": proof_raw,
    }
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
        sys.stderr.write("SELECTION_CONSUMER_OVERLAY_FAIL PYTHON_ISOLATION\n")
        return 2
    try:
        result = generate(parse_args(sys.argv[1:] if argv is None else argv))
    except Exception as failure:
        code = getattr(failure, "code", "INTERNAL_ERROR")
        if type(code) is not str or not code or any(c not in "ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789" for c in code):
            code = "INTERNAL_ERROR"
        sys.stderr.write("SELECTION_CONSUMER_OVERLAY_FAIL " + code + "\n")
        return 2
    sys.stdout.write(json.dumps({
        "schema": SCHEMA,
        "status": result["status"],
        "derivedSha256": result["derivedSha256"],
        "files": 2,
    }, separators=(",", ":")) + "\n")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
