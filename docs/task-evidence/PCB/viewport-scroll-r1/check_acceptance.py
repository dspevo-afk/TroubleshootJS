"""Check final recorded toolbar gates; does not drive the browser."""
import json,hashlib
from pathlib import Path
HERE=Path(__file__).resolve().parent

def read(name):return json.loads((HERE/name).read_text(encoding='utf-8-sig'))
def require(ok,msg):
    if not ok:raise ValueError(msg)
def main():
    require(read('final-build-timing.json')['status']=='PASS','Build did not pass')
    dom=read('final-dom-results.json')
    require(len(dom['results'])==2 and all(r['status']=='PASS' and r['exitCode']==0 for r in dom['results']),'DOM failures')
    require('130 assertions' in (HERE/'final-u04_ui_contract.txt').read_text(encoding='utf-8-sig'),'U04 result missing')
    require('380 PASS' in (HERE/'final-bench_meter_contract.txt').read_text(encoding='utf-8-sig'),'Meter result missing')
    require(read('post-dom-tooling-audit.json')['status']=='PASS','Dependency drift')
    require(read('reuse-boundary.json')['status']=='PASS_UNCHANGED_JVM_AND_COMPILED_WEB_BOUNDARY','Native reuse not audited')
    failed=read('timing-attempt1/result.json')
    require(failed['status']=='FAIL_STRICT_RESTORE_WALL_GATE' and failed['restoreElapsedMillis']==90048,'Earlier timing failure must remain explicit')
    require(read('timing-attempt1/final-player-reports.json')[-1]['report']['sessionRestores'][0]['elapsedMillis']==90048,'Original timing observation changed')
    expected=read('preview-expected.json')
    for name in ('final-private-served.json','final-public-served.json'):
        served=read(name)
        for suffix,key in (('source-digest','SourceDigest'),('script-digest','ScriptDigest'),('web-digest','WebDigest'),('execution-digest','Digest'),('file-count','FileCount')):
            require(str(served['data-tsj-preview-'+suffix])==str(expected[key]),'Served mismatch '+name+'/'+key)
    require(read('post-player-provenance.json')==expected,'Execution tree changed')
    require(read('post-player-input-audit.json')['status']=='PASS','Consumed inputs changed')
    early=read('focus-adapter-canary.json')
    before=dict(early['before']['meter']);after=dict(early['after']['meter'])
    require(before['data-bench-world-x']==after['data-bench-world-x'] and before['data-bench-world-y']==after['data-bench-world-y'],'Meter Home did not restore world position')
    require(early['arrowMoved']['worldX']!=before['data-bench-world-x'] and '12 V' in early['after']['text'],'Actual meter/probe canary missing')
    ui=read('final-viewport-observations.json')
    for row in ui:
        require(row['canvas']['left']==0 and row['canvas']['right']==row['viewportWidth'],'Shifted canvas: '+row['label'])
        require(all(a['left']==0 and a['top']==0 for a in row['canvasAncestors']),'Scrolled canvas ancestor: '+row['label'])
        require(row['ribbon']['top']==0 and row['ribbon']['clientHeight']==row['ribbon']['scrollHeight'],'Clipped control band: '+row['label'])
    focus=next(r for r in ui if r['label']=='private-narrow-keyboard')['focus']
    require(focus['text']=='Settings' and focus['rect']['left']>=0 and focus['rect']['right']<=319,'Keyboard focus remains clipped')
    require({r['viewportWidth'] for r in ui}=={319,1280},'Required viewport missing')
    events={e['label']:e['report'] for e in read('final-player-reports.json')}
    closed=events['private-closed']
    cleanup=('ownerRestored','candidateDisposed','temporaryMeterClean','debugRestored','coordinatorReleased','playerSessionReleased','playerBridgeReleased')
    require(closed['status']=='PASS' and closed['seed']=='42' and closed['packages']==54 and closed['coldProof'] and closed['normalPhysicalAdmission'] and closed['fullProductionD01'] and closed['ordinaryCustomerRetestPassed'],'Private flow failed')
    require(closed['cleanupPending'] is False and all(closed[k] is True for k in cleanup),'Private cleanup failed')
    require(closed['maxJobMillis']==90000 and closed['maxJobSteps']==640 and closed['maxUnitMillis']==5000,'Changed cold limits')
    require(closed['generationMillis']<=90000 and closed['generationWork']<=640 and closed['maxManualUnitMillis']<=5000,'Cold execution exceeded limits')
    restore=closed['sessionRestores']
    require(len(restore)==1 and restore[0]['outcome']=='PASS' and restore[0]['freshOwner'] and restore[0]['priorOwnerRetired'],'Restore owner failure')
    require(restore[0]['elapsedMillis']<=90000 and restore[0]['work']<=640 and restore[0]['maxJobMillis']==90000 and restore[0]['maxJobSteps']==640 and restore[0]['maxUnitMillis']==5000,'Restore execution exceeded or changed limits')
    require('Customer retest did not pass' in (HERE/'final-private-unrepaired-dom.txt').read_text(encoding='utf-8'),'Unrepaired flow not checked')
    require('Board Power: OFF' in (HERE/'final-private-restored-dom.txt').read_text(encoding='utf-8'),'Restored power lost')
    require('Customer retest passed' in (HERE/'final-private-retest-dom.txt').read_text(encoding='utf-8'),'Repaired retest missing')
    require(read('final-private-browser-logs.json')==read('final-public-browser-logs.json')==[],'Browser logs need review')
    require(read('resource-cleanup.json')['status']=='PASS','Resources still active')
    result={'status':'PASS','scope':'Local toolbar/focus owner; finite public LED3 and private54 root42,319/1280 widths; unchanged PCB/electrical proof reused at audited JVM boundary only','limits':'No packing change. Full native matrix/independent electrical oracles NOT RUN. One unchanged-fixture retry after a preserved90.048s strict restore timing failure; no all-run or modest-host timing claim. Reference-host timings only. Preserved failed canaries and recorder error remain failures.'}
    (HERE/'acceptance.json').write_bytes((json.dumps(result,indent=2)+'\n').encode('utf-8'))
    print('PASS recorded final gates, inputs, viewport observations, private restore/retest and cleanup')
if __name__=='__main__':main()
