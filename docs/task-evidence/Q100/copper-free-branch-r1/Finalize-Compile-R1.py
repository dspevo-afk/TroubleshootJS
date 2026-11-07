"""Bind a returned source compile to the exact class tree selected by the JVM."""
import sys
if not (sys.flags.isolated and sys.dont_write_bytecode):
    raise SystemExit("ISOLATION_REQUIRED")
import hashlib
import json
import os
import stat
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def sha(body):
    return hashlib.sha256(body).hexdigest()


def data(value):
    return (json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False) + "\n").encode()


def file_record(path):
    path = Path(path).resolve(strict=True)
    body = path.read_bytes()
    return {"path": str(path), "bytes": len(body), "sha256": sha(body)}


def need(condition, code):
    if not condition:
        raise RuntimeError(code)


def main():
    need(len(sys.argv) == 4, "Expected stage compile-call-dir original-manifest-sha")
    stage = Path(sys.argv[1]).resolve(strict=True)
    call = Path(sys.argv[2]).resolve(strict=True)
    need(stage.parent == ROOT and call.parent == ROOT and stage != call, "OWNERSHIP")
    original = (stage / "source-manifest.json").read_bytes()
    need(sha(original) == sys.argv[3], "INPUT_MANIFEST_CHANGED")
    manifest = json.loads(original)
    need(manifest["schema"] == "tsj-copper-free-branch-source-v1", "INPUT_SCHEMA")
    for record in manifest["sources"] + manifest["tools"]:
        need(file_record(record["path"]) == record, "COMPILE_INPUT_CHANGED")
    need(sha((ROOT / "PLAN_R1.md").read_bytes()) == manifest["planSha256"], "PLAN_CHANGED")
    parent_raw = (call / "parent.json").read_bytes()
    parent = json.loads(parent_raw)
    process = parent["process"]
    need(process == json.loads((call / "process.json").read_bytes()), "PROCESS_COPY")
    request_raw = (stage / "request.json").read_bytes()
    request = json.loads(request_raw)
    need(request == json.loads((call / "request.json").read_bytes()), "COMPILE_REQUEST_COPY")
    need(process["FilePath"] == request["FilePath"] and process["Arguments"] == request["Arguments"], "COMPILE_PROCESS_SELECTION")
    need(process["ExitCode"] == 0 and process["TerminationProven"] is True, "COMPILE_EXIT")
    need(0 < parent["elapsedMs"] <= 60000 and request["LimitMs"] == 60000, "COMPILE_WALL_BOUND")
    for name, field in (("stdout.txt", "Stdout"), ("stderr.txt", "Stderr")):
        need((call / name).read_bytes() == process[field].encode("utf-8"), "COMPILE_STREAM_COPY")
    for field in ("StdoutLog", "StderrLog", "ProcessRoot"):
        need(not Path(process[field]).exists(), "COMPILE_RESOURCE_REMAINS")
    classes = stage / "classes"
    records = []
    total_bytes = 0
    for directory, directories, files in os.walk(classes, followlinks=False):
        for name in directories + files:
            info = (Path(directory) / name).lstat()
            need(not stat.S_ISLNK(info.st_mode) and not getattr(info, "st_file_attributes", 0) & 0x400, "CLASS_REPARSE")
        for name in files:
            path = Path(directory) / name
            need(path.suffix == ".class", "FOREIGN_CLASS_FILE")
            record = file_record(path)
            total_bytes += record["bytes"]
            need(total_bytes <= 256 * 1024 * 1024 and len(records) < 10000, "CLASS_TREE_BOUND")
            record["path"] = path.relative_to(classes).as_posix()
            records.append(record)
    records.sort(key=lambda row: row["path"])
    need(bool(records), "EMPTY_CLASS_TREE")
    inventory = {"schema": "tsj-copper-free-branch-classes-v1", "files": records, "count": len(records), "bytes": total_bytes}
    inventory_raw = data(inventory)
    inventory_path = stage / "class-inventory.json"
    with inventory_path.open("xb") as stream:
        stream.write(inventory_raw)
    wire = file_record(ROOT / "source/WIRE_R1.md")
    manifest["wire"] = wire
    manifest["build"] = {"compileParentPath": str(call / "parent.json"), "compileParentSha256": sha(parent_raw), "compilerRequestPath": str(stage / "request.json"), "compilerRequestSha256": sha(request_raw), "classesRoot": str(classes), "classInventoryPath": str(inventory_path), "classInventorySha256": sha(inventory_raw), "classCount": len(records)}
    final_raw = data(manifest)
    with (stage / "run-manifest.json").open("xb") as stream:
        stream.write(final_raw)
    print(json.dumps({"status": "COMPILE_BOUND_ONLY", "manifestSha256": sha(final_raw), "classCount": len(records), "classInventorySha256": sha(inventory_raw), "wireSha256": wire["sha256"]}, separators=(",", ":")))


if __name__ == "__main__":
    main()
