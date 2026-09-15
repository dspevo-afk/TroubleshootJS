package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Vector;

/** The legacy literal oracle must describe current board copper, not removed harness/component posts. */
public final class Task43PhysicalEndpointContractTest {
    public static void main(String[] args) throws Exception {
        CirSim sim=new CirSim(); sim.gridSize=16; sim.gridMask=~15; sim.gridRound=7; CircuitElm.sim=sim;
        Method factory=Task43PPhysicalTruthDeveloperVerifier.class.getDeclaredMethod("manifestFor",String.class);
        factory.setAccessible(true); int checks=0;
        for(String family:new String[]{"LED_INDICATOR","DIODE_PROTECTED_INDICATOR","PARALLEL_DUAL_INDICATOR",
                "RC_DELAY","NPN_LOW_SIDE_SWITCH","NMOS_LOW_SIDE_SWITCH"}) {
            Object manifest=factory.invoke(null,family);
            Field field=manifest.getClass().getDeclaredField("terminals"); field.setAccessible(true);
            for(long seed:new long[]{0,2,3}) {
                GeneratedBoardInstance instance=QuickPlayFamilyRegistry.generate(family,seed);
                Vector<?> terminals=(Vector<?>)field.get(manifest);
                if(terminals.size()!=instance.getBoard().getPadIds().size()) throw new AssertionError("Missing expected terminal");
                for(Object terminal:terminals) {
                    String id=(String)value(terminal,"padId");
                    CircuitPostMeasurementEndpoint endpoint=(CircuitPostMeasurementEndpoint)instance.getSimulationBindings().getEndpoint(id);
                    if(!value(terminal,"solverClass").equals(endpoint.getElement().getClass().getSimpleName()) ||
                            ((Integer)value(terminal,"solverPost")).intValue()!=endpoint.getPostIndex())
                        throw new AssertionError("Literal copper oracle differs: "+family+"/"+seed+"/"+id);
                    checks++;
                }
            }
        }
        Method composed=Task43PPhysicalTruthDeveloperVerifier.class.getDeclaredMethod("controlledIndicatorManifest",BoundedAssemblyPlan.class);
        composed.setAccessible(true); StringBuilder failures=new StringBuilder();
        for(long seed:new long[]{-1,0,1,Long.MIN_VALUE}) {
            String family="COMPOSED_CONTROLLED_INDICATOR"; GeneratedBoardInstance instance=new PlayerLaunchRequest(family,Long.toString(seed),PlayerFamilyCatalog.candidateProfile(family).name()).generation().resolve(new GenerationRequest.PlanCache()).construct().instance;
            BoundedAssemblyPlan plan=((ControlledIndicatorDeviceBehavior)instance.getBehaviorContract()).getPlan();
            Object manifest=composed.invoke(null,plan);
            Vector<?> terminals=(Vector<?>)value(manifest,"terminals");
            if(terminals.size()!=instance.getBoard().getPadIds().size()) throw new AssertionError("Missing controlled expected terminal");
            for(Object terminal:terminals) {
                String id=(String)value(terminal,"padId");
                CircuitPostMeasurementEndpoint endpoint=(CircuitPostMeasurementEndpoint)instance.getSimulationBindings().getEndpoint(id);
                String actual=endpoint.getElement().getClass().getSimpleName()+"/"+endpoint.getPostIndex();
                String expected=value(terminal,"solverClass")+"/"+value(terminal,"solverPost");
                if(!actual.equals(expected)) failures.append("\nseed=").append(seed).append(" ").append(id).append(" expected=").append(expected).append(" actual=").append(actual);
                checks++;
            }
        }
        if(failures.length()!=0) throw new AssertionError("Literal controlled copper oracle differs:"+failures);
        System.out.println("PASS: Task43 current physical endpoint contracts assertions="+checks+" legacyFamilies=6 controlledSignedSeeds=4");
    }
    private static Object value(Object target,String name) throws Exception {
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }
}
