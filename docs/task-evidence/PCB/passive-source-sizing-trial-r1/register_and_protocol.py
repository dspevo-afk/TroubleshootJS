"""Register exact fixtures, derive actual registry order, declare protocol."""
import hashlib
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
COPY = Path(json.loads((HERE/'local-state.json').read_text())['scratch'])
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
new = json.loads((HERE/'new-suites.json').read_text())
registry = COPY/'scripts/verify-current-contracts.ps1'
base = {r['path']: r['sha256'] for r in json.loads((HERE/'copy-inputs.json').read_text())['files']}
assert sha(registry) == base['scripts/verify-current-contracts.ps1']
data = registry.read_bytes(); nl = b'\r\n' if b'\r\n' in data else b'\n'
needle = b"        @{ Name = 'PcbPassiveSizingRouteContractTestGroup2'; Marker = 'PCB passive sizing route3 contracts ' },"
assert data.count(needle) == 1
entries = [("        @{ Name = '%s'; Marker = '%s' }," % tuple(row)).encode() for row in new['unit']+new['routes']]
registry.write_bytes(data.replace(needle, needle+nl+nl.join(entries)))
old_unit = [['PcbPassivePoseSourceContractTest', 'PCB passive pose source bindings '],
            ['PcbPassivePosePolicyContractTest', 'PCB passive pose policy contracts '],
            ['PcbPassivePoseAdmissionContractTest', 'PCB passive pose admission contracts '],
            ['PcbPassivePoseTrialSourceContractTest', 'PCB passive pose trial source contracts ']]
all_rows = re.findall(r"^\s*@\{ Name = '([^']+)'; Marker = '([^']*)' \},?\s*$", registry.read_text(), re.MULTILINE)
manifests = {}
for phase, selected in [('unit', new['unit']+old_unit), ('routes', new['routes'])]:
    names = {row[0] for row in selected}
    ordered = [[name, marker] for name, marker in all_rows if name in names]
    assert len(ordered) == len(selected) and {tuple(r) for r in ordered} == {tuple(r) for r in selected}
    for name, marker in ordered: assert marker in (COPY/'tests/contracts'/(name+'.java')).read_text()
    target = HERE/(phase+'-suites.json'); assert not target.exists()
    target.write_text(json.dumps(ordered, indent=2)+'\n'); manifests[phase] = ordered
assert len(manifests['unit']) == 8 and len(manifests['routes']) == 14
roots = json.loads((HERE/'proposal.json').read_text())['roots']
protocol = {
    'schema': 'pcb-passive-source-sizing-trial-v1', 'roots': roots,
    'cases': [[seed, version] for seed in roots for version in (15, 18)],
    'admissionByVersion': {'15': 'RB56_BOARD_NORMAL@1', '18': 'RB56_BOARD_NORMAL@4'},
    'policyByVersion': {'15': 0, '18': 3}, 'density': 14,
    'sourcePrefixes': {'old': 'PCB_PASSIVE_POSE_SOURCE_CASE ', 'trial16': 'PCB_PASSIVE_POLICY_INPUT ',
                       'trial18': 'PCB_SOURCE_SIZED_POSE_INPUT '},
    'trialSourceFields': ['seed', 'sourceCanonical15', 'sourcePlanSha25618', 'graph18', 'intent18',
                         'layoutSeed18', 'routingSeed18', 'eligibleIds', 'densityVersion18',
                         'passivePosePolicy18', 'admissionIdentity18'],
    'rawPrefix': 'PCB_SOURCE_SIZED_POSE_RAW ',
    'rawFields': ['seed', 'attempt', 'geometry15', 'geometry18', 'evaluations15', 'evaluations18',
                  'demand15', 'demand18', 'courtyard15', 'courtyard18'],
    'rawCases': [[seed, attempt] for seed in ('42', '-1022', '4') for attempt in (0, 2, 3, 4, 5)],
    'routePrefix': 'PCB_SOURCE_SIZED_POSE_ROUTE ', 'posePrefix': 'PCB_SOURCE_SIZED_POSE_POSES ',
    'poseFields': ['seed', 'issuedAdmissionIdentity', 'version', 'top0', 'top90'],
    'whitespacePrefix': 'PCB_WHITESPACE_CASE ',
    'whitespaceFields': ['seed', 'version', 'parts', 'gridUnits', 'sampledCells',
                         'packageAccessOccupiedCells', 'protectedFreeCells', 'protectedFreeFraction',
                         'largestProtectedEmptyRectangle', 'scope'],
    'whitespaceScope': 'GRID10_FULL_PACKAGE_ACCESS_BOTH_COPPER_FACES_VIAS_SILK_AND_DECLARED_ISOLATION;FREE_IS_NOT_PACKABLE_PROOF',
    'unitReportOrder': 'trial18source14,raw15,old14,trial16source14; pure policy/admission emit only PASS markers',
    'routeReportOrder': 'whitespace,pose,route per case; fixed14 roots each15 then18',
    'timingScope': 'Native source-sized orthogonal trial; unchanged per-JVM/search/physical limits',
    'fitnessMetrics': ['area', 'largestProtectedEmptyRectangleArea', 'copperManhattanLength', 'vias'],
    'fitness': 'Every trial18 metric <= paired15, at least one strict improvement overall, at least one selected canonical TOP90 overall; any missing/failure/no-op rejects',
    'baselinesSha256': {name: sha(HERE/name) for name in ('accepted15-baselines.json', 'raw-baselines.json')},
    'limits': 'Finite informed regressions only; no physical/player/repair/replay/manufacturing promotion from this diagnostic'}
(HERE/'protocol.json').write_text(json.dumps(protocol, indent=2)+'\n')
print('Registered18 fixtures; source-derived unit8/routes14 order; declared28 cases and whitespace rows')
