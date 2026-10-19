"""Render native geometry receipts. Developer diagnostics, never player assets."""
import argparse
import html
import json
from pathlib import Path

PACKET = Path(__file__).resolve().parent


def records(text, prefix):
    return [json.loads(line[len(prefix):]) for line in text.splitlines() if line.startswith(prefix)]


def rect(box, **attrs):
    values = dict(zip(('x', 'y', 'width', 'height'), box), **attrs)
    return '<rect ' + ' '.join(f'{key.replace("_", "-")}="{html.escape(str(value), quote=True)}"' for key, value in values.items()) + '/>'


def svg(case, whitespace, detail=False):
    void = whitespace['largestProtectedEmptyRectangle']
    box = [void[key] for key in ('x', 'y', 'width', 'height')]
    outline = case['outline']
    view = ([box[0]-100, box[1]-100, box[2]+200, box[3]+200] if detail else outline)
    elements = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="' + ' '.join(map(str, view)) + '" width="1500" height="900">',
                rect(outline, fill='#f8fafc', stroke='#111827', stroke_width=4)]
    for item in case['domainUnions']:
        color = '#be185d' if item['domain'] == 'PRIMARY' else '#0369a1'
        elements.append(rect(item['rect'], fill='none', stroke=color, stroke_width=5, stroke_dasharray='20 10'))
    for item in case['barrierReservedStrips']:
        elements.append(rect(item['rect'], fill='#f97316', fill_opacity='.32', stroke='#c2410c', stroke_width=3))
    # Show the exact conservative rectangles consumed by the whitespace metric,
    # separately from the narrower physical copper and via lands below.
    for trace in case['traces']:
        guard = trace['whitespaceGuard']
        color = '#ef4444' if trace['layer'] == 'TOP' else '#3b82f6'
        for (x0,y0),(x1,y1) in zip(trace['points'],trace['points'][1:]):
            elements.append(rect([min(x0,x1)-guard,min(y0,y1)-guard,
                                  abs(x1-x0)+2*guard,abs(y1-y0)+2*guard],
                                 fill=color,fill_opacity='.12'))
    for via in case['vias']:
        x,y,w,h = via['bounds']
        guard = via['whitespaceGuard']
        elements.append(rect([x-guard,y-guard,w+2*guard,h+2*guard],fill='#a16207',fill_opacity='.16'))
    for part in case['packages']:
        elements.append(rect(part['packageAccess'], fill='#e2e8f0', fill_opacity='.6', stroke='#64748b', stroke_width=2))
        elements.append(rect(part['routingCourtyard'], fill='none', stroke='#94a3b8', stroke_width=1))
        for domain in part['domainCourtyards']:
            elements.append(rect(domain['rect'], fill='none', stroke='#be185d' if domain['domain']=='PRIMARY' else '#0369a1', stroke_width=2))
    for trace in case['traces']:
        points = ' '.join(f'{x},{y}' for x, y in trace['points'])
        color = '#b91c1c' if trace['layer'] == 'TOP' else '#2563eb'
        elements.append(f'<polyline fill="none" stroke-linejoin="round" stroke-linecap="round" points="{points}" stroke="{color}" stroke-width="{trace["width"]}" opacity=".72"/>')
    for via in case['vias']:
        elements.append(rect(via['bounds'], fill='#a16207', stroke='#422006', stroke_width=1))
    for part in case['packages']:
        for pad in part['pads']:
            elements.append(rect(pad['bounds'], fill='#d4a72c', stroke='#713f12', stroke_width=1))
        x, y, _, _ = part['routingCourtyard']
        elements.append(f'<text x="{x}" y="{y-4}" font-family="sans-serif" font-size="15" fill="#101e29">{html.escape(part["componentId"])}</text>')
    for label in case['silk']:
        elements.append(rect(label['bounds'], fill='none', stroke='#475569', stroke_width=1, stroke_dasharray='3 3'))
    elements.append(rect(box, fill='#22c55e', fill_opacity='.15', stroke='#15803d', stroke_width=5, stroke_dasharray='15 8'))
    elements.append('</svg>')
    return '\n'.join(elements)


def projection_witnesses(case, void):
    """Existing package access alone can obstruct removal of a whole-axis strip.

    These witnesses explain the compactor's limitation, not whether moving a
    package or rerouting could reclaim the pocket.
    """
    result = {}
    for axis, start_key, extent_key in ((0, 'x', 'width'), (1, 'y', 'height')):
        start, end = void[start_key], void[start_key] + void[extent_key]
        witnesses = []
        for part in case['packages']:
            box = part['packageAccess']
            overlap = min(end, box[axis] + box[axis+2]) - max(start, box[axis])
            if overlap > 0:
                witnesses.append({'componentId':part['componentId'], 'packageAccess':box,
                                  'projectionOverlap':overlap})
        result[start_key] = witnesses
    return result


def boundary_witnesses(case, void):
    """Attribute neighboring GRID10 cells to every matching exported obstacle.

    This preserves the existing metric's deliberately conservative inclusive
    last-cell convention. It is not an exact positive-area package-fit oracle.
    """
    ox, oy, width, height = case['outline']
    cols, rows = width // 10, height // 10
    left, top = (void['x']-ox)//10, (void['y']-oy)//10
    right, bottom = left+void['width']//10-1, top+void['height']//10-1
    obstacles = []
    for part in case['packages']:
        obstacles.append(('PACKAGE_ACCESS', part['componentId'], part['packageAccess']))
    for trace in case['traces']:
        guard = trace['whitespaceGuard']
        for index, ((x0, y0), (x1, y1)) in enumerate(zip(trace['points'], trace['points'][1:])):
            obstacles.append(('COPPER_'+trace['layer'], trace['sourceId']+'/'+str(index),
                [min(x0,x1)-guard, min(y0,y1)-guard, abs(x1-x0)+2*guard, abs(y1-y0)+2*guard]))
    for via in case['vias']:
        x, y, w, h = via['bounds']
        guard = via['whitespaceGuard']
        obstacles.append(('VIA', via['id'], [x-guard,y-guard,w+2*guard,h+2*guard]))
    for label in case['silk']:
        obstacles.append(('SILK', label['id'], label['bounds']))
    for strip in case['barrierReservedStrips']:
        obstacles.append(('ISOLATION', strip['firstDomain']+'/'+strip['secondDomain'], strip['rect']))
    cells = {'left':[(left-1,y) for y in range(top,bottom+1)],
             'right':[(right+1,y) for y in range(top,bottom+1)],
             'top':[(x,top-1) for x in range(left,right+1)],
             'bottom':[(x,bottom+1) for x in range(left,right+1)]}
    matches = {side:[] for side in cells}
    for kind, source, box in obstacles:
        x, y, w, h = box
        x0,y0 = max(0,(x-ox)//10),max(0,(y-oy)//10)
        x1,y1 = min(cols-1,(x+w-ox+9)//10),min(rows-1,(y+h-oy+9)//10)
        if max(left,x0)<=min(right,x1) and max(top,y0)<=min(bottom,y1):
            raise ValueError('Reported free rectangle overlaps protected sampled obstacle: '+source)
        for side, border in cells.items():
            count = sum(x0<=cx<=x1 and y0<=cy<=y1 for cx,cy in border)
            if count:
                matches[side].append({'kind':kind,'sourceId':source,'guardedRect':box,'adjacentCells':count})
    return {'obstacleMatches':matches, 'outsideSampledGrid':{
        side:sum(not(0<=x<cols and 0<=y<rows) for x,y in border) for side,border in cells.items()},
        'scope':'All matching records retained; neighboring sampled cells explain metric boundaries, not placement or routing feasibility'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rasterize', action='store_true', help='Also render local PNGs with installed PyMuPDF; no browser or network')
    args = parser.parse_args()
    log = (PACKET / 'native.txt').read_text(encoding='utf-8-sig')
    cases = records(log, 'PCB_OCCUPANCY_DIAGNOSTIC ')
    whitespace = {item['seed']: item for item in records(log, 'PCB_WHITESPACE_CASE ')}
    if sorted(item['seed'] for item in cases) != ['-1022', '42']:
        raise ValueError('Expected exactly the two predeclared roots')
    expected = {'42': ('df556b8d89f1f91e15912f99af9238cd99368ce19e1929e1707b09c97758598a', 178200, 1),
                '-1022': ('b9d98e2a5d1b255fd2fb1430688d90d37e89737051d59363f931d9a326fb6d71', 729600, 0)}
    summaries = []
    pages = []
    for case in cases:
        seed = case['seed']
        digest, area, candidate = expected[seed]
        if case['geometryFingerprintBefore'] != digest or case['geometryFingerprintAfter'] != digest:
            raise ValueError('Geometry differs from accepted13 or diagnostic mutated it: ' + seed)
        if case['placementVersion'] != 13 or case['selectedPlacementAttempt'] != candidate:
            raise ValueError('Unexpected placement identity: ' + seed)
        void = whitespace[seed]['largestProtectedEmptyRectangle']
        if void['area'] != area:
            raise ValueError('Existing protected whitespace receipt changed: ' + seed)
        (PACKET / f'geometry-{seed}.json').write_text(json.dumps(case, indent=2)+'\n', encoding='utf-8')
        for detail in (False, True):
            name = f'root-{seed}-' + ('pocket' if detail else 'full') + '.svg'
            (PACKET / name).write_text(svg(case, whitespace[seed], detail)+'\n', encoding='utf-8')
            if args.rasterize:
                import fitz
                with fitz.open(stream=(PACKET / name).read_bytes(), filetype='svg') as document:
                    document[0].get_pixmap().save((PACKET / name).with_suffix('.png'))
            pages.append(f'<section><h2>Root {seed}, candidate {candidate}: {"pocket detail" if detail else "whole board"}</h2><img src="{name}" alt="Authoritative domain, access and copper geometry for root {seed}"/></section>')
        summaries.append(dict(seed=seed, selectedPlacementAttempt=candidate, geometrySha256=digest,
                              domainUnions=case['domainUnions'], barrierReservedStrips=case['barrierReservedStrips'],
                              largestProtectedEmptyRectangle=void,
                              packageProjectionWitnesses=projection_witnesses(case, void),
                              sampledBoundaryWitnesses=boundary_witnesses(case, void),
                              packages=len(case['packages']), traces=len(case['traces']), vias=len(case['vias'])))
    (PACKET / 'geometry-assessment.json').write_text(json.dumps({'status':'PASS', 'cases':summaries,
        'limits':'Actual drawing geometry and existing conservative GRID10 metric. Domain unions are bounding boxes, not allowed placement regions. Empty rectangle is not proof any package fits.'}, indent=2)+'\n', encoding='utf-8')
    page = '''<!doctype html><html lang="en"><meta charset="utf-8"><title>PCB occupancy cause audit</title>
<style>body{font:16px system-ui;color:#172033;background:#eef2f6;margin:24px}h1{margin:0}p{max-width:1000px;line-height:1.5}section{background:white;border:1px solid #cbd5e1;padding:18px;margin:24px 0}h2{margin:0 0 12px}img{display:block;width:100%;height:auto;max-height:75vh;object-fit:contain}.legend{display:flex;gap:20px;flex-wrap:wrap}.legend span{border-left:8px solid var(--c);padding-left:8px}</style>
<h1>PCB occupancy cause audit</h1><p>Developer evidence for accepted private13. Geometry is unchanged. Dashed domain unions are bounds of actual conductive courtyards, not allowed-placement rectangles. Green marks the existing conservative GRID10 largest protected empty rectangle; it does not prove a package can fit.</p>
<div class="legend"><span style="--c:#be185d">PRIMARY courtyards</span><span style="--c:#0369a1">SECONDARY courtyards</span><span style="--c:#f97316">Full-board isolation strip</span><span style="--c:#64748b">Package + access</span><span style="--c:#b91c1c">Top copper</span><span style="--c:#2563eb">Bottom copper</span><span style="--c:#a16207">Faint copper/via clearance rectangles</span><span style="--c:#15803d">Largest sampled pocket</span></div>
''' + '\n'.join(pages) + '</html>\n'
    (PACKET / 'index.html').write_text(page, encoding='utf-8')
    print('PASS: two unchanged accepted13 geometry overlays and pocket details rendered')


if __name__ == '__main__':
    main()
