from pathlib import Path
import ast,hashlib,json
s=Path(__file__).resolve().parent
old=s/'run_sticky_finite_r2_final_gates.py'
text=old.read_text(encoding='utf-8').replace('sticky-finite-r2','pivot-lower-fusion')
text=text.replace('pivot-lower-fusion-index-path.txt','pivot-lower-fusion-final-source-path.txt')
text=text.replace('native-owned-zero-row.ps1','native-pivot-lower-fusion.ps1')
text=text.replace('exact staged source gates','exact proposed final source gates')
ast.parse(text)
dest=s/'run_pivot_lower_fusion_final_gates.py';assert not dest.exists()
dest.write_text(text,encoding='utf-8',newline='\n')
print(json.dumps({'sourceSha256':hashlib.sha256(old.read_bytes()).hexdigest(),'runnerSha256':hashlib.sha256(dest.read_bytes()).hexdigest(),'status':'PREPARED_NOT_EXECUTED'}))
