import copy
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import read_first_request as r

def entry():
    return dict(operation=1, armId='OBSERVED_DEFAULT_NEAREST',
                receipt=dict(path='op-1.jsonl', bytes=1, sha256='a'*64),
                elapsedToCloseNanos=100, maxFullGroupNanos=50,
                cleanup=dict(status='PASS', elapsedNanos=10,
                             ownersReleased=True, contextRestored=True))

def trailer(profile='BASE', kind='AUTHORED'):
    return dict(schema=r.SCHEMA, profile=profile, kind=kind,
                runId='11111111-2222-3333-4444-555555555555',
                sourceManifestSha256='b'*64, wireSha256=r.WIRE,
                descriptor=dict(path='one-arm-index.json', bytes=10, sha256='c'*64),
                wrapper=dict(preparationNanos=10, postNanos=10, elapsedNanos=100,
                             workNanos=100, groups=2),
                scope='ONE_CANONICAL_REQUEST_ONLY', layoutQualification=False)

class StrictJson(unittest.TestCase):
    def test_duplicate_nonfinite_bom_surrogate_and_negative_zero(self):
        for raw in (b'{"a":1,"a":2}', b'{"a":{"x":1,"x":2}}', b'{"x":NaN}',
                    b'{"x":Infinity}', b'\xef\xbb\xbf{}', b'{"x":-0}',
                    b'{"x":"\\ud800"}', b'\xff'):
            with self.subTest(raw=raw), self.assertRaises(r.Rejected):
                r.parse(raw)

    def test_finite_host_float_is_allowed(self):
        self.assertEqual(r.parse(b'{"ms":1.25}'), {'ms':1.25})

class ProcessAndFiles(unittest.TestCase):
    def test_process_identity_types_and_exit(self):
        p=dict(ExitCode=0, TerminationProven=True, Stdout='', Stderr='',
               ProcessId=17, ProcessStartTicks=639000000000000000,
               Arguments=['-ea'], FilePath='java.exe')
        self.assertIs(r.process(p, True), p)
        for key,value in (('ExitCode', True), ('TerminationProven', 1), ('ProcessId', 0),
                          ('ProcessStartTicks', str(p['ProcessStartTicks'])), ('Arguments','-ea')):
            bad=copy.deepcopy(p); bad[key]=value
            with self.subTest(key=key), self.assertRaises(r.Rejected): r.process(bad, True)
        bad=copy.deepcopy(p); bad['ExitCode']=2
        with self.assertRaises(r.Rejected): r.process(bad, True)
        bad=copy.deepcopy(p); bad['ExitCode']=2
        self.assertIs(r.process(bad, False), bad)

    def test_stable_read_size(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); target=root/'target'; target.write_bytes(b'ok')
            self.assertEqual(r.stable_read(target, 2), b'ok')
            with self.assertRaises(r.Rejected): r.stable_read(target, 1)

    @unittest.skip('NOT_RUN: optional link creation excluded; prior junction approval rejection and symlink unavailable')
    def test_optional_reparse_fixture_not_run(self):
        self.fail('The optional reparse fixture is intentionally not executed.')

class DescriptorAndBudgets(unittest.TestCase):
    def test_exact_entry_and_cleanup(self):
        e=entry(); self.assertIs(r.validate_entry(e), e)
        for key in ('operation','armId','elapsedToCloseNanos','maxFullGroupNanos','cleanup'):
            bad=copy.deepcopy(e); bad[key]=None
            with self.subTest(key=key), self.assertRaises(r.Rejected): r.validate_entry(bad)
        for key,value in (('status','FAIL'),('ownersReleased',False),('contextRestored',False),
                          ('elapsedNanos',True),('elapsedNanos',120000000001)):
            bad=copy.deepcopy(e); bad['cleanup'][key]=value
            with self.subTest(cleanup=key,value=value), self.assertRaises(r.Rejected): r.validate_entry(bad)

    def test_single_operation_index_exact_context(self):
        run='11111111-2222-3333-4444-555555555555'; source='b'*64
        idx=dict(schema=r.INDEX, profile='BASE', kind='AUTHORED', runId=run,
                 sourceManifestSha256=source, wireSha256=r.WIRE,
                 scope='ONE_CANONICAL_REQUEST_ONLY', layoutQualification=False,
                 operations=[entry()])
        raw=json.dumps(idx,separators=(',',':')).encode()
        self.assertEqual(r.validate_index(idx,raw,'BASE','AUTHORED',run,source)['operation'],1)
        for key,value in (('profile','RAMP'),('wireSha256','d'*64),
                          ('layoutQualification',True),('scope','WHOLE_STUDY')):
            bad=copy.deepcopy(idx); bad[key]=value
            with self.subTest(key=key), self.assertRaises(r.Rejected):
                r.validate_index(bad,raw,'BASE','AUTHORED',run,source)
        bad=copy.deepcopy(idx); bad['operations'].append(entry())
        with self.assertRaises(r.Rejected): r.validate_index(bad,raw,'BASE','AUTHORED',run,source)

    def test_trailer_is_one_exact_bounded_line(self):
        body=trailer(); stream='PREFIX_VIA_FIRST_REQUEST_COMPLETE '+json.dumps(body,separators=(',',':'))+'\n'
        parsed,total=r.validate_trailer(stream,'',len(stream.encode()),'BASE','AUTHORED',
                                         body['runId'],body['sourceManifestSha256'])
        self.assertEqual(parsed,body); self.assertEqual(total,len(stream.encode()))
        for badout,baderr,count in ((stream+'extra\n','',None),(stream,'stderr',None),
                                    (stream.replace('PREFIX_VIA_FIRST_REQUEST_COMPLETE','WRONG'),'',None)):
            with self.subTest(badout=badout[:20]), self.assertRaises(r.Rejected):
                r.validate_trailer(badout,baderr,len(badout.encode())+len(baderr.encode()) if count is None else count,
                                   'BASE','AUTHORED',body['runId'],body['sourceManifestSha256'])
        changed=copy.deepcopy(body); changed['extra']=1
        bad='PREFIX_VIA_FIRST_REQUEST_COMPLETE '+json.dumps(changed)+'\n'
        with self.assertRaises(r.Rejected):
            r.validate_trailer(bad,'',len(bad.encode()),'BASE','AUTHORED',body['runId'],body['sourceManifestSha256'])
        with self.assertRaises(r.Rejected): r.validate_trailer(stream,'',True,'BASE','AUTHORED',body['runId'],body['sourceManifestSha256'])

    def test_wrapper_cleanup_and_group_budget(self):
        body=trailer(); e=entry()
        body['wrapper']['elapsedNanos']=110; body['wrapper']['workNanos']=100
        r.validate_wrapper_cleanup(body,e)
        body['wrapper']['elapsedNanos']=111
        with self.assertRaises(r.Rejected): r.validate_wrapper_cleanup(body,e)
        self.assertEqual(r.combined_group_count(638),640)
        with self.assertRaises(r.Rejected): r.combined_group_count(639)

class BaseOracle(unittest.TestCase):
    class Reader:
        @staticmethod
        def strict_json(raw): return r.parse(raw)

    def receipt(self, run, source, elapsed, other=7):
        rows=[dict(record='HEADER',runId=run,sourceManifestSha256=source,other=other),
              dict(record='GROUP',elapsedBeforeOwnLineNanos=elapsed,work=3),
              dict(record='RESULT',route='TYPED_REQUEST_REJECTION')]
        return b''.join(json.dumps(x,separators=(',',':')).encode()+b'\n' for x in rows)

    def test_only_three_designated_fields_are_normalized(self):
        a=self.receipt('old','a'*64,12); b=self.receipt('new','b'*64,99)
        r.compare_base(a,b,self.Reader)
        with self.assertRaises(r.Rejected):
            r.compare_base(a,self.receipt('new','b'*64,99,other=8),self.Reader)
        self.assertFalse(r.same_json({'x':True},{'x':1}))

if __name__=='__main__': unittest.main()
