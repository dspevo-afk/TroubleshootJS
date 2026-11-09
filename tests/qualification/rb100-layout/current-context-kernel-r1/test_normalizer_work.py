"""Synthetic direct tests for normalize_prior._validate_prior_work.

The values below are authored independently from the validator. They model a
small coherent prior-work census and do not read prior operation/result DATA.
"""

import importlib.util
from pathlib import Path
import sys
import unittest


_SOURCE = Path(__file__).resolve().with_name("normalize_prior.py")
_SPEC = importlib.util.spec_from_file_location(
    "_tsj_normalize_prior_work_under_test", _SOURCE)
if _SPEC is None or _SPEC.loader is None:
    raise RuntimeError("NORMALIZER_SOURCE_LOAD")
sut = importlib.util.module_from_spec(_SPEC)
sys.modules[_SPEC.name] = sut
_SPEC.loader.exec_module(sut)


METADATA_TREE_MINIMUM = 13


def _valid_work_case():
    work = {
        "elapsedCutoffNanos": 1_000_000,
        "cleanupNanos": 4_000,
        "groupsBeforeExport": 12,
        "wrapperGroupsBeforeExport": 2,
        "maxGroupNanosBeforeExport": 2_600,
        "maximumUnitsPerGroupBeforeExport": 23,
        "unitsBeforeExport": 84,
        "originalOwnerSteps": 30,
        "originalDiagnosticUnits": 5,
        "originalCaptureUnits": 4,
        "extraTerminalCallbackUnits": 1,
        "controlOwnerUnits": 6,
        "controlWrapperUnits": 7,
        "trialOwnerUnits": 8,
        "trialWrapperUnits": 10,
        "controlFailureObservationUnits": 2,
        "trialFailureObservationUnits": 3,
        "controlTerminalEvidenceUnits": 3,
        "trialTerminalEvidenceUnits": 4,
        "metadataUnits": 13,
        "copiedEntries": 12,
        "copyChunks": 2,
        "copyChunkLimit": 4096,
        "copiedEntryLimit": 1_048_576,
        "cleanup": {
            "originalOwnersReleased": True,
            "originalContextRestored": True,
            "controlReferencesDropped": True,
            "trialReferencesDropped": True,
            "sourceUnchanged": True,
            "allReferencesDropped": True,
        },
    }
    operation = {
        "p07OwnerSteps": 30,
        "callbackUnits": 4,
        "groupCount": 5,
        "maxFullGroupNanos": 2_500,
        "cleanupNanos": 3_000,
    }
    parsed = {
        "groups": [
            {"ordinal": 0, "p07Steps": 10, "diagnosticUnits": 2,
             "elapsedBeforeOwnLineNanos": 1_000},
            {"ordinal": 1, "p07Steps": 20, "diagnosticUnits": 3,
             "elapsedBeforeOwnLineNanos": 2_000},
            {"ordinal": 2, "p07Steps": 0, "diagnosticUnits": 0,
             "elapsedBeforeOwnLineNanos": 0},
            {"ordinal": 3, "p07Steps": 0, "diagnosticUnits": 0,
             "elapsedBeforeOwnLineNanos": 0},
            {"ordinal": 4, "p07Steps": 0, "diagnosticUnits": 0,
             "elapsedBeforeOwnLineNanos": 0},
        ],
    }
    reference = {"groupOrdinal": 0}
    control = {
        "work": {
            "advanceCalls": 2,
            "ownerUnits": 6,
            "wrapperUnits": 7,
            "failureObservationUnits": 2,
            "terminalEvidenceUnits": 3,
            "groups": 2,
            "maxGroupNanos": 2_200,
            "maximumUnitsPerGroup": 9,
            "copyEntries": 5,
            "copyChunks": 1,
        },
    }
    trial = {
        "work": {
            "advanceCalls": 3,
            "ownerUnits": 8,
            "wrapperUnits": 10,
            "failureObservationUnits": 3,
            "terminalEvidenceUnits": 4,
            "groups": 3,
            "maxGroupNanos": 2_600,
            "maximumUnitsPerGroup": 11,
            "copyEntries": 7,
            "copyChunks": 1,
        },
    }
    return (work, operation, parsed, reference, control, trial,
            METADATA_TREE_MINIMUM)


class PriorWorkValidationTests(unittest.TestCase):
    def _rejects(self, expected_code, args):
        with self.assertRaises(sut.PriorNormalizationError) as failure:
            sut._validate_prior_work(*args)
        self.assertEqual(failure.exception.code, expected_code)

    def test_accepts_hand_calculated_coherent_work(self):
        args = _valid_work_case()
        work, operation, parsed, _reference, _control, _trial, minimum = args

        # Independent literal equations: owner=10+20, diagnostics=2+3;
        # callback group=10+2+4+1; additive units=84; groups=5+2+3+2.
        self.assertEqual(work["originalOwnerSteps"], 10 + 20)
        self.assertEqual(work["originalDiagnosticUnits"], 2 + 3)
        self.assertLessEqual(10 + 2 + operation["callbackUnits"]
                             + work["extraTerminalCallbackUnits"], 32_768)
        self.assertEqual(work["unitsBeforeExport"],
                         30 + 5 + 4 + 1 + 6 + 7 + 8 + 10 + 13)
        self.assertEqual(work["groupsBeforeExport"], 5 + 2 + 3 + 2)
        self.assertEqual(work["copiedEntries"], 5 + 7)
        self.assertEqual(work["copyChunks"], 1 + 1)
        self.assertEqual(minimum, METADATA_TREE_MINIMUM)
        self.assertGreaterEqual(work["metadataUnits"], minimum)
        self.assertEqual(max(2_500, 1_000, 2_000, 2_200, 2_600),
                         work["maxGroupNanosBeforeExport"])
        self.assertEqual(max(23, 9, 11), work["maximumUnitsPerGroupBeforeExport"])
        self.assertEqual(len(parsed["groups"]), 5)
        self.assertIsNone(sut._validate_prior_work(*args))

    def test_rejects_owner_type(self):
        args = _valid_work_case()
        args[0]["originalOwnerSteps"] = True
        self._rejects("PRIOR_WORK_INTEGER", args)

    def test_rejects_owner_binding(self):
        args = _valid_work_case()
        args[0]["originalOwnerSteps"] = 31
        self._rejects("ORIGINAL_WORK_BINDING", args)

    def test_rejects_callback_group_overflow(self):
        args = _valid_work_case()
        args[0]["extraTerminalCallbackUnits"] = 32_768
        self._rejects("ORIGINAL_CALLBACK_GROUP_LIMIT", args)

    def test_rejects_group_total_mismatch(self):
        args = _valid_work_case()
        args[0]["groupsBeforeExport"] = 13
        self._rejects("PRIOR_WORK_GROUPS", args)

    def test_rejects_copy_entry_limit(self):
        args = _valid_work_case()
        args[0]["copiedEntries"] = 1_048_577
        args[0]["copyChunks"] = 257
        self._rejects("PRIOR_WORK_BOUNDS", args)

    def test_rejects_elapsed_cutoff_limit(self):
        args = _valid_work_case()
        args[0]["elapsedCutoffNanos"] = 90_000_000_001
        self._rejects("PRIOR_WORK_BOUNDS", args)

    def test_rejects_cleanup_duration_limit(self):
        args = _valid_work_case()
        args[0]["cleanupNanos"] = 120_000_000_001
        self._rejects("PRIOR_WORK_BOUNDS", args)

    def test_rejects_metadata_below_tree_minimum(self):
        args = _valid_work_case()
        args[0]["metadataUnits"] = 12
        # Keep the additive equation coherent: 84 - 1 metadata unit = 83.
        args[0]["unitsBeforeExport"] = 83
        self._rejects("PRIOR_WORK_UNITS", args)

    def test_rejects_cleanup_proof_mutation(self):
        args = _valid_work_case()
        args[0]["cleanup"]["sourceUnchanged"] = False
        self._rejects("PRIOR_CLEANUP_PROOF", args)

    def test_fixture_is_fresh_per_case(self):
        first = _valid_work_case()
        second = _valid_work_case()
        first[0]["cleanup"]["allReferencesDropped"] = False
        self.assertTrue(second[0]["cleanup"]["allReferencesDropped"])
        self.assertIsNot(first[0], second[0])


if __name__ == "__main__":
    unittest.main()
