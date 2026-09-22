"""Validate an actual compiled A10 report; this reader does not execute a solver."""
import argparse
import json
import math
from pathlib import Path

STAGES = ('RESOLVE', 'HEALTHY', 'PHYSICAL', 'HYPOTHESES', 'SYMPTOM', 'PUBLISH')
FAMILIES = ('LED_INDICATOR', 'NMOS_LOW_SIDE_SWITCH', 'controlled-indicator')
NORMAL_BUDGET_SCOPE = 'NORMAL_FROZEN_ATTEMPT'
D01_BUDGET_SCOPE = 'D01_JOB_BUDGET'
# Independently counted current provider programs: 9, 16 and 144 observations/
# input operations, plus nine lifecycle/repair units for each of 3, 3 and 4 hypotheses.
PROOF_UNITS = {'LED_INDICATOR': 54, 'NMOS_LOW_SIDE_SWITCH': 75, 'controlled-indicator': 612}
# The E04 pair is intentionally seed-specific: 0 is direct/linear and 1 is
# hysteretic/averaged.  These are complete compiled proof/cache fixtures, not
# a sampled substitute for either selected family population.
D01_FIXTURES = (
    ('RB15_CONTROL', '0'),
    ('controlled-indicator', '0'),
    ('SENSOR_CONTROL', '0'),
    ('SENSOR_CONTROL', '1'),
)
D01_DIAGNOSTIC_STABLE_FIELDS = (
    'hypotheses', 'observations', 'serialObservations', 'adaptiveObservations',
    'adaptivePopulationObservations', 'repairWitnesses', 'repairs',
    'adaptivePlanDepth', 'adaptivePlanLeaves', 'adaptivePlanNodes',
    'proofStageScope',
)


def require(value, reason):
    if not value:
        raise ValueError(reason)


def integer(value, minimum=0, maximum=None):
    require(type(value) is int and value >= minimum, 'Invalid count/timing')
    require(maximum is None or value <= maximum, 'Budget exceeded')
    return value


def validate_d01_benchmarks(report):
    rows = report.get('d01Benchmarks')
    require(isinstance(rows, list) and len(rows) == len(D01_FIXTURES) * 2,
            'Incomplete D01 benchmark cohort')
    max_job = integer(report.get('maxJobMillis'), 1)
    max_step = integer(report.get('maxStepMillis'), 1)
    max_work = integer(report.get('maxWork'), 1)
    expected = {(family, seed, repeat) for family, seed in D01_FIXTURES
                for repeat in (0, 1)}
    observed = set()
    by_fixture = {}
    for row in rows:
        require(isinstance(row, dict), 'Malformed D01 benchmark row')
        repeat = row.get('repeat')
        require(type(repeat) is int and repeat in (0, 1),
                'Invalid D01 cold/warm repeat')
        key = (row.get('family'), row.get('seed'), repeat)
        require(key in expected and key not in observed,
                'Missing or duplicate D01 fixture')
        observed.add(key)
        by_fixture[(row['family'], row['seed'], repeat)] = row
        require(row.get('corpus') == 'd01' and row.get('outcome') == 'PASS',
                'D01 benchmark did not complete')
        require(row.get('budgetScope') == D01_BUDGET_SCOPE,
                'D01 benchmark has missing or wrong budget scope')
        budget = integer(row.get('benchmarkAttemptMillis'), 1, max_job)
        require(budget == max_job, 'D01 benchmark budget changed')
        elapsed = integer(row.get('elapsedMs'), 0, budget)
        integer(row.get('work'), len(STAGES), max_work)
        integer(row.get('maxAdvanceMs'), 0, max_step)
        require(type(row.get('planCacheHit')) is bool and
                type(row.get('proofCacheHit')) is bool,
                'Invalid D01 cache provenance')
        if repeat == 1:
            require(row.get('planCacheHit') is True and
                    row.get('proofCacheHit') is True,
                    'D01 warm record missed its cache reuse')
        else:
            require(row.get('proofCacheHit') is False,
                    'D01 cold record reused a diagnostic proof')

        stages = row.get('stages')
        require(isinstance(stages, list) and len(stages) == len(STAGES) and
                all(isinstance(stage, dict) for stage in stages) and
                tuple(stage.get('stage') for stage in stages) == STAGES,
                'Missing/duplicate/out-of-order D01 stage evidence')
        stage_by_name = {}
        for stage in stages:
            require(isinstance(stage, dict), 'Malformed D01 stage evidence')
            stage_name = stage.get('stage')
            stage_by_name[stage_name] = stage
            integer(stage.get('elapsedMs'), 0, budget)
            integer(stage.get('work'), 1, max_work)
        require(sum(stage['work'] for stage in stages) == row['work'],
                'D01 stage work does not reconcile')

        diagnostic = row.get('diagnostic')
        require(isinstance(diagnostic, dict), 'Missing D01 benchmark diagnostics')
        hypotheses = integer(diagnostic.get('hypotheses'), 1, 12)
        if row['family'] == 'RB15_CONTROL':
            require(hypotheses == 3, 'RB15 D01 hypothesis population changed')
        elif row['family'] == 'SENSOR_CONTROL':
            require(hypotheses == 3, 'E04 D01 hypothesis population changed')
        else:
            require(hypotheses >= 4, 'Controlled D01 hypothesis population shrank')
        observations = integer(diagnostic.get('observations'), hypotheses,
                               hypotheses * 4096)
        serial_observations = integer(diagnostic.get('serialObservations'), hypotheses,
                                      hypotheses * 4096)
        require(serial_observations == observations,
                'D01 serial observation total does not reconcile')
        if row['family'] == 'RB15_CONTROL':
            require(observations == 27, 'RB15 D01 observation population changed')
        elif row['family'] == 'SENSOR_CONTROL':
            require(observations == 108,
                    'E04 D01 benchmark lost its complete three-condition population')
        else:
            require(observations > 100,
                    'Controlled D01 benchmark lost its complete observation population')
        adaptive_observations = integer(diagnostic.get('adaptiveObservations'), 1,
                                        observations)
        adaptive_population = integer(
            diagnostic.get('adaptivePopulationObservations'), hypotheses, observations)
        require(adaptive_population >= adaptive_observations,
                'D01 adaptive population omits the selected route')

        serial_solves = integer(diagnostic.get('serialSolves'), 0, hypotheses)
        adaptive_solves = integer(diagnostic.get('adaptiveSolves'), 0,
                                  adaptive_observations)
        solves = integer(diagnostic.get('solves'), 0, hypotheses)
        hypothesis_solves = integer(diagnostic.get('hypothesisSolves'), 0, hypotheses)
        expected_serial_solves = 0 if row['proofCacheHit'] else hypotheses
        expected_adaptive_solves = 0
        require(serial_solves == expected_serial_solves and
                adaptive_solves == expected_adaptive_solves and
                hypothesis_solves == expected_serial_solves and
                solves == serial_solves + adaptive_solves,
                'D01 cold/warm solver provenance does not reconcile')
        repair_witnesses = integer(diagnostic.get('repairWitnesses'), 0, hypotheses)
        repairs = integer(diagnostic.get('repairs'), 0, hypotheses)
        require(repair_witnesses == repairs == hypotheses,
                'Every retained D01 hypothesis needs a repair witness')

        for field in ('serialReferenceMs', 'coldProofMs', 'staticPlanOverheadMs',
                      'warmReuseMs'):
            integer(diagnostic.get(field), 0, budget)
        hypothesis_stage_ms = stage_by_name['HYPOTHESES']['elapsedMs']
        if row['proofCacheHit']:
            require(diagnostic.get('proofPath') == 'VALUE_ONLY_REUSE' and
                    diagnostic.get('serialReferenceMs') == 0 and
                    diagnostic.get('coldProofMs') == 0 and
                    diagnostic.get('staticPlanOverheadMs') == 0 and
                    diagnostic.get('warmReuseMs') == hypothesis_stage_ms,
                    'D01 warm record retained serial-proof timing')
        else:
            require(diagnostic.get('proofPath') == 'SERIAL_REFERENCE' and
                    diagnostic.get('serialReferenceMs') >= 0 and
                    diagnostic.get('coldProofMs') == hypothesis_stage_ms and
                    diagnostic.get('coldProofMs') >= diagnostic.get('serialReferenceMs') and
                    diagnostic.get('coldProofMs') == diagnostic.get('serialReferenceMs') +
                    diagnostic.get('staticPlanOverheadMs') and
                    diagnostic.get('warmReuseMs') == 0,
                    'D01 cold record lost serial-proof/static-plan provenance')
        require(diagnostic.get('proofStageScope') ==
                'SERIAL_REFERENCE_PLUS_STATIC_ADAPTIVE_PLAN',
                'D01 proof scope changed')

        depth = integer(diagnostic.get('adaptivePlanDepth'), 0, observations)
        leaves = integer(diagnostic.get('adaptivePlanLeaves'), 1, hypotheses)
        nodes = integer(diagnostic.get('adaptivePlanNodes'), leaves, 4096)
        require(depth < nodes, 'D01 adaptive plan depth exceeds its node graph')
        for field in D01_DIAGNOSTIC_STABLE_FIELDS:
            require(field in diagnostic, 'Missing D01 diagnostic provenance: ' + field)
        # The emitted elapsed value is deliberately only bounded above.  Cold
        # and warm wall-clock timings are not a performance oracle.
        require(elapsed >= 0, 'Negative D01 elapsed time')

    require(observed == expected, 'Missing or unexpected D01 benchmark manifest')
    for family, seed in D01_FIXTURES:
        cold = by_fixture[(family, seed, 0)]
        warm = by_fixture[(family, seed, 1)]
        cold_diagnostic = cold['diagnostic']
        warm_diagnostic = warm['diagnostic']
        for field in D01_DIAGNOSTIC_STABLE_FIELDS:
            require(cold_diagnostic.get(field) == warm_diagnostic.get(field),
                    'D01 warm reuse changed retained diagnostic evidence')


def validate(report):
    require(report.get('protocol') == 'TSJ-A10-GENERATION-1', 'Unknown protocol')
    require(report.get('status') == report.get('cleanup') == 'PASS', 'Incomplete qualification/cleanup')
    integer(report.get('assertions'), 80)
    require(report.get('cancellations') == 6, 'Missing cancellation boundaries')
    require(report.get('unitCancellations') == 18, 'Missing private hypothesis operation cancellation boundaries')
    require(report.get('rejected') == 5, 'Missing input/scenario/publication negatives')
    require(report.get('lifecycleFailures') == 12, 'Missing preparation/scope/Quick Play/cleanup boundaries')
    integer(report.get('cancellationMaxMs'), 0, 500)
    require(report.get('retiredUiReferences') is True, 'Missing retired scope/UI ownership check')
    require(report.get('sessionExclusivity') is True, 'Missing yielded/pending proof exclusivity checks')
    require(report.get('maxJobMillis') == 90000 and report.get('maxStepMillis') == 5000 and
            report.get('maxWork') == 640 and report.get('benchmarkAttemptMillis') == 5000,
            'Budget changed')
    temporal = report.get('temporalRegression', {})
    cleanup = report.get('observationCleanup', {})
    integer(cleanup.get('assertions'), 14)
    require(all(cleanup.get(key) is True for key in ('cursorRetry', 'closedSessionRetry',
            'retainedObservation', 'successorUntouched', 'leaseRetained', 'privateGraphDisposed')),
            'Missing observation cleanup failure/retry proof')
    work = report.get('temporalWork', {})
    integer(work.get('assertions'), 100)
    require(work.get('comparedProfiles') == 2 and work.get('phases') == 4 and
            work.get('cancelBoundaries') == work.get('staleGraphBoundaries') ==
            work.get('sourceChangeBoundaries') == 4 and
            work.get('stagedAbortBoundaries') == 9 and work.get('abortedSourcesDisconnected') is True and
            work.get('exactStateSamplesEventsCounters') is True and
            work.get('partialPublicationRejected') is True and work.get('repairPowerGuard') is True,
            'Missing independent RC phase or cancellation proof')
    require(report.get('temporalBatch') == {'fixedAndAdaptive': True,
            'exactStateAndEvents': True, 'rcOracle': True, 'measurementCounters': True,
            'stoppedStep': True}, 'Missing actual serial/batch RC oracle')
    require(temporal.get('family') == 'RC_DELAY' and temporal.get('seed') == '0' and
            temporal.get('firstOutcome') == 'PASS' and temporal.get('dependenciesEqual') is True and
            temporal.get('changedReferenceRejected') is True and
            temporal.get('profileBoundaryCancelled') is True, 'Missing RC regression/reference/cancellation checks')
    for key in ('firstMs', 'repeatMs'):
        integer(temporal.get(key), 0, 90000)
    for key in ('firstMaxAdvanceMs', 'repeatMaxAdvanceMs'):
        integer(temporal.get(key), 0, 5000)
    browser = report.get('browser', {})
    viewport = browser.get('viewport', {})
    integer(viewport.get('width'), 1)
    integer(viewport.get('height'), 1)
    ratio = browser.get('devicePixelRatio')
    require(type(ratio) in (int, float) and math.isfinite(ratio) and ratio > 0, 'Missing pixel ratio')
    require(isinstance(browser.get('userAgent'), str) and browser['userAgent'], 'Missing browser version')
    memory = browser.get('memory', {})
    require(memory.get('status') in ('AVAILABLE', 'UNAVAILABLE'), 'Missing memory qualification')
    if memory['status'] == 'AVAILABLE':
        for field in ('usedJSHeapSize', 'totalJSHeapSize', 'jsHeapSizeLimit'):
            integer(memory.get(field), 1)
    else:
        require(bool(memory.get('reason')), 'Missing memory limitation')
    rows = report.get('attempts')
    require(isinstance(rows, list) and len(rows) == 24, 'Incomplete frozen corpus')
    normal_budget = report.get('benchmarkAttemptMillis')
    observed = set()
    holdout_started = False
    for row in rows:
        key = (row.get('family'), row.get('seed'), row.get('repeat'))
        require(key not in observed, 'Duplicate attempt')
        observed.add(key)
        require(row.get('outcome') == 'PASS', 'Failed performance attempt')
        require(row.get('budgetScope') == NORMAL_BUDGET_SCOPE,
                'Normal attempt has missing or wrong budget scope')
        row_budget = integer(row.get('benchmarkAttemptMillis'), 1, 5000)
        require(row_budget == normal_budget,
                'Normal attempt budget does not match the frozen report budget')
        integer(row.get('elapsedMs'), 0, 5000)
        integer(row.get('work'), 6, 640)
        integer(row.get('maxAdvanceMs'), 0, 5000)
        require(type(row.get('planCacheHit')) is bool and
                type(row.get('proofCacheHit')) is bool, 'Invalid cache provenance')
        if row.get('repeat') == 1:
            require(row.get('planCacheHit') is True, 'Immediate repeat missed its immutable plan')
            require(row.get('proofCacheHit') is True,
                    'Immediate repeat missed its value-only diagnostic proof')
        else:
            require(row.get('proofCacheHit') is False,
                    'Cold D01 attempt unexpectedly reused a proof')
        diagnostic = row.get('diagnostic')
        require(isinstance(diagnostic, dict), 'Missing D01 diagnostic work evidence')
        hypotheses = integer(diagnostic.get('hypotheses'), 1)
        observations = integer(diagnostic.get('observations'), hypotheses)
        repair_witnesses = integer(diagnostic.get('repairWitnesses'), hypotheses)
        require(repair_witnesses == hypotheses,
                'Each retained D01 hypothesis needs a repair witness')
        expected_solves = 0 if row.get('proofCacheHit') else hypotheses
        serial_solves = integer(diagnostic.get('serialSolves'), 0, hypotheses)
        require(serial_solves == expected_solves and
                diagnostic.get('hypothesisSolves') == expected_solves,
                'D01 cold/warm solver-work provenance changed')
        require(row.get('corpus') == ('pilot' if row.get('seed') in ('0', '1') else 'holdout'),
                'Corpus label changed')
        if row.get('corpus') == 'holdout':
            holdout_started = True
        require(not holdout_started or row.get('corpus') == 'holdout', 'Pilot followed holdout')
        stages = row.get('stages')
        require(isinstance(stages, list) and tuple(s.get('stage') for s in stages) == STAGES,
                'Missing/duplicate/out-of-order stage evidence')
        for stage in stages:
            integer(stage.get('elapsedMs'), 0, 5000)
            integer(stage.get('work'), 1, 640)
            if stage['stage'] == 'HYPOTHESES' and row.get('proofCacheHit'):
                require(stage['work'] == 1,
                        'Warm D01 receipt did not report its one bounded reuse unit')
            elif stage['stage'] == 'HEALTHY' and row.get('family') == 'controlled-indicator':
                # Physical planning now advances in bounded turns before assembly.
                # The actual proof population and the whole-job640 ceiling remain fixed.
                integer(stage['work'], 1, 640 - PROOF_UNITS['controlled-indicator'] - 4)
            else:
                require(stage['work'] == (PROOF_UNITS.get(row.get('family'))
                        if stage['stage'] == 'HYPOTHESES' else 1), 'Current operation population changed')
        require(sum(s['work'] for s in stages) == row['work'], 'Stage work does not reconcile')
        for field in ('serialReferenceMs', 'coldProofMs', 'staticPlanOverheadMs',
                      'warmReuseMs'):
            integer(diagnostic.get(field), 0, 5000)
        hypothesis_stage_ms = next(stage['elapsedMs'] for stage in stages
                                   if stage['stage'] == 'HYPOTHESES')
        if row['proofCacheHit']:
            require(diagnostic.get('proofPath') == 'VALUE_ONLY_REUSE' and
                    diagnostic.get('serialReferenceMs') == 0 and
                    diagnostic.get('coldProofMs') == 0 and
                    diagnostic.get('staticPlanOverheadMs') == 0 and
                    diagnostic.get('warmReuseMs') == hypothesis_stage_ms,
                    'Normal warm record retained serial-proof timing')
        else:
            require(diagnostic.get('proofPath') == 'SERIAL_REFERENCE' and
                    diagnostic.get('coldProofMs') == hypothesis_stage_ms and
                    diagnostic.get('coldProofMs') >= diagnostic.get('serialReferenceMs') and
                    diagnostic.get('coldProofMs') == diagnostic.get('serialReferenceMs') +
                    diagnostic.get('staticPlanOverheadMs') and
                    diagnostic.get('warmReuseMs') == 0,
                    'Normal cold record lost serial-proof/static-plan provenance')
    require(observed == {(f, str(s), r) for f in FAMILIES for s in range(4) for r in range(2)},
            'Missing or unexpected frozen manifest')
    validate_d01_benchmarks(report)
    result = {'status': 'PASS', 'attempts': len(rows), 'cancellationMaxMs': report['cancellationMaxMs']}
    for repeat, label in ((0, 'first'), (1, 'immediateRepeat')):
        values = sorted(row['elapsedMs'] for row in rows if row['repeat'] == repeat)
        result[label] = {'p50Ms': values[math.ceil(len(values) * .5) - 1],
                         'p95Ms': values[math.ceil(len(values) * .95) - 1], 'worstMs': max(values)}
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(validate(json.loads(args.report.read_text(encoding='utf-8-sig'))), indent=2))
