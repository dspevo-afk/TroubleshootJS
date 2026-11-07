"""Independent static boundaries for the native-only P8 transform.

Runtime predicate truth is checked by selected-JDK8 literal canaries, not these
source checks. No canonical cases or protected inputs are loaded here.
"""
from __future__ import annotations

import sys
if not sys.flags.isolated or not sys.dont_write_bytecode:
    raise RuntimeError("ISOLATED_NO_BYTECODE_REQUIRED")
import importlib.util
import hashlib
from pathlib import Path
import unittest

HERE = Path(__file__).resolve().parent
if len(sys.argv) != 2 or not Path(sys.argv[1]).is_absolute():
    raise ValueError("usage: test_overlay.py <absolute-repository-root>")
E = Path(sys.argv.pop(1))
P6 = HERE.parent / "canonical-prefix-rejection-stages-r1"


def method(raw: bytes, anchor: bytes) -> bytes:
    """Extract an ordinary Java method by balanced braces, independent of edits."""
    begin = raw.index(anchor)
    opened = raw.index(b"{", begin)
    depth = 1
    cursor = opened + 1
    while depth:
        if raw[cursor:cursor + 1] == b"{":
            depth += 1
        elif raw[cursor:cursor + 1] == b"}":
            depth -= 1
        cursor += 1
    return raw[begin:cursor]


class OverlayBoundaries(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location("p8_overlay", HERE / "build_overlay.py")
        cls.builder = importlib.util.module_from_spec(spec)
        raw = (HERE / "build_overlay.py").read_bytes()
        if hashlib.sha256(raw).hexdigest() != "e62fbb833e3751ff2f53b38958b5b8ddf946ff210b18cfcf3b5052e19ab15ec0":
            raise RuntimeError("BUILDER_PIN")
        exec(compile(raw, str(HERE / "build_overlay.py"), "exec"), cls.builder.__dict__)
        cls.router_source = (P6 / "overlays-r1/PcbNetRouter.java").read_bytes()
        cls.rules_source = (E / "src/com/lushprojects/circuitjs1/client/PcbTwoLayerRules.java").read_bytes()
        cls.router = cls.builder.make_router_overlay(cls.router_source)
        cls.rules = cls.builder.make_rules_overlay(cls.rules_source)

    def test_router_source_change_rejected(self):
        changed = self.router_source.replace(b"return true;", b"return false;", 1)
        self.assertNotEqual(changed, self.router_source)
        with self.assertRaises((ValueError, RuntimeError)):
            self.builder.make_router_overlay(changed)

    def test_rules_source_change_rejected(self):
        changed = self.rules_source.replace(b"return true;", b"return false;", 1)
        self.assertNotEqual(changed, self.rules_source)
        with self.assertRaises((ValueError, RuntimeError)):
            self.builder.make_rules_overlay(changed)

    def test_router_newline_change_rejected(self):
        with self.assertRaises((ValueError, RuntimeError)):
            self.builder.make_router_overlay(self.router_source.replace(b"\r\n", b"\n"))

    def test_rules_bom_rejected(self):
        with self.assertRaises((ValueError, RuntimeError)):
            self.builder.make_rules_overlay(b"\xef\xbb\xbf" + self.rules_source)

    def test_outer_bounds_endpoint_loop_identical(self):
        anchor = b"boolean permitsLayerStep(int ax,int ay,int bx,int by,String net,"
        self.assertEqual(method(self.router_source, anchor), method(self.router, anchor))

    def test_coarse_endpoint_chain_identical(self):
        anchor = b"private boolean permitsLayerStepToEndpoint("
        self.assertEqual(method(self.router_source, anchor), method(self.router, anchor))

    def test_coarse_attach_api_identical(self):
        for anchor in (b"void attachFixedGraphStageObserverForStudy(",
                       b"void detachFixedGraphStageObserverForStudy(",
                       b"boolean hasFixedGraphStageObserverForStudy("):
            self.assertEqual(method(self.router_source, anchor), method(self.router, anchor))

    def test_copy_shell_constructor_identical(self):
        anchor = b"private Router(Router source,boolean observationShell)"
        self.assertEqual(method(self.router_source, anchor), method(self.router, anchor))

    def test_occupancy_owner_untouched(self):
        anchor = b"private boolean canOccupy("
        self.assertEqual(method(self.router_source, anchor), method(self.router, anchor))

    def test_legal_guard_expressions_identical(self):
        before = method(self.router_source, b"private boolean isLegalMove(")
        after = method(self.router, b"private boolean isLegalMove(")
        # The original two compound guard expressions end at their return;
        # inspect their exact text, without prescribing the reporting helper.
        for first in (b"if (startPad.getMountingSide()", b"if (endPad!=null"):
            expression = before[before.index(first):before.index(b"return false;", before.index(first))].rstrip()
            self.assertIn(expression, after)

    def test_repeat_transform_is_pure_and_deterministic(self):
        self.assertIsInstance(self.router, bytes)
        self.assertIsInstance(self.rules, bytes)
        self.assertEqual(self.router, self.builder.make_router_overlay(self.router_source))
        self.assertEqual(self.rules, self.builder.make_rules_overlay(self.rules_source))
        # A generated overlay is never silently accepted as its source.
        with self.assertRaises((ValueError, RuntimeError)):
            self.builder.make_router_overlay(self.router)
        with self.assertRaises((ValueError, RuntimeError)):
            self.builder.make_rules_overlay(self.rules)


if __name__ == "__main__":
    unittest.main(verbosity=2)
