"""Prepare root-owned wrappers and a private adapter; preserve every input."""
import argparse,difflib,hashlib,json,runpy,sys
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
OLD=HERE.parent/'sparse-board-diagnostic-r1'

def sha(b):return hashlib.sha256(b).hexdigest()
def replace_once(source,old,new):
    assert source.count(old)==1,old
    return source.replace(old,new)

def new_text(path,body):
    with path.open('x',encoding='utf-8',newline='\n') as f:f.write(body)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--private',type=Path,required=True)
    args=parser.parse_args();private=args.private.resolve()
    assert private.is_dir() and not any(private.iterdir()),'Use a fresh task-owned OS-temp folder'
    base=runpy.run_path(str(HERE/'inputs.py'));assert base['record']()==base['read'](HERE/'baseline-inputs.json')
    first=private/'adapter-initial.ps1';first_manifest=private/'adapter-initial-manifest.json'
    prior_argv=sys.argv
    try:
        sys.argv=['make_export_adapter.py','--output',str(first),'--manifest',str(first_manifest)]
        runpy.run_path(str(OLD/'make_export_adapter.py'),run_name='__main__')
    finally:sys.argv=prior_argv
    raw=first.read_bytes();source=raw.decode('utf-8')
    old="[ValidateSet('RC_DELAY','COMPOSED_CONTROLLED_INDICATOR')]"
    new="[ValidateSet('NPN_LOW_SIDE_SWITCH')]"
    final=replace_once(source,old,new).encode('utf-8')
    target=private/'adapter.ps1'
    with target.open('xb') as f:f.write(final)
    manifest=json.loads(first_manifest.read_text(encoding='utf-8-sig'))
    manifest['initialAcceptedAdapterSha256']=sha(raw)
    manifest['changes'].append({'label':'Restrict task binding to the one NPN family',
                               'replacements':1,'oldSha256':sha(old.encode()),'newSha256':sha(new.encode())})
    manifest['generatedSha256']=sha(final);manifest['generatedBytes']=len(final)
    manifest['limits']+=' Six guarded changes including NPN-only family binding; original five-change adapter retained privately.'
    new_text(HERE/'adapter-manifest.json',json.dumps(manifest,indent=2)+'\n')
    maintained=(ROOT/'scripts/verify-current-contracts.ps1').read_text(encoding='utf-8-sig')
    diff=''.join(difflib.unified_diff(maintained.splitlines(True),final.decode().replace('\r\n','\n').splitlines(True),fromfile='maintained/verify-current-contracts.ps1',tofile='private-task-adapter.ps1'))
    new_text(HERE/'adapter-diff.txt',diff)

    runner=(OLD/'run_export.ps1').read_text(encoding='utf-8-sig')
    runner=replace_once(runner,"ValidateSet('canaries','rc','composed')","ValidateSet('canaries','npn')")
    runner=replace_once(runner,"'export_inputs.py'","'inputs.py'")
    runner=replace_once(runner,"else{'RC_DELAY'}","else{'NPN_LOW_SIDE_SWITCH'}")
    runner=replace_once(runner,"else{'-9187226374781659024'}","else{'380469407799690561'}")
    runner=replace_once(runner,"$family=if($Phase -eq 'rc'){'RC_DELAY'}else{'COMPOSED_CONTROLLED_INDICATOR'}","$family='NPN_LOW_SIDE_SWITCH'")
    runner=replace_once(runner,"$seed=if($Phase -eq 'rc'){'-9187226374781659024'}else{'7141843758046216173'}","$seed='380469407799690561'")
    new_text(HERE/'run_export.ps1',runner)
    exits=(OLD/'record_exit.ps1').read_text(encoding='utf-8-sig')
    new_text(HERE/'record_exit.ps1',replace_once(exits,"ValidateSet('canaries','rc','composed')","ValidateSet('canaries','npn')"))
    reader=(OLD/'qualify.py').read_text(encoding='utf-8-sig')
    reader=replace_once(reader,"EXPECTED={'rc':('RC_DELAY','EASY','-9187226374781659024'),\n          'composed':('COMPOSED_CONTROLLED_INDICATOR','MEDIUM','7141843758046216173')}",
                        "EXPECTED={'npn':('NPN_LOW_SIDE_SWITCH','EASY','380469407799690561')}")
    reader=replace_once(reader,"PASS sparse export unchanged published source1482 machinery24 frozen helpers12",'PASS exact NPN unchanged source1482 machinery24 sealed observation inputs audit')
    reader=replace_once(reader,"runpy.run_path(str(HERE/'export_inputs.py'))['record']()","runpy.run_path(str(HERE/'inputs.py'))['audit_export']()")
    reader=replace_once(reader,'PASS exact two accepted replays; independent metrics/inventories; eight nonvacuous reader negatives; separate actual tool exits',
                        'PASS exact accepted NPN replay; independent metrics/inventories; four nonvacuous reader negatives; separate actual tool exits')
    new_text(HERE/'qualify.py',reader)
    print('PASS root wrappers and six-change private NPN adapter prepared; no native execution')

if __name__=='__main__':main()
