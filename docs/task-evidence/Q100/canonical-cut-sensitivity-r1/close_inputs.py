"""Close the frozen P7 source inventory without running graph analysis."""
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import time


def main():
    started = time.perf_counter_ns()
    spec_path, spec_sha, output = sys.argv[1:]
    raw = Path(spec_path).read_bytes()
    assert hashlib.sha256(raw).hexdigest() == spec_sha, "SPEC_HASH"
    spec = json.loads(raw)
    path = Path(__file__).resolve().with_name("run_cut_sensitivity.py")
    code = path.read_bytes()
    assert str(path) == spec["worker"], "WORKER_PATH"
    assert hashlib.sha256(code).hexdigest() == spec["workerSha256"], "WORKER_HASH"
    loader = importlib.util.spec_from_file_location("p7_final_source_close", path)
    helper = importlib.util.module_from_spec(loader)
    exec(compile(code, str(path), "exec"), helper.__dict__)
    checked = helper.load_spec(spec_path, spec_sha, full=True)
    inventory = helper.json_bytes(helper.pin(checked, checked["sourceManifest"], 4194304))
    production = [r for r in inventory["files"] if r["path"].startswith("src/")
                  and r["path"].endswith(".java")]
    assert len(production) == 850 and len(inventory["files"]) == 1084, "SOURCE_CENSUS"
    assert helper.digest(helper.stable(spec_path, 4194304)) == spec_sha, "SPEC_POST"
    assert helper.digest(helper.stable(str(path), 4194304)) == spec["workerSha256"], "WORKER_POST"
    extras = []
    for filename in ("host-negative-controls.ps1", "reader-negative-controls.ps1"):
        data = helper.stable(str(path.with_name(filename)), 4194304)
        extras.append(dict(path=filename, bytes=len(data), sha256=helper.digest(data)))
    result = dict(schema="tsj-canonical-cut-final-source-v1",
                  status="PASS_SOURCE_CLOSURE_ONLY", specSha256=spec_sha,
                  sourceManifestSha256=checked["sourceManifestSha256"], sourceRows=1084,
                  productionFilesUnchanged=850, pinnedFiles=len(checked["files"]),
                  controls=extras, elapsedNanos=time.perf_counter_ns() - started)
    payload = helper.encoded(result)
    with Path(output).open("xb") as stream:
        stream.write(payload)
    sys.stdout.buffer.write(payload)


if __name__ == "__main__":
    main()
