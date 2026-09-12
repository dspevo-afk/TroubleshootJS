"""Validate an actual compiled A10 report; this reader does not execute a solver."""
import argparse
import json
import math
from pathlib import Path

STAGES = ('RESOLVE', 'HEALTHY', 'PHYSICAL', 'HYPOTHESES', 'SYMPTOM', 'PUBLISH')
FAMILIES = ('LED_INDICATOR', 'NMOS_LOW_SIDE_SWITCH', 'controlled-indicator')
# Independently counted current provider programs: 9, 16 and 144 observations/
# input operations, plus nine lifecycle/repair units for each of 3, 3 and 4 hypotheses.
PROOF_UNITS = {'LED_INDICATOR': 54, 'NMOS_LOW_SIDE_SWITCH': 75, 'controlled-indicator': 612}


def require(value, reason):
    if not value:
        raise ValueError(reason)


def integer(value, minimum=0, maximum=None):
    require(type(value) is int and value >= minimum, 'Invalid count/timing')
    require(maximum is None or value <= maximum, 'Budget exceeded')
    return value


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
    observed = set()
    holdout_started = False
    for row in rows:
        key = (row.get('family'), row.get('seed'), row.get('repeat'))
        require(key not in observed, 'Duplicate attempt')
        observed.add(key)
        require(row.get('outcome') == 'PASS', 'Failed performance attempt')
        integer(row.get('elapsedMs'), 0, 5000)
        integer(row.get('work'), 6, 640)
        integer(row.get('maxAdvanceMs'), 0, 5000)
        require(type(row.get('planCacheHit')) is bool and row.get('proofCacheHit') is False,
                'Invalid cache provenance')
        if row.get('repeat') == 1:
            require(row.get('planCacheHit') is True, 'Immediate repeat missed its immutable plan')
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
            require(stage['work'] == (PROOF_UNITS.get(row.get('family'))
                    if stage['stage'] == 'HYPOTHESES' else 1), 'Current operation population changed')
        require(sum(s['work'] for s in stages) == row['work'], 'Stage work does not reconcile')
    require(observed == {(f, str(s), r) for f in FAMILIES for s in range(4) for r in range(2)},
            'Missing or unexpected frozen manifest')
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
