package com.lushprojects.circuitjs1.client;

import java.util.HashSet;

/** Independent selection/session and real scheduler boundary falsifiers. */
public final class QuickPlayGateContractTest {
    private static int checks;
    private static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        for(long seed:new long[]{0,1,-1,Long.MIN_VALUE,Long.MAX_VALUE,9007199254740993L,-4518705223253195925L}) selection(seed);
        for(int i=0;i<1024;i++) selection(QuickPlayGateCorpus.seed(false,i));
        for(long seed:new long[]{0,1,-1,Long.MIN_VALUE,Long.MAX_VALUE,9007199254740993L})
            mediumSelection(seed);
        for(String family:PlayerFamilyCatalog.families())for(DifficultyProfile profile:DifficultyProfile.values())
            check(QuickPlayAdmission.supports(family,profile)==profile.isAvailable(),
                "A normal family or available difficulty retained a fixed-layout fallback");
        check(!QuickPlayAdmission.supports("UNKNOWN",DifficultyProfile.EASY) &&
            !QuickPlayAdmission.supports(Rb15Plan.FAMILY_ID,null),"Unknown family/profile admitted");
        boolean epoch2=false;try{PlayerLaunchRequest.parse("tsj-alpha/2/EASY/RELAY_OUTPUT/4");}
        catch(IllegalArgumentException expected){epoch2=true;}
        check(epoch2,"Previous physical interpretation silently reinterpreted");
        check(QuickPlayAdmission.supports(ControlledIndicatorBlockContributions.FAMILY_ID, DifficultyProfile.MEDIUM),
            "Procedural Medium family omitted from broad-seed admission");
        boolean old=false; try {PlayerLaunchRequest.parse("tsj-alpha/1/EASY/RB15_CONTROL/0");} catch(IllegalArgumentException expected){old=true;}
        check(old,"Old interpretation silently accepted");
        scheduler(); session();
        System.out.println("PASS: Quick Play gate contracts assertions="+checks);
    }
    private static void selection(long seed) {
        PlayerLaunchRequest launch=PlayerLaunchRequest.random(Rb15Plan.FAMILY_ID,Long.toString(seed),"EASY");
        GenerationRequest request=launch.generation(); HashSet<Long> seen=new HashSet<Long>();
        check(launch.seed==seed && launch.candidateSearch && request.candidateCount()==4,"Entropy was remapped");
        check(QuickPlayFamilyRegistry.selectNormalPlayerSeed(Rb15Plan.FAMILY_ID,seed)==seed,"Hidden whitelist");
        String previous="";
        for(int i=0;i<4;i++) {
            GenerationRequest exact=request.candidate(i); long value=exact.getDescriptor().getRootSeed();
            check(seen.add(value) && exact.candidateCount()==1,"Duplicate or recursively retrying candidate");
            check(i!=0 || value==seed,"First candidate is not exact launch entropy");
            String manifest=request.candidateManifest(i);
            check(manifest.compareTo(previous)>0,"Canonical sort changed ordinal order"); previous=manifest;
            PlayerLaunchRequest accepted=launch.accepted(value), replay=PlayerLaunchRequest.parse(accepted.replay());
            check(replay.seed==value && !replay.candidateSearch && replay.generation().candidateCount()==1,"Accepted replay retries or rounds");
            check(exact.canonical().contains("physicalEnvelope="+SupportedEnvelope.current().identity()),"Physical policy omitted");
        }
        boolean rejected=false;try{launch.replay();}catch(IllegalStateException expected){rejected=true;}
        check(rejected,"Unaccepted search advertised as replay");
        rejected=false;try{launch.accepted(seed+1);}catch(IllegalStateException expected){rejected=true;}
        check(rejected,"Foreign accepted seed");
        rejected=false;try{request.candidate(4);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Unbounded retry index");
        rejected=false;try{request.resolve(new GenerationRequest.PlanCache());}catch(IllegalStateException expected){rejected=true;}
        check(rejected,"Search bypassed coordinator");
    }
    private static void mediumSelection(long seed) {
        PlayerLaunchRequest launch = PlayerLaunchRequest.random(
            ControlledIndicatorBlockContributions.FAMILY_ID, Long.toString(seed), "MEDIUM");
        GenerationRequest request = launch.generation();
        check(launch.seed == seed && launch.candidateSearch && request.candidateCount() == 4,
            "Medium entropy was collapsed to a curated seed");
        HashSet<Long> seen = new HashSet<Long>();
        for (int i = 0; i < request.candidateCount(); i++) {
            GenerationRequest exact = request.candidate(i);
            long value = exact.getDescriptor().getRootSeed();
            check(exact.isComposition() && seen.add(value),
                "Medium candidate lost composition or duplicated a seed");
            check(i != 0 || value == seed, "Medium first candidate changed launch entropy");
            PlayerLaunchRequest accepted = launch.accepted(value);
            GenerationRequest replay = PlayerLaunchRequest.parse(accepted.replay()).generation();
            check(!accepted.candidateSearch && replay.isComposition() && replay.candidateCount() == 1 &&
                    replay.getDescriptor().getRootSeed() == value,
                "Medium accepted replay changed or retried the exact board");
        }
    }

    private static void session() {
        PlayerSession session=new PlayerSession();
        PlayerLaunchRequest launch=PlayerLaunchRequest.random(Rb15Plan.FAMILY_ID,"7","EASY");
        Object owner=new Object(); int token=session.begin(launch);
        check(!session.prepared(token,launch,owner),"Search entropy became an accepted replay");
        PlayerLaunchRequest accepted=launch.accepted(QuickPlayAdmission.candidateSeed(7,2));
        check(session.prepared(token,launch,accepted,owner),"Accepted candidate not installed");
        check(session.request()==accepted && session.owner()==owner,"Session retained launch rather than accepted seed");
        check(!session.prepared(token,launch,accepted,new Object()),"Stale completion changed session");
    }
    private static final class Service implements GenerationJob.Services {
        int current=-1,begins,aborts,published,proof;
        boolean allReject,programming,badCleanup,live=true;
        long clock;
        public int candidateCount(){return 4;}
        public String manifest(int i){return "candidate=0"+i;}
        public void beginCandidate(int i){current=i;begins++;proof=0;}
        public String resolve(){return "resolved";}
        public String healthy(){
            if(programming)throw new IllegalStateException("Internal provider bug");
            if(allReject || current==0)throw new GenerationJob.Rejected("Expected routing exhaustion");
            return "healthy";
        }
        public String physical(){if(current==1)throw new GenerationJob.Rejected("Outside envelope");return "physical";}
        public boolean proveNext(){
            proof++;
            if(current==2 && proof==2)throw new GenerationJob.Rejected("Incompatible diagnostic population");
            return proof<2;
        }
        public String symptom(){return "symptom";}
        public String dependencies(){return "same-owned-proof";}
        public void publish(GenerationReceipt receipt){published++;}
        public void abort(){aborts++;if(badCleanup)throw new IllegalStateException("Cleanup failed");}
        public boolean isCurrent(){return live;}
        public long nowMillis(){return clock;}
    }
    private static void finish(GenerationJob job){for(int i=0;i<100 && job.isRunning();i++)job.advance();check(!job.isRunning(),"Scheduler did not stop");}
    private static void scheduler(){
        Service s=new Service(); GenerationJob job=new GenerationJob(s,90000,640,5000); finish(job);
        check(job.getOutcome()==GenerationJob.Outcome.PASS && s.begins==4 && s.aborts==3 && s.published==1,"Ordered retries failed");
        check(job.getCandidateIndex()==3 && job.getAttempts().size()==4,"Candidate ledger incomplete");
        check(job.getStageWorkCount(GenerationJob.Stage.HYPOTHESES)==4 && job.getCandidateStageWorkCount(GenerationJob.Stage.HYPOTHESES)==2,"Retry lost aggregate or candidate proof count");
        check(job.getAttempts().get(2).stage==GenerationJob.Stage.HYPOTHESES && job.getAttempts().get(2).outcome==GenerationJob.Outcome.EXPECTED_REJECTION,"Wrong rejection stage");
        check(job.getReceipt().getManifest().equals("candidate=03"),"Receipt belongs to a rejected candidate");
        boolean immutable=false;try{job.getAttempts().clear();}catch(UnsupportedOperationException expected){immutable=true;}
        check(immutable && job.getAttempts().size()==4,"Mutable attempt ledger");
        s=new Service();s.allReject=true;job=new GenerationJob(s,90000,640,5000);finish(job);
        check(job.getOutcome()==GenerationJob.Outcome.EXPECTED_REJECTION && s.begins==4 && s.aborts==4 && s.published==0,"Exhausted search admitted a failure");
        s=new Service();s.badCleanup=true;job=new GenerationJob(s,90000,640,5000);finish(job);
        check(job.getOutcome()==GenerationJob.Outcome.INFRASTRUCTURE_FAILURE && s.begins==1 && s.aborts==1,"Failed cleanup permitted retry");
        s=new Service();s.programming=true;job=new GenerationJob(s,90000,640,5000);finish(job);
        check(job.getOutcome()==GenerationJob.Outcome.PROGRAMMING_FAILURE && s.begins==1,"Programming bug treated as candidate rejection");
        s=new Service();job=new GenerationJob(s,90000,3,5000);finish(job);
        check(job.getOutcome()==GenerationJob.Outcome.WORK_EXHAUSTED && job.getStepCount()==3,"Retry reset global work budget");
        s=new Service();job=new GenerationJob(s,90,640,50);job.advance();s.clock=90;finish(job);
        check(job.getOutcome()==GenerationJob.Outcome.TIMEOUT && s.begins==1,"Clock changed accepted candidate order");
        s=new Service();job=new GenerationJob(s,90000,640,5000);job.advance();job.cancel();finish(job);
        check(job.getOutcome()==GenerationJob.Outcome.CANCELLED && s.begins==1 && s.aborts==1,"Cancellation retried");
        s=new Service();job=new GenerationJob(s,90000,640,5000);job.advance();s.live=false;finish(job);
        check(job.getOutcome()==GenerationJob.Outcome.STALE && s.begins==1 && s.aborts==1,"Stale launch retried");
    }
}
