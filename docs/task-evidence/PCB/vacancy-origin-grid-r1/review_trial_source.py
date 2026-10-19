"""Root static comparison of trial owners after the worker relinquishes them."""
import hashlib
import json
import re
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
COPY = Path(json.loads((HERE / 'local-state.json').read_text())['scratch']).resolve()
assert (HERE / 'phase2-worker-notes.md').is_file(), 'Worker handoff required'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
prefix = 'src/com/lushprojects/circuitjs1/client/'
planner = (COPY / (prefix + 'PcbPlacementPlanner.java')).read_text()
block = '''        Vector<Point> ringZeroVacancies=vacancies;
        if(constraints.placementDensityVersion==15 && pinLocal && !anchor &&
                vacancies!=null) {
            Vector<Point> aligned=PcbVacancyTargets.originsOnGrid(vacancies,10);
            if(!aligned.isEmpty()) ringZeroVacancies=aligned;
        }
'''
assert planner.count(block) == 1
restored = planner.replace(block, '').replace(
    '''                if(ring==0 && index>0 && ringZeroVacancies!=null &&
                        index<=ringZeroVacancies.size()) {
                    Point vacancy=ringZeroVacancies.get(index-1);x=vacancy.x;y=vacancy.y;''',
    '''                if(ring==0 && index>0 && vacancies!=null && index<=vacancies.size()) {
                    Point vacancy=vacancies.get(index-1);x=vacancy.x;y=vacancy.y;''')
assert restored == (ROOT / (prefix + 'PcbPlacementPlanner.java')).read_text(), 'Planner changed outside origin hook'
generator = (COPY / (prefix + 'SeededPcbLayoutGenerator.java')).read_text()
restored = generator.replace('''        if (constraints.placementDensityVersion != 14 &&
                constraints.placementDensityVersion != 15) return;''',
    '        if (constraints.placementDensityVersion != 14) return;').replace(
    '''                board.getPlacementConstraints().placementDensityVersion == 14 ||
                board.getPlacementConstraints().placementDensityVersion == 15))''',
    '                board.getPlacementConstraints().placementDensityVersion == 14))')
assert restored == (ROOT / (prefix + 'SeededPcbLayoutGenerator.java')).read_text(), 'Generator changed beyond inherited trim/canvas boundaries'
constraints = (COPY / (prefix + 'PcbPlacementConstraints.java')).read_text()
assert constraints.count(' && densityVersion != 15)') == 1
assert constraints.replace(' && densityVersion != 15)', ')') == (ROOT / (prefix + 'PcbPlacementConstraints.java')).read_text(), 'Constraints changed beyond explicit density15 registration'
adapter = 'tests/contracts/PcbPostSelectionEdgeTrimContractTest.java'
old = (ROOT / adapter).read_text()
new = (COPY / adapter).read_text()
start = '        check(board.getPlacementConstraints().requiresTwoLayerRouting,'
assert old[old.index(start):] == new[new.index(start):], 'Authoritative observation/validation body changed'
common = (COPY / 'tests/contracts/PcbVacancyGridTrialContractTest.java').read_text()
pattern = r'new Baseline\("(-?[0-9]+)", ([0-9]+), ([0-9]+),\s*"([a-f0-9]{64})",\s*"([a-f0-9]{64})",\s*"([a-f0-9]{64})",\s*"(-?[0-9]+)", "(-?[0-9]+)"\)'
actual = re.findall(pattern, common)
bases = json.loads((HERE / 'accepted15-baselines.json').read_text())['rows']
expected = [(r['seed'], str(r['parts']), str(r['pads']), r['sourcePlanSha256'],
    r['liveBoardGraphSha256'], r['sourceIntentSha256'], r['layoutSeed'], r['routingSeed']) for r in bases]
assert actual == expected and len(actual) == 14, 'Literal baseline transport differs'
partitions = [('PcbVacancyGridTrialContractTest.java', [-1017, -1004])]
partitions += [('PcbVacancyGridTrialContractTestGroup' + str(i) + '.java', roots)
    for i, roots in enumerate(([-1022, 0], [17, 42], [1, 7], [5, 14], [2, 3], [4, 6]), 2)]
for name, roots in partitions:
    text = (COPY / 'tests/contracts' / name).read_text()
    if name.endswith('ContractTest.java'):
        assert '{ -1017L, -1004L }' in text
    else:
        found = re.search(r'new long\[\]\s*\{([^}]+)\}', text)
        assert found and [int(x.strip().rstrip('L')) for x in found.group(1).split(',')] == roots
owners = [prefix + n for n in ('Rb56Placement.java', 'PcbPlacementPlanner.java', 'SeededPcbLayoutGenerator.java', 'PcbPlacementConstraints.java')]
owners += [adapter, 'tests/contracts/PcbVacancyGridTrialTargetContractTest.java']
owners += ['tests/contracts/' + p[0] for p in partitions]
(HERE / 'phase2-source-review.json').write_text(json.dumps({'status': 'PASS',
    'scope': 'Root reviewed4 production diffs, adapter and all new fixtures; old physical observation body exact after line-ending normalization, planner outside origin-only hook exact, generator outside inherited density guards exact, constraints outside explicit density15 registration exact, all14 baseline literals/partitions exact',
    'sourceHashes': {p: sha(COPY / p) for p in owners},
    'runtime': 'NOT RUN; static review is not physical or metric acceptance',
    'old14and15EntryGuardsPreserved': True, 'metricOracleChanged': False}, indent=2) + '\n')
print('PASS root static source/baseline/partition review; runtime NOT RUN')
