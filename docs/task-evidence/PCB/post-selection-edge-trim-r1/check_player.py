"""Validate recorded real-input production flows; does not drive or simulate them."""
import hashlib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent
def read(n):return json.loads((HERE/n).read_text(encoding='utf-8-sig'))
def need(ok,message):
    if not ok:raise ValueError(message)
def main():
    need(read('final-geometry-assessment.json')['status']=='PASS','Final geometry gate')
    native_text=(HERE/'final-native.txt').read_text(encoding='utf-8-sig')
    need('PASS: focused current contracts; 66 Java suites.' in native_text and
        'CLEANUP: current JVM contract classes and task-owned scratch removed.' in native_text,
        'Maintained selected66 terminal/cleanup markers')
    for phase in ('native','build'):
        t=read('final-'+phase+'-timing.json')
        need(t['status']=='PASS' and t['exitCode']==0,'Terminal '+phase+' gate')
    expected=read('preview-expected.json')
    for name in ('desktop-served.json','narrow-served.json'):
        served=read(name)
        for suffix,key in (('source-digest','SourceDigest'),('script-digest','ScriptDigest'),('web-digest','WebDigest'),('execution-digest','Digest'),('file-count','FileCount')):
            need(str(served['data-tsj-preview-'+suffix])==str(expected[key]),'Served identity '+name+'/'+key)
    need(read('post-player-provenance.json')==expected,'Post-player execution bytes')
    for name in ('post-native-input-audit.json','post-build-input-audit.json','post-player-input-audit.json'):
        audit=read(name)
        need(audit['status']=='PASS' and not audit['changedPaths'] and audit['actualInputCount']==audit['frozenInputCount']==1466,'Frozen final inputs '+name)
    events={r['label']:r['report'] for r in read('player-reports.json')}
    cleanup=('ownerRestored','candidateDisposed','temporaryMeterClean','debugRestored','coordinatorReleased','playerSessionReleased','playerBridgeReleased')
    runs=[]
    for label in ('desktop-current15-closed','narrow-saved14-closed'):
        r=events[label]
        need(r['status']=='PASS' and r['seed']=='4' and r['packages']==55,'Player admission '+label)
        need(all(r[k] is True for k in ('coldProof','normalPhysicalAdmission','fullProductionD01','ordinaryCustomerRetestPassed','normalLabels')+cleanup),'Player truth/cleanup '+label)
        need(r['forcedAfterPublish'] is False and r['cleanupPending'] is False,'Player forced/unfinished '+label)
        need(r['maxJobMillis']==90000 and r['maxJobSteps']==640 and r['maxUnitMillis']==5000 and r['generationMillis']<=90000 and r['generationWork']<=640 and r['maxManualUnitMillis']<=5000,'Cold bounds '+label)
        restores=r['sessionRestores'];need(len(restores)==1,'Exactly one restore '+label)
        restore=restores[0]
        need(restore['outcome']=='PASS' and restore['freshOwner'] is True and restore['priorOwnerRetired'] is True and restore['elapsedMillis']<=90000 and restore['work']<=640 and restore['maxJobMillis']==90000 and restore['maxJobSteps']==640 and restore['maxUnitMillis']==5000,'Restore lifecycle/bounds '+label)
        runs.append({'label':label,'coldMillis':r['generationMillis'],'coldWork':r['generationWork'],'restore':restore,'wallMillis':r['wallMillis']})
    for stem in ('desktop-restored15','narrow-restored14'):
        dom=(HERE/(stem+'-dom.txt')).read_text(encoding='utf-8')
        need('Board Power: OFF' in dom and '1 part' in dom,'Restored physical state '+stem)
        need('Customer retest passed' in (HERE/(stem+'-retest-dom.txt')).read_text(encoding='utf-8'),'Restored customer behavior '+stem)
    saved=(HERE/'repaired15-55.tsjsave').read_bytes()
    need(hashlib.sha256(saved).hexdigest()==read('download-owner.json')['sha256'] and b'tsj-private-session/15/tsj-generation-dependencies-v16/rb56-placement-15/RB56_CONTROL/4' in saved,'Actual current15 downloaded file')
    old=(HERE.parent/'canvas-owner-fit-r1/repaired14-55.tsjsave').read_bytes()
    need(hashlib.sha256(old).hexdigest()=='453928609c7a09c533da3e7df64bd08190ac384b5982cdd712fde7e2a1bb2c71','Unchanged actual saved14 input')
    need(read('desktop-browser-logs.json')==read('narrow-browser-logs.json')==[],'Review any browser errors/warnings')
    images=sorted(HERE.glob('*.jpg'));need(2<=len(images)<=5,'Two to five inspected unedited screenshots')
    need(read('resource-cleanup.json')['status']=='PASS','Owned resource cleanup')
    result={'status':'PASS','runs':runs,'screenshots':[{'file':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in images],
        'limits':'Two reference-host real-input private55-package flows, new15/save15 and preserved actual14 file. Strict90s/640/5s and cleanup7 unchanged. Full native matrix, independent electrical oracles, unseen/all-seed/manufacturing/modest-host claims NOT RUN.'}
    (HERE/'player-acceptance.json').write_text(json.dumps(result,indent=2)+'\n');print('PASS: recorded actual player flows and exact served/final inputs')
if __name__=='__main__':main()
