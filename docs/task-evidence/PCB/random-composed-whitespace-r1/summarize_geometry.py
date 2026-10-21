"""Describe envelope extents from a qualified receipt; never propose a legal move."""
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent


def read(p):
    return json.loads(p.read_text(encoding='utf-8-sig'))


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def merged(intervals):
    result = []
    for start, end in sorted(intervals):
        if not result or start > result[-1][1]:
            result.append([start, end])
        else:
            result[-1][1] = max(result[-1][1], end)
    return result


def union_area(rectangles):
    xs = sorted({v for x, y, w, h in rectangles for v in (x, x + w)})
    return sum((b - a) * sum(d - c for c, d in merged(
        [(y, y + h) for x, y, w, h in rectangles if x <= a and x + w >= b]))
        for a, b in zip(xs, xs[1:]))


def describe(components, key, outline):
    rectangles = [p[key] for p in components]
    x = min(r[0] for r in rectangles)
    y = min(r[1] for r in rectangles)
    right = max(r[0] + r[2] for r in rectangles)
    bottom = max(r[1] + r[3] for r in rectangles)
    extremes = {
        'left': [p['componentId'] for p in components if p[key][0] == x],
        'top': [p['componentId'] for p in components if p[key][1] == y],
        'right': [p['componentId'] for p in components if p[key][0] + p[key][2] == right],
        'bottom': [p['componentId'] for p in components if p[key][1] + p[key][3] == bottom],
    }
    projections = {}
    for axis in (0, 1):
        intervals = merged([(r[axis], r[axis] + r[axis + 2]) for r in rectangles])
        gaps = [[a[1], b[0], b[0] - a[1]] for a, b in zip(intervals, intervals[1:])]
        projections['x' if axis == 0 else 'y'] = {'occupied': intervals, 'internalGaps': gaps,
            'internalGapsOverChannel20': [g for g in gaps if g[2] > 20]}
    return {'boundingRectangle': [x, y, right - x, bottom - y],
            'rectangleUnionArea': union_area(rectangles), 'extremeComponents': extremes,
            'outlineMargins': {'left': x - outline[0], 'top': y - outline[1],
                'right': outline[0] + outline[2] - right,
                'bottom': outline[1] + outline[3] - bottom},
            'projections': projections}


def main():
    acceptance = read(HERE / 'assessment.json')
    assert acceptance['status'] == 'PASS_EXACT_RANDOM_COMPOSED_WHITESPACE_DIAGNOSIS'
    receipt = HERE / 'composed/receipt.txt'
    assert acceptance['result']['receiptSha256'] == sha(receipt)
    prefix = 'PCB_SPARSE_NORMAL_DIAGNOSTIC '
    rows = [json.loads(s[len(prefix):]) for s in receipt.read_text(encoding='utf-8-sig').splitlines()
            if s.startswith(prefix)]
    assert len(rows) == 1
    row = rows[0]
    assert row['replayId'] == acceptance['result']['replayId']
    parts, outline = row['layout']['components'], row['layout']['outline']
    value = {'status': 'DESCRIPTIVE_EXACT_SELECTED_GEOMETRY_ONLY',
        'assessmentSha256': sha(HERE / 'assessment.json'), 'receiptSha256': sha(receipt),
        'replayId': row['replayId'], 'outline': outline, 'physicalMetrics': row['physicalMetrics'],
        'packingProbe': row['packingProbe'],
        'largestProtectedEmptyRectangle': acceptance['result']['whitespaceSampler']['largestProtectedEmptyRectangle'],
        'envelopes': {key: describe(parts, key, outline) for key in
            ('bodyBounds', 'keepOut', 'selectionBounds', 'routingCourtyard', 'accessEnvelope')},
        'anchors': [{k: p[k] for k in ('componentId', 'packageId', 'anchor', 'pose', 'accessEnvelope')}
                    for p in parts if p['anchor'] != 'NONE'],
        'limits': 'Actual drawing units and axis-aligned exported envelopes; rectangle union area is descriptive. '
                  'Projection gaps exclude trace/ink/barrier/access-route guards, so even a gap over20 is not '
                  'a legal strip-removal proof. No movement, reroute, alternate seed, experiment, or optimum claim.'}
    target = HERE / 'geometry-summary.json'
    with target.open('x', encoding='utf-8', newline='\n') as f:
        json.dump(value, f, indent=2)
        f.write('\n')
    print('PASS descriptive envelope extents from the qualified exact receipt')


if __name__ == '__main__':
    main()
