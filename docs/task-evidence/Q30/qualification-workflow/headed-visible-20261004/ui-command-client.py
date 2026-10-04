import json
import sys
import time
from pathlib import Path

workspace=Path(__file__).resolve().parent
root=Path((workspace/'headed-ui-root.txt').read_text(encoding='utf-8-sig').strip())
commands=json.loads(sys.stdin.read())
if isinstance(commands,dict): commands=[commands]
if not (root/'ready.json').exists():
    print(json.dumps({'status':'HOST_NOT_READY','root':str(root),'progress':json.loads((root/'result-progress.json').read_text()) if (root/'result-progress.json').exists() else None}));sys.exit(2)
if (root/'result.json').exists(): raise RuntimeError('UI host has closed')
for command in commands:
    number=len(list((root/'commands').glob('*.json')))+1
    if number>1 and not (root/'responses'/f'{number-1:04d}.json').exists():
        print(json.dumps({'status':'PREVIOUS_ACTION_RUNNING','sequence':number-1}));break
    path=root/'commands'/f'{number:04d}.json'
    temp=root/'commands'/f'{number:04d}.tmp'
    temp.write_text(json.dumps(command)+'\n',encoding='utf-8');temp.rename(path)
    response=root/'responses'/f'{number:04d}.json'
    until=time.monotonic()+15
    while time.monotonic()<until and not response.exists(): time.sleep(.1)
    if response.exists():
        value=json.loads(response.read_text(encoding='utf-8-sig'));print(json.dumps(value),flush=True)
        if value['status']!='PASS': break
    else:
        print(json.dumps({'status':'ACTION_RUNNING','sequence':number,'response':str(response)}));break
