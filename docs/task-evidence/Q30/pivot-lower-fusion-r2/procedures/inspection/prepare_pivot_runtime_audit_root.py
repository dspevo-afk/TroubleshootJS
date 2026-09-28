from pathlib import Path
import ast,hashlib,json
s=Path(__file__).resolve().parent
old=s/'audit_sticky_finite_r2_root_runtime.py'
text=old.read_text(encoding='utf-8')
text=text.replace('sticky-finite-r2-source-pair-path.txt','pivot-lower-fusion-r2-source-pair-path.txt')
text=text.replace('sticky-finite-r2-candidate-canaries','pivot-lower-fusion-r2-canaries')
text=text.replace('sticky-finite-r2-root-gwt','pivot-lower-fusion-root-gwt')
text=text.replace('sticky-finite-r2-root-runtime-equality','pivot-lower-fusion-root-runtime-equality')
text+='''
final=s/'pivot-lower-fusion-final-source'
audit=json.loads((final/'final-source-audit.json').read_bytes())
for r in audit['inputs']:
    b=(final/r['path']).read_bytes()
    assert len(b)==r['bytes'] and hashlib.sha256(b).hexdigest()==r['sha256'],r['path']
print('PASS: all 1339 proposed final source inputs unchanged')
'''
ast.parse(text)
dest=s/'audit_pivot_lower_fusion_root_runtime.py';assert not dest.exists()
dest.write_text(text,encoding='utf-8',newline='\n')
print(json.dumps({'sourceSha256':hashlib.sha256(old.read_bytes()).hexdigest(),'runnerSha256':hashlib.sha256(dest.read_bytes()).hexdigest(),'status':'PREPARED_NOT_EXECUTED'}))
