"""Independent reader for the bounded paired LED diversity diagnosis (not production acceptance)."""
import argparse, hashlib, json, re, sys
from pathlib import Path

if not __debug__:
    raise RuntimeError("Assertion-enabled Python is required")

PREFIX = "PCB_PAIR_DIVERSITY_DIAGNOSTIC "
SCHEMA = "paired-diversity-diagnostic@1"
FAMILY, PROFILE = "LED_INDICATOR", "EASY"
DUP_SEED, PRIOR_SEED = -611041393415742133, 4515843680402079038
TARGET, MAX_ROOTS, MAX_ORDERINGS, MAX_EXPANSIONS = 20, 48, 800, 1_000_000
MASK64, LONG_MIN, LONG_MAX = (1 << 64) - 1, -(1 << 63), (1 << 63) - 1
INT_MIN, INT_MAX = -(1 << 31), (1 << 31) - 1
ROW_KEYS = ["schema", "family", "batchStartOrdinal", "rootOrdinal", "acceptedOrdinal",
 "profile", "seed", "priorMatchingSeed", "physicalFingerprintSha256",
 "noveltyFingerprintSha256", "physicalCanonical", "noveltyCanonical",
 "placementCanonical", "outline", "packingCanonical", "packingMetricEvidence"]
METRIC_KEYS = ["area", "copper", "vias", "protectedVoid"]
EVIDENCE_KEYS = ["state", "baseline", "candidate", "unavailableReason"]
STATES = {"NO_CHANGE": {"NOT_MEASURED"},
 "NO_HEADROOM": {"NOT_MEASURED", "BASELINE_UNAVAILABLE"},
 "ROUTE_REJECTED": {"BASELINE_ONLY"},
 "KEPT": {"CANDIDATE_UNAVAILABLE", "COMPARED"}, "SELECTED": {"COMPARED"}}
PACK_RE = re.compile(r"normal-packing@3;attempts=(0|1);orderings=(0|[1-9][0-9]*);"
 "expansions=(0|[1-9][0-9]*);millis=(0|[1-9][0-9]*);"
 "route=(P05_ONE_FACE|P07_FULLER_TWO_LAYER);"
 "disposition=(NO_CHANGE|NO_HEADROOM|KEPT|SELECTED|ROUTE_REJECTED);"
 "baseline=([0-9a-f]{64});selected=([0-9a-f]{64})")
SHA_RE = re.compile(r"[0-9a-f]{64}\Z")
SEED_RE = re.compile(r"(?:0|-?[1-9][0-9]*)\Z")
MATRIX, PASS = "PHYSICAL_MATRIX|", "PASS: paired diversity diagnostic batch "
DUP_TEXT = "Duplicate physical realization: LED_INDICATOR/-611041393415742133"
PINS = {
 "tests/contracts/QuickPlayPhysicalMatrixContractTest.java": "f54486fad58d30d003355932a67e01dfdeb814d401a7bcfdaa212f2e0d4ff395",
 "src/com/lushprojects/circuitjs1/client/PhysicalBoardFingerprint.java": "e6b8a1bca49ff913e614171c65bc86df9f6a5a39ed28cb44744b8e88cf9daf06",
 "src/com/lushprojects/circuitjs1/client/PlayerSessionFingerprint.java": "e1efcc45ae485b978f9ee771193eaae2622f4930d7a7461318de4c09c10673cf",
 "src/com/lushprojects/circuitjs1/client/PcbPackingMetrics.java": "724e4d6822eaeb3178efdfbaa30340ff12f0bdb1821734666b1a49df47ca5a8e",
 "src/com/lushprojects/circuitjs1/client/PcbPackingProbeReceipt.java": "c7d2f17a3e4b2a892beae7856b5d00dce9af66f7d50d2c3f802ec74d8116cf68",
 "docs/task-evidence/PCB/paired-diversity-diagnostic-r1/export-source/SparseNormalBoardDiagnostic.java": "fb0f1caea0d5d341e1fc5ea04ee3f0b82b8d7d4ac0664e5cca81722e51a9ef6a",
 "src/com/lushprojects/circuitjs1/client/PcbPlacementConstraints.java": "1144f0575cbbae802db890100a460fda9a8541c79459629c06769a4ee9d6fceb",
 "src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java": "df561459c15fcd85840672971b1244bc019e92f58cfef4f81dc663d14f99f1b1",
 "src/com/lushprojects/circuitjs1/client/PcbPlacementCompactor.java": "9dcf4917692f32071574ef5adf8f75aa55ef0e6a9a3159d3011549be2b4fbda3",
 "src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java": "7f27c83d12f47579a7fea50b496c2f0320e833e412f0aebe82594a62c7184750",
 "tests/contracts/PcbConnectedPocketPackingContractTest.java": "d4d788bb9d6cf351c8c28c02ac8f7b4aef8445b110380382532392886d4e0a51",
 "tests/contracts/PcbNormalPackingIdentityContractTest.java": "a4c4806a856dd6ed9d5aee213c5c82d7914bc87d75fd90a1b45ee5ce29250",
}

class ReaderError(ValueError):
    pass

def need(ok, why):
    if not ok:
        raise ReaderError(why)

def integer(v):
    return type(v) is int

def string(v, name, maximum=None):
    need(type(v) is str and bool(v), name + " must be nonempty text")
    need(not any(0xD800 <= ord(c) <= 0xDFFF for c in v), name + " has invalid Unicode")
    if maximum is not None:
        need(len(v) <= maximum, name + " exceeds source bound")
    return v

def seed(v, name):
    string(v, name)
    need(SEED_RE.fullmatch(v) is not None and LONG_MIN <= int(v) <= LONG_MAX,
         name + " is not canonical signed-long decimal")
    return int(v)

def root_seed(i):
    need(integer(i) and 0 <= i < MAX_ROOTS, "root ordinal out of range")
    if i == 0: return 0
    if i == 1: return 17
    if i == 2: return LONG_MIN
    if i == 3: return LONG_MAX
    z = (0x632BE59BD9B4E019 + 0x9E3779B97F4A7C15 * i) & MASK64
    z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & MASK64
    z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & MASK64
    z ^= z >> 31
    return z - (1 << 64) if z >= 1 << 63 else z

def sha_text(v):
    try: return hashlib.sha256(v.encode("utf-8", "strict")).hexdigest()
    except UnicodeError as exc: raise ReaderError("canonical is not valid UTF-8") from exc

def unique_pairs(pairs):
    result = {}
    for k, v in pairs:
        need(k not in result, "duplicate JSON key " + repr(k))
        result[k] = v
    return result

def bad_constant(v):
    raise ReaderError("non-JSON numeric constant " + v)

def read_text(path):
    raw = Path(path).read_bytes()
    try: return raw, raw.decode("utf-8-sig", "strict")
    except UnicodeDecodeError as exc: raise ReaderError(str(path) + " is not strict UTF-8") from exc

def read_rows(path):
    raw, text = read_text(path)
    rows = []
    for n, line in enumerate(text.splitlines(), 1):
        if line.startswith(PREFIX):
            try:
                row = json.loads(line[len(PREFIX):], object_pairs_hook=unique_pairs, parse_constant=bad_constant)
            except (json.JSONDecodeError, ReaderError) as exc:
                raise ReaderError("invalid diagnostic JSON line " + str(n) + ": " + str(exc)) from exc
            need(type(row) is dict, "diagnostic row is not an object")
            rows.append(row)
    need(rows, str(path) + " has no diagnostic rows")
    return raw, text, rows

def metrics(v, label):
    need(type(v) is dict and list(v) == METRIC_KEYS, label + " fields/order invalid")
    for k in METRIC_KEYS:
        n = v[k]
        need(integer(n), label + "." + k + " is not integer")
        need((1 if k == "area" else 0) <= n <= (LONG_MAX if k in ("area", "copper") else INT_MAX),
             label + "." + k + " outside metric range")
    need(v["protectedVoid"] <= v["area"], label + ".protectedVoid exceeds area")
    return v

def improves(a, b):
    return b["area"] < a["area"] and b["copper"] <= a["copper"] and b["vias"] <= a["vias"] and b["protectedVoid"] <= a["protectedVoid"]

def packing(row):
    can, ev = row["packingCanonical"], row["packingMetricEvidence"]
    if can is None:
        need(ev is None, "null packing receipt has metric evidence")
        return None
    m = PACK_RE.fullmatch(string(can, "packingCanonical"))
    need(m is not None, "packingCanonical is not normal-packing@3")
    attempts, orderings, expansions, millis = map(int, m.group(1, 2, 3, 4))
    disposition = m.group(6)
    need(orderings <= MAX_ORDERINGS and expansions <= MAX_EXPANSIONS and millis <= LONG_MAX,
         "packing work or clock exceeds source bound")
    if attempts == 0:
        need(orderings == expansions == millis == 0 and disposition in ("NO_CHANGE", "NO_HEADROOM"),
             "zero-attempt packing receipt contradicts work/disposition")
    else:
        need(disposition in ("KEPT", "SELECTED", "ROUTE_REJECTED"), "attempted packing disposition invalid")
    h0, h1 = m.group(7, 8)
    need((h0 != h1) == (disposition == "SELECTED"), "packing geometry hashes contradict disposition")
    need(type(ev) is dict and list(ev) == EVIDENCE_KEYS, "metric evidence fields/order invalid")
    state, base, cand, reason = ev["state"], ev["baseline"], ev["candidate"], ev["unavailableReason"]
    need(type(state) is str and state in STATES[disposition], "packing state/disposition contradiction")
    if base is not None: base = metrics(base, "baseline")
    if cand is not None: cand = metrics(cand, "candidate")
    if state == "NOT_MEASURED": need(base is None and cand is None and reason is None, "NOT_MEASURED has data")
    elif state == "BASELINE_UNAVAILABLE":
        need(base is None and cand is None, "BASELINE_UNAVAILABLE has metrics")
        string(reason, "unavailableReason")
        need(len(reason) <= 160 and bool(reason.strip()), "unavailable reason blank/oversized")
    elif state == "BASELINE_ONLY": need(base is not None and cand is None and reason is None, "BASELINE_ONLY shape invalid")
    elif state == "CANDIDATE_UNAVAILABLE":
        need(base is not None and cand is None, "CANDIDATE_UNAVAILABLE shape invalid")
        string(reason, "unavailableReason")
        need(len(reason) <= 160 and bool(reason.strip()), "unavailable reason blank/oversized")
    else:
        need(base is not None and cand is not None and reason is None, "COMPARED shape invalid")
        need(improves(base, cand) == (disposition == "SELECTED"), "selected metrics do not strictly improve all fields")
    _, _, w, h = row["outline"]
    measured = cand if disposition == "SELECTED" else base
    if measured is not None:
        need(measured["area"] == w * h, "packing metrics area disagrees with selected outline")
    return {"canonical": can, "millis": millis, "disposition": disposition, "metricEvidence": ev}

def placement(text):
    string(text, "placementCanonical")
    need(text.endswith(";"), "placementCanonical missing final separator")
    out, ids = [], []
    for entry in text[:-1].split(";"):
        f = entry.split(":")
        need(len(f) == 4, "placement entry shape invalid")
        ident, xy, rotation, side = f
        string(ident, "placement ID")
        string(rotation, "placement rotation")
        string(side, "placement side")
        pair = xy.split(",")
        need(len(pair) == 2 and all(re.fullmatch(r"-?(?:0|[1-9][0-9]*)", p) for p in pair),
             "placement coordinate invalid")
        x, y = map(int, pair)
        need(INT_MIN <= x <= INT_MAX and INT_MIN <= y <= INT_MAX, "placement coordinate outside int32")
        ids.append(ident)
        out.append((ident, x, y, rotation, side))
    need(ids == sorted(ids) and len(ids) == len(set(ids)), "placement IDs duplicated/unordered")
    need(out, "placementCanonical empty")
    return out

def macro(poses):
    return "".join(str((a[1] > b[1]) - (a[1] < b[1])) + "," +
                   str((a[2] > b[2]) - (a[2] < b[2])) + ";"
                   for i, a in enumerate(poses) for b in poses[i + 1:])

def validate_row(r, i):
    need(type(r) is dict and list(r) == ROW_KEYS, "row fields/order differ from exporter schema")
    need(r["schema"] == SCHEMA and r["family"] == FAMILY and r["profile"] == PROFILE,
         "row schema/family/profile changed")
    need(integer(r["batchStartOrdinal"]) and r["batchStartOrdinal"] == 0, "batch start is not zero")
    root, accepted = r["rootOrdinal"], r["acceptedOrdinal"]
    need(integer(root) and integer(accepted) and 0 <= root < MAX_ROOTS, "row ordinal invalid")
    need(accepted == i, "accepted ordinal/order invalid")
    n = seed(r["seed"], "seed")
    need(n == root_seed(root), "seed does not match original root sequence")
    if r["priorMatchingSeed"] is not None: seed(r["priorMatchingSeed"], "priorMatchingSeed")
    phys = string(r["physicalCanonical"], "physicalCanonical", 1_048_576)
    nov = string(r["noveltyCanonical"], "noveltyCanonical", 1_048_576)
    need(phys.startswith("physical-board@1;") and nov.startswith("physical-board@1;"), "fingerprint version invalid")
    need(r["physicalFingerprintSha256"] == sha_text(phys), "physical fingerprint digest mismatch")
    need(r["noveltyFingerprintSha256"] == sha_text(nov), "novelty fingerprint digest mismatch")
    poses = placement(r["placementCanonical"])
    o = r["outline"]
    need(type(o) is list and len(o) == 4 and all(integer(v) and INT_MIN <= v <= INT_MAX for v in o),
         "outline must be four int32 values")
    need(o[2] > 0 and o[3] > 0, "outline dimensions must be positive")
    p = packing(r)
    return {"row": r, "seed": n, "root": root, "poses": poses, "macro": macro(poses),
            "size": str(o[2]) + "x" + str(o[3]), "packing": p}

def sequence(rows, label):
    checked = [validate_row(r, i) for i, r in enumerate(rows)]
    roots = [x["root"] for x in checked]
    need(roots == sorted(roots) and len(roots) == len(set(roots)), label + " root order invalid")
    need(len({x["seed"] for x in checked}) == len(checked), label + " repeats seed")
    return checked

def current_rows(rows, log, expected_prior=str(PRIOR_SEED)):
    xs = sequence(rows, "current")
    need(2 <= len(xs) <= TARGET, "current row count outside failed-batch bound")
    ords = [i for i in range(MAX_ROOTS) if root_seed(i) == DUP_SEED]
    need(ords == [18], "pinned duplicate seed/root changed")
    seen, novelty, repeats = {}, {}, []
    for i, item in enumerate(xs):
        r = item["row"]
        j = seen.get(r["physicalCanonical"])
        if j is None:
            need(r["priorMatchingSeed"] is None, "prior seed claims missing physical match")
            seen[r["physicalCanonical"]] = i
        else:
            repeats.append((i, j))
            need(r["priorMatchingSeed"] == str(xs[j]["seed"]), "priorMatchingSeed does not identify first match")
        k = novelty.get(r["noveltyCanonical"])
        if k is None: novelty[r["noveltyCanonical"]] = i
        else: need(i == len(xs) - 1, "novelty duplicate precedes pinned failure")
    need(len(repeats) == 1, "current rows do not contain exactly one physical duplicate")
    i, j = repeats[0]
    c, p = xs[i], xs[j]
    need(i == len(xs) - 1, "physical duplicate is not last emitted row")
    need(c["seed"] == DUP_SEED and c["root"] == 18, "last row is not pinned duplicate seed/root")
    need(p["seed"] == seed(expected_prior, "expected prior seed") and (expected_prior != str(PRIOR_SEED) or p["root"] == 13),
         "pinned duplicate prior row is not seed/root 4515843680402079038/13")
    cr, pr = c["row"], p["row"]
    need(cr["physicalCanonical"] == pr["physicalCanonical"] and
         cr["physicalFingerprintSha256"] == pr["physicalFingerprintSha256"], "duplicate physical identity differs")
    need(cr["noveltyCanonical"] == pr["noveltyCanonical"] and
         cr["placementCanonical"] == pr["placementCanonical"] and cr["outline"][2:] == pr["outline"][2:],
         "duplicate novelty/normalized pose/outline size differs")
    need(log.count(DUP_TEXT) == 1, "current log lacks exact original duplicate assertion")
    need(PASS not in log and not any(x.startswith(MATRIX) for x in log.splitlines()),
         "current log unexpectedly contains completion row")
    return xs, c, p

def matrix(text):
    lines = [x for x in text.splitlines() if x.startswith(MATRIX)]
    need(len(lines) == 1, "nohook receipt needs exactly one PHYSICAL_MATRIX row")
    p = lines[0].split("|")
    need(len(p) == 13 and p[1:3] == [FAMILY, PROFILE], "PHYSICAL_MATRIX shape/family/profile invalid")
    names = ("accepted", "physical", "placements", "routes", "copper", "macros", "sizes", "rejected", "roots")
    result = {}
    for name, token in zip(names, p[3:12]):
        need(re.fullmatch(r"0|[1-9][0-9]*", token) is not None, "invalid PHYSICAL_MATRIX " + name)
        result[name] = int(token)
    need(re.fullmatch(r"-?(?:0|[1-9][0-9]*)", p[12]) is not None, "invalid matrix clock")
    result["elapsedMillis"] = int(p[12])
    return result

def nohook_rows(rows, text):
    xs = sequence(rows, "nohook")
    m = matrix(text)
    need(len(xs) == TARGET and m["accepted"] == TARGET, "nohook needs exactly 20 accepted rows")
    need(m["physical"] == TARGET, "nohook physical count is not 20")
    for name, minimum in (("placements", 12), ("routes", 12), ("copper", 12), ("macros", 6), ("sizes", 2)):
        need(m[name] >= minimum, "nohook " + name + " diversity below " + str(minimum))
    need(TARGET <= m["roots"] <= MAX_ROOTS and m["accepted"] + m["rejected"] == m["roots"],
         "nohook accepted/rejected/root counts invalid")
    need(m["roots"] == xs[-1]["root"] + 1, "nohook root count differs from last accepted row")
    phys = {x["row"]["physicalCanonical"] for x in xs}
    nov = {x["row"]["noveltyCanonical"] for x in xs}
    need(len(phys) == len(nov) == TARGET, "nohook rows duplicate physical/novelty fingerprints")
    need(all(x["row"]["priorMatchingSeed"] is None for x in xs), "nohook row reports prior duplicate")
    counts = {"physical": len(phys),
       "placements": len({x["row"]["placementCanonical"] for x in xs}),
       "macros": len({x["macro"] for x in xs}), "sizes": len({x["size"] for x in xs})}
    for k, v in counts.items(): need(m[k] == v, "nohook summary " + k + " differs from rows")
    ps = [x for x in text.splitlines() if x.startswith(PASS)]
    need(len(ps) == 1 and re.fullmatch(
      r"PASS: paired diversity diagnostic batch family=LED_INDICATOR startOrdinal=0 pairs=1 assertions=[1-9][0-9]*", ps[0]),
      "nohook receipt lacks exact original diagnostic PASS")
    need(DUP_TEXT not in text, "nohook hit duplicate assertion")
    return xs, m, counts

def compact(x):
    r = x["row"]
    return {k: r[k] for k in ("seed", "rootOrdinal", "acceptedOrdinal",
      "physicalFingerprintSha256", "noveltyFingerprintSha256", "physicalCanonical",
      "noveltyCanonical", "placementCanonical", "outline", "packingCanonical", "packingMetricEvidence")}

def compare(cur, base, collision, prior, hashes):
    by_seed = {x["row"]["seed"]: x for x in base}
    wanted = [str(prior["seed"]), str(collision["seed"])]
    need(all(s in by_seed for s in wanted), "nohook lacks current collision/prior seed rows")
    pairs = []
    for s in wanted:
        c = next(x for x in cur if x["row"]["seed"] == s)
        b = by_seed[s]
        cr, br = c["row"], b["row"]
        pairs.append({"seed": s, "current": compact(c), "nohook": compact(b),
          "observedDeltas": {
            "physicalFingerprintEqual": cr["physicalFingerprintSha256"] == br["physicalFingerprintSha256"],
            "noveltyFingerprintEqual": cr["noveltyFingerprintSha256"] == br["noveltyFingerprintSha256"],
            "placementEqual": cr["placementCanonical"] == br["placementCanonical"],
            "outlineOriginDelta": {"x": cr["outline"][0] - br["outline"][0], "y": cr["outline"][1] - br["outline"][1]},
            "outlineSizeDelta": {"width": cr["outline"][2] - br["outline"][2], "height": cr["outline"][3] - br["outline"][3]},
            "packingCanonicalEqual": cr["packingCanonical"] == br["packingCanonical"],
            "packingMetricEvidenceEqual": cr["packingMetricEvidence"] == br["packingMetricEvidence"]}})
    return {"schema": "paired-diversity-diagnostic-comparison@1",
      "status": "DIAGNOSTIC_ONLY",
      "productionMatrixStatus": "BLOCKED_BY_REPRODUCED_PHYSICAL_DUPLICATE",
      "family": FAMILY, "profile": PROFILE, "batchStartOrdinal": 0,
      "currentRowCount": len(cur), "nohookAcceptedRowCount": len(base),
      "inputSha256": hashes, "consumedSourceSha256": PINS,
      "collision": {"classification": "ORIGINAL_PHYSICAL_DUPLICATE_ASSERTION",
        "seed": str(collision["seed"]), "rootOrdinal": collision["root"],
        "priorSeed": str(prior["seed"]), "priorRootOrdinal": prior["root"],
        "rows": [compact(prior), compact(collision)]}, "bySeedCurrentVsNoHook": pairs}

def fixture(root, accepted, n, key, index=0, origin=(0, 0)):
    w, h = 100 + index % 2 * 10, 100
    ph, nv = "physical-board@1;fixture=" + key + ";", "physical-board@1;novelty=" + key + ";"
    poses = ["C0:500,500:ROT_0:TOP;"]
    for bit in range(5):
        poses.append("C" + str(bit + 1) + ":" + str(550 if (index >> bit) & 1 else 450) + ",500:ROT_0:TOP;")
    area = w * h
    ev = {"state": "COMPARED",
      "baseline": {"area": area + 1000, "copper": 100, "vias": 0, "protectedVoid": 1000},
      "candidate": {"area": area, "copper": 100, "vias": 0, "protectedVoid": 1000},
      "unavailableReason": None}
    can = "normal-packing@3;attempts=1;orderings=1;expansions=1;millis=1;route=P05_ONE_FACE;disposition=SELECTED;baseline=" + "a"*64 + ";selected=" + "b"*64
    return {"schema": SCHEMA, "family": FAMILY, "batchStartOrdinal": 0,
      "rootOrdinal": root, "acceptedOrdinal": accepted, "profile": PROFILE, "seed": str(n),
      "priorMatchingSeed": None, "physicalFingerprintSha256": sha_text(ph),
      "noveltyFingerprintSha256": sha_text(nv), "physicalCanonical": ph,
      "noveltyCanonical": nv, "placementCanonical": "".join(poses),
      "outline": [origin[0], origin[1], w, h], "packingCanonical": can, "packingMetricEvidence": ev}

def reject(name, fragment, action):
    try: action()
    except ReaderError as exc:
        need(fragment in str(exc), name + " failed for unexpected reason: " + str(exc))
        return {"name": name, "result": "REJECTED_AS_EXPECTED"}
    raise ReaderError(name + " accepted tampered input")

def canaries():
    first = fixture(0, 0, 0, "shared")
    dup = fixture(18, 1, DUP_SEED, "shared")
    dup["priorMatchingSeed"] = "0"
    log = "AssertionError: " + DUP_TEXT
    current_rows([first, dup], log, "0")
    moved = json.loads(json.dumps(dup))
    moved["outline"][0] += 100
    moved["packingCanonical"] = moved["packingCanonical"].replace("millis=1", "millis=900")
    validate_row(moved, 1)
    controls = [{"name": "positive_clock_translation_and_improved_geometry", "result": "ACCEPTED_AS_EXPECTED"}]
    ords = list(range(TARGET))
    base = [fixture(i, i, root_seed(i), "nohook-" + str(i), i) for i in ords]
    summary = ("PHYSICAL_MATRIX|LED_INDICATOR|EASY|20|20|20|20|20|20|2|0|20|123\n"
      "PASS: paired diversity diagnostic batch family=LED_INDICATOR startOrdinal=0 pairs=1 assertions=200\n")
    nohook_rows(base, summary)
    controls.append({"name": "positive_nohook_20_row_fixture", "result": "ACCEPTED_AS_EXPECTED"})

    bad = json.loads(json.dumps([first, dup])); bad[0]["seed"] = "1"
    controls.append(reject("seed_root_identity", "seed does not match original root sequence",
      lambda: current_rows(bad, log, "0")))
    bad = json.loads(json.dumps([first, dup])); bad[0]["physicalFingerprintSha256"] = "0"*64
    controls.append(reject("physical_digest", "physical fingerprint digest mismatch",
      lambda: current_rows(bad, log, "0")))
    bad = json.loads(json.dumps([first, dup])); bad[1]["priorMatchingSeed"] = "17"
    controls.append(reject("prior_seed_mapping", "priorMatchingSeed does not identify first match",
      lambda: current_rows(bad, log, "0")))
    unique = json.loads(json.dumps(dup)); unique["physicalCanonical"] += "other;"
    unique["noveltyCanonical"] += "other;"
    unique["physicalFingerprintSha256"] = sha_text(unique["physicalCanonical"])
    unique["noveltyFingerprintSha256"] = sha_text(unique["noveltyCanonical"])
    unique["priorMatchingSeed"] = None
    controls.append(reject("duplicate_absence", "exactly one physical duplicate",
      lambda: current_rows([first, unique], log, "0")))
    controls.append(reject("assertion_text", "lacks exact original duplicate assertion",
      lambda: current_rows([first, dup], "AssertionError: changed", "0")))
    controls.append(reject("row_order", "accepted ordinal/order",
      lambda: sequence([dup, first], "current")))
    for k, v in (("vias", 1), ("protectedVoid", 1001)):
        bad = json.loads(json.dumps(dup)); bad["packingMetricEvidence"]["candidate"][k] = v
        controls.append(reject("metric_" + k, "selected metrics do not strictly improve",
          lambda data=bad: validate_row(data, 1)))
    for name, slot, value, reason in (
        ("placements", 5, 11, "placements diversity below 12"),
        ("routes", 6, 11, "routes diversity below 12"),
        ("copper", 7, 11, "copper diversity below 12"),
        ("macros", 8, 5, "macros diversity below 6"),
        ("sizes", 9, 1, "sizes diversity below 2")):
        fields = summary.splitlines()[0].split("|"); fields[slot] = str(value)
        changed = "|".join(fields) + "\n" + "\n".join(summary.splitlines()[1:]) + "\n"
        controls.append(reject("count_" + name, reason, lambda text=changed: nohook_rows(base, text)))
    controls.append(reject("nohook_pass_text", "lacks exact original diagnostic PASS",
      lambda: nohook_rows(base, summary.split("PASS:")[0])))
    return {"status": "PASS", "controls": controls}

def report(current_path, nohook_path, current_log_path):
    cb, _, cr = read_rows(current_path)
    nb, nt, nr = read_rows(nohook_path)
    lb, log = read_text(current_log_path)
    need(len(cr) == 19 and [r.get("rootOrdinal") for r in cr] == list(range(19)),
         "current observations must cover every original root through the root-18 failure")
    need(not any(x.startswith("PHYSICAL_REJECT|") for x in log.splitlines()),
         "this observed current batch has no rejected roots")
    cur, collision, prior = current_rows(cr, log)
    base, mat, counts = nohook_rows(nr, nt)
    result = compare(cur, base, collision, prior, {
      "currentObservations": hashlib.sha256(cb).hexdigest(),
      "nohookReceipt": hashlib.sha256(nb).hexdigest(),
      "currentNativeLog": hashlib.sha256(lb).hexdigest()})
    result.update({"nohookMatrixSummary": mat, "independentlyRecomputedNoHookCounts": counts,
      "canaries": canaries(),
      "allowedCrossRunDifferences": [
        {"field": "placement/physical/novelty geometry", "reason": "Packing may move parts/copper; each run's source replay and uniqueness assertions remain active."},
        {"field": "outline origin/dimensions", "reason": "Packing may translate/resize; duplicate current rows retain identical normalized poses and dimensions."},
        {"field": "packing receipt metrics and clocks", "reason": "Observed diagnostic data may differ; schema, bounds and disposition consistency are checked."}],
      "limits": [
        "LED_INDICATOR batch only; original root sequence, target 20, maximum 48 roots.",
        "Current reproduces the original duplicate assertion; no-hook PASS is bounded to its 20 accepted rows.",
        "Route/copper diversity comes from the original PHYSICAL_MATRIX summary and retained Java assertions, not independent reconstruction.",
        "Physical canonical fingerprints are not authenticated receipts or proof of electrical solver-graph equivalence.",
        "No full-matrix, all-seed, optimality, manufacturing, player-behavior, or production-acceptance claim."]})
    return result

def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--canaries", action="store_true", help="run self-contained reader controls")
    parser.add_argument("current_observations", nargs="?")
    parser.add_argument("nohook_receipt", nargs="?")
    parser.add_argument("current_log", nargs="?")
    a = parser.parse_args(argv)
    if a.canaries and not any((a.current_observations, a.nohook_receipt, a.current_log)):
        out = {"schema": "paired-diversity-reader-canaries@1", "status": "PASS", "canaries": canaries()}
    else:
        need(all((a.current_observations, a.nohook_receipt, a.current_log)),
             "usage: read_diagnostic.py current-observations nohook-receipt current-native-log")
        out = report(a.current_observations, a.nohook_receipt, a.current_log)
    print(json.dumps(out, ensure_ascii=False, indent=2, sort_keys=True))

if __name__ == "__main__":
    try: main()
    except (ReaderError, OSError, UnicodeError) as exc:
        print("read_diagnostic rejected input: " + str(exc), file=sys.stderr)
        sys.exit(2)

