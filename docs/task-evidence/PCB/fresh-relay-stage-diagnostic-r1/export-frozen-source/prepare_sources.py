"""Prepare exact borrowed passive control sources with only finite relay bindings."""
import difflib,hashlib,json,runpy,sys
from pathlib import Path
HERE=Path(__file__).resolve().parent
sha=lambda data:hashlib.sha256(data).hexdigest()
def main(private_root):
    util=runpy.run_path(str(HERE/'inputs.py'));baseline=util['record']();assert baseline==util['read'](HERE/'baseline-inputs.json')
    target=HERE/'private-inputs.json';assert not target.exists()
    originals={n:(util['BORROW']/n).read_bytes() for n in util['NAMES']}
    assert {n:sha(v) for n,v in originals.items()}==baseline['borrowedPrivateSources']
    exporter=originals['common/SparseNormalBoardDiagnostic.java'].decode('utf-8')
    adapter=originals['common/verify-two-pad-span.ps1'].decode('utf-8')
    patch=runpy.run_path(str(HERE/'relay_binding_patch.py'))['adapt'](exporter,adapter)
    assert set(patch)=={'exporter','adapter','changes'} and len(patch['changes'])==4
    restored={n:patch[n] for n in ('exporter','adapter')}
    for c in reversed(patch['changes']):
        assert set(c)=={'owner','label','old','new'} and restored[c['owner']].count(c['new'])==1
        restored[c['owner']]=restored[c['owner']].replace(c['new'],c['old'])
    assert restored=={'exporter':exporter,'adapter':adapter}
    assert "[ValidateSet('RELAY_OUTPUT')]" in patch['adapter']
    marker='    private static void export('
    assert exporter.split(marker,1)[1]==patch['exporter'].split(marker,1)[1]
    outputs=dict(originals);outputs['common/SparseNormalBoardDiagnostic.java']=patch['exporter'].encode();outputs['common/verify-two-pad-span.ps1']=patch['adapter'].encode()
    for n,data in outputs.items():
        f=Path(private_root)/n;assert not f.exists();f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
    manifest={'status':'PREPARED_FINITE_RELAY_PASSIVE_CONTROL','baseCommit':util['BASE'],'privateInputs':{n:sha(v) for n,v in outputs.items()},'borrowedPrivateSources':baseline['borrowedPrivateSources'],'changes':[{k:c[k] for k in ('owner','label')}|{'replacements':1,'oldSha256':sha(c['old'].encode()),'newSha256':sha(c['new'].encode())} for c in patch['changes']],'limits':'Only finite exporter/host binding changes. Generator passive control/planner/compactor exact accepted borrowed bytes; all export assertions/disposal/observers unchanged. No new production or private placement variant.'}
    target.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    for n,old in (('exporter',exporter),('adapter',adapter)):
        diff=''.join(difflib.unified_diff(old.splitlines(True),patch[n].splitlines(True),fromfile='borrowed-'+n,tofile='private-relay-'+n))
        (HERE/(n+'-diff.txt')).write_text(diff,encoding='utf-8',newline='\n')
    print('PASS prepared private5 relay inputs; four reverse-exact binding changes; export and both observers unchanged')
if __name__=='__main__':main(sys.argv[1])
