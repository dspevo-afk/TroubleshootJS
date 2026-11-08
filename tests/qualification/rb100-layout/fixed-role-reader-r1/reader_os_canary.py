"""Root-selected OS canary: synthetic byte reader only, never normal input admission."""
import hashlib
import json
from pathlib import Path
import sys
import types
assert sys.flags.isolated and sys.dont_write_bytecode
assert len(sys.argv) in (4,5)
source=Path(sys.argv[1]); fixture=Path(sys.argv[2]); case=sys.argv[3]
assert fixture.name=='synthetic-io-r1'
m=types.ModuleType('_current_fixed_role_reader')
m.__file__=str(source)
sys.modules[m.__name__]=m
exec(compile(source.read_bytes(),str(source),'exec'),m.__dict__)
roots={'N':str(fixture/'N'),'D':str(fixture/'D')}
raw=b'SYNTHETIC_FIXED_ROLE_IO_R1\n'
role='N_PRIOR_OPERATION1'
rows=[dict(role=role,root='N',path='fixture.bin',maxBytes=1024,expectedBytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())]
if case=='sha':rows[0]['sha256']='0'*64
elif case=='length':rows[0]['expectedBytes']=len(raw)+1
elif case=='size':rows[0]['maxBytes']=len(raw)-1;rows[0]['expectedBytes']=None
elif case=='bool-limit':rows[0]['maxBytes']=True;rows[0]['expectedBytes']=None
elif case=='bool-length':rows[0]['expectedBytes']=True
elif case=='zero-disallowed':rows[0]['path']='empty.bin';rows[0]['expectedBytes']=0;rows[0]['sha256']=hashlib.sha256(b'').hexdigest()
elif case=='role':rows[0]['role']='UNKNOWN_ROLE_DO_NOT_OPEN'
elif case=='extra-root':roots['EXTRA']=roots['N']
elif case=='noncanonical':roots['N']+=str(Path('/'))+'.'
elif case=='escape':rows[0]['path']='../fixture.bin'
elif case=='junction':rows[0]['path']='alias/fixture.bin'
elif case=='directory':rows[0]['path']='directory.bin'
elif case=='contract':pass
elif case=='contract-positive':pass
elif case!='positive':raise AssertionError('UNKNOWN_SYNTHETIC_CASE')
try:
    if case=='contract':
        m._load_pinned_allowlist(str(fixture/'fake-contract.json'))
        raise AssertionError('NORMAL_CONTRACT_MUTATION_ACCEPTED')
    if case=='contract-positive':
        assert len(sys.argv)==5
        specs,evidence=m._load_pinned_allowlist(sys.argv[4])
        assert len(specs)==20 and evidence['allowlistSha256']=='c487eae6ed0e070aaad75dccd14d23313ed9da2c6bda7f93bfeb886109f20f91'
        print(json.dumps(dict(status='PASS_ALLOWLIST_METADATA_ONLY',evidence=evidence),sort_keys=True,separators=(',',':')))
        raise SystemExit(0)
    values,evidence=m._read_synthetic_roles(roots=roots,rows=rows,synthetic_mode='SYNTHETIC')
except m.ReaderError as exc:
    print('READER_REJECT '+exc.code,file=sys.stderr)
    raise SystemExit(2)
assert case=='positive' and values=={role:raw}
print(json.dumps(dict(status='PASS_SYNTHETIC_BYTE_READER_ONLY',roleCount=len(values),bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest(),evidence=evidence),sort_keys=True,separators=(',',':')))
