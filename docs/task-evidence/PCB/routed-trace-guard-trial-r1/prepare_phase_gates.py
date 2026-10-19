"""Namespace maintained bounded native transport, cleanup and privacy owners."""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
OLD = HERE.parent/'compactor-cut-cause-r1'
for name in ('run_native.ps1','check_process_cleanup.ps1','sanitize.py',
             'suite_transport.ps1','check_suite_transport.ps1','retire_copy.py'):
    text = (OLD/name).read_text()
    text = text.replace('compactor-cut-cause-r1','routed-trace-guard-trial-r1')
    text = text.replace('tsj-compactor-cut-cause-','tsj-routed-trace-guard-')
    text = text.replace('Get-CompactorSuiteNames','Get-TraceGuardSuiteNames')
    text = text.replace('Native compactor cut observation; unchanged per-JVM/search/physical limits',
        'Native trace guard trial; unchanged per-JVM/search/physical limits')
    text = text.replace('compactor-cut phase','trace-guard phase')
    if name == 'check_suite_transport.ps1':
        text = text.replace("$unit.Count -ne 1 -or $unit[0] -cne 'PcbCompactorCutObserverContractTest'",
            "$unit.Count -ne 3 -or $unit[0] -cne 'PcbRoutedTraceGuardContractTest' -or $unit[1] -cne 'PcbTraceWidthGeometryContractTest' -or $unit[2] -cne 'RelBSessionIdentityContractTest'")
        text = text.replace('Actual one-row manifest','Actual three-row manifest')
        text = text.replace('Actual one-suite manifest','Actual three-suite manifest')
        text = text.replace('PcbCompactorCutCauseContractTest','PcbRoutedTraceGuardTrialContractTest')
        text = text.replace('one-row and seven-row','three-row and seven-row')
    if name == 'retire_copy.py':
        # The trial and prior diagnostic each add eight bounded contract files.
        assert "'fromCount':1475" in text
    target = HERE/name
    assert not target.exists()
    target.write_text(text)
print('PASS native/transport/cleanup/privacy owners namespaced; unchanged numeric limits')
