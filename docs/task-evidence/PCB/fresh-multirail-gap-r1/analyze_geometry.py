"""Developer geometry views of the accepted selected export; no placement changes."""
import hashlib,html,json
from pathlib import Path
HERE=Path(__file__).resolve().parent
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
seal=json.loads((HERE/"analysis-inputs.json").read_text())
assert all(sha(HERE/n)==h for n,h in seal["files"].items())
accepted=json.loads((HERE/"export-attempt1/assessment-r2.json").read_text())
assert accepted["status"]=="PASS_SELECTED_GEOMETRY_EXPORT_REVISED_SCOPE_R2"
row=accepted["fullExport"];layout=row["layout"];parts=layout["components"]
outlier=next(p for p in parts if p["componentId"]=="JLOAD")
other_right=max(p["access"][0]+p["access"][2] for p in parts if p is not outlier)
gap_left,gap_right=other_right,outlier["access"][0]
assert gap_right>gap_left
crossings=[]
for trace in layout["traces"]:
    for a,b in zip(trace["points"],trace["points"][1:]):
        if min(a[0],b[0])<gap_right and max(a[0],b[0])>gap_left:
            crossings.append({"net":trace["net"],"layer":trace["layer"],"sourceId":trace["sourceId"],"first":a,"second":b})
assert crossings,"Do not describe the gap as entirely empty when copper crosses it"
analysis={"status":"PASS_FINITE_SELECTED_GEOMETRY_ANALYSIS","seed":row["seed"],
    "geometrySha256":accepted["geometrySha256"],"outline":layout["outline"],
    "outlier":{"componentId":outlier["componentId"],"publicReference":outlier["publicReference"],"anchor":outlier["anchor"],"bounds":outlier["bounds"],"access":outlier["access"],"routingCourtyard":outlier["routingCourtyard"]},
    "horizontalBand":{"left":gap_left,"right":gap_right,"width":gap_right-gap_left,"basis":"Maximum right edge of every other declared package access envelope to JLOAD access left edge; not a free-copper or routable vacancy metric"},
    "crossingCenterlineSegments":crossings,
    "outlierDeclaredNets":{t["net"]:next(n["padIds"] for n in row["nets"] if n["netId"]==t["net"]) for t in outlier["terminals"]},
    "packingMetrics":row["packingMetrics"],"limits":"Selected accepted layout only. No discarded candidate metrics/reason, manufacturing clearance or legal compaction proof; diagnostic SVG/PNG is not production-player input evidence"}
(HERE/"geometry-analysis.json").write_text(json.dumps(analysis,indent=2)+"\n",encoding="utf-8")
def rect(r,**attrs):
    return '<rect x="%s" y="%s" width="%s" height="%s" %s/>'%(*r,' '.join(k.replace('_','-')+'="'+html.escape(str(v),quote=True)+'"' for k,v in attrs.items()))
def label(x,y,value,size=28,fill="#f0f5f1"):
    return '<text x="%s" y="%s" font-family="sans-serif" font-size="%s" fill="%s">%s</text>'%(x,y,size,fill,html.escape(value))
def svg(view,title):
    x,y,w,h=view;items=['<svg xmlns="http://www.w3.org/2000/svg" width="1400" height="800" viewBox="%s %s %s %s">'%(x-40,y-140,w+80,h+220)]
    items.append(rect([x-40,y-140,w+80,h+220],fill="#14262a"))
    items.append(label(x,y-75,title,42))
    items.append(label(x,y-25,"Developer geometry: cyan TOP / amber BOTTOM copper; dashed package access; red JLOAD",27))
    items.append(rect(layout["outline"],fill="#225747",stroke="#9abb9e",stroke_width=5))
    band=[gap_left,layout["outline"][1],gap_right-gap_left,layout["outline"][3]]
    items.append(rect(band,fill="#ffffff",fill_opacity="0.035",stroke="#e6b561",stroke_width=5,stroke_dasharray="20 15"))
    for trace in layout["traces"]:
        points=' '.join(str(a)+','+str(b) for a,b in trace["points"])
        color="#73c7ef" if trace["layer"]=="TOP" else "#f3c878"
        items.append('<polyline points="%s" fill="none" stroke="%s" stroke-width="%s" stroke-opacity="0.86"/>'%(points,color,max(3,trace["width"])))
    for p in parts:
        color="#ff7975" if p["componentId"]=="JLOAD" else "#b7cbd0"
        items.append(rect(p["access"],fill="none",stroke=color,stroke_width=3,stroke_dasharray="10 10"))
        items.append(rect(p["bodyBounds"],fill="#31434b",stroke=color,stroke_width=4))
        items.append(label(p["bodyBounds"][0],p["bodyBounds"][1]-15,p["componentId"],27,color))
        for t in p["terminals"]:
            cx,cy=t["padCenter"]
            items.append('<circle cx="%s" cy="%s" r="11" fill="#f3db9c" stroke="#263d37" stroke-width="3"/>'%(cx,cy))
    for via in layout["vias"]:
        items.append('<circle cx="%s" cy="%s" r="%s" fill="#a3cfbe"/>'%(via["x"],via["y"],max(3,via["landRadius"])))
    items.append(label(gap_left+15,layout["outline"][1]+60,"%s units between package access envelopes"%(gap_right-gap_left),30,"#ffd59a"))
    items.append(label(x,y+h+50,"Copper crosses this band. No legal shrink is established by the picture.",30))
    return ''.join(items)+'</svg>\n'
outline=layout["outline"]
(HERE/"selected-whole-board.svg").write_text(svg(outline,"Exact fresh multi-rail selected geometry / seed "+row["seed"]),encoding="utf-8")
detail=[gap_left-250,outline[1],outline[0]+outline[2]-(gap_left-250),outline[3]]
(HERE/"selected-gap-detail.svg").write_text(svg(detail,"Selected JLOAD anchor and intervening copper"),encoding="utf-8")
print("PASS selected geometry analysis: JLOAD RIGHT, package-access span="+str(gap_right-gap_left)+", copper crossing segments="+str(len(crossings)))
