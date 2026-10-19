"""Adapt exact previous phase helpers to the new namespace and manifests."""
from pathlib import Path
HERE=Path(__file__).resolve().parent
OLD=HERE.parent/'passive-sizing-control-r1'
for name in ('run_native.ps1','check_process_cleanup.ps1','sanitize.py','preserve_attempt.py'):
    source=(OLD/name).read_text()
    source=source.replace('passive-sizing-control-r1','passive-source-sizing-trial-r1')
    source=source.replace('tsj-passive-sizing-','tsj-passive-source-size-')
    source=source.replace('Native sizing-only control; unchanged per-JVM/search/physical limits','Native source-sized orthogonal trial; unchanged per-JVM/search/physical limits')
    source=source.replace('route-suites.json','routes-suites.json')
    source=source.replace('sizing phase','source-sizing phase')
    target=HERE/name;assert not target.exists();target.write_text(source)
print('Prepared maintained native/cleanup/privacy/failure helpers for source-sizing namespace')
