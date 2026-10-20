"""Create a private planner and task-owned adapters for the paired-pocket probe."""
import argparse,difflib,hashlib,json
from pathlib import Path

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
BASE='c29b81bbe87aa192761b35a6ee3c9ebdb037ba43'
OWNER='src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java'
NPN_ADAPTER_SHA='c333569940e56f845bf6632fe33eac8c029d4a4972c7d426637e9a94276d32e4'
NPN_EXPORTER_SHA='14c472a9b8a28d648a8be17d4a4ae0d6b66ecac3e286fb475808d98f71d20b36'
DUAL_EXPORTER_SHA='55658de039f165c621f8062a3b938b8d4e8d16adf1d3009c8dfa975fdf327eef'
FAMILY='PARALLEL_DUAL_INDICATOR'
SEED='-9223372036854775808'

def sha(raw):
    return hashlib.sha256(raw).hexdigest()

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--baseline-adapter',type=Path,required=True)
    parser.add_argument('--output-directory',type=Path,required=True)
    args=parser.parse_args()
    assert args.output_directory.is_dir() and not list(args.output_directory.iterdir())
    assert not (HERE/'private-input-manifest.json').exists()

    baseline=json.loads((HERE/'baseline-inputs.json').read_text(encoding='utf-8'))
    assert baseline['baseCommit']==BASE
    published_owner=(ROOT/OWNER).read_bytes()
    assert sha(published_owner)==baseline['files'][OWNER]
    original_owner=published_owner.decode('utf-8')
    owner=original_owner
    owner_changes=[]

    def replace(source,old,new,label,changes):
        nl='\r\n' if '\r\n' in source else '\n'
        old=old.replace('\n',nl)
        new=new.replace('\n',nl)
        assert source.count(old)==1,label
        changes.append({'label':label,'replacements':1,
            'oldSha256':sha(old.encode('utf-8')),
            'newSha256':sha(new.encode('utf-8'))})
        return source.replace(old,new)

    owner=replace(owner,
        '        if (evaluations == input.evaluations) return input;',
        r'''        int taskPairEvaluationStart = evaluations;
        long taskPairAreaStart = area;
        double taskPairWireStart = wire;
        String taskInvocationGroup = "compactConnectedVacancies/candidate-" +
            input.candidate + "/evaluation-" + taskPairEvaluationStart;
        class TaskPairCandidate {
            final String componentA, componentB, netId;
            TaskPairCandidate(String a, String b, String net) {
                componentA = a; componentB = b; netId = net;
            }
        }
        TreeMap<String, TaskPairCandidate> taskPairByComponents =
            new TreeMap<String, TaskPairCandidate>();
        Vector<String> taskNetIds = board.getNetIds();
        Collections.sort(taskNetIds);
        for (String taskNetId : taskNetIds) {
            GenerationWorkScope.check();
            BoardNet taskNet = board.getNet(taskNetId);
            if (taskNet == null) continue;
            Vector<String> taskPadIds = taskNet.getPadIds();
            Collections.sort(taskPadIds);
            if (taskPadIds.size() != 2) continue;
            BoardPad taskFirstPad = board.getPad(taskPadIds.get(0));
            BoardPad taskSecondPad = board.getPad(taskPadIds.get(1));
            String taskFirstComponent = taskFirstPad.getComponentId();
            String taskSecondComponent = taskSecondPad.getComponentId();
            if (taskFirstComponent.equals(taskSecondComponent)) continue;
            String taskComponentA, taskComponentB;
            BoardPad taskPadA, taskPadB;
            if (taskFirstComponent.compareTo(taskSecondComponent) < 0) {
                taskComponentA = taskFirstComponent;
                taskComponentB = taskSecondComponent;
                taskPadA = taskFirstPad;
                taskPadB = taskSecondPad;
            } else {
                taskComponentA = taskSecondComponent;
                taskComponentB = taskFirstComponent;
                taskPadA = taskSecondPad;
                taskPadB = taskFirstPad;
            }
            boolean taskGraphPair = false;
            for (TopologyPlacementGraph.PadLink link :
                    topology.getLinksFor(taskComponentA)) {
                if (taskNetId.equals(link.getNetId()) &&
                        taskPadA.getId().equals(link.getPadId()) &&
                        taskComponentB.equals(link.getOtherComponentId()) &&
                        taskPadB.getId().equals(link.getOtherPadId())) {
                    taskGraphPair = true;
                    break;
                }
            }
            if (!taskGraphPair) continue;
            BoardComponent taskBoardA = board.getComponent(taskComponentA);
            BoardComponent taskBoardB = board.getComponent(taskComponentB);
            if (taskBoardA == null || taskBoardB == null ||
                    taskBoardA.getPhysicalPackage().getTerminalIds().size() != 2 ||
                    taskBoardB.getPhysicalPackage().getTerminalIds().size() != 2)
                continue;
            PcbPlacementConstraints.Part taskDemandA =
                constraints.get(taskComponentA);
            PcbPlacementConstraints.Part taskDemandB =
                constraints.get(taskComponentB);
            if (taskDemandA == null || taskDemandB == null ||
                    !taskDemandA.regionId.equals(taskDemandB.regionId) ||
                    !taskDemandA.domainId.equals(taskDemandB.domainId) ||
                    taskDemandA.anchor != PcbPlacementConstraints.Anchor.NONE ||
                    taskDemandB.anchor != PcbPlacementConstraints.Anchor.NONE ||
                    !taskDemandA.terminalDomain(taskPadA.getTerminalId()).equals(
                        taskDemandB.terminalDomain(taskPadB.getTerminalId())))
                continue;
            PcbFootprint taskFootprintA =
                findPlacedFootprint(placed, taskComponentA);
            PcbFootprint taskFootprintB =
                findPlacedFootprint(placed, taskComponentB);
            if (taskFootprintA == null || taskFootprintB == null ||
                    taskFootprintA.getPlacement().getPhysicalGeometry()
                        .getIsolationBody() != null ||
                    taskFootprintB.getPlacement().getPhysicalGeometry()
                        .getIsolationBody() != null)
                continue;
            String taskPairKey = taskComponentA +
                Character.toString((char)0) + taskComponentB;
            TaskPairCandidate priorPair = taskPairByComponents.get(taskPairKey);
            if (priorPair == null || taskNetId.compareTo(priorPair.netId) < 0)
                taskPairByComponents.put(taskPairKey, new TaskPairCandidate(
                    taskComponentA, taskComponentB, taskNetId));
        }
        Vector<TaskPairCandidate> taskDisjointPairs =
            new Vector<TaskPairCandidate>();
        java.util.HashSet<String> taskUsedPairComponents =
            new java.util.HashSet<String>();
        for (TaskPairCandidate pair : taskPairByComponents.values()) {
            if (taskUsedPairComponents.contains(pair.componentA) ||
                    taskUsedPairComponents.contains(pair.componentB))
                continue;
            taskDisjointPairs.add(pair);
            taskUsedPairComponents.add(pair.componentA);
            taskUsedPairComponents.add(pair.componentB);
        }
        int taskTargetsEvaluated = 0;
        int taskAcceptedMoves = 0;
        for (TaskPairCandidate pair : taskDisjointPairs) {
            GenerationWorkScope.check();
            if (evaluations >= MEDIUM_MAX_EVALUATIONS) break;
            int indexA = -1, indexB = -1;
            for (int i = 0; i < placed.size(); i++) {
                String componentId = placed.get(i).getPlacement().getComponentId();
                if (pair.componentA.equals(componentId)) indexA = i;
                if (pair.componentB.equals(componentId)) indexB = i;
            }
            if (indexA < 0 || indexB < 0)
                throw new IllegalArgumentException("Missing paired pocket placement");
            PcbFootprint sourceA = placed.get(indexA);
            PcbFootprint sourceB = placed.get(indexB);
            PcbPlacementConstraints.Part demandA =
                constraints.get(pair.componentA);
            PcbPlacementConstraints.Part demandB =
                constraints.get(pair.componentB);
            PcbComponentPlacement poseA = sourceA.getPlacement();
            PcbComponentPlacement poseB = sourceB.getPlacement();
            Rectangle allowed = vacancyDomainBox(sourceA, placed, constraints,
                input.outline);
            if (allowed == null) continue;
            Vector<Rectangle> obstacles = new Vector<Rectangle>();
            for (int i = 0; i < placed.size(); i++) {
                if (i == indexA || i == indexB) continue;
                PcbFootprint peer = placed.get(i);
                obstacles.add(envelope(peer, constraints.get(
                    peer.getPlacement().getComponentId()).accessMargin));
            }
            Rectangle pairEnvelope = union(envelope(sourceA, demandA.accessMargin),
                envelope(sourceB, demandB.accessMargin));
            Vector<Point> targets = PcbVacancyTargets.targets(allowed, obstacles,
                pairEnvelope, new Point(poseA.getX(), poseA.getY()));
            PcbFootprint bestA = sourceA, bestB = sourceB;
            long bestArea = area;
            double bestWire = wire;
            int bestDx = 0, bestDy = 0, bestEvaluation = -1;
            java.util.HashSet<String> visited = new java.util.HashSet<String>();
            int targetLimit = Math.min(5, targets.size());
            for (int targetIndex = 0; targetIndex < targetLimit; targetIndex++) {
                GenerationWorkScope.check();
                if (evaluations >= MEDIUM_MAX_EVALUATIONS) break;
                Point target = targets.get(targetIndex);
                int targetAx = pocketGridOrigin(target.x, poseA.getX());
                int targetAy = pocketGridOrigin(target.y, poseA.getY());
                int dx = PcbCoordinateSystem.boardDisplacement(targetAx,
                    poseA.getX());
                int dy = PcbCoordinateSystem.boardDisplacement(targetAy,
                    poseA.getY());
                if (dx == 0 && dy == 0 ||
                        !visited.add(dx + "," + dy))
                    continue;
                int targetBx = PcbCoordinateSystem.checkedAddBoardCoordinate(
                    poseB.getX(), dx);
                int targetBy = PcbCoordinateSystem.checkedAddBoardCoordinate(
                    poseB.getY(), dy);
                evaluations++;
                taskTargetsEvaluated++;
                PcbFootprint trialA = sourceA.translated(targetAx, targetAy);
                PcbFootprint trialB = sourceB.translated(targetBx, targetBy);
                Vector<PcbFootprint> candidate = new Vector<PcbFootprint>(placed);
                candidate.set(indexA, trialA);
                candidate.set(indexB, trialB);
                if (!fits(trialA, input.outline, candidate, indexA, constraints) ||
                        !fits(trialB, input.outline, candidate, indexB, constraints))
                    continue;
                long candidateArea = occupiedEnvelopeArea(candidate, constraints);
                double candidateWire = totalWireDemand(candidate, topology);
                if (candidateArea > area || candidateWire > wire ||
                        candidateArea == area && candidateWire == wire ||
                        candidateArea > bestArea ||
                        candidateArea == bestArea && candidateWire >= bestWire)
                    continue;
                try {
                    validate(board, constraints, input.outline, candidate);
                } catch (Rejected insufficientAccess) {
                    continue;
                }
                bestA = trialA;
                bestB = trialB;
                bestArea = candidateArea;
                bestWire = candidateWire;
                bestDx = dx;
                bestDy = dy;
                bestEvaluation = evaluations;
            }
            if (bestA != sourceA && bestB != sourceB) {
                placed.set(indexA, bestA);
                placed.set(indexB, bestB);
                area = bestArea;
                wire = bestWire;
                taskAcceptedMoves++;
                String jsonNetId = pair.netId.replace("\\", "\\\\")
                    .replace("\"", "\\\"");
                String jsonComponentA = pair.componentA.replace("\\", "\\\\")
                    .replace("\"", "\\\"");
                String jsonComponentB = pair.componentB.replace("\\", "\\\\")
                    .replace("\"", "\\\"");
                System.out.println("PCB_POCKET_PAIR_MOVE_OBSERVATION {\"schema\":\"pocket-paired-move@1\",\"invocationGroup\":\"" +
                    taskInvocationGroup + "\",\"netId\":\"" + jsonNetId +
                    "\",\"componentA\":\"" + jsonComponentA +
                    "\",\"componentB\":\"" + jsonComponentB +
                    "\",\"fromA\":[" + poseA.getX() + "," + poseA.getY() +
                    "],\"fromB\":[" + poseB.getX() + "," + poseB.getY() +
                    "],\"toA\":[" + bestA.getPlacement().getX() + "," +
                    bestA.getPlacement().getY() + "],\"toB\":[" +
                    bestB.getPlacement().getX() + "," +
                    bestB.getPlacement().getY() + "],\"dx\":" + bestDx +
                    ",\"dy\":" + bestDy + ",\"evaluation\":" +
                    bestEvaluation + "}");
            }
        }
        System.out.println("PCB_POCKET_PAIR_SWEEP_OBSERVATION {\"schema\":\"pocket-paired-move@1\",\"invocationGroup\":\"" +
            taskInvocationGroup + "\",\"evaluationStart\":" +
            taskPairEvaluationStart + ",\"evaluationEnd\":" + evaluations +
            ",\"eligiblePairs\":" + taskPairByComponents.size() +
            ",\"disjointPairs\":" + taskDisjointPairs.size() +
            ",\"targetsEvaluated\":" + taskTargetsEvaluated +
            ",\"acceptedMoves\":" + taskAcceptedMoves +
            ",\"areaStart\":" + taskPairAreaStart + ",\"areaEnd\":" + area +
            ",\"wireStart\":" + taskPairWireStart + ",\"wireEnd\":" +
            wire + "}");
        if (evaluations == input.evaluations) return input;''',
        'one canonical disjoint two-part translation pass after the incumbent single-part sweep',
        owner_changes)
    assert len(owner_changes)==1

    adapter_source=args.baseline_adapter.read_bytes()
    prior_adapter=json.loads((HERE.parent/'npn-sparse-diagnostic-r1/adapter-manifest.json')
        .read_text(encoding='utf-8'))
    assert prior_adapter['generatedSha256']==NPN_ADAPTER_SHA
    assert sha(adapter_source)==NPN_ADAPTER_SHA
    original_adapter=adapter_source.decode('utf-8')
    adapter_changes=[]
    baseline_adapter=replace(original_adapter,
        "ValidateSet('NPN_LOW_SIDE_SWITCH')",
        "ValidateSet('PARALLEL_DUAL_INDICATOR')",
        'restrict baseline exporter binding to the one Dual family',
        adapter_changes)
    experiment_adapter=replace(baseline_adapter,
        '    [Parameter(Mandatory=$true)][string]$TaskExportSource,',
        "    [Parameter(Mandatory=$true)][string]$TaskExportSource,\n    [Parameter(Mandatory=$true)][ValidateScript({Test-Path -LiteralPath $_ -PathType Leaf})][string]$TaskPlannerSource,",
        'require the private paired planner source input',
        adapter_changes)
    experiment_adapter=replace(experiment_adapter,
        "        Where-Object { $_.Name -ne 'PhysicalSpecificationDeveloperVerifier.java' -and",
        "        Where-Object { $_.Name -ne 'PcbPlacementPlanner.java' -and $_.Name -ne 'PhysicalSpecificationDeveloperVerifier.java' -and",
        'exclude only the checked-in planner from this native compile',
        adapter_changes)
    experiment_adapter=replace(experiment_adapter,
        '    $sourcePaths += $stub',
        '    $sourcePaths += $stub\n    $sourcePaths += (Resolve-Path -LiteralPath $TaskPlannerSource).Path',
        'compile the one task-owned paired planner source',
        adapter_changes)
    assert len(adapter_changes)==4

    source_exporter=HERE.parent/'npn-sparse-diagnostic-r1/export-source/SparseNormalBoardDiagnostic.java'
    assert sha(source_exporter.read_bytes())==NPN_EXPORTER_SHA
    exporter=HERE/'export-source/SparseNormalBoardDiagnostic.java'
    exporter_bytes=exporter.read_bytes()
    assert sha(exporter_bytes)==DUAL_EXPORTER_SHA
    owner_bytes=owner.encode('utf-8')
    baseline_adapter_bytes=baseline_adapter.encode('utf-8')
    experiment_adapter_bytes=experiment_adapter.encode('utf-8')
    (args.output_directory/'PcbPlacementPlanner.java').write_bytes(owner_bytes)
    (args.output_directory/'baseline-adapter.ps1').write_bytes(baseline_adapter_bytes)
    (args.output_directory/'experiment-adapter.ps1').write_bytes(
        experiment_adapter_bytes)
    for name,before,after in (
            ('owner-diff.txt',original_owner,owner),
            ('adapter-diff.txt',original_adapter,experiment_adapter)):
        lines=difflib.unified_diff(before.splitlines(),after.splitlines(),
            fromfile='published/'+name,tofile='private-experiment/'+name,
            lineterm='')
        (args.output_directory/name).write_bytes(
            ('\n'.join(lines)+'\n').encode('utf-8'))
    manifest={
        'status':'GENERATED_TASK_ONLY_PAIRED_POCKET_EXPERIMENT',
        'baseCommit':BASE,
        'taskIdentity':{
            'family':FAMILY,
            'profile':'EASY',
            'publicSeed':SEED,
            'replayId':'tsj-alpha/6/EASY/PARALLEL_DUAL_INDICATOR/-9223372036854775808'
        },
        'publishedOwner':OWNER,
        'publishedOwnerSha256':sha(published_owner),
        'privateOwnerSha256':sha(owner_bytes),
        'baselineAdapterSha256':sha(baseline_adapter_bytes),
        'experimentAdapterSha256':sha(experiment_adapter_bytes),
        'exporterSha256':sha(exporter_bytes),
        'publishedNpnAdapterSha256':sha(adapter_source),
        'publishedNpnExporterSha256':sha(source_exporter.read_bytes()),
        'ownerChanges':owner_changes,
        'adapterChanges':adapter_changes,
        'limits':'Task-only generic disjoint linked-pair translation after the existing one-part vacancy pass. It uses exact two-pad topology, at most five existing vacancy targets per canonical disjoint pair and the original shared 60000 placement-evaluation ceiling. Both translated footprints are installed in the candidate vector before fit and full validation. It preserves the existing area/wire strict-progress ordering, full validators, normal route request, routing limits and four-metric selector. The paired observer is passive. No published source or production interpretation changes.'
    }
    (args.output_directory/'private-input-manifest.json').write_text(
        json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    print('PASS generated private paired-pocket planner1 guarded substitution and adapters4 guarded substitutions')

if __name__=='__main__':
    main()
