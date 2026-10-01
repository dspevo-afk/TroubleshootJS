package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.util.Vector;

/** Narrow guards for the one-request disabled-family qualification scope. */
public final class IsolatedGenerationQualificationContractTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        validNormalSearchScope();
        scopeMisuseAndLoss();
        attachGuards();
        unauthorizedStartPreservesPredecessor();
        publicationAndSuccessorGuards();
        activeOperationTelemetry();
        System.out.println("PASS: isolated generation qualification " + assertions + " assertions");
    }

    private static GenerationRequest search(long seed) {
        return PlayerLaunchRequest.random(Rb30Plan.FAMILY_ID,
            Long.toString(seed), "MEDIUM").generation();
    }

    private static CirSim freshSim() {
        CirSim sim = new CirSim();
        sim.elmList = new Vector<CircuitElm>();
        sim.generationCoordinator = new GenerationCoordinator(sim);
        return sim;
    }

    private static void validNormalSearchScope() {
        CirSim sim = freshSim();
        GenerationCoordinator coordinator = sim.generationCoordinator;
        GenerationRequest request = search(13L);
        require(!PlayerFamilyCatalog.isNormalPlayerEnabled(request.getFamilyId()),
            "isolated qualification leaves the family disabled in the global catalog");
        IsolatedGenerationQualification authority = IsolatedGenerationQualification.attach(
            sim, coordinator, request);
        try {
            authority.requireStart(coordinator, sim, request);
            require(authority.getStartChecks() == 1 && !authority.isClosed() &&
                authority.getBoundJob() == null,
                "an exact ordinary four-candidate medium request passes the scoped start guard");
            require(coordinator.getJob() == null && coordinator.isAttachedIsolatedQualification(authority),
                "attachment is bound to the simulator's existing idle coordinator");
        } finally {
            authority.close();
        }
        require(authority.isClosed() && !coordinator.isAttachedIsolatedQualification(authority) &&
            !PlayerFamilyCatalog.isNormalPlayerEnabled(request.getFamilyId()),
            "closing scope detaches the token without changing catalog availability");
    }

    private static void scopeMisuseAndLoss() {
        CirSim sim = freshSim();
        GenerationCoordinator coordinator = sim.generationCoordinator;
        GenerationRequest request = search(64L);
        IsolatedGenerationQualification authority = IsolatedGenerationQualification.attach(
            sim, coordinator, request);
        boolean rejected = false;
        try { coordinator.start(request.candidate(0), null, false); }
        catch (IllegalStateException expected) { rejected = true; }
        require(rejected && authority.getStartChecks() == 1 && coordinator.getJob() == null,
            "an exact-candidate request cannot reuse a search-bound authority");

        authority.close();
        require(authority.isClosed() && authority.getBoundJob() == null &&
            !coordinator.isAttachedIsolatedQualification(authority),
            "scope loss revokes its coordinator and job references");
        rejected = false;
        try { coordinator.start(request, null, false); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected && coordinator.getJob() == null &&
            !PlayerFamilyCatalog.isNormalPlayerEnabled(request.getFamilyId()),
            "closed scope cannot admit a later normal start");
    }

    private static void attachGuards() throws Exception {
        GenerationRequest request = search(35L);

        CirSim debugSim = freshSim();
        debugSim.troubleshootDebug = true;
        rejectAttach(debugSim, debugSim.generationCoordinator, request,
            "debug mode cannot receive an isolated normal-player authority");

        CirSim developerSim = freshSim();
        developerSim.developerVerifierRunning = true;
        rejectAttach(developerSim, developerSim.generationCoordinator, request,
            "developer-verifier mode cannot receive an isolated authority");

        CirSim privateSim = freshSim();
        rejectAttach(privateSim, privateSim.generationCoordinator,
            GenerationRequest.forFamilyQualification(Rb30Plan.FAMILY_ID, 35L),
            "private measurement requests cannot use the normal-player scope");

        CirSim replaySim = freshSim();
        rejectAttach(replaySim, replaySim.generationCoordinator,
            new PlayerLaunchRequest(Rb30Plan.FAMILY_ID, "35", "MEDIUM").generation(),
            "an exact replay cannot use a candidate-search scope");

        CirSim wrongCoordinatorSim = freshSim();
        rejectAttach(wrongCoordinatorSim, new GenerationCoordinator(wrongCoordinatorSim), request,
            "a replacement coordinator cannot borrow the simulator's scope");

        CirSim dirtyCacheSim = freshSim();
        GenerationCoordinator dirtyCoordinator = dirtyCacheSim.generationCoordinator;
        Field plansField = GenerationCoordinator.class.getDeclaredField("plans");
        plansField.setAccessible(true);
        GenerationRequest.Prepared prepared = request.candidate(0).resolve(
            (GenerationRequest.PlanCache) plansField.get(dirtyCoordinator));
        require(prepared != null, "cache canary prepared one ordinary candidate");
        rejectAttach(dirtyCacheSim, dirtyCoordinator, request,
            "a coordinator with an existing plan-cache artifact is not fresh");
    }

    private static void unauthorizedStartPreservesPredecessor() throws Exception {
        CirSim sim = freshSim();
        GenerationCoordinator coordinator = sim.generationCoordinator;
        DeadlineServices service = new DeadlineServices();
        GenerationJob sentinel = new GenerationJob(service, 90000L, 640, 5000L);
        Field jobField = GenerationCoordinator.class.getDeclaredField("job");
        jobField.setAccessible(true);
        jobField.set(coordinator, sentinel);
        boolean rejected = false;
        try { coordinator.start(search(37L), null, false); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected && coordinator.getJob() == sentinel && sentinel.isRunning() &&
            service.aborts == 0 && coordinator.getDiagnosticProofCacheSize() == 0 &&
            coordinator.getDiagnosticMeasurementProofCacheSize() == 0 && sim.elmList.isEmpty(),
            "ordinary start without authority rejects before touching a predecessor, caches, or graph");
        jobField.set(coordinator, null);
    }

    private static void rejectAttach(CirSim sim, GenerationCoordinator coordinator,
            GenerationRequest request, String message) {
        boolean rejected = false;
        try { IsolatedGenerationQualification.attach(sim, coordinator, request); }
        catch (IllegalArgumentException expected) { rejected = true; }
        catch (IllegalStateException expected) { rejected = true; }
        require(rejected && !PlayerFamilyCatalog.isNormalPlayerEnabled(Rb30Plan.FAMILY_ID), message);
    }

    private static void publicationAndSuccessorGuards() throws Exception {
        CirSim sim=freshSim();
        GenerationCoordinator coordinator=sim.generationCoordinator;
        GenerationRequest request=search(7);
        IsolatedGenerationQualification scope=IsolatedGenerationQualification.attach(sim,coordinator,request);
        boolean rejected=false;
        try { coordinator.start(request,new GenerationCoordinator.Completion() {
            public void complete(GenerationJob job,GeneratedBoardInstance owner) { throw new AssertionError("unexpected callback"); }
        },false); } catch(IllegalStateException expected) { rejected=true; }
        require(rejected && coordinator.getJob()==null,"scope rejects synchronous timing before mutation");
        GenerationJob job=new GenerationJob(new DeadlineServices(),90000,640,5000);
        Field jobField=GenerationCoordinator.class.getDeclaredField("job"); jobField.setAccessible(true);
        jobField.set(coordinator,job);
        scope.bindJob(coordinator,sim,request,job);
        sim.developerVerifierRunning=true;
        require(scope.isCurrentForJob(coordinator,sim,request,job),
            "standard staged installation's internal flag does not revoke the normal job");
        Field index=GenerationJob.class.getDeclaredField("candidateIndex"); index.setAccessible(true); index.setInt(job,0);
        scope.requirePublication(coordinator,sim,request.candidate(0),job);
        require(scope.getPublicationChecks()==1,"exact live scoped candidate reaches catalog-only publication check");
        rejected=false;
        try { scope.requirePublication(coordinator,sim,request.candidate(1),job); }
        catch(GenerationJob.Stale expected) { rejected=true; }
        require(rejected,"foreign candidate cannot borrow publication authority");
        GenerationJob successor=new GenerationJob(new DeadlineServices(),90000,640,5000);
        jobField.set(coordinator,successor);
        require(!scope.isCurrentForJob(coordinator,sim,request,job),"successor revokes prior job ownership");
        rejected=false;
        try { scope.requirePublication(coordinator,sim,request.candidate(0),job); }
        catch(GenerationJob.Stale expected) { rejected=true; }
        require(rejected && coordinator.getJob()==successor && successor.isRunning(),"stale publication preserves successor");
        scope.close();
        require(scope.getBoundJob()==null,"closed scope releases job ownership");
    }

    private static void activeOperationTelemetry() {
        TimedServices delayed=new TimedServices(5001,false);
        GenerationJob deadline=new GenerationJob(delayed,90000,640,5000);
        deadline.advance(); deadline.advance();
        require(deadline.getOutcome()==GenerationJob.Outcome.TIMEOUT && delayed.aborts==1,
            "active operation guard is enforced with exact deterministic clock");
        require(deadline.getMaxActiveOperationMillis()==5001,"maximum active operation captures bounded failure");
        TimedServices rejected=new TimedServices(19,true);
        GenerationJob rejection=new GenerationJob(rejected,90000,640,5000);
        rejection.advance(); rejection.advance();
        require(rejection.getOutcome()==GenerationJob.Outcome.EXPECTED_REJECTION && rejection.getAttempts().size()==1,
            "deterministic rejection retains one completed attempt");
        require("frozen-rejection".equals(rejection.getAttempts().get(0).failureMessage),
            "attempt retains the original rejection before retry clears live failure");
        require(rejection.getMaxActiveOperationMillis()==19,"rejected operation telemetry excludes idle yield");
    }

    private static final class TimedServices extends DeadlineServices {
        final long duration; final boolean reject; long now;
        TimedServices(long duration,boolean reject) {this.duration=duration;this.reject=reject;}
        public String resolve() {return "resolved";}
        public String healthy() {now+=duration;if(reject)throw new GenerationJob.Rejected("frozen-rejection");return null;}
        public long nowMillis() {return now;}
    }

    private static class DeadlineServices implements GenerationJob.Services {
        int aborts;
        public int candidateCount() { return 1; }
        public String manifest(int index) { return "candidate=0"; }
        public void beginCandidate(int index) { }
        public String resolve() { throw new AssertionError("unused"); }
        public String healthy() { throw new AssertionError("unused"); }
        public String physical() { throw new AssertionError("unused"); }
        public boolean proveNext() { throw new AssertionError("unused"); }
        public String symptom() { throw new AssertionError("unused"); }
        public String dependencies() { throw new AssertionError("unused"); }
        public void publish(GenerationReceipt receipt) { throw new AssertionError("unused"); }
        public void abort() { aborts++; }
        public boolean isCurrent() { return true; }
        public long nowMillis() { return 0; }
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
