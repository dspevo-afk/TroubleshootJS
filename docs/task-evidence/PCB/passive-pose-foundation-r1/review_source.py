"""Root pre-runtime structural audit; behavioral expectations stay in native fixtures."""
import hashlib
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
COPY = Path(json.loads((HERE / 'local-state.json').read_text())['scratch']).resolve()
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
adapter = 'tests/contracts/PcbPostSelectionEdgeTrimContractTest.java'
assert sha(COPY / adapter) == '215dfed0c329fd261c93981ab3f2d25b56413885f51ac6ca7af4f1579b5fd488'
old = (ROOT / adapter).read_text()
new = (COPY / adapter).read_text()
# The reused adapter is the sole addition; the authoritative private body is exact.
start = '    private static Observation routeAndObserve('
assert old[old.index(start):] == new[new.index(start):]
catalog_path = 'src/com/lushprojects/circuitjs1/client/PhysicalPackages.java'
accepted_catalog = (ROOT / catalog_path).read_text()
trial_catalog = (COPY / catalog_path).read_text()
restored_catalog = trial_catalog
for name, previous, updated in (
        ('AXIAL_RESISTOR', 'packageWithCatalog', 'passiveTopCardinalPackageWithCatalog'),
        ('AXIAL_DIODE', 'packageWithCatalog', 'passiveTopCardinalPackageWithCatalog'),
        ('RADIAL_ELECTROLYTIC_CAPACITOR', 'fixedPackage', 'passiveTopCardinalFixedPackage'),
        ('RADIAL_CERAMIC_CAPACITOR', 'fixedPackage', 'passiveTopCardinalFixedPackage')):
    before = 'static final PhysicalPackage ' + name + ' = ' + updated + '('
    assert restored_catalog.count(before) == 1
    restored_catalog = restored_catalog.replace(before, before.replace(updated, previous))
begin = restored_catalog.index('    /** Mechanical pose allowance for the four standard passive package families only. */')
end = restored_catalog.index('    private static PhysicalPackage developerCanaryPackageOf(', begin)
helpers = restored_catalog[begin:end]
assert 'allCardinalRotations(), topOnlyMountingSide()' in helpers
assert 'PcbBoardSide.TOP' in helpers and 'PcbBoardSide.BOTTOM' not in helpers
assert restored_catalog[:begin] + restored_catalog[end:] == accepted_catalog, 'Unscoped catalog/geometry edit'
source = (COPY / 'tests/contracts/PcbPassivePoseSourceContractTest.java').read_text()
baseline = json.loads((HERE / 'accepted15-baselines.json').read_text())['rows']
assert source.count('new Expected(') == 14
for row in baseline:
    for key in ('seed', 'sourcePlanSha256', 'liveBoardGraphSha256', 'sourceIntentSha256', 'layoutSeed', 'routingSeed'):
        assert json.dumps(row[key]) in source
for name in ('PcbPassivePoseCurrent15ContractTest', 'PcbPassivePoseCurrent15ContractTestGroup2'):
    assert (COPY / ('tests/contracts/' + name + '.java')).is_file()
# No generator or source-plan owner may be touched by the capability probe.
base = {r['path']: r['sha256'] for r in json.loads((HERE / 'copy-inputs.json').read_text())['files']}
for relative in base:
    if relative.startswith('src/') and not relative.endswith('/PhysicalPackages.java'):
        assert sha(COPY / relative) == base[relative], 'Unscoped production owner changed'
(HERE / 'source-review.json').write_text(json.dumps({'status': 'PASS',
    'acceptedBaselineSha256': sha(HERE / 'accepted15-baselines.json'),
    'authoritativeRouteBodyExact': True, 'sourceBindingLiteralRoots': 14,
    'catalogOutsideFourDeclarationsAndNarrowHelpersExact': True,
    'unchangedOtherProductionOwners': True,
    'rootMechanicalReview': 'See README final review; worker source is reviewed before registration/freeze'}, indent=2) + '\n')
print('PASS exact authoritative route body, sealed literals and unchanged generation owners')
