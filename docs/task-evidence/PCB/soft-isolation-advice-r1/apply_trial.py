"""Apply the predeclared native-only advice trial to byte-snapshotted owners."""
import hashlib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
original=json.loads((HERE/'original-source.json').read_text(encoding='utf-8-sig'))
expected={r['path']:r['sha256'] for r in original['files']}
def replace(text,old,new):
    if text.count(old)!=1:raise ValueError('Anchor count: '+old[:100])
    return text.replace(old,new)
def edit(relative,change):
    p=ROOT/relative;raw=p.read_bytes()
    if hashlib.sha256(raw).hexdigest()!=expected[relative]:raise ValueError('Original changed: '+relative)
    eol='\r\n' if b'\r\n' in raw else '\n'
    text=raw.decode('utf-8').replace('\r\n','\n')
    p.write_bytes(change(text).replace('\n',eol).encode('utf-8'))
base='src/com/lushprojects/circuitjs1/client/'
def versions(s):
    s=replace(s,'CURRENT_VERSION = 14;','CURRENT_VERSION = 14, SOFT_ISOLATION_TRIAL_VERSION = 15;')
    s=s.replace('|| version == CURRENT_VERSION;', '|| version == CURRENT_VERSION || version == SOFT_ISOLATION_TRIAL_VERSION;')
    s=replace(s,'return placementVersion == CURRENT_VERSION ? 13 : placementVersion;', 'if (placementVersion == SOFT_ISOLATION_TRIAL_VERSION) return 14;\n        return placementVersion == CURRENT_VERSION ? 13 : placementVersion;')
    s=s.replace('|| placementVersion == CURRENT_VERSION ?', '|| placementVersion == CURRENT_VERSION || placementVersion == SOFT_ISOLATION_TRIAL_VERSION ?')
    s=s.replace('|| placementVersion == CURRENT_VERSION,', '|| placementVersion == CURRENT_VERSION || placementVersion == SOFT_ISOLATION_TRIAL_VERSION,')
    return s
edit(base+'Rb56Placement.java',versions)
edit(base+'PcbPlacementConstraints.java',lambda s:replace(s,'&& densityVersion != 13)', '&& densityVersion != 13 && densityVersion != 14)'))
edit(base+'SeededPcbLayoutGenerator.java',lambda s:replace(s,'if (mediumCanvas && board.getPlacementConstraints().placementDensityVersion == 13)', 'if (mediumCanvas && (board.getPlacementConstraints().placementDensityVersion == 13 ||\n                board.getPlacementConstraints().placementDensityVersion == 14))'))
def planner(s):
    s=replace(s,'''        int boundaryX=-1;
        if(placementVersion>=8 && (candidate==3 || pinLocal) &&
                SeededPcbLayoutGenerator.hasRepeatedControlRegions(constraints) && constraints.getBarriers().size()==1) {''','''        int boundaryX=-1;
        boolean prioritizeBoundary=placementVersion>=8 && (candidate==3 || pinLocal) &&
            SeededPcbLayoutGenerator.hasRepeatedControlRegions(constraints) && constraints.getBarriers().size()==1;
        // Trial15 advises only candidate0; retain its established grouping/order.
        boolean softBoundary=placementVersion==14 && candidate==0 &&
            SeededPcbLayoutGenerator.hasRepeatedControlRegions(constraints) && constraints.getBarriers().size()==1;
        if(prioritizeBoundary || softBoundary) {''')
    s=replace(s,'            useGrouped=false;\n        }\n        TreeMap<String,Item> remaining', '            if(prioritizeBoundary)useGrouped=false;\n        }\n        TreeMap<String,Item> remaining')
    s=replace(s,'pinLocal,true,boundaryX,evaluations);','pinLocal,true,boundaryX,softBoundary,evaluations);')
    s=replace(s,'pinLocal,false,boundaryX,evaluations);','pinLocal,false,boundaryX,softBoundary,evaluations);')
    s=replace(s,'if(boundaryX>=0) for(Item item:remaining.values())','if(prioritizeBoundary) for(Item item:remaining.values())')
    s=replace(s,'boolean tight,boolean pinLocal,boolean anchor,int boundaryX,int[] evaluations)', 'boolean tight,boolean pinLocal,boolean anchor,int boundaryX,boolean softBoundary,int[] evaluations)')
    s=replace(s,'''            if(first!=null && second!=null && first.x+first.width<=second.x) {
                preferredX=boundaryX-(first.x+first.width+second.x)/2+
                    item.footprint.getPlacement().getX();''','''            Integer gapCenter=softBoundary ? isolationGapCenterX(first,second) :
                first!=null && second!=null && first.x+first.width<=second.x ?
                    Integer.valueOf((first.x+first.width+second.x)/2) : null;
            if(gapCenter!=null) {
                preferredX=boundaryX-gapCenter.intValue()+item.footprint.getPlacement().getX();''')
    anchor='    private static PcbFootprint mediumPlace(Item item,Vector<PcbFootprint> placed,'
    helper='''    /** Spatial gap of transformed declared courtyards; advisory, never admission. */
    static Integer isolationGapCenterX(Rectangle first,Rectangle second) {
        if(first==null || second==null || first.width<=0 || first.height<=0 ||
                second.width<=0 || second.height<=0)return null;
        long firstRight=(long)first.x+first.width,secondRight=(long)second.x+second.width;
        if(firstRight>Integer.MAX_VALUE || secondRight>Integer.MAX_VALUE)return null;
        Rectangle left=first,right=second;
        if(second.x<first.x){left=second;right=first;}
        long leftRight=(long)left.x+left.width;
        if(leftRight>right.x)return null;
        long midpoint=leftRight+((long)right.x-leftRight)/2;
        return midpoint<Integer.MIN_VALUE || midpoint>Integer.MAX_VALUE ? null :
            Integer.valueOf((int)midpoint);
    }

'''
    return replace(s,anchor,helper+anchor)
edit(base+'PcbPlacementPlanner.java',planner)
print('APPLIED native-only trial15; default14 unchanged')
