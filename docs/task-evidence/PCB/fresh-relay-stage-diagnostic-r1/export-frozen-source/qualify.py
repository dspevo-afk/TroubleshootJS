"""Strict receipt reader for one fresh ordinary relay placement-stage export."""
import copy
import hashlib
import json
import re
import runpy
from pathlib import Path

if not __debug__:
    raise RuntimeError("Assertion-enabled Python required")

HERE = Path(__file__).resolve().parent
PRIOR = HERE.parent / "sparse-board-diagnostic-r1" / "qualify.py"
CAUSE_READER = HERE.parent / "composed-rejection-diagnostic-r1" / "qualify.py"
CAUSE_READER_SHA256 = "461a8283ebfb1e056ab9e106127b2af9f9cc7c2121acf76d0e705393791e7be7"
FAMILY = "RELAY_OUTPUT"
PROFILE = "EASY"
SEED = "-397178238694963708"
REPLAY = "tsj-alpha/6/EASY/RELAY_OUTPUT/-397178238694963708"
ROW_PREFIX = "PCB_SPARSE_NORMAL_DIAGNOSTIC "
SAMPLER_PREFIX = "PCB_WHITESPACE_CASE "
STAGE_PREFIX = "PCB_PACKING_SPAN_CHECKPOINT "
CAUSE_PREFIX = "PCB_PACKING_REJECTION_DIAGNOSTIC "
STAGE_SCHEMA = "packing-span-checkpoint@1"
STAGE_PHASES = ("SOURCE", "ORDINARY", "PAIRED", "FINAL")
STAGE_KEYS = {
    "schema", "phase", "inputPlanCanonical", "inputPlanSha256", "layoutSeed",
    "routingSeed", "planCandidate", "evaluations", "planOutline", "netSpans",
}
SPAN_KEYS = {"netId", "padIds", "centers", "span"}
CAUSE_SCHEMA = "packing-rejection-diagnostic@1"
CAUSE_EVENT_KEYS = {
    "schema", "event", "phase", "inputPlanCanonical", "inputPlanSha256",
    "planCandidate", "planOutline", "layoutSeed", "routingSeed", "routePolicy",
    "baselineGeometrySha256", "exceptionType", "kind", "reason", "blockedNet",
    "message", "recovery",
}


def require(ok, message):
    if not ok:
        raise AssertionError(message)


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def sha_bytes(data):
    return hashlib.sha256(data).hexdigest()


def read_rows(receipt, prefix):
    return [json.loads(line[len(prefix):]) for line in receipt.splitlines()
            if line.startswith(prefix)]


def load_published_reader():
    """Load the pinned sparse oracle with only relay identity and packing4 adapted."""
    baseline = read(HERE / "baseline-inputs.json")
    expected_sha = baseline["production"]["borrowedPublishedInputs"][
        "sparse-board-diagnostic-r1/qualify.py"]
    require(re.fullmatch(r"[0-9a-f]{64}", expected_sha) is not None,
            "published row oracle baseline hash")
    require(sha_bytes(PRIOR.read_bytes()) == expected_sha,
            "published sparse row oracle source pin")
    source = PRIOR.read_text(encoding="utf-8-sig")
    old_identity = (
        "EXPECTED={'rc':('RC_DELAY','EASY','-9187226374781659024'),\n"
        "          'composed':('COMPOSED_CONTROLLED_INDICATOR','MEDIUM','7141843758046216173')}"
    )
    new_identity = "EXPECTED={'relay':('RELAY_OUTPUT','EASY','-397178238694963708')}"
    old_epoch = "layout['constraints']['placementPackingVersion']==3"
    new_epoch = "layout['constraints']['placementPackingVersion']==4"
    require(source.count(old_identity) == 1 and source.count(old_epoch) == 1,
            "exact two prior-oracle adaptation sites")
    adapted = source.replace(old_identity, new_identity, 1).replace(old_epoch, new_epoch, 1)
    namespace = {"__name__": "_fresh_relay_published_reader", "__file__": str(PRIOR)}
    exec(compile(adapted, str(PRIOR), "exec"), namespace)
    require(namespace["EXPECTED"] == {
        "relay": (FAMILY, PROFILE, SEED)}, "finite adapted row identity")
    changes = [
        {"site": "EXPECTED", "from": "RC/composed", "to": REPLAY},
        {"site": "placementPackingVersion", "from": 3, "to": 4},
    ]
    return namespace, sha_bytes(adapted.encode("utf-8")), changes, expected_sha


def canonical_long(value):
    if not isinstance(value, str) or re.fullmatch(
            r"(?:0|-[1-9][0-9]*|[1-9][0-9]*)", value) is None:
        return False
    parsed = int(value)
    return -(1 << 63) <= parsed <= (1 << 63) - 1


def manhattan(first, second):
    return abs(first[0] - second[0]) + abs(first[1] - second[1])


def expected_two_pad_nets(row):
    pads = {item["padId"]: item for item in row["layout"]["pads"]}
    result = {}
    for net in row["layout"]["nets"]:
        endpoints = net["padIds"]
        if len(endpoints) != 2:
            continue
        ordered = sorted(endpoints)
        require(len(set(ordered)) == 2 and all(pad_id in pads for pad_id in ordered),
                "ordinary row two-pad endpoint membership")
        result[net["netId"]] = ordered
    return result


def parse_plan(checkpoint, components, pads):
    outline = checkpoint["planOutline"]
    prefix = "task-packing-plan@1;outline=" + ",".join(str(value) for value in outline) + ";"
    canonical = checkpoint["inputPlanCanonical"]
    require(isinstance(canonical, str) and canonical.startswith(prefix) and
            canonical.endswith("\n"), "checkpoint canonical plan header")
    lines = canonical[len(prefix):].splitlines()
    require(len(lines) == len(components) and all(lines),
            "checkpoint canonical footprint inventory")
    digest = sha_bytes(canonical.encode("utf-8"))
    require(re.fullmatch(r"[0-9a-f]{64}", checkpoint["inputPlanSha256"]) is not None and
            checkpoint["inputPlanSha256"] == digest, "checkpoint input-plan hash")

    found_components = {}
    found_pads = {}
    top_pattern = re.compile(
        r"(?P<component>.+)@(?P<x>-?[0-9]+),(?P<y>-?[0-9]+),"
        r"(?P<w>[0-9]+),(?P<h>[0-9]+)$")
    pad_pattern = re.compile(
        r"(?:^|[|;])pad=(?P<pad>[^|;]+)@(?P<x>-?[0-9]+),(?P<y>-?[0-9]+) "
        r"escape=(-?[0-9]+),(-?[0-9]+),([0-9]+) attachment=")
    for line in lines:
        head, separator, placement = line.partition("|")
        match = top_pattern.fullmatch(head)
        require(separator and match is not None, "checkpoint footprint identity and pose")
        component_id = match.group("component")
        x, y, width, height = (int(match.group(name)) for name in ("x", "y", "w", "h"))
        require(component_id in components and component_id not in found_components and
                width > 0 and height > 0,
                "checkpoint exact component inventory")
        placement_prefix = "component=" + component_id + "@" + \
            ",".join(str(value) for value in (x, y, width, height)) + "|pose="
        require(placement.startswith(placement_prefix),
                "checkpoint component placement identity")
        found_components[component_id] = {"x": x, "y": y, "width": width, "height": height}
        component_pad_ids = []
        for pad_match in pad_pattern.finditer(line):
            pad_id = pad_match.group("pad")
            require(pad_id in pads and pads[pad_id]["componentId"] == component_id and
                    pad_id not in found_pads,
                    "checkpoint pad-to-component membership")
            center = [int(pad_match.group("x")), int(pad_match.group("y"))]
            found_pads[pad_id] = center
            component_pad_ids.append(pad_id)
        expected_component_pads = [terminal["padId"] for terminal in
                                    components[component_id]["terminals"]]
        require(len(component_pad_ids) == len(expected_component_pads) and
                set(component_pad_ids) == set(expected_component_pads),
                "checkpoint complete component terminal-pad inventory")
    require(set(found_components) == set(components) and set(found_pads) == set(pads),
            "checkpoint complete component and pad inventories")
    return found_components, found_pads


def validate_checkpoint(checkpoint, phase, row, expected_nets):
    require(isinstance(checkpoint, dict) and set(checkpoint) == STAGE_KEYS,
            "checkpoint exact schema")
    require(checkpoint["schema"] == STAGE_SCHEMA and checkpoint["phase"] == phase,
            "checkpoint phase and schema")
    outline = checkpoint["planOutline"]
    require(type(outline) is list and len(outline) == 4 and
            all(type(value) is int for value in outline) and outline[2] > 0 and outline[3] > 0,
            "checkpoint plan outline")
    require(canonical_long(checkpoint["layoutSeed"]) and
            canonical_long(checkpoint["routingSeed"]) and
            type(checkpoint["planCandidate"]) is int and checkpoint["planCandidate"] >= 0 and
            type(checkpoint["evaluations"]) is int and
            0 <= checkpoint["evaluations"] <= 60000,
            "checkpoint candidate and bounded cumulative evaluations")
    components = {item["componentId"]: item for item in row["layout"]["components"]}
    pads = {item["padId"]: item for item in row["layout"]["pads"]}
    component_poses, pad_centers = parse_plan(checkpoint, components, pads)

    spans = checkpoint["netSpans"]
    require(type(spans) is list and len(spans) == len(expected_nets),
            "checkpoint exact two-pad net inventory")
    net_map = {net["netId"]: net for net in row["layout"]["nets"]}
    seen = []
    span_map = {}
    for item in spans:
        require(isinstance(item, dict) and set(item) == SPAN_KEYS,
                "checkpoint span entry schema")
        net_id = item["netId"]
        require(isinstance(net_id, str) and net_id in expected_nets,
                "checkpoint exact two-pad net inventory")
        endpoints = expected_nets[net_id]
        require(item["padIds"] == endpoints, "checkpoint two-pad net endpoint identities")
        centers = item["centers"]
        require(type(centers) is list and len(centers) == 2 and
                all(type(center) is list and len(center) == 2 and
                    all(type(value) is int for value in center) for center in centers),
                "checkpoint endpoint center types")
        expected_centers = [pad_centers[pad_id] for pad_id in endpoints]
        require(centers == expected_centers,
                "checkpoint endpoint centers match canonical pad coordinates")
        require(type(item["span"]) is int and item["span"] >= 0 and
                item["span"] == manhattan(centers[0], centers[1]),
                "checkpoint Manhattan span recomputes")
        require(net_map[net_id]["padIds"] == endpoints,
                "checkpoint span endpoints match exported logical net")
        seen.append(net_id)
        span_map[net_id] = copy.deepcopy(item)
    require(seen == sorted(expected_nets) and len(set(seen)) == len(seen),
            "checkpoint exact two-pad net inventory")
    return {"outline": list(outline), "candidate": checkpoint["planCandidate"],
            "layoutSeed": checkpoint["layoutSeed"], "routingSeed": checkpoint["routingSeed"],
            "evaluations": checkpoint["evaluations"], "components": component_poses,
            "pads": pad_centers, "spans": span_map}


def validate_stages(checkpoints, row):
    require(type(checkpoints) is list and len(checkpoints) == 4,
            "exact four SOURCE/ORDINARY/PAIRED/FINAL checkpoints")
    expected_nets = expected_two_pad_nets(row)
    require(expected_nets, "ordinary relay has exact two-pad net inventory")
    maps = [validate_checkpoint(item, phase, row, expected_nets)
            for item, phase in zip(checkpoints, STAGE_PHASES)]
    require(all(item["candidate"] == maps[0]["candidate"] and
                item["layoutSeed"] == maps[0]["layoutSeed"] and
                item["routingSeed"] == maps[0]["routingSeed"] for item in maps),
            "stage candidate and replay seeds remain stable")
    outlines = [item["outline"] for item in maps]
    require(all(item[:2] == outlines[0][:2] for item in outlines),
            "stage outline origin remains stable")
    for before, after in zip(outlines, outlines[1:]):
        for dimension in (2, 3):
            reduction = before[dimension] - after[dimension]
            require(reduction >= 0 and reduction % 10 == 0,
                    "stage strip outlines contract on the grid10 step")
    evaluations = [item["evaluations"] for item in maps]
    require(evaluations[0] <= evaluations[1] <= evaluations[2] <= 60000 and
            evaluations[3] == evaluations[2],
            "stage cumulative evaluations remain ordered and within shared cap")
    require({key: value for key, value in checkpoints[3].items() if key != "phase"} ==
            {key: value for key, value in checkpoints[2].items() if key != "phase"},
            "FINAL equals PAIRED except phase")

    selected = {}
    pad_centers = {item["padId"]: item["center"] for item in row["layout"]["pads"]}
    for net_id, endpoints in expected_nets.items():
        selected[net_id] = {"netId": net_id, "padIds": endpoints,
                            "centers": [pad_centers[pad_id] for pad_id in endpoints],
                            "span": manhattan(pad_centers[endpoints[0]], pad_centers[endpoints[1]])}
    disposition = row["packingProbe"]["disposition"]
    if disposition == "SELECTED":
        require(all(selected[net_id]["span"] <= maps[3]["spans"][net_id]["span"]
                    for net_id in expected_nets),
                "selected routed pad spans do not exceed FINAL proposal")
        relation = "selected routed geometry is bounded by FINAL proposal spans"
    else:
        require(all(selected[net_id]["span"] == maps[0]["spans"][net_id]["span"]
                    for net_id in expected_nets),
                "retained incumbent spans equal SOURCE up to translation")
        relation = "retained incumbent spans equal SOURCE up to translation"
    return expected_nets, maps, selected, relation


def verify_no_eligible_pair(row):
    components = {item["componentId"]: item for item in row["layout"]["components"]}
    pads = {item["padId"]: item for item in row["layout"]["pads"]}
    candidates = []
    screens = {"twoPadDifferentComponentNet": 0, "twoTerminalNonanchorSameRegionDomain": 0,
               "linkedTerminalDomainsMatch": 0}
    for net in row["layout"]["nets"]:
        endpoints = net["padIds"]
        if len(endpoints) != 2:
            continue
        first, second = (pads[pad_id] for pad_id in endpoints)
        if first["componentId"] == second["componentId"]:
            continue
        screens["twoPadDifferentComponentNet"] += 1
        a, b = components[first["componentId"]], components[second["componentId"]]
        if (len(a["terminals"]) != 2 or len(b["terminals"]) != 2 or
                a["anchor"] != "NONE" or b["anchor"] != "NONE" or
                a["regionId"] != b["regionId"] or a["domainId"] != b["domainId"]):
            continue
        screens["twoTerminalNonanchorSameRegionDomain"] += 1
        terminal_a = {item["terminalId"]: item for item in a["terminals"]}[first["terminalId"]]
        terminal_b = {item["terminalId"]: item for item in b["terminals"]}[second["terminalId"]]
        domain_a, domain_b = terminal_a["domainId"], terminal_b["domainId"]
        if not isinstance(domain_a, str) or not domain_a or domain_a != domain_b:
            continue
        screens["linkedTerminalDomainsMatch"] += 1
        candidates.append({"netId": net["netId"], "padIds": sorted(endpoints),
                           "componentIds": sorted((a["componentId"], b["componentId"])),
                           "regionId": a["regionId"], "domainId": a["domainId"],
                           "linkedTerminalDomain": domain_a})
    require(not candidates, "no exported net passes necessary pair eligibility")
    return {"exactTwoPadCrossComponentNets": screens["twoPadDifferentComponentNet"],
            "afterTwoTerminalNonanchorRegionDomain": screens["twoTerminalNonanchorSameRegionDomain"],
            "afterLinkedTerminalDomainMatch": screens["linkedTerminalDomainsMatch"],
            "necessaryEligiblePairs": candidates,
            "interpretation": "No exported two-pad net passes all necessary component/anchor/region/domain screens; isolation-body and movable-peer checks are therefore unnecessary for ruling out an eligible pair in this inventory."}


def validate_causes(events, row, final_checkpoint, cause_reader):
    require(type(events) is list, "typed cause event list")
    for event in events:
        require(isinstance(event, dict) and set(event) == CAUSE_EVENT_KEYS and
                event.get("schema") == CAUSE_SCHEMA,
                "typed cause event schema")
        cause_reader["validate_event"](event)
        if event["event"] == "COMPACT_ROUTER_REJECTED":
            require(event["blockedNet"] is None or
                    event["blockedNet"] in {net["netId"] for net in row["layout"]["nets"]},
                    "inner blocked net belongs to exported logical board")
    outer_indices = [index for index, event in enumerate(events)
                     if event["event"] == "PACKING_CAUGHT"]
    require(len(outer_indices) <= 1, "at most one final optional-probe catch")
    probe = row["packingProbe"]
    if probe["disposition"] == "ROUTE_REJECTED":
        require(len(outer_indices) == 1, "route-rejected probe has exact outer catch")
    else:
        require(not outer_indices, "nonrejected probe has no outer catch")
    if not outer_indices:
        return {"eventCount": len(events), "outer": None,
                "unrelatedInnerRouterEvents": sum(
                    event["event"] == "COMPACT_ROUTER_REJECTED" for event in events),
                "causeCorrelation": "NOT APPLICABLE; no outer optional-probe catch was emitted"}

    outer_index = outer_indices[0]
    outer = events[outer_index]
    compare = {
        "inputPlanCanonical": final_checkpoint["inputPlanCanonical"],
        "inputPlanSha256": final_checkpoint["inputPlanSha256"],
        "planCandidate": final_checkpoint["planCandidate"],
        "planOutline": final_checkpoint["planOutline"],
        "layoutSeed": final_checkpoint["layoutSeed"],
        "routingSeed": final_checkpoint["routingSeed"],
        "routePolicy": probe["routePolicy"],
    }
    require(all(outer[key] == value for key, value in compare.items()),
            "packing catch binds FINAL proposal")
    require(outer["baselineGeometrySha256"] == probe["baselineGeometrySha256"],
            "packing catch binds actual probe baseline")
    correlated = None
    if outer["kind"] == "ROUTING":
        require(outer_index > 0 and
                events[outer_index - 1]["event"] == "COMPACT_ROUTER_REJECTED",
                "outer router catch has immediately preceding inner rejection")
        inner = events[outer_index - 1]
        fields = ("inputPlanCanonical", "inputPlanSha256", "planCandidate", "planOutline",
                  "layoutSeed", "routingSeed", "routePolicy")
        require(all(inner[key] == outer[key] for key in fields),
                "outer router correlation")
        require(isinstance(inner["recovery"], dict) and outer["recovery"] == inner["recovery"],
                "correlated router recovery statistics")
        recovery = inner["recovery"]
        require(inner["reason"] == recovery["rejectionReason"] and
                inner["message"] == outer["message"],
                "correlated router reason and wrapper message")
        require(recovery["orderingPasses"] == probe["orderings"] and
                recovery["expansions"] == probe["expansions"],
                "correlated recovery equals actual packing probe counters")
        require(inner["blockedNet"] is None or inner["blockedNet"] in
                {net["netId"] for net in row["layout"]["nets"]},
                "correlated blocked net belongs to exported logical board")
        correlated = inner
    return {"eventCount": len(events), "outer": outer,
            "correlatedRouterEvent": correlated,
            "unrelatedInnerRouterEvents": sum(
                event["event"] == "COMPACT_ROUTER_REJECTED" for event in events) -
                (1 if correlated is not None else 0),
            "causeCorrelation": "PACKING_CAUGHT is bound to the actual FINAL proposal"}


def rewrite_checkpoint_hash(checkpoint):
    checkpoint["inputPlanSha256"] = sha_bytes(
        checkpoint["inputPlanCanonical"].encode("utf-8"))


def reader_negatives(row, sampler, reader):
    controls = (
        ("public identity", lambda item: item.update(publicSeed="0")),
        ("pad net membership", lambda item: item["layout"]["pads"][0].update(
            netId="__WRONG_NET__")),
        ("independent copper", lambda item: item["physicalMetrics"].update(
            copper=item["physicalMetrics"]["copper"] + 1)),
        ("independent protected void", lambda item: item["physicalMetrics"].update(
            protectedVoid=item["physicalMetrics"]["protectedVoid"] + 1)),
        ("current physical epochs", lambda item: item["layout"]["constraints"].update(
            placementPackingVersion=3)),
    )
    rejected = []
    for expected, mutate in controls:
        changed = copy.deepcopy(row)
        mutate(changed)
        try:
            reader["validate_row"](changed, sampler, "relay")
        except AssertionError as error:
            require(str(error) == expected, "published row negative failed at its exact assertion")
            rejected.append(expected)
        else:
            raise AssertionError("published row reader accepted negative control: " + expected)
    return rejected


def stage_reader_negatives(checkpoints, row):
    expected_nets = expected_two_pad_nets(row)
    require(expected_nets, "stage negatives require observed two-pad inventory")
    rejected = []

    changed = copy.deepcopy(checkpoints)
    changed[0]["netSpans"].pop()
    try:
        validate_stages(changed, row)
    except AssertionError as error:
        require(str(error) == "checkpoint exact two-pad net inventory",
                "missing-span negative failed at exact inventory assertion")
        rejected.append("missing two-pad span inventory")
    else:
        raise AssertionError("stage reader accepted missing two-pad span")

    changed = copy.deepcopy(checkpoints)
    changed[0]["netSpans"][0]["span"] += 1
    try:
        validate_stages(changed, row)
    except AssertionError as error:
        require(str(error) == "checkpoint Manhattan span recomputes",
                "span arithmetic negative failed at exact assertion")
        rejected.append("independent Manhattan span arithmetic")
    else:
        raise AssertionError("stage reader accepted false Manhattan span")

    changed = copy.deepcopy(checkpoints)
    canonical = changed[0]["inputPlanCanonical"]
    pad_match = re.search(
        r"(?P<separator>[|;])pad=(?P<pad>[^|;]+)"
        r"(?P<pose>@-?[0-9]+,-?[0-9]+ escape=-?[0-9]+,-?[0-9]+,[0-9]+ attachment=)",
        canonical)
    require(pad_match is not None, "pad identity negative canonical framing")
    changed[0]["inputPlanCanonical"] = (
        canonical[:pad_match.start()] + pad_match.group("separator") +
        "pad=__WRONG_PAD__" + pad_match.group("pose") + canonical[pad_match.end():])
    rewrite_checkpoint_hash(changed[0])
    try:
        validate_stages(changed, row)
    except AssertionError as error:
        require(str(error) == "checkpoint pad-to-component membership",
                "rehash-valid pad negative failed at exact identity assertion")
        rejected.append("rehash-valid wrong pad identity")
    else:
        raise AssertionError("stage reader accepted rehashed wrong pad identity")

    changed = copy.deepcopy(checkpoints)
    changed[0]["evaluations"] = 60001
    try:
        validate_stages(changed, row)
    except AssertionError as error:
        require(str(error) == "checkpoint candidate and bounded cumulative evaluations",
                "evaluation budget negative failed at exact assertion")
        rejected.append("shared evaluation cap")
    else:
        raise AssertionError("stage reader accepted over-cap evaluations")

    changed = copy.deepcopy(checkpoints)
    changed[3]["planCandidate"] += 1
    try:
        validate_stages(changed, row)
    except AssertionError as error:
        require(str(error) == "stage candidate and replay seeds remain stable",
                "FINAL relation negative failed at exact assertion")
        rejected.append("FINAL candidate relation")
    else:
        raise AssertionError("stage reader accepted changed FINAL candidate")
    changed = copy.deepcopy(checkpoints)
    value = changed[3]["evaluations"]
    changed[3]["evaluations"] = value - 1 if value else 1
    try:
        validate_stages(changed, row)
    except AssertionError as error:
        require(str(error) == "stage cumulative evaluations remain ordered and within shared cap",
                "FINAL count negative failed at exact correlation")
        rejected.append("FINAL retains paired cumulative work")
    else:
        raise AssertionError("reader accepted changed FINAL counted work")

    changed = copy.deepcopy(checkpoints)
    changed[3]["inputPlanCanonical"] = changed[3]["inputPlanCanonical"][:-1] + ".negative\n"
    rewrite_checkpoint_hash(changed[3])
    try:
        validate_stages(changed, row)
    except AssertionError as error:
        require(str(error) == "FINAL equals PAIRED except phase",
                "hash-valid FINAL suffix negative failed at exact relation")
        rejected.append("hash-valid FINAL canonical suffix relation")
    else:
        raise AssertionError("reader accepted uncorrelated hash-valid FINAL plan")
    return rejected


def cause_reader_negatives(events, row, final_checkpoint, cause_reader, summary):
    outer = summary["outer"]
    if outer is None:
        return ["NOT APPLICABLE: no PACKING_CAUGHT event exists to mutate"]
    rejected = []

    changed = copy.deepcopy(events)
    index = next(i for i, item in enumerate(changed) if item["event"] == "PACKING_CAUGHT")
    changed[index]["planCandidate"] += 1
    try:
        validate_causes(changed, row, final_checkpoint, cause_reader)
    except AssertionError as error:
        require(str(error) == "packing catch binds FINAL proposal",
                "FINAL cause-correlation negative failed at exact assertion")
        rejected.append("cause event detached from FINAL proposal")
    else:
        raise AssertionError("cause reader accepted catch detached from FINAL proposal")

    if summary.get("correlatedRouterEvent") is None:
        return rejected + ["NOT APPLICABLE: outer catch is not a correlated router rejection"]
    changed = copy.deepcopy(events)
    outer_index = next(i for i, item in enumerate(changed) if item["event"] == "PACKING_CAUGHT")
    for index in (outer_index - 1, outer_index):
        recovery = changed[index]["recovery"]
        old = recovery["expansions"]
        literal = ";expansions=" + str(old) + ";"
        require(recovery["canonical"].count(literal) == 1,
                "recovery counter negative canonical framing")
        recovery["expansions"] = old + 1
        recovery["canonical"] = recovery["canonical"].replace(
            literal, ";expansions=" + str(old + 1) + ";", 1)
    try:
        validate_causes(changed, row, final_checkpoint, cause_reader)
    except AssertionError as error:
        require(str(error) == "correlated recovery equals actual packing probe counters",
                "cause counter negative failed at exact assertion")
        rejected.append("self-consistent cause counter disagrees with native probe")
    else:
        raise AssertionError("cause reader accepted changed native probe counter")
    return rejected


def load_cause_reader(baseline):
    require(isinstance(baseline, str) and re.fullmatch(r"[0-9a-f]{64}", baseline) is not None,
            "actual probe baseline hash type")
    raw = CAUSE_READER.read_bytes()
    require(sha_bytes(raw) == CAUSE_READER_SHA256, "typed cause schema validator source pin")
    source = raw.decode("utf-8-sig").replace("\r\n", "\n")
    old = 'SELECTED_GEOMETRY = "14cb0825afcb5d05b9c5866706a8945649f801a5f1bdb64477bb2264881a851f"'
    require(source.count(old) == 1, "exact cause baseline adaptation site")
    adapted = source.replace(old, 'SELECTED_GEOMETRY = "' + baseline + '"', 1)
    namespace = {"__name__": "_fresh_relay_cause_reader", "__file__": str(CAUSE_READER)}
    exec(compile(adapted, str(CAUSE_READER), "exec"), namespace)
    require(namespace["validate_event"].__globals__["SELECTED_GEOMETRY"] == baseline,
            "actual baseline belongs to event validator globals")
    return namespace, sha_bytes(adapted.encode("utf-8"))


def validate_receipt(receipt):
    """Validate one exact relay receipt and return independently checked evidence."""
    require(isinstance(receipt, str), "receipt text input")
    reader, adapted_reader_sha, reader_changes, published_reader_sha = load_published_reader()
    rows = read_rows(receipt, ROW_PREFIX)
    samplers = read_rows(receipt, SAMPLER_PREFIX)
    checkpoints = read_rows(receipt, STAGE_PREFIX)
    events = read_rows(receipt, CAUSE_PREFIX)
    require(len(rows) == len(samplers) == 1, "one ordinary row and whitespace sampler")
    row, sampler = rows[0], samplers[0]
    row_result = reader["validate_row"](row, sampler, "relay")
    require(row["counts"]["logicalParts"] == row["counts"]["layoutParts"] == 9,
            "nine-part relay source and visual inventory")
    probe = row["packingProbe"]
    if probe["disposition"] in {"NO_CHANGE", "NO_HEADROOM"}:
        require(probe["attempts"] == 0 and probe["orderings"] == 0 and
                probe["expansions"] == 0 and probe["millis"] == 0,
                "skipped optional route has zero route work")
    else:
        require(probe["attempts"] == 1,
                "entered optional route records one attempt")
    require(row["physicalMetrics"]["available"] is True and
            row["independentMetrics"]["matchesProduction"] is True,
            "independent four-metric comparison available")

    final_prefix = "PASS: sparse normal board diagnostic family=" + FAMILY + \
        " seed=" + SEED + " assertions="
    markers = [line for line in receipt.splitlines()
               if line.startswith("PASS: sparse normal board diagnostic ")]
    require(len(markers) == 1 and re.fullmatch(
        re.escape(final_prefix) + r"[1-9][0-9]*", markers[0]) is not None,
        "exact relay native final assertion marker")
    assertions = int(markers[0][len(final_prefix):])

    expected_nets, stage_maps, selected_spans, selected_relation = validate_stages(checkpoints, row)
    require(row["packingProbe"]["routePolicy"] == "P05_ONE_FACE",
            "ordinary relay one-face route policy")
    require(sha_bytes(CAUSE_READER.read_bytes()) == CAUSE_READER_SHA256,
            "typed cause schema validator source pin")
    cause_reader, adapted_cause_sha = load_cause_reader(row["packingProbe"]["baselineGeometrySha256"])
    cause_summary = validate_causes(events, row, checkpoints[3], cause_reader)
    eligibility = verify_no_eligible_pair(row)

    row_negatives = reader_negatives(row, sampler, reader)
    stage_negatives = stage_reader_negatives(checkpoints, row)
    cause_negatives = cause_reader_negatives(
        events, row, checkpoints[3], cause_reader, cause_summary)

    stage_results = []
    for checkpoint, stage_map in zip(checkpoints, stage_maps):
        stage_results.append({
            "phase": checkpoint["phase"],
            "planCandidate": checkpoint["planCandidate"],
            "layoutSeed": checkpoint["layoutSeed"],
            "routingSeed": checkpoint["routingSeed"],
            "outline": checkpoint["planOutline"],
            "evaluations": checkpoint["evaluations"],
            "inputPlanSha256": checkpoint["inputPlanSha256"],
            "componentPoses": stage_map["components"],
            "changedComponentIdsSincePrevious": [] if not stage_results else [
                key for key in sorted(stage_map["components"])
                if stage_map["components"][key] != stage_results[-1]["componentPoses"][key]],
            "componentCount": len(stage_map["components"]),
            "padCount": len(stage_map["pads"]),
            "twoPadNetSpans": [stage_map["spans"][net_id] for net_id in sorted(expected_nets)],
        })
    return {
        "status": "PASS_EXACT_FRESH_RELAY_STAGE_RECEIPT",
        "identity": {"family": FAMILY, "profile": PROFILE, "publicSeed": SEED,
                     "replayId": REPLAY},
        "publishedRowOracle": {"sha256": published_reader_sha,
                                "adaptedSha256": adapted_reader_sha,
                                "guardedChanges": reader_changes},
        "diagnosticAssertionMarker": {"line": markers[0], "assertions": assertions},
        "row": row_result,
        "packingProbe": row["packingProbe"],
        "stages": stage_results,
        "selectedPadSpanRelation": selected_relation,
        "eligiblePairScreen": eligibility,
        "adaptedCauseReaderSha256": adapted_cause_sha,
        "typedCauseEvents": cause_summary,
        "readerNegativeControls": row_negatives,
        "stageNegativeControls": stage_negatives,
        "causeNegativeControls": cause_negatives,
        "limits": "One exact ordinary EASY relay replay. This receipt check does not validate host canaries, native process exits/cleanup, execution provenance, private source seals, visual cleanup, a route/placement optimum, production changes, or any broader seed/family claim.",
    }
