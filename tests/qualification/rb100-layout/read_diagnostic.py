"""Independent transport/census reader for the fixed RB100 placement diagnostic."""
import argparse
import copy
import hashlib
import json
from pathlib import Path

PREFIX = 'RB100_LAYOUT_PLACEMENT_DIAGNOSTIC '
SUMMARY = 'PASS: RB100 layout placement diagnostic contracts '
SCOPE = 'OBSERVATIONAL_FIXED_ATTEMPT_ONLY'
SUBJECTS = ['SHARED_HYSTERETIC_LINEAR', 'SEPARATE_DIRECT_SWITCHING', 'STRUCTURAL_CONTROL']
EPOCH = 'tsj-generation-dependencies-v22'
LAYOUT = 21
REASONS = {'PART_LIMIT', 'CONNECTOR_FACING_OUTWARD', 'DEMAND_LIMIT', 'WIDTH_LIMIT',
           'OUTLINE_BUDGET', 'MIXED_CORRIDOR_ORIENTATION', 'MEDIUM_OUTLINE_LIMIT',
           'MEDIUM_OUTLINE_BUDGET', 'MEDIUM_CONNECTOR_PACKING', 'MEDIUM_COMPONENT_ORDER',
           'MEDIUM_COMPONENT_PACKING', 'MEDIUM_REGION_HINT_NONFINITE',
           'MEDIUM_PLACEMENT_BUDGET', 'MISSING_COMPONENT', 'DUPLICATE_COMPONENT',
           'ENVELOPE_ACCESS_OR_DOMAIN', 'STRANDED_ESCAPE', 'DOMAIN_CORRIDOR'}


def need(value, message):
    if not value:
        raise ValueError(message)


def strict_json(text):
    def pairs(items):
        result = {}
        for key, value in items:
            need(key not in result, 'duplicate JSON key: ' + key)
            result[key] = value
        return result
    def constant(value):
        raise ValueError('nonfinite JSON: ' + value)
    return json.loads(text, object_pairs_hook=pairs, parse_constant=constant)


def integer(value, minimum, maximum):
    return type(value) is int and minimum <= value <= maximum


def validate_witness(witness, required_prune):
    need(type(witness) is dict and all(type(witness.get(name)) is dict for name in
         ['barrier', 'trial', 'trialDomainCourtyards', 'preTrialAggregateBounds',
          'postUnionAggregateBounds', 'signedMargins', 'orientationMasks']),
         'missing immutable corridor witness')
    margin_names = ['firstToSecondXMargin', 'secondToFirstXMargin',
                    'firstToSecondYMargin', 'secondToFirstYMargin']
    for name in margin_names:
        need(integer(witness['signedMargins'].get(name), -(1 << 62), 1 << 62),
             'invalid signed margin type')
    masks = witness['orientationMasks']
    allowed, current = masks.get('appliedAllowed'), masks.get('unconstrainedSeparating')
    need(integer(allowed, 1, 15) and integer(current, 0, 15), 'invalid orientation masks')
    labels = ['firstLeftSecond', 'secondLeftFirst', 'firstAboveSecond', 'secondAboveFirst']
    for key, mask in [('appliedAllowedNames', allowed), ('unconstrainedSeparatingNames', current)]:
        need(masks.get(key) == [label for bit, label in enumerate(labels) if mask & (1 << bit)],
             'orientation names differ from mask')
    need(current & allowed == 0, 'witness does not describe a rejected orientation')
    need(not required_prune or current != 0, 'required prune has no unconstrained orientation')


def validate(cases, summary):
    need(type(cases) is list and len(cases) == 3, 'exact three-fixture census required')
    need(type(summary) is dict and summary.get('status') == 'PASS' and
         summary.get('scope') == SCOPE and summary.get('cases') == 3 and
         integer(summary.get('layoutVersion'), LAYOUT, LAYOUT) and
         summary.get('dependencyEpoch') == EPOCH and
         integer(summary.get('planVersion'), 1, 1) and
         integer(summary.get('modelVersion'), 4, 4) and
         integer(summary.get('physicalIntentVersion'), 4, 4) and
         summary.get('seed') == '-6587958367974952701' and
         integer(summary.get('candidate'), 79, 79), 'diagnostic summary differs')
    for index, case in enumerate(cases):
        need(type(case) is dict and case.get('status') == 'PASS' and
             case.get('scope') == SCOPE and case.get('cleanupStatus') == 'PASS' and
             case.get('failureType') == 'none' and case.get('cleanupFailureType') == 'none' and
             case.get('failure') == '' and case.get('cleanupFailure') == '', 'case status/errors differ')
        for name, minimum, maximum in [('operationMs', 0, 90000), ('cleanupMs', 0, 120000),
                                       ('maxUnitMs', 0, 5000), ('workUnits', 1, 640)]:
            need(integer(case.get(name), minimum, maximum), 'invalid typed budget: ' + name)
        facts = case.get('facts')
        need(type(facts) is dict and facts.get('subject') == SUBJECTS[index], 'fixture order/identity differs')
        need(integer(facts.get('layoutVersion'), LAYOUT, LAYOUT) and
             facts.get('dependencyEpoch') == EPOCH and
             integer(facts.get('planVersion'), 1, 1) and
             integer(facts.get('modelVersion'), 4, 4) and
             integer(facts.get('physicalIntentVersion'), 4, 4), 'current interpretation differs')
        if index < 2:
            need(facts.get('fixture') == 'AUTHORED_RB100' and facts.get('components') == 100 and
                 facts.get('pads') == 246 and facts.get('seed') == '-6587958367974952701' and
                 integer(facts.get('candidate'), 79, 79), 'authored fixed input differs')
            outcome = facts.get('nullOutcome')
            need(outcome == facts.get('observedOutcome') and
                 outcome in {'ACCEPTED'} | {'PLACEMENT_' + reason for reason in REASONS},
                 'authored typed outcome differs')
            snapshot = facts.get('failureSnapshot')
            if outcome == 'ACCEPTED':
                need(snapshot is None and type(facts.get('nullGeometryKey')) is str and
                     facts.get('nullGeometryKey') == facts.get('observedGeometryKey') and
                     facts.get('nullGeometryKey') != 'NOT_AVAILABLE', 'accepted geometry parity differs')
            else:
                need(facts.get('nullGeometryKey') == facts.get('observedGeometryKey') ==
                     'NOT_AVAILABLE', 'rejected placement has geometry')
                if outcome in {'PLACEMENT_MEDIUM_COMPONENT_PACKING', 'PLACEMENT_MEDIUM_CONNECTOR_PACKING'}:
                    need(type(snapshot) is dict, 'missing actual failed-item snapshot')
                if snapshot is not None:
                    need(type(snapshot) is dict and type(snapshot.get('componentId')) is str and
                         snapshot.get('componentId') != '', 'missing actual failed item')
                    for key, required_prune in [('firstGlobalCorridorWitness', False),
                                                ('firstRequiredOrientationPruneWitness', True)]:
                        need(key in snapshot, 'missing witness field')
                        if snapshot[key] is not None:
                            validate_witness(snapshot[key], required_prune)
        else:
            need(facts.get('fixture') == 'MEDIUM_GENERIC_EDGE_REGION_20_PART' and
                 facts.get('seed') == '0' and integer(facts.get('candidate'), 0, 0) and
                 facts.get('components') == 20, 'structural control differs')
            need(facts.get('nullOutcome') == facts.get('observedOutcome') == 'ACCEPTED' and
                 type(facts.get('nullGeometryKey')) is str and
                 facts.get('nullGeometryKey') == facts.get('observedGeometryKey') == facts.get('retryGeometryKey') and
                 facts.get('nullGeometryKey') != 'NOT_AVAILABLE' and
                 facts.get('callbackExceptionPropagated') is True and
                 integer(facts.get('callbackAbortCallbacks'), 1, 1), 'positive/abort/retry facts differ')


def read_receipt(path):
    raw = path.read_bytes()
    need(len(raw) <= 4 * 1024 * 1024, 'receipt exceeds bound')
    lines = raw.decode('utf-8-sig').splitlines()
    cases = [strict_json(line[len(PREFIX):]) for line in lines if line.startswith(PREFIX)]
    summaries = [strict_json(line[len(SUMMARY):]) for line in lines if line.startswith(SUMMARY)]
    need(len(summaries) == 1, 'exact one terminal diagnostic summary required')
    validate(cases, summaries[0])
    return cases, summaries[0], hashlib.sha256(raw).hexdigest()


def negative_canaries(cases, summary, rejected):
    count = 0
    mutations = [lambda rows: rows.pop(), lambda rows: rows.__setitem__(1, copy.deepcopy(rows[0])),
                 lambda rows: rows[0].__setitem__('operationMs', True),
                 lambda rows: rows[0].__setitem__('cleanupStatus', 'FAIL'),
                 lambda rows: rows[2]['facts'].__setitem__('callbackExceptionPropagated', 1),
                 lambda rows: rows[0]['facts'].__setitem__('nullOutcome', 'PLACEMENT_UNKNOWN'),
                 lambda rows: rows[0]['facts'].__setitem__('layoutVersion', 20),
                 lambda rows: rows[0]['facts'].__setitem__('dependencyEpoch', 'tsj-generation-dependencies-v21'),
                 lambda rows: rows[0]['facts'].__setitem__('physicalIntentVersion', 1),
                 lambda rows: rows[0]['facts'].__setitem__('nullGeometryKey', 'INVALID')]
    for mutate in mutations:
        rows = copy.deepcopy(cases)
        mutate(rows)
        try:
            validate(rows, summary)
        except ValueError:
            count += 1
        else:
            raise ValueError('malformed fixture was accepted')
    for text in ['{"status":"PASS","status":"FAIL"}', '{"x":NaN}']:
        try:
            strict_json(text)
        except ValueError:
            count += 1
        else:
            raise ValueError('invalid JSON fixture accepted')
    for rejected_path in rejected:
        try:
            read_receipt(rejected_path)
        except (ValueError, UnicodeError):
            count += 1
        else:
            raise ValueError('actual incompatible receipt was accepted')
    return count


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--receipt', type=Path, required=True)
    parser.add_argument('--rejected-receipt', type=Path, action='append', default=[])
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    need(not args.output.exists(), 'refusing existing output')
    cases, summary, digest = read_receipt(args.receipt)
    checked = negative_canaries(cases, summary, args.rejected_receipt)
    result = {'protocol': 'rb100-placement-diagnostic-reader-v2', 'status': 'PASS_OBSERVATION_ONLY',
              'scope': SCOPE, 'layoutQualification': False, 'receiptSha256': digest,
              'readerSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              'negativeCanaries': checked, 'summary': summary, 'cases': cases}
    with args.output.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(result, indent=2) + '\n')
    print('PASS: actual three-fixture diagnostic transport; negative canaries=' + str(checked) +
          '; layout qualification=false')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, UnicodeError, TypeError, KeyError) as failure:
        print('FAIL:', failure)
        raise SystemExit(2)
