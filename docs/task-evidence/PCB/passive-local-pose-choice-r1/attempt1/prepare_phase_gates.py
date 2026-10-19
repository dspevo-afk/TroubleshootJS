"""Adapt maintained phase owners to this namespace; no limit changes."""
from pathlib import Path
HERE=Path(__file__).resolve().parent
OLD=HERE.parent/'passive-source-sizing-trial-r1'
for name in ('run_native.ps1','check_process_cleanup.ps1','sanitize.py','preserve_attempt.py',
             'validate_unit.py','run_assessment.py'):
    source=(OLD/name).read_text()
    source=source.replace('passive-source-sizing-trial-r1','passive-local-pose-choice-r1')
    source=source.replace('tsj-passive-source-size-','tsj-passive-local-pose-')
    source=source.replace('Native source-sized orthogonal trial; unchanged per-JVM/search/physical limits',
                          'Native local two-pose choice; unchanged per-JVM/search/physical limits')
    source=source.replace('source-sizing phase','local-pose phase')
    source=source.replace('PCB_SOURCE_SIZED_POSE','PCB_LOCAL_POSE')
    source=source.replace("'suiteCount': 8","'suiteCount': 9").replace('unit8','unit9')
    source=source.replace('trial18','trial19').replace('old+16+18','old+16+19')
    source=source.replace('Source-sized pose strict assessment','Local pose strict assessment')
    target=HERE/name; assert not target.exists(); target.write_text(source)
print('Prepared maintained native/cleanup/privacy/readers; limits unchanged')
