"""Eleven bounded Source checks; no owner/receipt/normal entrypoints."""
import argparse,ast,hashlib
from pathlib import Path
import sys,types,unittest
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
owner=types.ModuleType('_capture_cohort_phases_source')
exec(compile((R/'derive_capture_cohort_phases.py').read_bytes(),'derive_capture_cohort_phases.py','exec',dont_inherit=True,optimize=0),owner.__dict__)

class SourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source=SOURCE.read_bytes();cls.bridge=(R/'CohortProjectionBridge.java.fragment').read_bytes();cls.phases=(R/'CaptureCohortPhases.java.fragment').read_bytes()
    def derive(self,**kw):
        v=dict(source=self.source,bridge=self.bridge,phases=self.phases);v.update(kw);return owner.derive(**v)
    def reject(self,code,**kw):
        with self.assertRaisesRegex(owner.SourceError,'^'+code+'$'):self.derive(**kw)
    def test_pins_and_unique_parts(self):
        for raw,n,pin in [(self.source,owner.SOURCE_BYTES,owner.SOURCE_SHA),(self.bridge,owner.BRIDGE_BYTES,owner.BRIDGE_SHA),(self.phases,owner.PHASES_BYTES,owner.PHASES_SHA)]:
            self.assertEqual(len(raw),n);self.assertEqual(hashlib.sha256(raw).hexdigest(),pin)
        out=self.derive();self.assertEqual(out.count(self.bridge),1);self.assertEqual(out.count(self.phases),1)
    def test_exact_inverse(self):
        a,b,c,d=owner.spans(self.source)
        out=self.derive().replace(self.bridge+b'\n',self.source[a:b],1).replace(self.phases+b'\n',self.source[c:d],1)
        self.assertEqual(out,self.source)
    def test_unchanged_three_regions(self):
        a,b,c,d=owner.spans(self.source);out=self.derive()
        self.assertEqual(out[:a],self.source[:a]);self.assertIn(self.source[b:c],out);self.assertTrue(out.endswith(self.source[d:]))
    def test_retired_api_and_single_derivation(self):
        out=self.derive();self.assertNotIn(b'static Map<String,Object> captureBoundBody(',out)
        self.assertEqual(out.count(b'CohortTerminalTelemetryStudy.deriveCohort(view, actualCapturedIds, meter)'),1)
        prepare=self.phases.split(b'static CaptureCohort prepareCaptureCohort(',1)[1].split(b'    /** Paid borrow;',1)[0]
        finish=self.phases.split(b'static Map<String,Object> finishCaptureBody(',1)[1]
        self.assertEqual(prepare.count(b'deriveSetupCohort('),1);self.assertNotIn(b'deriveSetupCohort(',finish)
        self.assertEqual(finish.count(b'CurrentResultProjections.cohort(meter, raw)'),1)
        self.assertLess(finish.index(b'meter.copy(4);'),finish.index(b'association.meter = null;'))
        self.assertLess(finish.index(b'meter.charge(4);'),finish.index(b'association.meter = null;'))
    def test_changed_source(self):self.reject('METADATA_SOURCE',source=b'x'+self.source[1:])
    def test_changed_bridge(self):self.reject('BRIDGE_SOURCE',bridge=b'x'+self.bridge[1:])
    def test_changed_phases(self):self.reject('PHASES_SOURCE',phases=b'x'+self.phases[1:])
    def test_truncated(self):
        self.reject('METADATA_SOURCE',source=self.source[:-1]);self.reject('BRIDGE_SOURCE',bridge=self.bridge[:-1]);self.reject('PHASES_SOURCE',phases=self.phases[:-1])
    def test_type_and_caps(self):
        for key,code,cap in [('source','METADATA_SOURCE',owner.SOURCE_CAP),('bridge','BRIDGE_SOURCE',owner.FRAGMENT_CAP),('phases','PHASES_SOURCE',owner.FRAGMENT_CAP)]:
            self.reject(code,**{key:bytearray(getattr(self,key))});self.reject(code,**{key:b'x'*(cap+1)})
    def test_derived_rejected(self):self.reject('METADATA_SOURCE',source=self.derive())
    def test_pure_owner(self):
        t=ast.parse((R/'derive_capture_cohort_phases.py').read_bytes())
        self.assertEqual([x.names[0].name for x in ast.walk(t) if isinstance(x,ast.Import)],['hashlib'])
        self.assertFalse(any(isinstance(x,ast.ImportFrom) for x in ast.walk(t)))
        self.assertFalse(any(isinstance(x,ast.Call) and isinstance(x.func,ast.Name) and x.func.id in {'open','exec','eval','compile','__import__'} for x in ast.walk(t)))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--metadata-source',required=True,type=Path)
    a,rest=p.parse_known_args();SOURCE=a.metadata_source;unittest.main(argv=[sys.argv[0]]+rest)
