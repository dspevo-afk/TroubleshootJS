package com.lushprojects.circuitjs1.client;

import java.util.HashSet;
import java.util.Vector;

/** Independent selection/session and real scheduler boundary falsifiers. */
public final class QuickPlayGateContractTest {
    private static int checks;
    private static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        replayEpoch();
        for(long seed:new long[]{0,1,-1,Long.MIN_VALUE,Long.MAX_VALUE,9007199254740993L,-4518705223253195925L}) selection(seed);
        for(int i=0;i<1024;i++) selection(QuickPlayGateCorpus.seed(false,i));
        for(long seed:new long[]{0,1,-1,Long.MIN_VALUE,Long.MAX_VALUE,9007199254740993L})
            mediumSelection(seed);
        for(long seed:new long[]{Long.MIN_VALUE,-9007199254740993L,0,9007199254740993L,Long.MAX_VALUE})
            normalMediumSelection(seed);
        for(String family:PlayerFamilyCatalog.families())for(DifficultyProfile profile:DifficultyProfile.values())
            check(QuickPlayAdmission.supports(family,profile)==
                (profile==PlayerFamilyCatalog.candidateProfile(family)),
                "Procedural admission advertised unavailable family/profile content");
        check(!QuickPlayAdmission.supports("UNKNOWN",DifficultyProfile.EASY) &&
            !QuickPlayAdmission.supports(Rb15Plan.FAMILY_ID,null),"Unknown family/profile admitted");
        boolean epoch2=false;try{PlayerLaunchRequest.parse("tsj-alpha/2/EASY/RELAY_OUTPUT/4");}
        catch(IllegalArgumentException expected){epoch2=true;}
        check(epoch2,"Previous physical interpretation silently reinterpreted");
        check(QuickPlayAdmission.supports(ControlledIndicatorBlockContributions.FAMILY_ID, DifficultyProfile.MEDIUM),
            "Procedural Medium family omitted from broad-seed admission");
        check(QuickPlayAdmission.supports(Rb30Plan.FAMILY_ID, DifficultyProfile.MEDIUM),
            "Normal Q30 MEDIUM family omitted from broad-seed admission");
        stagedCatalogSelection();
        boolean old=false; try {PlayerLaunchRequest.parse("tsj-alpha/1/EASY/RB15_CONTROL/0");} catch(IllegalArgumentException expected){old=true;}
        check(old,"Old interpretation silently accepted");
        scheduler(); session(); recentPhysicalBoards(); newBoardIdentity(); copperNormalization();
        System.out.println("PASS: Quick Play gate contracts assertions="+checks);
    }
    private static void replayEpoch() {
        boolean stale = false;
        try { PlayerLaunchRequest.parse("tsj-alpha/3/MEDIUM/RB30_CONTROL/13"); }
        catch (IllegalArgumentException expected) {
            stale = "Unsupported replay identity or epoch".equals(expected.getMessage());
        }
        check(stale, "A v3 Q30 replay silently adopted the v4 interpretation");
        for (long seed : new long[] {Long.MIN_VALUE, Long.MIN_VALUE + 1, -9007199254740993L,
                -1, 0, 1, 9007199254740993L, Long.MAX_VALUE - 1, Long.MAX_VALUE}) {
            String replay = "tsj-alpha/4/MEDIUM/RB30_CONTROL/" + Long.toString(seed);
            PlayerLaunchRequest decoded = PlayerLaunchRequest.parse(replay);
            check(decoded.seed == seed && decoded.profile == DifficultyProfile.MEDIUM &&
                    Rb30Plan.FAMILY_ID.equals(decoded.familyId) && replay.equals(decoded.replay()),
                "Current v4 replay rounded or reinterpreted signed-long seed " + seed);
        }
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
    private static void normalMediumSelection(long seed) {
        PlayerLaunchRequest launch = PlayerLaunchRequest.random(Rb30Plan.FAMILY_ID,
            Long.toString(seed), "MEDIUM");
        GenerationRequest request = launch.generation();
        check(launch.seed == seed && launch.candidateSearch && request.candidateCount() == 4 &&
            request.getDescriptor().getRootSeed() == seed &&
            request.getExecutionPolicy() == GenerationExecutionPolicy.NORMAL_MEDIUM &&
            MediumBoardNormalAdmission.IDENTITY.equals(
                request.getRequiredPhysicalAdmissionIdentity()),
            "Normal Q30 selection remapped entropy or lost its physical/execution capability");
        HashSet<Long> seen = new HashSet<Long>();
        for (int i = 0; i < request.candidateCount(); i++) {
            GenerationRequest candidate = request.candidate(i);
            long expected = QuickPlayAdmission.candidateSeed(seed, i);
            check(candidate.getDescriptor().getRootSeed() == expected &&
                candidate.getExecutionPolicy() == GenerationExecutionPolicy.NORMAL_MEDIUM &&
                seen.add(Long.valueOf(expected)),
                "Normal Q30 candidates changed order or repeated exact signed-long seeds");
            PlayerLaunchRequest accepted = launch.accepted(expected);
            PlayerLaunchRequest exact = PlayerLaunchRequest.parse(accepted.replay());
            check(!exact.candidateSearch && exact.profile == DifficultyProfile.MEDIUM &&
                exact.seed == expected && exact.generation().candidateCount() == 1 &&
                exact.generation().getExecutionPolicy() == GenerationExecutionPolicy.NORMAL_MEDIUM &&
                MediumBoardNormalAdmission.IDENTITY.equals(
                    exact.generation().getRequiredPhysicalAdmissionIdentity()),
                "Normal Q30 replay did not preserve an exact single medium candidate");
        }
    }
    private static void stagedCatalogSelection() {
        Vector<String> catalog = PlayerFamilyCatalog.families();
        Vector<String> expected = QuickPlayFamilyRegistry.getNormalPlayerFamilyIds();
        expected.add(ControlledIndicatorBlockContributions.FAMILY_ID);
        check(catalog.size() == 10 && catalog.equals(expected),
            "Current menu retains the qualified families while Q30 is blocked");
        check(PlayerFamilyCatalog.registeredFamilies().contains(Rb30Plan.FAMILY_ID),
            "Blocked Q30 retains its registered construction capability");
        int leafCount = QuickPlayFamilyRegistry.getNormalPlayerFamilyIds().size();
        for (int i = 0; i < catalog.size(); i++) {
            String family = catalog.get(i);
            long seed = (i & 1) == 0 ? Long.MIN_VALUE : 9007199254740993L;
            QuickPlaySelection selected = new QuickPlaySelector(new QuickPlayFixedRandomSource(
                new long[] { i, seed })).select();
            GenerationRequest staged = GenerationRequest.stagedQuickPlay(selected);
            check(family.equals(selected.getFamilyId()) && selected.getSeed() == seed &&
                staged.isQuickPlay() && staged.getDescriptor().getRootSeed() == seed &&
                staged.getDifficulty() == PlayerFamilyCatalog.candidateProfile(family) &&
                staged.candidateCount() == QuickPlayAdmission.MAX_CANDIDATES,
                "Full menu selection did not reach its staged request factory: " + family);
            if (i < leafCount) {
                check(QuickPlayFamilyRegistry.isNormalPlayerEligible(family),
                    "Existing synchronous leaf route left its registry");
            } else {
                boolean rejected = false;
                try { new QuickPlaySelector(new QuickPlayFixedRandomSource(
                    new long[] { i, seed })).generate(selected); }
                catch (IllegalArgumentException expectedFailure) { rejected = true; }
                check(rejected && !QuickPlayFamilyRegistry.isNormalPlayerEligible(family),
                    "Staged catalog family crossed the synchronous generator boundary");
            }
            check(staged.getExecutionPolicy() == GenerationExecutionPolicy.SMALL_BOARD,
                "Small or composed family received a different execution contract");
        }
        GenerationRequest registered = GenerationRequest.stagedQuickPlay(
            new QuickPlaySelection(Rb30Plan.FAMILY_ID, Long.MIN_VALUE));
        check(registered.getExecutionPolicy() == GenerationExecutionPolicy.NORMAL_MEDIUM &&
            MediumBoardNormalAdmission.IDENTITY.equals(registered.getRequiredPhysicalAdmissionIdentity()),
            "Registered Q30 retains medium request semantics without player eligibility");
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
    private static void recentPhysicalBoards() {
        QuickPlayBoardHistory history = new QuickPlayBoardHistory();
        history.published("layout-a");
        boolean duplicate = false;
        try { history.requireNovel("layout-a"); }
        catch (GenerationJob.Rejected expected) { duplicate = true; }
        check(duplicate && history.size() == 1,
            "A second seed could publish the same physical board");
        for (int i = 0; i < QuickPlayBoardHistory.CAPACITY; i++)
            history.published("layout-" + i);
        check(history.size() == QuickPlayBoardHistory.CAPACITY,
            "Successful-board history is not bounded");
        history.requireNovel("layout-a");
        duplicate = false;
        try { history.requireNovel("layout-0"); }
        catch (GenerationJob.Rejected expected) { duplicate = true; }
        check(duplicate, "Current recent-history entry was lost");
    }
    private static void newBoardIdentity() {
        QuickPlayIdentityAllocator allocator = new QuickPlayIdentityAllocator();
        PlayerLaunchRequest first = PlayerLaunchRequest.random(Rb15Plan.FAMILY_ID, "7", "EASY");
        PlayerLaunchRequest second = allocator.allocate(first);
        PlayerLaunchRequest third = allocator.allocate(first);
        check(second.seed == 7 && third.seed != 7 && second.seed != third.seed,
            "Repeated entropy reused a New Board identity");
        check(third.generation().candidateCount() == 4 &&
            PlayerLaunchRequest.parse(third.accepted(third.seed).replay()).seed == third.seed,
            "Distinct New Board root lost exact replay");
        PlayerLaunchRequest exact = PlayerLaunchRequest.parse(second.accepted(second.seed).replay());
        check(allocator.allocate(exact) == exact &&
            allocator.allocate(first).seed == QuickPlayAdmission.candidateSeed(third.seed, 1),
            "Exact replay changed the new-board entropy cursor");
        QuickPlayIdentityAllocator stuck = new QuickPlayIdentityAllocator();
        HashSet<Long> roots = new HashSet<Long>();
        for (int i = 0; i <= QuickPlayBoardHistory.CAPACITY; i++)
            check(roots.add(Long.valueOf(stuck.allocate(first).seed)),
                "Repeated entropy recycled a recent New Board root");
    }
    private static void copperNormalization() {
        PcbBoardLayout whole = copperFixture();
        whole.addTrace(new PcbTraceGeometry("N", new int[] {10, 110}, new int[] {20, 20}));
        whole.addTrace(new PcbTraceGeometry("N", new int[] {50, 50}, new int[] {20, 80}));
        PcbBoardLayout split = copperFixture();
        split.addTrace(new PcbTraceGeometry("N", new int[] {10, 50}, new int[] {20, 20}));
        split.addTrace(new PcbTraceGeometry("N", new int[] {50, 110}, new int[] {20, 20}));
        split.addTrace(new PcbTraceGeometry("N", new int[] {50, 50}, new int[] {20, 80}));
        check(PhysicalBoardFingerprint.copperUnion(whole, 0, 0).equals(
                PhysicalBoardFingerprint.copperUnion(split, 0, 0)),
            "Route-tree segmentation made identical physical copper look new");
        PcbBoardLayout changed = copperFixture();
        changed.addTrace(new PcbTraceGeometry("N", new int[] {10, 110}, new int[] {20, 20}));
        changed.addTrace(new PcbTraceGeometry("N", new int[] {50, 50}, new int[] {20, 90}));
        check(!PhysicalBoardFingerprint.copperUnion(whole, 0, 0).equals(
                PhysicalBoardFingerprint.copperUnion(changed, 0, 0)),
            "Distinct drawn copper collapsed to one novelty identity");
    }
    private static PcbBoardLayout copperFixture() {
        return new PcbBoardLayout(400, 300, new Rectangle(0, 0, 200, 200),
            new Rectangle(230, 0, 100, 200));
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
