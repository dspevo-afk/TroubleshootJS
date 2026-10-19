"""Apply the unchanged strict geometry oracle to promoted final-source execution."""
import hashlib,json
import assess_geometry as gate
HERE=gate.HERE
try:
    plan=json.loads((HERE/'final-suite-plan.json').read_text(encoding='utf-8-sig'))
    timing=json.loads((HERE/'final-native-timing.json').read_text(encoding='utf-8-sig'))
    audit=json.loads((HERE/'post-native-input-audit.json').read_text(encoding='utf-8-sig'))
    if len(plan['names'])!=66 or len(set(plan['names']))!=66:raise ValueError('Unique selected66 plan required')
    if timing['status']!='PASS' or timing['exitCode']!=0 or audit['status']!='PASS':raise ValueError('Final native/input gates required')
    text=(HERE/'final-native.txt').read_text(encoding='utf-8-sig')
    if 'CLEANUP: current JVM contract classes and task-owned scratch removed.' not in text or 'CURRENT_CONTRACT_FAILURE' in text:raise ValueError('Native cleanup/failure proof')
    raw=(gate.OLD/'published14-baselines.json').read_bytes()
    if hashlib.sha256(raw).hexdigest()!=gate.PIN:raise ValueError('Independent baseline changed')
    saved,current=gate.collect(text)
    result=gate.assess_rows({r['seed']:r for r in json.loads(raw)['cases']},saved,current)
    result['phase']='Promoted final-source selected66 native; same literal14/15 pair fixtures and strict oracle'
except Exception as failure:
    result={'status':'FAIL','failure':str(failure)}
(HERE/'final-geometry-assessment.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result));raise SystemExit(0 if result['status']=='PASS' else 1)
