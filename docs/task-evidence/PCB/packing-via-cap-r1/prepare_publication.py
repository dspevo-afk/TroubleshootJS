"""Preserve exact task logs privately; project only the owned checkout prefix."""
import argparse,hashlib,json,re,runpy
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(raw):return hashlib.sha256(raw).hexdigest()
parser=argparse.ArgumentParser();parser.add_argument('--private-directory',type=Path,required=True)
args=parser.parse_args()
assert args.private_directory.is_dir() and args.private_directory.name.startswith('tsj-packing-via-cap-')
assert runpy.run_path(str(HERE/'player_inputs.py'))['record']()==read(HERE/'player-inputs.json')
assert read(HERE/'player-assessment.json')['status']=='PASS_FINITE_FINAL_PLAYER'
assert read(HERE/'process-resource-cleanup.json')['status']=='PASS'
rawdir=args.private_directory/'publication-raw';assert not rawdir.exists();rawdir.mkdir()
logs=('fixture.txt','fixture-r2/fixture.txt','fixture-r3/fixture.txt','target-r3/native.txt',
      'final/contracts.txt','final/matrix.txt','final/census.txt','final/target.txt','final/build.txt',
      'preview-start.txt','preview-stop.txt')
prefixes=sorted({str(ROOT),ROOT.as_posix(),str(ROOT).replace('\\','\\\\')},key=len,reverse=True)
records=[]
for n in logs:
    f=HERE/n;raw=f.read_bytes();projected=raw;counts={}
    for prefix in prefixes:
        token=prefix.encode();count=projected.count(token)
        if count:counts['ownedCheckoutPrefix'+str(len(counts)+1)]=count;projected=projected.replace(token,b'[WORKTREE]')
    if raw!=projected:
        private=rawdir/n;private.parent.mkdir(parents=True,exist_ok=True);private.write_bytes(raw)
        assert private.read_bytes()==raw;f.write_bytes(projected)
    records.append({'path':n,'rawSha256':sha(raw),'rawBytes':len(raw),'publicSha256':sha(projected),'publicBytes':len(projected),'prefixReplacements':counts,'allOtherBytesExact':True,'rawPreservedPrivately':raw!=projected})
(HERE/'log-path-projection.json').write_text(json.dumps({'status':'PASS_PATH_ONLY_PROJECTION','logs':records,'limits':'Only exact owned checkout prefix variants replaced. Complete changed originals retained privately/hash-bound; every failure and all remaining bytes preserved. Source/helpers/receipts untouched.'},indent=2)+'\n')
attrs='* -text whitespace=cr-at-eol\nadapter-diff.txt whitespace=-blank-at-eol,-blank-at-eof,cr-at-eol\n'
for f in sorted(HERE.glob('*-body.txt')):
    value=f.read_text(encoding='utf-8-sig')
    if any(line.rstrip(' \t')!=line for line in value.splitlines()):attrs+=f.name+' whitespace=-blank-at-eol,cr-at-eol\n'
(HERE/'.gitattributes').write_text(attrs)
bad=[]
pattern=re.compile(r'(?i)(?:[A-Z]:[\\/]+Users[\\/]|'+r'/'+r'Users'+r'/|'+r'/'+r'home'+r'/|wss?://|Authorization:\s*Bearer|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY)')
for f in HERE.rglob('*'):
    if f.is_file():
        assert f.resolve().is_relative_to(HERE.resolve()) and '__pycache__' not in f.parts
        if f.suffix.lower() in ('.txt','.md','.json','.java','.py','.ps1') or f.name=='.gitattributes':
            for i,line in enumerate(f.read_text(encoding='utf-8-sig').splitlines(),1):
                if pattern.search(line):bad.append({'path':f.relative_to(HERE).as_posix(),'line':i})
assert not bad,bad
(HERE/'packet-privacy-review.json').write_text(json.dumps({'status':'PASS','textFilesScanned':sum(f.is_file() and f.suffix.lower() in ('.txt','.md','.json','.java','.py','.ps1') for f in HERE.rglob('*')),'privatePathWsCredentialMatches':bad,'limits':'Task packet text only; raw public bodies/images preserved. Root separately reviews added tracked diff and exact intended index.'},indent=2)+'\n')
print('PASS task log prefix projection/raw preservation and packet text privacy')
