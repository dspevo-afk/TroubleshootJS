"""Explicit Source fixture driver; not producer or Source-I/O host acceptance."""
import argparse
import importlib.util
from pathlib import Path
import sys
import unittest
HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('_runtime_prepayment',HERE/'derive_runtime_prepayment.py')
owner=importlib.util.module_from_spec(spec);spec.loader.exec_module(owner)
STUDY=None
FRAGMENT=None

class SourceTests(unittest.TestCase):
    def reject(self,s,f,code):
        with self.assertRaises(owner.SourceError) as raised:owner.derive(s,f)
        self.assertEqual(str(raised.exception),code)

    def test_exact_one_clause_reconstruction(self):
        out=owner.derive(STUDY,FRAGMENT)
        self.assertEqual(out,owner.derive(STUDY,FRAGMENT));self.assertEqual(out.count(FRAGMENT),1)
        self.assertEqual(out.replace(FRAGMENT,owner.old_clause(STUDY),1),STUDY)

    def test_admission_and_prepayment_precede_allocation(self):
        tokens=[b'if (m==null',b'm.charge(2)',b'if (args==null',b'r7ReadObject(m,args,"runId")',b'r7ReadObject(m,args,"sourceManifestSha256")',b'r7ReadObject(m,args,"wireContractSha256")',b'r7ReadObject(m,args,"outputDir")',b'm.copy(4)',b'inputsType.getDeclaredConstructor',b'c.newInstance(run,manifest,wire,output)',b'm.finish()',b'return inputs']
        offsets=[FRAGMENT.index(t) for t in tokens];self.assertEqual(offsets,sorted(offsets))
        for t in [b'.original(',b'captureOriginal(',b'dataJson(',b'new SetupMeter',b'inputContextSha256']:
            self.assertNotIn(t,FRAGMENT)

    def test_unmetered_overload_retired(self):
        out=owner.derive(STUDY,FRAGMENT)
        self.assertNotIn(b'Object runtimeInputs(Arguments args)',out)
        self.assertEqual(out.count(b'Object runtimeInputs(Arguments args,SetupMeter m)'),1)

    def test_held_entries_preserved(self):
        out=owner.derive(STUDY,FRAGMENT)
        for s in [b'    public static void main(String[] args) throws Exception {\n        throw new IllegalStateException("ADOPTED_SNAPSHOT_COMPONENT_ONLY");\n    }\n',b'    static Comparison assemble(Arguments args,long started) throws Exception {\n        throw new IllegalStateException("SELECTED_WIRE_CANARY_ONLY");\n    }\n']:
            self.assertEqual(out.count(s),1)

    def test_reject_wrong_study_types(self):
        for s in [None,'text',bytearray(STUDY),memoryview(STUDY)]:
            with self.subTest(kind=type(s).__name__):self.reject(s,FRAGMENT,'STUDY_SOURCE')

    def test_reject_changed_and_derived_study(self):
        for s in [bytes([STUDY[0]^1])+STUDY[1:],STUDY[:-1],STUDY+b'\n',b'x'*(owner.SOURCE_CAP+1),owner.derive(STUDY,FRAGMENT)]:
            with self.subTest(size=len(s)):self.reject(s,FRAGMENT,'STUDY_SOURCE')

    def test_reject_wrong_fragment_types(self):
        for f in [None,'text',bytearray(FRAGMENT),memoryview(FRAGMENT)]:
            with self.subTest(kind=type(f).__name__):self.reject(STUDY,f,'FRAGMENT_SOURCE')

    def test_reject_changed_fragment(self):
        for f in [bytes([FRAGMENT[0]^1])+FRAGMENT[1:],FRAGMENT[:-1],FRAGMENT+b'\n',b'x'*(owner.FRAGMENT_CAP+1)]:
            with self.subTest(size=len(f)):self.reject(STUDY,f,'FRAGMENT_SOURCE')

def main():
    global STUDY,FRAGMENT
    parser=argparse.ArgumentParser();parser.add_argument('--study-source',required=True)
    args,rest=parser.parse_known_args();p=Path(args.study_source)
    if p.stat().st_size>owner.SOURCE_CAP:raise ValueError('SOURCE_CAP')
    STUDY=p.read_bytes();FRAGMENT=(HERE/'RuntimeInputs.java.fragment').read_bytes()
    owner.checked(STUDY,owner.STUDY_BYTES,owner.STUDY_SHA,owner.SOURCE_CAP,'STUDY_SOURCE')
    owner.checked(FRAGMENT,owner.FRAGMENT_BYTES,owner.FRAGMENT_SHA,owner.FRAGMENT_CAP,'FRAGMENT_SOURCE')
    unittest.main(argv=[sys.argv[0],*rest])

if __name__=='__main__':main()
