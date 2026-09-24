"""Render one Q30_GEOMETRY pilot record as deterministic structural SVG views.

This is a small, dependency-free evidence helper.  It deliberately renders
only physical structure: board outline, component courtyards, plated pads and
vias, and routed copper.  It does not render solver state or any player-facing
workbench state.

Example::

    python render-p1-geometry.py \
        --raw p1-selected-geometry-seed0.txt \
        --output-prefix p1-selected-seed0

The command writes ``<prefix>-top.svg`` and ``<prefix>-bottom.svg``.  The
bottom view mirrors the board in X so it represents looking at the opposite
face while keeping labels readable.  The generated files are structural
renderings, not actual production workbench screenshots.
"""

from __future__ import annotations

import argparse
import json
import math
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from xml.sax.saxutils import escape


SVG_NS = "http://www.w3.org/2000/svg"
CANVAS_WIDTH = 1500
CANVAS_HEIGHT = 1000
CANVAS_MARGIN = 34
HEADER_HEIGHT = 82
LEGEND_WIDTH = 276
LEGEND_GAP = 28

VISIBLE_COPPER = "#ffc34d"
OPPOSITE_COPPER = "#6686a2"
PAD_METAL = "#f4d66d"
PAD_FILL = "#c78d1b"
VIA_METAL = "#f7e08d"
HOLE_FILL = "#0b1720"
BOARD_FILL = "#102f2d"
BOARD_EDGE = "#b9e0d0"
TEXT_PRIMARY = "#f3f7f4"
TEXT_MUTED = "#b8c8c4"
PANEL_FILL = "#17262b"
PANEL_EDGE = "#5d7777"

REGION_COLORS = (
    "#4d7192",
    "#76568c",
    "#3e806f",
    "#a16645",
    "#7d7540",
    "#8c4f68",
    "#477e88",
    "#986d3d",
    "#5b6e49",
    "#7a5c78",
    "#3d6b83",
    "#88634e",
)


class GeometryError(ValueError):
    """Raised when a raw receipt does not contain one usable geometry row."""


def xml_text(value: object) -> str:
    """Escape text/attribute content while keeping quote handling explicit."""

    return escape(str(value), {"\"": "&quot;", "'": "&apos;"})


def number(value: object, field: str) -> float:
    """Return a finite number and reject booleans/non-numeric values."""

    if isinstance(value, bool):
        raise GeometryError(f"{field} must be numeric")
    try:
        result = float(value)
    except (TypeError, ValueError) as exc:
        raise GeometryError(f"{field} must be numeric") from exc
    if not math.isfinite(result):
        raise GeometryError(f"{field} must be finite")
    return result


def fmt(value: object) -> str:
    """Format SVG numbers without platform-dependent float noise."""

    result = number(value, "SVG coordinate")
    if abs(result) < 0.0005:
        result = 0.0
    if result.is_integer():
        return str(int(result))
    return f"{result:.3f}".rstrip("0").rstrip(".")


def attrs(values: dict[str, object]) -> str:
    """Serialize attributes in insertion order for stable output."""

    return "".join(f' {key}="{xml_text(value)}"' for key, value in values.items())


def element(name: str, values: dict[str, object]) -> str:
    return f"<{name}{attrs(values)}/>"


def text_element(name: str, values: dict[str, object], text: object) -> str:
    return f"<{name}{attrs(values)}>{xml_text(text)}</{name}>"


def require_sequence(value: object, field: str) -> list[object]:
    if not isinstance(value, list):
        raise GeometryError(f"{field} must be an array")
    return value


def require_string(value: object, field: str) -> str:
    if not isinstance(value, str) or not value:
        raise GeometryError(f"{field} must be a non-empty string")
    return value


def rectangle(value: object, field: str) -> tuple[float, float, float, float]:
    values = require_sequence(value, field)
    if len(values) != 4:
        raise GeometryError(f"{field} must contain x, y, width, height")
    x, y, width, height = (
        number(values[index], f"{field}[{index}]") for index in range(4)
    )
    if width <= 0 or height <= 0:
        raise GeometryError(f"{field} width and height must be positive")
    return x, y, width, height


def validate_geometry(data: object) -> dict[str, object]:
    """Validate the subset of the pilot schema needed by this renderer."""

    if not isinstance(data, dict):
        raise GeometryError("Q30_GEOMETRY payload must be an object")
    if data.get("schema") != 1:
        raise GeometryError("Q30_GEOMETRY schema must be 1")

    outline = rectangle(data.get("outline"), "outline")
    components = require_sequence(data.get("components"), "components")
    pads = require_sequence(data.get("pads"), "pads")
    traces = require_sequence(data.get("traces"), "traces")
    vias = require_sequence(data.get("vias"), "vias")

    for index, component in enumerate(components):
        if not isinstance(component, dict):
            raise GeometryError(f"components[{index}] must be an object")
        require_string(component.get("id"), f"components[{index}].id")
        require_string(component.get("region"), f"components[{index}].region")
        rectangle(component.get("courtyard"), f"components[{index}].courtyard")

    for index, pad in enumerate(pads):
        if not isinstance(pad, dict):
            raise GeometryError(f"pads[{index}] must be an object")
        require_string(pad.get("id"), f"pads[{index}].id")
        require_string(pad.get("net"), f"pads[{index}].net")
        number(pad.get("x"), f"pads[{index}].x")
        number(pad.get("y"), f"pads[{index}].y")

    for index, trace in enumerate(traces):
        if not isinstance(trace, dict):
            raise GeometryError(f"traces[{index}] must be an object")
        require_string(trace.get("net"), f"traces[{index}].net")
        layer = require_string(trace.get("layer"), f"traces[{index}].layer")
        if layer.upper() not in {"TOP", "BOTTOM"}:
            raise GeometryError(f"traces[{index}].layer must be TOP or BOTTOM")
        x_values = require_sequence(trace.get("x"), f"traces[{index}].x")
        y_values = require_sequence(trace.get("y"), f"traces[{index}].y")
        if len(x_values) != len(y_values) or len(x_values) < 2:
            raise GeometryError(
                f"traces[{index}] must have matching x/y arrays with at least two points"
            )
        for point_index, value in enumerate(x_values):
            number(value, f"traces[{index}].x[{point_index}]")
        for point_index, value in enumerate(y_values):
            number(value, f"traces[{index}].y[{point_index}]")

    for index, via in enumerate(vias):
        if not isinstance(via, dict):
            raise GeometryError(f"vias[{index}] must be an object")
        require_string(via.get("id"), f"vias[{index}].id")
        require_string(via.get("net"), f"vias[{index}].net")
        number(via.get("x"), f"vias[{index}].x")
        number(via.get("y"), f"vias[{index}].y")
        if number(via.get("landRadius"), f"vias[{index}].landRadius") <= 0:
            raise GeometryError(f"vias[{index}].landRadius must be positive")

    return {
        "outline": outline,
        "components": components,
        "pads": pads,
        "traces": traces,
        "vias": vias,
        "seed": str(data.get("seed", "unknown")),
        "candidate": str(data.get("candidate", "unknown")),
    }


def read_geometry(raw_path: Path) -> dict[str, object]:
    """Read exactly one Q30_GEOMETRY JSON record from a raw text receipt."""

    matches: list[tuple[int, str]] = []
    try:
        raw_lines = raw_path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        raise GeometryError(f"cannot read raw receipt {raw_path}: {exc}") from exc

    marker = "Q30_GEOMETRY "
    for line_number, raw_line in enumerate(raw_lines, 1):
        line = raw_line.strip()
        if line.startswith(marker):
            matches.append((line_number, line[len(marker) :]))
    if len(matches) != 1:
        raise GeometryError(
            f"expected exactly one Q30_GEOMETRY line in {raw_path}, found {len(matches)}"
        )
    line_number, payload = matches[0]
    try:
        data = json.loads(payload)
    except json.JSONDecodeError as exc:
        raise GeometryError(
            f"invalid Q30_GEOMETRY JSON on line {line_number}: {exc}"
        ) from exc
    return validate_geometry(data)


@dataclass(frozen=True)
class Projector:
    """Map board coordinates into the SVG board viewport."""

    origin_x: float
    origin_y: float
    width: float
    height: float
    board_x: float
    board_y: float
    scale: float
    mirrored: bool

    def point(self, x_value: object, y_value: object) -> tuple[float, float]:
        x = number(x_value, "point x")
        y = number(y_value, "point y")
        relative_x = x - self.origin_x
        if self.mirrored:
            relative_x = self.width - relative_x
        return (
            self.board_x + relative_x * self.scale,
            self.board_y + (y - self.origin_y) * self.scale,
        )

    def rect(self, value: object, field: str) -> tuple[float, float, float, float]:
        x, y, width, height = rectangle(value, field)
        if self.mirrored:
            x = self.origin_x + self.width - (x - self.origin_x) - width
        return (
            self.board_x + (x - self.origin_x) * self.scale,
            self.board_y + (y - self.origin_y) * self.scale,
            width * self.scale,
            height * self.scale,
        )


def make_projector(outline: tuple[float, float, float, float], mirrored: bool) -> Projector:
    origin_x, origin_y, width, height = outline
    available_width = CANVAS_WIDTH - 2 * CANVAS_MARGIN - LEGEND_WIDTH - LEGEND_GAP
    available_height = CANVAS_HEIGHT - HEADER_HEIGHT - 2 * CANVAS_MARGIN
    scale = min(available_width / width, available_height / height)
    board_width = width * scale
    board_height = height * scale
    board_x = float(CANVAS_MARGIN)
    board_y = HEADER_HEIGHT + (available_height - board_height) / 2
    return Projector(
        origin_x,
        origin_y,
        width,
        height,
        board_x,
        board_y,
        scale,
        mirrored,
    )


def region_colors(components: list[object]) -> dict[str, str]:
    regions = sorted({str(component["region"]) for component in components})
    colors: dict[str, str] = {}
    for index, region in enumerate(regions):
        if index < len(REGION_COLORS):
            colors[region] = REGION_COLORS[index]
        else:
            # Deterministic fallback for an unusually large region inventory.
            hue = (index * 47 + 17) % 360
            colors[region] = f"hsl({hue}, 38%, 42%)"
    return colors


def polyline_path(projector: Projector, x_values: list[object], y_values: list[object]) -> str:
    points = [projector.point(x, y) for x, y in zip(x_values, y_values)]
    start_x, start_y = points[0]
    chunks = [f"M {fmt(start_x)} {fmt(start_y)}"]
    chunks.extend(f"L {fmt(x)} {fmt(y)}" for x, y in points[1:])
    return " ".join(chunks)


def component_label(
    projector: Projector,
    component: dict[str, object],
    rect: tuple[float, float, float, float],
) -> list[str]:
    x, y, width, height = rect
    component_id = str(component["id"])
    region = str(component["region"])
    label_width = max(12.0, width - 5.0)
    label_size = max(7.0, min(11.0, height * 0.34))
    center_x = x + width / 2
    center_y = y + height / 2 + label_size * 0.34
    return [
        text_element(
            "title",
            {},
            f"{component_id}; region {region}; structural courtyard",
        ),
        text_element(
            "text",
            {
                "class": "component-label",
                "x": fmt(center_x),
                "y": fmt(center_y),
                "text-anchor": "middle",
                "font-size": fmt(label_size),
                "textLength": fmt(label_width),
                "lengthAdjust": "spacingAndGlyphs",
            },
            component_id,
        ),
    ]


def legend_lines(
    projector: Projector,
    regions: dict[str, str],
    legend_x: float,
    legend_y: float,
    legend_height: float,
    view_face: str,
    mirrored: bool,
) -> list[str]:
    legend_width = CANVAS_WIDTH - legend_x - CANVAS_MARGIN
    lines = [
        element(
            "rect",
            {
                "class": "legend-panel",
                "data-kind": "legend",
                "x": fmt(legend_x),
                "y": fmt(legend_y),
                "width": fmt(legend_width),
                "height": fmt(legend_height),
                "rx": "12",
            },
        ),
        text_element(
            "text",
            {
                "class": "legend-heading",
                "x": fmt(legend_x + 18),
                "y": fmt(legend_y + 30),
            },
            "STRUCTURAL RENDERING",
        ),
        text_element(
            "text",
            {
                "class": "legend-face",
                "x": fmt(legend_x + 18),
                "y": fmt(legend_y + 54),
            },
            f"{view_face} face" + (" · mirrored" if mirrored else ""),
        ),
        text_element(
            "text",
            {
                "class": "legend-note",
                "x": fmt(legend_x + 18),
                "y": fmt(legend_y + 75),
            },
            "Developer-only physical view",
        ),
        text_element(
            "text",
            {
                "class": "legend-note",
                "x": fmt(legend_x + 18),
                "y": fmt(legend_y + 91),
            },
            "Not a production workbench screenshot",
        ),
    ]

    swatch_x = legend_x + 18
    label_x = swatch_x + 27
    key_y = legend_y + 126
    key_rows = (
        (VISIBLE_COPPER, "visible-face copper", "line"),
        (OPPOSITE_COPPER, "opposite-face copper", "line"),
        (PAD_METAL, "plated through-hole pad", "ring"),
        (VIA_METAL, "plated via", "ring"),
    )
    lines.append(
        text_element(
            "text",
            {"class": "legend-section", "x": fmt(swatch_x), "y": fmt(key_y)},
            "FACE / PLATING KEY",
        )
    )
    for index, (color, label, kind) in enumerate(key_rows):
        row_y = key_y + 24 + index * 22
        if kind == "line":
            lines.append(
                element(
                    "line",
                    {
                        "class": "legend-swatch-line",
                        "x1": fmt(swatch_x),
                        "y1": fmt(row_y - 4),
                        "x2": fmt(swatch_x + 18),
                        "y2": fmt(row_y - 4),
                        "stroke": color,
                    },
                )
            )
        else:
            lines.append(
                element(
                    "circle",
                    {
                        "class": "legend-swatch-ring",
                        "cx": fmt(swatch_x + 9),
                        "cy": fmt(row_y - 4),
                        "r": "6",
                        "stroke": color,
                    },
                )
            )
        lines.append(
            text_element(
                "text",
                {
                    "class": "legend-label",
                    "x": fmt(label_x),
                    "y": fmt(row_y),
                },
                label,
            )
        )

    region_y = key_y + 24 + len(key_rows) * 22 + 20
    lines.append(
        text_element(
            "text",
            {"class": "legend-section", "x": fmt(swatch_x), "y": fmt(region_y)},
            "REGION COURTYARDS",
        )
    )
    region_start = region_y + 24
    max_rows = max(1, int((legend_height - (region_start - legend_y) - 18) // 20))
    column_count = max(1, math.ceil(len(regions) / max_rows))
    column_width = (legend_width - 36) / column_count
    for index, region in enumerate(sorted(regions)):
        column = index // max_rows
        row = index % max_rows
        row_y = region_start + row * 20
        x = swatch_x + column * column_width
        lines.append(
            element(
                "rect",
                {
                    "class": "legend-region-swatch",
                    "x": fmt(x),
                    "y": fmt(row_y - 11),
                    "width": "14",
                    "height": "12",
                    "rx": "2",
                    "fill": regions[region],
                },
            )
        )
        lines.append(
            text_element(
                "text",
                {
                    "class": "legend-label",
                    "x": fmt(x + 20),
                    "y": fmt(row_y),
                    "font-size": "10",
                },
                region,
            )
        )
    return lines


def render_view(
    geometry: dict[str, object],
    output_path: Path,
    view_face: str,
    mirrored: bool,
) -> dict[str, object]:
    outline = geometry["outline"]
    assert isinstance(outline, tuple)
    components = geometry["components"]
    pads = geometry["pads"]
    traces = geometry["traces"]
    vias = geometry["vias"]
    assert isinstance(components, list)
    assert isinstance(pads, list)
    assert isinstance(traces, list)
    assert isinstance(vias, list)

    projector = make_projector(outline, mirrored)
    origin_x, origin_y, board_width, board_height = outline
    board_x = projector.board_x
    board_y = projector.board_y
    board_w = board_width * projector.scale
    board_h = board_height * projector.scale
    legend_x = board_x + board_w + LEGEND_GAP
    legend_y = HEADER_HEIGHT
    legend_h = CANVAS_HEIGHT - HEADER_HEIGHT - CANVAS_MARGIN
    region_map = region_colors(components)
    visible_layer = view_face.upper()

    lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        element(
            "svg",
            {
                "xmlns": SVG_NS,
                "width": CANVAS_WIDTH,
                "height": CANVAS_HEIGHT,
                "viewBox": f"0 0 {CANVAS_WIDTH} {CANVAS_HEIGHT}",
                "role": "img",
                "aria-labelledby": "svg-title svg-description",
                "shape-rendering": "geometricPrecision",
            },
        ).replace("/>", ">", 1),
        text_element(
            "title",
            {"id": "svg-title"},
            f"Q30 structural rendering · {view_face} face",
        ),
        text_element(
            "desc",
            {"id": "svg-description"},
            "Developer-only structural rendering of board geometry and routed copper; not an actual production workbench screenshot.",
        ),
        element(
            "rect",
            {
                "class": "canvas",
                "x": "0",
                "y": "0",
                "width": CANVAS_WIDTH,
                "height": CANVAS_HEIGHT,
                "fill": "#091318",
            },
        ),
        text_element(
            "text",
            {"class": "page-title", "x": CANVAS_MARGIN, "y": "32"},
            "Q30 structural geometry",
        ),
        text_element(
            "text",
            {"class": "page-subtitle", "x": CANVAS_MARGIN, "y": "57"},
            f"seed {geometry['seed']} · candidate {geometry['candidate']} · {view_face} face",
        ),
        element(
            "rect",
            {
                "class": "board-shadow",
                "x": fmt(board_x + 5),
                "y": fmt(board_y + 7),
                "width": fmt(board_w),
                "height": fmt(board_h),
                "rx": "10",
                "fill": "#000000",
                "opacity": "0.45",
            },
        ),
        element(
            "rect",
            {
                "class": "board",
                "data-kind": "board",
                "x": fmt(board_x),
                "y": fmt(board_y),
                "width": fmt(board_w),
                "height": fmt(board_h),
                "rx": "10",
                "fill": BOARD_FILL,
                "stroke": BOARD_EDGE,
                "stroke-width": "2",
            },
        ),
        element(
            "rect",
            {
                "class": "board-outline",
                "data-kind": "board-outline",
                "x": fmt(board_x + 8),
                "y": fmt(board_y + 8),
                "width": fmt(max(0.0, board_w - 16)),
                "height": fmt(max(0.0, board_h - 16)),
                "rx": "6",
                "fill": "none",
                "stroke": "#d8efe3",
                "stroke-width": "1",
                "stroke-dasharray": "7 5",
                "opacity": "0.9",
            },
        ),
        element(
            "clipPath",
            {"id": "board-clip"},
        ).replace("/>", ">", 1),
        f'<rect x="{fmt(board_x)}" y="{fmt(board_y)}" width="{fmt(board_w)}" '
        f'height="{fmt(board_h)}" rx="10"/>',
        "</clipPath>",
        '<g clip-path="url(#board-clip)">',
    ]

    for index, trace in enumerate(traces):
        assert isinstance(trace, dict)
        if str(trace["layer"]).upper() == visible_layer:
            continue
        lines.append(
            element(
                "path",
                {
                    "class": "trace opposite-copper",
                    "data-kind": "trace",
                    "data-index": index,
                    "data-face": str(trace["layer"]).upper(),
                    "d": polyline_path(projector, trace["x"], trace["y"]),
                    "fill": "none",
                    "stroke": OPPOSITE_COPPER,
                    "stroke-width": "3.2",
                    "stroke-linecap": "round",
                    "stroke-linejoin": "round",
                    "opacity": "0.54",
                },
            )
        )
    for index, trace in enumerate(traces):
        assert isinstance(trace, dict)
        if str(trace["layer"]).upper() != visible_layer:
            continue
        lines.append(
            element(
                "path",
                {
                    "class": "trace visible-copper",
                    "data-kind": "trace",
                    "data-index": index,
                    "data-face": str(trace["layer"]).upper(),
                    "d": polyline_path(projector, trace["x"], trace["y"]),
                    "fill": "none",
                    "stroke": VISIBLE_COPPER,
                    "stroke-width": "5.4",
                    "stroke-linecap": "round",
                    "stroke-linejoin": "round",
                    "opacity": "0.96",
                },
            )
        )

    for index, component in enumerate(components):
        assert isinstance(component, dict)
        x, y, width, height = projector.rect(
            component["courtyard"], f"components[{index}].courtyard"
        )
        region = str(component["region"])
        package = str(component.get("package", ""))
        stroke = "#e6f4ee" if "CONNECTOR" in package else "#b9d2cf"
        lines.append(
            element(
                "rect",
                {
                    "class": "component-courtyard",
                    "data-kind": "component-courtyard",
                    "data-id": str(component["id"]),
                    "data-region": region,
                    "x": fmt(x),
                    "y": fmt(y),
                    "width": fmt(width),
                    "height": fmt(height),
                    "rx": "4",
                    "fill": region_map[region],
                    "fill-opacity": "0.42",
                    "stroke": stroke,
                    "stroke-width": "1.2",
                    "stroke-dasharray": "4 3",
                },
            )
        )
        lines.extend(component_label(projector, component, (x, y, width, height)))

    for index, pad in enumerate(pads):
        assert isinstance(pad, dict)
        x, y = projector.point(pad["x"], pad["y"])
        outer_radius = max(3.8, 10.0 * projector.scale)
        hole_radius = max(1.5, outer_radius * 0.39)
        lines.append(
            element(
                "circle",
                {
                    "class": "pad-plated",
                    "data-kind": "pad",
                    "data-index": index,
                    "data-id": str(pad["id"]),
                    "cx": fmt(x),
                    "cy": fmt(y),
                    "r": fmt(outer_radius),
                    "fill": PAD_FILL,
                    "stroke": PAD_METAL,
                    "stroke-width": "1.5",
                },
            )
        )
        lines.append(
            element(
                "circle",
                {
                    "class": "pad-hole",
                    "data-kind": "pad-hole",
                    "cx": fmt(x),
                    "cy": fmt(y),
                    "r": fmt(hole_radius),
                    "fill": HOLE_FILL,
                },
            )
        )

    for index, via in enumerate(vias):
        assert isinstance(via, dict)
        x, y = projector.point(via["x"], via["y"])
        land_radius = number(via["landRadius"], f"vias[{index}].landRadius")
        outer_radius = max(4.4, (land_radius + 5.0) * projector.scale)
        hole_radius = max(1.6, outer_radius * 0.40)
        lines.append(
            element(
                "circle",
                {
                    "class": "via-plated",
                    "data-kind": "via",
                    "data-index": index,
                    "data-id": str(via["id"]),
                    "cx": fmt(x),
                    "cy": fmt(y),
                    "r": fmt(outer_radius),
                    "fill": "#806e2f",
                    "stroke": VIA_METAL,
                    "stroke-width": "2",
                },
            )
        )
        lines.append(
            element(
                "circle",
                {
                    "class": "via-hole",
                    "data-kind": "via-hole",
                    "cx": fmt(x),
                    "cy": fmt(y),
                    "r": fmt(hole_radius),
                    "fill": HOLE_FILL,
                },
            )
        )

    lines.extend(["</g>"])
    lines.extend(
        legend_lines(
            projector,
            region_map,
            legend_x,
            legend_y,
            legend_h,
            view_face,
            mirrored,
        )
    )
    lines.extend(
        [
            text_element(
                "text",
                {
                    "class": "footer-note",
                    "x": fmt(CANVAS_MARGIN),
                    "y": fmt(CANVAS_HEIGHT - 12),
                },
                "Structural rendering only · physical geometry and routed copper",
            ),
            "</svg>",
        ]
    )

    try:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    except OSError as exc:
        raise GeometryError(f"cannot write SVG {output_path}: {exc}") from exc

    return {
        "path": str(output_path),
        "traces": len(traces),
        "vias": len(vias),
        "face": view_face,
        "mirrored": mirrored,
    }


def validate_svg(path: Path, expected_traces: int, expected_vias: int) -> dict[str, object]:
    """Parse an output SVG and verify its structural trace/via element counts."""

    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as exc:
        raise GeometryError(f"cannot parse rendered SVG {path}: {exc}") from exc
    if root.tag != f"{{{SVG_NS}}}svg":
        raise GeometryError(f"rendered SVG {path} has no SVG root")
    trace_count = sum(1 for node in root.iter() if node.get("data-kind") == "trace")
    via_count = sum(1 for node in root.iter() if node.get("data-kind") == "via")
    if trace_count != expected_traces:
        raise GeometryError(
            f"rendered SVG {path} has {trace_count} traces; expected {expected_traces}"
        )
    if via_count != expected_vias:
        raise GeometryError(
            f"rendered SVG {path} has {via_count} vias; expected {expected_vias}"
        )
    return {"path": str(path), "traces": trace_count, "vias": via_count}


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Render one Q30_GEOMETRY line as deterministic top and mirrored-bottom "
            "structural SVG views."
        )
    )
    parser.add_argument(
        "--raw",
        "--input",
        dest="raw",
        type=Path,
        required=True,
        help="raw pilot text file containing exactly one Q30_GEOMETRY line",
    )
    parser.add_argument(
        "--output-prefix",
        "--out-prefix",
        dest="output_prefix",
        type=Path,
        required=True,
        help="explicit output prefix; writes <prefix>-top.svg and <prefix>-bottom.svg",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        geometry = read_geometry(args.raw)
        top_path = Path(f"{args.output_prefix}-top.svg")
        bottom_path = Path(f"{args.output_prefix}-bottom.svg")
        rendered = [
            render_view(geometry, top_path, "TOP", mirrored=False),
            render_view(geometry, bottom_path, "BOTTOM", mirrored=True),
        ]
        validation = [
            validate_svg(top_path, len(geometry["traces"]), len(geometry["vias"])),
            validate_svg(bottom_path, len(geometry["traces"]), len(geometry["vias"])),
        ]
    except GeometryError as exc:
        print(f"render-p1-geometry.py: error: {exc}", file=sys.stderr)
        return 2

    receipt = {
        "status": "PASS",
        "outputPrefix": str(args.output_prefix),
        "outputs": rendered,
        "xmlValidation": validation,
        "instructions": (
            "Use <prefix>-top.svg for the top-face view and <prefix>-bottom.svg "
            "for the mirrored bottom-face view; both are structural renderings, "
            "not actual production workbench screenshots."
        ),
    }
    print(json.dumps(receipt, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
