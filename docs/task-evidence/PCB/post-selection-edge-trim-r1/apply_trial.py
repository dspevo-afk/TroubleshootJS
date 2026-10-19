"""Apply explicit native-only15 from verified original owners; current14 stays."""
import hashlib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
EXPECTED={r['path']:r['sha256'] for r in json.loads((HERE/'original-source.json').read_text(encoding='utf-8-sig'))['files']}
BASE='src/com/lushprojects/circuitjs1/client/'
def once(text,old,new):
    if text.count(old)!=1:raise ValueError('Anchor count '+old[:80])
    return text.replace(old,new)
def edit(name,change):
    path=ROOT/BASE/name;raw=path.read_bytes()
    if hashlib.sha256(raw).hexdigest()!=EXPECTED[BASE+name]:raise ValueError('Original changed '+name)
    eol='\r\n' if b'\r\n' in raw else '\n'
    path.write_bytes(change(raw.decode().replace('\r\n','\n')).replace('\n',eol).encode())
def versions(s):
    s=once(s,'CURRENT_VERSION = 14;','CURRENT_VERSION = 14, EDGE_TRIM_TRIAL_VERSION15 = 15;')
    s=once(s,'|| version == CURRENT_VERSION;', '|| version == CURRENT_VERSION || version == EDGE_TRIM_TRIAL_VERSION15;')
    s=once(s,'return placementVersion == CURRENT_VERSION ? 13 : placementVersion;', 'if (placementVersion == EDGE_TRIM_TRIAL_VERSION15) return 14;\n        return placementVersion == CURRENT_VERSION ? 13 : placementVersion;')
    s=once(s,'|| placementVersion == CURRENT_VERSION ?', '|| placementVersion == CURRENT_VERSION || placementVersion == EDGE_TRIM_TRIAL_VERSION15 ?')
    s=once(s,'|| placementVersion == CURRENT_VERSION,', '|| placementVersion == CURRENT_VERSION || placementVersion == EDGE_TRIM_TRIAL_VERSION15,')
    return s
def generator(s):
    s=once(s,'if (mediumCanvas && board.getPlacementConstraints().placementDensityVersion == 13)',
        'if (mediumCanvas && (board.getPlacementConstraints().placementDensityVersion == 13 ||\n                board.getPlacementConstraints().placementDensityVersion == 14))')
    s=once(s,'''        if (!selectMediumLayout(session))
            return finishMediumFailure(session);
        session.bestLayout.setGenerationStatistics''','''        if (!selectMediumLayout(session))
            return finishMediumFailure(session);
        trimSelectedMediumBorder(session);
        session.bestLayout.setGenerationStatistics''')
    start=s.index('        for (PcbFootprint footprint:plan.footprints) {',s.index('private PcbBoardLayout finishRoutedPlan'))
    end=s.index('        int preferredBoardX',start)
    old=s[start:end]
    access='''    private static int accessEdgeMargin(PcbPlacementConstraints constraints,
            Vector<PcbFootprint> footprints, Rectangle occupied, int edgeMargin) {
'''+old.replace('plan.footprints','footprints').replace('board.getPlacementConstraints().get(','constraints.get(')+'''        return edgeMargin;
    }

'''
    s=s[:start]+'''        edgeMargin=accessEdgeMargin(board.getPlacementConstraints(),
            plan.footprints,occupied,edgeMargin);
'''+s[end:]
    helpers='''    /** Preserve the existing drawing-grid phase while removing spare border. */
    static int finalEdgeTrimTarget(int oldMargin, int requiredMargin) {
        if (oldMargin < FINAL_EDGE_MARGIN || requiredMargin < FINAL_EDGE_MARGIN)
            throw new IllegalArgumentException("Invalid final PCB edge margin");
        if (requiredMargin >= oldMargin) return oldMargin;
        return oldMargin - ((oldMargin - requiredMargin) / GRID) * GRID;
    }

    /** Selection is complete before this physical border-only contraction. */
    private void trimSelectedMediumBorder(Session session) {
        PcbPlacementConstraints constraints=session.board.getPlacementConstraints();
        if (constraints.placementDensityVersion != 14) return;
        PcbBoardLayout layout=session.bestLayout;
        Rectangle outline=layout.getBoardOutline(),occupied=layout.getOccupiedContentBounds();
        long widthDifference=(long)outline.width-occupied.width;
        long heightDifference=(long)outline.height-occupied.height;
        if (widthDifference < 0 || widthDifference != heightDifference ||
                (widthDifference & 1L) != 0 ||
                (long)occupied.x-outline.x != widthDifference/2L ||
                (long)occupied.y-outline.y != widthDifference/2L)
            throw new IllegalStateException("Selected PCB has no uniform crop margin");
        int oldMargin=PcbCoordinateSystem.checkedInt(widthDifference/2L);
        PcbPlacementPlanner.Plan plan=null;
        for (MediumBoardPhysicalPolicy.PlacementCandidate candidate:session.mediumPlacements)
            if (candidate.attempt == session.mediumSelectedPlacementAttempt) {
                if (plan != null) throw new IllegalStateException("Duplicate selected PCB placement");
                plan=candidate.plan;
            }
        if (plan == null) throw new IllegalStateException("Missing selected PCB placement");
        Vector<PcbFootprint> footprints=translatedPlanFootprints(plan,layout,0,0);
        int requiredMargin=accessEdgeMargin(constraints,footprints,occupied,FINAL_EDGE_MARGIN);
        int target=finalEdgeTrimTarget(oldMargin,requiredMargin);
        if (target == oldMargin) return;
        int validated=validatedCompactEdgeMargin(session.board,constraints,plan,layout,
            outline,occupied,outline.x,outline.y,target);
        if (validated == oldMargin) return;
        if (validated != target)
            throw new IllegalStateException("Selected PCB crop exceeded its valid outline");
        PcbBoardLayout candidate=layout.copyForCompaction();
        try {
            candidate.compactToContent(outline.x,outline.y,validated);
            candidate.positionPartsTrayDisjointFromBoard();
            candidate.ensureCanvasContainsBoard();
            PcbPlacementPlanner.validate(session.board,constraints,candidate.getBoardOutline(),
                translatedPlanFootprints(plan,candidate,0,0));
            candidate.validateGeometry(session.board);
            candidate.validateRoutingGeometry(session.board);
            candidate.validateRouteQuality();
            PcbTraceClearance.validate(candidate.getTraces());
            if (MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(session.mediumSelectedRoute))
                new PcbTwoLayerRules(session.board,candidate).validate(candidate);
        } catch (PcbPlacementPlanner.Rejected rejected) {
            return; // Original selected geometry remains untouched.
        } catch (PcbBoardLayout.RouteQualityRejectedException rejected) {
            return; // Preserve the admitted route when its unchanged quality guard rejects.
        }
        // Unexpected errors and generation cancellation propagate; no live state was mutated.
        session.bestLayout=candidate;
        Rectangle trimmed=candidate.getBoardOutline();
        session.bestArea=(long)trimmed.width*trimmed.height;
        session.bestScore=candidate.getRouteQualityScore(session.board);
        session.mediumSelectedRouteQualityMilli=routeQualityMilli(session.bestScore);
        if (layout == session.mediumTwoLayerLayout) {
            session.mediumTwoLayerLayout=candidate;
            session.mediumTwoLayerArea=session.bestArea;
            session.mediumTwoLayerQuality=session.bestScore;
        } else {
            session.mediumOneFaceLayout=candidate;
            session.mediumOneFaceArea=session.bestArea;
            session.mediumOneFaceQuality=session.bestScore;
        }
    }

'''
    s=once(s,'    private boolean finishMediumSuccess(Session session) {',access+helpers+'    private boolean finishMediumSuccess(Session session) {')
    return s
def layout_copy(s):
    start=s.index('    /** Fresh layout owner from frozen coordinate values; never repeats routing. */')
    end=s.index('    void replaceTraces',start)
    old=s[start:end]
    body_start=old.index('        PcbBoardLayout copy=new PcbBoardLayout')
    body_end=old.index('        copy.seal();return copy;')
    body=old[body_start:body_end]
    replacement='''    /** Isolated physical candidate; shared geometry values are immutable. */
    PcbBoardLayout copyForCompaction() {
        requireMutable();
        return copyWithCopper();
    }
    /** Fresh layout owner from frozen coordinate values; never repeats routing. */
    PcbBoardLayout copySealed() {
        if(!sealed)throw new IllegalStateException("Only a frozen realization can be copied");
        PcbBoardLayout copy=copyWithCopper();
        copy.seal();return copy;
    }
    private PcbBoardLayout copyWithCopper() {
'''+body+'''        return copy;
    }
'''
    return s[:start]+replacement+s[end:]
edit('Rb56Placement.java',versions)
edit('PcbPlacementConstraints.java',lambda s:once(s,'&& densityVersion != 13)', '&& densityVersion != 13 && densityVersion != 14)'))
edit('SeededPcbLayoutGenerator.java',generator)
edit('PcbBoardLayout.java',layout_copy)
print('APPLIED explicit native15 post-selection border trim; current/replay14 unchanged')
