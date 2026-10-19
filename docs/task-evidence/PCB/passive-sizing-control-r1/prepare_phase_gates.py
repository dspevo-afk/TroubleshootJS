"""Reuse the maintained bounded driver pattern with this control's exact scope."""
from pathlib import Path
HERE=Path(__file__).resolve().parent
OLD=HERE.parent/'passive-pose-choice-cause-r1'
for name in ('run_native.ps1','check_process_cleanup.ps1','sanitize.py','preserve_attempt.py'):
    text=(OLD/name).read_text()
    text=text.replace('passive-pose-choice-cause-r1','passive-sizing-control-r1')
    text=text.replace('tsj-passive-choice-','tsj-passive-sizing-')
    text=text.replace('Read-only candidate1 choice trace; unchanged native per-JVM/search limits',
                      'Native sizing-only control; unchanged per-JVM/search/physical limits')
    text=text.replace('choice phase','sizing phase')
    if name=='preserve_attempt.py':
        text=text.replace("'unit-validation.json','assessment.json','native-evidence-reuse.json','route-reproduction.json'",
                          "'unit-validation.json','assessment.json'")
    assert not (HERE/name).exists()
    (HERE/name).write_text(text)
print('Prepared bounded control phase/cleanup/privacy/failure-preservation gates; no runtime')
