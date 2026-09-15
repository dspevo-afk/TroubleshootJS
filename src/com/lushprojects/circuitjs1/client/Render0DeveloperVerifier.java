package com.lushprojects.circuitjs1.client;

import java.util.Vector;
import com.google.gwt.user.client.Timer;

/** Real second-renderer proof on an admitted LED board, using existing meter and repair owners. */
final class Render0DeveloperVerifier {
    interface Completion { void finished(String report,Throwable failure); }
    static void start(final CirSim sim,final boolean forced,final Completion completion) {
        if(!sim.troubleshootDebug || !sim.developerVerifierRunning)throw new IllegalStateException("RENDER-0 is developer-only");
        new Timer() {
            final Task41SimulationSnapshot outer=Task41SimulationSnapshot.capture(sim);
            final long started=System.currentTimeMillis();
            int phase,checks,negativeChecks,frameBaseline;
            String operation="admission";
            GeneratedBoardInstance owner;
            PcbWorkbenchController controller;
            WorkbenchRenderHost host;
            double canvasOhms,svgOhms,repairedOhms;
            Object boardIdentity;
            boolean identities,copper,solver,repair,detached,immutable;
            public void run() {
                try {
                    if(phase==0) {
                        sim.generationCoordinator.startForDeveloperVerification(new PlayerLaunchRequest("LED_INDICATOR","3","EASY").generation());
                        phase=1; schedule(0); return;
                    }
                    if(phase==1 && sim.generationCoordinator.isRunning()) {
                        sim.generationCoordinator.advanceForDeveloperVerification(); schedule(0); return;
                    }
                    if(phase==1) {
                        require(sim.generationCoordinator.getJob().getOutcome()==GenerationJob.Outcome.PASS,"actual admission");
                        owner=sim.getGeneratedBoardInstance(); controller=sim.pcbWorkbenchController;
                        host=controller.getRenderHostForDeveloperVerification();
                        require(owner.getSeed()==3 && "LED_INDICATOR".equals(owner.getCircuitFamilyId()),"exact canary board");
                        sim.getGeneratedChallengeController().beginDeveloperVerificationScope();
                        settle(); require(!sim.getGeneratedChallengeController().performCustomerRetest().isPassed(),"unrepaired behavior fails");
                        power(BoardPowerState.UNPOWERED);
                        phase=2; schedule(80); return;
                    }
                    if(phase==2) {
                        operation="camera-frame";
                        settle();
                        frameBaseline=controller.viewFramesPresentedForDeveloperVerification();
                        // The ordinary pending repaint has completed on the event loop.
                        for(int i=0;i<64;i++)controller.requestViewFrame();
                        phase=3; schedule(80); return;
                    }
                    require(controller.viewFramesPresentedForDeveloperVerification()==frameBaseline+1 &&
                        !controller.hasPendingViewFrameForDeveloperVerification(), "64 view requests coalesce into one actual frame");
                    verify();
                    finish(null);
                } catch(Throwable failure) { finish(new IllegalStateException(operation+": "+failure,failure)); }
            }
            void verify() {
                operation="canvas/immutable-scene";
                host.draw(new Graphics(sim.backcontext),sim.circuitArea);
                WorkbenchPhysicalScene first=host.scene(); boardIdentity=first.boardIdentity;
                require(first.componentIds.size()==owner.getBoard().getComponentIds().size() && first.pads.size()==owner.getBoard().getPadIds().size(),"complete stable component/pad IDs");
                boolean rejected=false;
                try { first.componentIds.add("forged"); } catch(UnsupportedOperationException expected) { rejected=true; }
                require(rejected,"component identity list immutable"); negativeChecks++;
                rejected=false;
                try { first.pads.clear(); } catch(UnsupportedOperationException expected) { rejected=true; }
                require(rejected,"pad list immutable"); negativeChecks++;
                Rectangle copy=first.board.rectangle(); copy.x+=999;
                require(first.board.x==owner.getPcbLayout().getBoardOutline().x,"appearance bounds defensive"); immutable=true;
                for(WorkbenchPhysicalScene.Part p:first.parts)require(p.geometry!=null && p.nameplate!=null,"declared package realization and public markings");
                ProbeTarget a=probe("J1.1"),b=probe("R1.1");
                canvasOhms=measure(a,b); require(Math.abs(canvasOhms)<.001,"independent connected-copper expectation");
                WorkbenchRenderHit oldHit=hit("J1.1");
                WorkbenchRenderHit selected=componentHit("R1");
                require("R1".equals(host.selectedComponent(selected)),"canvas selects actual R1");
                PhysicalPart<?> failed=owner.getPhysicalBoardRuntime().getInstalledPart("R1");
                WorkbenchOperation remove=WorkbenchOperation.forPart(WorkbenchOperation.REMOVE,failed);
                WorkbenchCapabilityStrategy action=WorkbenchCapabilityDiscovery.find(failed,remove,owner.getPhysicalBoardRuntime().getWorkbenchCapabilityRegistry());
                require(action!=null && action.isAvailable(remove,controller),"existing remove action available");
                Vector<BoardComponent> components=new Vector<BoardComponent>();
                Vector<BoardPad> pads=new Vector<BoardPad>();
                for(String id:owner.getBoard().getComponentIds())components.add(owner.getBoard().getComponent(id));
                for(String id:owner.getBoard().getPadIds())pads.add(owner.getBoard().getPad(id));
                GeneratedChallengeController challenge=sim.getGeneratedChallengeController();
                PhysicalBoardRuntime runtime=owner.getPhysicalBoardRuntime();
                PcbConductorGraph.Snapshot copperBefore=owner.getCurrentConductorSnapshot();
                Vector<CircuitElm> elements=new Vector<CircuitElm>(sim.elmList);
                String state=solverState();
                operation="attach-svg/same-owner";
                controller.replaceRendererForDeveloperVerification(new SvgWorkbenchCanaryBackend(sim.cv.getElement().getParentElement()));
                host.draw(new Graphics(sim.backcontext),sim.circuitArea);
                require(sim.getGeneratedBoardInstance()==owner && sim.getGeneratedChallengeController()==challenge && owner.getPhysicalBoardRuntime()==runtime,"swap retains same generated challenge/runtime");
                for(BoardComponent c:components)require(owner.getBoard().getComponent(c.getId())==c,"same BoardComponent instance");
                for(BoardPad p:pads)require(owner.getBoard().getPad(p.getId())==p,"same BoardPad instance");
                identities=true;
                require(host.scene().boardIdentity==boardIdentity && host.scene().copper==first.copper && owner.getCurrentConductorSnapshot()==copperBefore,"same exact copper realization"); copper=true;
                require(elements.equals(sim.elmList) && state.equals(solverState()),"same solver instances and solved values on renderer swap"); solver=true;
                require(host.resolve(oldHit)==null,"detached renderer hit rejected"); negativeChecks++;
                require(a.isValid() && b.isValid(),"retained stable probe targets survive backend swap");
                ProbeTarget svgA=probe("J1.1"),svgB=probe("R1.1");
                require(a.isSameTarget(svgA) && b.isSameTarget(svgB),"renderer hits resolve same stable targets");
                require(a.getMeasurementEndpoint()==svgA.getMeasurementEndpoint() && b.getMeasurementEndpoint()==svgB.getMeasurementEndpoint(),"same actual solver probe endpoints");
                svgOhms=measure(svgA,svgB); require(Math.abs(svgOhms-canvasOhms)<.001,"same real meter result");
                require(Math.abs(measure(svgB,svgA))<.001,"reversed SVG copper probes");
                WorkbenchRenderHit svgHit=hit("J1.1");
                require(host.resolve(new WorkbenchRenderHit(boardIdentity,svgHit.attachment,WorkbenchRenderHit.Kind.PAD,"UNKNOWN",null,-1))==null,"unknown physical ID rejected"); negativeChecks++;
                require(host.resolve(new WorkbenchRenderHit(new Object(),svgHit.attachment,WorkbenchRenderHit.Kind.PAD,"J1.1",null,-1))==null,"foreign board hit rejected"); negativeChecks++;
                require("R1".equals(host.selectedComponent(componentHit("R1"))),"SVG selects same repair component");
                require(action==WorkbenchCapabilityDiscovery.find(failed,remove,runtime.getWorkbenchCapabilityRegistry()),"same repair operation and capability provider");
                if(forced)throw new IllegalStateException("render0-forced-failure-after-attach");
                operation="svg/actual-repair";
                PhysicalSlotMutationProvider provider=runtime.getMutationProvider("R1");
                String correct=owner.getDiagnosticProvider().getCorrectCatalogId(owner,"R1");
                PhysicalPart<?> replacement=((CatalogAcquisitionProvider)provider).acquireFromCatalog(correct); settle();
                require(action.invoke(remove,controller),"existing remove action invoked while SVG attached"); settle();
                require(runtime.getInstalledPart("R1")==null && !failed.isInstalled(),"real removal into authoritative tray");
                host.draw(new Graphics(sim.backcontext),sim.circuitArea);
                require(host.scene().installed("R1")==null,"SVG receives updated empty physical slot");
                WorkbenchOperation install=WorkbenchOperation.forPartAtSlot(WorkbenchOperation.INSTALL,replacement,"R1");
                WorkbenchCapabilityStrategy installAction=WorkbenchCapabilityDiscovery.find(replacement,install,runtime.getWorkbenchCapabilityRegistry());
                require(installAction!=null && installAction.invoke(install,controller),"existing tray installation action"); settle();
                host.draw(new Graphics(sim.backcontext),sim.circuitArea);
                require(host.scene().installed("R1").id.equals(replacement.getId()),"same repaired physical part visible through SVG");
                power(BoardPowerState.POWERED);
                require(challenge.performCustomerRetest().isPassed(),"actual customer behavior restored"); repair=true;
                power(BoardPowerState.UNPOWERED);
                state=solverState(); elements=new Vector<CircuitElm>(sim.elmList);
                operation="detach-svg/restore-production";
                controller.replaceRendererForDeveloperVerification(null); host.draw(new Graphics(sim.backcontext),sim.circuitArea);
                require(host.isProduction() && sim.getGeneratedBoardInstance()==owner && sim.getGeneratedChallengeController()==challenge,"2D reattached without challenge rebuild");
                require(elements.equals(sim.elmList) && state.equals(solverState()),"repaired solver untouched by reattachment");
                require(host.scene().copper==first.copper && host.scene().boardIdentity==boardIdentity,"copper and physical board retained after repair/swap");
                require(host.resolve(svgHit)==null,"retired SVG hit rejected"); negativeChecks++;
                repairedOhms=measure(probe("J1.1"),probe("R1.1")); require(Math.abs(repairedOhms)<.001,"same copper probe result after repair and renderer restore");
                require(!svgAttached(),"SVG engine element detached"); detached=true;
                challenge.endDeveloperVerificationScope();
                operation="camera-frame";
            }
            String solverState() {
                StringBuilder out=new StringBuilder(sim.dumpCircuit()).append('/').append(sim.t);
                for(CircuitElm e:sim.elmList) {
                    out.append('|').append(e.getCurrent());
                    if(e.volts!=null)for(double v:e.volts)out.append('/').append(v);
                }
                return out.toString();
            }
            WorkbenchRenderHit hit(String pad) { Point p=host.getPadPoint(pad); return host.hit(p.x,p.y,true); }
            ProbeTarget probe(String pad) { ProbeTarget target=host.resolve(hit(pad)); require(target!=null && target.isValid(),"physical probe "+pad); return target; }
            WorkbenchRenderHit componentHit(String id) {
                WorkbenchPhysicalScene.Part p=host.scene().installed(id);
                Point at=host.projectForDeveloperVerification(p.body.x+p.body.width/2,p.body.y+p.body.height/2,true);
                return host.hit(at.x,at.y,false);
            }
            double measure(ProbeTarget red,ProbeTarget black) {
                sim.instrumentController.setResistanceProbesForDeveloperVerification(red,black);
                double value=sim.instrumentController.getLatestResistanceReadingForDeveloperVerification();
                require(!sim.activeMeasurementOverlay && sim.getBoardPowerController().isElectricallyUnpowered(),"meter cleanup and power isolation");
                require(!Double.isNaN(value) && !Double.isInfinite(value),"finite solver-backed reading"); return value;
            }
            void settle() { GeneratedRuntimeDeveloperSettlement.settle(sim,owner,"RENDER-0"); }
            void power(BoardPowerState state) { settle(); sim.setBoardPowerState(state); settle(); require(sim.getBoardPowerController().getState()==state,"actual requested power state"); }
            void require(boolean value,String label) { checks++; if(!value)throw new IllegalStateException(label); }
            void finish(Throwable failure) {
                cancel(); long cleanupStarted=System.currentTimeMillis(); boolean restored=false;
                String frame=controller==null?"null":controller.viewFrameEvidenceForDeveloperVerification();
                try {
                    if(controller!=null && sim.pcbWorkbenchController==controller)controller.replaceRendererForDeveloperVerification(null);
                    sim.generationCoordinator.cancel(); outer.restore(sim); outer.assertRestored(sim); restored=true;
                    if(svgAttached())throw new IllegalStateException("SVG overlay leaked after cleanup");
                } catch(Throwable cleanup) { failure=new IllegalStateException("RENDER-0 cleanup failed after "+failure+": "+cleanup); }
                long cleanupMs=System.currentTimeMillis()-cleanupStarted;
                String report="{\"schema\":1,\"status\":\""+(failure==null?"PASS":"FAIL")+"\",\"family\":\"LED_INDICATOR\",\"seed\":\"3\",\"checks\":"+checks+
                    ",\"negativeChecks\":"+negativeChecks+",\"sameIdentities\":"+identities+",\"sameCopper\":"+copper+",\"sameSolver\":"+solver+
                    ",\"sameRepair\":"+repair+",\"detached\":"+detached+",\"immutable\":"+immutable+",\"canvasOhms\":"+canvasOhms+
                    ",\"svgOhms\":"+svgOhms+",\"repairedOhms\":"+repairedOhms+",\"operationMs\":"+(cleanupStarted-started)+
                    ",\"cleanupMs\":"+cleanupMs+",\"ownerRestored\":"+restored+",\"camera\":"+frame+
                    ",\"failure\":"+(failure==null?"null":quote(failure.toString()))+"}";
                completion.finished(report,failure);
            }
        }.schedule(0);
    }
    private static native String quote(String text) /*-{ return JSON.stringify(text); }-*/;
    private static native boolean svgAttached() /*-{ return !!$doc.querySelector('[data-tsj-renderer-canary]'); }-*/;
    static native void publish(String report) /*-{ $doc.documentElement.setAttribute('data-tsj-render0-report',report); }-*/;
}
