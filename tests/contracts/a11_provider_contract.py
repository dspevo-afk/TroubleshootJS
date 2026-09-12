"""Current provider report and source-boundary checks; solver evidence is separate."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

CORE = (
    'BoundedAssemblyPlan.java', 'BoundedGeneratedBoardAssembler.java',
    'ElectricalRealizationSpec.java', 'PhysicalConstructionMaterializer.java',
    'GeneratedDiagnosticProofService.java', 'GeneratedDiagnosticObservationExecutor.java',
    'SeededPcbLayoutGenerator.java', 'CirSim.java', 'ChallengeDescriptor.java',
    'GenerationRequest.java',
)
INDIVIDUAL = (
    'AlternateNmosDriverProvider', 'NmosDriverProfile',
    'NmosControlledDriverProvider', 'NpnControlledDriverProvider',
    'NmosControlledDriverPhysicalProvider', 'NpnControlledDriverPhysicalProvider',
    'NmosControlledIndicatorDriverObservation', 'NpnControlledIndicatorDriverObservation',
    'nmos-low-side-driver-alt',
)
EXACT = dict(protocol='TSJ-A11-PROVIDERS-1', status='PASS', scope='provider-declarations',
             entries=9, paired=7, joins=2, roleVariants=3, plans=6, parts=78,
             generatorVersion=6, solverEvidence='separate-current-Task49-report')


def validate(report: object) -> None:
    if not isinstance(report, dict) or set(report) != set(EXACT) | {'negativeCases', 'assertions'}:
        raise ValueError('Incomplete or unknown provider report fields')
    for key, expected in EXACT.items():
        if type(report[key]) is not type(expected) or report[key] != expected:
            raise ValueError('Unqualified provider report: ' + key)
    for key, minimum in (('negativeCases', 17), ('assertions', 200)):
        if type(report[key]) is not int or report[key] < minimum:
            raise ValueError('Missing executed conformance: ' + key)


def boundary(name: str, content: str) -> None:
    for implementation in INDIVIDUAL:
        if implementation in content:
            raise ValueError(f'{name} imports or names individual provider {implementation}')


def verify(root: Path, report_path: Path | None) -> int:
    checks = 0
    for name in CORE:
        content = (root / 'src/com/lushprojects/circuitjs1/client' / name).read_text(encoding='utf-8')
        boundary(name, content)
        checks += 1
        for implementation in INDIVIDUAL:
            try:
                boundary(name, content + '\nimport fixture.' + implementation + ';')
            except ValueError:
                checks += 1
            else:
                raise AssertionError('Source falsifier was accepted')
    valid = dict(EXACT, negativeCases=17, assertions=200)
    validate(valid)
    checks += 1
    for key in valid:
        missing = dict(valid)
        del missing[key]
        try:
            validate(missing)
        except ValueError:
            checks += 1
        else:
            raise AssertionError('Incomplete report was accepted')
    for key, value in (('status', 'FAIL'), ('entries', 8), ('paired', 6), ('roleVariants', 2),
                       ('generatorVersion', 5), ('negativeCases', 0), ('assertions', True),
                       ('scope', 'solver-runtime'), ('parts', 0), ('unknown', 1)):
        altered = dict(valid, **{key: value})
        try:
            validate(altered)
        except ValueError:
            checks += 1
        else:
            raise AssertionError('Invalid report was accepted')
    if report_path:
        validate(json.loads(report_path.read_text(encoding='utf-8-sig')))
        checks += 1
    return checks


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    total = verify(Path(__file__).resolve().parents[2], args.report)
    print(f'PASS: A11 provider boundaries/report assertions={total}')
