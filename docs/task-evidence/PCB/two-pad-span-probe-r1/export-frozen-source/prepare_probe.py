"""Prepare two sealed private arms; leave every production checkout owner unchanged."""
import argparse
import difflib
import hashlib
import json
import runpy
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
PRIOR = HERE.parent / 'composed-rejection-diagnostic-r1/export-frozen-source/private-inputs'
CLIENT = ROOT / 'src/com/lushprojects/circuitjs1/client'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--private-root', required=True, type=Path)
    args = parser.parse_args()
    base = runpy.run_path(str(HERE / 'inputs.py'))
    assert base['record']() == base['read'](HERE / 'baseline-inputs.json')
    assert args.private_root.is_dir() and not any(args.private_root.iterdir())
    changes = []

    def replace(source, old, new, label, owner):
        nl = '\r\n' if '\r\n' in source else '\n'
        old, new = old.replace('\n', nl), new.replace('\n', nl)
        assert source.count(old) == 1, label
        changes.append({'owner': owner, 'label': label, 'replacements': 1,
                        'oldSha256': sha(old.encode()), 'newSha256': sha(new.encode())})
        return source.replace(old, new)

    def checkpoint(phase, plan):
        return ('        TaskPackingSpanObservation.emit("' + phase + '", session.board,\n'
                '            ' + plan + ', session.seed, session.routingSeed);\n')

    original_generator = (PRIOR / 'SeededPcbLayoutGenerator.java').read_bytes().decode('utf-8')
    control_generator = replace(original_generator,
        '        PcbPlacementPlanner.Plan source = session.bestPlan;\n',
        '        PcbPlacementPlanner.Plan source = session.bestPlan;\n' + checkpoint('SOURCE', 'source'),
        'observe selected-source input before optional compaction', 'control-generator')
    ordinary = ('        session.packingPlan = PcbPlacementCompactor.compact(session.board, constraints, source);\n')
    control_generator = replace(control_generator, ordinary,
        ordinary + checkpoint('ORDINARY', 'session.packingPlan'),
        'observe ordinary optional-compaction result', 'control-generator')
    paired = ('        session.packingPlan = PcbPlacementCompactor.compactPairedVacancies(\n'
              '            session.board, constraints, session.packingPlan);\n')
    control_generator = replace(control_generator, paired,
        paired + checkpoint('PAIRED', 'session.packingPlan') + checkpoint('FINAL', 'session.packingPlan'),
        'observe paired and final proposal before unchanged acceptance flow', 'control-generator')
    variant_generator = replace(control_generator, ordinary,
        '        java.util.TreeMap<String,Long> taskSpanCaps = PcbPlacementPlanner.twoPadSpanBounds(\n'
        '            session.board, source.footprints);\n'
        '        session.packingPlan = PcbPlacementCompactor.compact(\n'
        '            session.board, constraints, source, taskSpanCaps);\n',
        'pass selected-source caps only to optional single-part compaction', 'variant-generator')
    variant_generator = replace(variant_generator, checkpoint('FINAL', 'session.packingPlan'),
        '        if (!PcbPlacementPlanner.twoPadSpansFit(session.board,\n'
        '                session.packingPlan.footprints, taskSpanCaps))\n'
        '            session.packingPlan = new PcbPlacementPlanner.Plan(source.outline,\n'
        '                source.footprints, source.regions, source.courtyardArea, source.demandArea,\n'
        '                source.candidate, session.packingPlan.evaluations);\n' + checkpoint('FINAL', 'session.packingPlan'),
        'filter completed proposal while retaining all counted placement evaluations', 'variant-generator')

    planner = (CLIENT / 'PcbPlacementPlanner.java').read_bytes().decode('utf-8')
    compactor = (CLIENT / 'PcbPlacementCompactor.java').read_bytes().decode('utf-8')
    patch = runpy.run_path(str(HERE / 'span_guard_patch.py'))['adapt'](planner, compactor)
    assert set(patch) == {'planner', 'compactor', 'changes'} and patch['changes']
    restored = {'planner': patch['planner'], 'compactor': patch['compactor']}
    for item in reversed(patch['changes']):
        assert set(item) == {'owner', 'label', 'old', 'new'} and item['owner'] in restored
        assert restored[item['owner']].count(item['new']) == 1, item['label']
        restored[item['owner']] = restored[item['owner']].replace(item['new'], item['old'])
    assert restored == {'planner': planner, 'compactor': compactor}
    guard_changes = [{'owner': x['owner'], 'label': x['label'], 'replacements': 1,
                      'oldSha256': sha(x['old'].encode()), 'newSha256': sha(x['new'].encode())}
                     for x in patch['changes']]

    adapter_original = (PRIOR / 'verify-composed-rejection.ps1').read_bytes().decode('utf-8')
    generator_parameter = '    [Parameter(Mandatory=$true)][ValidateScript({Test-Path -LiteralPath $_ -PathType Leaf})][string]$TaskGeneratorSource,'
    adapter = replace(adapter_original, generator_parameter,
        generator_parameter + '\n'
        '    [Parameter(Mandatory=$true)][ValidateScript({Test-Path -LiteralPath $_ -PathType Leaf})][string]$TaskPlannerSource,\n'
        '    [Parameter(Mandatory=$true)][ValidateScript({Test-Path -LiteralPath $_ -PathType Leaf})][string]$TaskCompactorSource,',
        'require actual private planner and compactor source files', 'adapter')
    adapter = replace(adapter,
        "Where-Object { $_.Name -ne 'SeededPcbLayoutGenerator.java' -and",
        "Where-Object { $_.Name -ne 'PcbPlacementPlanner.java' -and $_.Name -ne 'PcbPlacementCompactor.java' -and $_.Name -ne 'SeededPcbLayoutGenerator.java' -and",
        'substitute exactly the three scoped physical owners', 'adapter')
    adapter = replace(adapter,
        '    $sourcePaths += (Resolve-Path -LiteralPath $TaskGeneratorSource).Path',
        '    $sourcePaths += (Resolve-Path -LiteralPath $TaskGeneratorSource).Path\n'
        '    $sourcePaths += (Resolve-Path -LiteralPath $TaskPlannerSource).Path\n'
        '    $sourcePaths += (Resolve-Path -LiteralPath $TaskCompactorSource).Path',
        'compile the three sealed private physical owner copies', 'adapter')

    exporter_original = (PRIOR / 'SparseNormalBoardDiagnostic.java').read_bytes()
    observer = (HERE / 'observer-source/TaskPackingSpanObservation.java.inc').read_bytes()
    outputs = {
        'common/SparseNormalBoardDiagnostic.java': exporter_original + b'\n' + observer,
        'common/verify-two-pad-span.ps1': adapter.encode(),
        'control/SeededPcbLayoutGenerator.java': control_generator.encode(),
        'control/PcbPlacementPlanner.java': planner.encode(),
        'control/PcbPlacementCompactor.java': compactor.encode(),
        'variant/SeededPcbLayoutGenerator.java': variant_generator.encode(),
        'variant/PcbPlacementPlanner.java': patch['planner'].encode(),
        'variant/PcbPlacementCompactor.java': patch['compactor'].encode(),
    }
    for n, data in outputs.items():
        p = args.private_root / n
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(data)
    manifest = {'status': 'GENERATED_PRIVATE_TWO_ARM_SPAN_PROBE',
        'baseCommit': base['BASE'], 'borrowedPrivateSources': base['read'](HERE / 'baseline-inputs.json')['borrowedPrivateSources'],
        'privateInputs': {n: sha(data) for n, data in outputs.items()},
        'factoryChanges': changes, 'guardChanges': guard_changes,
        'appendedObserverSha256': sha(observer),
        'candidateInterpretation': 'PRIVATE_TWO_PAD_SPAN_FILTER@1;NOT_PRODUCTION_MODEL',
        'limits': 'One control/variant exact replay, no production changes or model promotion. All default callers '
                  'retain null caps; only optional vacancy moves use selected-source bounds. Final cap fallback '
                  'retains source geometry with trial counted evaluations. No additional route, RNG, solver, '
                  'budget, validator or strict four-metric acceptance change.'}
    (HERE / 'private-inputs.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    for name, before, after in (
        ('control-generator', original_generator, control_generator),
        ('variant-generator', control_generator, variant_generator),
        ('variant-planner', planner, patch['planner']),
        ('variant-compactor', compactor, patch['compactor']),
        ('adapter', adapter_original, adapter),
    ):
        diff = ''.join(difflib.unified_diff(before.splitlines(True), after.splitlines(True),
                    fromfile='accepted-' + name, tofile='private-' + name))
        (HERE / (name + '-diff.txt')).write_text(diff, encoding='utf-8', newline='\n')
    print('PASS prepared control and private capped-span arm; exact guard reverse check and eight frozen inputs')


if __name__ == '__main__':
    main()
