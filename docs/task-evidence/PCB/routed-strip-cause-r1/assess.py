"""Bounded observer-schema/selected-binding reader; not a transformed-board oracle."""
import json
import re
import sys
from pathlib import Path
import final_rows
HERE = Path(__file__).resolve().parent
ROOTS = final_rows.ROOTS
EVENT_PREFIX = 'PCB_ROUTED_STRIP_EVENT '
BINDING_PREFIX = 'PCB_ROUTED_STRIP_BINDING '
STATUSES = {'NO_CUT', 'APPLIED', 'PLACEMENT_REJECTED', 'QUALITY_REJECTED', 'PENDING'}


def digest(value, nullable=False):
    assert nullable and value is None or type(value) is str and re.fullmatch('[a-f0-9]{64}', value), 'Invalid signature'


def integer(value, lower, upper):
    assert type(value) is int and lower <= value <= upper, 'Invalid bounded integer'


def assess(rows, events, bindings):
    final_checks = final_rows.compare(rows)
    assert type(events) is list and type(bindings) is list
    assert [b['seed'] for b in bindings] == ROOTS, 'Missing/duplicate/reordered binding'
    assert all(e['seed'] in ROOTS for e in events), 'Unexpected event root'
    assert len(events) <= 36, 'Event cap exceeded'
    findings = []
    for seed, binding in zip(ROOTS, bindings):
        assert binding['schema'] == 'PCB_ROUTED_STRIP_BINDING@1'
        digest(binding['selectedSignature'])
        assert type(binding['truncated']) is bool
        assert binding['status'] in {'MATCHED', 'AMBIGUOUS', 'NO_MATCH', 'INCONCLUSIVE'}
        group = [e for e in events if e['seed'] == seed]
        calls = [e for e in group if e['kind'] == 'CALL']
        axes = [e for e in group if e['kind'] == 'AXIS']
        assert len(group) == len(calls) + len(axes), 'Unknown event kind'
        assert 1 <= len(calls) <= 6 and len(axes) <= 12
        assert sorted(c['callOrdinal'] for c in calls) == list(range(1, len(calls) + 1)), 'Missing/duplicate/noncanonical call ordinal'
        assert all(a['callOrdinal'] in range(1, len(calls) + 1) for a in axes), 'Orphan axis'
        uncertain = binding['truncated']
        causes = {}
        for call in calls:
            ordinal = call['callOrdinal']
            integer(ordinal, 1, 6)
            assert call['schema'] == 'PCB_ROUTED_STRIP_EVENT@1'
            assert call['status'] in {'COMPLETE', 'INCOMPLETE'} and type(call['truncated']) is bool
            digest(call['inputSignature'])
            digest(call['outputSignature'], nullable=True)
            chain = sorted([a for a in axes if a['callOrdinal'] == ordinal], key=lambda a: a['axisOrdinal'])
            integer(call['axisCount'], 0, 2)
            assert len(chain) == call['axisCount']
            assert [a['axisOrdinal'] for a in chain] == list(range(len(chain))), 'Missing/duplicate/reordered axis'
            if call['status'] == 'COMPLETE':
                assert call['axisCount'] == 2
                digest(call['outputSignature'])
            else:
                uncertain = True
            uncertain |= call['truncated']
            previous = call['inputSignature']
            rejected = []
            for axis in chain:
                assert axis['schema'] == 'PCB_ROUTED_STRIP_EVENT@1'
                integer(axis['axisOrdinal'], 0, 1)
                assert axis['axis'] == ('X' if axis['axisOrdinal'] == 0 else 'Y')
                assert axis['status'] in STATUSES
                integer(axis['occupiedIntervalCount'], 1, 2**31 - 1)
                integer(axis['cutCount'], 0, 2**31 - 1)
                assert type(axis['cutsTruncated']) is bool and type(axis['cuts']) is list
                assert len(axis['cuts']) == min(axis['cutCount'], 256)
                assert axis['cutsTruncated'] == (axis['cutCount'] > 256)
                for cut in axis['cuts']:
                    assert type(cut) is list and len(cut) == 2
                    integer(cut[0], -(2**31), 2**31 - 1)
                    integer(cut[1], 10, 2**31 - 1)
                    assert cut[1] % 10 == 0
                assert [c[0] for c in axis['cuts']] == sorted(set(c[0] for c in axis['cuts'])), 'Noncanonical cut order'
                digest(axis['inputSignature'])
                digest(axis['proposedSignature'], nullable=True)
                digest(axis['outputSignature'], nullable=True)
                assert axis['inputSignature'] == previous, 'Broken axis input chain'
                status = axis['status']
                if status == 'NO_CUT':
                    assert axis['cutCount'] == 0 and axis['proposedSignature'] is None
                    assert axis['outputSignature'] == axis['inputSignature']
                elif status == 'PENDING':
                    uncertain = True
                    assert call['status'] == 'INCOMPLETE'
                else:
                    assert axis['cutCount'] > 0
                    digest(axis['proposedSignature'])
                    digest(axis['outputSignature'])
                    if status == 'APPLIED':
                        assert axis['outputSignature'] == axis['proposedSignature']
                    else:
                        assert axis['outputSignature'] == axis['inputSignature']
                        expected_type = 'com.lushprojects.circuitjs1.client.' + (
                            'PcbPlacementPlanner$Rejected' if status == 'PLACEMENT_REJECTED' else 'PcbBoardLayout$RouteQualityRejectedException')
                        assert axis['caughtType'] == expected_type and type(axis['caughtMessage']) is str and axis['caughtMessage']
                        rejected.append({'axis': axis['axis'], 'status': status,
                            'caughtType': axis['caughtType'], 'caughtMessage': axis['caughtMessage'],
                            'proposedCutCount': axis['cutCount'],
                            'storedProposedRemoval': sum(c[1] for c in axis['cuts'])})
                if status not in {'PLACEMENT_REJECTED', 'QUALITY_REJECTED'}:
                    assert axis['caughtType'] is None and axis['caughtMessage'] is None
                uncertain |= axis['cutsTruncated']
                previous = axis['outputSignature']
            if call['status'] == 'COMPLETE':
                assert previous == call['outputSignature'], 'Broken completed output chain'
            causes[ordinal] = rejected
        matches = sorted(c['callOrdinal'] for c in calls if c['status'] == 'COMPLETE' and c['outputSignature'] == binding['selectedSignature'])
        incomplete = sorted(c['callOrdinal'] for c in calls if c['status'] == 'INCOMPLETE')
        for values in (binding['matchingCallOrdinals'], binding['incompleteCallOrdinals']):
            assert type(values) is list and all(type(v) is int for v in values)
            assert values == sorted(set(values))
        assert binding['matchingCallOrdinals'] == matches and binding['incompleteCallOrdinals'] == incomplete, 'Fabricated selected/incomplete binding'
        if binding['status'] == 'MATCHED':
            assert len(matches) == 1 and not binding['truncated'] and not incomplete
        elif binding['status'] == 'AMBIGUOUS':
            assert len(matches) > 1
        elif binding['status'] == 'NO_MATCH':
            assert not matches
        uncertain |= binding['status'] != 'MATCHED' or len(matches) != 1
        findings.append({'seed': seed, 'status': 'INCONCLUSIVE' if uncertain else 'PASS',
            'matchingCallOrdinals': matches, 'recordedCalls': len(calls), 'recordedAxes': len(axes),
            'selectedRejectedAxes': [r for ordinal in matches for r in causes[ordinal]],
            'cause': 'INCONCLUSIVE' if uncertain else ('CAUGHT_AXIS_REJECTION' if causes[matches[0]] else 'NO_CAUGHT_AXIS_REJECTION'),
            'scope': 'Observed selected compaction decisions only; no transformed-board, requested-attempt, minimal-layout or population proof'})
    return {'status': 'INCONCLUSIVE' if any(f['status'] == 'INCONCLUSIVE' for f in findings) else 'PASS',
        'finalAccepted15Reproduction': final_checks, 'findings': findings, 'generationPolicyChanged': False}


def extract(text, prefix):
    return [json.loads(line[len(prefix):]) for line in text.splitlines() if line.startswith(prefix)]


if __name__ == '__main__':
    try:
        text = Path(sys.argv[1]).read_text(encoding='utf-8-sig')
        result = assess(final_rows.extract(text), extract(text, EVENT_PREFIX), extract(text, BINDING_PREFIX))
    except (AssertionError, KeyError, TypeError, ValueError) as error:
        result = {'status': 'FAIL', 'phase': 'reader-validation', 'error': str(error)}
    Path(sys.argv[2]).write_text(json.dumps(result, indent=2) + '\n')
    print(result['status'] + ': selected routed-strip cause reader')
    raise SystemExit({'PASS': 0, 'FAIL': 1, 'INCONCLUSIVE': 2}[result['status']])
