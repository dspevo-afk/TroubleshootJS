"""Check current cold MEDIUM assessment; historical raw observations stay sealed."""
import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[3]
SEEDS = ['-1017', '-1067', '-1022']
SIZES = [40, 55, 60]


def need(value, message):
    if not value:
        raise ValueError(message)


def load_reader(name, relative, digest):
    path = REPO / relative
    need(hashlib.sha256(path.read_bytes()).hexdigest() == digest, 'sealed reader bytes changed')
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


FEATURES = load_reader('r12_sealed_features',
    'docs/task-evidence/U05/q60-calibration-r2/read_features.py',
    '469ba33f119cff99d5311bc1572a72a46f4c87e456658ab7718f8a18bb4c6311')
WAITS = load_reader('r12_sealed_waits',
    'docs/task-evidence/U05/q60-calibration-r6/read_waits.py',
    '6d3e93ed1dcc3a2ff544756f2c14636cd161e0095befb39ebdf096df396348dc')


def check_case(envelope, seed, size, plan):
    # Reuse the independent sample/tolerance/minimax/proof/cleanup validators,
    # not the old cohort entry point or its old base/selection metadata.
    checked = FEATURES._validate_capture(envelope, seed, plan)
    report = envelope['report']
    facts, analysis = checked['features'], checked['analysis']
    need(report['packages'] == size, 'curated package boundary differs')
    need(type(report.get('normalProfileAssessmentVersion')) is int and
         report['normalProfileAssessmentVersion'] == 3, 'current assessment version required')
    need(report.get('normalProfileValidated') == 'MEDIUM', 'actual MEDIUM assessment missing')
    need(report.get('normalPublicationGranted') is False, 'private audit is not public release')
    prefix = 'tsj-difficulty-calibration/1;status=UNCALIBRATED'
    expected = 'difficulty/3;profile=MEDIUM' + report['difficultyFeatures'][len(prefix):]
    need(report.get('normalProfileAssessment') == expected, 'assessment aggregate/label mismatch')
    interacting = facts['temporal'] > 0 or (facts['parallel'] == 'true' and facts['modes'] >= 2)
    need(analysis['readings'] >= 2 and facts['owners'] >= 2 and
         analysis['repairClasses'] >= 2 and interacting, 'evidence does not satisfy unchanged MEDIUM rule')
    waits = WAITS.rows(report)
    need(facts['temporal'] == len(report['calibrationTemporalWaits'][0]), 'trace/feature wait census differs')
    return {'seed': seed, 'packages': size, 'readings': analysis['readings'],
            'repairClasses': analysis['repairClasses'], 'singleRemaining': analysis['singleRemaining'],
            'generationMillis': checked['generationMillis'], 'generationWork': checked['generationWork'],
            'maxManualUnitMillis': checked['manualUnitMaxMillis'], 'naturalRequestedSeconds': waits,
            'profile': 'MEDIUM', 'publicReleaseGranted': False}


def self_test():
    FEATURES.self_test()
    WAITS.self_test()
    plan = {'build': 'A' * 32, 'sourceBinding': 'b' * 64}
    rejected = 0
    for seed, size in zip(SEEDS, SIZES):
        report = FEATURES._synthetic_report(seed)
        for row in report['calibrationObservations']:
            for column, sample in enumerate(row['samples']):
                value = float(row['ordinal'] // 3 if column == 0 else row['ordinal'] % 3)
                sample.update(value=value, tolerance=FEATURES._expected_tolerance(value))
        analysis = FEATURES._analyze(report['calibrationObservations'])
        report['packages'] = size
        report['diagnosticProgramVersion'] = 2
        report['difficultyFeatures'] = FEATURES._feature_string(size, analysis).replace(';temporal=3;', ';temporal=4;')
        report['calibrationTemporalWaits'] = [['HIGH_INPUT_SETTLED@0.0', 'SERVICE_DECAY_1@1.0',
            'SERVICE_DECAY_2@0.4', 'SERVICE_DECAY_3@0.0'] for _ in range(5)]
        report.update(normalProfileAssessmentVersion=3, normalProfileValidated='MEDIUM',
                      normalPublicationGranted=False)
        report['normalProfileAssessment'] = report['difficultyFeatures'].replace(
            'tsj-difficulty-calibration/1;status=UNCALIBRATED', 'difficulty/3;profile=MEDIUM')
        envelope = {'schema': FEATURES.CAPTURE_SCHEMA, 'seed': seed, **plan, 'report': report}
        need(check_case(envelope, seed, size, plan)['readings'] == 2, 'positive math fixture failed')
        changes = [('normalProfileAssessmentVersion', 2), ('normalProfileValidated', 'HARD'),
                   ('normalPublicationGranted', True), ('normalProfileAssessment', report['normalProfileAssessment'] + ';fault=RDA'),
                   ('normalCatalogEnabled', True), ('cleanupPending', True), ('candidateDisposed', False),
                   ('coldProof', False), ('generationMillis', 90001), ('packages', 61)]
        bads = []
        for key, value in changes:
            bad = copy.deepcopy(envelope); bad['report'][key] = value; bads.append(bad)
        for key, value in [('seed', '1'), ('build', 'C' * 32), ('sourceBinding', 'd' * 64)]:
            bad = copy.deepcopy(envelope); bad[key] = value; bads.append(bad)
        bad = copy.deepcopy(envelope)
        bad['report']['calibrationObservations'][0]['samples'] = copy.deepcopy(
            bad['report']['calibrationObservations'][1]['samples'])
        bads.append(bad)
        bad = copy.deepcopy(envelope)
        for row in bad['report']['calibrationObservations']:
            for sample in row['samples']:
                value = float(row['ordinal'])
                sample.update(value=value, tolerance=FEATURES._expected_tolerance(value))
        single = FEATURES._analyze(bad['report']['calibrationObservations'])
        bad['report']['difficultyFeatures'] = FEATURES._feature_string(size, single).replace(';temporal=3;', ';temporal=4;')
        bad['report']['normalProfileAssessment'] = bad['report']['difficultyFeatures'].replace(
            'tsj-difficulty-calibration/1;status=UNCALIBRATED', 'difficulty/3;profile=MEDIUM')
        bads.append(bad)
        bad = copy.deepcopy(envelope)
        bad['report']['calibrationTemporalWaits'][0][3] = 'SERVICE_DECAY_3@0.1'
        bads.append(bad)
        for bad in bads:
            try:
                check_case(bad, seed, size, plan)
            except (FEATURES.ReaderError, ValueError, KeyError, TypeError):
                rejected += 1
                continue
            raise ValueError('MEDIUM reader accepted a negative canary')
    print(f'PASS: mathematical MEDIUM reader fixtures; {rejected} negative canaries; no simulator run')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--self-test', action='store_true')
    parser.add_argument('--plan', type=Path)
    parser.add_argument('--captures', type=Path, nargs=3)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if args.self_test:
        self_test(); return
    need(args.plan and args.captures and args.output, 'plan/captures/output required')
    plan = json.loads(args.plan.read_text(encoding='utf-8-sig'))
    need(plan.get('schema') == 'tsj-rb56-medium-profile-plan/1' and
         plan.get('baseCommit') == 'e83dcc5ab2372dbbc3dac4605158160242052ecb' and
         plan.get('development') == SEEDS and plan.get('packages') == SIZES and
         plan.get('budgets') == {'jobMillis': 90000, 'jobWork': 640, 'unitMillis': 5000} and
         plan.get('assessmentVersion') == 3, 'profile plan identity/policy differs')
    cases = []
    for path, seed, size in zip(args.captures, SEEDS, SIZES):
        raw = path.read_bytes(); need(len(raw) < 16 * 1024 * 1024, 'capture too large')
        case = check_case(json.loads(raw), seed, size, plan)
        case['captureSha256'] = hashlib.sha256(raw).hexdigest(); cases.append(case)
    result = {'status': 'PASS_CURRENT_COLD_MEDIUM_ASSESSMENT', 'cases': cases,
              'planSha256': hashlib.sha256(args.plan.read_bytes()).hexdigest(),
              'readerSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              'build': plan['build'], 'sourceBinding': plan['sourceBinding'],
              'scope': 'Current declared-program policy; curated profile boundaries, not random population or global probe optimum',
              'protectedHardRegressionRead': False, 'normalReleaseGranted': False}
    args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print('PASS: three current cold MEDIUM assessments, independent reduction/wait/cleanup checks')


if __name__ == '__main__':
    try:
        main()
    except (FEATURES.ReaderError, ValueError, KeyError, OSError, TypeError) as error:
        print('FAIL:', error)
        raise SystemExit(2)
