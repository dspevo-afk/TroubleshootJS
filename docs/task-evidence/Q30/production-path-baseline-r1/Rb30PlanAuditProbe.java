package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Plan/request identity probe; no construction, routing, generation, or solver call. */
public final class Rb30PlanAuditProbe {
    private static final int[] TARGETS = {33, 35, 37};

    private static final class Row {
        final long seed;
        final Rb30Plan plan;
        final int boardComponentCount;
        Row(long seed, Rb30Plan plan) {
            this.seed = seed;
            this.plan = plan;
            this.boardComponentCount = plan.board().getComponentIds().size();
        }
    }

    public static void main(String[] args) {
        Map<Long, Row> rows = new LinkedHashMap<Long, Row>();
        for (long seed : new long[] {7L, 13L, 64L}) resolve(rows, seed);
        if (!hasAllTargets(rows)) {
            for (long seed = 0; seed <= 20 && !hasAllTargets(rows); seed++) {
                if (!rows.containsKey(Long.valueOf(seed))) resolve(rows, seed);
            }
        }
        System.out.println("PLAN_AUDIT_EVALUATED count=" + rows.size() +
            " seeds=" + seeds(rows));
        for (Row row : rows.values()) {
            int declared = row.plan.physicalPackageCount();
            System.out.println("PLAN_AUDIT_ROW seed=" + row.seed +
                " canonical=" + row.plan.canonical() +
                " declaredPhysicalPackageCount=" + declared +
                " actualPlanBoardComponentCount=" + row.boardComponentCount);
            if (declared != row.boardComponentCount) {
                throw new AssertionError("plan board component count differs from declaration for seed " +
                    row.seed);
            }
        }
        List<Row> selected = new ArrayList<Row>();
        Row required = rows.get(Long.valueOf(7L));
        if (required == null) throw new AssertionError("seed 7 was not resolved");
        selected.add(required);
        for (int target : TARGETS) {
            if (containsCount(selected, target)) continue;
            Row choice = preferredDistinct(rows, target);
            if (choice == null) choice = lowestFallback(rows, target);
            if (choice == null) {
                System.out.println("PLAN_AUDIT_STATUS missingTarget=" + target);
                System.exit(2);
            }
            selected.add(choice);
        }
        System.out.println("PLAN_AUDIT_STATUS targets=33,35,37 found=true selectionCount=" +
            selected.size());
        for (Row row : selected) {
            System.out.println("PLAN_AUDIT_SELECTED seed=" + row.seed +
                " physicalPackageCount=" + row.plan.physicalPackageCount());
            GenerationRequest request = PlayerLaunchRequest.random(
                Rb30Plan.FAMILY_ID, Long.toString(row.seed), "MEDIUM").generation();
            if (request.isPrivateDiagnosticQualification() ||
                    request.getExecutionPolicy() != GenerationExecutionPolicy.NORMAL_MEDIUM ||
                    request.candidateCount() != 4) {
                throw new AssertionError("prospective normal medium request identity changed for seed " +
                    row.seed);
            }
            System.out.println("PLAN_AUDIT_NORMAL_ROOT seed=" + row.seed +
                " private=" + request.isPrivateDiagnosticQualification() +
                " executionPolicy=" + request.getExecutionPolicy().canonical() +
                " candidateCount=" + request.candidateCount());
            for (int ordinal = 0; ordinal < request.candidateCount(); ordinal++) {
                GenerationRequest candidate = request.candidate(ordinal);
                if (candidate.getExecutionPolicy() != GenerationExecutionPolicy.NORMAL_MEDIUM ||
                        candidate.getDescriptor().getRootSeed() !=
                            QuickPlayAdmission.candidateSeed(row.seed, ordinal)) {
                    throw new AssertionError("prospective candidate identity changed for seed " +
                        row.seed + " ordinal " + ordinal);
                }
                System.out.println("PLAN_AUDIT_NORMAL_CANDIDATE rootSeed=" + row.seed +
                    " ordinal=" + ordinal +
                    " candidateSeed=" + candidate.getDescriptor().getRootSeed() +
                    " manifest=" + request.candidateManifest(ordinal));
            }
        }
    }

    private static void resolve(Map<Long, Row> rows, long seed) {
        Rb30Plan plan = Rb30Plan.resolve(seed);
        rows.put(Long.valueOf(seed), new Row(seed, plan));
    }

    private static boolean hasAllTargets(Map<Long, Row> rows) {
        for (int target : TARGETS) {
            boolean found = false;
            for (Row row : rows.values()) {
                if (row.plan.physicalPackageCount() == target) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private static boolean containsCount(List<Row> rows, int count) {
        for (Row row : rows) if (row.plan.physicalPackageCount() == count) return true;
        return false;
    }

    private static Row preferredDistinct(Map<Long, Row> rows, int target) {
        for (long seed : new long[] {13L, 64L}) {
            Row row = rows.get(Long.valueOf(seed));
            if (row != null && row.plan.physicalPackageCount() == target) return row;
        }
        return null;
    }

    private static Row lowestFallback(Map<Long, Row> rows, int target) {
        for (long seed = 0; seed <= 20; seed++) {
            Row row = rows.get(Long.valueOf(seed));
            if (row != null && row.plan.physicalPackageCount() == target) return row;
        }
        return null;
    }

    private static String seeds(Map<Long, Row> rows) {
        StringBuilder result = new StringBuilder();
        for (Long seed : rows.keySet()) {
            if (result.length() > 0) result.append(',');
            result.append(seed.longValue());
        }
        return result.toString();
    }
}