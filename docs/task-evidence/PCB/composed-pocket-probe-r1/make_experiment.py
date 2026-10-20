"""Generate a private same-cap second-sweep owner and pinned native adapter."""
import argparse,difflib,hashlib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
OWNER='src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java'
def sha(raw):return hashlib.sha256(raw).hexdigest()
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--baseline-adapter',type=Path,required=True);parser.add_argument('--output-directory',type=Path,required=True);a=parser.parse_args()
    assert a.output_directory.is_dir() and not list(a.output_directory.iterdir())
    assert not (HERE/'private-input-manifest.json').exists()
    baseline=json.loads((HERE.parent/'sparse-board-diagnostic-r1/baseline-inputs.json').read_text())
    raw=(ROOT/OWNER).read_bytes();assert sha(raw)==baseline['files'][OWNER]
    original=raw.decode('utf-8');owner=original;owner_changes=[]
    def replace(source,old,new,label,changes):
        nl='\r\n' if '\r\n' in source else '\n';old=old.replace('\n',nl);new=new.replace('\n',nl)
        assert source.count(old)==1,label
        changes.append({'label':label,'replacements':1,'oldSha256':sha(old.encode()),'newSha256':sha(new.encode())})
        return source.replace(old,new)
    owner=replace(owner,
        '        int evaluations = input.evaluations;\n        for (PcbPlacementConstraints.Part demand : constraints.getParts()) {',
        '''        int evaluations = input.evaluations;
        for (int taskSweep = 1; taskSweep <= 2 && evaluations < MEDIUM_MAX_EVALUATIONS; taskSweep++) {
            int taskStart = evaluations;
            int taskMoves = 0;
            long taskAreaStart = area;
            double taskWireStart = wire;
        for (PcbPlacementConstraints.Part demand : constraints.getParts()) {''',
        'at most two declared-order sweeps sharing the original counter',owner_changes)
    owner=replace(owner,
        '''                bestWire = candidateWire;
            }
            if (best != source) {
                placed.set(index, best);
                area = bestArea;
                wire = bestWire;
            }''',
        '''                bestWire = candidateWire;
            }
            if (best != source) {
                placed.set(index, best);
                area = bestArea;
                wire = bestWire;
                taskMoves++;
            }''',
        'passive accepted-translation count',owner_changes)
    owner=replace(owner,
        '        }\n        if (evaluations == input.evaluations) return input;',
        '''        }
            System.out.println("PCB_POCKET_SWEEP_OBSERVATION {\\"schema\\":\\"pocket-second-sweep@1\\",\\"sweep\\":" + taskSweep +
                ",\\"evaluationStart\\":" + taskStart + ",\\"evaluationEnd\\":" + evaluations +
                ",\\"acceptedMoves\\":" + taskMoves + ",\\"areaStart\\":" + taskAreaStart + ",\\"areaEnd\\":" + area +
                ",\\"wireStart\\":" + taskWireStart + ",\\"wireEnd\\":" + wire + "}");
            if (taskMoves == 0) break;
        }
        if (evaluations == input.evaluations) return input;''',
        'passive sweep receipt and stop after no accepted translation',owner_changes)
    assert len(owner_changes)==3
    adapter_raw=a.baseline_adapter.read_bytes();prior=json.loads((HERE.parent/'sparse-board-diagnostic-r1/adapter-manifest.json').read_text())
    assert sha(adapter_raw)==prior['generatedSha256'];adapter_original=adapter_raw.decode('utf-8');adapter=adapter_original;adapter_changes=[]
    adapter=replace(adapter,'    [Parameter(Mandatory=$true)][string]$TaskExportSource,',
        "    [Parameter(Mandatory=$true)][string]$TaskExportSource,\n    [Parameter(Mandatory=$true)][ValidateScript({Test-Path -LiteralPath $_ -PathType Leaf})][string]$TaskPlannerSource,",
        'mandatory existing private owner input binding',adapter_changes)
    adapter=replace(adapter,"        Where-Object { $_.Name -ne 'PhysicalSpecificationDeveloperVerifier.java' -and",
        "        Where-Object { $_.Name -ne 'PcbPlacementPlanner.java' -and $_.Name -ne 'PhysicalSpecificationDeveloperVerifier.java' -and",
        'exclude exactly one checked-in owner from native compilation',adapter_changes)
    adapter=replace(adapter,'    $sourcePaths += $stub',
        '    $sourcePaths += $stub\n    $sourcePaths += (Resolve-Path -LiteralPath $TaskPlannerSource).Path',
        'compile exactly one frozen private owner instead',adapter_changes)
    assert len(adapter_changes)==3
    owner_bytes=owner.encode('utf-8');adapter_bytes=adapter.encode('utf-8')
    (a.output_directory/'PcbPlacementPlanner.java').write_bytes(owner_bytes)
    (a.output_directory/'verify-task-export.ps1').write_bytes(adapter_bytes)
    for name,before,after in (('owner-diff.txt',original,owner),('adapter-diff.txt',adapter_original,adapter)):
        lines=difflib.unified_diff(before.splitlines(),after.splitlines(),fromfile='published/'+name,tofile='private-experiment/'+name,lineterm='')
        (HERE/name).write_bytes(('\n'.join(lines)+'\n').encode('utf-8'))
    export=HERE.parent/'sparse-board-diagnostic-r1/export-source/SparseNormalBoardDiagnostic.java'
    assert sha(export.read_bytes())=='b0bfa9152cf71438261dec707c27e91bd0e7db5757a4a2db0851d462e239876c'
    (HERE/'private-input-manifest.json').write_text(json.dumps({'status':'GENERATED_TASK_ONLY_EXPERIMENT','publishedOwner':OWNER,'publishedOwnerSha256':sha(raw),'privateOwnerSha256':sha(owner_bytes),'privateOwnerBytes':len(owner_bytes),'ownerChanges':owner_changes,'baselineAdapterSha256':sha(adapter_raw),'privateAdapterSha256':sha(adapter_bytes),'adapterChanges':adapter_changes,'reusedExporterSha256':sha(export.read_bytes()),'limits':'Only this experimental native compilation substitutes one private planner owner. Production source/layout19/packing3 remain unchanged; experimental output is not the current production replay. Same original evaluation ceiling and validators, no allowance increase.'},indent=2)+'\n')
    print('PASS generated private second-sweep owner3 guarded changes and private adapter3 guarded changes')
if __name__=='__main__':main()
