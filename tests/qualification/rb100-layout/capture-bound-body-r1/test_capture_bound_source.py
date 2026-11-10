"""Eight Source checks; no JVM, normal owner or receipt entrypoints."""
import argparse,ast,hashlib
from pathlib import Path
import sys,types,unittest
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
owner=types.ModuleType('_capture_bound_source')
exec(compile((R/'derive_capture_bound_body.py').read_bytes(),'derive_capture_bound_body.py','exec',dont_inherit=True,optimize=0),owner.__dict__)

class SourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source=SOURCE.read_bytes();cls.fragment=(R/'CaptureBoundBody.java.fragment').read_bytes()
    def derive(self,**kw):
        v=dict(source=self.source,fragment=self.fragment);v.update(kw);return owner.derive(**v)
    def reject(self,code,**kw):
        with self.assertRaisesRegex(owner.SourceError,'^'+code+'$'):self.derive(**kw)
    def test_exact_once_inverse_and_single_capture_binding(self):
        out=self.derive();self.assertEqual(out.replace(self.fragment+b'\n',b'',1),self.source)
        self.assertEqual(out.count(self.fragment),1)
        self.assertEqual(self.fragment.count(b'originalCapture.get("netIds")'),1)
        self.assertIn(b'cohortBeforeBody(meter, setup, (List<String>)ids)',self.fragment)
        self.assertIn(b'originalCapture, cohort, arms);',self.fragment)
        signature=self.fragment.split(b'static Map<String,Object> captureBoundBody(',1)[1].split(b') throws',1)[0]
        self.assertNotIn(b'List<',signature)
    def test_unchanged_prefix_suffix_and_pins(self):
        out=self.derive();at=self.source.index(owner.ANCHOR)
        self.assertEqual(out[:at],self.source[:at]);self.assertEqual(out[at+len(self.fragment)+1:],self.source[at:])
        self.assertEqual(hashlib.sha256(self.source).hexdigest(),owner.SOURCE_SHA)
        self.assertEqual(hashlib.sha256(self.fragment).hexdigest(),owner.FRAGMENT_SHA)
    def test_changed_source(self):self.reject('METADATA_SOURCE',source=b'x'+self.source[1:])
    def test_changed_fragment(self):self.reject('BINDING_SOURCE',fragment=b'x'+self.fragment[1:])
    def test_truncated(self):
        self.reject('METADATA_SOURCE',source=self.source[:-1]);self.reject('BINDING_SOURCE',fragment=self.fragment[:-1])
    def test_type_and_caps(self):
        self.reject('METADATA_SOURCE',source=bytearray(self.source));self.reject('BINDING_SOURCE',fragment=bytearray(self.fragment))
        self.reject('METADATA_SOURCE',source=b'x'*(owner.SOURCE_CAP+1));self.reject('BINDING_SOURCE',fragment=b'x'*(owner.FRAGMENT_CAP+1))
    def test_derived_input_rejected(self):self.reject('METADATA_SOURCE',source=self.derive())
    def test_pure_owner(self):
        t=ast.parse((R/'derive_capture_bound_body.py').read_bytes())
        self.assertEqual([x.names[0].name for x in ast.walk(t) if isinstance(x,ast.Import)],['hashlib'])
        self.assertFalse(any(isinstance(x,ast.ImportFrom) for x in ast.walk(t)))
        self.assertFalse(any(isinstance(x,ast.Call) and isinstance(x.func,ast.Name) and x.func.id in {'open','exec','eval','compile','__import__'} for x in ast.walk(t)))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--metadata-source',required=True,type=Path)
    a,rest=p.parse_known_args();SOURCE=a.metadata_source;unittest.main(argv=[sys.argv[0]]+rest)
