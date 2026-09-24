#!/usr/bin/env python3
"""Measure Q30 and synthetic structural floorplans from stable text exports.

The Q30 exporter is developer-only and prints one inspect block containing the
part rectangles, courtyards, pads, and net pad coordinates.  The synthetic
fixture prints routing rows but intentionally does not print its placement.
This script reconstructs that fixture from the exact Q30TwoLayerScaling.java
construction: the component list, package shapes, Java Random jitter, and
board outline are all encoded below as a small independent reader.

No routing result is inferred from a geometric metric.  The synthetic route
rows are retained separately and only rows explicitly marked SUCCESS with a
validated mixed-layer layout are included in the successful comparison corpus.
"""

from __future__ import print_function

import argparse
import collections
import hashlib
import json
import math
import pathlib
import re
import statistics
import sys


SEEDS = [0, 1, 3, 17, 42, -1, 11, 23, 37, 59, 83, -23]
SYNTHETIC_SUCCESS_POLICY = "FULLER_TWO_LAYER"


def sha256(path):
    digest = hashlib.sha256()
    with open(str(path), "rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def rect(value):
    fields = [int(item) for item in value.split(",")]
    if len(fields) != 4 or fields[2] <= 0 or fields[3] <= 0:
        raise ValueError("invalid rectangle: %r" % value)
    return {"x": fields[0], "y": fields[1],
            "width": fields[2], "height": fields[3]}


def rect_area(value):
    return value["width"] * value["height"]


def rect_intersection(first, second):
    left = max(first["x"], second["x"])
    top = max(first["y"], second["y"])
    right = min(first["x"] + first["width"],
                second["x"] + second["width"])
    bottom = min(first["y"] + first["height"],
                 second["y"] + second["height"])
    if right <= left or bottom <= top:
        return 0
    return (right - left) * (bottom - top)


def java_string_hash(value):
    result = 0
    for char in value:
        result = ((result * 31) + ord(char)) & 0xffffffff
    if result & 0x80000000:
        result -= 0x100000000
    return result


class JavaRandom(object):
    """java.util.Random for the two nextInt(3) calls in the fixture."""

    MASK = (1 << 48) - 1

    def __init__(self, seed):
        self.state = (int(seed) ^ 0x5DEECE66D) & self.MASK

    def next(self, bits):
        self.state = (self.state * 0x5DEECE66D + 0xB) & self.MASK
        return self.state >> (48 - bits)

    def next_int(self, bound):
        if bound <= 0:
            raise ValueError("bound")
        if (bound & (bound - 1)) == 0:
            return (bound * self.next(31)) >> 31
        while True:
            bits = self.next(31)
            value = bits % bound
            if bits - value + (bound - 1) >= 0:
                return value


def java_long(value):
    value &= 0xffffffffffffffff
    return value - (1 << 64) if value & (1 << 63) else value


def q30_region(component_id):
    """The exact region expression in RootPlanManifest.rootPlacement()."""
    if component_id in ("J1", "F1", "DREV", "C12"):
        return "entry"
    if component_id in ("U1", "CIN", "C5", "REN"):
        return "regulation"
    if component_id in ("JSA", "U2A", "RSA", "RREFA", "RFB_A",
                        "RREF_HA", "RREF_LA"):
        return "sensor-a"
    if component_id in ("JSB", "U2B", "RSB", "RREFB", "RFB_B",
                        "RREF_HB", "RREF_LB"):
        return "sensor-b"
    if component_id in ("RREF_H", "RREF_L"):
        return "sensor-reference"
    if component_id in ("RLED", "LED1"):
        return "status"
    if component_id.endswith("A") or component_id == "JOA":
        return "output-a"
    if component_id.endswith("B") or component_id == "JOB":
        return "output-b"
    return "output-common"


Q30_CONNECTORS = {
    "J1", "JSA", "JSB", "JLOAD", "JOA", "JOB"
}


def parse_q30_pad_summary(value):
    result = {}
    for token in value.split(";"):
        token = token.strip()
        if not token:
            continue
        match = re.match(
            r"^(?P<id>[^@]+)@(?P<x>-?\d+),(?P<y>-?\d+)"
            r" escape=(?P<dx>-?\d+),(?P<dy>-?\d+),(?P<length>\d+)$",
            token)
        if not match:
            raise ValueError("invalid Q30 pad summary: %r" % token)
        item = match.groupdict()
        result[item["id"]] = {
            "id": item["id"], "x": int(item["x"]), "y": int(item["y"]),
            "escapeDx": int(item["dx"]), "escapeDy": int(item["dy"]),
            "escapeLength": int(item["length"]),
        }
    return result


def parse_net_pads(value):
    result = {}
    for token in value.split(","):
        token = token.strip()
        if not token:
            continue
        match = re.match(r"^([^@]+)@(-?\d+)/(-?\d+)$", token)
        if not match:
            raise ValueError("invalid Q30 net pad: %r" % token)
        result[match.group(1)] = {
            "id": match.group(1), "x": int(match.group(2)),
            "y": int(match.group(3)),
        }
    return result


def parse_q30_header(line):
    # q30-floorplan-v3's developer exporter has a stray quote after the
    # numeric candidate value.  Parse the fields explicitly so the evidence
    # reader remains compatible with both that receipt and a corrected export.
    match = re.search(
        r'"seed":"(-?\d+)"\s*,\s*"candidate":(-?\d+)"?\s*,\s*'
        r'"variant":"([^"]+)"\s*,\s*"outline":"([^"]+)"', line)
    if not match:
        raise ValueError("invalid Q30_ROOT_INSPECT header: %s" % line)
    return {
        "seed": int(match.group(1)), "candidate": int(match.group(2)),
        "variant": match.group(3), "outline": rect(match.group(4)),
    }


def parse_q30(path, expected_parts=None):
    blocks = []
    current = None
    for line_number, raw in enumerate(pathlib.Path(path).read_text(
            encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if line.startswith("Q30_ROOT_INSPECT "):
            if current is not None:
                blocks.append(current)
            current = parse_q30_header(line)
            current["parts"] = []
            current["nets"] = []
            continue
        if line.startswith("Q30_ROOT_INSPECT_REJECT "):
            if current is not None:
                blocks.append(current)
                current = None
            item = json.loads(line[len("Q30_ROOT_INSPECT_REJECT "):])
            blocks.append({"seed": int(item["seed"]),
                           "candidate": int(item["candidate"]),
                           "variant": "EXACT_ROOT",
                           "status": "REJECTED",
                           "reason": item["reason"],
                           "parts": [], "nets": []})
            continue
        if current is None:
            continue
        if line.startswith("Q30_ROOT_PART "):
            item = json.loads(line[len("Q30_ROOT_PART "):])
            item["bounds"] = rect(item["bounds"])
            item["courtyard"] = rect(item["courtyard"])
            item["pads"] = parse_q30_pad_summary(item["pads"])
            current["parts"].append(item)
        elif line.startswith("Q30_ROOT_NET "):
            item = json.loads(line[len("Q30_ROOT_NET "):])
            item["pads"] = parse_net_pads(item["pads"])
            item["box"] = rect_from_box(item["box"])
            current["nets"].append(item)
        elif line.startswith("Q30_ROOT_INSPECT "):
            raise AssertionError("unreachable nested inspect at line %d" % line_number)
    if current is not None:
        blocks.append(current)
    if not blocks:
        raise ValueError("no Q30_ROOT_INSPECT blocks in %s" % path)
    for block in blocks:
        if block.get("status") != "REJECTED":
            validate_block(block, expected_parts)
    return blocks


def rect_from_box(value):
    fields = [int(item) for item in value.split(",")]
    if len(fields) != 4:
        raise ValueError("invalid net box: %r" % value)
    return {"x": fields[0], "y": fields[1],
            "width": fields[2] - fields[0],
            "height": fields[3] - fields[1]}


def validate_block(block, expected_parts=None):
    if expected_parts is not None and len(block["parts"]) != expected_parts:
        raise ValueError("Q30 seed %s candidate %s has %d parts, expected %d" %
                         (block["seed"], block["candidate"], len(block["parts"]),
                          expected_parts))
    if len(block["parts"]) < 1:
        raise ValueError("Q30 inspect block has no parts")
    if len(block["nets"]) < 1:
        raise ValueError("Q30 seed %s candidate %s has unexpected net count %d" %
                         (block["seed"], block["candidate"], len(block["nets"])))
    part_ids = [item["id"] for item in block["parts"]]
    if len(set(part_ids)) != len(part_ids):
        raise ValueError("duplicate Q30 part in seed/candidate")
    pad_ids = set()
    for part in block["parts"]:
        for pad_id in part["pads"]:
            if pad_id in pad_ids:
                raise ValueError("duplicate Q30 pad %s" % pad_id)
            pad_ids.add(pad_id)
    net_pad_ids = set()
    for net in block["nets"]:
        for pad_id in net["pads"]:
            if pad_id not in pad_ids:
                raise ValueError("net references unknown Q30 pad %s" % pad_id)
            if pad_id in net_pad_ids:
                raise ValueError("Q30 pad appears in two nets: %s" % pad_id)
            net_pad_ids.add(pad_id)
    if pad_ids != net_pad_ids:
        raise ValueError("Q30 pad/net incidence is incomplete")


def parse_package_counts(value):
    result = {}
    for key, amount in re.findall(r"([A-Za-z0-9_]+)=(\d+)", value):
        result[key] = int(amount)
    return result


def parse_synthetic_rows(path):
    rows = []
    for line_number, raw in enumerate(pathlib.Path(path).read_text(
            encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line.startswith("SCALE_ROW "):
            continue
        row = json.loads(line[len("SCALE_ROW "):])
        if row.get("count") == 33:
            row["_line"] = line_number
            rows.append(row)
    if not rows:
        raise ValueError("no count=33 SCALE_ROW records in %s" % path)
    return rows


def synthetic_components(count, seed, row):
    if count != 33:
        raise ValueError("this comparison reconstructs the 33-part fixture only")
    channels = (count - 5) // 7
    extras = count - 5 - 7 * channels
    if channels != 4 or extras != 0:
        raise AssertionError("unexpected 33-part fixture arithmetic")

    components = []
    nets = collections.OrderedDict()

    def add(component_id, package, terminal_count, col, board_row,
            region, connector, net_ids):
        if len(net_ids) != terminal_count:
            raise AssertionError("terminal count for %s" % component_id)
        package_dims = {
            "P03_scale2": (140, 150, 2),
            "P03_scale3": (140, 150, 3),
            "P03_scale4": (140, 150, 4),
            "P03_scale5": (140, 150, 5),
        }
        width, height, shape_count = package_dims[package]
        if shape_count != terminal_count:
            raise AssertionError("fixture shape mismatch for %s" % component_id)
        random_seed = java_long(java_long(seed) * 1315423911 +
                                java_string_hash(component_id))
        random = JavaRandom(random_seed)
        x = 110 + col * 220 + (random.next_int(3) - 1) * 10
        y = 110 + board_row * 240 + (random.next_int(3) - 1) * 10
        pads = collections.OrderedDict()
        for index, net_id in enumerate(net_ids):
            left = index % 2 == 0
            pad_y = 30 + (index // 2) * 30
            pad_x = 20 if left else width - 20
            terminal = str(index + 1)
            pad_id = component_id + "." + terminal
            pads[pad_id] = {"id": pad_id, "x": x + pad_x,
                            "y": y + pad_y, "net": net_id}
            role = ("RETURN" if net_id == "GND" else
                    "SUPPLY" if net_id in ("VIN", "VRAW", "V5") else
                    "SIGNAL")
            nets.setdefault(net_id, {"id": net_id, "role": role,
                                     "pads": collections.OrderedDict()})
            nets[net_id]["pads"][pad_id] = {
                "id": pad_id, "x": x + pad_x, "y": y + pad_y,
                "component": component_id,
            }
        components.append({
            "id": component_id, "package": package, "region": region,
            "connector": connector,
            "bounds": {"x": x, "y": y, "width": width, "height": height},
            "courtyard": {"x": x, "y": y, "width": width, "height": height},
            "pads": pads,
        })

    add("JIN", "P03_scale2", 2, 0, 0, "POWER", True, ["VIN", "GND"])
    add("FUSE", "P03_scale2", 2, 1, 0, "POWER", False, ["VIN", "VRAW"])
    add("REG", "P03_scale4", 4, 2, 0, "POWER", False,
        ["VRAW", "V5", "GND", "VRAW"])
    add("CIN", "P03_scale2", 2, 3, 0, "POWER", False, ["VRAW", "GND"])
    add("COUT", "P03_scale2", 2, 4, 0, "POWER", False, ["V5", "GND"])
    for channel in range(channels):
        board_row = channel + 1
        prefix = "CH%d_" % channel
        sensor = prefix + "SNS"
        reference = prefix + "REF"
        drive = prefix + "DRV"
        coil = prefix + "COIL"
        output = prefix + "OUT"
        region = "CH%d" % channel
        add(prefix + "JS", "P03_scale2", 2, 0, board_row, region,
            True, [sensor, "GND"])
        add(prefix + "RREF", "P03_scale2", 2, 1, board_row, region,
            False, ["V5", reference])
        add(prefix + "CTRL", "P03_scale5", 5, 2, board_row, region,
            False, [sensor, reference, "V5", drive, "GND"])
        add(prefix + "Q", "P03_scale3", 3, 3, board_row, region,
            False, [drive, coil, "GND"])
        add(prefix + "RELAY", "P03_scale5", 5, 4, board_row, region,
            False, [coil, "VRAW", "VRAW", output, "GND"])
        add(prefix + "JOUT", "P03_scale2", 2, 5, board_row, region,
            True, [output, "GND"])
        add(prefix + "DIODE", "P03_scale2", 2, 6, board_row, region,
            False, [coil, "VRAW"])

    outline = {"x": 30, "y": 30,
               "width": int(row["boardWidth"]),
               "height": int(row["boardHeight"])}
    if outline["width"] != 2300 or outline["height"] != 1540:
        raise ValueError("unexpected 33-part synthetic outline")
    return {
        "seed": int(seed), "candidate": 0, "variant": "SCALE_FIXTURE",
        "outline": outline, "parts": components,
        "nets": list(nets.values()),
        "route": row,
        "packageCounts": parse_package_counts(row["packageCounts"]),
    }


def point_distance(first, second):
    return abs(first[0] - second[0]) + abs(first[1] - second[1])


def median_or_none(values):
    return statistics.median(values) if values else None


def mean_or_none(values):
    return (sum(values) / float(len(values))) if values else None


def percentile_or_none(values, fraction):
    """Linear-interpolated percentile using the (n - 1) index convention."""
    if not values:
        return None
    ordered = sorted(values)
    position = (len(ordered) - 1) * fraction
    lower = int(math.floor(position))
    upper = int(math.ceil(position))
    if lower == upper:
        return ordered[lower]
    weight = position - lower
    return ordered[lower] + weight * (ordered[upper] - ordered[lower])


def channel_for_region(region):
    """Return only mechanically source-backed channel groupings.

    Q30 root regions use a and b suffixes for the two repeated paths.  The
    synthetic fixture names its repeated regions CH0..CH3 directly.  Other
    regions are deliberately left unassigned instead of being given an
    inferred circuit meaning.
    """
    match = re.match(r"^CH(\d+)$", region)
    if match:
        return "CH" + match.group(1)
    if region.endswith("-a"):
        return "channel-a"
    if region.endswith("-b"):
        return "channel-b"
    return None


def aspect_or_none(width, height):
    return width / float(height) if height else None


def group_geometry(parts, component_distances, electrical_distances,
                   net_hpls, overlap_pairs, edge_clearances):
    """Summarize one exact region or mechanical channel group."""
    courtyards = [part["courtyard"] for part in parts]
    courtyard_area = sum(rect_area(value) for value in courtyards)
    if courtyards:
        left = min(value["x"] for value in courtyards)
        top = min(value["y"] for value in courtyards)
        right = max(value["x"] + value["width"] for value in courtyards)
        bottom = max(value["y"] + value["height"] for value in courtyards)
        envelope_width = right - left
        envelope_height = bottom - top
    else:
        left = top = envelope_width = envelope_height = 0
    envelope_area = envelope_width * envelope_height
    return {
        "componentCount": len(parts),
        "padCount": sum(len(part["pads"]) for part in parts),
        "courtyardArea": courtyard_area,
        "envelopeX": left,
        "envelopeY": top,
        "envelopeWidth": envelope_width,
        "envelopeHeight": envelope_height,
        "envelopeArea": envelope_area,
        "envelopeAspect": aspect_or_none(envelope_width, envelope_height),
        "courtyardFractionOfEnvelope": (
            courtyard_area / float(envelope_area) if envelope_area else None),
        "courtyardOverlapPairs": overlap_pairs,
        "minimumCourtyardEdgeClearance": (
            min(edge_clearances) if edge_clearances else None),
        "componentPairCount": len(component_distances),
        "componentPairDistanceMean": mean_or_none(component_distances),
        "componentPairDistanceMedian": median_or_none(component_distances),
        "componentPairDistanceP95": percentile_or_none(component_distances, .95),
        "electricallyAdjacentPadPairCount": len(electrical_distances),
        "electricallyAdjacentPadDistanceMean": mean_or_none(
            electrical_distances),
        "electricallyAdjacentPadDistanceP95": percentile_or_none(
            electrical_distances, .95),
        "netCount": len(net_hpls),
        "netHplTotal": sum(net_hpls),
        "netHplMean": mean_or_none(net_hpls),
        "netHplP95": percentile_or_none(net_hpls, .95),
    }


def prim_mst(points):
    if len(points) < 2:
        return [], 0
    reached = {0}
    edges = []
    total = 0
    while len(reached) < len(points):
        best = None
        for source in sorted(reached):
            for target in range(len(points)):
                if target in reached:
                    continue
                candidate = (point_distance(points[source], points[target]),
                             source, target)
                if best is None or candidate < best:
                    best = candidate
        distance, source, target = best
        reached.add(target)
        edges.append((points[source], points[target]))
        total += distance
    return edges, total


def orientation(a, b, c):
    return ((b[0] - a[0]) * (c[1] - a[1]) -
            (b[1] - a[1]) * (c[0] - a[0]))


def proper_segment_cross(first, second):
    a, b = first
    c, d = second
    if a in (c, d) or b in (c, d):
        return False
    one = orientation(a, b, c)
    two = orientation(a, b, d)
    three = orientation(c, d, a)
    four = orientation(c, d, b)
    return ((one > 0 and two < 0) or (one < 0 and two > 0)) and \
        ((three > 0 and four < 0) or (three < 0 and four > 0))


def model_metrics(model):
    parts = model["parts"]
    nets = model["nets"]
    outline = model["outline"]
    part_by_id = {part["id"]: part for part in parts}
    pad_by_id = {}
    for part in parts:
        for pad_id, pad in part["pads"].items():
            pad["component"] = part["id"]
            pad_by_id[pad_id] = pad

    net_boxes = {}
    net_edges = []
    net_hpl = []
    net_mst = []
    net_infos = []
    net_info_by_id = {}
    pad_net = {}
    component_pairs = []
    region_pair_counts = collections.Counter()
    region_counts = collections.Counter(part["region"] for part in parts)
    region_crossing_nets = 0
    region_transition_count = 0
    for net in nets:
        pad_values = list(net["pads"].values())
        points = [(pad["x"], pad["y"]) for pad in pad_values]
        component_ids = sorted(set(pad["component"] for pad in pad_values))
        regions = sorted(set(part_by_id[item]["region"] for item in component_ids))
        box = {"x": min((point[0] for point in points), default=0),
               "y": min((point[1] for point in points), default=0),
               "width": (max((point[0] for point in points), default=0) -
                         min((point[0] for point in points), default=0)),
               "height": (max((point[1] for point in points), default=0) -
                          min((point[1] for point in points), default=0))}
        hpl = box["width"] + box["height"]
        info = {
            "id": net["id"],
            "role": str(net.get("role") or "UNSPECIFIED"),
            "padCount": len(pad_values),
            "componentCount": len(component_ids),
            "regions": regions,
            "hpl": hpl,
        }
        net_infos.append(info)
        net_info_by_id[net["id"]] = info
        for pad in pad_values:
            pad_net[pad["id"]] = net
        if len(points) < 2:
            continue
        net_boxes[net["id"]] = box
        edges, mst = prim_mst(points)
        net_edges.extend((net["id"], edge) for edge in edges)
        net_hpl.append(hpl)
        net_mst.append(mst)
        if len(regions) > 1:
            region_crossing_nets += 1
            region_transition_count += len(regions) - 1
        for index, first_id in enumerate(component_ids):
            for second_id in component_ids[index + 1:]:
                first_region = part_by_id[first_id]["region"]
                second_region = part_by_id[second_id]["region"]
                key = "|".join(sorted((first_region, second_region)))
                region_pair_counts[key] += 1
                first_center = part_center(part_by_id[first_id])
                second_center = part_center(part_by_id[second_id])
                component_pairs.append({
                    "distance": point_distance(first_center, second_center),
                    "sameRegion": first_region == second_region,
                    "regions": key,
                    "firstRegion": first_region,
                    "secondRegion": second_region,
                })

    # A pre-route electrically adjacent package-pad pair is every unordered
    # pair of pads on the same net whose pads belong to different packages.
    # This intentionally counts all pairs on a fanout net, not just an MST.
    electrical_pairs = []
    electrical_distances_by_region = collections.defaultdict(list)
    electrical_distances_by_channel = collections.defaultdict(list)
    for net in nets:
        pads = list(net["pads"].values())
        for index, first in enumerate(pads):
            for second in pads[index + 1:]:
                if first["component"] == second["component"]:
                    continue
                first_region = part_by_id[first["component"]]["region"]
                second_region = part_by_id[second["component"]]["region"]
                first_channel = channel_for_region(first_region)
                second_channel = channel_for_region(second_region)
                distance = point_distance((first["x"], first["y"]),
                                          (second["x"], second["y"]))
                electrical_pairs.append({
                    "net": net["id"], "distance": distance,
                    "firstRegion": first_region,
                    "secondRegion": second_region,
                    "firstChannel": first_channel,
                    "secondChannel": second_channel,
                })
                for region in set((first_region, second_region)):
                    electrical_distances_by_region[region].append(distance)
                channels = set((first_channel, second_channel))
                channels.discard(None)
                for channel in channels:
                    electrical_distances_by_channel[channel].append(distance)

    ratsnest_crossings = 0
    for index, first in enumerate(net_edges):
        for second in net_edges[index + 1:]:
            if first[0] == second[0]:
                continue
            if proper_segment_cross(first[1], second[1]):
                ratsnest_crossings += 1
    bbox_overlaps = 0
    net_ids = sorted(net_boxes)
    for index, first_id in enumerate(net_ids):
        for second_id in net_ids[index + 1:]:
            if rect_intersection(net_boxes[first_id], net_boxes[second_id]) > 0:
                bbox_overlaps += 1

    courtyard_area = sum(rect_area(part["courtyard"]) for part in parts)
    courtyard_overlap_pairs = 0
    courtyard_overlap_area = 0
    outside_count = 0
    edge_clearances = []
    for index, first in enumerate(parts):
        courtyard = first["courtyard"]
        left = courtyard["x"] - outline["x"]
        top = courtyard["y"] - outline["y"]
        right = (outline["x"] + outline["width"] -
                 courtyard["x"] - courtyard["width"])
        bottom = (outline["y"] + outline["height"] -
                  courtyard["y"] - courtyard["height"])
        edge_clearances.extend((left, top, right, bottom))
        if min(left, top, right, bottom) < 0:
            outside_count += 1
        for second in parts[index + 1:]:
            overlap = rect_intersection(courtyard, second["courtyard"])
            if overlap > 0:
                courtyard_overlap_pairs += 1
                courtyard_overlap_area += overlap

    connector_ids = [part["id"] for part in parts if part.get("connector")]
    connector_pad_distances = []
    connector_edge_margins = []
    connector_crossing_incidences = 0
    connector_incidences = 0
    connector_metrics = {}
    for component_id in connector_ids:
        part = part_by_id[component_id]
        connector_pad_distances_for_part = []
        connector_edge_margins_for_part = []
        connector_crossing_for_part = 0
        connector_incidences_for_part = 0
        for pad in part["pads"].values():
            same_net = pad_net.get(pad["id"])
            if same_net is None:
                continue
            others = [other for other in same_net["pads"].values()
                      if other["component"] != component_id]
            if others:
                nearest = min(
                    point_distance((pad["x"], pad["y"]),
                                   (other["x"], other["y"]))
                    for other in others)
                connector_pad_distances.append(nearest)
                connector_pad_distances_for_part.append(nearest)
            edge_margin = min(
                pad["x"] - outline["x"],
                pad["y"] - outline["y"],
                outline["x"] + outline["width"] - pad["x"],
                outline["y"] + outline["height"] - pad["y"])
            connector_edge_margins.append(edge_margin)
            connector_edge_margins_for_part.append(edge_margin)
            connector_incidences += 1
            connector_incidences_for_part += 1
            regions = set(part_by_id[other["component"]]["region"]
                          for other in others)
            regions.add(part["region"])
            if len(regions) > 1:
                connector_crossing_incidences += 1
                connector_crossing_for_part += 1
        courtyard = part["courtyard"]
        connector_metrics[component_id] = {
            "region": part["region"],
            "padCount": len(part["pads"]),
            "courtyardArea": rect_area(courtyard),
            "courtyardWidth": courtyard["width"],
            "courtyardHeight": courtyard["height"],
            "courtyardAspect": aspect_or_none(courtyard["width"],
                                               courtyard["height"]),
            "nearestSameNetPadDistanceCount": len(
                connector_pad_distances_for_part),
            "nearestSameNetPadDistanceMean": mean_or_none(
                connector_pad_distances_for_part),
            "nearestSameNetPadDistanceMedian": median_or_none(
                connector_pad_distances_for_part),
            "nearestSameNetPadDistanceP95": percentile_or_none(
                connector_pad_distances_for_part, .95),
            "minimumBoardEdgeMargin": (
                min(connector_edge_margins_for_part)
                if connector_edge_margins_for_part else None),
            "meanBoardEdgeMargin": mean_or_none(
                connector_edge_margins_for_part),
            "boardEdgeMarginP95": percentile_or_none(
                connector_edge_margins_for_part, .95),
            "crossRegionIncidenceFraction": (
                connector_crossing_for_part /
                float(connector_incidences_for_part)
                if connector_incidences_for_part else None),
        }

    pair_distances = [item["distance"] for item in component_pairs]
    same_region_pairs = [item for item in component_pairs if item["sameRegion"]]
    board_area = outline["width"] * outline["height"]

    # Region and channel diagnostics use the exact exported courtyards.  A
    # cross-region pair is incident to both endpoint groups, which keeps each
    # group's local burden visible without pretending that it belongs to one
    # side only.
    region_parts = collections.defaultdict(list)
    channel_parts = collections.defaultdict(list)
    region_component_distances = collections.defaultdict(list)
    channel_component_distances = collections.defaultdict(list)
    region_net_hpls = collections.defaultdict(list)
    channel_net_hpls = collections.defaultdict(list)
    region_edge_clearances = collections.defaultdict(list)
    channel_edge_clearances = collections.defaultdict(list)
    region_overlap_pairs = collections.Counter()
    channel_overlap_pairs = collections.Counter()
    for part in parts:
        region = part["region"]
        channel = channel_for_region(region)
        region_parts[region].append(part)
        if channel is not None:
            channel_parts[channel].append(part)
        courtyard = part["courtyard"]
        edges = [
            courtyard["x"] - outline["x"],
            courtyard["y"] - outline["y"],
            outline["x"] + outline["width"] -
            courtyard["x"] - courtyard["width"],
            outline["y"] + outline["height"] -
            courtyard["y"] - courtyard["height"],
        ]
        region_edge_clearances[region].extend(edges)
        if channel is not None:
            channel_edge_clearances[channel].extend(edges)
    for item in component_pairs:
        for region in set((item["firstRegion"], item["secondRegion"])):
            region_component_distances[region].append(item["distance"])
        channels = set((channel_for_region(item["firstRegion"]),
                        channel_for_region(item["secondRegion"])))
        channels.discard(None)
        for channel in channels:
            channel_component_distances[channel].append(item["distance"])
    for info in net_infos:
        if info["padCount"] < 2:
            continue
        for region in info["regions"]:
            region_net_hpls[region].append(info["hpl"])
        channels = set(channel_for_region(region) for region in info["regions"])
        channels.discard(None)
        for channel in channels:
            channel_net_hpls[channel].append(info["hpl"])
    for index, first in enumerate(parts):
        for second in parts[index + 1:]:
            overlap = rect_intersection(first["courtyard"], second["courtyard"])
            if overlap <= 0:
                continue
            if first["region"] == second["region"]:
                region_overlap_pairs[first["region"]] += 1
            first_channel = channel_for_region(first["region"])
            second_channel = channel_for_region(second["region"])
            if first_channel is not None and first_channel == second_channel:
                channel_overlap_pairs[first_channel] += 1

    region_metrics = {}
    for region in sorted(region_parts):
        region_metrics[region] = group_geometry(
            region_parts[region], region_component_distances[region],
            electrical_distances_by_region[region], region_net_hpls[region],
            region_overlap_pairs[region], region_edge_clearances[region])
    channel_metrics = {}
    for channel in sorted(channel_parts):
        channel_metrics[channel] = group_geometry(
            channel_parts[channel], channel_component_distances[channel],
            electrical_distances_by_channel[channel], channel_net_hpls[channel],
            channel_overlap_pairs[channel], channel_edge_clearances[channel])

    role_groups = collections.defaultdict(list)
    for info in net_infos:
        role_groups[info["role"]].append(info)

    def role_metrics(infos):
        routeable = [info for info in infos if info["padCount"] >= 2]
        hpls = [info["hpl"] for info in routeable]
        fanouts = [info["padCount"] for info in infos]
        max_fanout = max(fanouts) if fanouts else 0
        max_infos = [info for info in infos
                     if info["padCount"] == max_fanout]
        max_hpls = [info["hpl"] for info in max_infos]
        return {
            "netCount": len(infos),
            "padCountTotal": sum(info["padCount"] for info in infos),
            "maxFanout": max_fanout,
            "netFanoutMean": mean_or_none(fanouts),
            "netFanoutP95": percentile_or_none(fanouts, .95),
            "maxFanoutNetCount": len(max_infos),
            "maxFanoutNetIds": sorted(info["id"] for info in max_infos),
            "maxFanoutNetHplMean": mean_or_none(max_hpls),
            "maxFanoutNetHplP95": percentile_or_none(max_hpls, .95),
            "hplNetCount": len(routeable),
            "hplTotal": sum(hpls),
            "hplMean": mean_or_none(hpls),
            "hplMedian": median_or_none(hpls),
            "hplP95": percentile_or_none(hpls, .95),
            "hplMax": max(hpls) if hpls else None,
        }

    role_metric_values = {
        role: role_metrics(role_groups[role])
        for role in sorted(role_groups)
    }
    max_fanout = max((info["padCount"] for info in net_infos), default=0)
    max_fanout_infos = [info for info in net_infos
                        if info["padCount"] == max_fanout]
    max_fanout_hpls = [info["hpl"] for info in max_fanout_infos]
    courtyard_rects = [part["courtyard"] for part in parts]
    courtyard_left = min(value["x"] for value in courtyard_rects)
    courtyard_top = min(value["y"] for value in courtyard_rects)
    courtyard_right = max(value["x"] + value["width"]
                           for value in courtyard_rects)
    courtyard_bottom = max(value["y"] + value["height"]
                            for value in courtyard_rects)
    courtyard_envelope_width = courtyard_right - courtyard_left
    courtyard_envelope_height = courtyard_bottom - courtyard_top
    courtyard_envelope_area = (courtyard_envelope_width *
                                courtyard_envelope_height)
    metrics = {
        "structure": {
            "parts": len(parts), "pads": len(pad_by_id), "nets": len(nets),
            "connectors": len(connector_ids),
            "connectorPads": sum(len(part_by_id[item]["pads"])
                                  for item in connector_ids),
            "regionCounts": dict(sorted(region_counts.items())),
        },
        "area": {
            "boardArea": board_area,
            "boardWidth": outline["width"], "boardHeight": outline["height"],
            "boardAspect": aspect_or_none(outline["width"], outline["height"]),
            "courtyardArea": courtyard_area,
            "courtyardFraction": courtyard_area / float(board_area),
            "courtyardEnvelopeWidth": courtyard_envelope_width,
            "courtyardEnvelopeHeight": courtyard_envelope_height,
            "courtyardEnvelopeArea": courtyard_envelope_area,
            "courtyardEnvelopeAspect": aspect_or_none(
                courtyard_envelope_width, courtyard_envelope_height),
            "courtyardFractionOfEnvelope": (
                courtyard_area / float(courtyard_envelope_area)
                if courtyard_envelope_area else None),
            "courtyardOverlapPairs": courtyard_overlap_pairs,
            "courtyardOverlapArea": courtyard_overlap_area,
            "courtyardOutsideCount": outside_count,
            "minimumCourtyardEdgeClearance": min(edge_clearances),
        },
        "locality": {
            "netHplTotal": sum(net_hpl),
            "netHplMean": mean_or_none(net_hpl),
            "netHplMedian": median_or_none(net_hpl),
            "netMstTotal": sum(net_mst),
            "netMstMean": mean_or_none(net_mst),
            "netMstMedian": median_or_none(net_mst),
            "componentPairCount": len(component_pairs),
            "componentPairDistanceMean": mean_or_none(pair_distances),
            "componentPairDistanceMedian": median_or_none(pair_distances),
            "componentPairDistanceP95": percentile_or_none(pair_distances, .95),
            "electricallyAdjacentPadPairCount": len(electrical_pairs),
            "electricallyAdjacentPadDistanceMean": mean_or_none(
                [item["distance"] for item in electrical_pairs]),
            "electricallyAdjacentPadDistanceP95": percentile_or_none(
                [item["distance"] for item in electrical_pairs], .95),
            "sameRegionPairFraction": (
                len(same_region_pairs) / float(len(component_pairs))
                if component_pairs else None),
            "regionCrossingNetCount": region_crossing_nets,
            "regionCrossingNetFraction": (
                region_crossing_nets / float(len(nets)) if nets else None),
            "regionTransitionCount": region_transition_count,
            "regionPairEdgeCounts": dict(sorted(region_pair_counts.items())),
            "maxNetFanout": max_fanout,
            "maxFanoutNetCount": len(max_fanout_infos),
            "maxFanoutNetIds": sorted(info["id"]
                                       for info in max_fanout_infos),
            "maxFanoutNetHplMean": mean_or_none(max_fanout_hpls),
            "maxFanoutNetHplP95": percentile_or_none(max_fanout_hpls, .95),
            "netRoleMetrics": role_metric_values,
            "regionMetrics": region_metrics,
            "channelMetrics": channel_metrics,
        },
        "crossing": {
            "ratsnestSegmentCount": len(net_edges),
            "estimatedRatsnestCrossings": ratsnest_crossings,
            "netBoundingBoxOverlapPairs": bbox_overlaps,
        },
        "connector": {
            "count": len(connector_ids),
            "padCount": sum(len(part_by_id[item]["pads"])
                             for item in connector_ids),
            "nearestSameNetPadDistanceMean": mean_or_none(connector_pad_distances),
            "nearestSameNetPadDistanceMedian": median_or_none(connector_pad_distances),
            "nearestSameNetPadDistanceP95": percentile_or_none(
                connector_pad_distances, .95),
            "minimumBoardEdgeMargin": min(connector_edge_margins)
            if connector_edge_margins else None,
            "meanBoardEdgeMargin": mean_or_none(connector_edge_margins),
            "boardEdgeMarginP95": percentile_or_none(
                connector_edge_margins, .95),
            "crossRegionIncidenceFraction": (
                connector_crossing_incidences / float(connector_incidences)
                if connector_incidences else None),
            "byId": connector_metrics,
        },
    }
    return metrics


def part_center(part):
    bounds = part["bounds"]
    return (bounds["x"] + bounds["width"] / 2.0,
            bounds["y"] + bounds["height"] / 2.0)


def q30_model(block):
    parts = []
    for source in block["parts"]:
        item = dict(source)
        item["region"] = q30_region(item["id"])
        item["connector"] = item["id"] in Q30_CONNECTORS
        for pad in item["pads"].values():
            pad["net"] = None
        parts.append(item)
    part_by_id = {part["id"]: part for part in parts}
    nets = []
    for source in block["nets"]:
        item = dict(source)
        pads = collections.OrderedDict()
        for pad_id, pad in source["pads"].items():
            component_id = pad_id.split(".", 1)[0]
            if component_id not in part_by_id:
                raise ValueError("unknown Q30 net component %s" % component_id)
            part_pad = part_by_id[component_id]["pads"][pad_id]
            part_pad["net"] = item["id"]
            copy = dict(pad)
            copy["component"] = component_id
            pads[pad_id] = copy
        item["pads"] = pads
        nets.append(item)
    for part in parts:
        if any(pad.get("net") is None for pad in part["pads"].values()):
            raise ValueError("Q30 part has an unassigned pad: %s" % part["id"])
    return {
        "seed": block["seed"], "candidate": block["candidate"],
        "variant": block["variant"], "outline": block["outline"],
        "parts": parts, "nets": nets,
    }


def synthetic_model(row):
    model = synthetic_components(int(row["count"]), int(row["seed"]), row)
    return model


def flatten_numeric(value, prefix=""):
    result = {}
    if isinstance(value, dict):
        for key, child in value.items():
            next_prefix = prefix + "." + key if prefix else key
            result.update(flatten_numeric(child, next_prefix))
    elif isinstance(value, (int, float)) and not isinstance(value, bool):
        result[prefix] = value
    return result


def numeric_summary(rows):
    values = collections.defaultdict(list)
    for row in rows:
        for key, value in flatten_numeric(row["metrics"]).items():
            if value is not None:
                values[key].append(value)
    result = {}
    for key in sorted(values):
        ordered = sorted(values[key])
        result[key] = {
            "min": ordered[0], "median": statistics.median(ordered),
            "mean": sum(ordered) / float(len(ordered)), "max": ordered[-1],
            "count": len(ordered),
        }
    return result


def route_summary(rows):
    successes = [row for row in rows
                 if row["route"].get("outcome") == "SUCCESS" and
                 row["route"].get("validMixedLayer") is True]
    outcomes = collections.Counter(row["route"].get("outcome") for row in rows)
    fields = {}
    for key in ("expansions", "routeMs", "vias", "topSegments",
                "bottomSegments", "topLength", "bottomLength"):
        values = [row["route"][key] for row in successes
                  if key in row["route"]]
        if values:
            fields[key] = {
                "min": min(values), "median": statistics.median(values),
                "mean": sum(values) / float(len(values)), "max": max(values),
                "count": len(values),
            }
    return {"attempts": len(rows), "successes": len(successes),
            "outcomes": dict(sorted(outcomes.items())),
            "successfulRouteMetrics": fields}


def add_comparison(q30_summary, synthetic_summary, keys):
    result = {}
    for key in keys:
        first = q30_summary.get(key)
        second = synthetic_summary.get(key)
        if first is None or second is None:
            continue
        first_value = first["median"]
        second_value = second["median"]
        result[key] = {
            "q30Median": first_value,
            "syntheticMedian": second_value,
            "syntheticMinusQ30": second_value - first_value,
            "syntheticOverQ30": (second_value / first_value
                                  if first_value != 0 else None),
        }
    return result


def build_output(q30_blocks, synthetic_rows, args):
    q30_rows = []
    for block in q30_blocks:
        if block.get("status") == "REJECTED":
            continue
        model = q30_model(block)
        q30_rows.append({
            "seed": model["seed"], "candidate": model["candidate"],
            "variant": model["variant"], "outline": model["outline"],
            "parts": model["parts"], "nets": model["nets"],
            "metrics": model_metrics(model),
        })
    synthetic_rows_out = []
    for row in synthetic_rows:
        if row.get("policy") != SYNTHETIC_SUCCESS_POLICY:
            continue
        model = synthetic_model(row)
        if row.get("outcome") == "SUCCESS" and row.get("validMixedLayer") is True:
            synthetic_rows_out.append({
                "seed": model["seed"], "candidate": 0,
                "variant": model["variant"], "outline": model["outline"],
                "parts": model["parts"], "nets": model["nets"],
                "metrics": model_metrics(model), "route": row,
            })
    if not synthetic_rows_out:
        raise ValueError("no validated successful synthetic 33-part rows")

    q30_summary = numeric_summary(q30_rows)
    synthetic_summary = numeric_summary(synthetic_rows_out)
    comparison_keys = [
        "structure.parts", "structure.pads", "structure.nets",
        "structure.connectors", "area.boardArea", "area.courtyardArea",
        "area.courtyardFraction", "area.boardAspect",
        "area.courtyardEnvelopeAspect", "locality.netHplTotal",
        "locality.netMstTotal", "locality.componentPairDistanceMedian",
        "locality.electricallyAdjacentPadPairCount",
        "locality.electricallyAdjacentPadDistanceMean",
        "locality.electricallyAdjacentPadDistanceP95",
        "locality.maxNetFanout", "locality.maxFanoutNetHplMean",
        "locality.maxFanoutNetHplP95",
        "locality.netRoleMetrics.SUPPLY.hplTotal",
        "locality.netRoleMetrics.SUPPLY.hplP95",
        "locality.netRoleMetrics.SUPPLY.maxFanout",
        "locality.netRoleMetrics.RETURN.hplTotal",
        "locality.netRoleMetrics.RETURN.hplP95",
        "locality.netRoleMetrics.RETURN.maxFanout",
        "locality.sameRegionPairFraction", "locality.regionCrossingNetFraction",
        "locality.regionTransitionCount", "crossing.estimatedRatsnestCrossings",
        "crossing.netBoundingBoxOverlapPairs",
        "connector.nearestSameNetPadDistanceMedian",
        "connector.nearestSameNetPadDistanceP95",
        "connector.crossRegionIncidenceFraction",
        "area.courtyardOverlapPairs", "area.minimumCourtyardEdgeClearance",
    ]
    fixture_provenance = {
        "q30InspectInput": str(pathlib.Path(args.q30_inspect).name),
        "syntheticRawInput": str(pathlib.Path(args.synthetic_raw).name),
        "q30InspectSha256": sha256(args.q30_inspect),
        "syntheticRawSha256": sha256(args.synthetic_raw),
        "q30PlacementSource":
            "RootPlanManifest.EXACT_ROOT -> Rb30Plan.resolve(seed).board()",
        "q30SourceSha256": args.q30_source_sha256,
        "q30PlanSourceSha256": args.q30_plan_sha256,
        "q30PlannerSourceSha256": args.q30_planner_sha256,
        "q30PlannerInput": args.q30_planner_input,
        "q30PlannerSourceMode": args.q30_planner_mode,
        "plannerLabel": args.planner_label,
        "measurementLabel": args.measurement_label,
        "measurementStatus": args.measurement_status,
        "q30FloorplanPatchSha256": args.q30_patch_sha256,
        "syntheticFixtureSourceSha256": args.synthetic_source_sha256,
        "q30Seeds": sorted(set(block["seed"] for block in q30_blocks)),
        "q30CandidatesPerSeed": len(q30_blocks) // len(set(block["seed"] for block in q30_blocks)),
        "q30RejectedAttempts": len([block for block in q30_blocks
                                     if block.get("status") == "REJECTED"]),
        "q30RejectedReasons": dict(sorted(collections.Counter(
            block.get("reason") for block in q30_blocks
            if block.get("status") == "REJECTED").items())),
        "syntheticSuccessfulSeeds": sorted(row["seed"] for row in synthetic_rows_out),
    }
    result = {
        "schema": 1,
        "kind": "Q30-P1 floorplan diagnosis; structural developer evidence",
        "measurement": {
            "label": args.measurement_label,
            "status": args.measurement_status,
            "plannerLabel": args.planner_label,
        },
        "fixtureProvenance": fixture_provenance,
        "methodology": {
            "q30Population": "All actual exact-root placements for every requested seed and candidate; no best-row filtering.",
            "q30Source": "Current Q30PhysicalPilot RootPlanManifest.EXACT_ROOT delegates to Rb30Plan.resolve(seed).board().",
            "syntheticPopulation": "Every count=33 FULLER_TWO_LAYER row marked SUCCESS and validMixedLayer=true in thirtythree-raw.txt.",
            "regionMapping": "Q30 uses RootPlanManifest.rootPlacement() region expression; synthetic uses Q30TwoLayerScaling.fixture() POWER/CH<n> domains.",
            "ratsnestHpl": "For each net, pad bounding-box half-perimeter (Manhattan dx+dy), summed across nets.",
            "ratsnestMst": "Deterministic Manhattan minimum spanning tree lower-bound over each net's pad coordinates.",
            "componentPairDistance": "For component pairs sharing a net, Manhattan distance between component bounds centers.",
            "electricalAdjacency": "For each net, every unordered pair of pads from different packages; distance is Manhattan pad-center distance before routing. A fanout net therefore contributes all cross-package pairs.",
            "percentile": "p95 sorts the row values and linearly interpolates at index (n - 1) * 0.95; it does not round to a nearest rank.",
            "fanoutAndRoleHpl": "Net fanout is pad count, including one-pad nets for the fanout maximum. HPL is net pad-box width plus height; HPL totals and percentiles include nets with at least two pads. Roles are copied from the Q30 exporter or authored synthetic fixture. Maximum-fanout HPL metrics summarize all tied maximum-fanout nets.",
            "regionTransitions": "For each net, the number of unique regions minus one; this is a region-incidence proxy, not ordered trace transitions.",
            "regionDiagnostics": "Each exact source-backed region reports its component/courtyard union envelope, area, aspect (width/height), courtyard fraction, edge margin, pair locality, electrical pad adjacency, and incident-net HPL. A cross-region pair is included in both endpoint regions; an incident net HPL is included once per region.",
            "channelDiagnostics": "Q30 channel-a/channel-b groups are a mechanical suffix grouping of exact root region names ending in -a or -b. Synthetic CH0..CH3 groups are retained as authored. Regions without these forms remain unassigned; no semantic circuit classification is inferred.",
            "crossingProxy": "Proper interior intersections between straight-line MST segments from different nets; this is a geometry proxy, not routed copper.",
            "bboxOverlapProxy": "Pairs of distinct net pad bounding boxes with positive-area overlap.",
            "courtyard": "Sum and pairwise positive-area overlap of exported routing courtyards; touching edges are not overlaps.",
            "connectorLocality": "For each connector pad, nearest pad on the same electrical net from another component.",
            "connectorDiagnostics": "Each connector reports source-backed region, package pad count, courtyard area/aspect, nearest same-net pad mean/median/p95, board-edge margin statistics, and cross-region incidence.",
            "connectorEdgeMargin": "Minimum axis clearance from a connector pad center to the board outline.",
            "aspect": "Aspect is the axis-aligned width divided by height; region and channel envelopes bound their exported courtyards.",
            "units": "Board coordinates and areas are the pilot's integer coordinate units; distances use Manhattan units.",
        },
        "corpora": {
            "q30CurrentExactRoot": {
                "placementCount": len(q30_rows),
                "attemptCount": len(q30_blocks),
                "seedCount": len(set(block["seed"] for block in q30_blocks)),
                "candidateCount": len(set(row["candidate"] for row in q30_rows)),
                "rejectedCount": len([block for block in q30_blocks
                                      if block.get("status") == "REJECTED"]),
                "rejectedReasons": dict(sorted(collections.Counter(
                    block.get("reason") for block in q30_blocks
                    if block.get("status") == "REJECTED").items())),
                "summary": q30_summary,
                "rowsFile": "q30-current-exact-root.jsonl",
            },
            "synthetic33Successful": {
                "placementCount": len(synthetic_rows_out),
                "seedCount": len(set(row["seed"] for row in synthetic_rows_out)),
                "policy": SYNTHETIC_SUCCESS_POLICY,
                "summary": synthetic_summary,
                "routeSummary": route_summary(synthetic_rows_out),
                "rowsFile": "synthetic-33-success.jsonl",
            },
        },
        "medianComparison": add_comparison(q30_summary, synthetic_summary,
                                             comparison_keys),
        "limits": [
            "Q30 rows are physical placements and do not prove a route, solver behavior, player inspectability, or normal admission.",
            "The synthetic rows use a different graph, package inventory, pad count, region arrangement, and direct authored coordinates; they are a structured control, not a matched Q30 replacement.",
            "Ratsnest crossings and net-box overlaps are lower-level geometric proxies. They do not certify copper crossings, clearance, or route feasibility.",
            "Electrical pad adjacency is a pre-route complete cross-package net-pair metric; it does not predict the router's chosen topology or physical copper length.",
            "Q30 role and region labels are source-backed exporter/manifest labels. Channel groups are mechanical suffix groupings only, and unassigned regions are intentionally left out.",
            "Q30 connector identity follows the root manifest's four connector packages plus two output headers; synthetic connector identity follows JIN, CH<n>_JS, and CH<n>_JOUT.",
            "Successful synthetic rows are retained as every count=33 success row present in the supplied raw receipt; no single favorable seed is selected.",
        ],
    }
    return result, q30_rows, synthetic_rows_out


def write_jsonl(path, rows):
    with pathlib.Path(path).open("w", encoding="utf-8", newline="\n") as output:
        for row in rows:
            output.write(json.dumps(row, sort_keys=True, separators=(",", ":")) + "\n")


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--q30-inspect", required=True)
    parser.add_argument("--synthetic-raw", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--q30-source-sha256", default="not-supplied")
    parser.add_argument("--q30-plan-sha256", default="not-supplied")
    parser.add_argument("--q30-planner-sha256", default="not-supplied")
    parser.add_argument("--q30-planner-input", default="not-supplied")
    parser.add_argument("--q30-planner-mode", default="not-supplied")
    parser.add_argument("--planner-label", default="not-supplied")
    parser.add_argument("--measurement-label", default="unclassified")
    parser.add_argument("--measurement-status", default="unclassified")
    parser.add_argument("--q30-patch-sha256", default="not-supplied")
    parser.add_argument("--synthetic-source-sha256", default="not-supplied")
    parser.add_argument("--expected-q30-parts", type=int, default=None)
    args = parser.parse_args(argv)
    output_dir = pathlib.Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    q30_blocks = parse_q30(args.q30_inspect, args.expected_q30_parts)
    synthetic_rows = parse_synthetic_rows(args.synthetic_raw)
    result, q30_rows, synthetic_rows_out = build_output(q30_blocks,
                                                         synthetic_rows, args)
    write_jsonl(output_dir / "q30-current-exact-root.jsonl", q30_rows)
    write_jsonl(output_dir / "synthetic-33-success.jsonl", synthetic_rows_out)
    with (output_dir / "floorplan-comparison.json").open("w", encoding="utf-8",
                                                           newline="\n") as output:
        json.dump(result, output, indent=2, sort_keys=True)
        output.write("\n")
    print("PASS: parsed %d Q30 placements across %d seeds and %d validated synthetic 33-part placements" %
          (len(q30_rows), len(set(row["seed"] for row in q30_rows)),
           len(synthetic_rows_out)))
    print("Q30 rows: %s" % (output_dir / "q30-current-exact-root.jsonl"))
    print("Synthetic rows: %s" % (output_dir / "synthetic-33-success.jsonl"))
    print("Comparison: %s" % (output_dir / "floorplan-comparison.json"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
