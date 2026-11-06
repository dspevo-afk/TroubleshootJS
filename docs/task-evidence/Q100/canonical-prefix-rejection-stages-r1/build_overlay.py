#!/usr/bin/env python3
"""Build the frozen native-only PcbNetRouter predicate-stage overlay."""
from __future__ import annotations

import difflib
import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCE_REL = Path("src/com/lushprojects/circuitjs1/client/PcbNetRouter.java")
BASELINE_PATH = ROOT / "source-baseline-r1.json"
ADDENDUM_PATH = ROOT / "ADDENDUM_R2.md"
OUTPUT_DIR = ROOT / "overlays-r1"

EXPECTED_SOURCE_BYTES = 129081
EXPECTED_SOURCE_SHA256 = "92347537ca59629f91d6a72596cd59d6520cff2deebb3681f22d6d9ec82c21ec"
EXPECTED_ADDENDUM_SHA256 = "3da49dcbc8dbb1950e5a875a5bf04c00b0829d60e61f2ef7e294a4fc4295abc2"
CRLF = b"\r\n"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def source_bytes(text: str) -> bytes:
    return text.encode("utf-8").replace(b"\n", CRLF)


def add_edit(edits, source: bytes, anchor: bytes, replacement: bytes, label: bytes,
             *, after: bool = False) -> None:
    if source.count(anchor) != 1:
        raise ValueError("expected unique source fragment: " + label.decode("ascii"))
    start = source.index(anchor)
    if after:
        start += len(anchor)
    end = start if after else start + len(anchor)
    edits.append((start, end, replacement, label))


def _build_overlay(raw: bytes):
    """Pure transform plus byte-preservation proof; no filesystem or process I/O."""
    original = raw
    if len(original) != EXPECTED_SOURCE_BYTES or sha256(original) != EXPECTED_SOURCE_SHA256:
        raise ValueError("input bytes do not match the frozen PcbNetRouter source pin")
    try:
        original_text = original.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ValueError("source is not UTF-8") from exc
    if b"\r\n" not in original or b"\n" in original.replace(CRLF, b"") or \
            original.startswith(b"\xef\xbb\xbf"):
        raise ValueError("source newline/BOM shape differs from the frozen source")

    field_anchor = source_bytes(
        "        private final SeededPcbLayoutGenerator.AttemptObserver observer;\n")
    api = source_bytes("""        interface FixedGraphStageObserver {
            void onEndpoint(String netId,String startPadId,String endpointPadId,
                    int fromGridX,int fromGridY,int toGridX,int toGridY,int direction,int stage);
        }
        static final int STAGE_LEGAL_MOVE_REJECTED=0;
        static final int STAGE_TRAVERSE_REJECTED=1;
        static final int STAGE_OCCUPANCY_REJECTED=2;
        static final int STAGE_ALLOWED=3;
        private FixedGraphStageObserver fixedGraphStageObserverForStudy;
""")

    old_endpoint = source_bytes("""        private boolean permitsLayerStepToEndpoint(int x,int y,int nx,int ny,int direction,
                String net,PcbPadPlacement start,PcbPadPlacement end) {
            return isLegalMove(x,y,nx,ny,direction,gridX(start.getX()),gridY(start.getY()),
                end==null?-1:gridX(end.getX()),end==null?-1:gridY(end.getY()),start,end) &&
                canTraverse(x,y,nx,ny,start,end,net) && canOccupy(nx,ny,net,start,end);
        }
""")
    new_endpoint = source_bytes("""        void attachFixedGraphStageObserverForStudy(FixedGraphStageObserver observer,
                Router expectedQuery,Router live,Router pristine,boolean comparisonPassed) {
            if(observer==null || expectedQuery==null || live==null || pristine==null ||
                    expectedQuery==live || expectedQuery==pristine || live==pristine)
                throw new IllegalArgumentException("Invalid fixed graph stage observer roles");
            if(this!=expectedQuery || this==live || this==pristine || !comparisonPassed)
                throw new IllegalArgumentException("Fixed graph stage observer requires a compared query copy");
            if(fixedGraphStageObserverForStudy!=null)
                throw new IllegalStateException("Fixed graph stage observer is already attached");
            fixedGraphStageObserverForStudy=observer;
        }
        void detachFixedGraphStageObserverForStudy() {
            fixedGraphStageObserverForStudy=null;
        }
        boolean hasFixedGraphStageObserverForStudy() {
            return fixedGraphStageObserverForStudy!=null;
        }
        private boolean reportFixedGraphStageForStudy(String net,PcbPadPlacement start,
                PcbPadPlacement end,int x,int y,int nx,int ny,int direction,int stage,
                boolean allowed) {
            FixedGraphStageObserver callback=fixedGraphStageObserverForStudy;
            if(callback!=null)
                callback.onEndpoint(net,start.getPadId(),end==null?null:end.getPadId(),
                    x,y,nx,ny,direction,stage);
            return allowed;
        }
        private boolean permitsLayerStepToEndpoint(int x,int y,int nx,int ny,int direction,
                String net,PcbPadPlacement start,PcbPadPlacement end) {
            if(!isLegalMove(x,y,nx,ny,direction,gridX(start.getX()),gridY(start.getY()),
                    end==null?-1:gridX(end.getX()),end==null?-1:gridY(end.getY()),start,end))
                return reportFixedGraphStageForStudy(net,start,end,x,y,nx,ny,direction,
                    STAGE_LEGAL_MOVE_REJECTED,false);
            if(!canTraverse(x,y,nx,ny,start,end,net))
                return reportFixedGraphStageForStudy(net,start,end,x,y,nx,ny,direction,
                    STAGE_TRAVERSE_REJECTED,false);
            if(!canOccupy(nx,ny,net,start,end))
                return reportFixedGraphStageForStudy(net,start,end,x,y,nx,ny,direction,
                    STAGE_OCCUPANCY_REJECTED,false);
            return reportFixedGraphStageForStudy(net,start,end,x,y,nx,ny,direction,
                STAGE_ALLOWED,true);
        }
""")
    if any(new_endpoint.count(predicate) != 1 for predicate in
           (b"isLegalMove(", b"canTraverse(", b"canOccupy(")):
        raise ValueError("endpoint predicate must occur exactly once in ordered chain")
    helper_start = new_endpoint.index(b"private boolean reportFixedGraphStageForStudy")
    helper_end = new_endpoint.index(b"private boolean permitsLayerStepToEndpoint", helper_start)
    helper = new_endpoint[helper_start:helper_end]
    if helper.index(b"if(callback!=null)") > helper.index(b"start.getPadId()"):
        raise ValueError("callback null check must precede endpoint pad-ID extraction")

    edits = []
    add_edit(edits, original, field_anchor, api, b"observer-api-and-field", after=True)
    add_edit(edits, original, old_endpoint, new_endpoint, b"attach-and-endpoint-reporting")
    edits.sort(key=lambda item: item[0])
    for first, second in zip(edits, edits[1:]):
        if first[1] > second[0]:
            raise ValueError("overlay edit fragments overlap")

    out = bytearray()
    preserved = []
    edit_records = []
    cursor = 0
    for start, end, replacement, label in edits:
        unchanged = original[cursor:start]
        output_start = len(out)
        out.extend(unchanged)
        if unchanged:
            preserved.append({
                "sourceOffset": cursor,
                "overlayOffset": output_start,
                "bytes": len(unchanged),
                "sha256": sha256(unchanged),
            })
        replacement_start = len(out)
        out.extend(replacement)
        edit_records.append({
            "label": label.decode("ascii"),
            "sourceOffset": start,
            "sourceBytes": end - start,
            "sourceSha256": sha256(original[start:end]),
            "overlayOffset": replacement_start,
            "overlayBytes": len(replacement),
            "overlaySha256": sha256(replacement),
        })
        cursor = end
    tail = original[cursor:]
    tail_output_start = len(out)
    out.extend(tail)
    if tail:
        preserved.append({
            "sourceOffset": cursor,
            "overlayOffset": tail_output_start,
            "bytes": len(tail),
            "sha256": sha256(tail),
        })
    overlay = bytes(out)

    expected_unchanged = len(original) - sum(row["sourceBytes"] for row in edit_records)
    if sum(row["bytes"] for row in preserved) != expected_unchanged:
        raise ValueError("unchanged-byte accounting mismatch")
    expected_overlay_size = len(original) + sum(
        row["overlayBytes"] - row["sourceBytes"] for row in edit_records)
    if len(overlay) != expected_overlay_size:
        raise ValueError("overlay size accounting mismatch")
    for row in preserved:
        start = row["sourceOffset"]
        length = row["bytes"]
        output_start = row["overlayOffset"]
        if original[start:start + length] != overlay[output_start:output_start + length]:
            raise ValueError("non-whitelisted source bytes changed")

    bounds_and_endpoint_loop = source_bytes("""        boolean permitsLayerStep(int ax,int ay,int bx,int by,String net,
                PcbPadPlacement start,PcbPadPlacement end,PcbPadPlacement[] reachedTerminals) {
            int x=gridX(ax),y=gridY(ay),nx=gridX(bx),ny=gridY(by);
            if(x<0 || y<0 || nx<0 || ny<0 || x>=gridWidth || nx>=gridWidth ||
                    y>=gridHeight || ny>=gridHeight) return false;
            int direction=nx>x?1:nx<x?3:ny>y?2:0;
            if(permitsLayerStepToEndpoint(x,y,nx,ny,direction,net,start,end)) return true;
            if(reachedTerminals==null) return false;
            for(PcbPadPlacement reached:reachedTerminals) {
                if(reached==null) throw new IllegalArgumentException("Missing reached P07 terminal");
                if(reached.getPadId().equals(start.getPadId()) ||
                        end!=null && reached.getPadId().equals(end.getPadId())) continue;
                if(permitsLayerStepToEndpoint(x,y,nx,ny,direction,net,start,reached)) return true;
            }
            return false;
        }
""")
    if original.count(bounds_and_endpoint_loop) != 1 or overlay.count(bounds_and_endpoint_loop) != 1:
        raise ValueError("Router bounds/endpoint loop changed")
    if b"fixedGraphStageObserverForStudy" in original:
        raise ValueError("unexpected pre-existing native observer field")
    copy_start_marker = b"        private Router(Router source,boolean observationShell)"
    copy_end_marker = b"        private static PcbRoutingWork copyObservationWork"
    copy_start = overlay.index(copy_start_marker)
    copy_end = overlay.index(copy_end_marker, copy_start)
    if b"fixedGraphStageObserverForStudy" in overlay[copy_start:copy_end] or \
            b"copy.fixedGraphStageObserverForStudy" in overlay:
        raise ValueError("fixed graph copy code must leave the observer field at default null")
    return overlay, preserved, edit_records


def make_overlay(raw: bytes) -> bytes:
    """Purely transform pinned source bytes; performs no I/O or mutation."""
    return _build_overlay(raw)[0]


def write_same_or_new(path: Path, data: bytes) -> None:
    if path.exists():
        if path.read_bytes() != data:
            raise SystemExit("refusing to replace an existing nonmatching artifact: " + str(path))
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: build_overlay.py REPOSITORY_ROOT")
    repository = Path(sys.argv[1]).resolve(strict=True)
    if not repository.is_dir():
        raise SystemExit("repository root must be a directory")
    addendum = ADDENDUM_PATH.read_bytes()
    if sha256(addendum) != EXPECTED_ADDENDUM_SHA256:
        raise SystemExit("frozen ADDENDUM_R2.md SHA-256 mismatch")

    baseline = json.loads(BASELINE_PATH.read_text(encoding="utf-8"))
    rows = [row for row in baseline["files"] if row["path"] == SOURCE_REL.as_posix()]
    if len(rows) != 1 or rows[0].get("bytes") != EXPECTED_SOURCE_BYTES or \
            rows[0].get("sha256") != EXPECTED_SOURCE_SHA256:
        raise SystemExit("frozen source-baseline row mismatch")

    original = (repository / SOURCE_REL).read_bytes()
    try:
        overlay, preserved, edit_records = _build_overlay(original)
    except ValueError as exc:
        raise SystemExit(str(exc)) from exc
    original_text = original.decode("utf-8")
    diff_text = "\n".join(difflib.unified_diff(
        original_text.splitlines(keepends=False),
        overlay.decode("utf-8").splitlines(keepends=False),
        fromfile="source/PcbNetRouter.java",
        tofile="overlays-r1/PcbNetRouter.java",
        lineterm="",
    )) + "\n"
    diff_bytes = diff_text.encode("utf-8")
    builder_sha = sha256(Path(__file__).read_bytes())
    manifest = {
        "schema": "tsj-p6-pcb-net-router-overlay-manifest-r1",
        "builder": {
            "path": "build_overlay.py",
            "sha256": builder_sha,
        },
        "source": {
            "path": SOURCE_REL.as_posix(),
            "bytes": len(original),
            "sha256": sha256(original),
            "frozenBaselineRow": True,
        },
        "addendum": {
            "path": "ADDENDUM_R2.md",
            "sha256": sha256(addendum),
        },
        "overlay": {
            "path": "overlays-r1/PcbNetRouter.java",
            "bytes": len(overlay),
            "sha256": sha256(overlay),
        },
        "diff": {
            "path": "overlays-r1/PcbNetRouter.diff",
            "bytes": len(diff_bytes),
            "sha256": sha256(diff_bytes),
        },
        "editFragments": edit_records,
        "unchangedSegments": preserved,
        "unchangedBytes": sum(row["bytes"] for row in preserved),
        "nonWhitelistedBytesIdentical": True,
        "routerBoundsAndEndpointLoopByteIdentical": True,
        "predicateOrderAndSingleEvaluation": ["isLegalMove", "canTraverse", "canOccupy"],
        "observerFieldCopiedToFixedGraphCopies": False,
    }
    manifest_bytes = (json.dumps(manifest, sort_keys=True, indent=2) + "\n").encode("utf-8")
    write_same_or_new(OUTPUT_DIR / "PcbNetRouter.java", overlay)
    write_same_or_new(OUTPUT_DIR / "PcbNetRouter.diff", diff_bytes)
    write_same_or_new(OUTPUT_DIR / "manifest-r1.json", manifest_bytes)
    print(json.dumps({
        "status": "PASS_STATIC_OVERLAY_GENERATED",
        "builderSha256": builder_sha,
        "sourceBytes": len(original),
        "sourceSha256": sha256(original),
        "overlayBytes": len(overlay),
        "overlaySha256": sha256(overlay),
        "diffSha256": sha256(diff_bytes),
        "manifestSha256": sha256(manifest_bytes),
        "unchangedBytes": manifest["unchangedBytes"],
    }, sort_keys=True))


if __name__ == "__main__":
    main()
