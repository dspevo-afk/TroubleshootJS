"""Adapt only the frozen route report/suite framing check in memory.

The published reader remains byte-pinned.  This module loads that source,
replaces one exact old framing block, and exposes the resulting reader API
without writing a repaired native assessment artifact.
"""

import hashlib
from pathlib import Path


HERE = Path(__file__).resolve().parent
FROZEN_ASSESS_PATH = HERE / "assess.py"
FROZEN_ASSESS_SHA256 = (
    "4e4da013ea0d489f73d8806d8016e6548fe47fb65e323e7c8ba021ccdfd94a0c"
)

OLD_FRAMING_BLOCK = (
    '        report_positions = [position for position, line in '
    'enumerate(text_lines) if line.startswith("PCB_")]\n'
    '        suite_positions = [text_lines.index(line) for line in suite_passes]\n'
    '        _need(len(reports) == 42 and max(report_positions) < min(suite_positions),\n'
    '              "all cohort reports must precede route suite PASS")\n'
    '        receipt_report_positions = [position for position, line in '
    'enumerate(receipt_lines) if line.startswith("PCB_")]\n'
    '        receipt_suite_positions = [receipt_lines.index(line) for line in receipt_passes]\n'
    '        _need(len(receipt_reports) == 42 and max(receipt_report_positions) < min(receipt_suite_positions),\n'
    '              "child receipt reports must precede route suite PASS")\n'
)

NEW_FRAMING_BLOCK = (
    '        report_positions = [position for position, line in '
    'enumerate(text_lines) if line.startswith("PCB_")]\n'
    '        suite_positions = [text_lines.index(line) for line in suite_passes]\n'
    '        _need(len(reports) == 42 and len(report_positions) == len(reports),\n'
    '              "cohort report count/order changed")\n'
    '        _need(len(suite_positions) == len(PARTITIONS) == 7,\n'
    '              "route suite partition count changed")\n'
    '        def _check_pair_framing(positions, parsed, passes, label):\n'
    '            _need(len(parsed) == len(positions) == 42 and\n'
    '                  len(passes) == len(PARTITIONS),\n'
    '                  label + " report/suite count changed")\n'
    '            previous = -1\n'
    '            for pair_index, pair in enumerate(PARTITIONS):\n'
    '                upper = passes[pair_index]\n'
    '                segment_positions = [position for position in positions\n'
    '                                     if previous < position < upper]\n'
    '                _need(len(segment_positions) == 6,\n'
    '                      label + " pair does not contain six reports")\n'
    '                segment = [item for position, item in zip(positions, parsed)\n'
    '                           if previous < position < upper]\n'
    '                expected = [(WHITESPACE_PREFIX, pair[0]),\n'
    '                            (LEG_PREFIX, pair[0]),\n'
    '                            (SUMMARY_PREFIX, pair[0]),\n'
    '                            (WHITESPACE_PREFIX, pair[1]),\n'
    '                            (LEG_PREFIX, pair[1]),\n'
    '                            (SUMMARY_PREFIX, pair[1])]\n'
    '                actual = [(item[0], item[1].get("seed")) for item in segment]\n'
    '                _need(actual == expected,\n'
    '                      label + " pair report order or root changed")\n'
    '                previous = upper\n'
    '            _need(all(position < passes[-1] for position in positions),\n'
    '                  label + " report appeared after the final suite PASS")\n'
    '        _check_pair_framing(report_positions, reports, suite_positions, "terminal")\n'
    '        receipt_report_positions = [position for position, line in '
    'enumerate(receipt_lines) if line.startswith("PCB_")]\n'
    '        receipt_suite_positions = [receipt_lines.index(line) for line in receipt_passes]\n'
    '        _check_pair_framing(receipt_report_positions, receipt_reports,\n'
    '                           receipt_suite_positions, "child receipt")\n'
)


def _need(condition, message):
    if not condition:
        raise AssertionError(message)


def _sha(data):
    return hashlib.sha256(data).hexdigest()


def _adapted_source():
    data = FROZEN_ASSESS_PATH.read_bytes()
    _need(_sha(data) == FROZEN_ASSESS_SHA256,
          "frozen assess.py hash changed before framing adaptation")
    source = data.decode("utf-8", errors="strict")
    _need(source.count(OLD_FRAMING_BLOCK) == 1,
          "frozen route framing block is missing or duplicated")
    prefix, suffix = source.split(OLD_FRAMING_BLOCK, 1)
    adapted = prefix + NEW_FRAMING_BLOCK + suffix
    _need(adapted.startswith(prefix) and adapted.endswith(suffix),
          "framing adapter changed text outside its bounded block")
    return adapted


def _load_namespace():
    source = _adapted_source()
    namespace = {
        "__name__": "receipt_order_reader._frozen_assess",
        "__file__": str(FROZEN_ASSESS_PATH),
    }
    exec(compile(source, str(FROZEN_ASSESS_PATH), "exec"), namespace, namespace)
    return namespace


_ADAPTED = _load_namespace()
ADAPTED_ASSESS_SHA256 = _sha(_adapted_source().encode("utf-8"))
OLD_FRAMING_BLOCK_SHA256 = _sha(OLD_FRAMING_BLOCK.encode("utf-8"))
NEW_FRAMING_BLOCK_SHA256 = _sha(NEW_FRAMING_BLOCK.encode("utf-8"))
FRAMING_RULE = "seven ordered two-root pairs; six reports after the prior suite and before its matching PASS in both logs"


def __getattr__(name):
    try:
        return _ADAPTED[name]
    except KeyError as exc:
        raise AttributeError(name) from exc


def source_metadata():
    """Return the immutable source/adaptation pins for review receipts."""
    return {
        "frozenAssessSha256": FROZEN_ASSESS_SHA256,
        "adaptedAssessSha256": ADAPTED_ASSESS_SHA256,
        "oldFramingBlockSha256": OLD_FRAMING_BLOCK_SHA256,
        "newFramingBlockSha256": NEW_FRAMING_BLOCK_SHA256,
        "replacementCount": 1,
        "framingRule": FRAMING_RULE,
    }
