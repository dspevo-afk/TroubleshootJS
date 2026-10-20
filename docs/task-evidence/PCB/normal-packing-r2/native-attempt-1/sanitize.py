"""Known-prefix privacy replacements after the named r2 phase exits."""
import hashlib
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
R1 = HERE.parent / "normal-packing-r1"
state = json.loads((R1 / "baseline-local-state.json").read_text(
    encoding="utf-8-sig"))
OLD_COPY = Path(state["scratch"]).resolve()
if not OLD_COPY.name.startswith("tsj-normal-packing-baseline-"):
    raise RuntimeError("r1 baseline COPY prefix is not task-owned")
phase = sys.argv[1]
assert phase in ("unit", "routes")
assert (HERE / (phase + "-timing.json")).is_file()
assert json.loads((HERE / (phase + "-process-cleanup.json")).read_text(
    encoding="utf-8-sig"))["status"] == "PASS"

known = [(str(ROOT), "[WORKTREE]"), (str(OLD_COPY), "[BASELINE_COPY]")]
if len(sys.argv) > 2:
    known.append((str(Path(sys.argv[2]).resolve()), "[JDK8]"))

rows = []
for name in (phase + ".txt", phase + "-receipt.txt"):
    path = HERE / name
    if not path.exists():
        continue
    raw = path.read_bytes()
    text = raw.decode("utf-8-sig")
    replacements = []
    for prefix, label in known:
        count = 0
        forms = sorted({prefix, prefix.replace("\\", "/"),
                        json.dumps(prefix)[1:-1]}, key=len, reverse=True)
        for form in forms:
            count += text.count(form)
            text = text.replace(form, label)
        replacements.append({"prefixKind": label, "count": count})
    if any(item["count"] for item in replacements):
        path.write_bytes(text.encode("utf-8"))
    rows.append({"file": name, "rawSha256": hashlib.sha256(raw).hexdigest(),
                 "sanitizedSha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                 "replacements": replacements})

(HERE / (phase + "-log-sanitization.json")).write_text(
    json.dumps({"status": "PASS",
                "scope": "Known worktree/old baseline COPY/JDK prefixes only; "
                         "idempotent status/assertion/timing preservation",
                "files": rows}, indent=2) + "\n", encoding="utf-8")
print("PASS normal-packing-r2 named-phase known-prefix-only privacy audit")
