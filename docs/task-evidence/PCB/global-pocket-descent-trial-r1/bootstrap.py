"""Adapt proven21 corpus/host scaffolding for22 and an independent fourth unit."""
import hashlib
import json
import re
from pathlib import Path
HERE=Path(__file__).resolve().parent
OLD=HERE.parent/'local-pocket-descent-trial-r1'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(OLD/'native-inputs.json')=='00dea91518cd61c95378edcf92f1aa5f28b517e887f78d9dd82f190ef69699c5'
seal=json.loads((OLD/'native-inputs.json').read_text())
names=('prepare_cases.py','run_native.ps1','check_process_cleanup.ps1','sanitize.py',
 'suite_transport.ps1','check_suite_transport.ps1','retire_copy.py')
for name in names:
 assert sha(OLD/name)==seal['gateOwners'][name],name
 text=(OLD/name).read_text()
 for before,after in (
  ('local-pocket-descent-trial-r1','global-pocket-descent-trial-r1'),
  ('tsj-local-pocket-descent-','tsj-global-pocket-descent-'),
  ('PcbLocalPocketDescent','PcbGlobalPocketDescent'),
  ('PCB local pocket descent','PCB global pocket descent'),
  ('PCB_POCKET_DESCENT_CASE','PCB_GLOBAL_POCKET_CASE'),
  ('observePocketDescent','observeGlobalPocketDescent'),
  ('Get-LocalPocketSuiteNames','Get-GlobalPocketSuiteNames'),
  ('Native local pocket descent trial','Native global pocket descent trial'),
  ('local-pocket','global-pocket')):
  text=text.replace(before,after)
 if name=='prepare_cases.py':
  text=text.replace('91ffe9a3c0e444c28b1ad0ae51005071e7fe0c8a','a3a0b851c1e5b80212b1d604a3962a8e77437893')
  text=re.sub(r'\b21\b','22',text).replace('pocket21','global22').replace('explicit21','explicit22')
  text=text.replace('Native20','Native22').replace('native20','native22').replace('15/21','15/22')
  anchor="        ['PcbVacancyTargetsContractTest', 'PCB vacancy targets contracts '],"
  assert anchor in text
  text=text.replace(anchor,"        ['PcbMediumPlacementCostContractTest', 'PCB medium placement cost contracts '],\n"+anchor)
  assert '[unit[0]]+suites' in text
  text=text.replace('[unit[0]]+suites','unit[:2]+suites')
 if name=='check_suite_transport.ps1':
  before="if($unit.Count -ne 3 -or $unit[0] -cne 'PcbGlobalPocketDescentContractTest' -or $unit[1] -cne 'PcbVacancyTargetsContractTest' -or $unit[2] -cne 'RelBSessionIdentityContractTest')"
  after="if($unit.Count -ne 4 -or $unit[0] -cne 'PcbGlobalPocketDescentContractTest' -or $unit[1] -cne 'PcbMediumPlacementCostContractTest' -or $unit[2] -cne 'PcbVacancyTargetsContractTest' -or $unit[3] -cne 'RelBSessionIdentityContractTest')"
  assert before in text
  text=text.replace(before,after).replace('three-suite','four-suite').replace('three-row','four-row')
 if name=='retire_copy.py':
  assert "'fromCount':1475" in text
  text=text.replace("'fromCount':1475","'fromCount':1476")
 target=HERE/name
 assert not target.exists(),name
 target.write_text(text)
print('PASS adapted21 scaffolding only; four unit/seven route manifests; no rejected policy source copied')
