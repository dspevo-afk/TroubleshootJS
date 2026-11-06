#!/usr/bin/env python3
"""Create pinned BASE and RAMP native source overlays without editing the repository."""
import difflib
import hashlib
import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile

PLAN_SHA256 = "2d640b3d36cca2ae2613718e356e84ed603abf3ccf813f4a1372f35b28e72bce"
SOURCE_SHA256 = "759831c91345aedab9e78d8c83785019f7e59287a18f4f99f7af14876776c474"
PARENT_HEAD = "30b2a1cbbb02b39dbc275a82755902e61043b714"
PARENT_BRANCH = "codex/q100-prefix-via-ramp-20261105"
SOURCE_REL = Path("src/com/lushprojects/circuitjs1/client/PcbLayerRoutingPrototype.java")
MAX_INPUT_BYTES = 32 * 1024 * 1024


def require(condition, label):
    if not condition:
        raise ValueError(label)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def checked_path(path, label):
    path = Path(path).resolve(strict=True)
    for ancestor in (path, *path.parents):
        info = ancestor.lstat()
        require(not stat.S_ISLNK(info.st_mode) and
                not (getattr(info, "st_file_attributes", 0) & 0x400),
                label + "_REPARSE")
    return path


def stable_read(path, maximum, label):
    path = checked_path(path, label)
    before = path.stat()
    require(before.st_size <= maximum, label + "_SIZE")
    data = path.read_bytes()
    after = path.stat()
    require((before.st_size, before.st_mtime_ns, before.st_ino) ==
            (after.st_size, after.st_mtime_ns, after.st_ino) and
            len(data) == after.st_size, label + "_CHANGED")
    return data


def write_new(path, data):
    path = Path(path)
    with path.open("xb") as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())


def replace_once(text, old, new, label):
    count = text.count(old)
    require(count == 1, "SOURCE_SEAM_" + label + "_COUNT_" + str(count))
    return text.replace(old, new, 1)


def source_newline(text):
    return "\r\n" if "\r\n" in text else "\n"


def with_newline(block, newline):
    return block.replace("\n", newline)


def overlay_router(raw):
    text = raw.decode("utf-8")
    newline = source_newline(text)
    changes = []

    old = with_newline(
        "            final PcbBoardHole[] holes;\n            final Rectangle[] holeBounds;", newline)
    new = with_newline(
        "            final PcbBoardHole[] holes;\n            final int prefixViaPrice;\n            final Rectangle[] holeBounds;", newline)
    text = replace_once(text, old, new, "BRANCH_PRICE_FIELD")
    changes.append("BranchSearch owns one final branch-frozen prefixViaPrice.")

    old = with_newline(
        "                this.holes=holes; holeBounds=bounds;\n"
        "                turnLowerBound=new TurnLowerBound(source.turnLowerBound,distance,owner.turnBoundWorkspace);\n"
        "                goalsTop=source.goalsTop; goalsBottom=source.goalsBottom;", newline)
    new = with_newline(
        "                this.holes=holes; holeBounds=bounds;\n"
        "                // Detached copies recompute against the new owner; non-60 explicit costs stay constant.\n"
        "                prefixViaPrice=PrefixViaRampSupport.effectivePrice(viaCost,holes.length,maximumVias);\n"
        "                turnLowerBound=new TurnLowerBound(source.turnLowerBound,distance,owner.turnBoundWorkspace);\n"
        "                goalsTop=source.goalsTop; goalsBottom=source.goalsBottom;\n"
        "                PrefixViaRampSupport.recordBranch(net,start.getPadId(),root.getPadId(),pass,\n"
        "                    holes.length,maximumVias,viaCost,prefixViaPrice,true);", newline)
    text = replace_once(text, old, new, "BRANCH_COPY_PRICE")
    changes.append("Detached branch copies recompute price from the copied owner scalar and frozen prefix.")

    old = with_newline(
        "                holes=layout.getHoles().toArray(new PcbBoardHole[0]);\n"
        "                holeBounds=new Rectangle[holes.length];", newline)
    new = with_newline(
        "                holes=layout.getHoles().toArray(new PcbBoardHole[0]);\n"
        "                prefixViaPrice=PrefixViaRampSupport.effectivePrice(viaCost,holes.length,maximumVias);\n"
        "                holeBounds=new Rectangle[holes.length];", newline)
    text = replace_once(text, old, new, "BRANCH_PREFIX_PRICE")
    changes.append("Normal branch price is computed once from its frozen hole prefix and request cap.")

    old = with_newline(
        "                for(int layer=0;layer<2;layer++) if(layer==primary || policy==Policy.FULLER_TWO_LAYER)\n"
        "                    offer(queue,best,new Node(cell(start),layer,4,0,layer==primary?0:policy.secondaryCost,\n"
        "                        heuristic(cell(start),4,layer),serial++,cells,null));\n"
        "                observer.check(pass);", newline)
    new = with_newline(
        "                for(int layer=0;layer<2;layer++) if(layer==primary || policy==Policy.FULLER_TWO_LAYER)\n"
        "                    offer(queue,best,new Node(cell(start),layer,4,0,layer==primary?0:policy.secondaryCost,\n"
        "                        heuristic(cell(start),4,layer),serial++,cells,null));\n"
        "                observer.check(pass);\n"
        "                PrefixViaRampSupport.recordBranch(net,start.getPadId(),root.getPadId(),pass,\n"
        "                    holes.length,maximumVias,viaCost,prefixViaPrice,false);", newline)
    text = replace_once(text, old, new, "BRANCH_OBSERVATION")
    changes.append("Actual normal BranchSearch construction records its frozen price while observation is active.")

    text = replace_once(text, "viaCost,fixedGraphEdges());",
                        "prefixViaPrice,fixedGraphEdges());", "RELAXED_BOUND_PRICE")
    changes.append("The branch relaxed VIA-distance bound uses the same frozen price.")

    text = replace_once(text, "current.cost+viaCost,heuristic(current.cell,4,1-current.layer)",
                        "current.cost+prefixViaPrice,heuristic(current.cell,4,1-current.layer)",
                        "TRANSITION_G_COST")
    changes.append("Actual transition expansion charges the frozen branch price.")

    require(text.count("current.cost+prefixViaPrice,heuristic(current.cell,4,1-current.layer)") == 1,
            "TRANSITION_G_COST_RESULT")
    require(text.count("prefixViaPrice,fixedGraphEdges());") == 1,
            "RELAXED_BOUND_PRICE_RESULT")
    return text.encode("utf-8"), changes


def run_git(repo, *args):
    try:
        return subprocess.check_output(["git", "-C", str(repo), *args],
                                       stderr=subprocess.STDOUT, text=True).strip()
    except subprocess.CalledProcessError as failure:
        raise ValueError("GIT_" + "_".join(args) + ":" + failure.output.strip())


def main(argv):
    require(len(argv) == 3, "USAGE: prepare_overlay.py <repoRoot> <NEW-output-root>")
    repo = checked_path(argv[1], "REPOSITORY")
    output_arg = Path(argv[2])
    require(output_arg.is_absolute(), "OUTPUT_PATH_MUST_BE_ABSOLUTE")
    output = output_arg.resolve(strict=False)
    temp_root = Path(tempfile.gettempdir()).resolve(strict=True)
    require(output != temp_root and temp_root in output.parents, "OUTPUT_MUST_BE_UNDER_OS_TEMP")
    require(not output.exists(), "OUTPUT_ROOT_ALREADY_EXISTS")
    require(repo not in output.parents and output not in repo.parents, "OUTPUT_REPOSITORY_OVERLAP")
    require(output.parent.exists(), "OUTPUT_PARENT_MISSING")
    for ancestor in (output.parent, *output.parent.parents):
        info = ancestor.lstat()
        require(not stat.S_ISLNK(info.st_mode) and
                not (getattr(info, "st_file_attributes", 0) & 0x400),
                "OUTPUT_PARENT_REPARSE")

    script_dir = Path(__file__).resolve().parent
    qr_root = script_dir.parent
    plan_path = checked_path(qr_root / "PLAN_R1.md", "PLAN")
    plan_raw = stable_read(plan_path, 1024 * 1024, "PLAN")
    require(sha(plan_raw) == PLAN_SHA256, "PLAN_PIN")
    router_path = checked_path(repo / SOURCE_REL, "ROUTER_SOURCE")
    router_raw = stable_read(router_path, MAX_INPUT_BYTES, "ROUTER_SOURCE")
    require(sha(router_raw) == SOURCE_SHA256, "ROUTER_SOURCE_PIN")

    support_path = checked_path(script_dir / "PrefixViaRampSupport.java", "SUPPORT_TEMPLATE")
    canary_path = checked_path(script_dir / "PrefixViaRampCanary.java", "CANARY_TEMPLATE")
    note_path = checked_path(script_dir / "SOURCE_NOTE_R1.md", "SOURCE_NOTE")
    builder_path = checked_path(__file__, "BUILDER")
    support_raw = stable_read(support_path, MAX_INPUT_BYTES, "SUPPORT_TEMPLATE")
    canary_raw = stable_read(canary_path, MAX_INPUT_BYTES, "CANARY_TEMPLATE")
    note_raw = stable_read(note_path, MAX_INPUT_BYTES, "SOURCE_NOTE")
    builder_raw = stable_read(builder_path, MAX_INPUT_BYTES, "BUILDER")

    require(run_git(repo, "rev-parse", "HEAD") == PARENT_HEAD, "PARENT_HEAD")
    require(run_git(repo, "branch", "--show-current") == PARENT_BRANCH, "PARENT_BRANCH")
    reported_repo = checked_path(run_git(repo, "rev-parse", "--show-toplevel"),
                                 "GIT_REPOSITORY_ROOT")
    require(reported_repo == repo, "REPOSITORY_ROOT")

    router_overlay, changes = overlay_router(router_raw)
    support_template = support_raw.decode("utf-8")
    require(support_template.count('private static final String MODE = "__MODE__";') == 1,
            "MODE_TEMPLATE_SEAM")
    canary_text = canary_raw.decode("utf-8")
    require("PREFIX_VIA_RAMP_CAPTURE_LIMIT" in canary_text and
            "PASS_PREFIX_VIA_RAMP_CANARY" in canary_text,
            "CANARY_MARKERS")

    mode_contents = {}
    for mode in ("BASE", "RAMP"):
        support = support_template.replace(
            'private static final String MODE = "__MODE__";',
            'private static final String MODE = "' + mode + '";', 1).encode("utf-8")
        mode_contents[mode] = {
            "router": router_overlay,
            "support": support,
            "canary": canary_raw,
        }

    original_lines = router_raw.decode("utf-8").splitlines(keepends=True)
    changed_lines = router_overlay.decode("utf-8").splitlines(keepends=True)
    readable = list(difflib.unified_diff(original_lines, changed_lines,
        fromfile=str(SOURCE_REL).replace("\\", "/"),
        tofile="overlay/PcbLayerRoutingPrototype.java", n=3))
    readable.append("\n--- PrefixViaRampSupport.java compile-time mode variants ---\n")
    readable.append("BASE: private static final String MODE = \"BASE\";\n")
    readable.append("RAMP: private static final String MODE = \"RAMP\";\n")
    readable_raw = "".join(readable).encode("utf-8")

    output.mkdir()
    for mode in ("BASE", "RAMP"):
        mode_dir = output / mode
        mode_dir.mkdir()
        names = {
            "router": "PcbLayerRoutingPrototype.java",
            "support": "PrefixViaRampSupport.java",
            "canary": "PrefixViaRampCanary.java",
        }
        for key, name in names.items():
            write_new(mode_dir / name, mode_contents[mode][key])
    write_new(output / "readable-patch.diff", readable_raw)

    files = []
    mode_files = {}
    for mode in ("BASE", "RAMP"):
        rows = {}
        for key, name in (("router", "PcbLayerRoutingPrototype.java"),
                          ("support", "PrefixViaRampSupport.java"),
                          ("canary", "PrefixViaRampCanary.java")):
            relative = mode + "/" + name
            data = (output / relative).read_bytes()
            row = {"path": relative, "bytes": len(data), "sha256": sha(data)}
            rows[key] = row
            files.append(row)
        mode_files[mode] = rows
    patch_row = {"path": "readable-patch.diff", "bytes": len(readable_raw),
                 "sha256": sha(readable_raw)}
    files.append(patch_row)
    manifest = {
        "schema": "tsj-prefix-via-ramp-overlay-manifest-v1",
        "parentHead": PARENT_HEAD,
        "parentBranch": PARENT_BRANCH,
        "repositoryRoot": str(repo),
        "plan": {"path": "PLAN_R1.md", "bytes": len(plan_raw), "sha256": sha(plan_raw)},
        "productionSource": {"path": str(SOURCE_REL).replace("\\", "/"),
                             "bytes": len(router_raw), "sha256": sha(router_raw)},
        "builder": {"path": "source/prepare_overlay.py", "bytes": len(builder_raw),
                    "sha256": sha(builder_raw)},
        "templates": {
            "support": {"path": "source/PrefixViaRampSupport.java", "bytes": len(support_raw),
                        "sha256": sha(support_raw)},
            "canary": {"path": "source/PrefixViaRampCanary.java", "bytes": len(canary_raw),
                       "sha256": sha(canary_raw)},
            "sourceNote": {"path": "source/SOURCE_NOTE_R1.md", "bytes": len(note_raw),
                           "sha256": sha(note_raw)},
        },
        "modeFiles": mode_files,
        "readablePatch": patch_row,
        "files": files,
        "treatment": {
            "BASE": "frozen branch VIA price is the actual owner scalar",
            "RAMP": "normal base-60 branch price is base + floor((long)base*min(H,M)/M), with M=0 returning base",
            "detachedCopy": "copy constructors preserve the owner's VIA base scalar and recompute from the copied prefix; explicit raised scalars remain constant",
            "capsChanged": False,
            "publicOrPlayerOption": False,
        },
        "sourceChanges": changes,
    }
    manifest_raw = (json.dumps(manifest,ensure_ascii=False,sort_keys=True,indent=2) + "\n").encode("utf-8")
    write_new(output / "overlay-manifest.json", manifest_raw)
    print(json.dumps({"status":"OVERLAY_PREPARED_NOT_RUN",
                      "outputRoot":str(output),
                      "overlayManifestSha256":sha(manifest_raw),
                      "BASE":mode_files["BASE"],"RAMP":mode_files["RAMP"],
                      "readablePatch":patch_row},ensure_ascii=False,sort_keys=True))


if __name__ == "__main__":
    main(sys.argv)
