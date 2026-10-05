"""Focused pure classifier tests for the fresh Q30 release audit."""
from __future__ import annotations

import unittest

from audit_release import classify_process_rows
from windows_process_identity import QUERY_PRESENT, UNRESOLVED, ABSENT


class ReleaseAuditClassifierTests(unittest.TestCase):
    def test_saved_four_pid_reuse_observations_prove_all_old_instances_absent(self):
        old_image = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
        current_edge = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
        old_python = r"C:\Program Files\WindowsApps\PythonSoftwareFoundation.Python.3.13_3.13.3824.0_x64__qbz5n2kfra8p0\python3.13.exe"
        cases = [
            (740, 134356787622477720, old_image, 134356892787824120, current_edge),
            (6540, 134356787951066420, old_image, 134356837955143470, None),
            (21340, 134356801756807260, old_image, 134356892787796210, current_edge),
            (21340, 134356804847620180, old_python, 134356892787796210, current_edge),
        ]
        rows, queries = [], {}
        for pid, old_ticks, old_exe, live_ticks, live_exe in cases:
            rows.append({"pid": pid, "creationFileTimeTicks": old_ticks,
                         "birthPrecision": "CIM_MICROSECOND", "executable": old_exe})
            queries[pid] = {"pid": pid, "queryStatus": QUERY_PRESENT,
                "identity": {"pid": pid, "creationFileTimeTicks": live_ticks,
                    "birthPrecision": "CIM_MICROSECOND", "executable": live_exe}}
        result = classify_process_rows(rows, queries)
        self.assertEqual([ABSENT] * 4, [row["status"] for row in result])
        self.assertEqual("precise-birth-differs", result[1]["reason"])

    def test_same_birth_and_missing_image_remains_unresolved(self):
        row = {"pid": 6540, "creationFileTimeTicks": 134356787951066420,
               "birthPrecision": "CIM_MICROSECOND", "executable": r"C:\Program Files\x.exe"}
        live = {"pid": 6540, "queryStatus": QUERY_PRESENT, "identity": {
            "pid": 6540, "creationFileTimeTicks": row["creationFileTimeTicks"],
            "birthPrecision": "CIM_MICROSECOND", "executable": None}}
        self.assertEqual(UNRESOLVED, classify_process_rows([row], {6540: live})[0]["status"])


if __name__ == "__main__":
    unittest.main()
