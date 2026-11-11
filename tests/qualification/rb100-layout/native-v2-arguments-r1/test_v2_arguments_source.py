"""Explicit Source fixture driver; not a producer or Source-I/O host proof."""
import argparse
import hashlib
import importlib.util
from pathlib import Path
import sys
import unittest

HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('_v2_arguments_source',HERE/'derive_v2_arguments.py')
owner=importlib.util.module_from_spec(spec)
spec.loader.exec_module(owner)
STUDY=None
FRAGMENT=None


class SourceTests(unittest.TestCase):
    def reject(self,study,fragment,code):
        with self.assertRaises(owner.SourceError) as raised:owner.derive(study,fragment)
        self.assertEqual(str(raised.exception),code)

    def test_exact_replay_and_four_changes_only(self):
        out=owner.derive(STUDY,FRAGMENT)
        self.assertEqual(out,owner.derive(STUDY,FRAGMENT))
        old_args=owner.clause(STUDY,owner.ARGUMENTS_START,owner.OLD_ARGUMENTS_BYTES,
            owner.OLD_ARGUMENTS_SHA,'ARGS')
        old_inputs=owner.clause(STUDY,owner.INPUTS_START,owner.OLD_INPUTS_BYTES,
            owner.OLD_INPUTS_SHA,'INPUTS')
        restored=out.replace(FRAGMENT,old_args,1)
        anchor=b'        Object meter(long started) throws Exception {\n'
        self.assertEqual(restored.count(anchor),1)
        restored=restored.replace(anchor,old_inputs+anchor,1)
        restored=restored.replace(owner.NEW_MANIFEST_READ,owner.OLD_MANIFEST_READ,1)
        legacy_start=b'    @SuppressWarnings("unchecked")\n    static SetupResult readInput('
        pos=STUDY.index(legacy_start)
        legacy=STUDY[pos:pos+1710]
        anchor=STUDY[pos+1710:pos+1774]
        self.assertEqual(restored.count(anchor),1)
        restored=restored.replace(anchor,legacy+anchor,1)
        self.assertEqual(restored,STUDY)

    def test_existing_parent_signature_and_loader_preserved(self):
        out=owner.derive(STUDY,FRAGMENT)
        for literal in [b'final Class<?> owner, inputsType, meterType, originalType, captureType;',
            b'inputsType=Class.forName(PARENT+"$Inputs");',
            b'new Class<?>[]{inputsType,meterType}']:
            self.assertEqual(out.count(literal),1)
        self.assertNotIn(owner.INPUTS_START,out)

    def test_held_entries_preserved(self):
        out=owner.derive(STUDY,FRAGMENT)
        for literal in [
            b'    public static void main(String[] args) throws Exception {\n        throw new IllegalStateException("ADOPTED_SNAPSHOT_COMPONENT_ONLY");\n    }\n',
            b'    static Comparison assemble(Arguments args,long started) throws Exception {\n        throw new IllegalStateException("SELECTED_WIRE_CANARY_ONLY");\n    }\n']:
            self.assertEqual(out.count(literal),1)

    def test_generic_arguments_and_all_other_source_unchanged(self):
        out=owner.derive(STUDY,FRAGMENT)
        cut=STUDY.index(owner.ARGUMENTS_START)
        self.assertEqual(out[:cut],STUDY[:cut])
        self.assertIn(b'r7ReadObject(m,args,"runId")',out)
        self.assertNotIn(owner.OLD_MANIFEST_READ,out)
        self.assertEqual(out.count(owner.NEW_MANIFEST_READ),1)

    def test_reject_wrong_study_types(self):
        for value in [None,'text',bytearray(STUDY),memoryview(STUDY)]:
            with self.subTest(kind=type(value).__name__):self.reject(value,FRAGMENT,'STUDY_SOURCE')

    def test_reject_changed_truncated_oversized_or_derived_study(self):
        changed=bytes([STUDY[0]^1])+STUDY[1:]
        for value in [changed,STUDY[:-1],STUDY+b'\n',b'x'*(owner.SOURCE_CAP+1),
                owner.derive(STUDY,FRAGMENT),STUDY.replace(b'\n',b'\r\n')]:
            with self.subTest(size=len(value)):self.reject(value,FRAGMENT,'STUDY_SOURCE')

    def test_reject_wrong_fragment_types(self):
        for value in [None,'text',bytearray(FRAGMENT),memoryview(FRAGMENT)]:
            with self.subTest(kind=type(value).__name__):self.reject(STUDY,value,'FRAGMENT_SOURCE')

    def test_reject_changed_truncated_oversized_fragment(self):
        changed=bytes([FRAGMENT[0]^1])+FRAGMENT[1:]
        for value in [changed,FRAGMENT[:-1],FRAGMENT+b'\n',b'x'*(owner.FRAGMENT_CAP+1)]:
            with self.subTest(size=len(value)):self.reject(STUDY,value,'FRAGMENT_SOURCE')


def main():
    global STUDY,FRAGMENT
    parser=argparse.ArgumentParser()
    parser.add_argument('--study-source',required=True)
    parsed,rest=parser.parse_known_args()
    source=Path(parsed.study_source)
    if source.stat().st_size>owner.SOURCE_CAP:raise ValueError('SOURCE_CAP')
    STUDY=source.read_bytes();FRAGMENT=(HERE/'V2Arguments.java.fragment').read_bytes()
    owner.checked(STUDY,owner.STUDY_BYTES,owner.STUDY_SHA,owner.SOURCE_CAP,'STUDY_SOURCE')
    owner.checked(FRAGMENT,owner.FRAGMENT_BYTES,owner.FRAGMENT_SHA,owner.FRAGMENT_CAP,'FRAGMENT_SOURCE')
    unittest.main(argv=[sys.argv[0],*rest])


if __name__=='__main__':main()
