#!/usr/bin/env python3
"""Create the hash-pinned shared-capture-budget source overlay."""

import argparse
import difflib
import hashlib
import os
import stat
import sys
from pathlib import Path


if not sys.flags.isolated or not sys.dont_write_bytecode:
    raise SystemExit("run this transformer with Python -I -B")


CAPTURE_SHA256 = "c33f6f696fb52fedce583f19f47f3ff44ca01bca3c9b97410291d5284642d88b"
OWNER_SHA256 = "264c89e58e0e29b3375520459d627d945ec28cd747065647181ce118d75dd9f6"
CAPTURE_NAME = "FailureCohortRoutingStudy.java"
OWNER_NAME = "ActualGeneratorBranchOrderStudy.java"
REPARSE_POINT_ATTRIBUTE = 0x400


OWNER_REPLACEMENTS = (
    (
        "shared owner budget interface",
        b"""public final class ActualGeneratorBranchOrderStudy {
    interface TerminalPrefixInspector {""",
        b"""public final class ActualGeneratorBranchOrderStudy {
    interface SharedOwnerBudget {
        void checkOuterDeadline();
        void admitOwnerGroup(int ordinal);
    }

    interface TerminalPrefixInspector {""",
    ),
    (
        "shared owner budget field",
        b"""        int nextGroupOrdinal;
        int branchCount;""",
        b"""        int nextGroupOrdinal;
        SharedOwnerBudget sharedOwnerBudget;
        int branchCount;""",
    ),
    (
        "constructor chain",
        b"""        ArmRun(Study study, int operation) {
            this(study, operation, null, null);
        }

        ArmRun(Study study, int operation,
                PcbLayerRoutingPrototype.FixedPrefixGraphObserver fixedPrefixGraphObserver) {
            this(study, operation, fixedPrefixGraphObserver, null);
        }

        ArmRun(Study study, int operation,
                PcbLayerRoutingPrototype.FixedPrefixGraphObserver fixedPrefixGraphObserver,
                TerminalPrefixInspector terminalPrefixInspector) {
            if (fixedPrefixGraphObserver != null && operation != 1)
                throw new IllegalArgumentException(
                    "Fixed-prefix graph observation is supported only for operation 1");
            if (terminalPrefixInspector != null && operation != 1)
                throw new IllegalArgumentException(
                    "Terminal-prefix inspection is supported only for operation 1");
            this.study = study;
            this.operation = operation;
            this.fixedPrefixGraphObserver = fixedPrefixGraphObserver;
            this.terminalPrefixInspector = terminalPrefixInspector;
        }""",
        b"""        ArmRun(Study study, int operation) {
            this(study, operation, null, null, null);
        }

        ArmRun(Study study, int operation,
                PcbLayerRoutingPrototype.FixedPrefixGraphObserver fixedPrefixGraphObserver) {
            this(study, operation, fixedPrefixGraphObserver, null, null);
        }

        ArmRun(Study study, int operation,
                PcbLayerRoutingPrototype.FixedPrefixGraphObserver fixedPrefixGraphObserver,
                TerminalPrefixInspector terminalPrefixInspector) {
            this(study, operation, fixedPrefixGraphObserver, terminalPrefixInspector, null);
        }

        ArmRun(Study study, int operation,
                PcbLayerRoutingPrototype.FixedPrefixGraphObserver fixedPrefixGraphObserver,
                TerminalPrefixInspector terminalPrefixInspector,
                SharedOwnerBudget sharedOwnerBudget) {
            if (fixedPrefixGraphObserver != null && operation != 1)
                throw new IllegalArgumentException(
                    "Fixed-prefix graph observation is supported only for operation 1");
            if (terminalPrefixInspector != null && operation != 1)
                throw new IllegalArgumentException(
                    "Terminal-prefix inspection is supported only for operation 1");
            this.study = study;
            this.operation = operation;
            this.fixedPrefixGraphObserver = fixedPrefixGraphObserver;
            this.terminalPrefixInspector = terminalPrefixInspector;
            this.sharedOwnerBudget = sharedOwnerBudget;
        }""",
    ),
    (
        "execute cleanup nesting start",
        b"""            } finally {
                long cleanupStarted = System.nanoTime();
                if (sink != null && !sink.isClosed()) {""",
        b"""            } finally {
                try { long cleanupStarted = System.nanoTime();
                if (sink != null && !sink.isClosed()) {""",
    ),
    (
        "execute cleanup nesting end",
        b"""                if (!ownersReleased || !contextRestored)
                    failure = appendFailure(failure,
                        new IllegalStateException("Generator owners or native context were not released"));
            }
            if (failure != null) rethrow(failure);""",
        b"""                if (!ownersReleased || !contextRestored)
                    failure = appendFailure(failure,
                        new IllegalStateException("Generator owners or native context were not released"));
                } finally {
                    long guardReleaseStarted = System.nanoTime();
                    boolean hadSharedOwnerBudget = sharedOwnerBudget != null;
                    try {
                        clearSharedOwnerBudget();
                    } finally {
                        if (hadSharedOwnerBudget && result != null)
                            result.cleanupNanos += elapsed(guardReleaseStarted, System.nanoTime());
                    }
                }
            }
            if (failure != null) rethrow(failure);""",
    ),
    (
        "shared deadline callback",
        b"""        private void checkOperationDeadline() {
            long elapsedNanos = elapsed(operationStarted, System.nanoTime());""",
        b"""        private void checkOperationDeadline() {
            if (sharedOwnerBudget != null) sharedOwnerBudget.checkOuterDeadline();
            long elapsedNanos = elapsed(operationStarted, System.nanoTime());""",
    ),
    (
        "guard reference clear helper",
        b"""        private String groupJson(GroupLedger group, long beforeOwnLine) {""",
        b"""        void clearSharedOwnerBudget() {
            sharedOwnerBudget = null;
        }

        private String groupJson(GroupLedger group, long beforeOwnLine) {""",
    ),
    (
        "aggregate owner admission",
        b"""        private GroupLedger newGroup(String phase, long started) {
            checkOperationDeadline();
            if (nextGroupOrdinal >= GROUP_LIMIT)
                throw new IllegalStateException("Actual study exceeded 640 total groups");
            return new GroupLedger(nextGroupOrdinal++, phase, started);
        }""",
        b"""        private GroupLedger newGroup(String phase, long started) {
            checkOperationDeadline();
            if (nextGroupOrdinal >= GROUP_LIMIT)
                throw new IllegalStateException("Actual study exceeded 640 total groups");
            if (sharedOwnerBudget != null) {
                sharedOwnerBudget.admitOwnerGroup(nextGroupOrdinal);
                int admittedOrdinal = nextGroupOrdinal++;
                return new GroupLedger(admittedOrdinal, phase, started);
            }
            return new GroupLedger(nextGroupOrdinal++, phase, started);
        }""",
    ),
)


CAPTURE_REPLACEMENTS = (
    (
        "capture ArmRun type for cleanup",
        b"""        Object study=null,armRun=null,armResult=null,legacyCapture=null,decorator=null;""",
        b"""        Object study=null,armRun=null,armResult=null,legacyCapture=null,decorator=null;
        Class<?> armType=null;""",
    ),
    (
        "already-adopted original owner assertion",
        b"""        OriginalArm original=captureOriginal(inputs,meter);
        meter.priorGroups=original.groupCount;""",
        b"""        OriginalArm original=captureOriginal(inputs,meter);
        if(meter.priorGroups!=original.groupCount || meter.originalOwnerActive ||
                meter.inFlightOwnerGroups!=0 || !meter.originalOwnerAdopted)
            throw new IllegalStateException("original owner group count was not adopted exactly once");""",
    ),
    (
        "capture owner lifecycle state",
        b"""        OriginalArm built=null;
        long armStart=System.nanoTime();""",
        b"""        OriginalArm built=null;
        boolean ownerStarted=false,ownerAdoptionAttempted=false,ownerAdopted=false,executeEntered=false;
        Throwable primaryFailure=null;
        long armStart=System.nanoTime();""",
    ),
    (
        "meter start, opt-in constructor, invocation and immediate adoption",
        b"""            Class<?> armType=Class.forName("com.lushprojects.circuitjs1.client.ActualGeneratorBranchOrderStudy$ArmRun");
            Constructor<?> ac=armType.getDeclaredConstructor(studyType,int.class,
                PcbLayerRoutingPrototype.FixedPrefixGraphObserver.class,
                ActualGeneratorBranchOrderStudy.TerminalPrefixInspector.class);
            ac.setAccessible(true);
            armRun=ac.newInstance(study,Integer.valueOf(1),null,decorator);
            meter.charge(8);
            meter.finishGroup();
            Method execute=armType.getDeclaredMethod("execute"); execute.setAccessible(true);
            armResult=invoke(execute,armRun);
            long armElapsed=elapsed(armStart,System.nanoTime());""",
        b"""            armType=Class.forName("com.lushprojects.circuitjs1.client.ActualGeneratorBranchOrderStudy$ArmRun");
            Class<?> budgetType=Class.forName(
                "com.lushprojects.circuitjs1.client.ActualGeneratorBranchOrderStudy$SharedOwnerBudget");
            Constructor<?> ac=armType.getDeclaredConstructor(studyType,int.class,
                PcbLayerRoutingPrototype.FixedPrefixGraphObserver.class,
                ActualGeneratorBranchOrderStudy.TerminalPrefixInspector.class,budgetType);
            ac.setAccessible(true);
            armRun=ac.newInstance(study,Integer.valueOf(1),null,decorator,meter);
            meter.charge(8);
            meter.finishGroup();
            meter.beginOriginalOwner();
            ownerStarted=true;
            Method execute=armType.getDeclaredMethod("execute"); execute.setAccessible(true);
            try {
                armResult=execute.invoke(armRun);
                executeEntered=true;
            } catch(InvocationTargetException wrapped) {
                executeEntered=true;
                Throwable cause=wrapped.getCause();
                if(cause instanceof Exception) throw (Exception)cause;
                if(cause instanceof Error) throw (Error)cause;
                throw new IllegalStateException("maintained invocation failed",cause);
            }
            int actualOwnerGroups=intField(armRun,"nextGroupOrdinal");
            ownerAdoptionAttempted=true;
            meter.adoptOriginalOwner(actualOwnerGroups);
            ownerAdopted=true;
            long armElapsed=elapsed(armStart,System.nanoTime());""",
    ),
    (
        "failure-preserving owner adoption and fallback guard clear",
        b"""            built=buildOriginalArm(inputs,meter,legacyCapture,armRun,armResult,armType,captureType,data);
            return built;
        } finally {
            long releaseStarted=System.nanoTime();
            armResult=null; armRun=null; study=null; decorator=null;
            if(built!=null) built.cleanupNanos+=elapsed(releaseStarted,System.nanoTime());
        }""",
        b"""            built=buildOriginalArm(inputs,meter,legacyCapture,armRun,armResult,armType,captureType,data);
            return built;
        } catch(Throwable failure) {
            primaryFailure=failure;
            rethrow(failure);
            return null;
        } finally {
            Throwable cleanupFailure=null;
            try {
                if(ownerStarted && !ownerAdoptionAttempted) {
                    try {
                        int actualOwnerGroups=armRun==null?0:intField(armRun,"nextGroupOrdinal");
                        ownerAdoptionAttempted=true;
                        meter.adoptOriginalOwner(actualOwnerGroups);
                        ownerAdopted=true;
                    } catch(Throwable adoptionFailure) {
                        cleanupFailure=adoptionFailure;
                    }
                }
            } finally {
                try {
                    if(armRun!=null && !executeEntered) {
                        try {
                            Method clear=armType.getDeclaredMethod("clearSharedOwnerBudget");
                            clear.setAccessible(true);
                            invoke(clear,armRun);
                        } catch(Throwable clearFailure) {
                            if(cleanupFailure==null) cleanupFailure=clearFailure;
                            else if(clearFailure!=cleanupFailure) cleanupFailure.addSuppressed(clearFailure);
                        }
                    }
                } finally {
                    long releaseStarted=System.nanoTime();
                    armResult=null; armRun=null; study=null; decorator=null;
                    if(built!=null) built.cleanupNanos+=elapsed(releaseStarted,System.nanoTime());
                }
            }
            if(cleanupFailure!=null) {
                if(primaryFailure!=null) {
                    if(cleanupFailure!=primaryFailure) primaryFailure.addSuppressed(cleanupFailure);
                } else {
                    rethrow(cleanupFailure);
                }
            }
        }""",
    ),
    (
        "meter owner state",
        b"""private static final class Meter {
    int sessionConstructorEntries;""",
        b"""private static final class Meter implements ActualGeneratorBranchOrderStudy.SharedOwnerBudget {
    int sessionConstructorEntries;""",
    ),
    (
        "meter owner counters",
        b"""    int groups,groupUnits,maxGroupUnits,priorGroups,copyChunks;
    boolean open;""",
        b"""    int groups,groupUnits,maxGroupUnits,priorGroups,copyChunks,inFlightOwnerGroups;
    boolean open,originalOwnerStarted,originalOwnerActive,originalOwnerAdopted;""",
    ),
    (
        "one-shot owner begin/adopt and callbacks",
        b"""    void setCopyAllowance(int allowance) {
        if(allowance<0 || allowance>COPY_LIMIT)
            throw new IllegalArgumentException("copy allowance is out of range");
        if(copiedEntries>allowance)
            throw new IllegalStateException("copy allowance is below already reserved entries");
        copyAllowance=allowance;
    }

    void beginGroup() throws IOException {""",
        b"""    void setCopyAllowance(int allowance) {
        if(allowance<0 || allowance>COPY_LIMIT)
            throw new IllegalArgumentException("copy allowance is out of range");
        if(copiedEntries>allowance)
            throw new IllegalStateException("copy allowance is below already reserved entries");
        copyAllowance=allowance;
    }

    void beginOriginalOwner() {
        if(open)
            throw new IllegalStateException("original owner must start between wrapper groups");
        if(priorGroups!=0 || originalOwnerStarted || originalOwnerActive || originalOwnerAdopted)
            throw new IllegalStateException("original owner budget can start only once before adoption");
        check();
        originalOwnerStarted=true;
        originalOwnerActive=true;
        inFlightOwnerGroups=0;
    }

    void adoptOriginalOwner(int actualOrdinal) {
        if(!originalOwnerStarted || !originalOwnerActive || originalOwnerAdopted)
            throw new IllegalStateException("original owner budget is not active for adoption");
        if(actualOrdinal<0 || actualOrdinal!=inFlightOwnerGroups)
            throw new IllegalStateException("actual original owner ordinal differs from admitted groups");
        if(priorGroups!=0)
            throw new IllegalStateException("original owner groups were already adopted");
        priorGroups=actualOrdinal;
        inFlightOwnerGroups=0;
        originalOwnerActive=false;
        originalOwnerAdopted=true;
    }

    public void checkOuterDeadline() {
        check();
    }

    public void admitOwnerGroup(int ordinal) {
        check();
        if(!originalOwnerStarted || !originalOwnerActive || originalOwnerAdopted)
            throw new IllegalStateException("original owner group admission is not active");
        if(ordinal<0 || ordinal!=inFlightOwnerGroups)
            throw new IllegalStateException("original owner group ordinal is not the next admitted ordinal");
        long occupied=(long)groups+(long)priorGroups+(long)inFlightOwnerGroups+(open?1L:0L);
        if(occupied>=MAX_GROUPS)
            throw new IllegalStateException("native owner exceeded 640 aggregate measured groups");
        inFlightOwnerGroups++;
    }

    void beginGroup() throws IOException {""",
    ),
    (
        "wrapper admission includes live owner groups",
        b"""        if(groups+priorGroups>=MAX_GROUPS)
            throw new IOException("native wrapper exceeded 640 measured groups");""",
        b"""        if((long)groups+(long)priorGroups+(long)inFlightOwnerGroups>=MAX_GROUPS)
            throw new IOException("native wrapper exceeded 640 measured groups");""",
    ),
    (
        "meter invariant includes open and in-flight groups",
        b"""        if(groups+priorGroups>MAX_GROUPS || groupUnits>STEP_LIMIT)
            throw new IllegalStateException("native wrapper exceeded a frozen work bound");""",
        b"""        if((long)groups+(long)priorGroups+(long)inFlightOwnerGroups+(open?1L:0L)>MAX_GROUPS ||
                groupUnits>STEP_LIMIT)
            throw new IllegalStateException("native wrapper exceeded a frozen work bound");""",
    ),
    (
        "finish cap includes live owner groups",
        b"""        if(groups+priorGroups>MAX_GROUPS)
            throw new IOException("native wrapper exceeded 640 measured groups");""",
        b"""        if((long)groups+(long)priorGroups+(long)inFlightOwnerGroups>MAX_GROUPS)
            throw new IOException("native wrapper exceeded 640 measured groups");""",
    ),
)


def has_reparse_component(path):
    absolute = Path(os.path.abspath(str(path)))
    for component in (absolute,) + tuple(absolute.parents):
        try:
            metadata = component.lstat()
        except FileNotFoundError:
            continue
        attributes = getattr(metadata, "st_file_attributes", 0)
        if stat.S_ISLNK(metadata.st_mode) or attributes & REPARSE_POINT_ATTRIBUTE:
            return True
    return False


def replace_once(source, label, old, new):
    occurrences = source.count(old)
    if occurrences != 1:
        raise ValueError(
            "{}: expected one exact source occurrence, found {}".format(label, occurrences)
        )
    return source.replace(old, new, 1)


def transform_source(source, expected_sha256, replacements, label):
    actual = hashlib.sha256(source).hexdigest()
    if actual != expected_sha256:
        raise ValueError(
            "{} bytes do not match the pinned parent (sha256 {})".format(label, actual)
        )
    transformed = source
    for replacement_label, old, new in replacements:
        transformed = replace_once(transformed, replacement_label, old, new)
    if transformed == source:
        raise ValueError("{} transformation unexpectedly produced no changes".format(label))
    return transformed


def make_diff(source, transformed, source_name, target_name):
    before_text = source.decode("utf-8")
    after_text = transformed.decode("utf-8")
    return "".join(
        difflib.unified_diff(
            before_text.splitlines(keepends=True),
            after_text.splitlines(keepends=True),
            fromfile="immutable/" + source_name,
            tofile="shared-budget/" + target_name,
            n=3,
        )
    ).encode("utf-8")


def require_source(parser, path, expected_name, expected_hash, label):
    if has_reparse_component(path) or not path.is_file():
        parser.error("{} must be a regular file with no reparse-point path component".format(label))
    if path.name != expected_name:
        parser.error("{} must name {}".format(label, expected_name))
    source = path.read_bytes()
    if hashlib.sha256(source).hexdigest() != expected_hash:
        parser.error("{} bytes do not match the pinned parent".format(label))
    return source


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--capture-source", required=True,
                        help="exact SHA-pinned native capture producer source")
    parser.add_argument("--owner-source", required=True,
                        help="exact SHA-pinned native owner source")
    parser.add_argument("--output-dir", required=True,
                        help="existing empty task-owned output directory")
    args = parser.parse_args()

    capture_path = Path(args.capture_source)
    owner_path = Path(args.owner_source)
    output_dir = Path(args.output_dir)
    if has_reparse_component(output_dir) or not output_dir.is_dir():
        parser.error("--output-dir must be an existing directory with no reparse-point path component")
    if any(output_dir.iterdir()):
        parser.error("--output-dir must be empty; refusing to overwrite or mix artifacts")

    capture = require_source(parser, capture_path, CAPTURE_NAME, CAPTURE_SHA256, "--capture-source")
    owner = require_source(parser, owner_path, OWNER_NAME, OWNER_SHA256, "--owner-source")
    try:
        capture_overlay = transform_source(
            capture, CAPTURE_SHA256, CAPTURE_REPLACEMENTS, CAPTURE_NAME
        )
        owner_overlay = transform_source(
            owner, OWNER_SHA256, OWNER_REPLACEMENTS, OWNER_NAME
        )
        capture_diff = make_diff(capture, capture_overlay, CAPTURE_NAME, CAPTURE_NAME)
        owner_diff = make_diff(owner, owner_overlay, OWNER_NAME, OWNER_NAME)
    except (UnicodeDecodeError, ValueError) as error:
        parser.error(str(error))

    outputs = (
        (output_dir / CAPTURE_NAME, capture_overlay),
        (output_dir / OWNER_NAME, owner_overlay),
        (output_dir / "FailureCohortRoutingStudy-shared-budget.diff", capture_diff),
        (output_dir / "ActualGeneratorBranchOrderStudy-shared-budget.diff", owner_diff),
    )
    if has_reparse_component(output_dir):
        parser.error("--output-dir acquired a reparse-point path component")
    for target, content in outputs:
        if has_reparse_component(target):
            parser.error("output file path has a symlink or reparse-point component")
        with target.open("xb") as output:
            output.write(content)


if __name__ == "__main__":
    main()
