package com.lushprojects.circuitjs1.client;

import com.google.gwt.user.client.Timer;

/** Independent solved-current regressions for both fault owners on the same physical board. */
final class FaultBlindManipulationVerifier {
    interface Completion { void finished(String report, Throwable failure); }
    static void start(final CirSim sim, final boolean forced, final Completion completion) {
        if (!sim.troubleshootDebug || !sim.developerVerifierRunning)
            throw new IllegalStateException("Service evidence is developer-only");
        new Timer() {
            final Task41SimulationSnapshot saved = Task41SimulationSnapshot.capture(sim);
            final long started = System.currentTimeMillis();
            int index, checks;
            GeneratedBoardInstance owner;
            String firstMenu, operation = "construction";
            StringBuilder rows = new StringBuilder();
            public void run() {
                try {
                    if (index == 2) { finish(null); return; }
                    String target = index == 0 ? "R1" : "R2";
                    operation = "install/" + target;
                    owner = new ParallelDualIndicatorGenerator().generateForFaultVerification(0,
                        GeneratedFaultType.RESISTOR_OPEN, target);
                    FreshGeneratedRuntimeInstallation.install(sim, owner, false);
                    settle();
                    require(WireCurrentAdjacencyChecks.rejectsStaleEndpoint(sim),
                        "wire-current oracle rejects a stale analyzed terminal");
                    require(!sim.getGeneratedChallengeController().isDeveloperVerificationScopeActive(),
                        "ordinary READY checks remain active for wrong player repairs");
                    verify(target);
                    index++; schedule(0);
                } catch (Throwable failure) { finish(new IllegalStateException(operation + ": " + failure, failure)); }
            }
            void verify(String target) {
                String healthy = "R1".equals(target) ? "R2" : "R1";
                PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
                PhysicalSlotMutationProvider provider = runtime.getMutationProvider(healthy);
                PhysicalPart<?> original = runtime.getInstalledPart(healthy);
                PhysicalPartProvenance provenance = original.getProvenance();
                PhysicalPartElectricalBacking backing = original.getElectricalBacking();
                power(BoardPowerState.POWERED);
                double baseline = current(healthy), total = sourceCurrent();
                require(baseline > .002 && baseline < .020, "healthy branch initially conducts");
                require(current(target) < 1e-6, "selected open branch is independently isolated");
                WorkbenchOperation remove = WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, original);
                require(provider.supports(remove) && !provider.isAvailable(remove, sim.pcbWorkbenchController), "power guard retained");
                power(BoardPowerState.UNPOWERED);
                String menu = menu();
                if (index == 0) firstMenu = menu;
                else require(firstMenu.equals(menu), "same physical menu for either fault owner");
                for (String id : new String[] {"R1", "R2"}) {
                    PhysicalSlotMutationProvider p = runtime.getMutationProvider(id);
                    PhysicalPart<?> part = runtime.getInstalledPart(id);
                    require(p.isAvailable(WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, part), sim.pcbWorkbenchController), "both resistors removable");
                    for (String pad : owner.getBoard().getComponent(id).getPadIds())
                        require(p.isAvailable(WorkbenchOperation.forPartLead(WorkbenchOperation.LIFT_LEAD, part, id, pad), sim.pcbWorkbenchController), "both resistor leads liftable");
                }
                operation = "remove-healthy/" + healthy;
                require(provider.invoke(remove, sim.pcbWorkbenchController), "ordinary supported removal"); settle();
                require(runtime.getInstalledPart(healthy) == null && runtime.getPart(original.getId()) == original &&
                    original.getProvenance() == provenance && original.getElectricalBacking() == backing &&
                    runtime.getWorkbenchPartsProviderForPart(original.getId()).getLooseParts().contains(original), "tray retains real original");
                power(BoardPowerState.POWERED);
                double removed = current(healthy), removedTotal = sourceCurrent();
                require(removed < 1e-6 && removedTotal < 1e-6 && Math.abs(total - removedTotal - baseline) < .00005,
                    "healthy removal changes actual solved branch and source currents");
                if (forced) throw new IllegalStateException("service-forced-failure-after-healthy-removal");
                operation = "reinstall-healthy/" + healthy;
                power(BoardPowerState.UNPOWERED);
                require(provider.install(original.getId()), "reinstall the exact healthy identity"); settle();
                power(BoardPowerState.POWERED);
                double restored = current(healthy);
                require(Math.abs(restored - baseline) < .00001 && Math.abs(sourceCurrent() - total) < .00001,
                    "reinstall restores original electrical contribution");
                operation = "wrong-compatible-value/" + healthy;
                power(BoardPowerState.UNPOWERED);
                PhysicalPart<?> wrong = ((CatalogAcquisitionProvider)provider).acquireFromCatalog("R_CATALOG_100000"); settle();
                require(provider.removeInstalledPart(), "healthy part can be replaced"); settle();
                require(provider.install(wrong.getId()), "wrong compatible value installs"); settle();
                power(BoardPowerState.POWERED);
                double wrongCurrent = current(healthy);
                require(runtime.getInstalledPart(healthy) == wrong && wrongCurrent > 1e-6 && wrongCurrent < baseline / 10 &&
                    Math.abs(((ResistorElm)owner.getComponentBindings().getSingleElement(healthy)).getResistance() - 100000) < .01,
                    "wrong value remains installed and sets actual solved current");
                require(!sim.getGeneratedChallengeController().performCustomerRetest().isPassed(), "wrong repair remains nonfunctional");
                power(BoardPowerState.UNPOWERED);
                require(provider.removeInstalledPart(), "remove wrong value"); settle();
                PhysicalSlotMutationProvider other = runtime.getMutationProvider(target);
                PhysicalPart<?> otherOriginal = runtime.getInstalledPart(target);
                boolean cross = original.getGeometryRealization().isEquivalentTo(otherOriginal.getGeometryRealization());
                if (cross) {
                    require(other.removeInstalledPart(), "prepare compatible other position"); settle();
                    require(other.install(wrong.getId()) && runtime.getInstalledPart(target) == wrong, "same purchased part installs in another eligible position"); settle();
                    power(BoardPowerState.POWERED);
                    require(current(target) > 1e-6 && current(target) < .001, "wrong-position part has its real electrical effect");
                    power(BoardPowerState.UNPOWERED); require(other.removeInstalledPart(), "remove misplaced part"); settle();
                    require(other.install(otherOriginal.getId()), "restore other original"); settle();
                }
                require(provider.install(original.getId()), "restore measured healthy original"); settle();
                GeneratedRuntimeInvariant.verify(sim, owner, sim.getBoardModificationController(), sim.elmList);
                if (rows.length() > 0) rows.append(',');
                rows.append("{\"faultOwner\":\"").append(target).append("\",\"healthy\":\"").append(healthy)
                    .append("\",\"baselineAmps\":").append(baseline).append(",\"removedAmps\":").append(removed)
                    .append(",\"restoredAmps\":").append(restored).append(",\"wrongValueAmps\":").append(wrongCurrent)
                    .append(",\"crossPosition\":").append(cross).append('}');
            }
            String menu() {
                StringBuilder result = new StringBuilder();
                for (String id : owner.getBoard().getComponentIds()) {
                    PhysicalPart<?> part = owner.getPhysicalBoardRuntime().getInstalledPart(id);
                    PhysicalSlotMutationProvider provider = owner.getPhysicalBoardRuntime().getMutationProvider(id);
                    append(result, provider, WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, part));
                    append(result, provider, WorkbenchOperation.forPart(WorkbenchOperation.RESTORE, part));
                    for (String pad : owner.getBoard().getComponent(id).getPadIds()) {
                        append(result, provider, WorkbenchOperation.forPartLead(WorkbenchOperation.LIFT_LEAD, part, id, pad));
                        append(result, provider, WorkbenchOperation.forPartLead(WorkbenchOperation.RECONNECT_LEAD, part, id, pad));
                    }
                }
                return result.toString();
            }
            void append(StringBuilder out, PhysicalSlotMutationProvider p, WorkbenchOperation op) {
                out.append(op.getComponentId()).append('/').append(op.getPadId()).append('/').append(op.getId())
                    .append('/').append(p.supports(op)).append('/').append(p.isAvailable(op, sim.pcbWorkbenchController))
                    .append('/').append(p.getOperationLabel(op)).append(';');
            }
            double current(String id) { return Math.abs(owner.getComponentBindings().getSingleElement(id).getCurrent()); }
            double sourceCurrent() { return Math.abs(ParallelDualIndicatorGeneratedBoardValidator.source(owner).getCurrent()); }
            void settle() { GeneratedRuntimeDeveloperSettlement.settle(sim, owner, operation); WireCurrentAdjacencyChecks.verify(sim); }
            void power(BoardPowerState state) { settle(); sim.setBoardPowerState(state); settle(); require(sim.getBoardPowerController().getState() == state, "requested electrical state"); }
            void require(boolean ok, String message) { checks++; if (!ok) throw new IllegalStateException(message); }
            void finish(Throwable failure) {
                cancel(); long cleanupAt = System.currentTimeMillis(); boolean restored = false;
                try { saved.restore(sim); saved.assertRestored(sim); restored = true; }
                catch (Throwable cleanup) { failure = new IllegalStateException("Service cleanup failed after " + failure, cleanup); }
                String report = "{\"schema\":1,\"status\":" + quote(failure == null ? "PASS" : "FAIL") + ",\"checks\":" + checks +
                    ",\"cases\":[" + rows + "],\"operation\":" + quote(operation) + ",\"operationMs\":" + (cleanupAt - started) +
                    ",\"cleanupMs\":" + (System.currentTimeMillis() - cleanupAt) + ",\"ownerRestored\":" + restored +
                    ",\"failure\":" + (failure == null ? "null" : quote(failure.toString())) + "}";
                completion.finished(report, failure);
            }
        }.schedule(0);
    }
    private static native String quote(String text) /*-{ return JSON.stringify(text); }-*/;
    static native void publish(String report) /*-{ $doc.documentElement.setAttribute('data-tsj-service-report', report); }-*/;
}
