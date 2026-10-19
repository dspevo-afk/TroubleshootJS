"""Reuse the maintained phase shell with exact new copy/scope ownership."""
from pathlib import Path
HERE=Path(__file__).resolve().parent
OLD=HERE.parent/'passive-pose-policy-r1'
for name in ('run_native.ps1','check_process_cleanup.ps1','sanitize.py'):
    text=(OLD/name).read_text()
    text=text.replace('passive-pose-policy-r1','passive-pose-choice-cause-r1')
    text=text.replace('tsj-passive-policy-','tsj-passive-choice-')
    text=text.replace('Explicit native-only passive policy prerequisite; unchanged per-JVM and search limits',
      'Read-only candidate1 choice trace; unchanged native per-JVM/search limits')
    text=text.replace('bounded native policy phase','bounded native choice phase')
    text=text.replace('policy phase driver','choice phase driver')
    assert not (HERE/name).exists()
    (HERE/name).write_text(text)
print('Prepared read-only choice phase/cleanup/privacy shells; no runtime')
