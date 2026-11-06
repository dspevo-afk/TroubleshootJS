import copy,json,unittest
import read_canary as r

def valid(mode='RAMP'):
    return dict(schema=r.SCHEMA,mode=mode,assertions=1,price='PASS',literalPath='PASS',realBranch='PASS',
      zeroCapGuard='PASS',saturatedCapGuard='PASS',copyGate='PASS',sourceUnchanged='PASS',captureOutput='PASS',
      copyPrefixHoles=2,copyMaximumVias=48,baseViaCost=60,sourceBranchPrice=62 if mode=='RAMP' else 60,
      copiedBase60Price=62 if mode=='RAMP' else 60,copiedRaisedPrice=120,priceRows=3,
      authoredRun='NOT_RUN',phase2='NOT_RUN',scope='PREFIX_VIA_PRICE_PREREQUISITE_ONLY',layoutQualification=False,canaryWorkElapsedNanos=1000)

class NativeBoundary(unittest.TestCase):
    def test_both_exact_modes(self):
        for mode in ('BASE','RAMP'):self.assertEqual(r.validate_native(valid(mode),mode)['mode'],mode)
    def test_each_required_gate(self):
        for gate in r.GATES:
            for bad in ('FAIL','NOT_RUN',True,None):
                with self.subTest(gate=gate,bad=bad):
                    x=valid();x[gate]=bad
                    with self.assertRaises(r.Rejected):r.validate_native(x,'RAMP')
    def test_no_missing_or_extra_field(self):
        for key in r.NATIVE_KEYS:
            x=valid();del x[key]
            with self.assertRaises(r.Rejected):r.validate_native(x,'RAMP')
        x=valid();x['unexpected']='PASS'
        with self.assertRaises(r.Rejected):r.validate_native(x,'RAMP')
    def test_literal_prices_and_types(self):
        for key in ('copyPrefixHoles','copyMaximumVias','baseViaCost','sourceBranchPrice','copiedBase60Price','copiedRaisedPrice'):
            for value in (False,True,0,1.0,'62',-1):
                x=valid();x[key]=value
                with self.subTest(key=key,value=value):
                    with self.assertRaises(r.Rejected):r.validate_native(x,'RAMP')
    def test_row_and_assertion_counts(self):
        for key in ('priceRows','assertions'):
            for value in (0,-1,True,1.0,'3',10000001):
                x=valid();x[key]=value
                with self.assertRaises(r.Rejected):r.validate_native(x,'RAMP')
    def test_no_authored_or_layout_claim(self):
        for key in ('authoredRun','phase2'):
            x=valid();x[key]='PASS'
            with self.assertRaises(r.Rejected):r.validate_native(x,'RAMP')
        for v in (True,0,'false'):
            x=valid();x['layoutQualification']=v
            with self.assertRaises(r.Rejected):r.validate_native(x,'RAMP')
    def test_wrong_mode_or_scope(self):
        for key,value in (('mode','BASE'),('scope','PLAYABLE'),('schema','old')):
            x=valid();x[key]=value
            with self.assertRaises(r.Rejected):r.validate_native(x,'RAMP')
    def test_strict_json(self):
        for raw in ('{"mode":"BASE","mode":"RAMP"}','{"n":NaN}','{"n":Infinity}'):
            with self.assertRaises(r.Rejected):r.parse(raw)
        self.assertEqual(r.parse(json.dumps(valid())),valid())
    def test_process_proof(self):
        p=dict(ExitCode=0,TerminationProven=True,Stdout='',Stderr='',ProcessId=1,ProcessStartTicks=639000000000000000)
        self.assertEqual(r.process(p,True),p)
        for key,val in (('ExitCode',2),('ExitCode',True),('TerminationProven',False),('ProcessId',0),('ProcessStartTicks','639000000000000000')):
            x=copy.deepcopy(p);x[key]=val
            with self.assertRaises(r.Rejected):r.process(x,True)
    def test_numeric_budgets_fail_closed(self):
        for v in (True,float('inf'),float('nan'),-1,121000):
            with self.assertRaises(r.Rejected):r.number(v,120000,'CLEANUP')
        self.assertEqual(r.number(120000,120000,'CLEANUP'),120000)

if __name__=='__main__':unittest.main()
