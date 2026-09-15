package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Real CircuitJS and production mutation/probe qualification of the isolated P06 prototype. */
final class P06FactoryLinkDeveloperVerifier {
    private static int checks;
    private static void require(boolean value,String reason) {
        checks++; if (!value) throw new IllegalStateException("P06 " + reason);
    }

    static void verify(CirSim sim, boolean forced, boolean keepBench) {
        if (!sim.troubleshootDebug || !sim.developerVerifierRunning)
            throw new IllegalStateException("P06 fixture requires explicit developer verification");
        checks = 0;
        Task41SimulationSnapshot saved = Task41SimulationSnapshot.capture(sim);
        boolean retained = false, restored = false;
        double initialCurrent = 0, removedCurrent = 0, restoredCurrent = 0, underCurrent = 0;
        String graphIdentity = null;
        try {
            P06FactoryLinkPrototype prototype = new P06FactoryLinkPrototype();
            GeneratedBoardInstance owner = prototype.instance;
            saved.beginProof(sim);
            // The ordinary installation path owns its containers; do not let it delete the saved owner.
            sim.elmList = new Vector<CircuitElm>(); sim.adjustables = new Vector<Adjustable>();
            sim.undoStack = new Vector<String>(); sim.redoStack = new Vector<String>();
            sim.installGeneratedBoardForDeveloperVerification(owner);
            sim.setSimRunning(true); settle(sim);
            require(sim.pcbWorkbenchController != null, "production workbench attached");
            PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
            PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
            PhysicalServicePart original = (PhysicalServicePart)runtime.getInstalledPart("FL1");
            PhysicalPartElectricalBacking originalBacking = original.getElectricalBacking();
            PhysicalPartProvenance originalProvenance = original.getProvenance();
            ServiceSlotController provider = (ServiceSlotController)runtime.getMutationProvider("FL1");
            String geometry = owner.getPcbLayout().geometryFingerprint();
            graphIdentity = owner.getPristineConductorGraph().toCanonical();
            power(sim,BoardPowerState.POWERED);
            prototype.verifyReadings(owner,BoardPowerState.POWERED,true);
            initialCurrent = Math.abs(prototype.load.getCurrent());
            underCurrent = Math.abs(prototype.underLoad.getCurrent());
            require(PcbConductorProjection.audit(owner,sim.elmList).pads == 7, "all copper pads correspond to real solver islands");
            require(!provider.isAvailable(WorkbenchOperation.forPart(WorkbenchOperation.REMOVE,original),
                sim.pcbWorkbenchController), "powered removal guard retained");
            renderer.setViewingFace(PcbBoardSide.TOP);
            PhysicalPartRenderGeometry geometryOnScreen = renderer.getInstalledGeometryForDeveloperVerification("FL1");
            Rectangle body = geometryOnScreen.getBodyBounds();
            int bodyX = body.x + body.width/2, bodyY = body.y + body.height/2;
            require(renderer.findProbeTarget(sim,bodyX,bodyY) == null,
                "insulated body and the copper under its projection are not a phantom terminal");
            require("FL1".equals(renderer.findComponentId(bodyX,bodyY)), "actual body is selectable");
            ProbeTarget boardTarget = null;
            for (PcbBoardSide face : new PcbBoardSide[] {PcbBoardSide.TOP,PcbBoardSide.BOTTOM}) {
                renderer.setViewingFace(face);
                for (String pad : new String[] {"FL1.1","FL1.2"}) {
                    Rectangle bounds = renderer.getPadProbeBoundsForDeveloperVerification(pad);
                    ProbeTarget target = renderer.findProbeTarget(sim,bounds.x+bounds.width/2,bounds.y+bounds.height/2);
                    require(target != null && target.isValid() &&
                        target.getMeasurementEndpoint() == owner.getSimulationBindings().getEndpoint(pad),
                        "both PTH faces resolve the exact persistent board terminal");
                    if (face == PcbBoardSide.TOP && "FL1.1".equals(pad)) boardTarget=target;
                }
            }
            renderer.setViewingFace(PcbBoardSide.TOP);
            power(sim,BoardPowerState.UNPOWERED);
            require(provider.removeInstalledPart(), "ordinary scoped link removal succeeds"); settle(sim);
            require(runtime.getInstalledPart("FL1") == null && runtime.getPart(original.getId()) == original &&
                original.getElectricalBacking() == originalBacking && original.getProvenance() == originalProvenance &&
                runtime.getWorkbenchPartsProviderForPart(original.getId()).getLooseParts().contains(original),
                "removal retains exact loose identity, provenance and finite backing");
            require(boardTarget.isValid() && boardTarget.getMeasurementEndpoint() == owner.getSimulationBindings().getEndpoint("FL1.1"),
                "board copper remains probeable after the link leaves");
            require(renderer.findComponentId(bodyX,bodyY) == null, "removed body is not selectable at its old projection");
            for (int terminal=0;terminal<2;terminal++) {
                ProbeTarget loose = original.getRenderMetadata().getLooseProbeProvider()
                    .createLooseProbeTarget(sim,owner,original,terminal,renderer);
                require(loose != null && loose.isValid() &&
                    loose.getMeasurementEndpoint() == original.getTerminal(terminal).getEndpoint(),
                    "loose metal probes retain actual detached component endpoints");
            }
            power(sim,BoardPowerState.POWERED);
            prototype.verifyReadings(owner,BoardPowerState.POWERED,false);
            removedCurrent = Math.abs(prototype.load.getCurrent());
            require(PcbConductorProjection.audit(owner,sim.elmList).pads == 7, "link removal does not cut or short underpass copper");
            power(sim,BoardPowerState.UNPOWERED);
            require(provider.install(original.getId()), "same physical link reinstalls"); settle(sim);
            power(sim,BoardPowerState.POWERED);
            prototype.verifyReadings(owner,BoardPowerState.POWERED,true);
            restoredCurrent = Math.abs(prototype.load.getCurrent());
            power(sim,BoardPowerState.UNPOWERED);
            require(provider.invoke(WorkbenchOperation.forPartLead(WorkbenchOperation.LIFT_LEAD,original,"FL1","FL1.1"),
                sim.pcbWorkbenchController), "link lead uses the existing guarded lift operation"); settle(sim);
            power(sim,BoardPowerState.POWERED); prototype.verifyReadings(owner,BoardPowerState.POWERED,false);
            power(sim,BoardPowerState.UNPOWERED);
            require(provider.invoke(WorkbenchOperation.forPartLead(WorkbenchOperation.RECONNECT_LEAD,original,"FL1","FL1.1"),
                sim.pcbWorkbenchController), "lifted link lead reconnects"); settle(sim);
            power(sim,BoardPowerState.POWERED); prototype.verifyReadings(owner,BoardPowerState.POWERED,true);
            power(sim,BoardPowerState.UNPOWERED);
            ReplaceableServiceBoardCapability capability = (ReplaceableServiceBoardCapability)runtime.getScopedMutationCapability("FL1");
            require(capability.catalogLabel().contains("factory link") && !capability.catalogLabel().contains("fuse"),
                "service catalog uses truthful link identity");
            PhysicalPart<?> replacement = provider.acquireFromCatalog(capability.catalogId()); settle(sim);
            require(replacement != original && replacement.getPackage() == PhysicalPackages.RAISED_FACTORY_LINK &&
                replacement.getElectricalBacking() != originalBacking, "catalog creates independent finite backing");
            require(provider.removeInstalledPart(), "remove before catalog swap"); settle(sim);
            require(provider.install(replacement.getId()), "catalog link installation"); settle(sim);
            power(sim,BoardPowerState.POWERED); prototype.verifyReadings(owner,BoardPowerState.POWERED,true);
            power(sim,BoardPowerState.UNPOWERED);
            require(provider.removeInstalledPart(), "remove catalog link"); settle(sim);
            require(provider.install(original.getId()), "restore exact original for the visual bench"); settle(sim);
            require(geometry.equals(owner.getPcbLayout().geometryFingerprint()) &&
                graphIdentity.equals(owner.getPristineConductorGraph().toCanonical()),
                "all service operations leave sealed copper and placement identity unchanged");
            require(PcbConductorProjection.audit(owner,sim.elmList).pads == 7, "final correspondence after all real mutations");
            boolean rejected = false;
            try { GeneratedDiagnosticSolvabilityAdmission.validate(owner); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "prototype evidence cannot masquerade as normal diagnostic admission");
            require(!forced, "forced-negative");
            if (keepBench) {
                sim.pcbWorkbenchController.attachToSidebar(sim.verticalPanel);
                sim.refreshGeneratedUiForDeveloperVerification(); sim.updateCircuit();
                publishBenchGeometry(renderer); retained = true;
            }
        } finally {
            if (!retained) { saved.restore(sim); saved.assertRestored(sim); restored = true; }
            publishCleanup(restored, retained);
        }
        publish("{\"schema\":1,\"status\":\"PASS\",\"runtimeAssertions\":" + checks +
            ",\"initialLoadAmps\":" + initialCurrent + ",\"removedLoadAmps\":" + removedCurrent +
            ",\"restoredLoadAmps\":" + restoredCurrent + ",\"underpassAmps\":" + underCurrent +
            ",\"ownerRestored\":" + restored + ",\"prototypeRetained\":" + retained +
            ",\"normalAdoption\":false,\"copperIdentityPreserved\":true}");
    }

    private static void power(CirSim sim,BoardPowerState state) {
        sim.setBoardPowerState(state); settle(sim);
        require(sim.getBoardPowerController().getState() == state, "requested power state reached");
        if (state == BoardPowerState.UNPOWERED)
            require(sim.getBoardPowerController().isElectricallyUnpowered(), "real electrical isolation reached");
    }
    private static void settle(CirSim sim) {
        for (int i=0;i<20;i++) {
            sim.updateCircuit();
            if (sim.stopMessage != null) throw new IllegalStateException("P06 solver stopped: " + sim.stopMessage);
            if (sim.isGeneratedRuntimeSettled()) return;
        }
        throw new IllegalStateException("P06 exceeded bounded solver settlement");
    }
    private static void publishBenchGeometry(PcbWorkbenchRenderer renderer) {
        Rectangle body = renderer.getInstalledGeometryForDeveloperVerification("FL1").getBodyBounds();
        publishPoint(body.x+body.width/2,body.y+body.height/2);
    }
    private static native void publishCleanup(boolean restored,boolean retained) /*-{
        $doc.documentElement.setAttribute("data-tsj-p06-cleanup", JSON.stringify({ownerRestored:restored,prototypeRetained:retained}));
    }-*/;
    private static native void publishPoint(int x,int y) /*-{
        $doc.documentElement.setAttribute("data-tsj-p06-body", JSON.stringify({x:x,y:y}));
    }-*/;
    private static native void publish(String report) /*-{
        $doc.documentElement.setAttribute("data-tsj-p06-report", report);
    }-*/;
    private P06FactoryLinkDeveloperVerifier() { }
}
