"""Adapt only cleanup/archive ownership and phase artifact names before freeze."""
from pathlib import Path
HERE=Path(__file__).resolve().parent
OLD=HERE.parent/'passive-pose-foundation-r1'
data=(OLD/'retire_copy.py').read_text()
data=data.replace('tsj-passive-pose-','tsj-passive-policy-').replace('Unique tsj-routed-strip OS-temp copy','Unique tsj-passive-policy OS-temp copy')
data=data.replace("assert json.loads((HERE / 'process-cleanup.json').read_text(encoding='utf-8-sig'))['status'] == 'PASS'", "for phase in ('unit','routes'):\n    if (HERE / (phase+'-timing.json')).exists():\n        assert json.loads((HERE / (phase+'-process-cleanup.json')).read_text(encoding='utf-8-sig'))['status'] == 'PASS'")
(HERE/'retire_copy.py').write_text(data)
data=(OLD/'preserve_attempt.py').read_text()
data=data.replace("assert json.loads((HERE / 'timing.json').read_text(encoding='utf-8-sig'))['status'] == 'FAIL'\nassert json.loads((HERE / 'process-cleanup.json').read_text(encoding='utf-8-sig'))['status'] == 'PASS'", "phases=[p for p in ('unit','routes') if (HERE/(p+'-timing.json')).exists()]\nassert phases and any(json.loads((HERE/(p+'-timing.json')).read_text(encoding='utf-8-sig'))['status']=='FAIL' for p in phases)\nfor p in phases:\n    assert json.loads((HERE/(p+'-process-cleanup.json')).read_text(encoding='utf-8-sig'))['status']=='PASS'")
start=data.index("artifacts = ['native-inputs.json'")
end=data.index('names = sorted(',start)
data=data[:start]+'''artifacts=['native-inputs.json','native-input-audit.json']
for phase in phases:
    for suffix in ('.txt','-timing.json','-process-launch.json','-process-cleanup.json','-log-sanitization.json','-receipt.txt'):
        name=phase+suffix
        if (HERE/name).exists():artifacts.append(name)
for name in ('unit-validation.json','assessment.json'):
    if (HERE/name).exists():artifacts.append(name)
'''+data[end:]
(HERE/'preserve_attempt.py').write_text(data)
print('Prepared exact-boundary retirement and failed-phase archive gates')
