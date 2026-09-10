package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Map;
import com.lushprojects.circuitjs1.client.PowerDomainContract.*;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Scalar;
import com.lushprojects.circuitjs1.client.PowerOperatingAssessment.*;

public final class A06PowerContractTest {
    private static int assertions;
    public static void main(String[] args) {
        String vectors = A06PowerContractVectors.run();
        assertions = A06PowerContractVectors.assertions;
        final PowerDomainContract c = A06PowerContractVectors.fixture(StorageRequirement.NONE,true);
        reject(new Runnable(){public void run(){c.requireSourceCoverage(Arrays.asList("SA"));}},"missing source");
        reject(new Runnable(){public void run(){c.requireSourceCoverage(Arrays.asList("SA","SB","SB"));}},"duplicate source");
        reject(new Runnable(){public void run(){new Reference("bad\nkey","I",null,false);}},"control-character identity");
        reject(new Runnable(){public void run(){new Reference("G","I",null,true);}},"earth without identity");
        reject(new Runnable(){public void run(){new PowerDomainContract("d",c.getReferences().values(),
            c.getRails().values(),Arrays.asList(A06PowerContractVectors.source("SA","A"),A06PowerContractVectors.source("SA","B")),c.getBackfeedPaths().values());}},"duplicate declaration");
        reject(new Runnable(){public void run(){new PowerDomainContract("d",c.getReferences().values(),
            c.getRails().values(),Collections.singletonList(A06PowerContractVectors.source("S","UNKNOWN")),c.getBackfeedPaths().values());}},"dangling rail");
        reject(new Runnable(){public void run(){new PowerDomainContract("d",Arrays.asList(
            new Reference("G1","I1",null,false),new Reference("G2","I2",null,false)),
            Arrays.asList(new Rail("A","G1",StorageRequirement.NONE),new Rail("B","G2",StorageRequirement.NONE)),
            c.getSources().values(),c.getBackfeedPaths().values());}},"cross-isolation backfeed");
        reject(new Runnable(){public void run(){new BackfeedPath("p","A","B",Scalar.known(-1),true);}},"negative bound");
        try {c.getRails().clear();throw new AssertionError("mutable power map");}catch(UnsupportedOperationException expected){assertions++;}
        Map<String,SourceState> extra = A06PowerContractVectors.states(SourceState.isolated(),SourceState.isolated());extra.put("EXTRA",SourceState.connected());
        final Map<String,SourceState> foreign = extra;
        reject(new Runnable(){public void run(){PowerOperatingAssessment.assess(c,foreign,A06PowerContractVectors.volts(0,0),true);}},"foreign state");
        for (long seed : new long[] {-1,1,Long.MIN_VALUE,Long.MAX_VALUE}) {
            BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(BoundedAssemblyRequest.forControlledIndicator(seed));
            PowerDomainContract domain = plan.getPowerDomainContract();
            check(domain.getSources().size()==3,"three current controls");
            check(domain.getReferences().size()==1,"one actual joined return");
            for (Reference r : domain.getReferences().values()) check(r.getEarthId()==null && !r.isEarthBondPermitted(),"no earth inferred");
            for (Source s : domain.getSources().values()) check(s.getImplementedCurrentLimitAmps().getState()==ElectricalPortContract.State.NOT_APPLICABLE,"capacity is not limiter");
            RealizationManifest manifest = A03RealizationReplay.capture(plan);
            check(A03RealizationReplay.resolve(manifest).getPowerDomainContract().toCanonical().equals(domain.toCanonical()),"domain reconstruction");
            ArrayList<RealizationManifest.Choice> stripped = new ArrayList<RealizationManifest.Choice>();
            for (RealizationManifest.Choice choice : manifest.getChoices()) if (!choice.getKey().startsWith("power.")) stripped.add(choice);
            final RealizationManifest incomplete = new RealizationManifest(RealizationManifest.VERSION,manifest.getDescriptor(),
                manifest.getBlocks(),manifest.getVersionPins(),stripped,manifest.getNetBindings(),manifest.getTargets());
            reject(new Runnable(){public void run(){A03RealizationReplay.resolve(incomplete);}},"retired power-less interpretation");
            PhysicalConstructionMetadata metadata = PhysicalConstructionMaterializer.describe(plan);
            GeneratedExternalPowerBindings bindings = new GeneratedExternalPowerBindings(metadata.getBoard());
            for (SourceState state : bindings.getSourceStates().values()) check(state.getConnection()==Connection.UNKNOWN,"unbound source is unknown");
            check(!bindings.areAllDisconnected(),"unbound is not disconnected");
        }
        System.out.println("A06_VECTORS_BEGIN");System.out.println(vectors);System.out.println("A06_VECTORS_END");
        System.out.println("PASS: A06 power contracts assertions="+assertions);
    }
    private static void reject(Runnable r,String label){try{r.run();}catch(IllegalArgumentException e){assertions++;return;}throw new AssertionError(label);}
    private static void check(boolean ok,String label){assertions++;if(!ok)throw new AssertionError(label);}
}
