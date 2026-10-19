package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Bounded, read-only capture of actual candidate-1 placement visits. */
final class PcbPassiveChoiceObserver {
    static final String SCHEMA = "pcb-passive-choice-observer-v1";
    static final String MARKER = "PCB_PASSIVE_CHOICE_CAPTURE_V1";
    static final int MAX_ITEMS = 128;
    static final int MAX_VISITS = 60000;
    static final int MAX_EXPORT_VISITS = 512;
    private static Capture active;

    private PcbPassiveChoiceObserver() { }

    static synchronized Capture start(long rootSeed, long layoutSeed,
            int placementVersion, Capture reference) {
        if (active != null)
            throw new IllegalStateException("A passive choice capture is already active");
        if (placementVersion != 15 && placementVersion != 16)
            throw new IllegalArgumentException("Unsupported passive choice observation version");
        if (reference != null && (!reference.stopped || reference.placementVersion != 15 ||
                reference.rootSeed != rootSeed || reference.layoutSeed != layoutSeed ||
                reference.modeTrial))
            throw new IllegalArgumentException("Passive choice reference is stale or mismatched");
        if (placementVersion == 15 && reference != null)
            throw new IllegalArgumentException("Old capture cannot have a reference");
        if (placementVersion == 16 && reference == null)
            throw new IllegalArgumentException("Trial capture requires its paired old capture");
        active = new Capture(rootSeed, layoutSeed, placementVersion, reference);
        if(reference!=null && !reference.complete())active.addReason("REFERENCE_INCOMPLETE");
        return active;
    }

    static synchronized void stop(Capture capture) {
        if (capture == null || active != capture || capture.stopped)
            throw new IllegalStateException("Stale passive choice capture handle");
        capture.stopped = true;
        if (capture.planBeginCount != 1 || capture.planEndCount != 1 ||
                capture.planErrorCount != 0 || !capture.planBegan || !capture.planEnded)
            capture.addReason("PLAN_LIFECYCLE_INCOMPLETE");
        if (capture.itemOverflow || capture.visitOverflow || capture.exportOverflow)
            capture.addReason("CAPTURE_BOUND_EXCEEDED");
        if (capture.declaredItems != capture.expectedPartCount)
            capture.addReason("DECLARED_ITEM_COUNT_MISMATCH");
        if (capture.placedItems != capture.expectedPartCount)
            capture.addReason("PLACED_ITEM_COUNT_MISMATCH");
        if (capture.current != null)
            capture.addReason("ITEM_FRAME_NOT_ENDED");
        if (!capture.itemFramesComplete())
            capture.addReason("ITEM_FRAMES_INCOMPLETE");
        if (capture.sizing == null || capture.outline == null || capture.prePlan == null ||
                capture.postPlan == null)
            capture.addReason("REQUIRED_FRAME_MISSING");
        active = null;
    }

    /** Called once at the existing planner entry; other candidates stay unobserved. */
    static synchronized Capture beginPlan(long layoutSeed, int candidate,
            int policyVersion, int densityVersion, int partCount) {
        Capture capture = active;
        if (capture == null || candidate != 1 || layoutSeed != capture.layoutSeed)
            return null;
        capture.planBegin(layoutSeed, candidate, policyVersion, densityVersion, partCount);
        return capture;
    }

    static final class Capture {
        final long rootSeed, layoutSeed;
        final int placementVersion, policyVersion;
        final boolean modeTrial;
        final Capture reference;
        final Vector<ItemRecord> items = new Vector<ItemRecord>();
        final Vector<String> reasons = new Vector<String>();
        int candidate = -1, algorithmCandidate = -1, densityVersion = -1;
        int expectedPartCount = -1, declaredItems, placedItems, planBeginCount, planEndCount,
            planErrorCount, actualVisitCount;
        boolean started = true, stopped, planBegan, planEnded, itemOverflow, visitOverflow,
            exportOverflow;
        String planErrorType;
        Boolean sizingEqual;
        Sizing sizing;
        Outline outline;
        PlanSnapshot prePlan, postPlan;
        FirstDivergence firstDivergence;
        SelectedDivergence firstSelectedFootprintDivergence;
        Vector<VisitEvent> divergenceOldVisits, divergenceTrialVisits;
        ItemRecord current;
        Vector<VisitEvent> currentVisits;
        int currentVisitCount;
        String currentGuidanceKey;
        int currentPlaceOrdinal = -1;
        boolean currentFallbackSearch;
        FallbackSnapshot currentFallbackBounds;

        Capture(long root, long layout, int version, Capture ref) {
            rootSeed = root; layoutSeed = layout; placementVersion = version;
            policyVersion = version == 16 ? 1 : 0; reference = ref;
            modeTrial = ref != null;
        }

        void planBegin(long seed, int requestedCandidate, int policy, int density, int parts) {
            planBeginCount++;
            if (planBeginCount != 1) addReason("DUPLICATE_PLAN_BEGIN");
            if (seed != layoutSeed || requestedCandidate != 1 || policy != policyVersion ||
                    density != 14 || parts < 1 || parts > MAX_ITEMS)
                addReason("PLAN_INPUT_MISMATCH");
            candidate = requestedCandidate;
            densityVersion = density;
            expectedPartCount = parts;
            planBegan = true;
        }

        void planParameters(int requestedCandidate, int algorithm, int parts) {
            candidate = requestedCandidate; algorithmCandidate = algorithm;
            if (expectedPartCount != parts) addReason("PLAN_PART_COUNT_CHANGED");
        }

        void recordDeclaration(int ordinal, PcbPlacementConstraints.Part declaration,
                PcbPlacementPlanner.Item item,PcbPlacementConstraints.Anchor resolvedAnchor) {
            declaredItems++;
            if (declaration == null || item == null || ordinal < 0) {
                addReason("INVALID_ITEM_DECLARATION"); return;
            }
            if (items.size() >= MAX_ITEMS) {
                itemOverflow = true; return;
            }
            PcbFootprint source = item.footprint;
            PcbComponentPlacement sourcePlacement = source.getPlacement();
            PhysicalPackage physicalPackage = sourcePlacement.getPhysicalPackage();
            ItemRecord record = new ItemRecord();
            record.componentId = declaration.componentId;
            record.declarationOrdinal = ordinal;
            record.domainId = declaration.domainId;
            record.regionId = declaration.regionId;
            record.declaredAnchor = declaration.anchor.name();
            record.anchor = resolvedAnchor.name();
            record.packageId = physicalPackage.getId();
            record.canonicalPassive = PcbPassivePosePolicy.supportsPackage(physicalPackage);
            record.passivePolicyEligible = item.passivePoseAlternative != null;
            record.sourceGeometryKey = geometryKey(source);
            record.sourceGeometryFingerprint = source.geometryFingerprint();
            record.sourceRotation = sourcePlacement.getRotation().name();
            record.sourceSide = sourcePlacement.getMountingSide().name();
            record.sourceX = sourcePlacement.getX(); record.sourceY = sourcePlacement.getY();
            record.sourceWidth = sourcePlacement.getWidth();
            record.sourceHeight = sourcePlacement.getHeight();
            record.accessMargin = declaration.accessMargin;
            record.envelopeTOP0 = copy(item.envelope);
            record.alternateGeometryKey = item.passivePoseAlternative == null ? null :
                geometryKey(item.passivePoseAlternative);
            record.alternateGeometryFingerprint = item.passivePoseAlternative == null ? null :
                item.passivePoseAlternative.geometryFingerprint();
            record.alternateRotation = item.passivePoseAlternative == null ? null :
                item.passivePoseAlternative.getPlacement().getRotation().name();
            record.envelopeTOP90 = item.alternateEnvelope == null ? null : copy(item.alternateEnvelope);
            record.sizingDemandArea = item.sizingDemandArea;
            record.sizingEnvelopeWidth = item.sizingEnvelopeWidth;
            record.sizingEnvelopeHeight = item.sizingEnvelopeHeight;
            ItemRecord old = referenceItem(record.componentId);
            if (modeTrial && old != null) {
                record.sourceGeometryEqual = record.sourceGeometryKey.equals(old.sourceGeometryKey) &&
                    record.sourceGeometryFingerprint.equals(old.sourceGeometryFingerprint) &&
                    record.sourceRotation.equals(old.sourceRotation) &&
                    record.sourceSide.equals(old.sourceSide) &&
                    record.sourceWidth == old.sourceWidth && record.sourceHeight == old.sourceHeight;
                if (!record.sourceGeometryEqual) noteDivergence("source-geometry",
                    record.componentId, null);
                boolean itemSizeEqual=record.sizingDemandArea==old.sizingDemandArea&&
                    record.sizingEnvelopeWidth==old.sizingEnvelopeWidth&&
                    record.sizingEnvelopeHeight==old.sizingEnvelopeHeight&&
                    rectEquals(record.envelopeTOP0,old.envelopeTOP0)&&
                    rectEquals(record.envelopeTOP90,old.envelopeTOP90);
                if(!itemSizeEqual)sizingEqual=Boolean.FALSE;
            } else record.sourceGeometryEqual = !modeTrial;
            items.add(record);
            if (items.size() != ordinal + 1)
                addReason("ITEM_DECLARATION_ORDER_MISMATCH");
        }

        void recordSizingTotals(int parts, long demandArea, long courtyardArea,
                int maxWidth, int maxHeight, int anchorWidth) {
            sizing = new Sizing(parts, demandArea, courtyardArea, maxWidth, maxHeight,
                anchorWidth);
            if (modeTrial) {
                Sizing old = reference.sizing;
                if(sizingEqual==null)sizingEqual=Boolean.TRUE;
                sizingEqual=Boolean.valueOf(sizingEqual.booleanValue()&&old!=null&&sizing.equals(old));
                if (!sizingEqual.booleanValue()) {
                    noteDivergence("sizing", null, null);
                }
            }
            if(modeTrial && reference!=null && !reference.complete())addReason("REFERENCE_INCOMPLETE");
        }

        void recordOutline(long targetArea, int aspectIndex, double aspect,
                double compactFactor, double spacingFactor, int contentWidth,
                int contentHeight, Rectangle rawOutline, int boundaryX,
                double boundaryFirstDemand, double boundaryTotalDemand,
                double boundaryFraction) {
            outline = new Outline(targetArea, aspectIndex, bits(aspect), bits(compactFactor),
                bits(spacingFactor), contentWidth, contentHeight, copy(rawOutline),
                (long)rawOutline.width * rawOutline.height, boundaryX,
                boundaryX < 0 ? null : bits(boundaryFirstDemand),
                boundaryX < 0 ? null : bits(boundaryTotalDemand),
                boundaryX < 0 ? null : bits(boundaryFraction));
            if (modeTrial) {
                Outline old = reference.outline;
                if (old == null) noteDivergence("outline", null, null);
                else {
                    if (!outline.sameRawOutline(old)) noteDivergence("outline", null, null);
                    else if (!outline.sameBoundary(old)) noteDivergence("boundary", null, null);
                }
            }
        }

        void beginItem(PcbFootprint source, Vector<PcbFootprint> placed,
                boolean anchor, Double hintXFraction, Double hintYFraction, Point connected,
                int preferredBeforeGroupX, int preferredBeforeGroupY,
                int preferredX, int preferredY, Rectangle group, int direction,
                int baseX, int baseY, boolean boundaryPackage, int boundaryX,
                PcbSupportPairPlacement.Pair support, Vector<Point> vacancies,
                Point vacancyPreference, Rectangle vacancyPocket,int evaluationsBefore) {
            String componentId = source.getPlacement().getComponentId();
            current = findItem(componentId);
            if (current == null) {
                addReason("PLACED_ITEM_WITHOUT_DECLARATION"); return;
            }
            if (current.placeOrdinal != null) {
                addReason("DUPLICATE_ITEM_PLACEMENT"); current = null; return;
            }
            currentPlaceOrdinal = placedItems++;
            current.placeOrdinal = currentPlaceOrdinal;
            current.evaluationsBefore = evaluationsBefore;
            current.orderKind = anchor ? "anchor" : "remaining";
            currentVisits = modeTrial ? new Vector<VisitEvent>() : current.visits;
            currentVisitCount = 0;
            currentFallbackSearch = false; currentFallbackBounds = null;
            current.prefix = prefix(placed);
            Guidance value = new Guidance();
            value.hintXBits = hintXFraction == null ? null : bits(hintXFraction.doubleValue());
            value.hintYBits = hintYFraction == null ? null : bits(hintYFraction.doubleValue());
            value.connectedX = connected.x; value.connectedY = connected.y;
            value.preferredBeforeGroupX = preferredBeforeGroupX;
            value.preferredBeforeGroupY = preferredBeforeGroupY;
            value.preferredX = preferredX; value.preferredY = preferredY;
            value.group = group == null ? null : copy(group);
            value.direction = direction; value.baseX = baseX; value.baseY = baseY;
            value.boundaryPackage = boundaryPackage; value.boundaryX = boundaryX;
            value.support = support == null ? null : new SupportSnapshot(support);
            value.vacancies = pointSnapshots(vacancies);
            value.vacancyPreference = vacancyPreference == null ? null :
                new PointSnapshot(vacancyPreference.x, vacancyPreference.y);
            value.vacancyPocket = vacancyPocket == null ? null : copy(vacancyPocket);
            value.sourceEnvelope = copy(current.envelopeTOP0);
            current.guidance = value;
            currentGuidanceKey = value.json();

            if (modeTrial) {
                ItemRecord old = referenceItem(componentId);
                current.reference = old;
                current.samePlaceOrdinal = old != null && old.placeOrdinal != null &&
                    old.placeOrdinal.intValue() == currentPlaceOrdinal &&
                    old.orderKind.equals(current.orderKind);
                current.prefixEqual = old != null && samePrefix(current.prefix, old.prefix);
                current.guidanceEqual = old != null && old.guidance != null &&
                    currentGuidanceKey.equals(old.guidance.json());
                if (!current.samePlaceOrdinal) noteDivergence("item-order", componentId, null);
                else if (!current.prefixEqual) noteDivergence("placed-prefix", componentId, null);
                else if (!current.guidanceEqual) noteDivergence("guidance", componentId, null);
            }
        }

        int recordVisit(String visitKey, String kind, int ordinal, int ring,
                int index, int fallbackOrdinal, int requestedX, int requestedY,
                PcbFootprint trial, boolean legal) {
            if (current == null || trial == null) {
                addReason("VISIT_WITHOUT_ITEM"); return -1;
            }
            currentVisitCount++;
            actualVisitCount++;
            String rotation = trial.getPlacement().getRotation().name();
            PoseStats stats = current.stats(rotation);
            if (stats == null) {
                addReason("UNSUPPORTED_VISIT_ROTATION"); return -1;
            }
            stats.visits++;
            if (legal) stats.legal++;
            if ("ring".equals(kind)) {
                stats.ringVisits++; if (legal) stats.ringLegal++;
            } else {
                stats.fallbackVisits++; if (legal) stats.fallbackLegal++;
                if (legal && stats.firstFallbackLegalKey == null)
                    stats.firstFallbackLegalKey = visitKey;
            }
            if (actualVisitCount > MAX_VISITS) {
                visitOverflow = true; return -1;
            }
            VisitEvent event = new VisitEvent(visitKey, kind, ordinal, ring, index,
                fallbackOrdinal, requestedX, requestedY, trial.getPlacement().getX(),
                trial.getPlacement().getY(), rotation, geometryKey(trial), legal);
            currentVisits.add(event);
            compareVisit(event);
            return currentVisits.size() - 1;
        }

        void recordScore(int visitIndex, double score, boolean incumbentUpdate) {
            if (current == null || visitIndex < 0 || visitIndex >= currentVisits.size()) {
                if (Double.isNaN(score) || Double.isInfinite(score))
                    addReason("NONFINITE_ACTUAL_SCORE");
                return;
            }
            if (Double.isNaN(score) || Double.isInfinite(score)) {
                addReason("NONFINITE_ACTUAL_SCORE"); return;
            }
            VisitEvent event = currentVisits.get(visitIndex);
            event.scored = true; event.scoreBits = bits(score);
            event.incumbentUpdate = incumbentUpdate;
            PoseStats stats = current.stats(event.rotation);
            stats.scored++;
            if ("ring".equals(event.kind)) stats.ringScored++;
            else stats.fallbackScored++;
            if (stats.minimum == null || score < stats.minimum.doubleValue()) {
                stats.minimum = Double.valueOf(score);
                stats.minimumScoreBits = bits(score);
                stats.minimumVisitKey = event.visitKey;
            }
            if (incumbentUpdate) stats.incumbentUpdates++;
            compareScoredVisit(event);
        }

        void markFallbackSearch() { if (current != null) currentFallbackSearch = true; }

        void recordFallbackBounds(int minX,int maxX,int minY,int maxY,int columns,
                int rows,long total,int start) {
            if (current != null) currentFallbackBounds = new FallbackSnapshot(minX,maxX,
                minY,maxY,columns,rows,total,start);
        }

        void endItem(PcbFootprint selected, double selectedScore,int evaluationsAfter) {
            if (current == null) return;
            current.evaluationDelta = evaluationsAfter-current.evaluationsBefore;
            current.selected = selected == null ? null : new SelectedSnapshot(selected,
                selectedScore, currentFallbackSearch, currentVisitCount);
            current.actualVisitCount = currentVisitCount;
            current.fallbackBounds = currentFallbackBounds;
            ItemRecord old = current.reference;
            if (modeTrial && old != null) {
                current.selectedKey = current.selected == null ? null :
                    current.selected.selectedKey;
                String oldKey = old.selected == null ? null : old.selected.selectedKey;
                if (!same(current.selectedKey, oldKey)) {
                    noteDivergence("selected-footprint", current.componentId, null);
                    if (firstSelectedFootprintDivergence == null)
                        recordSelectedDivergence(old, current);
                }
                recordReferenceOrigin(old, current);
            }
            if (!modeTrial && current.selected != null)
                current.selectedKey = current.selected.selectedKey;
            if (currentVisits.size() != currentVisitCount && currentVisitCount <= MAX_VISITS)
                addReason("ITEM_VISIT_CAPTURE_MISMATCH");
            current = null; currentVisits = null; currentGuidanceKey = null;
            currentVisitCount = 0; currentPlaceOrdinal = -1;
            currentFallbackSearch = false; currentFallbackBounds = null;
        }

        void recordPlanSnapshot(PcbPlacementPlanner.Plan plan, boolean beforeCompaction) {
            if (plan == null) { addReason("NULL_PLAN_FRAME"); return; }
            PlanSnapshot snapshot = new PlanSnapshot(plan);
            if (beforeCompaction) prePlan = snapshot;
            else {
                postPlan = snapshot;
                if (modeTrial && reference.postPlan != null &&
                        !snapshot.samePhysicalPlan(reference.postPlan))
                    noteDivergence("compaction-output", null, null);
            }
        }

        void planEnd(PcbPlacementPlanner.Plan plan) {
            planEndCount++;
            if (planEndCount != 1) addReason("DUPLICATE_PLAN_END");
            planEnded = true;
        }

        void planError(Throwable error) {
            planErrorCount++;
            if (error == null) addReason("PLAN_ERROR_WITHOUT_TYPE");
            else planErrorType=error.getClass().getName();
        }

        boolean complete() {
            return stopped && planBeginCount == 1 && planEndCount == 1 &&
                planErrorCount == 0 && planBegan && planEnded && !itemOverflow &&
                !visitOverflow && !exportOverflow && reasons.isEmpty() &&
                sizing != null && outline != null && prePlan != null && postPlan != null &&
                declaredItems == expectedPartCount && items.size() == expectedPartCount &&
                placedItems == expectedPartCount && current == null && itemFramesComplete();
        }

        private boolean itemFramesComplete() {
            if (items.size() != expectedPartCount) return false;
            for (ItemRecord item : items)
                if (item.placeOrdinal == null || item.selected == null ||
                        item.actualVisitCount < 0) return false;
            return true;
        }

        String json() {
            StringBuilder out = new StringBuilder(8192);
            out.append('{');
            field(out, "schema", SCHEMA); comma(out); field(out, "marker", MARKER);
            comma(out); field(out, "rootSeed", Long.toString(rootSeed));
            comma(out); field(out, "layoutSeed", Long.toString(layoutSeed));
            comma(out); number(out, "interpretation", placementVersion);
            comma(out); number(out, "policyVersion", policyVersion);
            comma(out); number(out, "densityVersion", densityVersion);
            comma(out); number(out, "candidate", candidate);
            comma(out); number(out, "algorithmCandidate", algorithmCandidate);
            comma(out); field(out, "mode", modeTrial ? "trial" : "old");
            comma(out); bool(out, "referenceAvailable", reference != null);
            comma(out); number(out, "expectedPartCount", expectedPartCount);
            comma(out); out.append("\"lifecycle\":{");
            bool(out, "started", started); comma(out); bool(out, "stopped", stopped);
            comma(out); number(out, "planBeginCount", planBeginCount);
            comma(out); number(out, "planEndCount", planEndCount);
            comma(out); number(out, "planErrorCount", planErrorCount);
            comma(out); if(planErrorType==null)nullableField(out,"planErrorType",null);else field(out,"planErrorType",planErrorType);
            comma(out); bool(out, "planBegan", planBegan);
            comma(out); bool(out, "planEnded", planEnded); out.append('}');
            comma(out); out.append("\"bounds\":{");
            number(out, "maxItems", MAX_ITEMS); comma(out); number(out, "itemCount", declaredItems);
            comma(out); number(out, "maxVisits", MAX_VISITS);
            comma(out); number(out, "actualVisitCount", actualVisitCount);
            comma(out); bool(out, "itemOverflow", itemOverflow);
            comma(out); bool(out, "visitOverflow", visitOverflow);
            comma(out); number(out, "maxExportVisitsPerVersion", MAX_EXPORT_VISITS);
            comma(out); bool(out, "exportOverflow", exportOverflow); out.append('}');
            comma(out); field(out, "status", complete() ? "COMPLETE" : "INCONCLUSIVE");
            comma(out); strings(out, "reasonCodes", reasons);
            comma(out); out.append("\"sizing\":"); if (sizing == null) out.append("null"); else sizing.json(out);
            comma(out); out.append("\"sizingEqual\":"); if(sizingEqual==null)out.append("null");else out.append(sizingEqual.booleanValue());
            comma(out); out.append("\"outline\":"); if (outline == null) out.append("null"); else outline.json(out);
            comma(out); out.append("\"compaction\":{");
            out.append("\"pre\":"); if (prePlan == null) out.append("null"); else prePlan.json(out);
            comma(out); out.append("\"post\":"); if (postPlan == null) out.append("null"); else postPlan.json(out);
            out.append('}');
            comma(out); out.append("\"firstDivergence\":");
            if (firstDivergence == null) out.append("null"); else firstDivergence.json(out);
            comma(out); out.append("\"items\":[");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) comma(out); items.get(i).json(out);
            }
            out.append(']');
            comma(out); out.append("\"firstSelectedFootprintDivergence\":");
            if (firstSelectedFootprintDivergence == null) out.append("null");
            else firstSelectedFootprintDivergence.json(out);
            comma(out); out.append("\"divergenceVisits\":");
            if (divergenceOldVisits == null || divergenceTrialVisits == null) out.append("null");
            else {
                out.append('{'); array(out, "old", divergenceOldVisits);
                comma(out); array(out, "trial", divergenceTrialVisits); out.append('}');
            }
            out.append('}'); return out.toString();
        }

        private void compareVisit(VisitEvent event) {
            if (!modeTrial || current.reference == null) return;
            if (!contextComparable(current)) return;
            VisitEvent old = current.reference.findVisit(event.visitKey);
            if (old == null) {
                noteDivergence("visit-sequence", current.componentId, event.visitKey); return;
            }
            if (!old.geometryKey.equals(event.geometryKey))
                noteDivergence("visit-template", current.componentId, event.visitKey);
            else if (old.requestedX != event.requestedX || old.requestedY != event.requestedY)
                noteDivergence("requested-origin", current.componentId, event.visitKey);
            else if (old.x != event.x || old.y != event.y)
                noteDivergence("grid-clipping", current.componentId, event.visitKey);
            else if (old.legal != event.legal)
                noteDivergence("fit-result", current.componentId, event.visitKey);
        }

        private void compareScoredVisit(VisitEvent event) {
            if (!modeTrial || current == null || current.reference == null ||
                    !contextComparable(current)) return;
            VisitEvent old = current.reference.findVisit(event.visitKey);
            if (old == null) return;
            if (old.scored != event.scored || old.scoreBits != null &&
                    !old.scoreBits.equals(event.scoreBits))
                noteDivergence("score-result", current.componentId, event.visitKey);
            else if (old.incumbentUpdate != event.incumbentUpdate)
                noteDivergence("incumbent-update", current.componentId, event.visitKey);
        }

        private boolean contextComparable(ItemRecord item) {
            if (!item.sourceGeometryEqual || !item.samePlaceOrdinal || !item.prefixEqual ||
                    !item.guidanceEqual || sizing == null || reference.sizing == null ||
                    outline == null || reference.outline == null) return false;
            return outline.sameRawOutline(reference.outline) && outline.sameBoundary(reference.outline);
        }

        private void recordSelectedDivergence(ItemRecord old, ItemRecord trial) {
            SelectedDivergence value = new SelectedDivergence();
            value.componentId = trial.componentId;
            value.oldPlaceOrdinal = old.placeOrdinal;
            value.trialPlaceOrdinal = trial.placeOrdinal;
            value.oldSelectedKey = old.selected == null ? null : old.selected.selectedKey;
            value.trialSelectedKey = trial.selected == null ? null : trial.selected.selectedKey;
            value.sourceGeometryEqual = trial.sourceGeometryEqual;
            value.sizingEqual = Boolean.TRUE.equals(sizingEqual);
            value.outlineEqual = outline != null && reference.outline != null && outline.sameRawOutline(reference.outline);
            value.boundaryEqual = outline != null && reference.outline != null && outline.sameBoundary(reference.outline);
            value.samePlaceOrdinal = trial.samePlaceOrdinal;
            value.prefixEqual = trial.prefixEqual;
            value.guidanceEqual = trial.guidanceEqual;
            value.comparable = value.sourceGeometryEqual && value.outlineEqual && value.boundaryEqual &&
                value.samePlaceOrdinal && value.prefixEqual && value.guidanceEqual;
            value.reasonCodes = new Vector<String>();
            if (!value.sourceGeometryEqual) value.reasonCodes.add("SOURCE_GEOMETRY_DIFF");
            if (!value.sizingEqual) value.reasonCodes.add("SIZING_DIFF");
            if (!value.outlineEqual) value.reasonCodes.add("OUTLINE_DIFF");
            if (!value.boundaryEqual) value.reasonCodes.add("BOUNDARY_DIFF");
            if (!value.samePlaceOrdinal) value.reasonCodes.add("ITEM_ORDER_DIFF");
            if (!value.prefixEqual) value.reasonCodes.add("PLACED_PREFIX_DIFF");
            if (!value.guidanceEqual) value.reasonCodes.add("GUIDANCE_DIFF");
            value.oldVisitCount = old.actualVisitCount;
            value.trialVisitCount = trial.actualVisitCount;
            value.visitArraysComplete = old.visits.size() == old.actualVisitCount &&
                currentVisits.size() == currentVisitCount && old.actualVisitCount <= MAX_EXPORT_VISITS &&
                currentVisitCount <= MAX_EXPORT_VISITS;
            value.exportOverflow = !value.visitArraysComplete &&
                (old.actualVisitCount > MAX_EXPORT_VISITS || currentVisitCount > MAX_EXPORT_VISITS);
            if (value.visitArraysComplete) {
                divergenceOldVisits = new Vector<VisitEvent>(old.visits);
                divergenceTrialVisits = new Vector<VisitEvent>(currentVisits);
            } else {
                value.visitArraysComplete = false;
                divergenceOldVisits = null; divergenceTrialVisits = null;
                if (value.exportOverflow) {
                    exportOverflow = true; addReason("DIVERGENCE_EXPORT_LIMIT");
                }
            }
            firstSelectedFootprintDivergence = value;
        }

        private void recordReferenceOrigin(ItemRecord old, ItemRecord trial) {
            if (old.selected == null) return;
            ReferenceOrigin value = new ReferenceOrigin();
            value.itemFound = true;
            value.oldSelectedRotation = old.selected.rotation;
            value.oldSelectedX = old.selected.x; value.oldSelectedY = old.selected.y;
            value.oldSelectedGeometryKey = old.selected.geometryKey;
            for (VisitEvent event : currentVisits) {
                if (event.x == old.selected.x && event.y == old.selected.y) {
                    if ("DEG_0".equals(event.rotation)) value.trialSameOriginDEG0++;
                    if ("DEG_90".equals(event.rotation)) value.trialSameOriginDEG90++;
                    if ("DEG_180".equals(event.rotation)) value.trialSameOriginDEG180++;
                    if ("DEG_270".equals(event.rotation)) value.trialSameOriginDEG270++;
                    if (old.selected.rotation.equals(event.rotation) &&
                            old.selected.geometryKey.equals(event.geometryKey))
                        value.trialOldGeometryOriginVisits++;
                }
            }
            value.trialOldGeometryOriginVisited = value.trialOldGeometryOriginVisits > 0;
            trial.referenceOrigin = value;
        }

        private void noteDivergence(String stage, String componentId, String visitKey) {
            if (modeTrial && firstDivergence == null)
                firstDivergence = new FirstDivergence(stage, componentId, visitKey);
        }

        private ItemRecord referenceItem(String componentId) {
            if (reference == null) return null;
            return reference.findItem(componentId);
        }

        private ItemRecord findItem(String componentId) {
            for (ItemRecord item : items)
                if (item.componentId.equals(componentId)) return item;
            return null;
        }

        private void addReason(String reason) {
            if (!reasons.contains(reason)) reasons.add(reason);
        }
    }

    private static final class ItemRecord {
        String componentId, domainId, regionId, anchor, declaredAnchor, packageId, sourceGeometryKey,
            sourceGeometryFingerprint, sourceRotation, sourceSide, alternateGeometryKey,
            alternateGeometryFingerprint, alternateRotation, orderKind, selectedKey;
        int declarationOrdinal, sourceX, sourceY, sourceWidth, sourceHeight,
            sizingEnvelopeWidth, sizingEnvelopeHeight, actualVisitCount, accessMargin,
            evaluationsBefore,evaluationDelta;
        boolean canonicalPassive, passivePolicyEligible, sourceGeometryEqual,
            samePlaceOrdinal, prefixEqual, guidanceEqual;
        long sizingDemandArea;
        Rectangle envelopeTOP0, envelopeTOP90;
        Integer placeOrdinal;
        Vector<PrefixEntry> prefix = new Vector<PrefixEntry>();
        Vector<VisitEvent> visits = new Vector<VisitEvent>();
        Guidance guidance;
        SelectedSnapshot selected;
        ReferenceOrigin referenceOrigin;
        ItemRecord reference;
        FallbackSnapshot fallbackBounds;
        final PoseStats[] stats = { new PoseStats("DEG_0"), new PoseStats("DEG_90"),
            new PoseStats("DEG_180"), new PoseStats("DEG_270") };

        PoseStats stats(String rotation) {
            for (PoseStats value : stats) if (value.rotation.equals(rotation)) return value;
            return null;
        }

        VisitEvent findVisit(String key) {
            for (VisitEvent event : visits) if (event.visitKey.equals(key)) return event;
            return null;
        }

        void json(StringBuilder out) {
            out.append('{'); field(out,"componentId",componentId); comma(out);
            number(out,"declarationOrdinal",declarationOrdinal); comma(out);
            field(out,"domainId",domainId); comma(out); field(out,"regionId",regionId); comma(out);
            field(out,"anchor",anchor); comma(out); field(out,"declaredAnchor",declaredAnchor); comma(out); field(out,"packageId",packageId); comma(out);
            bool(out,"canonicalPassive",canonicalPassive); comma(out);
            bool(out,"passivePolicyEligible",passivePolicyEligible); comma(out);
            field(out,"sourceGeometryKey",sourceGeometryKey); comma(out);
            field(out,"sourceGeometryFingerprint",sourceGeometryFingerprint); comma(out);
            field(out,"sourceRotation",sourceRotation); comma(out); field(out,"sourceSide",sourceSide); comma(out);
            number(out,"sourceX",sourceX); comma(out); number(out,"sourceY",sourceY); comma(out);
            number(out,"sourceWidth",sourceWidth); comma(out); number(out,"sourceHeight",sourceHeight); comma(out);
            number(out,"accessMargin",accessMargin); comma(out);
            nullableField(out,"alternateGeometryKey",alternateGeometryKey); comma(out);
            nullableField(out,"alternateGeometryFingerprint",alternateGeometryFingerprint); comma(out);
            nullableField(out,"alternateRotation",alternateRotation); comma(out);
            out.append("\"envelopeTOP0\":"); rect(out,envelopeTOP0); comma(out);
            out.append("\"envelopeTOP90\":"); if(envelopeTOP90==null)out.append("null");else rect(out,envelopeTOP90); comma(out);
            field(out,"sizingDemandArea",Long.toString(sizingDemandArea)); comma(out);
            number(out,"sizingEnvelopeWidth",sizingEnvelopeWidth); comma(out);
            number(out,"sizingEnvelopeHeight",sizingEnvelopeHeight); comma(out);
            out.append("\"placeOrdinal\":"); if(placeOrdinal==null)out.append("null");else out.append(placeOrdinal); comma(out);
            nullableField(out,"orderKind",orderKind); comma(out);
            number(out,"evaluationDelta",evaluationDelta); comma(out);
            out.append("\"prefix\":["); for(int i=0;i<prefix.size();i++){if(i>0)comma(out);prefix.get(i).json(out);} out.append(']'); comma(out);
            out.append("\"guidance\":"); if(guidance==null)out.append("null");else guidance.json(out); comma(out);
            out.append("\"selectedSearch\":"); if(selected==null)out.append("null");else selected.json(out); comma(out);
            out.append("\"fallback\":"); if(fallbackBounds==null)out.append("null");else fallbackBounds.json(out); comma(out);
            out.append("\"poseStats\":{");
            for(int i=0;i<stats.length;i++){if(i>0)comma(out);out.append('"').append(stats[i].rotation).append("\":");stats[i].json(out);} out.append('}'); comma(out);
            out.append("\"referenceOrigin\":"); if(referenceOrigin==null)out.append("null");else referenceOrigin.json(out);
            out.append('}');
        }
    }

    private static final class Sizing {
        final int sourceCount,maxWidth,maxHeight,anchorWidth;
        final long demandArea,courtyardArea;
        Sizing(int count,long demand,long court,int width,int height,int anchor) {
            sourceCount=count;demandArea=demand;courtyardArea=court;maxWidth=width;maxHeight=height;anchorWidth=anchor;
        }
        boolean equals(Sizing other) { return other!=null&&sourceCount==other.sourceCount&&demandArea==other.demandArea&&courtyardArea==other.courtyardArea&&maxWidth==other.maxWidth&&maxHeight==other.maxHeight&&anchorWidth==other.anchorWidth; }
        void json(StringBuilder out) { out.append('{');number(out,"sourceCount",sourceCount);comma(out);field(out,"demandArea",Long.toString(demandArea));comma(out);field(out,"courtyardArea",Long.toString(courtyardArea));comma(out);number(out,"maxWidth",maxWidth);comma(out);number(out,"maxHeight",maxHeight);comma(out);number(out,"anchorWidth",anchorWidth);out.append('}'); }
    }

    private static final class Outline {
        final long targetArea,outlineArea;
        final int aspectIndex,contentWidth,contentHeight,boundaryX;
        final String aspectBits,compactFactorBits,spacingFactorBits,boundaryFirstDemandBits,boundaryTotalDemandBits,boundaryFractionBits;
        final Rectangle raw;
        Outline(long target,int index,String aspect,String compact,String spacing,int cw,int ch,Rectangle outline,long area,int bx,String first,String total,String fraction) {
            targetArea=target;aspectIndex=index;aspectBits=aspect;compactFactorBits=compact;spacingFactorBits=spacing;contentWidth=cw;contentHeight=ch;raw=outline;outlineArea=area;boundaryX=bx;boundaryFirstDemandBits=first;boundaryTotalDemandBits=total;boundaryFractionBits=fraction;
        }
        boolean sameRawOutline(Outline other) { return other!=null&&raw.x==other.raw.x&&raw.y==other.raw.y&&raw.width==other.raw.width&&raw.height==other.raw.height; }
        boolean sameBoundary(Outline other) { return other!=null&&boundaryX==other.boundaryX&&same(boundaryFirstDemandBits,other.boundaryFirstDemandBits)&&same(boundaryTotalDemandBits,other.boundaryTotalDemandBits)&&same(boundaryFractionBits,other.boundaryFractionBits); }
        void json(StringBuilder out) { out.append('{');field(out,"targetArea",Long.toString(targetArea));comma(out);number(out,"aspectIndex",aspectIndex);comma(out);field(out,"aspectBits",aspectBits);comma(out);field(out,"compactFactorBits",compactFactorBits);comma(out);field(out,"spacingFactorBits",spacingFactorBits);comma(out);number(out,"contentWidth",contentWidth);comma(out);number(out,"contentHeight",contentHeight);comma(out);out.append("\"outline\":");rect(out,raw);comma(out);field(out,"outlineArea",Long.toString(outlineArea));comma(out);number(out,"boundaryX",boundaryX);comma(out);nullableField(out,"boundaryFirstDemandBits",boundaryFirstDemandBits);comma(out);nullableField(out,"boundaryTotalDemandBits",boundaryTotalDemandBits);comma(out);nullableField(out,"boundaryFractionBits",boundaryFractionBits);out.append('}'); }
    }

    private static final class PlanSnapshot {
        final int candidate,evaluations;
        final long demandArea,courtyardArea;
        final Rectangle outline;
        final Vector<FootprintSnapshot> footprints=new Vector<FootprintSnapshot>();
        PlanSnapshot(PcbPlacementPlanner.Plan plan) { candidate=plan.candidate;evaluations=plan.evaluations;demandArea=plan.demandArea;courtyardArea=plan.courtyardArea;outline=copy(plan.outline);for(PcbFootprint f:plan.footprints)footprints.add(new FootprintSnapshot(f)); }
        boolean samePhysicalPlan(PlanSnapshot other) { if(other==null||candidate!=other.candidate||evaluations!=other.evaluations||demandArea!=other.demandArea||courtyardArea!=other.courtyardArea||!outline.equals(other.outline)||footprints.size()!=other.footprints.size())return false;for(int i=0;i<footprints.size();i++)if(!footprints.get(i).same(other.footprints.get(i)))return false;return true; }
        void json(StringBuilder out) { out.append('{');number(out,"candidate",candidate);comma(out);field(out,"demandArea",Long.toString(demandArea));comma(out);field(out,"courtyardArea",Long.toString(courtyardArea));comma(out);number(out,"evaluations",evaluations);comma(out);out.append("\"outline\":");rect(out,outline);comma(out);out.append("\"footprints\":[");for(int i=0;i<footprints.size();i++){if(i>0)comma(out);footprints.get(i).json(out);}out.append("]}"); }
    }

    private static final class FootprintSnapshot {
        final String componentId,packageId,geometryVariantKey,geometryTransformKey,rotation,side,fingerprint;
        final int x,y,width,height;
        FootprintSnapshot(PcbFootprint f) { PcbComponentPlacement p=f.getPlacement();componentId=p.getComponentId();packageId=p.getPhysicalPackage().getId();geometryVariantKey=p.getGeometryVariantKey();geometryTransformKey=p.getGeometryTransformKey();rotation=p.getRotation().name();side=p.getMountingSide().name();x=p.getX();y=p.getY();width=p.getWidth();height=p.getHeight();fingerprint=f.geometryFingerprint(); }
        String selectedKey() { return packageId+"|"+geometryVariantKey+"|"+geometryTransformKey+"|"+rotation+"|"+side+"|"+width+"x"+height+"@"+x+","+y; }
        boolean same(FootprintSnapshot other) { return other!=null&&selectedKey().equals(other.selectedKey())&&fingerprint.equals(other.fingerprint); }
        void json(StringBuilder out) { out.append('{');field(out,"componentId",componentId);comma(out);field(out,"packageId",packageId);comma(out);field(out,"geometryVariantKey",geometryVariantKey);comma(out);field(out,"geometryTransformKey",geometryTransformKey);comma(out);number(out,"x",x);comma(out);number(out,"y",y);comma(out);number(out,"width",width);comma(out);number(out,"height",height);comma(out);field(out,"rotation",rotation);comma(out);field(out,"side",side);comma(out);field(out,"selectedKey",selectedKey());comma(out);field(out,"geometryFingerprint",fingerprint);out.append('}'); }
    }

    private static final class SelectedSnapshot {
        final String rotation,side,geometryKey,geometryFingerprint,scoreBits,selectedKey;
        final int x,y,visitCount;
        final boolean fallbackUsed;
        SelectedSnapshot(PcbFootprint f,double score,boolean fallback,int visits) { PcbComponentPlacement p=f.getPlacement();rotation=p.getRotation().name();side=p.getMountingSide().name();x=p.getX();y=p.getY();geometryKey=geometryKey(f);geometryFingerprint=f.geometryFingerprint();scoreBits=Double.isNaN(score)||Double.isInfinite(score)?null:bits(score);visitCount=visits;fallbackUsed=fallback;selectedKey=geometryKey+"@"+x+","+y; }
        void json(StringBuilder out) { out.append('{');bool(out,"selected",true);comma(out);number(out,"x",x);comma(out);number(out,"y",y);comma(out);field(out,"rotation",rotation);comma(out);field(out,"side",side);comma(out);field(out,"geometryKey",geometryKey);comma(out);field(out,"geometryFingerprint",geometryFingerprint);comma(out);nullableField(out,"scoreBits",scoreBits);comma(out);bool(out,"fallbackUsed",fallbackUsed);comma(out);number(out,"visitCount",visitCount);comma(out);field(out,"selectedKey",selectedKey);out.append('}'); }
    }

    private static final class PoseStats {
        final String rotation;
        int visits,legal,scored,incumbentUpdates,ringVisits,ringLegal,ringScored,fallbackVisits,fallbackLegal,fallbackScored;
        Double minimum;
        String minimumScoreBits,minimumVisitKey,firstFallbackLegalKey;
        PoseStats(String name){rotation=name;}
        void json(StringBuilder out) { out.append('{');number(out,"visits",visits);comma(out);number(out,"legal",legal);comma(out);number(out,"scored",scored);comma(out);nullableField(out,"minimumScoreBits",minimumScoreBits);comma(out);nullableField(out,"minimumVisitKey",minimumVisitKey);comma(out);number(out,"incumbentUpdates",incumbentUpdates);comma(out);number(out,"ringVisits",ringVisits);comma(out);number(out,"ringLegal",ringLegal);comma(out);number(out,"ringScored",ringScored);comma(out);number(out,"fallbackVisits",fallbackVisits);comma(out);number(out,"fallbackLegal",fallbackLegal);comma(out);number(out,"fallbackScored",fallbackScored);comma(out);nullableField(out,"firstFallbackLegalKey",firstFallbackLegalKey);out.append('}'); }
    }

    private static final class VisitEvent {
        final String visitKey,kind,rotation,geometryKey;
        final int ordinal,ring,index,fallbackOrdinal,requestedX,requestedY,x,y;
        final boolean legal;
        boolean scored,incumbentUpdate;
        String scoreBits;
        VisitEvent(String key,String type,int ord,int r,int i,int fallback,int rx,int ry,int px,int py,String pose,String geometry,boolean fits) { visitKey=key;kind=type;ordinal=ord;ring=r;index=i;fallbackOrdinal=fallback;requestedX=rx;requestedY=ry;x=px;y=py;rotation=pose;geometryKey=geometry;legal=fits; }
        void json(StringBuilder out) { out.append('{');field(out,"visitKey",visitKey);comma(out);field(out,"kind",kind);comma(out);number(out,"ordinal",ordinal);comma(out);nullableNumber(out,"ring",ring<0?null:Integer.valueOf(ring));comma(out);nullableNumber(out,"index",index<0?null:Integer.valueOf(index));comma(out);nullableNumber(out,"fallbackOrdinal",fallbackOrdinal<0?null:Integer.valueOf(fallbackOrdinal));comma(out);number(out,"requestedX",requestedX);comma(out);number(out,"requestedY",requestedY);comma(out);number(out,"x",x);comma(out);number(out,"y",y);comma(out);field(out,"rotation",rotation);comma(out);field(out,"geometryKey",geometryKey);comma(out);bool(out,"legal",legal);comma(out);bool(out,"scored",scored);comma(out);nullableField(out,"scoreBits",scoreBits);comma(out);bool(out,"incumbentUpdate",incumbentUpdate);out.append('}'); }
    }

    private static final class PrefixEntry {
        final String componentId,selectedKey;
        PrefixEntry(PcbFootprint f) { FootprintSnapshot s=new FootprintSnapshot(f);componentId=s.componentId;selectedKey=s.selectedKey(); }
        void json(StringBuilder out) { out.append('{');field(out,"componentId",componentId);comma(out);field(out,"selectedKey",selectedKey);out.append('}'); }
        public boolean equals(Object value) { if(!(value instanceof PrefixEntry))return false;PrefixEntry other=(PrefixEntry)value;return componentId.equals(other.componentId)&&selectedKey.equals(other.selectedKey); }
    }

    private static final class Guidance {
        String hintXBits,hintYBits;
        int connectedX,connectedY,preferredBeforeGroupX,preferredBeforeGroupY,preferredX,preferredY,direction,baseX,baseY,boundaryX;
        boolean boundaryPackage;
        Rectangle group,sourceEnvelope,vacancyPocket;
        SupportSnapshot support;
        Vector<PointSnapshot> vacancies=new Vector<PointSnapshot>();
        PointSnapshot vacancyPreference;
        String json() { StringBuilder out=new StringBuilder();json(out);return out.toString(); }
        void json(StringBuilder out) { out.append('{');nullableField(out,"hintXBits",hintXBits);comma(out);nullableField(out,"hintYBits",hintYBits);comma(out);number(out,"connectedX",connectedX);comma(out);number(out,"connectedY",connectedY);comma(out);number(out,"preferredBeforeGroupX",preferredBeforeGroupX);comma(out);number(out,"preferredBeforeGroupY",preferredBeforeGroupY);comma(out);number(out,"preferredX",preferredX);comma(out);number(out,"preferredY",preferredY);comma(out);out.append("\"group\":");if(group==null)out.append("null");else rect(out,group);comma(out);number(out,"direction",direction);comma(out);number(out,"baseX",baseX);comma(out);number(out,"baseY",baseY);comma(out);bool(out,"boundaryPackage",boundaryPackage);comma(out);number(out,"boundaryX",boundaryX);comma(out);out.append("\"support\":");if(support==null)out.append("null");else support.json(out);comma(out);out.append("\"vacancies\":[");for(int i=0;i<vacancies.size();i++){if(i>0)comma(out);vacancies.get(i).json(out);}out.append(']');comma(out);out.append("\"vacancyPreference\":");if(vacancyPreference==null)out.append("null");else vacancyPreference.json(out);comma(out);out.append("\"vacancyPocket\":");if(vacancyPocket==null)out.append("null");else rect(out,vacancyPocket);comma(out);out.append("\"sourceEnvelope\":");rect(out,sourceEnvelope);out.append('}'); }
    }

    private static final class SupportSnapshot {
        final String ownerId,firstPadId,secondPadId;
        final int targetX,targetY,firstX,firstY,secondX,secondY;
        SupportSnapshot(PcbSupportPairPlacement.Pair p) { ownerId=p.ownerId;firstPadId=p.firstPadId;secondPadId=p.secondPadId;targetX=p.target.x;targetY=p.target.y;firstX=p.firstAnchor.getX();firstY=p.firstAnchor.getY();secondX=p.secondAnchor.getX();secondY=p.secondAnchor.getY(); }
        void json(StringBuilder out) { out.append('{');field(out,"ownerId",ownerId);comma(out);field(out,"firstPadId",firstPadId);comma(out);field(out,"secondPadId",secondPadId);comma(out);number(out,"targetX",targetX);comma(out);number(out,"targetY",targetY);comma(out);number(out,"firstX",firstX);comma(out);number(out,"firstY",firstY);comma(out);number(out,"secondX",secondX);comma(out);number(out,"secondY",secondY);out.append('}'); }
    }

    private static final class PointSnapshot {
        final int x,y;
        PointSnapshot(int px,int py){x=px;y=py;}
        void json(StringBuilder out){out.append('{');number(out,"x",x);comma(out);number(out,"y",y);out.append('}');}
    }

    private static final class ReferenceOrigin {
        boolean itemFound,trialOldGeometryOriginVisited;
        String oldSelectedRotation,oldSelectedGeometryKey;
        int oldSelectedX,oldSelectedY,trialSameOriginDEG0,trialSameOriginDEG90,
            trialSameOriginDEG180,trialSameOriginDEG270,trialOldGeometryOriginVisits;
        void json(StringBuilder out){out.append('{');bool(out,"itemFound",itemFound);comma(out);field(out,"oldSelectedRotation",oldSelectedRotation);comma(out);number(out,"oldSelectedX",oldSelectedX);comma(out);number(out,"oldSelectedY",oldSelectedY);comma(out);field(out,"oldSelectedGeometryKey",oldSelectedGeometryKey);comma(out);number(out,"trialSameOriginDEG0",trialSameOriginDEG0);comma(out);number(out,"trialSameOriginDEG90",trialSameOriginDEG90);comma(out);number(out,"trialSameOriginDEG180",trialSameOriginDEG180);comma(out);number(out,"trialSameOriginDEG270",trialSameOriginDEG270);comma(out);number(out,"trialOldGeometryOriginVisits",trialOldGeometryOriginVisits);comma(out);bool(out,"trialOldGeometryOriginVisited",trialOldGeometryOriginVisited);out.append('}');}
    }

    private static final class FallbackSnapshot {
        final int minX,maxX,minY,maxY,columns,rows,start;
        final long total;
        FallbackSnapshot(int a,int b,int c,int d,int e,int f,long g,int h){minX=a;maxX=b;minY=c;maxY=d;columns=e;rows=f;total=g;start=h;}
        void json(StringBuilder out){out.append('{');number(out,"minX",minX);comma(out);number(out,"maxX",maxX);comma(out);number(out,"minY",minY);comma(out);number(out,"maxY",maxY);comma(out);number(out,"columns",columns);comma(out);number(out,"rows",rows);comma(out);field(out,"total",Long.toString(total));comma(out);number(out,"start",start);out.append('}');}
    }

    private static final class FirstDivergence {
        final String stage,componentId,visitKey;
        FirstDivergence(String s,String c,String v){stage=s;componentId=c;visitKey=v;}
        void json(StringBuilder out){out.append('{');field(out,"stage",stage);comma(out);nullableField(out,"componentId",componentId);comma(out);nullableField(out,"visitKey",visitKey);out.append('}');}
    }

    private static final class SelectedDivergence {
        String componentId,oldSelectedKey,trialSelectedKey;
        Integer oldPlaceOrdinal,trialPlaceOrdinal;
        boolean sourceGeometryEqual,sizingEqual,outlineEqual,boundaryEqual,samePlaceOrdinal,prefixEqual,guidanceEqual,comparable,visitArraysComplete,exportOverflow;
        int oldVisitCount,trialVisitCount;
        Vector<String> reasonCodes=new Vector<String>();
        void json(StringBuilder out){out.append('{');field(out,"componentId",componentId);comma(out);nullableNumber(out,"oldPlaceOrdinal",oldPlaceOrdinal);comma(out);nullableNumber(out,"trialPlaceOrdinal",trialPlaceOrdinal);comma(out);nullableField(out,"oldSelectedKey",oldSelectedKey);comma(out);nullableField(out,"trialSelectedKey",trialSelectedKey);comma(out);bool(out,"sourceGeometryEqual",sourceGeometryEqual);comma(out);bool(out,"sizingEqual",sizingEqual);comma(out);bool(out,"outlineEqual",outlineEqual);comma(out);bool(out,"boundaryEqual",boundaryEqual);comma(out);bool(out,"samePlaceOrdinal",samePlaceOrdinal);comma(out);bool(out,"prefixEqual",prefixEqual);comma(out);bool(out,"guidanceEqual",guidanceEqual);comma(out);bool(out,"comparable",comparable);comma(out);strings(out,"reasonCodes",reasonCodes);comma(out);number(out,"oldVisitCount",oldVisitCount);comma(out);number(out,"trialVisitCount",trialVisitCount);comma(out);bool(out,"visitArraysComplete",visitArraysComplete);comma(out);bool(out,"exportOverflow",exportOverflow);out.append('}');}
    }

    private static boolean hasFallback(Vector<VisitEvent> events) { for(VisitEvent event:events)if("fallback".equals(event.kind))return true;return false; }
    private static Vector<PrefixEntry> prefix(Vector<PcbFootprint> footprints) { Vector<PrefixEntry> result=new Vector<PrefixEntry>();for(PcbFootprint f:footprints)result.add(new PrefixEntry(f));return result; }
    private static boolean samePrefix(Vector<PrefixEntry> a,Vector<PrefixEntry> b) { if(a==null||b==null||a.size()!=b.size())return false;for(int i=0;i<a.size();i++)if(!a.get(i).equals(b.get(i)))return false;return true; }
    private static Vector<PointSnapshot> pointSnapshots(Vector<Point> points) { Vector<PointSnapshot> result=new Vector<PointSnapshot>();if(points!=null)for(Point p:points)result.add(new PointSnapshot(p.x,p.y));return result; }
    private static Rectangle copy(Rectangle value) { return value==null?null:new Rectangle(value); }
    private static boolean rectEquals(Rectangle a,Rectangle b) { return a==null?b==null:b!=null&&a.x==b.x&&a.y==b.y&&a.width==b.width&&a.height==b.height; }
    private static String geometryKey(PcbFootprint f) { PcbComponentPlacement p=f.getPlacement();return p.getPhysicalPackage().getId()+"|"+p.getGeometryVariantKey()+"|"+p.getGeometryTransformKey()+"|"+p.getGeometryContractVersionValue()+"|"+p.getRotation().name()+"|"+p.getMountingSide().name()+"|"+p.getWidth()+"x"+p.getHeight(); }
    private static String bits(double value) { long bits=Double.doubleToLongBits(value);char[] chars=new char[16];for(int i=15;i>=0;i--){int nibble=(int)(bits&15L);chars[i]=(char)(nibble<10?'0'+nibble:'a'+nibble-10);bits>>>=4;}return new String(chars); }
    private static boolean same(String a,String b) { return a==null?b==null:a.equals(b); }
    private static void comma(StringBuilder out){out.append(',');}
    private static void field(StringBuilder out,String name,String value){quote(out,name);out.append(':');quote(out,value);}
    private static void nullableField(StringBuilder out,String name,String value){quote(out,name);out.append(':');if(value==null)out.append("null");else quote(out,value);}
    private static void number(StringBuilder out,String name,int value){quote(out,name);out.append(':').append(value);}
    private static void number(StringBuilder out,String name,long value){quote(out,name);out.append(':').append(value);}
    private static void bool(StringBuilder out,String name,boolean value){quote(out,name);out.append(':').append(value);}
    private static void nullableNumber(StringBuilder out,String name,Integer value){quote(out,name);out.append(':');if(value==null)out.append("null");else out.append(value.intValue());}
    private static void rect(StringBuilder out,Rectangle r){out.append('{');number(out,"x",r.x);comma(out);number(out,"y",r.y);comma(out);number(out,"w",r.width);comma(out);number(out,"h",r.height);out.append('}');}
    private static void strings(StringBuilder out,String name,Vector<String> values){quote(out,name);out.append(":[");for(int i=0;i<values.size();i++){if(i>0)comma(out);quote(out,values.get(i));}out.append(']');}
    private static void array(StringBuilder out,String name,Vector<VisitEvent> values){quote(out,name);out.append(":[");for(int i=0;i<values.size();i++){if(i>0)comma(out);values.get(i).json(out);}out.append(']');}
    private static void quote(StringBuilder out,String value){out.append('"');if(value!=null)for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c=='"'||c=='\\')out.append('\\').append(c);else if(c=='\b')out.append("\\b");else if(c=='\f')out.append("\\f");else if(c=='\n')out.append("\\n");else if(c=='\r')out.append("\\r");else if(c=='\t')out.append("\\t");else if(c<32){out.append("\\u00");int n=c;out.append("0123456789abcdef".charAt((n>>4)&15)).append("0123456789abcdef".charAt(n&15));}else out.append(c);}out.append('"');}
}
