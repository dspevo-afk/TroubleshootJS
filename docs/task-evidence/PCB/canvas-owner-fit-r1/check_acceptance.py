"""Check recorded final gates; this does not perform browser interactions."""
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent


def read(name):
    return json.loads((HERE / name).read_text(encoding='utf-8-sig'))


def require(condition, message):
    if not condition:
        raise ValueError(message)


def main():
    native = read('final-native-timing.json')
    build = read('final-build-timing.json')
    require(native['status'] == build['status'] == 'PASS' and native['suiteCount'] == 56,
            'Native/build terminal gates missing')
    require('CLEANUP: current JVM contract classes and task-owned scratch removed.' in
            (HERE/'final-native.txt').read_text(encoding='utf-8-sig'), 'Native cleanup missing')
    for name in ('post-native-input-audit.json', 'post-build-input-audit.json',
                 'post-player-input-audit.json'):
        audit = read(name)
        require(audit['status'] == 'PASS' and audit['changedPaths'] == [] and
                audit['actualInputCount'] == audit['frozenInputCount'] == 1447,
                'Changed consumed inputs: '+name)
    require(read('final-geometry-assessment.json')['status'] == 'PASS', 'Geometry gate missing')
    expected = read('preview-expected.json')
    for name in ('desktop-served.json', 'narrow-served.json'):
        served = read(name)
        for suffix, key in (('source-digest','SourceDigest'),('script-digest','ScriptDigest'),
                            ('web-digest','WebDigest'),('execution-digest','Digest'),
                            ('file-count','FileCount')):
            require(str(served['data-tsj-preview-'+suffix]) == str(expected[key]),
                    'Served identity mismatch: '+name+'/'+key)
    require(read('post-player-provenance.json') == expected, 'Post-player tree changed')
    events = {row['label']:row['report'] for row in read('player-reports.json')}
    cleanup = ('ownerRestored','candidateDisposed','temporaryMeterClean','debugRestored',
               'coordinatorReleased','playerSessionReleased','playerBridgeReleased')
    runs = []
    for label, seed, packages in (('desktop-current14-closed','4',55),
                                  ('narrow-saved13-closed','42',54)):
        report = events[label]
        require(report['status'] == 'PASS' and report['seed'] == seed and
                report['packages'] == packages and report['coldProof'] is True and
                report['normalPhysicalAdmission'] is True and report['fullProductionD01'] is True and
                report['ordinaryCustomerRetestPassed'] is True and
                report['forcedAfterPublish'] is False and report['cleanupPending'] is False and
                all(report[key] is True for key in cleanup), 'Player publication/cleanup failure: '+label)
        require(report['maxJobMillis'] == 90000 and report['maxJobSteps'] == 640 and
                report['maxUnitMillis'] == 5000 and report['generationMillis'] <= 90000 and
                report['generationWork'] <= 640 and report['maxManualUnitMillis'] <= 5000,
                'Cold limits changed: '+label)
        restores = report['sessionRestores']
        require(len(restores) == 1 and restores[0]['outcome'] == 'PASS' and
                restores[0]['freshOwner'] is True and restores[0]['priorOwnerRetired'] is True and
                restores[0]['elapsedMillis'] <= 90000 and restores[0]['work'] <= 640 and
                restores[0]['maxJobMillis'] == 90000 and restores[0]['maxJobSteps'] == 640 and
                restores[0]['maxUnitMillis'] == 5000, 'Restore failed or changed limits: '+label)
        runs.append(dict(label=label,coldMillis=report['generationMillis'],
                         coldWork=report['generationWork'],restore=restores[0],
                         wallMillis=report['wallMillis']))
    require(read('desktop-browser-logs.json') == read('narrow-browser-logs.json') == [],
            'Browser warnings/errors require review')
    saved14 = (HERE/'repaired14-55.tsjsave').read_bytes()
    require(hashlib.sha256(saved14).hexdigest() == read('download-owner.json')['sha256'] and
            b'tsj-private-session/14/tsj-generation-dependencies-v16/rb56-placement-14/RB56_CONTROL/4' in saved14,
            'Actual downloaded current14 save changed')
    saved13 = HERE.parent/'pad-cost-vacancy-r1/restored13-requalification-r1/repaired13-54.tsjsave'
    require(hashlib.sha256(saved13.read_bytes()).hexdigest() ==
            '45b4eceb7e0e4e8b202e17474fa95ab3bd9cc8077c9e5f444c970608d6ea443a',
            'Preserved saved13 fixture changed')
    for stem in ('desktop-restored14','narrow-restored13'):
        dom = (HERE/(stem+'-dom.txt')).read_text(encoding='utf-8')
        require('Board Power: OFF' in dom and '1 part' in dom, 'Restored physical state missing')
        require('Customer retest passed' in (HERE/(stem+'-retest-dom.txt')).read_text(encoding='utf-8'),
                'Restored customer PASS missing')
    require(read('resource-cleanup.json')['status'] == 'PASS', 'Owned resources not cleaned')
    require(read('narrow-view-diagnosis/diagnosis.json')['status'] == 'OPEN_NONBLOCKING_FOLLOWUP' and
            (HERE/'narrow-view-diagnosis/sideways-after-fit.jpg').is_file(),
            'Observed narrow toolbar limitation must remain explicit')
    images = sorted(HERE.glob('*.jpg'))
    require(len(images) == 5, 'Expected five inspected unedited captures')
    result = dict(status='PASS',nativeSeconds=native['elapsedSeconds'],
                  buildSeconds=build['elapsedSeconds'],runs=runs,
                  screenshots=[dict(file=p.name,sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in images],
                  limits='Finite informed regression cohort and two reference-host player cases. Narrow toolbar auto-scroll remains an open nonblocking follow-up, not a fixed or passing responsive layout. No shrinkage, all-seed, manufacturing, HARD or modest-host claim. Full native matrix and independent oracles NOT RUN.')
    (HERE/'acceptance.json').write_bytes((json.dumps(result,indent=2)+'\n').encode('utf-8'))
    print('PASS: final recorded gates, exact inputs/served identities, two player flows and cleanup')


if __name__ == '__main__':
    main()
