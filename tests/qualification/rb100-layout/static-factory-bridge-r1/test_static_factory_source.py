"""Source fixtures only; actual static dispatch requires the native gates."""
import argparse,importlib.util,sys,unittest
from pathlib import Path
HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('_static_factory_owner',HERE/'derive_static_factory_bridge.py')
owner=importlib.util.module_from_spec(spec);spec.loader.exec_module(owner)
SOURCE=None
FRAGMENT=None

class SourceTests(unittest.TestCase):
    def reject(self,s,f,code):
        with self.assertRaises(owner.SourceError) as raised:owner.derive(s,f)
        self.assertEqual(str(raised.exception),code)

    def test_exact_single_span_conservation(self):
        out=owner.derive(SOURCE,FRAGMENT)
        self.assertEqual(out,owner.derive(SOURCE,FRAGMENT));self.assertEqual(out.count(FRAGMENT),1)
        self.assertEqual(out.replace(FRAGMENT+b'\n',owner.old_clause(SOURCE),1),SOURCE)

    def test_shared_body_byte_conservation(self):
        old=owner.old_clause(SOURCE);marker=b'        fresh(m, producerStarted);\n'
        tail=old[old.index(marker)+len(marker):]
        packet=FRAGMENT+b'\n'
        self.assertTrue(packet.endswith(tail));self.assertEqual(packet.count(tail),1)
        self.assertEqual(FRAGMENT.count(b'readPrepared('),3)
        self.assertEqual(FRAGMENT.count(marker),2)
        self.assertEqual(FRAGMENT.count(b'State s = new State(m);'),1)

    def test_paid_fieldless_fixed_facade_order(self):
        current=FRAGMENT[:FRAGMENT.index(b'    static CohortTerminalTelemetryStudy.SetupResult read(')]
        tokens=[b'expectedContextSha == null',b'fresh(m, producerStarted)',b'm.charge(1)',b'new Factory()',b'return readPrepared(m, expectedContextSha, factory)']
        offsets=[current.index(x) for x in tokens];self.assertEqual(offsets,sorted(offsets))
        for name in ['schemaTag','contextSha256','dataEntries','dataJson']:
            self.assertEqual(current.count(('CohortTerminalTelemetryInputV2.'+name+'()').encode()),1)
        self.assertNotIn(b'Class.forName',current);self.assertNotIn(b'new LinkedHashMap',current)
        self.assertNotIn(b'new ArrayList',current)

    def test_existing_injected_entry_keeps_fresh_no_facade_charge(self):
        at=FRAGMENT.index(b'    static CohortTerminalTelemetryStudy.SetupResult read(')
        stop=FRAGMENT.index(b'    /** Both callers',at);body=FRAGMENT[at:stop]
        self.assertIn(b'factory == null',body);self.assertIn(b'fresh(m, producerStarted);',body)
        self.assertIn(b'return readPrepared(m, expectedContextSha, factory);',body)
        self.assertNotIn(b'm.charge(',body);self.assertNotIn(b'new Factory()',body)

    def test_reject_wrong_source_types(self):
        for s in [None,'text',bytearray(SOURCE),memoryview(SOURCE)]:
            with self.subTest(kind=type(s).__name__):self.reject(s,FRAGMENT,'INPUT_SOURCE')

    def test_reject_changed_and_derived_source(self):
        for s in [bytes([SOURCE[0]^1])+SOURCE[1:],SOURCE[:-1],SOURCE+b'\n',b'x'*(owner.SOURCE_CAP+1),owner.derive(SOURCE,FRAGMENT)]:
            with self.subTest(size=len(s)):self.reject(s,FRAGMENT,'INPUT_SOURCE')

    def test_reject_wrong_fragment_types(self):
        for f in [None,'text',bytearray(FRAGMENT),memoryview(FRAGMENT)]:
            with self.subTest(kind=type(f).__name__):self.reject(SOURCE,f,'FRAGMENT_SOURCE')

    def test_reject_changed_fragment(self):
        for f in [bytes([FRAGMENT[0]^1])+FRAGMENT[1:],FRAGMENT[:-1],FRAGMENT+b'\n',b'x'*(owner.FRAGMENT_CAP+1)]:
            with self.subTest(size=len(f)):self.reject(SOURCE,f,'FRAGMENT_SOURCE')

def main():
    global SOURCE,FRAGMENT
    p=argparse.ArgumentParser();p.add_argument('--input-source',required=True);args,rest=p.parse_known_args()
    path=Path(args.input_source)
    if path.stat().st_size>owner.SOURCE_CAP:raise ValueError('SOURCE_CAP')
    SOURCE=path.read_bytes();FRAGMENT=(HERE/'StaticFactoryBridge.java.fragment').read_bytes()
    owner.checked(SOURCE,owner.SOURCE_BYTES,owner.SOURCE_SHA,owner.SOURCE_CAP,'INPUT_SOURCE')
    owner.checked(FRAGMENT,owner.FRAGMENT_BYTES,owner.FRAGMENT_SHA,owner.FRAGMENT_CAP,'FRAGMENT_SOURCE')
    unittest.main(argv=[sys.argv[0],*rest])

if __name__=='__main__':main()
