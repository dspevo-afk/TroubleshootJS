"""Independent A09 dependency and strict report-reader checks. No solver claims."""
from __future__ import annotations
import argparse
import copy
import json
from pathlib import Path

TRUE_FIELDS = ('singleUseReceipt', 'staleReceiptRejected', 'emptyProofRejected',
               'developerFixtureRejected', 'missingRepairRejected',
               'answerDependentProgramRejected', 'unavailableInputRejected',
               'failureOwnerRestored')


def _integer(value: object, minimum: int = 0) -> int:
    if type(value) is not int or value < minimum:
        raise ValueError('missing, non-integer, negative or insufficient count')
    return value


def validate_report(report: object) -> dict:
    if not isinstance(report, dict):
        raise ValueError('A09 report must be an object')
    if report.get('protocol') != 'TSJ-A09-DIAGNOSTIC-1' or report.get('status') != 'PASS' or report.get('cleanup') != 'PASS':
        raise ValueError('unqualified A09 protocol/status/cleanup')
    for field in TRUE_FIELDS:
        if report.get(field) is not True:
            raise ValueError('missing executed proof: ' + field)
    _integer(report.get('assertions'), 70)
    _integer(report.get('rejectedCases'), 11)
    if report.get('leafHypotheses') != 14:
        raise ValueError('retained leaf corpus changed')
    _integer(report.get('leafSamples'), 50)
    if _integer(report.get('overRangeSamples'), 1) > report['leafSamples']:
        raise ValueError('over-range outcomes exceed actual sample count')
    _integer(report.get('blockVariants'), 2)
    _integer(report.get('wallMs'))
    rows = report.get('serialProofs')
    if not isinstance(rows, list) or len(rows) != 2:
        raise ValueError('missing two live composed serial proofs')
    seeds, hypotheses, samples = set(), 0, 0
    for row in rows:
        if not isinstance(row, dict):
            raise ValueError('malformed serial proof')
        seed = _integer(row.get('seed'))
        if seed in seeds:
            raise ValueError('duplicate composed realization')
        seeds.add(seed)
        hypotheses += _integer(row.get('hypotheses'), 2)
        samples += _integer(row.get('samples'), 20)
        _integer(row.get('serialProofMs'))
        if row.get('ownerRestored') is not True or row.get('repairRetestPassed') is not True:
            raise ValueError('unqualified composed execution/restore')
    if seeds != {0, 3} or report.get('composedHypotheses') != hypotheses or report.get('composedSamples') != samples:
        raise ValueError('inconsistent composed corpus totals')
    return report


def architecture(root: Path) -> int:
    src = root / 'src/com/lushprojects/circuitjs1/client'
    checked = 0
    core = ('GeneratedDiagnosticProofService.java', 'GeneratedDiagnosticObservationExecutor.java',
            'GeneratedDiagnosticEquivalence.java', 'GeneratedDiagnosticProofReceipt.java',
            'GeneratedDiagnosticSolvabilityAdmission.java', 'GeneratedDiagnosticSolvabilityContract.java')
    for name in core:
        content = (src / name).read_text(encoding='utf-8')
        for forbidden in ('Task41DeveloperVerifier', 'QuickPlayFamilyRegistry', 'new LedIndicatorGenerator',
                          'new NpnLowSideSwitchGenerator', 'new RcDelayGenerator', 'lastControlledAdmissionEvidence'):
            if forbidden in content:
                raise AssertionError(f'{name} retained central/developer production dependency {forbidden}')
            checked += 1
    program = (src / 'GeneratedDiagnosticProgram.java').read_text(encoding='utf-8')
    for forbidden in ('CirSim', 'GeneratedFaultCandidate', 'GeneratedBoardInstance', 'Runnable', 'Callback', 'java.lang.reflect'):
        if forbidden in program:
            raise AssertionError('data-only observation script exposes answer/runtime authority: ' + forbidden)
        checked += 1
    catalog = (src / 'GeneratedDiagnosticPlanCatalog.java').read_text(encoding='utf-8')
    if 'forFamily(' in catalog:
        raise AssertionError('central family diagnostic dispatch retained')
    checked += 1
    for name in ('LedIndicator', 'DiodeProtectedIndicator', 'ParallelDualIndicator', 'RcDelay', 'NpnLowSideSwitch', 'NmosLowSideSwitch'):
        if f'new {name}DiagnosticProvider(seed)' not in (src / f'{name}Generator.java').read_text(encoding='utf-8'):
            raise AssertionError('generator contribution missing: ' + name)
        checked += 1
    return checked


def self_test() -> int:
    # Synthetic input is ONLY a report-reader fixture, never browser evidence.
    fixture = dict(protocol='TSJ-A09-DIAGNOSTIC-1', status='PASS', cleanup='PASS',
                   assertions=100, rejectedCases=11, leafHypotheses=14, leafSamples=100, overRangeSamples=10,
                   composedHypotheses=6, composedSamples=120, blockVariants=2, wallMs=1000,
                   serialProofs=[dict(seed=s, hypotheses=3, samples=60, serialProofMs=100,
                                     ownerRestored=True, repairRetestPassed=True) for s in (0, 3)])
    fixture.update({key: True for key in TRUE_FIELDS})
    validate_report(fixture)
    cases = [None, {}, [], {'status': 'PASS'}]
    for field in fixture:
        value = copy.deepcopy(fixture); value.pop(field); cases.append(value)
    for field in TRUE_FIELDS:
        for bad in (False, 1, 'true', None):
            value = copy.deepcopy(fixture); value[field] = bad; cases.append(value)
    for field in ('assertions', 'rejectedCases', 'leafSamples', 'overRangeSamples', 'blockVariants', 'wallMs'):
        for bad in (-1, True, '100', None):
            value = copy.deepcopy(fixture); value[field] = bad; cases.append(value)
    for field in ('hypotheses', 'samples', 'serialProofMs', 'ownerRestored', 'repairRetestPassed'):
        value = copy.deepcopy(fixture); value['serialProofs'][0].pop(field); cases.append(value)
    for field in ('status', 'cleanup'):
        value = copy.deepcopy(fixture); value[field] = 'FAIL'; cases.append(value)
    value = copy.deepcopy(fixture); value['serialProofs'][1]['seed'] = 0; cases.append(value)
    value = copy.deepcopy(fixture); value['composedHypotheses'] = 5; cases.append(value)
    value = copy.deepcopy(fixture); value['serialProofs'][0]['repairRetestPassed'] = False; cases.append(value)
    for bad in (0, 101):
        value = copy.deepcopy(fixture); value['overRangeSamples'] = bad; cases.append(value)
    for case in cases:
        try:
            validate_report(case)
        except ValueError:
            continue
        raise AssertionError('strict reader accepted a malformed or failed proof')
    return len(cases) + 1


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    checks = architecture(Path(__file__).resolve().parents[2]) + self_test()
    if args.report:
        validate_report(json.loads(args.report.read_text(encoding='utf-8')))
        checks += 1
    print(f'PASS: A09 independent diagnostic contracts assertions={checks}')


if __name__ == '__main__':
    main()
