package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

/**
 * Immutable adaptive observation partition for one complete proof population.
 *
 * <p>This is a planning value, not a solver strategy.  It is built only from
 * the complete serial-proof evidence and can therefore never turn a sample or
 * an arbitrary top-N candidate subset into an admission claim.  Every leaf is
 * either one canonical hypothesis or a non-empty, explicitly named equivalent
 * physical-repair class.</p>
 */
final class GeneratedDiagnosticPartitionPlan {
    static final int VERSION = 1;
    private static final int MAX_HYPOTHESES = 512;
    private static final int MAX_NODES = 4096;
    private static final int MAX_CANONICAL_LENGTH = 1024 * 1024;

    private final String providerId;
    private final String programIdentity;
    private final Vector<String> hypothesisKeys;
    private final Node root;
    private final int nodeCount;
    private final int leafCount;
    private final int maximumObservationDepth;
    private final String canonical;

    private GeneratedDiagnosticPartitionPlan(String providerId, String programIdentity,
            Vector<String> hypothesisKeys, Node root, int nodeCount, int leafCount) {
        this.providerId = required(providerId, "provider identity");
        this.programIdentity = required(programIdentity, "diagnostic program identity");
        this.hypothesisKeys = sortedUnique(hypothesisKeys, "hypothesis population");
        if (root == null || nodeCount < 1 || leafCount < 1 || nodeCount > MAX_NODES)
            throw new IllegalArgumentException("Incomplete diagnostic partition plan");
        this.root = root;
        this.nodeCount = nodeCount;
        this.leafCount = leafCount;
        this.maximumObservationDepth = maximumObservationDepth(root, 0);
        StringBuilder value = new StringBuilder();
        frame(value, "tsj-diagnostic-partition-v" + VERSION);
        frame(value, this.providerId);
        frame(value, this.programIdentity);
        frame(value, Integer.toString(this.hypothesisKeys.size()));
        for (String key : this.hypothesisKeys) frame(value, key);
        frame(value, Integer.toString(nodeCount));
        frame(value, Integer.toString(leafCount));
        appendNode(value, root);
        if (value.length() > MAX_CANONICAL_LENGTH)
            throw new IllegalArgumentException("Diagnostic partition plan exceeds its bound");
        canonical = value.toString();
    }

    /** Builds a plan from the full admitted candidate population and program. */
    static GeneratedDiagnosticPartitionPlan build(Vector<GeneratedFaultCandidate> candidates,
            GeneratedDiagnosticProgram program,
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence) {
        return build(candidates, "provider-bound-at-admission", program, evidence);
    }

    /** Provider-aware production entry; the provider ID is part of the plan identity. */
    static GeneratedDiagnosticPartitionPlan build(Vector<GeneratedFaultCandidate> candidates,
            String providerId, GeneratedDiagnosticProgram program,
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence) {
        if (candidates == null || program == null || evidence == null)
            throw new IllegalArgumentException("Missing diagnostic partition inputs");
        Vector<GeneratedFaultCandidate> admitted =
            GeneratedFaultServiceabilityAdmission.getAdmittedCandidates(candidates);
        if (admitted.isEmpty() || admitted.size() > MAX_HYPOTHESES)
            throw new IllegalArgumentException("Diagnostic partition population is outside its bound");
        Collections.sort(admitted, new Comparator<GeneratedFaultCandidate>() {
            public int compare(GeneratedFaultCandidate first, GeneratedFaultCandidate second) {
                return first.getHypothesisKey().compareTo(second.getHypothesisKey());
            }
        });
        Vector<String> keys = new Vector<String>();
        for (GeneratedFaultCandidate candidate : admitted) {
            GeneratedFaultServiceabilityAdmission.validateCandidate(candidate);
            keys.add(candidate.getHypothesisKey());
        }
        GeneratedFaultServiceabilityAdmission.validateHypothesisPopulation(candidates, keys);
        Vector<String> expectedSamples = sampleIds(program);
        CanonicalBuild built = buildCanonical(keys, providerId, program.canonical(), evidence,
            expectedSamples);
        Vector<GeneratedDiagnosticSolvabilityEvidence> classified = built.classifiedEvidence;
        for (int i = 0; i < admitted.size(); i++) {
            GeneratedDiagnosticRepairSemantics expected =
                GeneratedDiagnosticRepairSemantics.forServiceability(
                    admitted.get(i).getServiceability());
            if (!expected.isEquivalentTo(classified.get(i).getRepairSemantics()))
                throw new IllegalArgumentException(
                    "Diagnostic evidence repair semantics do not match its physical hypothesis");
        }
        return built.plan;
    }

    /**
     * Test/adapter entry when the caller already has a complete canonical key
     * list. A real program is still required so every branch stays tied to a
     * declared meter output rather than an arbitrary sample-name string.
     */
    static GeneratedDiagnosticPartitionPlan buildForKeys(Vector<String> completeHypothesisKeys,
            String providerId, GeneratedDiagnosticProgram program,
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence) {
        if (program == null)
            throw new IllegalArgumentException("Missing diagnostic partition program");
        Vector<String> keys = sortedUnique(completeHypothesisKeys, "hypothesis population");
        if (keys.isEmpty() || keys.size() > MAX_HYPOTHESES)
            throw new IllegalArgumentException("Diagnostic partition population is outside its bound");
        return buildCanonical(keys, providerId, program.canonical(), evidence,
            sampleIds(program)).plan;
    }

    /** A single canonical construction also retains its already-classified evidence. */
    private static CanonicalBuild buildCanonical(Vector<String> completeKeys,
            String providerId, String programIdentity,
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence,
            Vector<String> expectedSamples) {
        if (evidence == null || evidence.isEmpty() || evidence.size() != completeKeys.size())
            throw new IllegalArgumentException(
                "Diagnostic partition must cover the complete hypothesis population");
        Vector<GeneratedDiagnosticSolvabilityEvidence> byKey = orderEvidence(completeKeys, evidence);
        Vector<GeneratedDiagnosticSolvabilityEvidence> classified =
            GeneratedDiagnosticEquivalence.classify(byKey, byKey.firstElement().getFamilyId(),
                byKey.firstElement().getSeed());
        if (expectedSamples == null)
            throw new IllegalArgumentException("Diagnostic partition requires an executable program");
        validateCompleteSamples(classified, expectedSamples);

        Vector<Integer> all = new Vector<Integer>();
        for (int i = 0; i < classified.size(); i++) all.add(Integer.valueOf(i));
        BuildResult built = buildNode(classified, all, 0, 0);
        GeneratedDiagnosticPartitionPlan result = new GeneratedDiagnosticPartitionPlan(providerId,
            programIdentity, completeKeys,
            built.node, built.nodeCount, built.leafCount);
        result.validateSampleOrder(expectedSamples);
        return new CanonicalBuild(result, classified);
    }

    private static Vector<GeneratedDiagnosticSolvabilityEvidence> orderEvidence(
            Vector<String> completeKeys,
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence) {
        if (evidence == null || evidence.isEmpty() || evidence.size() != completeKeys.size())
            throw new IllegalArgumentException(
                "Diagnostic partition must cover the complete hypothesis population");
        Vector<GeneratedDiagnosticSolvabilityEvidence> byKey =
            new Vector<GeneratedDiagnosticSolvabilityEvidence>();
        for (String key : completeKeys) {
            GeneratedDiagnosticSolvabilityEvidence found = null;
            for (GeneratedDiagnosticSolvabilityEvidence proof : evidence) {
                if (proof == null)
                    throw new IllegalArgumentException("Missing diagnostic proof evidence");
                if (key.equals(proof.getHypothesisKey())) {
                    if (found != null)
                        throw new IllegalArgumentException("Duplicate diagnostic proof hypothesis");
                    found = proof;
                }
            }
            if (found == null)
                throw new IllegalArgumentException("Diagnostic proof omitted hypothesis: " + key);
            byKey.add(found);
        }
        return byKey;
    }

    String getProviderId() { return providerId; }
    String getProgramIdentity() { return programIdentity; }
    Vector<String> getHypothesisKeys() { return new Vector<String>(hypothesisKeys); }
    Node getRoot() { return root; }
    int getNodeCount() { return nodeCount; }
    int getLeafCount() { return leafCount; }
    int getMaximumObservationDepth() { return maximumObservationDepth; }
    String canonical() { return canonical; }

    /**
     * Starts a bounded value cursor over this certified plan. The cursor
     * consumes only retained, declared sample IDs and never carries a board,
     * solver, player owner, or mutable program state.
     */
    Cursor beginCursor() { return new Cursor(root); }

    /**
     * Routes every complete serial CircuitJS evidence record through the
     * value tree. This binds the retained evidence to the plan rather than
     * treating the tree as post-hoc metadata.
     */
    void validateEvidenceRoutes(Vector<GeneratedDiagnosticSolvabilityEvidence> evidence) {
        Vector<GeneratedDiagnosticSolvabilityEvidence> ordered =
            orderEvidence(hypothesisKeys, evidence);
        for (GeneratedDiagnosticSolvabilityEvidence proof : ordered) {
            Cursor cursor = beginCursor();
            while (!cursor.isComplete()) {
                String sampleId = cursor.getNextSampleId();
                GeneratedDiagnosticSample value = sampleFor(proof, sampleId);
                cursor.accept(value);
            }
            if (!cursor.getResolvedHypothesisKeys().contains(proof.getHypothesisKey()))
                throw new IllegalStateException(
                    "Adaptive diagnostic plan routed a hypothesis to a foreign repair leaf");
        }
    }

    /**
     * Verifies that this value-only plan is a legal projection of the supplied
     * full canonical program. Program identity binds every input, power, wait
     * and settle prefix; sample order admits only declared meter outputs, with
     * DIODE voltage/current siblings kept in their emitted order. No live
     * owner is mutated or re-solved by this check.
     */
    void validateExecutableProgram(GeneratedDiagnosticProgram program) {
        if (program == null || !program.canonical().equals(programIdentity))
            throw new IllegalArgumentException("Adaptive diagnostic plan belongs to another program");
        validateSampleOrder(sampleIds(program));
    }

    /** Rebuilds the plan from complete current inputs and compares all bytes. */
    void validateAgainst(Vector<GeneratedFaultCandidate> candidates,
            GeneratedDiagnosticProgram program,
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence) {
        GeneratedDiagnosticPartitionPlan expected = build(candidates, providerId, program, evidence);
        if (!providerId.equals(expected.providerId) ||
                !programIdentity.equals(expected.programIdentity) ||
                !canonical.equals(expected.canonical))
            throw new IllegalStateException("Diagnostic partition plan is stale or foreign");
        validateExecutableProgram(program);
        validateEvidenceRoutes(evidence);
    }

    private static int maximumObservationDepth(Node node, int depth) {
        if (node == null) throw new IllegalArgumentException("Missing diagnostic partition node");
        if (node.leaf != null) return depth;
        int maximum = depth;
        for (Branch branch : node.branches)
            maximum = Math.max(maximum, maximumObservationDepth(branch.child, depth + 1));
        return maximum;
    }

    private static GeneratedDiagnosticSample sampleFor(
            GeneratedDiagnosticSolvabilityEvidence proof, String sampleId) {
        if (proof == null || sampleId == null)
            throw new IllegalStateException("Adaptive diagnostic plan has no declared sample");
        GeneratedDiagnosticSample result = null;
        for (int i = 0; i < proof.getStaticProofSampleCount(); i++) {
            GeneratedDiagnosticSample sample = proof.getStaticProofSample(i);
            if (sample == null) throw new IllegalStateException("Missing diagnostic sample");
            if (sampleId.equals(sample.getSampleId())) {
                if (result != null)
                    throw new IllegalStateException("Duplicate adaptive diagnostic sample: " + sampleId);
                result = sample;
            }
        }
        if (result == null)
            throw new IllegalStateException("Adaptive diagnostic plan sample was not executed: " + sampleId);
        return result;
    }

    private static BuildResult buildNode(
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence,
            Vector<Integer> subset, int depth, int minimumSample) {
        if (subset.isEmpty())
            throw new IllegalStateException("Diagnostic partition contains an empty branch");
        if (isEquivalentLeaf(evidence, subset)) {
            Vector<String> keys = new Vector<String>();
            String classId = null;
            for (Integer value : subset) {
                GeneratedDiagnosticSolvabilityEvidence proof = evidence.get(value.intValue());
                keys.add(proof.getHypothesisKey());
                if (classId == null) classId = proof.getEquivalentRepairClass();
            }
            return new BuildResult(Node.leaf(new Leaf(keys, classId)), 1, 1);
        }
        if (depth > MAX_HYPOTHESES)
            throw new IllegalStateException("Diagnostic partition depth exceeds its bound");

        Throwable lastFailure = null;
        int sampleCount = evidence.firstElement().getStaticProofSampleCount();
        for (int sample = minimumSample; sample < sampleCount; sample++) {
            Vector<Group> groups = groupsFor(evidence, subset, sample);
            if (groups.size() < 2) continue;
            validateDistinctGroups(evidence, groups, sample);
            Collections.sort(groups, new Comparator<Group>() {
                public int compare(Group first, Group second) {
                    return first.label.compareTo(second.label);
                }
            });
            try {
                Vector<Branch> branches = new Vector<Branch>();
                int nodes = 1;
                int leaves = 0;
                for (Group group : groups) {
                    BuildResult child = buildNode(evidence, group.indices, depth + 1, sample + 1);
                    branches.add(new Branch(group.label, group.representative, child.node));
                    nodes += child.nodeCount;
                    leaves += child.leafCount;
                    if (nodes > MAX_NODES)
                        throw new IllegalStateException("Diagnostic partition node bound exceeded");
                }
                String sampleId = evidence.firstElement().getStaticProofSample(sample).getSampleId();
                return new BuildResult(Node.branch(sampleId, branches), nodes, leaves);
            } catch (IllegalStateException failure) {
                lastFailure = failure;
            }
        }
        throw new IllegalStateException("Diagnostic observations cannot partition the full hypothesis population",
            lastFailure);
    }

    private static boolean isEquivalentLeaf(
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence,
            Vector<Integer> subset) {
        if (subset.size() <= 1) return true;
        GeneratedDiagnosticSolvabilityEvidence first = evidence.get(subset.firstElement().intValue());
        String classId = first.getEquivalentRepairClass();
        if (classId == null || "NONE".equals(classId)) return false;
        for (int i = 1; i < subset.size(); i++) {
            GeneratedDiagnosticSolvabilityEvidence next = evidence.get(subset.get(i).intValue());
            if (!classId.equals(next.getEquivalentRepairClass()) ||
                    !first.getRepairSemantics().isEquivalentTo(next.getRepairSemantics()) ||
                    !GeneratedDiagnosticEquivalence.sameObservations(first, next))
                return false;
        }
        return true;
    }

    private static Vector<Group> groupsFor(
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence,
            Vector<Integer> subset, int sample) {
        Vector<Group> groups = new Vector<Group>();
        for (Integer index : subset) {
            GeneratedDiagnosticSample value = evidence.get(index.intValue()).getStaticProofSample(sample);
            Group matching = null;
            for (Group group : groups) {
                GeneratedDiagnosticSample representative = group.representative;
                if (sameSample(value, representative)) {
                    matching = group;
                    break;
                }
            }
            if (matching == null) {
                matching = new Group(sampleIdentity(value), value);
                groups.add(matching);
            }
            matching.indices.add(index);
        }
        return groups;
    }

    /**
     * A tolerance boundary must never create two branches that a real reading
     * could satisfy simultaneously.  This also rejects non-transitive
     * near-equality populations instead of silently turning them into a
     * misleading adaptive split.
     */
    private static void validateDistinctGroups(
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence,
            Vector<Group> groups, int sample) {
        for (int first = 0; first < groups.size(); first++)
            for (int second = first + 1; second < groups.size(); second++)
                for (Integer leftIndex : groups.get(first).indices)
                    for (Integer rightIndex : groups.get(second).indices)
                        if (sameSample(
                                evidence.get(leftIndex.intValue()).getStaticProofSample(sample),
                                evidence.get(rightIndex.intValue()).getStaticProofSample(sample)))
                            throw new IllegalStateException(
                                "Diagnostic observation has ambiguous tolerance branches");
    }

    private static boolean sameSample(GeneratedDiagnosticSample first,
            GeneratedDiagnosticSample second) {
        if (first == null || second == null || first.getOutcome() != second.getOutcome())
            return false;
        if (first.isOverRange()) return true;
        return toleranceIntervalsOverlap(first, second);
    }

    /**
     * A numeric observation declares the closed interval represented by its
     * value and comparison tolerance.  This is the one branch-matching policy
     * used by both plan construction and Cursor.accept; max-tolerance equality
     * is too weak because two declared intervals can overlap while that test
     * reports them as distinct.
     */
    private static boolean toleranceIntervalsOverlap(GeneratedDiagnosticSample first,
            GeneratedDiagnosticSample second) {
        double distance = Math.abs(first.getValue() - second.getValue());
        return distance <= first.getComparisonTolerance() + second.getComparisonTolerance();
    }

    private static void validateCompleteSamples(
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence,
            Vector<String> expected) {
        if (expected.isEmpty())
            throw new IllegalArgumentException("Diagnostic program has no sample IDs");
        for (GeneratedDiagnosticSolvabilityEvidence proof : evidence) {
            if (proof.getStaticProofSampleCount() != expected.size())
                throw new IllegalArgumentException(
                    "Diagnostic proof sampled fewer or more observations than its program");
            for (int i = 0; i < expected.size(); i++)
                if (!expected.get(i).equals(proof.getStaticProofSample(i).getSampleId()))
                    throw new IllegalArgumentException(
                        "Diagnostic proof sample schema is truncated or reordered");
        }
    }

    private static Vector<String> sampleIds(GeneratedDiagnosticProgram program) {
        Vector<String> result = new Vector<String>();
        for (GeneratedDiagnosticProgram.Step step : program.getSteps()) {
            switch (step.kind) {
            case DC_VOLTAGE:
            case RESISTANCE:
            case CONTINUITY:
                addSampleId(result, step.id);
                break;
            case DIODE:
                addSampleId(result, step.id + "_VOLTAGE");
                addSampleId(result, step.id + "_CURRENT");
                break;
            default:
                break;
            }
        }
        return result;
    }

    private void validateSampleOrder(Vector<String> expectedSamples) {
        if (expectedSamples == null || expectedSamples.isEmpty())
            throw new IllegalArgumentException("Diagnostic program has no sample IDs");
        validateSampleOrder(root, expectedSamples, -1);
    }

    private static void validateSampleOrder(Node node, Vector<String> expectedSamples,
            int previousSample) {
        if (node == null) throw new IllegalArgumentException("Missing diagnostic partition node");
        if (node.isLeaf()) return;
        int currentSample = expectedSamples.indexOf(node.sampleId);
        if (currentSample < 0)
            throw new IllegalArgumentException("Diagnostic partition names an undeclared sample: " +
                node.sampleId);
        if (currentSample <= previousSample)
            throw new IllegalArgumentException(
                "Diagnostic partition chooses observations out of canonical program order");
        for (Branch branch : node.branches)
            validateSampleOrder(branch.child, expectedSamples, currentSample);
    }

    private static void addSampleId(Vector<String> values, String id) {
        if (values.contains(id))
            throw new IllegalArgumentException("Diagnostic program has duplicate sample ID");
        values.add(id);
    }

    private static String sampleIdentity(GeneratedDiagnosticSample sample) {
        if (sample.isOverRange()) return "OL";
        return "N:" + Long.toHexString(Double.doubleToLongBits(sample.getValue())) +
            ":" + Long.toHexString(Double.doubleToLongBits(sample.getComparisonTolerance()));
    }

    private static Vector<String> sortedUnique(Vector<String> values, String name) {
        if (values == null || values.isEmpty() || values.size() > MAX_HYPOTHESES)
            throw new IllegalArgumentException("Missing or oversized " + name);
        Vector<String> result = new Vector<String>(values);
        for (String value : result)
            if (value == null || value.length() == 0)
                throw new IllegalArgumentException("Missing " + name + " key");
        Collections.sort(result);
        for (int i = 1; i < result.size(); i++)
            if (result.get(i - 1).equals(result.get(i)))
                throw new IllegalArgumentException("Duplicate " + name + " key");
        return result;
    }

    private static String required(String value, String name) {
        if (value == null || value.length() == 0)
            throw new IllegalArgumentException("Missing diagnostic " + name);
        return value;
    }

    private static void appendNode(StringBuilder out, Node node) {
        if (node.leaf != null) {
            frame(out, "L");
            frame(out, node.leaf.repairClass);
            frame(out, Integer.toString(node.leaf.hypothesisKeys.size()));
            for (String key : node.leaf.hypothesisKeys) frame(out, key);
            return;
        }
        frame(out, "B"); frame(out, node.sampleId);
        frame(out, Integer.toString(node.branches.size()));
        for (Branch branch : node.branches) {
            frame(out, branch.label);
            appendNode(out, branch.child);
        }
    }

    private static void frame(StringBuilder out, String value) {
        if (value == null) out.append("N;");
        else out.append('V').append(value.length()).append(':').append(value).append(';');
    }

    static final class Node {
        private final String sampleId;
        private final Vector<Branch> branches;
        private final Leaf leaf;

        private Node(String sampleId, Vector<Branch> branches, Leaf leaf) {
            this.sampleId = sampleId;
            this.branches = branches == null ? null : new Vector<Branch>(branches);
            this.leaf = leaf;
        }
        static Node leaf(Leaf leaf) { return new Node(null, null, leaf); }
        static Node branch(String sampleId, Vector<Branch> branches) {
            if (sampleId == null || sampleId.length() == 0 || branches == null || branches.size() < 2)
                throw new IllegalArgumentException("Incomplete diagnostic partition branch");
            return new Node(sampleId, branches, null);
        }
        boolean isLeaf() { return leaf != null; }
        String getSampleId() { return sampleId; }
        Vector<Branch> getBranches() {
            return branches == null ? new Vector<Branch>() : new Vector<Branch>(branches);
        }
        Leaf getLeaf() { return leaf; }
    }

    static final class Branch {
        private final String label;
        private final GeneratedDiagnosticSample representative;
        private final Node child;
        private Branch(String label, GeneratedDiagnosticSample representative, Node child) {
            this.label = required(label, "partition branch label");
            if (representative == null)
                throw new IllegalArgumentException("Missing partition branch representative");
            this.representative = representative;
            if (child == null) throw new IllegalArgumentException("Missing partition branch child");
            this.child = child;
        }
        String getLabel() { return label; }
        GeneratedDiagnosticSample getRepresentative() { return representative; }
        Node getChild() { return child; }
    }

    static final class Leaf {
        private final Vector<String> hypothesisKeys;
        private final String repairClass;
        private Leaf(Vector<String> keys, String repairClass) {
            hypothesisKeys = sortedUnique(keys, "leaf hypothesis");
            if (repairClass == null || repairClass.length() == 0)
                throw new IllegalArgumentException("Missing partition leaf repair class");
            if (hypothesisKeys.size() > 1 && "NONE".equals(repairClass))
                throw new IllegalArgumentException("Partition leaf merges incompatible repairs");
            this.repairClass = repairClass;
        }
        Vector<String> getHypothesisKeys() { return new Vector<String>(hypothesisKeys); }
        String getEquivalentRepairClass() { return repairClass; }
    }

    /** One bounded traversal of a certified adaptive observation tree. */
    static final class Cursor {
        private Node current;
        private int observations;

        private Cursor(Node root) {
            if (root == null) throw new IllegalArgumentException("Missing diagnostic partition root");
            current = root;
        }

        boolean isComplete() { return current != null && current.isLeaf(); }

        String getNextSampleId() {
            if (current == null || current.isLeaf())
                throw new IllegalStateException("Adaptive diagnostic partition is already complete");
            return current.sampleId;
        }

        void accept(GeneratedDiagnosticSample sample) {
            if (sample == null) throw new IllegalArgumentException("Missing adaptive diagnostic sample");
            String expected = getNextSampleId();
            if (!expected.equals(sample.getSampleId()))
                throw new IllegalArgumentException("Adaptive diagnostic sample is undeclared or out of order: " +
                    sample.getSampleId());
            Branch selected = null;
            for (Branch branch : current.branches)
                if (sameSample(sample, branch.representative)) {
                    if (selected != null)
                        throw new IllegalStateException(
                            "Adaptive diagnostic sample matches multiple tolerance branches");
                    selected = branch;
                }
            if (selected == null)
                throw new IllegalStateException("Adaptive diagnostic plan has no legal branch for " + expected);
            current = selected.child;
            observations++;
            if (observations > MAX_HYPOTHESES)
                throw new IllegalStateException("Adaptive diagnostic partition exceeded its observation bound");
        }

        int getObservationCount() { return observations; }

        Vector<String> getResolvedHypothesisKeys() {
            if (!isComplete())
                throw new IllegalStateException("Adaptive diagnostic partition has not reached a repair leaf");
            return current.leaf.getHypothesisKeys();
        }

        String getEquivalentRepairClass() {
            if (!isComplete())
                throw new IllegalStateException("Adaptive diagnostic partition has not reached a repair leaf");
            return current.leaf.getEquivalentRepairClass();
        }
    }

    private static final class Group {
        private final String label;
        private final GeneratedDiagnosticSample representative;
        private final Vector<Integer> indices = new Vector<Integer>();
        private Group(String label, GeneratedDiagnosticSample representative) {
            this.label = label; this.representative = representative;
        }
    }

    private static final class CanonicalBuild {
        private final GeneratedDiagnosticPartitionPlan plan;
        private final Vector<GeneratedDiagnosticSolvabilityEvidence> classifiedEvidence;
        private CanonicalBuild(GeneratedDiagnosticPartitionPlan plan,
                Vector<GeneratedDiagnosticSolvabilityEvidence> classifiedEvidence) {
            this.plan = plan;
            this.classifiedEvidence = classifiedEvidence;
        }
    }

    private static final class BuildResult {
        private final Node node;
        private final int nodeCount;
        private final int leafCount;
        private BuildResult(Node node, int nodeCount, int leafCount) {
            this.node = node; this.nodeCount = nodeCount; this.leafCount = leafCount;
        }
    }
}
