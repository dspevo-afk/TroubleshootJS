"""Freeze/audit source, verifier and contract bytes without host paths."""
import argparse
import hashlib
import json
from pathlib import Path


def collect(root):
    paths = set()
    prior = root / "docs/task-evidence/PCB/nearest-vacancy-preference-r1/final-inputs.json"
    for entry in json.loads(prior.read_text(encoding="utf-8-sig"))["files"]:
        paths.add(root / entry["path"].replace("\\", "/"))
    for directory in ("src", "scripts", "tests/contracts"):
        for path in (root / directory).rglob("*"):
            if path.is_file() and "__pycache__" not in path.parts and path.suffix != ".pyc":
                paths.add(path)
    paths.update((root / ".tools/gwt-2.7.0").glob("*.jar"))
    return [{"path": path.relative_to(root).as_posix(),
             "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
            for path in sorted(paths, key=lambda p: p.as_posix().lower())]


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("freeze", "audit"))
    parser.add_argument("snapshot", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[4]
    actual = collect(root)
    if args.mode == "freeze":
        args.snapshot.write_text(json.dumps({"files": actual}, indent=2) + "\n", encoding="utf-8")
        print("FROZEN inputs=" + str(len(actual)))
    else:
        expected = json.loads(args.snapshot.read_text(encoding="utf-8-sig"))["files"]
        before = {entry["path"]: entry["sha256"] for entry in expected}
        after = {entry["path"]: entry["sha256"] for entry in actual}
        changed = [path for path in sorted(set(before) | set(after))
                   if before.get(path) != after.get(path)]
        result = {"status": "PASS" if not changed else "FAIL", "changedPaths": changed,
                  "frozenInputCount": len(before), "actualInputCount": len(after),
                  "scope": "Source/scripts/contracts/GWT jars; generated war provenance checked separately"}
        if args.output:
            args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(result))
        raise SystemExit(1 if changed else 0)
