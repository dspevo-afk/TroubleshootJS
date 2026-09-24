"""Insert the two human-readable tables from summary.json into README.md."""
import json
import pathlib


def fmt(value):
    if value is None:
        return "n/a"
    if isinstance(value, float) and value.is_integer():
        value = int(value)
    return "{:,}".format(value)


root = pathlib.Path(__file__).resolve().parent
data = json.loads((root / "summary.json").read_text(encoding="utf-8"))
by_policy = {policy: {s["count"]: s for s in data["sizePolicySummaries"] if s["policy"] == policy}
             for policy in ("FULLER_TWO_LAYER", "RESTRICTED_TWO_LAYER")}
full = by_policy["FULLER_TWO_LAYER"]
restricted = by_policy["RESTRICTED_TWO_LAYER"]
lines = [
    "### Population, outcomes and work",
    "",
    "| Parts | Channels + aux | Area / grid cells | Pads / nets / max degree | R passes; expansions p50 / p95 | F passes; expansions p50 / p95 | F route ms p50 / p95 |",
    "| ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
]
for count, f in sorted(full.items()):
    r = restricted.get(count)
    r_text = "not run" if r is None else "{}/{}; {} / {}".format(
        r["successes"], r["attempts"], fmt(r["expansionsP50"]), fmt(r["expansionsP95"]))
    lines.append("| {} | {} + {} | {} / {} | {} / {} / {} | {} | {}/{}; {} / {} | {} / {} |".format(
        count, f["channels"], f["auxiliaryShuntsOrLoads"], fmt(f["boardArea"]),
        fmt(f["gridCells"]), f["pads"], f["nets"], f["maxNetDegree"], r_text,
        f["successes"], f["attempts"], fmt(f["expansionsP50"]),
        fmt(f["expansionsP95"]), fmt(f["routeMsP50"]), fmt(f["routeMsP95"])))
lines += [
    "",
    "### Valid fuller-route geometry",
    "",
    "| Parts | Fuller passes | Vias median / max | Top / bottom segments median | Top / bottom length median | Typical failure when present |",
    "| ---: | ---: | ---: | ---: | ---: | --- |",
]
for count, f in sorted(full.items()):
    failures = ", ".join("{} ({})".format(key, amount) for key, amount in f["failures"].items()) or "none"
    lines.append("| {} | {}/{} | {} / {} | {} / {} | {} / {} | {} |".format(
        count, f["successes"], f["attempts"], fmt(f["viasSuccessMedian"]),
        fmt(f["viasSuccessMax"]), fmt(f["topSegmentsSuccessMedian"]),
        fmt(f["bottomSegmentsSuccessMedian"]), fmt(f["topLengthSuccessMedian"]),
        fmt(f["bottomLengthSuccessMedian"]), failures))
readme = root / "README.md"
body = readme.read_text(encoding="utf-8")
marker = "<!-- TABLES -->"
if body.count(marker) != 1:
    raise AssertionError("Expected one table marker")
readme.write_text(body.replace(marker, "\n".join(lines)), encoding="utf-8")
