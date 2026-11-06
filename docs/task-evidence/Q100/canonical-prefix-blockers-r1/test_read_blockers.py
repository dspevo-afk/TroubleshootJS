"""Literal synthetic tests for the P5 canonical prefix blocker reader."""
import sys
import os
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True

import read_blockers as reader


class BranchReaderStub:
    @staticmethod
    def java_lexical_key(value):
        return value


class TinyGraph:
    width = 2
    height = 2
    cells = 4
    state_count = 8
    starts = [0]
    steps = [2, 0, 0, 0, 0, 0, 0, 0]


def observation(clearance="", delta_count=2):
    return {
        "ordinal": 0, "layer": 0, "fromState": 0, "toState": 1, "bit": 2,
        "fromX": 10, "fromY": 10, "toX": 20, "toY": 10,
        "permitsStep": False,
        "routerCongestionBefore": 5, "routerCongestionAfter": 7,
        "routerCongestionDelta": 2,
        "workCongestionBefore": 9, "workCongestionAfter": 11,
        "workCongestionDelta": 2,
        "conflictsBefore": [{"netId": "B", "count": 1}],
        "conflictsAfter": [{"netId": "B", "count": 3}],
        "conflictsDelta": [{"netId": "B", "count": delta_count}],
        "targetOccupiedNet": None, "targetClearanceNet": clearance,
        "neighborOccupiedNets": [{"gridX": 1, "gridY": 1, "netId": "B"}],
        "classification": "FOREIGN_OCCUPANCY_REJECTION_RECORDED",
    }


class StrictReaderTests(unittest.TestCase):
    def test_json_rejects_duplicates_nonfinite_and_negative_zero(self):
        for raw, code in (
            (b'{"x":1,"x":2}', "JSON_DUPLICATE_KEY"),
            (b'{"x":NaN}', "JSON_NONFINITE"),
            (b'{"x":-0}', "JSON_NEGATIVE_ZERO"),
        ):
            with self.subTest(raw=raw), self.assertRaises(reader.Rejected) as caught:
                reader.strict_json(raw)
            self.assertEqual(str(caught.exception), code)

    def test_integer_validator_rejects_boolean(self):
        with self.assertRaises(reader.Rejected):
            reader._int(True, 0, 1, "LITERAL_INT")

    def test_cut_is_derived_from_reachable_planar_edges(self):
        result = reader._derive_cut_queries(TinyGraph(), {
            "cellCount": 4, "gridWidth": 2, "gridHeight": 2,
            "originX": 10, "originY": 20, "gridPitch": 5,
        })
        self.assertEqual(result["reachableStates"], [0, 1])
        self.assertEqual(result["directedPlanarEdgesExamined"], 1)
        queries = result["cutQueries"]
        self.assertEqual(len(queries), 2)
        self.assertEqual(
            [(row["ordinal"], row["fromState"], row["direction"], row["toState"])
             for row in queries],
            [(0, 0, "S", 2), (1, 1, "S", 3)],
        )
        self.assertTrue(all(row["expectedPermitsLayerStep"] is False for row in queries))

    def test_cut_excludes_disabled_internal_edges_between_multiple_starts(self):
        graph = TinyGraph()
        graph.starts = [0, 1]
        graph.steps = [0] * 8
        result = reader._derive_cut_queries(graph, {
            "cellCount": 4, "gridWidth": 2, "gridHeight": 2,
            "originX": 10, "originY": 20, "gridPitch": 5,
        })
        self.assertEqual(result["reachableStates"], [0, 1])
        self.assertEqual(result["directedPlanarEdgesExamined"], 0)
        self.assertEqual(
            [(row["fromState"], row["toState"]) for row in result["cutQueries"]],
            [(0, 2), (1, 3)],
        )
        self.assertTrue(all(row["toState"] not in result["reachableStates"]
                            for row in result["cutQueries"]))

    def test_foreign_delta_accepts_shared_clearance_sentinel(self):
        facts = {
            "gridWidth": 4, "gridHeight": 4, "gridPitch": 10,
            "originX": 0, "originY": 0, "netId": "A",
        }
        cut = {
            "ordinal": 0, "layer": 0, "fromState": 0, "toState": 1, "bit": 2,
            "fromX": 10, "fromY": 10, "toX": 20, "toY": 10,
        }
        self.assertTrue(reader._validate_observation(
            observation(), cut, facts, ["A", "B", "C"], 1, BranchReaderStub,
        ))

    def test_unknown_nonempty_clearance_owner_is_rejected(self):
        facts = {
            "gridWidth": 4, "gridHeight": 4, "gridPitch": 10,
            "originX": 0, "originY": 0, "netId": "A",
        }
        cut = {
            "ordinal": 0, "layer": 0, "fromState": 0, "toState": 1, "bit": 2,
            "fromX": 10, "fromY": 10, "toX": 20, "toY": 10,
        }
        with self.assertRaises(reader.Rejected):
            reader._validate_observation(
                observation("UNKNOWN_OWNER"), cut, facts, ["A", "B", "C"], 1,
                BranchReaderStub,
            )

    def test_delta_must_match_neighbor_histogram_times_congestion_delta(self):
        facts = {
            "gridWidth": 4, "gridHeight": 4, "gridPitch": 10,
            "originX": 0, "originY": 0, "netId": "A",
        }
        cut = {
            "ordinal": 0, "layer": 0, "fromState": 0, "toState": 1, "bit": 2,
            "fromX": 10, "fromY": 10, "toX": 20, "toY": 10,
        }
        with self.assertRaises(reader.Rejected):
            reader._validate_observation(
                observation(delta_count=1), cut, facts, ["A", "B", "C"], 1,
                BranchReaderStub,
            )

    def test_geometry_entry_census_is_literal_and_boolean_safe(self):
        rows = {
            "traces": [[None, "A", None, None, 0, "EXPOSED", 1, [1, 2], [3, 4]]],
            "holes": [],
        }
        self.assertEqual(reader._geometry_entries(rows), 13)
        rows["traces"][0][4] = True
        with self.assertRaises(reader.Rejected):
            reader._geometry_entries(rows)


class StableReadTests(unittest.TestCase):
    @staticmethod
    def _temporary_file():
        directory = Path(tempfile.mkdtemp(prefix="tsj-prefix-blocker-read-"))

        def cleanup():
            for name in ("source.bin", "replacement.bin"):
                candidate = directory / name
                try:
                    candidate.unlink()
                except FileNotFoundError:
                    pass
            directory.rmdir()

        path = directory / "source.bin"
        return directory, path, cleanup

    def _file(self, contents=b"stable"):
        directory, path, cleanup = self._temporary_file()
        self.addCleanup(cleanup)
        path.write_bytes(contents)
        return directory, path

    @staticmethod
    def _stat_record(size, ctime, *, dev=17, ino=23, mtime=29, nlink=1):
        return SimpleNamespace(
            st_dev=dev, st_ino=ino, st_size=size, st_mtime_ns=mtime,
            st_ctime_ns=ctime, st_nlink=nlink,
        )

    def _read_with_metadata(self, path, path_stats, handle_stats):
        path_values = iter(path_stats)
        handle_values = iter(handle_stats)

        def fake_path_stat(candidate, *args, **kwargs):
            if candidate == path:
                return next(path_values)
            return original_stat(candidate, *args, **kwargs)

        original_stat = type(path).stat
        with patch.object(reader, "no_reparse", return_value=path), \
                patch.object(type(path), "stat", new=fake_path_stat), \
                patch.object(reader.os, "fstat", side_effect=lambda _fd: next(handle_values)):
            return reader._stable_read(path, 64)

    def test_stable_read_accepts_unchanged_real_file(self):
        _directory, path = self._file()
        self.assertEqual(reader._stable_read(path, 64), b"stable")

    def test_stable_read_rejects_real_in_place_change_during_read(self):
        _directory, path = self._file()
        path_type = type(path)
        original_open = path_type.open

        class MutatingReader:
            def __init__(self, stream):
                self.stream = stream
                self.mutated = False

            def __enter__(self):
                return self

            def __exit__(self, exc_type, exc_value, traceback):
                return self.stream.__exit__(exc_type, exc_value, traceback)

            def fileno(self):
                return self.stream.fileno()

            def read(self, size=-1):
                raw = self.stream.read(size)
                if not self.mutated:
                    self.mutated = True
                    with open(path, "ab") as changed:
                        changed.write(b"!")
                return raw

        def mutating_open(candidate, *args, **kwargs):
            stream = original_open(candidate, *args, **kwargs)
            return MutatingReader(stream) if candidate == path else stream

        with patch.object(path_type, "open", new=mutating_open):
            with self.assertRaises(reader.Rejected) as caught:
                reader._stable_read(path, 64)
        self.assertEqual(str(caught.exception), "FILE_CHANGED")

    def test_stable_read_rejects_replacement_after_path_stat(self):
        directory, path = self._file(b"same-size")
        replacement = directory / "replacement.bin"
        replacement.write_bytes(b"same-size")
        before = path.stat()
        os.utime(replacement, ns=(before.st_atime_ns, before.st_mtime_ns))
        replacement_stat = replacement.stat()
        self.assertEqual(
            (before.st_dev, before.st_size, before.st_mtime_ns, before.st_nlink),
            (replacement_stat.st_dev, replacement_stat.st_size,
             replacement_stat.st_mtime_ns, replacement_stat.st_nlink),
        )
        self.assertNotEqual((before.st_dev, before.st_ino),
                            (replacement_stat.st_dev, replacement_stat.st_ino))

        path_type = type(path)
        original_open = path_type.open
        replaced = False

        def replacing_open(candidate, *args, **kwargs):
            nonlocal replaced
            if candidate == path and not replaced:
                os.replace(replacement, path)
                replaced = True
            return original_open(candidate, *args, **kwargs)

        with patch.object(path_type, "open", new=replacing_open):
            with self.assertRaises(reader.Rejected) as caught:
                reader._stable_read(path, 64)
        self.assertTrue(replaced)
        self.assertEqual(str(caught.exception), "FILE_CHANGED")

    def test_stable_read_accepts_literal_cross_api_ctime_divergence(self):
        _directory, path = self._file()
        size = len(b"stable")
        path_ctime = 1793938543852254100
        handle_ctime = 1793938543852764500
        self.assertEqual(self._read_with_metadata(
            path,
            [self._stat_record(size, path_ctime)] * 2,
            [self._stat_record(size, handle_ctime)] * 2,
        ), b"stable")

    def test_stable_read_rejects_ctime_changes_within_each_api(self):
        size = len(b"stable")
        cases = (
            ("path", (101, 102), (201, 201)),
            ("handle", (101, 101), (201, 202)),
        )
        for name, path_ctimes, handle_ctimes in cases:
            with self.subTest(api=name):
                _directory, path = self._file()
                with self.assertRaises(reader.Rejected) as caught:
                    self._read_with_metadata(
                        path,
                        [self._stat_record(size, value) for value in path_ctimes],
                        [self._stat_record(size, value) for value in handle_ctimes],
                    )
                self.assertEqual(str(caught.exception), "FILE_CHANGED")


if __name__ == "__main__":
    unittest.main()
