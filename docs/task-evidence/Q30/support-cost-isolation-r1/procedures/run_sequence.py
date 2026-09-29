from pathlib import Path
import datetime, json, subprocess, sys

rec=Path(__file__).resolve().parent
plan=json.loads((rec/'plan.json').read_text())['sequence']
first,last=map(int,sys.argv[1:3])
for row in plan[first-1:last]:
    label=row[0]
    print('START '+label+' '+datetime.datetime.now(datetime.timezone.utc).isoformat(),flush=True)
    with (rec/(label+'-console.json')).open('w',encoding='utf-8') as output:
        result=subprocess.run([sys.executable,'-B',str(rec/'measure.py'),*row],stdout=output,stderr=subprocess.STDOUT)
    if result.returncode:
        print('STOP '+label+' measurement exit '+str(result.returncode),flush=True)
        sys.exit(result.returncode)
    checked=subprocess.run([sys.executable,'-B',str(rec/'validate_measurement.py'),label])
    if checked.returncode:
        print('STOP '+label+' strict reader exit '+str(checked.returncode),flush=True)
        sys.exit(checked.returncode)
    summary=json.loads((rec/(label+'-summary.json')).read_text())
    print(json.dumps({'label':label,'cold':summary.get('cold'),'warm':summary.get('warm'),
                      'cleanup':summary.get('cleanup'),'status':'PASS'}),flush=True)
print('SEQUENCE COMPLETE '+str(first)+'-'+str(last),flush=True)
