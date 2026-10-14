import hashlib
import itertools
import json
import subprocess
from pathlib import Path

here = Path(__file__).resolve().parent
root = here.parents[4]
relative = "scripts/verify-current-contracts.ps1"
manifest = json.loads((root / "docs/task-evidence/PCB/nearest-vacancy-preference-r1/final-inputs.json").read_text(encoding="utf-8"))
expected = next(item["sha256"] for item in manifest["files"] if item["path"] == relative)
current = (root / relative).read_bytes()
blob = subprocess.check_output(["git", "cat-file", "blob", "HEAD:" + relative], cwd=str(root))
digest = lambda data: hashlib.sha256(data).hexdigest()
lf = current.replace(b"\r\n", b"\n")
crlf = lf.replace(b"\n", b"\r\n")

def split_lines(data):
    return data.splitlines(keepends=True)

def content_and_eol(line):
    if line.endswith(b"\r\n"):
        return line[:-2], b"\r\n"
    if line.endswith(b"\n"):
        return line[:-1], b"\n"
    if line.endswith(b"\r"):
        return line[:-1], b"\r"
    return line, b""

lines = split_lines(current)
line_parts = [content_and_eol(line) for line in lines]
blame = subprocess.check_output(
    ["git", "blame", "--line-porcelain", "HEAD", "--", relative], cwd=str(root)
).decode("utf-8", errors="replace").splitlines()
origins = {}
for line in blame:
    fields = line.split()
    if len(fields) >= 3 and len(fields[0]) == 40 and all(ch in "0123456789abcdef" for ch in fields[0]):
        try:
            final_line = int(fields[2]) - 1
        except ValueError:
            continue
        origins[final_line] = fields[0]

history = subprocess.check_output(
    ["git", "log", "--format=%H", "-30", "--", relative], cwd=str(root)
).decode("ascii").splitlines()
groups = [commit for commit in history if commit in set(origins.values())]
groups = groups[:16]
group_indices = {commit: [index for index, origin in origins.items() if origin == commit] for commit in groups}

matches = []
tested = 0
if digest(current) == expected:
    matches.append({"kind": "current", "sha256": digest(current)})
if digest(lf) == expected:
    matches.append({"kind": "all_lf", "sha256": digest(lf)})
if digest(crlf) == expected:
    matches.append({"kind": "all_crlf", "sha256": digest(crlf)})

# Test the likely mixed-checkout pattern: CRLF baseline with whole blame-origin
# groups using LF, first for the explicit recent PCB verifier insertions.
recent_candidates = [
    [index for index, (content, _) in enumerate(line_parts)
     if any(marker in content for marker in (
         b"PcbNearestVacancy", b"PcbSavedRoleWidth", b"PcbRoleWidth", b"PcbTraceWidth"))]
]
for indices in recent_candidates:
    candidate = b"".join(content + (b"\n" if index in set(indices) and eol else b"\r\n" if eol else b"")
                          for index, (content, eol) in enumerate(line_parts))
    tested += 1
    if digest(candidate) == expected:
        matches.append({"kind": "recent_registry_lines_lf", "lineNumbers": [i + 1 for i in indices], "sha256": digest(candidate)})

if groups:
    for mask in range(1 << len(groups)):
        lf_indices = set()
        selected = []
        for bit, commit in enumerate(groups):
            if mask & (1 << bit):
                selected.append(commit[:12])
                lf_indices.update(group_indices[commit])
        candidate = b"".join(content + ((b"\n" if index in lf_indices else b"\r\n") if eol else b"")
                              for index, (content, eol) in enumerate(line_parts))
        tested += 1
        if digest(candidate) == expected:
            matches.append({"kind": "blame_commit_groups_lf", "selectedCommits": selected,
                            "lineNumbers": sorted(index + 1 for index in lf_indices), "sha256": digest(candidate)})

copy_path = root / "docs/task-evidence/Q30/validated-factor-input/reproduction/consumed/verify-current-contracts.ps1"
copy = copy_path.read_bytes() if copy_path.exists() else b""
result = {
    "expectedSha256": expected,
    "currentSha256": digest(current),
    "allLfSha256": digest(lf),
    "allCrlfSha256": digest(crlf),
    "headBlobSha256": digest(blob),
    "currentNormalizesToHeadBlob": lf == blob.replace(b"\r\n", b"\n"),
    "currentNewlineCounts": {"crlf": current.count(b"\r\n"), "lf": current.count(b"\n"), "bareCr": current.count(b"\r") - current.count(b"\r\n")},
    "lineCount": len(lines),
    "blameCommitGroups": len(set(origins.values())),
    "searchedRecentBlameGroups": [commit[:12] for commit in groups],
    "testedCandidates": tested,
    "consumedCopySha256": digest(copy) if copy else None,
    "consumedCopyNormalizesToHeadBlob": bool(copy) and copy.replace(b"\r\n", b"\n") == blob.replace(b"\r\n", b"\n"),
    "matches": matches,
}
(here / "script-eol-recovery.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
print(json.dumps(result, indent=2))
