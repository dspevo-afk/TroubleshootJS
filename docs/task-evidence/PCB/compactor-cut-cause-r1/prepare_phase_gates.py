"""Namespace the maintained bounded native, birth-check and privacy owners."""
import json
from pathlib import Path
HERE=Path(__file__).resolve().parent
OLD=HERE.parent/'passive-local-pose-choice-r1'
protocol=json.loads((HERE/'protocol.json').read_text())
for phase in ('unit','routes'):
    (HERE/(phase+'-suites.json')).write_text(json.dumps(protocol[phase+'Suites'],indent=2)+'\n')
for name in ('run_native.ps1','check_process_cleanup.ps1','sanitize.py'):
    text=(OLD/name).read_text()
    text=text.replace('passive-local-pose-choice-r1','compactor-cut-cause-r1')
    text=text.replace('tsj-passive-local-pose-','tsj-compactor-cut-cause-')
    text=text.replace('Native local two-pose choice; unchanged per-JVM/search/physical limits',
        'Native compactor cut observation; unchanged per-JVM/search/physical limits')
    text=text.replace('local-pose phase','compactor-cut phase')
    text=text.replace('bounded native local-pose phase','bounded native compactor-cut phase')
    text=text.replace('native local-pose phase','native compactor-cut phase')
    if name=='run_native.ps1':
        old="$suiteRows=Get-Content -LiteralPath (Join-Path $PSScriptRoot $suiteFile) -Raw | ConvertFrom-Json\n$suites=@($suiteRows | ForEach-Object { [string]$_[0] })"
        new=". (Join-Path $PSScriptRoot 'suite_transport.ps1')\n$suites=@(Get-CompactorSuiteNames -JsonText (Get-Content -LiteralPath (Join-Path $PSScriptRoot $suiteFile) -Raw))"
        assert text.count(old)==1
        text=text.replace(old,new)
    target=HERE/name;assert not target.exists();target.write_text(text)
print('PASS maintained native/cleanup/privacy owners namespaced; timing schema and numeric limits unchanged')
