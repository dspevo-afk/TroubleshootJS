import hashlib
import json
import os
from pathlib import Path
import sys

SOURCE_PARENT = "4623f1e5680503527a4423c2ca31fb09ede3cbb8"
P9_ROOT_NAME = "canonical-reservation-provenance-r1"
P8_ROOT_NAME = "canonical-geometric-reasons-r1"
INPUT_SHA256 = "3bc847e015e1b379dcafcb1e39eb4964e65e7a57d6ef06ed2c710cca1ff48f2f"
INPUT_BYTES = 142148
CONTRACT_SET_SHA256 = "e5e5325644128e2f989e161e31eb1dcb669bfa2e3bfc74fe7f05821cf67bcf2d"

ATTACH_OLD = b"fixedGraphReasonObserverForStudy=observer;"
ATTACH_NEW = b"fixedGraphReasonObserverForStudy=CanonicalReservationProvenanceStudy.decorateForStudy(this,observer);"
DETACH_OLD = b"fixedGraphReasonObserverForStudy=null;"
DETACH_NEW = (b"            CanonicalReservationProvenanceStudy.detachForStudy("
               b"fixedGraphReasonObserverForStudy);\n")
SNAPSHOT_ANCHOR = (b"        /** Native fixed-graph observation copy; mutable "
                   b"counters/conflicts/work are deliberately fresh. */")
SNAPSHOT_INSERT = (
    b"        CanonicalReservationProvenanceStudy.RouterOperands "
    b"snapshotReservationOperandsForStudy() {\n"
    b"            return new CanonicalReservationProvenanceStudy.RouterOperands("
    b"layer,layout,board,outline,minX,minY,gridWidth,gridHeight,"
    b"checkProfiledPadTouches,emptyComponentFace,activeNetId,activeTraceWidth,"
    b"pads,padNets,padWidths,padBounds,escapeCorridors);\n"
    b"        }\n"
)
EDITS = (
    ("ATTACH_DECORATOR", 70923, ATTACH_OLD, ATTACH_NEW),
    ("DETACH_RELEASE", 71035, b"", DETACH_NEW),
    ("PASSIVE_SNAPSHOT", 80109, b"", SNAPSHOT_INSERT),
)


def sha256(raw):
    return hashlib.sha256(raw).hexdigest()


def _verify_input(raw):
    if not isinstance(raw, bytes):
        raise TypeError("raw Router source must be bytes")
    if len(raw) != INPUT_BYTES or sha256(raw) != INPUT_SHA256:
        raise ValueError("P8 Router does not match the frozen source pin")
    if raw.startswith(b"\xef\xbb\xbf"):
        raise ValueError("P8 Router framing has a BOM")
    if raw.count(ATTACH_OLD) != 1 or raw.count(DETACH_OLD) != 1 or raw.count(SNAPSHOT_ANCHOR) != 1:
        raise ValueError("P8 Router edit anchors are not unique")


def build_router_overlay(raw):
    """Return the exact pinned P8 Router with the three reviewed observer edits."""
    _verify_input(raw)
    out = bytearray()
    cursor = 0
    for edit_id, offset, removed, inserted in EDITS:
        if offset < cursor or raw[offset:offset + len(removed)] != removed:
            raise ValueError("Router edit anchor/offset mismatch: " + edit_id)
        out.extend(raw[cursor:offset])
        out.extend(inserted)
        cursor = offset + len(removed)
    out.extend(raw[cursor:])
    result = bytes(out)
    if result.count(ATTACH_NEW) != 1 or result.count(DETACH_NEW) != 1 or result.count(SNAPSHOT_INSERT) != 1:
        raise AssertionError("generated Router did not preserve exact edit templates")
    return result


def _ref(path, raw):
    return {"path": path, "bytes": len(raw), "sha256": sha256(raw)}


def build_overlay_manifest(raw, output, builder_raw, contract_raw, source_parent=SOURCE_PARENT):
    """Return canonical proof for edits and the unchanged-byte complement."""
    _verify_input(raw)
    if source_parent != SOURCE_PARENT:
        raise ValueError("unexpected source parent")
    if not all(isinstance(item, bytes) for item in (output, builder_raw, contract_raw)):
        raise TypeError("manifest inputs must be bytes")
    if sha256(contract_raw) != CONTRACT_SET_SHA256:
        raise ValueError("contract set does not match its frozen digest")
    expected = build_router_overlay(raw)
    if output != expected:
        raise ValueError("output is not the reviewed three-edit Router")

    edit_rows = []
    preserved = []
    out_cursor = 0
    in_cursor = 0
    rebuilt = bytearray()
    for edit_id, offset, removed, inserted in EDITS:
        segment = raw[in_cursor:offset]
        if segment:
            preserved.append({
                "inputOffset": in_cursor,
                "outputOffset": out_cursor,
                "bytes": len(segment),
                "sha256": sha256(segment),
            })
            rebuilt.extend(segment)
            out_cursor += len(segment)
        if raw[offset:offset + len(removed)] != removed:
            raise ValueError("edit source bytes changed")
        rebuilt.extend(inserted)
        edit_rows.append({
            "id": edit_id,
            "sourceOffset": offset,
            "removeBytes": len(removed),
            "removedSha256": sha256(removed),
            "insertBytes": len(inserted),
            "insertSha256": sha256(inserted),
        })
        out_cursor += len(inserted)
        in_cursor = offset + len(removed)
    segment = raw[in_cursor:]
    if segment:
        preserved.append({
            "inputOffset": in_cursor,
            "outputOffset": out_cursor,
            "bytes": len(segment),
            "sha256": sha256(segment),
        })
        rebuilt.extend(segment)
        out_cursor += len(segment)
    if bytes(rebuilt) != output or out_cursor != len(output):
        raise AssertionError("edit/complement proof does not reconstruct output")

    return json.dumps({
        "schema": "tsj-q100-reservation-overlay-v1",
        "sourceParent": source_parent,
        "builder": _ref("P9/build_overlay.py", builder_raw),
        "contractSet": _ref("P9/CONTRACT_SET_R1.json", contract_raw),
        "input": _ref("CANARY/overlays-r1/PcbNetRouter.java", raw),
        "outputs": [_ref("P9/overlays-r1/PcbNetRouter.java", output)],
        "edits": edit_rows,
        "preserved": preserved,
        "claims": {
            "observerOnly": True,
            "predicatesUnchanged": True,
            "cacheWritesUnchanged": True,
            "copyAndConstructorUnchanged": True,
            "p8ForwardingOnce": True,
        },
    }, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode("ascii") + b"\n"


def _canonical_leaf(path):
    p = Path(path)
    if not p.is_absolute() or p.resolve(strict=True) != p:
        raise ValueError("path must be canonical and absolute")
    cur = p
    while True:
        if cur.is_symlink():
            raise ValueError("reparse/symlink path is forbidden")
        if cur.parent == cur:
            break
        cur = cur.parent
    return p


def main(argv=None):
    args = sys.argv[1:] if argv is None else argv
    if len(args) != 3:
        raise SystemExit("usage: build_overlay.py P8_ROUTER_PATH P9_ROOT SOURCE_PARENT")
    source_path = _canonical_leaf(args[0])
    p9_root = _canonical_leaf(args[1])
    if source_path.name != "PcbNetRouter.java" or source_path.parent.name != "overlays-r1" or source_path.parent.parent.name != P8_ROOT_NAME:
        raise SystemExit("P8 Router path is not the pinned CANARY overlay location")
    if p9_root.name != P9_ROOT_NAME or not p9_root.is_dir():
        raise SystemExit("P9 input root is not the canonical existing root")
    if args[2] != SOURCE_PARENT:
        raise SystemExit("source parent differs from the frozen ancestor")
    source_raw = source_path.read_bytes()
    _verify_input(source_raw)
    builder_path = _canonical_leaf(__file__)
    contract_path = _canonical_leaf(p9_root / "CONTRACT_SET_R1.json")
    builder_raw = builder_path.read_bytes()
    contract_raw = contract_path.read_bytes()
    output = build_router_overlay(source_raw)
    proof = build_overlay_manifest(source_raw, output, builder_raw, contract_raw, args[2])
    destination = p9_root / "overlays-r1"
    destination.mkdir()
    with (destination / "PcbNetRouter.java").open("xb") as stream:
        stream.write(output)
        stream.flush()
        os.fsync(stream.fileno())
    with (destination / "overlay-manifest.json").open("xb") as stream:
        stream.write(proof)
        stream.flush()
        os.fsync(stream.fileno())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
