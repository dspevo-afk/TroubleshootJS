"""Pure identity-policy tests and real Windows capture canaries."""
from __future__ import annotations

import os
import subprocess
import sys
import unittest

from windows_process_identity import (
    ABSENT, CIM_MICROSECOND, MATCH, NATIVE_100NS, PROCESS_QUERY_LIMITED_INFORMATION,
    QUERY_ABSENT, QUERY_FAILED, QUERY_PRESENT, SYNCHRONIZE, UNRESOLVED,
    _api, compare_identity, identity_from_handle, query_process,
)


PID = 4812
IMAGE = r"C:\Program Files\TroubleshootJS\runner.exe"


def identity(ticks, precision=NATIVE_100NS, image=IMAGE, pid=PID):
    return {"pid": pid, "creationFileTimeTicks": ticks,
            "birthPrecision": precision, "executable": image}


def observed(record, *, status=QUERY_PRESENT, live=None, pid=PID):
    return {"pid": pid, "queryStatus": status,
            "identity": record if live is None else live}


class CompareIdentityTests(unittest.TestCase):
    def test_exact_native_birth_and_case_insensitive_windows_image_match(self):
        old = identity(134356043031214268)
        live = identity(134356043031214268, image=r"c:/PROGRAM FILES/TroubleshootJS/runner.exe")
        self.assertEqual(MATCH, compare_identity(old, observed(old, live=live))["status"])

    def test_cim_births_compare_at_their_documented_microsecond_precision(self):
        old = identity(134356765446133230, CIM_MICROSECOND)
        live = identity(134356765446133230, CIM_MICROSECOND)
        self.assertEqual(MATCH, compare_identity(old, observed(old, live=live))["status"])

    def test_native_and_cim_interval_overlap_stays_unresolved(self):
        old = identity(134356043031214268, NATIVE_100NS)
        live = identity(134356043031214260, CIM_MICROSECOND)
        result = compare_identity(old, observed(old, live=live))
        self.assertEqual((UNRESOLVED, "cross-precision-birth-overlap"),
                         (result["status"], result["reason"]))
        live["executable"] = None
        result = compare_identity(old, observed(old, live=live))
        self.assertEqual((UNRESOLVED, "cross-precision-birth-overlap"),
                         (result["status"], result["reason"]))

    def test_native_outside_cim_microsecond_interval_proves_absence(self):
        old = identity(134356043031214270, NATIVE_100NS)
        live = identity(134356043031214260, CIM_MICROSECOND, image=None)
        result = compare_identity(old, observed(old, live=live))
        self.assertEqual((ABSENT, "precise-birth-differs"), (result["status"], result["reason"]))

    def test_different_precise_birth_proves_absence_even_without_live_image(self):
        old = identity(134356765446133230, CIM_MICROSECOND)
        live = identity(134356865793803060, CIM_MICROSECOND, image=None)
        result = compare_identity(old, observed(old, live=live))
        self.assertEqual((ABSENT, "precise-birth-differs"), (result["status"], result["reason"]))

    def test_same_birth_but_different_image_is_contradictory_and_unresolved(self):
        old = identity(134356765446133230, CIM_MICROSECOND)
        live = identity(134356765446133230, CIM_MICROSECOND, image=r"C:\Windows\System32\other.exe")
        result = compare_identity(old, observed(old, live=live))
        self.assertEqual((UNRESOLVED, "matching-birth-image-differs"),
                         (result["status"], result["reason"]))

    def test_same_birth_with_unknown_image_stays_unresolved(self):
        old = identity(134356765446133230, CIM_MICROSECOND)
        live = identity(134356765446133230, CIM_MICROSECOND, image=None)
        result = compare_identity(old, observed(old, live=live))
        self.assertEqual((UNRESOLVED, "matching-birth-image-missing-or-ambiguous"),
                         (result["status"], result["reason"]))

    def test_missing_or_ambiguous_birth_stays_unresolved_even_when_paths_differ(self):
        old = identity(None)
        live = identity(100, image=r"C:\Windows\other.exe")
        result = compare_identity(old, observed(old, live=live))
        self.assertEqual((UNRESOLVED, "recorded-birth-missing-or-ambiguous"),
                         (result["status"], result["reason"]))

        old = identity(100, "UNKNOWN_PRECISION")
        result = compare_identity(old, observed(old, live=live))
        self.assertEqual(UNRESOLVED, result["status"])

    def test_cim_value_with_submicrosecond_digits_is_ambiguous(self):
        old = identity(101, CIM_MICROSECOND)
        live = identity(101, CIM_MICROSECOND)
        self.assertEqual(UNRESOLVED, compare_identity(old, observed(old, live=live))["status"])

    def test_confirmed_absence_is_distinct_from_query_failure(self):
        old = identity(100)
        absent = compare_identity(old, observed(None, status=QUERY_ABSENT))
        failed = compare_identity(old, observed(None, status=QUERY_FAILED))
        self.assertEqual((ABSENT, QUERY_ABSENT), (absent["status"], absent["queryStatus"]))
        self.assertEqual((UNRESOLVED, QUERY_FAILED), (failed["status"], failed["queryStatus"]))
        missing_birth = compare_identity(identity(None), observed(None, status=QUERY_ABSENT))
        self.assertEqual((UNRESOLVED, "recorded-birth-missing-or-ambiguous"),
                         (missing_birth["status"], missing_birth["reason"]))

    def test_zero_birth_ticks_are_missing_not_a_valid_process_identity(self):
        old = identity(0)
        live = identity(0)
        result = compare_identity(old, observed(old, live=live))
        self.assertEqual((UNRESOLVED, "recorded-birth-missing-or-ambiguous"),
                         (result["status"], result["reason"]))

    def test_absence_and_live_identity_are_bound_to_the_recorded_pid(self):
        old = identity(100)
        wrong_pid = observed(None, status=QUERY_ABSENT, pid=PID + 1)
        wrong_live = observed(old, live=identity(100, pid=PID + 1))
        self.assertEqual(UNRESOLVED, compare_identity(old, wrong_pid)["status"])
        self.assertEqual(UNRESOLVED, compare_identity(old, wrong_live)["status"])

    def test_float_birth_is_not_truncated_into_a_false_identity(self):
        old = identity(100.0)
        self.assertEqual(UNRESOLVED, compare_identity(old, observed(old, live=identity(100)))["status"])

    def test_root_relative_and_control_character_image_paths_are_ambiguous(self):
        old = identity(100, image=r"\Windows\System32\runner.exe")
        live = identity(100, image=r"C:\Windows\System32\runner.exe")
        self.assertEqual(UNRESOLVED, compare_identity(old, observed(old, live=live))["status"])

        old = identity(100, image="C:\\Program Files\\bad\x01.exe")
        live = identity(100, image=r"C:\Program Files\bad.exe")
        self.assertEqual(UNRESOLVED, compare_identity(old, observed(old, live=live))["status"])

        old = identity(100, image=r"C:\Program Files\runner.exe" + "\n")
        live = identity(100, image=r"C:\Program Files\runner.exe")
        self.assertEqual(UNRESOLVED, compare_identity(old, observed(old, live=live))["status"])

    def test_malformed_unhashable_metadata_fails_closed(self):
        old = identity(100)
        old["birthPrecision"] = []
        live = identity(100)
        self.assertEqual(UNRESOLVED, compare_identity(old, observed(old, live=live))["status"])
        query = observed(None)
        query["queryStatus"] = []
        result = compare_identity(identity(100), query)
        self.assertEqual((UNRESOLVED, "UNKNOWN"), (result["status"], result["queryStatus"]))


@unittest.skipUnless(os.name == "nt", "requires native Windows process APIs")
class WindowsProcessIdentityCanaries(unittest.TestCase):
    def test_current_process_is_captured_and_matches_from_one_handle(self):
        query = query_process(os.getpid())
        self.assertEqual(QUERY_PRESENT, query["queryStatus"], query)
        self.assertEqual(MATCH, compare_identity(query["identity"], query)["status"])
        self.assertEqual(NATIVE_100NS, query["identity"]["birthPrecision"])

    def test_identity_from_caller_owned_handle_returns_the_native_record(self):
        kernel32 = _api()
        handle = kernel32.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION | SYNCHRONIZE,
                                      False, os.getpid())
        self.assertTrue(handle)
        try:
            record = identity_from_handle(handle)
            self.assertEqual(os.getpid(), record["pid"])
            self.assertEqual(NATIVE_100NS, record["birthPrecision"])
            self.assertIsInstance(record["creationFileTimeTicks"], int)
            self.assertTrue(record["executable"])
            self.assertEqual(record, identity_from_handle(handle, os.getpid()))
            with self.assertRaises(ValueError):
                identity_from_handle(handle, os.getpid() + 1)
        finally:
            kernel32.CloseHandle(handle)

    def test_exited_child_pid_is_absent_or_proven_reused(self):
        child = subprocess.Popen([sys.executable, "-c", "import time; time.sleep(0.5)"])
        try:
            before = query_process(child.pid)
            self.assertEqual(QUERY_PRESENT, before["queryStatus"], before)
            child.wait(timeout=10)
            after = query_process(child.pid)
            result = compare_identity(before["identity"], after)
            self.assertEqual(ABSENT, result["status"], {"query": after, "comparison": result})
        finally:
            if child.poll() is None:
                child.kill()
                child.wait(timeout=10)


if __name__ == "__main__":
    unittest.main()
