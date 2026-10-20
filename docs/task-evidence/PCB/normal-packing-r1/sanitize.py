"""Known-prefix privacy replacements after the named normal phase exits."""
import hashlib
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
state = json.loads((HERE / "baseline-local-state.json").read_text(encoding="utf-8-sig"))
COPY = Path(state["scratch"]).resolve()
phase = sys.argv[1]
assert phase in ("baseline", "unit", "routes")
assert (HERE / (phase + "-timing.json")).is_file()
assert json.loads((HERE / (phase + "-process-cleanup.json")).read_text(
    encoding="utf-8-sig"))["status"] == "PASS"

known = [(str(COPY), "[TASK_COPY]"), (str(ROOT), "[WORKTREE]")]
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
                "scope": "Known baseline COPY/worktree/JDK prefixes only; "
                         "idempotent status/assertion/timing preservation",
                "files": rows}, indent=2) + "\n", encoding="utf-8")
print("PASS normal-packing named-phase known-prefix-only privacy audit")
