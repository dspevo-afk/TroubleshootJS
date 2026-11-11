"""Bounded explicit Source fixtures; no native/normal producer acceptance."""
import argparse
import importlib.util
from pathlib import Path
import sys
import unittest
HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('_allowance_source',HERE/'derive_capture_allowance.py')
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

    def test_prepayment_before_inspections_and_mutations(self):
        split=FRAGMENT.index(b'    static void mergeCaptureAllowance(')
        last=FRAGMENT.index(b'    static Object captureWithSetup(')
        for body,charge in [(FRAGMENT[:split],b'meter.charge(30L)'),(FRAGMENT[split:last],b'meter.charge(32L)')]:
            p=body.index(charge)
            for token in [b'if (setup==null)',b'=setup.units',b'field(raw,"open")',b'setNumber(raw,"copiedEntries"',b'meter.method("setCopyAllowance"',b'meter.finish()']:
                self.assertLess(p,body.index(token))
            self.assertLess(body.index(b'meter.method("setCopyAllowance"'),body.index(b'meter.finish()'))
        wrapper=FRAGMENT[last:]
        tokens=[b'meter.parent!=parent',b'beginCaptureAllowance(meter,setup)',b'parent.original(inputs,meter.meter)',b'meter.charge(5L)',b'field(original,"captureData")',b'mergeCaptureAllowance(meter,setup,remaining']
        self.assertEqual([wrapper.index(t) for t in tokens],sorted(wrapper.index(t) for t in tokens))
        self.assertEqual(wrapper.count(b'field('),5)

    def test_existing_carrier_and_input_holder_unchanged(self):
        out=owner.derive(STUDY,FRAGMENT)
        for start,end in [(b'        /** Prepaid runtime carrier only;',b'        Object meter(long started)'),(b'    static final class SetupResult {',b'    static int countMetered(')]:
            at=STUDY.index(start);stop=STUDY.index(end,at);block=STUDY[at:stop]
            self.assertEqual(out.count(block),1)

    def test_held_entries_preserved(self):
        out=owner.derive(STUDY,FRAGMENT)
        for block in [b'    public static void main(String[] args) throws Exception {\n        throw new IllegalStateException("ADOPTED_SNAPSHOT_COMPONENT_ONLY");\n    }\n',b'    static Comparison assemble(Arguments args,long started) throws Exception {\n        throw new IllegalStateException("SELECTED_WIRE_CANARY_ONLY");\n    }\n']:
            self.assertEqual(out.count(block),1)

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
    STUDY=p.read_bytes();FRAGMENT=(HERE/'CaptureAllowance.java.fragment').read_bytes()
    owner.checked(STUDY,owner.STUDY_BYTES,owner.STUDY_SHA,owner.SOURCE_CAP,'STUDY_SOURCE')
    owner.checked(FRAGMENT,owner.FRAGMENT_BYTES,owner.FRAGMENT_SHA,owner.FRAGMENT_CAP,'FRAGMENT_SOURCE')
    unittest.main(argv=[sys.argv[0],*rest])

if __name__=='__main__':main()
