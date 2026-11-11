"""Explicit pinned-Source driver; no producer or Source-I/O host guarantee."""
import argparse
import importlib.util
from pathlib import Path
import sys
import unittest
HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('_runtime_inputs_source',HERE/'derive_runtime_inputs.py')
owner=importlib.util.module_from_spec(spec);spec.loader.exec_module(owner)
STUDY=None
FRAGMENT=None

class SourceTests(unittest.TestCase):
    def reject(self,study,fragment,code):
        with self.assertRaises(owner.SourceError) as raised:owner.derive(study,fragment)
        self.assertEqual(str(raised.exception),code)

    def test_exact_single_insertion_reconstruction(self):
        out=owner.derive(STUDY,FRAGMENT)
        self.assertEqual(out,owner.derive(STUDY,FRAGMENT))
        self.assertEqual(out.count(FRAGMENT),1)
        self.assertEqual(out.replace(FRAGMENT,b'',1),STUDY)

    def test_held_entries_preserved(self):
        out=owner.derive(STUDY,FRAGMENT)
        for raw in [b'    public static void main(String[] args) throws Exception {\n        throw new IllegalStateException("ADOPTED_SNAPSHOT_COMPONENT_ONLY");\n    }\n',b'    static Comparison assemble(Arguments args,long started) throws Exception {\n        throw new IllegalStateException("SELECTED_WIRE_CANARY_ONLY");\n    }\n']:
            self.assertEqual(out.count(raw),1)

    def test_exact_owner_mapping_and_no_meter_factory_or_capture_calls(self):
        self.assertIn(b'c.newInstance(args.runId,args.sourceManifestSha256,\n                args.wireContractSha256,args.outputDir)',FRAGMENT)
        for token in [b'inputContextSha256',b'new Map',b'dataJson(',b'captureOriginal(',b'.original(',b'.meter(',b'.charge(',b'.copy(']:
            self.assertNotIn(token,FRAGMENT)
        self.assertIn(b'if (args==null) throw new IOException("RUNTIME_INPUT_ARGUMENTS");',FRAGMENT)

    def test_no_legacy_adapter_or_loader_restored(self):
        out=owner.derive(STUDY,FRAGMENT)
        self.assertNotIn(b'Object inputs(Arguments args)',out)
        self.assertNotIn(b'SetupResult readInput(',out)

    def test_reject_study_types(self):
        for v in [None,'text',bytearray(STUDY),memoryview(STUDY)]:
            with self.subTest(kind=type(v).__name__):self.reject(v,FRAGMENT,'STUDY_SOURCE')

    def test_reject_study_mutation_shape_and_reuse(self):
        for v in [bytes([STUDY[0]^1])+STUDY[1:],STUDY[:-1],STUDY+b'\n',b'x'*(owner.SOURCE_CAP+1),owner.derive(STUDY,FRAGMENT)]:
            with self.subTest(size=len(v)):self.reject(v,FRAGMENT,'STUDY_SOURCE')

    def test_reject_fragment_types(self):
        for v in [None,'text',bytearray(FRAGMENT),memoryview(FRAGMENT)]:
            with self.subTest(kind=type(v).__name__):self.reject(STUDY,v,'FRAGMENT_SOURCE')

    def test_reject_fragment_mutation_and_bounds(self):
        for v in [bytes([FRAGMENT[0]^1])+FRAGMENT[1:],FRAGMENT[:-1],FRAGMENT+b'\n',b'x'*(owner.FRAGMENT_CAP+1)]:
            with self.subTest(size=len(v)):self.reject(STUDY,v,'FRAGMENT_SOURCE')

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
