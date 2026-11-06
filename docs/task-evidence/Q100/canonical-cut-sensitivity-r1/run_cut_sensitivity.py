"""Bounded adapter for the frozen, offline directed-cut sensitivity study."""
from __future__ import annotations

import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path
import re
import stat
import sys
import tempfile
import time

PARENT = "23f94f8f4ea8025fe24475fabcb194840e472fd8"
P6_PINS = "b640587f9cb1ac3f3ec8feff85940f4b7a9c8fda37570b50bfa30c39fd067771"
P6_READER = "1d58c06ecb3581ad7d8c9eef1dfcd653d8ba1e5b797b2dc7cda76b2ed621a96e"
P6_QUAL = "d7f39f1a77e302e9e883b4aedbdeb9c416a93cf4cfa85029710b04da5b25d692"
GRAPH_SHA = "ff6466d21600fc07de94a2cc35ae9a369558219207d0bb2734dbda27ab1e4f0a"
PREFIX_SHA = "6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb"
CODEC_SHA = "c146cef4336d96eb6b6597569115d746d6a1fffe3b0c2ee2759d0e259d8b2a00"
VIA_SHA = "924edc27078935e18da981117a09011989233afafff2345402c7bd5c1105d9b1"
LIMITS = dict(workNanos=90000000000, groupNanos=5000000000, groups=640,
              batchEntries=4096, cloneEntries=1048576, observationUnits=32768,
              artifactBytes=8388608, resultBytes=65536, lineBytes=65536,
              readChunkBytes=32768, specBytes=4194304, parentMs=180000,
              workerMs=180000, cleanupMs=120000, outerMs=1800000)
FIELDS = set("schema parent repositoryRoot sourceManifest sourceManifestSha256 parentReader parentReaderSha256 parentPins parentPinsSha256 parentRun parentQualification parentQualificationSha256 transport transportSha256 blockers blockersSha256 stages stagesSha256 cutInput cutInputSha256 plan planSha256 protocol protocolSha256 schemaDocument schemaDocumentSha256 analysisModule analysisModuleSha256 worker workerSha256 tests testsSha256 graphOracle graphOracleSha256 viaOracle viaOracleSha256 captureReader captureReaderSha256 selectedPython selectedPythonSha256 selectedPowerShell selectedPowerShellSha256 files limits addendum addendumSha256".split())
ACCEPTANCE = dict(route=False, layout=False, electrical=False, q100=False,
                  productionFeasibility="UNKNOWN", newGeneration=False, newP07Search=False)
LIMITATIONS = ["CONDITIONAL_MASK_OPENINGS_ONLY", "NOT_WHOLE_NET_REMOVAL",
               "NOT_UNIQUE_TRACE_OWNERSHIP", "NOT_PHYSICAL_ROUTE",
               "NO_PRODUCTION_ADOPTION", "NO_NEW_ELECTRICAL_OR_POPULATION_QUALIFICATION"]
SCOPE = "CANONICAL_BASE_DIRECTED_CUT_SENSITIVITY_ONLY"
HEX = re.compile(r"[0-9a-f]{64}\Z")


class Rejected(ValueError):
    pass


def need(ok, code):
    if not ok:
        raise Rejected(code)


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def _pairs(rows):
    out = {}
    for key, value in rows:
        need(key not in out, "JSON_DUPLICATE_KEY")
        out[key] = value
    return out


def json_bytes(raw):
    need(type(raw) is bytes and not raw.startswith(b"\xef\xbb\xbf"), "JSON_BYTES")
    def number(text):
        value = float(text)
        need(math.isfinite(value), "JSON_NONFINITE")
        return value
    def constant(_):
        raise Rejected("JSON_NONFINITE")
    try:
        return json.loads(raw.decode("utf-8", "strict"), object_pairs_hook=_pairs,
                          parse_float=number, parse_constant=constant)
    except (UnicodeError, json.JSONDecodeError):
        raise Rejected("JSON_INVALID") from None


def encoded(value):
    return (json.dumps(value, sort_keys=True, ensure_ascii=True,
                       allow_nan=False, separators=(",", ":")) + "\n").encode("ascii")


def absolute(value):
    need(type(value) is str and Path(value).is_absolute(), "ABSOLUTE_PATH_REQUIRED")
    need(os.path.normpath(value) == value, "CANONICAL_PATH_REQUIRED")
    path = Path(value)
    for item in (path, *path.parents):
        if item.exists():
            meta = item.lstat()
            need(not item.is_symlink() and not getattr(meta, "st_file_attributes", 0) & 1024,
                 "REPARSE_PATH")
    return path


def stable(path, limit=8388608):
    path = absolute(os.fspath(path))
    before = path.stat()
    need(stat.S_ISREG(before.st_mode) and 0 <= before.st_size <= limit, "FILE_SIZE_OR_TYPE")
    common = lambda x: (x.st_dev, x.st_ino, x.st_size, x.st_mtime_ns, x.st_nlink)
    within_api = lambda x: (x.st_ctime_ns, getattr(x, "st_birthtime_ns", None),
                            getattr(x, "st_file_attributes", None))
    chunks = []
    with path.open("rb") as stream:
        opened = os.fstat(stream.fileno())
        need(common(opened) == common(before), "FILE_OPEN_CHANGED")
        total = 0
        while True:
            part = stream.read(32768)
            if not part:
                break
            chunks.append(part)
            total += len(part)
            need(total <= limit, "FILE_GROWTH")
        closed = os.fstat(stream.fileno())
    absolute(str(path))
    after = path.stat()
    need(common(before) == common(opened) == common(closed) == common(after)
         and within_api(before) == within_api(after)
         and within_api(opened) == within_api(closed), "FILE_CHANGED")
    raw = b"".join(chunks)
    need(len(raw) == before.st_size, "FILE_LENGTH")
    return raw


def pin(spec, path, maximum=8388608):
    rows = [r for r in spec["files"] if r["path"] == os.fspath(path)]
    need(len(rows) == 1, "UNBOUND_INPUT")
    raw = stable(path, maximum)
    need(len(raw) == rows[0]["bytes"] and digest(raw) == rows[0]["sha256"], "INPUT_HASH")
    return raw


def module(path, sha, name):
    raw = stable(path, 4194304)
    need(digest(raw) == sha, "MODULE_HASH")
    found = importlib.util.spec_from_file_location(name, path)
    obj = importlib.util.module_from_spec(found)
    sys.modules[name] = obj
    exec(compile(raw, os.fspath(path), "exec"), obj.__dict__)
    return obj


def load_spec(path, sha, full=False):
    need(type(sha) is str and HEX.fullmatch(sha), "SPEC_SHA_TYPE")
    raw = stable(path, LIMITS["specBytes"])
    need(digest(raw) == sha, "SPEC_HASH")
    spec = json_bytes(raw)
    need(type(spec) is dict and set(spec) == FIELDS, "SPEC_FIELDS")
    need(raw == encoded(spec), "SPEC_CANONICAL_EOF")
    need(spec["schema"] == "tsj-canonical-cut-sensitivity-input-v1" and spec["parent"] == PARENT,
         "SPEC_IDENTITY")
    need(encoded(spec["limits"]) == encoded(LIMITS), "SPEC_LIMITS")
    need(spec["parentReaderSha256"] == P6_READER and spec["parentPinsSha256"] == P6_PINS
         and spec["parentQualificationSha256"] == P6_QUAL
         and spec["graphOracleSha256"] == CODEC_SHA and spec["viaOracleSha256"] == VIA_SHA,
         "INHERITED_PIN")
    need(spec["selectedPythonSha256"] == "ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec"
         and spec["selectedPowerShellSha256"] == "60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f",
         "SELECTED_TOOL_PIN")
    for key in FIELDS - {"schema", "parent", "files", "limits"}:
        if key.endswith("Sha256"):
            need(type(spec[key]) is str and HEX.fullmatch(spec[key]), "PIN_SHA_TYPE")
        else:
            absolute(spec[key])
    need(type(spec["files"]) is list and 0 < len(spec["files"]) <= 512, "PIN_CENSUS")
    seen = set()
    for row in spec["files"]:
        need(type(row) is dict and set(row) == {"path", "bytes", "sha256"}, "PIN_FIELDS")
        absolute(row["path"])
        norm = os.path.normcase(row["path"])
        need(norm not in seen and type(row["bytes"]) is int and 0 < row["bytes"] <= 8388608
             and type(row["sha256"]) is str and HEX.fullmatch(row["sha256"]), "PIN_ROW")
        seen.add(norm)
    for key in FIELDS:
        if key.endswith("Sha256"):
            path_key = key[:-6]
            found = [r for r in spec["files"] if r["path"] == spec[path_key]]
            need(len(found) == 1 and found[0]["sha256"] == spec[key], "ROLE_PIN")
    home = Path(spec["worker"]).parent
    for role, filename in (("worker", "run_cut_sensitivity.py"),
                           ("analysisModule", "cut_sensitivity_core.py"),
                           ("tests", "test_cut_sensitivity.py"),
                           ("protocol", "PROTOCOL_R1.md"), ("schemaDocument", "SCHEMA_R1.md"),
                           ("addendum", "SCHEMA_ADDENDUM_R2.md")):
        need(Path(spec[role]) == home / filename, "ROLE_PATH")
    for filename in ("SOURCE_CENSUS_R3.md", "read_cut_sensitivity.py", "run-cut-sensitivity.ps1"):
        pin(spec, str(home / filename), 4194304)
    oracle_dir = Path(spec["captureReader"]).parent / "oracles-r1"
    need(Path(spec["graphOracle"]) == oracle_dir / "read_fixed_prefix_graph.py"
         and Path(spec["viaOracle"]) == oracle_dir / "fixed_prefix_minimum_vias.py",
         "ORACLE_ROLE_PATH")
    p6_home = Path(spec["parentReader"]).parent
    need(Path(spec["parentReader"]).name == "read_stages.py"
         and Path(spec["parentPins"]) == p6_home / "input-pins-r6.json"
         and Path(spec["parentQualification"]) == p6_home / "qualification-r6.json"
         and Path(spec["parentRun"]) == p6_home / "observe-r6", "PARENT_ROLE_PATH")
    p6_pins = json_bytes(pin(spec, spec["parentPins"], 4194304))
    p5_raw = pin(spec, p6_pins["p5InputPinsPath"], 4194304)
    need(digest(p5_raw) == p6_pins["p5InputPinsSha256"], "P5_PIN_AUTHORITY")
    p5_pins = json_bytes(p5_raw)
    prior_path = str(Path(spec["captureReader"]).parent / "qualification-r4.json")
    prior_rows = [row for row in spec["files"] if row["path"] == prior_path]
    need(p5_pins["p4QualificationPath"] == prior_path and len(prior_rows) == 1
         and prior_rows[0]["sha256"] == p5_pins["p4QualificationSha256"],
         "CONTROL_QUALIFICATION_ROLE")
    if full:
        for row in spec["files"]:
            pin(spec, row["path"])
        inventory = json_bytes(pin(spec, spec["sourceManifest"], 4194304))
        need(set(inventory) == {"schema", "parent", "repositoryRoot", "parentSourceRoot", "files"}
             and inventory["schema"] == "tsj-canonical-cut-source-inventory-v1"
             and inventory["parent"] == PARENT and inventory["repositoryRoot"] == spec["repositoryRoot"],
             "SOURCE_INVENTORY")
        old = json_bytes(pin(spec, spec["parentPins"], 4194304))
        need(inventory["files"] == old["sourceRows"] and len(inventory["files"]) == 1084
             and inventory["parentSourceRoot"] == old["repositoryRoot"], "SOURCE_PARENT")
        for root in (inventory["repositoryRoot"], inventory["parentSourceRoot"]):
            for row in inventory["files"]:
                relative = Path(row["path"])
                need(not relative.is_absolute() and ".." not in relative.parts, "SOURCE_ESCAPE")
                data = stable(str(Path(root) / relative), 67108864)
                need(len(data) == row["bytes"] and digest(data) == row["sha256"], "SOURCE_HASH")
    return spec


def validate_parent_receipt(spec, path, sha):
    raw = stable(path, 65536)
    need(digest(raw) == sha, "PARENT_RECEIPT_HASH")
    receipt = json_bytes(raw)
    ticks = receipt.get("ProcessStartTicks")
    need((type(ticks) is int and 0 < ticks <= 9223372036854775807)
         or (type(ticks) is str and re.fullmatch(r"[1-9][0-9]{0,18}", ticks)
             and int(ticks) <= 9223372036854775807), "PARENT_START_TICKS")
    args = ["-B", spec["parentReader"], spec["parentRun"], spec["parentPins"],
            spec["parentPinsSha256"], "OBSERVE"]
    need(receipt["FilePath"] == spec["selectedPython"] and receipt["Arguments"] == args
         and receipt["ExitCode"] == 0 and type(receipt["ExitCode"]) is int
         and receipt["TerminationProven"] is True and receipt["Stderr"] == ""
         and type(receipt["ProcessId"]) is int and receipt["ProcessId"] > 0, "PARENT_PROCESS")
    result = json_bytes(receipt["Stdout"].encode("utf-8"))
    need(result["status"] == "PASS_STAGE_OBSERVATIONS_ONLY" and result["mode"] == "OBSERVE"
         and result["inputPinsSha256"] == P6_PINS
         and result["operation"]["graphSha256"] == GRAPH_SHA
         and result["stageTotals"]["queryCount"] == 22
         and not any(result["acceptance"].values()), "PARENT_RESULT")
    return result


def load_oracles(spec):
    codec = module(spec["graphOracle"], CODEC_SHA, "read_fixed_prefix_graph")
    via = module(spec["viaOracle"], VIA_SHA, "fixed_prefix_minimum_vias")
    need(via.oracle is codec, "ORACLE_OBJECT_IDENTITY")
    return codec, via


def load_inputs(spec, budget=None):
    def do(phase, action):
        return action() if budget is None else budget.call(phase, action)
    codec, via = do("decode", lambda: load_oracles(spec))
    capture = do("decode", lambda: module(spec["captureReader"], spec["captureReaderSha256"],
                                          "p7_pinned_capture"))
    need(capture.graph_oracle is codec and capture.via_oracle is via, "CAPTURE_ORACLE_IDENTITY")
    raw = do("transport", lambda: pin(spec, spec["transport"]))
    decoded = do("decode", lambda: capture.decode_transport(raw, True))
    graph = decoded["graph"]
    blockers = do("decode", lambda: json_bytes(pin(spec, spec["blockers"], 65536)))
    stages = do("decode", lambda: json_bytes(pin(spec, spec["stages"], 65536)))
    cut = do("decode", lambda: json_bytes(pin(spec, spec["cutInput"], 65536)))
    prior_path = str(Path(spec["captureReader"]).parent / "qualification-r4.json")
    prior = do("decode", lambda: json_bytes(pin(spec, prior_path, 1048576)))["zeroCapacityResult"]
    need(prior["graph"]["graphSha256"] == GRAPH_SHA
         and prior["status"] == "PASS_ZERO_VIA_DIAGNOSIS_ONLY", "CONTROL_PARENT_DIAGNOSIS")
    index_path = str(Path(spec["parentRun"]) / "data" / "canonical-stage-index.json")
    index = do("decode", lambda: json_bytes(pin(spec, index_path, 65536)))
    need(index["mode"] == "OBSERVE" and index["sourceManifestSha256"] == P6_PINS
         and index["runId"] == decoded["header"]["runId"] == blockers["runId"] == stages["runId"],
         "CAPTURE_RUN_ID")
    for key, role in (("transport", "transport"), ("attribution", "blockers"), ("stages", "stages")):
        ref = index[key]
        need(Path(spec[role]) == Path(index_path).parent / ref["path"]
             and ref["sha256"] == spec[role + "Sha256"], "CAPTURE_ARTIFACT_BINDING")
    need(digest(decoded["graphBytes"]) == GRAPH_SHA
         and blockers["graphSha256"] == stages["graphSha256"] == cut["graphSha256"] == GRAPH_SHA
         and blockers["prefixGeometrySha256"] == stages["prefixGeometrySha256"]
         == cut["prefixGeometrySha256"] == PREFIX_SHA
         and (graph.width, graph.height, graph.cells, graph.state_count) == (478, 309, 147702, 295404)
         and len(graph.starts) == 2, "GRAPH_IDENTITY")
    need(len(cut["cutQueries"]) == len(blockers["observations"]) == len(stages["queries"]) == 22,
         "CUT_CENSUS")
    openings, counts, geometry = [], {"B_SENSE": 0, "B_REF": 0}, {"LEGAL_MOVE_REJECTED": 0, "TRAVERSE_REJECTED": 0}
    for ordinal, (q, b, s) in enumerate(zip(cut["cutQueries"], blockers["observations"], stages["queries"])):
        fields = ("ordinal", "layer", "fromState", "toState", "bit")
        need(all(type(q[k]) is int and type(b[k]) is int and type(s[k]) is int
                 and q[k] == b[k] == s[k] for k in fields)
             and q["ordinal"] == ordinal and b["permitsStep"] is False and s["permitsStep"] is False,
             "CUT_QUERY_BINDING")
        selected = b["classification"] == "FOREIGN_OCCUPANCY_REJECTION_RECORDED"
        owner = b["targetClearanceNet"]
        if selected:
            need(owner in counts and b["routerCongestionDelta"] == b["workCongestionDelta"] == 1
                 and s["selected"] is False and s["events"] == [], "CUT_FOREIGN")
            counts[owner] += 1
        else:
            need(b["classification"] == "BLOCKED_WITHOUT_FOREIGN_OCCUPANCY_REJECTION"
                 and b["routerCongestionDelta"] == b["workCongestionDelta"] == 0
                 and s["selected"] is True and len(s["events"]) == 1
                 and s["events"][0]["stage"] in geometry, "CUT_GEOMETRY")
            geometry[s["events"][0]["stage"]] += 1
        openings.append(tuple(q[k] for k in fields) + (owner, selected))
    need(counts == {"B_SENSE": 4, "B_REF": 4} and geometry ==
         {"LEGAL_MOVE_REJECTED": 9, "TRAVERSE_REJECTED": 5}, "CUT_TOTALS")
    context = dict(codec=codec, via=via, capture=capture, index=index, decoded=decoded,
                   acceptedMinimumVia=prior["qualificationMinimumVia"], zeroViaBfs=prior["zeroViaBfs"],
                   graph=dict(rawSha256=GRAPH_SHA, canonicalSha256=graph.graph_sha256,
                              prefixGeometrySha256=PREFIX_SHA, width=graph.width, height=graph.height,
                              cells=graph.cells, states=graph.state_count, starts=list(graph.starts),
                              goalCount=sum(graph.goals), initialFacesMask=3),
                   cut=dict(queryCount=22, openableCount=8, geometricCount=14, ownerCounts=counts,
                            legalMoveRejected=9, traverseRejected=5,
                            ordinalsSha256=digest(encoded([list(row) for row in openings]))))
    return graph, tuple(openings), context


def _canary(core, codec, via, budget):
    graph = budget.call("codec", lambda: codec.validate_payload(dict(
        protocol=codec.GRAPH_PROTOCOL, model=codec.GRAPH_MODEL, caseId="P7_LITERAL_CANARY",
        width=2, height=2, primary=0, steps=[0] * 8,
        vias=[False] * 4, goals=[False, True] + [False] * 6, starts=[0])))
    openings = ((0, 0, 0, 1, 2, "A", True), (1, 0, 0, 2, 4, "B", True))
    cases = core.analyze_graph_cases(graph, openings, ("A", "B"), codec, via, budget)
    need([row["bfs"]["reachable"] for row in cases] == [False, True, False, True], "CANARY_EXPECTATION")
    need(graph.steps == (0,) * 8 and [row["changedBitCount"] for row in cases] == [0, 1, 1, 2],
         "CANARY_PRESERVATION")
    return 2


def main(argv=None):
    worker_started = time.perf_counter_ns()
    args = list(sys.argv[1:] if argv is None else argv)
    phase, budget = "BOOTSTRAP", None
    try:
        need(len(args) == 6 and args[0] in ("CANARY", "ANALYZE"), "ARGUMENTS")
        mode, spec_path, sha, output, parent_path, parent_sha = args
        # Bootstrap is covered by the host's separately measured residual upper.
        first = load_spec(spec_path, sha)
        core = module(first["analysisModule"], first["analysisModuleSha256"], "p7_cut_core")
        budget = core.WorkBudget()
        phase = "SPEC"
        spec = budget.call("decode", lambda: load_spec(spec_path, sha))
        source_start = time.perf_counter_ns()
        roles = ("analysisModule", "worker", "tests", "graphOracle", "viaOracle", "captureReader",
                 "protocol", "schemaDocument", "addendum", "sourceManifest", "parentPins",
                 "parentReader", "parentQualification", "plan", "selectedPython", "selectedPowerShell")
        for key in roles:
            maximum = 67108864 if key.startswith("selected") else 8388608
            budget.call("hash", lambda key=key, maximum=maximum: pin(spec, spec[key], maximum))
        source_nanos = time.perf_counter_ns() - source_start
        need(Path(__file__) == Path(spec["worker"]), "WORKER_SELF_PATH")
        out = absolute(output)
        need(out.is_dir() and not any(out.iterdir()), "OUTPUT_NOT_EMPTY")
        need(Path(tempfile.gettempdir()) in out.parents, "OUTPUT_NOT_TEMP")
        inventory = budget.call("decode", lambda: json_bytes(pin(spec, spec["sourceManifest"], 4194304)))
        for root in (spec["repositoryRoot"], inventory["parentSourceRoot"], str(Path(spec["worker"]).parent)):
            need(Path(root) != out and Path(root) not in out.parents, "OUTPUT_IN_SOURCE")
        phase = "ANALYSIS"
        if mode == "ANALYZE":
            parent = budget.call("decode", lambda: validate_parent_receipt(spec, parent_path, parent_sha))
            graph, openings, context = load_inputs(spec, budget)
            cases = core.analyze_graph_cases(graph, openings, ("B_SENSE", "B_REF"),
                                             context["codec"], context["via"], budget)
            need(cases[0]["bfs"]["reachedStates"] == 12 and cases[0]["bfs"]["edgesExamined"] == 23
                 and cases[0]["minimumVias"]["minimumViaCount"] == context["acceptedMinimumVia"]["minimumViaCount"]
                 and cases[0]["minimumVias"]["termination"] == context["acceptedMinimumVia"]["termination"]
                 and cases[0]["minimumVias"]["reachable"] == context["acceptedMinimumVia"]["reachable"], "CONTROL_PARITY")
            parent_value = dict(qualificationSha256=P6_QUAL, inputPinsSha256=P6_PINS,
                                readerSha256=P6_READER, runId=parent["runId"],
                                receiptSha256=parent_sha, status=parent["status"])
            graph_value, cut_value, assertions = context["graph"], context["cut"], None
        else:
            need(parent_path == parent_sha == "-", "CANARY_PARENT_SENTINEL")
            codec, via = budget.call("decode", lambda: load_oracles(spec))
            assertions = _canary(core, codec, via, budget)
            cases, parent_value, graph_value, cut_value = [], None, None, None
        source_after_start = time.perf_counter_ns()
        for key in roles:
            maximum = 67108864 if key.startswith("selected") else 8388608
            budget.call("hash", lambda key=key, maximum=maximum: pin(spec, spec[key], maximum))
        source_nanos += time.perf_counter_ns() - source_after_start
        source = {key + "Sha256": spec[key + "Sha256"] for key in
                  ("analysisModule", "worker", "tests", "graphOracle", "viaOracle", "captureReader")}
        source.update(stableBefore=True, stableAfter=True)
        work = dict(analysis=budget.finish(), prerequisiteWallMs=None, workerElapsedNanos=0,
                    sourceVerificationNanos=source_nanos, serializationNanos=0, casesRun=len(cases))
        if mode == "CANARY":
            work["canaryAssertions"] = assertions
        result = dict(schema="tsj-canonical-cut-sensitivity-result-v1",
                      status=("PASS_CONDITIONAL_CUT_SENSITIVITY_ONLY" if mode == "ANALYZE"
                              else "PASS_LITERAL_HOST_CANARY_ONLY"), mode=mode, specSha256=sha,
                      scope=SCOPE, parent=parent_value, source=source, graph=graph_value, cut=cut_value,
                      cases=cases, work=work, acceptance=ACCEPTANCE, limitations=LIMITATIONS)
        phase = "PREPARE_RESULT"
        started = time.perf_counter_ns()
        budget.call("serialization", lambda: encoded(result))
        work["serializationNanos"] = time.perf_counter_ns() - started
        work["analysis"] = budget.finish()
        work["workerElapsedNanos"] = work["analysis"]["elapsedNanos"]
        def write_result():
            payload = encoded(result)
            need(len(payload) <= 65536, "RESULT_SIZE")
            with (out / "sensitivity-result.json").open("xb") as stream:
                stream.write(payload)
                stream.flush()
            return payload
        phase = "TRANSPORT"
        payload = budget.call("transport", write_result)
        final = budget.finish()
        index = dict(schema="tsj-canonical-cut-sensitivity-worker-v1", status=result["status"],
                     mode=mode, specSha256=sha, result=dict(path="sensitivity-result.json",
                     bytes=len(payload), sha256=digest(payload)), aggregateBytes=len(payload),
                     elapsedNanos=final["elapsedNanos"], work=final)
        line = b"CANONICAL_CUT_SENSITIVITY_COMPLETE " + encoded(index)
        need(len(line) <= 65536, "INDEX_SIZE")
        sys.stdout.buffer.write(line)
        sys.stdout.buffer.flush()
        return 0
    except Exception as error:
        code = str(error) if isinstance(error, (Rejected, ValueError)) and re.fullmatch(r"[A-Z0-9_]{1,80}", str(error)) else "WORKER_INTERNAL_REJECTION"
        failure = dict(schema="tsj-canonical-cut-sensitivity-worker-error-v1", status="REJECTED",
                       phase=phase, elapsedNanos=time.perf_counter_ns() - worker_started,
                       error=code, incomplete=True, acceptance=ACCEPTANCE)
        if budget is not None:
            try:
                failure["work"] = budget.finish()
            except Exception:
                failure["work"] = "INCOMPLETE_OR_OVER_BUDGET"
        sys.stdout.buffer.write(encoded(failure))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
