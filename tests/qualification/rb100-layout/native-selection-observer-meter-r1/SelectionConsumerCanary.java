package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy.*;

/** Native Java 7 component canary. All cap frames are injected generic TEST fixtures.
 * Does not invoke assembly, read authored inputs, export results, or exhaust a route.
 */
public final class SelectionConsumerCanary {
    private static int assertions, groups, fixtures, beginCalls, detachedCopies;
    private static String phase="start";
    private static final String REQUEST="generic-selection";
    private static final String[] HEAD_FIELDS={"branchOrdinal","cell","layer","direction",
        "transitions","cost","storedHeuristic","serial","key","storedPriority"};
    private static final int[] HEAD_VALUES={1,7,1,4,2,11,13,17,2907,24};
    private static final int SETUP_GUARD=1000, DRAIN_GUARD=100000;
    private SelectionConsumerCanary() { }

    public static void main(String[] args) throws Exception {
        try {
            denialAndShape();
            actualAdapter();
            literalAccountingAndAtomicity();
            malformedSelections();
            staleRejection();
            failureMatrix();
            factoryLimits();
            actualInjectedFrames();
            actualSmallRouteParity();
            System.out.println("PASS SelectionConsumerCanary assertions="+assertions+
                " groups="+groups+" fixtures="+fixtures+" beginCalls="+beginCalls+
                " headDtoCopies="+detachedCopies+" ownerDetachedCopies=0"+
                " boundary=INJECTED_GENERIC_COMPONENT_ONLY authored=0");
        } catch(Exception failure) { progress(); throw failure; }
        catch(Error failure) { progress(); throw failure; }
    }

    private static void progress() {
        System.err.println("FAILED SelectionConsumerCanary phase="+phase+" assertions="+
            assertions+" groupsStarted="+groups+" fixtures="+fixtures+
            " beginCalls="+beginCalls+" headDtoCopies="+detachedCopies+" ownerDetachedCopies=0"+" authored=0");
    }
    private static void group(String name) { phase=name; groups++; }

    private static void denialAndShape() {
        group("denial-and-immutable-shape");
        Throwable denied=failure(new Action() { public void run() {
            CohortTerminalTelemetryStudy.main(null);
        }});
        check(denied instanceof IllegalStateException &&
            "SELECTION_CONSUMER_COMPONENT_ONLY".equals(denied.getMessage()),
            "main denies before argument parsing or Session setup");
        Fake m=new Fake(); TerminalTelemetryObserver o=observer(m);
        denied=failure(new ActionObserver(o) { public void run() { value.resultMap(); }});
        check(denied instanceof IllegalStateException &&
            "SELECTION_CONSUMER_NO_WIRE".equals(denied.getMessage()),"unsealed export denies fixed wire message");
        o.seal();
        denied=failure(new ActionObserver(o) { public void run() { value.resultMap(); }});
        check("SELECTION_CONSUMER_NO_WIRE".equals(denied.getMessage()),"sealed export also denies");
        check(m.units==0 && beginCalls==0,"denial does no observer or Session work");
        Class<?> t=studyType("DetachedHead");
        check(Modifier.isStatic(t.getModifiers()) && Modifier.isFinal(t.getModifiers()),
            "DetachedHead is static final");
        TreeSet<String> names=new TreeSet<String>();
        for(Field f:t.getDeclaredFields()) {
            check(!Modifier.isStatic(f.getModifiers()),"head has no hidden static state");
            names.add(f.getName());
            check(f.getType()==Integer.TYPE && Modifier.isFinal(f.getModifiers()),
                "head contains only final primitive ints");
        }
        check(names.equals(new TreeSet<String>(Arrays.asList(HEAD_FIELDS))),"exact independent ten-field schema");
        for(String name:new String[]{"selectedHead","headCopies","headReleases"})
            check(Modifier.isPrivate(declared(TerminalTelemetryObserver.class,name).getModifiers()),
                "observer selection/accounting remains private: "+name);
        check(TerminalTelemetryObserver.FIELD_COUNT==33 &&
            TerminalTelemetryObserver.CALLBACK_CEILING==512 &&
            TerminalTelemetryObserver.POST_CALL_BOUND==104 &&
            TerminalTelemetryObserver.GROUP_CAPACITY==32768 &&
            TerminalTelemetryObserver.COPY_CAPACITY==1048576L,"parent limits unchanged");
    }

    private static void actualAdapter() throws Exception {
        group("actual-shared-Meter-adapter");
        Actual a=new Actual();
        ObservationSpec s=spec(a.port,2);
        TerminalTelemetryObserver o=new TerminalTelemetryObserver(a.port,s);
        TerminalSink sink=new TerminalSink(a.port,o,REQUEST);
        check(sink instanceof PcbLayerRoutingPrototype.BranchLimitSelectionDiagnostics,
            "actual sink opts into owner selected diagnostics");
        Ledger before=new Ledger(a.port);
        sink.onBranchLimitSelected(snapshot("BRANCH_SEARCH_LIMIT"),head());
        detachedCopies+=(int)number(o,"headCopies");
        before.delta(a.port,192,43,2,"actual selected adapter");
        literalState(o,43,2,102,192,1,1);
        check(!a.port.insideOwner && a.port.entriesRemaining==0,"actual selected adapter restores port state");
        headEquals(get(o,"selectedHead"),HEAD_VALUES);
        check(number(a.nativeMeter,"units")==a.port.units() &&
            number(a.nativeMeter,"copiedEntries")==a.port.copiedEntries() &&
            number(a.nativeMeter,"copyChunks")==a.port.copyChunks(),"port exposes actual shared Meter ledger");
        a.setup.finish();

        Actual ordinary=new Actual();
        TerminalTelemetryObserver old=new TerminalTelemetryObserver(ordinary.port,spec(ordinary.port,2));
        Ledger oldBefore=new Ledger(ordinary.port);
        new TerminalSink(ordinary.port,old,REQUEST).onTerminalBranch(snapshot("NO_PATH"));
        oldBefore.delta(ordinary.port,159,33,1,"actual ordinary adapter");
        check(!ordinary.port.insideOwner && get(old,"selectedHead")==null,"ordinary sink restores without head");
        ordinary.setup.finish();

        // Exact selected admission boundary: 42 remaining slots cannot admit either chunk.
        Actual limited=new Actual();
        TerminalTelemetryObserver rejected=new TerminalTelemetryObserver(limited.port,spec(limited.port,2));
        final TerminalSink rejectedSink=new TerminalSink(limited.port,rejected,REQUEST);
        set(limited.nativeMeter,"copiedEntries",Long.valueOf(1048576L-42));
        Ledger capBefore=new Ledger(limited.port);
        Throwable t=failure(new Action() { public void run() {
            rejectedSink.onBranchLimitSelected(snapshot("BRANCH_SEARCH_LIMIT"),head());
        }});
        check(t instanceof IllegalStateException && t.getMessage().contains("copy capacity"),
            "actual selected adapter rejects aggregate capacity before copying");
        capBefore.delta(limited.port,11,0,0,"actual capacity rejection");
        check(number(rejected,"copiedSlots")==0 && number(rejected,"headCopies")==0 &&
            number(rejected,"releases")==1 && number(rejected,"headReleases")==1 &&
            get(rejected,"selected")==null && get(rejected,"selectedHead")==null,
            "capacity rejection releases both borrows without partial selection");
        check(!limited.port.insideOwner && limited.port.entriesRemaining==0,
            "actual rejected adapter restores insideOwner");
        poisoned(rejected); limited.setup.finish();

        Actual exact=new Actual();
        TerminalTelemetryObserver admitted=new TerminalTelemetryObserver(exact.port,spec(exact.port,2));
        set(exact.nativeMeter,"copiedEntries",Long.valueOf(1048576L-43));
        Ledger exactBefore=new Ledger(exact.port);
        new TerminalSink(exact.port,admitted,REQUEST).onBranchLimitSelected(snapshot("BRANCH_SEARCH_LIMIT"),head());
        detachedCopies++;
        exactBefore.delta(exact.port,192,43,2,"injected exact-43-entry boundary");
        check(exact.port.copiedEntries()==1048576L && exact.port.entriesRemaining==0,
            "injected exact-entry boundary completes both chunks");
        exact.setup.finish();

        Actual chunks=new Actual();
        TerminalTelemetryObserver chunkObserver=new TerminalTelemetryObserver(chunks.port,spec(chunks.port,2));
        final TerminalSink chunkSink=new TerminalSink(chunks.port,chunkObserver,REQUEST);
        set(chunks.nativeMeter,"copyChunks",Integer.valueOf(Integer.MAX_VALUE-1));
        Ledger chunkBefore=new Ledger(chunks.port);
        Throwable chunkFailure=failure(new Action() { public void run() {
            chunkSink.onBranchLimitSelected(snapshot("BRANCH_SEARCH_LIMIT"),head());
        }});
        check(chunkFailure instanceof IllegalStateException,"injected int chunk boundary rejects selected pair");
        chunkBefore.delta(chunks.port,12,0,0,"injected two-chunk admission boundary");
        check(number(chunkObserver,"copiedSlots")==0 && number(chunkObserver,"headCopies")==0 &&
            !chunks.port.insideOwner && chunks.port.entriesRemaining==0,
            "actual int chunk preflight rejects before either DTO copy");
        poisoned(chunkObserver); chunks.setup.finish();
    }

    private static void literalAccountingAndAtomicity() {
        group("literal-ledgers-atomic-binding-finality");
        Fake selectedMeter=new Fake(); TerminalTelemetryObserver selected=observer(selectedMeter);
        PcbLayerRoutingPrototype.BranchSnapshot source=snapshot("BRANCH_SEARCH_LIMIT");
        PcbLayerRoutingPrototype.BranchLimitSelection sourceHead=head();
        selected.observeSelected(REQUEST,source,sourceHead);
        detachedCopies+=(int)number(selected,"headCopies");
        literalState(selected,43,2,102,192,1,1);
        check(selectedMeter.units==192 && selectedMeter.copies==43 && selectedMeter.chunks==2,
            "independent selected Meter exact ledger");
        Object terminal=get(selected,"selected"), detached=get(selected,"selectedHead");
        equalFields(source,terminal,"literal unchanged33-field copy");
        check(terminal!=source && detached!=sourceHead,"both owner DTOs detach");
        check(number(terminal,"pass")==2 && "N".equals(get(terminal,"netId")) &&
            "S".equals(get(terminal,"startPadId")) && "R".equals(get(terminal,"rootPadId")) &&
            number(terminal,"branchExpanded")==100000 && number(terminal,"totalExpanded")==100000,
            "selected terminal binds the literal callback context");
        headEquals(detached,HEAD_VALUES);
        set(source,"netId","TEST_MUTATED_OWNER"); set(sourceHead,"cost",Integer.valueOf(999));
        check("N".equals(get(terminal,"netId")) && number(detached,"cost")==11,
            "detached records do not alias subsequently TEST-mutated source DTOs");

        Fake absentMeter=new Fake(); TerminalTelemetryObserver absent=observer(absentMeter);
        absent.observe(REQUEST,snapshot("NO_PATH"));
        literalState(absent,33,1,90,159,0,0);
        check(selectedMeter.units-absentMeter.units==33 &&
            number(selected,"checks")-number(absent,"checks")==12 &&
            selectedMeter.copies-absentMeter.copies==10 && selectedMeter.chunks-absentMeter.chunks==1,
            "head delta is 20 copy units, 12 predicates, one borrowed release");
        check(get(absent,"selectedHead")==null,"ordinary non-found selection retains no head");

        Fake m=new Fake(); final TerminalTelemetryObserver first=observer(m);
        first.observe(REQUEST,snapshot("FOUND"));
        literalState(first,33,1,91,160,0,0);
        check(get(first,"selected")==null && get(first,"selectedHead")==null &&
            number(first,"found")==1,"FOUND retains neither selection");
        first.observeSelected(REQUEST,snapshot("BRANCH_SEARCH_LIMIT"),head());
        detachedCopies++;
        check(number(first,"accepted")==2 && number(first,"found")==1 &&
            number(first,"failures")==1 && number(first,"selectedCallbackOrdinal")==2,
            "first non-found atomically selects after FOUND");
        Object keep=get(first,"selected"), keepHead=get(first,"selectedHead");
        Throwable t=failure(new Action() { public void run() { first.observe(REQUEST,snapshot("FOUND")); }});
        check(t instanceof IllegalArgumentException && t.getMessage().contains("followed terminal failure"),
            "callback after first failure rejects");
        check(get(first,"selected")==keep && get(first,"selectedHead")==keepHead &&
            number(first,"accepted")==2,"first failure remains final and atomic on rejection");
        // Third callback detached the ordinary tuple; no new head copy was completed.
        poisoned(first);
    }

    private static void malformedSelections() {
        group("missing-wrong-and-malformed-heads");
        rejectFixture(snapshot("BRANCH_SEARCH_LIMIT"),null,false,"missing selected head");
        rejectFixture(snapshot("BRANCH_SEARCH_LIMIT"),null,true,"ordinary entry misses required head");
        rejectFixture(snapshot("NO_PATH"),head(),false,"NO_PATH cannot carry head");
        rejectFixture(snapshot("FOUND"),head(),false,"FOUND cannot carry head");
        rejectFixture(snapshot("TOTAL_SEARCH_LIMIT"),head(),false,"TOTAL cap cannot carry head");
        rejectFixture(null,head(),false,"missing snapshot cannot bind selected head");
        String[] fields={"branchOrdinal","cell","cell","layer","layer","direction","direction",
            "transitions","transitions","cost","storedHeuristic","serial","key","storedPriority"};
        int[] bad={0,-1,100,-1,2,-1,5,-1,49,-1,-1,-1,2908,25};
        for(int i=0;i<fields.length;i++) {
            PcbLayerRoutingPrototype.BranchLimitSelection h=head(); set(h,fields[i],Integer.valueOf(bad[i]));
            rejectFixture(snapshot("BRANCH_SEARCH_LIMIT"),h,false,"bad scalar "+fields[i]+"="+bad[i]);
        }
        PcbLayerRoutingPrototype.BranchSnapshot prefix=snapshot("BRANCH_SEARCH_LIMIT");
        set(prefix,"prefixViaCount",Integer.valueOf(47));
        rejectFixture(prefix,head(),false,"transition bound includes prefix vias");
        PcbLayerRoutingPrototype.BranchSnapshot count=snapshot("BRANCH_SEARCH_LIMIT");
        set(count,"bestStateCount",Integer.valueOf(0));
        rejectFixture(count,head(),false,"selection requires nonempty best-state index");
        PcbLayerRoutingPrototype.BranchSnapshot global=snapshot("BRANCH_SEARCH_LIMIT");
        set(global,"totalExpanded",Integer.valueOf(1000000));
        rejectFixture(global,head(),false,"selected cap must precede total cap");
        PcbLayerRoutingPrototype.BranchSnapshot local=snapshot("BRANCH_SEARCH_LIMIT");
        set(local,"branchExpanded",Integer.valueOf(99999)); set(local,"expandedTop",Integer.valueOf(99999));
        rejectFixture(local,head(),false,"local cap must be exactly 100000");
        PcbLayerRoutingPrototype.BranchLimitSelection wrap=head();
        set(wrap,"cost",Integer.valueOf(Integer.MAX_VALUE)); set(wrap,"storedHeuristic",Integer.valueOf(1));
        set(wrap,"storedPriority",Integer.valueOf(Integer.MIN_VALUE));
        Fake wm=new Fake(); TerminalTelemetryObserver wo=observer(wm);
        wo.observeSelected(REQUEST,snapshot("BRANCH_SEARCH_LIMIT"),wrap); detachedCopies++;
        check(number(get(wo,"selectedHead"),"storedPriority")==Integer.MIN_VALUE,
            "Java int priority wrap is accepted as specified");
        literalState(wo,43,2,102,192,1,1);
        set(wrap,"storedPriority",Integer.valueOf(Integer.MAX_VALUE));
        rejectFixture(snapshot("BRANCH_SEARCH_LIMIT"),wrap,false,"nonwrapping substitute priority rejects");
        // Maximum legal state key: (((48*2+1)*5+4)*250000)+249999 = 122499999.
        PcbLayerRoutingPrototype.BranchSnapshot wide=snapshot("BRANCH_SEARCH_LIMIT");
        set(wide,"gridWidth",Integer.valueOf(250000)); set(wide,"gridHeight",Integer.valueOf(1));
        PcbLayerRoutingPrototype.BranchLimitSelection edge=head();
        set(edge,"cell",Integer.valueOf(249999)); set(edge,"transitions",Integer.valueOf(48));
        set(edge,"key",Integer.valueOf(122499999));
        Fake em=new Fake(); TerminalTelemetryObserver eo=observer(em);
        eo.observeSelected(REQUEST,wide,edge); detachedCopies++;
        check(number(get(eo,"selectedHead"),"key")==122499999,"literal boundary key formula accepted");
    }

    private static void rejectFixture(final PcbLayerRoutingPrototype.BranchSnapshot s,
            final PcbLayerRoutingPrototype.BranchLimitSelection h,final boolean ordinary,String label) {
        final Fake m=new Fake(); final TerminalTelemetryObserver o=observer(m);
        Throwable t=failure(new Action() { public void run() {
            if(ordinary) o.observe(REQUEST,s); else o.observeSelected(REQUEST,s,h);
        }});
        detachedCopies+=(int)number(o,"headCopies");
        check(t instanceof IllegalArgumentException,label+" rejects malformed callback");
        check(get(o,"selected")==null && get(o,"selectedHead")==null && number(o,"accepted")==0,
            label+" cannot partially commit");
        check(number(o,"releases")== (s==null?0:1) && number(o,"headReleases")== (h==null?0:1),
            label+" separately releases actual borrows");
        poisoned(o);
    }

    private static void staleRejection() {
        group("sealed-separate-rejection-Meter");
        Fake m=new Fake(); final TerminalTelemetryObserver o=observer(m);
        o.observeSelected(REQUEST,snapshot("BRANCH_SEARCH_LIMIT"),head()); detachedCopies++;
        o.seal(); State state=new State(o); Ledger before=new Ledger(m);
        final Fake rejection=new Fake();
        Throwable t=failure(new Action() { public void run() {
            core(o,snapshot("BRANCH_SEARCH_LIMIT"),head(),rejection);
        }});
        check(t instanceof IllegalStateException && t.getMessage().contains("stale"),"sealed selected callback rejects");
        check(rejection.units==3 && rejection.copies==0 && rejection.chunks==0,
            "separate stale ledger charges reject and both borrowed releases");
        before.delta(m,0,0,0,"original stale ledger"); state.same(o,"sealed selected state");
        final Fake ordinary=new Fake();
        failure(new Action() { public void run() { o.observe(REQUEST,snapshot("NO_PATH"),ordinary); }});
        check(ordinary.units==2,"ordinary stale rejection charges snapshot only");
        state.same(o,"ordinary stale state");
        failure(new Action() { public void run() { core(o,snapshot("BRANCH_SEARCH_LIMIT"),head(),null); }});
        before.delta(m,0,0,0,"missing stale meter original ledger"); state.same(o,"missing rejection meter state");
    }

    private static void failureMatrix() throws Exception {
        group("copy-release-RuntimeException-Error-identity-rollback");
        for(int kind=0;kind<2;kind++) for(int at=0;at<3;at++) {
            final Fake m=new Fake(); final TerminalTelemetryObserver o=observer(m);
            o.observe(REQUEST,snapshot("FOUND"));
            final Throwable sentinel=kind==0?new IllegalStateException("TEST sentinel"):
                new AssertionError("TEST sentinel");
            // First selected head field occurs at copy 67: prior 33 + this tuple's 33.
            // Mid-head failure occurs after six copied head fields; release failures are post-commit.
            if(at==0) m.failCopy=73; // prior 33 + tuple 33 + seventh head copy
            else m.failCharge=m.units+(at==1?191:192);
            m.sentinel=sentinel;
            // The observer meter is the deliberate fault injector; the unchanged actual
            // adapter port supplies TerminalSink's owner-entry/finally guard.
            Actual guard=new Actual(); final TerminalSink failingSink=new TerminalSink(guard.port,o,REQUEST);
            Object old=get(o,"selected"), oldHead=get(o,"selectedHead");
            Throwable caught=failure(new Action() { public void run() {
                failingSink.onBranchLimitSelected(snapshot("BRANCH_SEARCH_LIMIT"),head());
            }});
            detachedCopies+=(int)number(o,"headCopies");
            check(caught==sentinel,"original unchecked failure identity kind="+kind+" point="+at);
            check(!guard.port.insideOwner,"TerminalSink finally restores insideOwner for original unchecked failure");
            guard.setup.finish();
            check(number(o,"accepted")==1 && number(o,"found")==1 && number(o,"failures")==0 &&
                number(o,"selectedCallbackOrdinal")==0 && get(o,"selected")==old &&
                get(o,"selectedHead")==oldHead,"failed copy/release rolls back both selections and accepted counters");
            check(!bool(o,"inCallback") && number(o,"releases")==2 && number(o,"headReleases")==1,
                "failure clears callback state and attempts both releases");
            check(number(o,"headCopies")== (at==0?0:1),"only completed head detach is counted");
            poisoned(o);
            Ledger before=new Ledger(m); State frozen=new State(o);
            final Fake reject=new Fake();
            failure(new Action() { public void run() { core(o,snapshot("BRANCH_SEARCH_LIMIT"),head(),reject); }});
            before.delta(m,0,0,0,"poisoned original ledger"); frozen.same(o,"poisoned stale callback");
        }
        // Faults through the actual TerminalSink must restore its guard too. Use the
        // real adapter's ledger bounds rather than replacing its final meter port.
        Actual actual=new Actual(); final TerminalTelemetryObserver o=
            new TerminalTelemetryObserver(actual.port,spec(actual.port,2));
        final TerminalSink sink=new TerminalSink(actual.port,o,REQUEST);
        set(actual.nativeMeter,"groupUnits",Integer.valueOf(32768-512-104+1));
        failure(new Action() { public void run() { sink.onBranchLimitSelected(snapshot("BRANCH_SEARCH_LIMIT"),head()); }});
        check(!actual.port.insideOwner && !bool(o,"inCallback"),"actual adapter restores guard on rejected owner callback");
        poisoned(o); actual.setup.finish();
        for(final String counter:new String[]{"headCopies","headReleases"}) {
            final Fake meter=new Fake(); final TerminalTelemetryObserver overflow=observer(meter);
            set(overflow,counter,Long.valueOf(Long.MAX_VALUE-511));
            Throwable t=failure(new Action() { public void run() {
                overflow.observeSelected(REQUEST,snapshot("BRANCH_SEARCH_LIMIT"),head());
            }});
            check(t instanceof IllegalStateException && t.getMessage().contains("counter overflow"),
                "new local counter participates in preflight overflow guard: "+counter);
            check(meter.copies==0 && meter.chunks==0 && get(overflow,"selectedHead")==null,
                "local head counter overflow rejects before copying");
            poisoned(overflow);
        }
    }

    private static void factoryLimits() {
        group("unaltered-spec-factory-limits");
        for(int pass:new int[]{-1,0,1,3}) {
            final int p=pass;
            Throwable t=failure(new Action() { public void run() { spec(new Fake(),p); }});
            check(t instanceof IllegalArgumentException && "Invalid pass".equals(t.getMessage()),
                "factory pass remains exactly two");
        }
        for(final int[] census:new int[][]{{247,1},{2,61},{0,1},{2,0}}) {
            Throwable t=failure(new Action() { public void run() {
                ObservationSpec.create(new Fake(),REQUEST,2,census[0],census[1],pads(),ordinals(),counts());
            }});
            check(t instanceof IllegalArgumentException,"factory pad/net census bounds unchanged");
        }
    }

    private static void actualInjectedFrames() throws Exception {
        group("actual-P06-injected-owner-frames");
        for(String reason:new String[]{"BRANCH_SEARCH_LIMIT","NO_PATH","TOTAL_SEARCH_LIMIT"}) {
            Delegate marker=new Delegate(); Frame f=frame(marker); marker.frame=f;
            int cells=(int)number(f.search,"cells"),layer=(int)number(f.search,"primary");
            Object chosen=node(cells,7,layer,1,0,11,13,17);
            Object later=node(cells,8,layer,1,0,12,13,18);
            if("NO_PATH".equals(reason)) frontier(f.branch,new Object[0]);
            else frontier(f.branch,new Object[]{chosen,later});
            int local="NO_PATH".equals(reason)?0:100000;
            int total="TOTAL_SEARCH_LIMIT".equals(reason)?1000000:local;
            counters(f,local,total); marker.reason=reason; marker.chosen=chosen;
            String before=f.fixture.layout.geometryFingerprint();
            f.session.advanceSlice(1);
            check(marker.calls==1 && marker.selected==("BRANCH_SEARCH_LIMIT".equals(reason)?1:0),
                "actual injected callback mode "+reason);
            check(marker.sink!=null && !marker.actual.port.insideOwner &&
                get(f.session,"search")==null,"actual terminal callback returns with owner state released");
            check(before.equals(f.fixture.layout.geometryFingerprint()) &&
                f.fixture.layout.getTraces().isEmpty() && f.fixture.layout.getHoles().isEmpty(),
                "actual injected frame preserves caller geometry");
            marker.actual.setup.finish();
        }
    }

    private static void actualSmallRouteParity() {
        group("actual-small-route-and-total-cap-parity");
        for(int maximum:new int[]{1000000,1}) {
            PcbLayerRoutingPrototype.Result plain=route(null,maximum);
            OldSink old=new OldSink(); PcbLayerRoutingPrototype.Result a=route(old,maximum);
            Marker selected=new Marker(); PcbLayerRoutingPrototype.Result b=route(selected,maximum);
            sameResult(plain,a); sameResult(plain,b);
            check(old.events.size()==selected.events.size() && selected.selected==0,
                "ordinary small-route/cap callbacks preserve old marker parity");
            for(int i=0;i<old.events.size();i++) equalFields(old.events.get(i),selected.events.get(i),"old/new ordinary event");
            if(maximum==1) check(!plain.accepted() && plain.expansions==1 &&
                plain.outcome.endsWith("TOTAL_SEARCH_LIMIT"),"bounded actual one-expansion cap");
            else check(plain.accepted(),"actual small P06 route succeeds");
        }
    }

    private static final class Actual {
        final Parent parent; final Object nativeMeter; final SetupMeter setup; final ObserverMeter port;
        Actual() throws Exception {
            parent=new Parent(); nativeMeter=parent.meter(System.nanoTime());
            setup=new SetupMeter(parent,nativeMeter);
            setup.method("reserve",new Class<?>[]{Long.TYPE},Long.valueOf(1));
            port=new ObserverMeter(setup);
            check(port.isOpen(),"actual private parent Meter opens by maintained reserve owner");
        }
    }
    private static final class Fake implements TerminalObserverMeter {
        long units,copies,chunks; int remaining; long failCharge=-1,failCopy=-1; Throwable sentinel;
        public boolean isOpen() { return true; }
        public long units() { return units; }
        public long groupUnits() { return units; }
        public long copiedEntries() { return copies; }
        public long copyChunks() { return chunks; }
        public void charge(long n) {
            check(n==1,"observer only charges one logical unit at a time");
            if(units+n==failCharge) { failCharge=-1; raise(sentinel); }
            units+=n;
        }
        public void beginCopyChunk(int n) {
            check(remaining==0 && (n==33 || n==10 || n<=4),"fixture chunk admitted without unfinished predecessor");
            remaining=n; chunks++;
        }
        public void copyEntry() {
            check(remaining>0,"copy entry belongs to predeclared chunk");
            if(copies+1==failCopy) { failCopy=-1; raise(sentinel); }
            remaining--; copies++;
        }
    }
    private static final class Ledger {
        final long units,copies,chunks;
        Ledger(TerminalObserverMeter m) { units=m.units(); copies=m.copiedEntries(); chunks=m.copyChunks(); }
        void delta(TerminalObserverMeter m,long u,long c,long h,String label) {
            check(m.units()-units==u,label+" exact unit delta expected="+u+" actual="+(m.units()-units));
            check(m.copiedEntries()-copies==c,label+" exact scalar copy delta");
            check(m.copyChunks()-chunks==h,label+" exact chunk delta");
        }
    }
    private static final class State {
        final Map<String,Object> fields=new LinkedHashMap<String,Object>();
        State(Object o) {
            for(Field f:o.getClass().getDeclaredFields()) if(!Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true); try { fields.put(f.getName(),f.get(o)); }
                catch(Exception e) { throw new AssertionError(e); }
            }
        }
        void same(Object o,String label) {
            for(Map.Entry<String,Object> e:fields.entrySet()) {
                Object now=get(o,e.getKey()),before=e.getValue();
                check(before==null?now==null:before.equals(now),label+" field "+e.getKey());
            }
        }
    }
    private static TerminalTelemetryObserver observer(TerminalObserverMeter meter) {
        // Setup is isolated from callback ledgers; factory itself is separately exercised.
        return new TerminalTelemetryObserver(meter,spec(new Fake(),2));
    }
    private static ObservationSpec spec(TerminalObserverMeter meter,int pass) {
        return ObservationSpec.create(meter,REQUEST,pass,2,1,pads(),ordinals(),counts());
    }
    private static Map<String,PadIdentity> pads() {
        Map<String,PadIdentity> m=new LinkedHashMap<String,PadIdentity>();
        m.put("S",new PadIdentity("N",1,2)); m.put("R",new PadIdentity("N",8,9)); return m;
    }
    private static Map<String,Integer> ordinals() {
        Map<String,Integer> m=new LinkedHashMap<String,Integer>(); m.put("N",Integer.valueOf(1)); return m;
    }
    private static Map<String,Integer> counts() {
        Map<String,Integer> m=new LinkedHashMap<String,Integer>(); m.put("N",Integer.valueOf(2)); return m;
    }
    private static PcbLayerRoutingPrototype.BranchSnapshot snapshot(String reason) {
        Object[] v={2,"N","S","R",1,2,8,9,10,10,1,0,0,1,14,0,
            100000,"TOTAL_SEARCH_LIMIT".equals(reason)?1000000:100000,100000,0,
            "NO_PATH".equals(reason)?0:2,3,reason,false,false,false,false,false,false,0,0,0,0};
        Class<?>[] types={Integer.TYPE,String.class,String.class,String.class,
            Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,
            Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,
            Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,String.class,
            Boolean.TYPE,Boolean.TYPE,Boolean.TYPE,Boolean.TYPE,Boolean.TYPE,Boolean.TYPE,
            Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE};
        return (PcbLayerRoutingPrototype.BranchSnapshot)construct(PcbLayerRoutingPrototype.BranchSnapshot.class,types,v);
    }
    private static PcbLayerRoutingPrototype.BranchLimitSelection head() {
        Object n=node(100,7,1,4,2,11,13,17);
        return (PcbLayerRoutingPrototype.BranchLimitSelection)construct(
            PcbLayerRoutingPrototype.BranchLimitSelection.class,new Class<?>[]{ownerType("Node"),Integer.TYPE},n,1);
    }
    private static Object node(int cells,int cell,int layer,int direction,int transitions,int cost,int h,int serial) {
        Class<?> t=ownerType("Node");
        return construct(t,new Class<?>[]{Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,
            Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,t},cell,layer,direction,transitions,cost,h,serial,cells,null);
    }
    private static void core(TerminalTelemetryObserver o,PcbLayerRoutingPrototype.BranchSnapshot s,
            PcbLayerRoutingPrototype.BranchLimitSelection h,TerminalObserverMeter rejection) {
        // Exercise the supported selected stale-entry API, not private-core reflection.
        o.observeSelected(REQUEST,s,h,rejection);
    }
    private static void literalState(Object o,int copies,int chunks,int validations,int units,int heads,int headReleases) {
        check(number(o,"copiedSlots")==copies && number(o,"inspections")==copies,"literal scalar copy/inspection accounting");
        check(number(o,"copyChunks")==chunks && number(o,"checks")==validations,"literal chunk/validation accounting");
        check(number(o,"unitsCharged")==units && number(o,"unitsMeasured")==units,"literal units equal actual ledger sampling");
        check(number(o,"headCopies")==heads && number(o,"headReleases")==headReleases,"literal head detach/release counts");
        check(number(o,"releases")==1 && number(o,"accepted")==1 && number(o,"dispatches")==1,
            "one borrowed snapshot accepted and released");
        check(!bool(o,"inCallback") && !bool(o,"poisoned"),"successful observer returns clean callback state");
    }
    private static void headEquals(Object o,int[] expected) {
        check(o!=null,"selected detached head exists");
        for(int i=0;i<HEAD_FIELDS.length;i++) check(number(o,HEAD_FIELDS[i])==expected[i],"literal head field "+HEAD_FIELDS[i]);
    }
    private static void poisoned(Object o) {
        check(bool(o,"poisoned") && bool(o,"sealed") && !bool(o,"inCallback"),"failed callback seals and poisons");
    }

    private static final class Frame {
        final P06FactoryLinkFixtures.Fixture fixture; final PcbLayerRoutingPrototype.Session session;
        final Object search,branch;
        Frame(P06FactoryLinkFixtures.Fixture f,PcbLayerRoutingPrototype.Session s,Object q,Object b) {
            fixture=f; session=s; search=q; branch=b;
        }
    }
    private static Frame frame(PcbLayerRoutingPrototype.Diagnostics sink) {
        P06FactoryLinkFixtures.Fixture f=fixture();
        PcbLayerRoutingPrototype.Policy p=PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER;
        beginCalls++;
        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(f.board,f.layout,p,
            P06FactoryLinkFixtures.OBSERVER,1000000,p.vias,sink);
        // Explicit TEST injection before Search exists: preserve the factory's pass-2
        // admission while obtaining a real Session/Search callback at that pass.
        set(session,"pass",Integer.valueOf(2));
        Object search=null,branch=null;
        for(int i=0;i<SETUP_GUARD && branch==null;i++) {
            session.advanceSlice(1); search=get(session,"search"); branch=search==null?null:get(search,"branch");
        }
        check(search!=null && branch!=null && number(branch,"expanded")==0,
            "bounded actual P06 setup finds unexpanded generic branch");
        return new Frame(f,session,search,branch);
    }
    private static P06FactoryLinkFixtures.Fixture fixture() {
        fixtures++; return new P06FactoryLinkFixtures.Fixture(true,0,0,true);
    }
    private static void counters(Frame f,int local,int total) {
        check(get(f.session,"budget")==get(f.search,"budget"),"actual Session/Search share budget");
        set(f.branch,"expanded",Integer.valueOf(local));
        int primary=(int)number(f.search,"primary");
        set(f.branch,"expandedTop",Integer.valueOf(primary==PcbCopperLayer.TOP.ordinal()?local:0));
        set(f.branch,"expandedBottom",Integer.valueOf(primary==PcbCopperLayer.BOTTOM.ordinal()?local:0));
        // Counter injection does not explore any cell. Supply the matching explicit
        // TEST minimum so the unchanged 33-field scalar contract remains coherent.
        set(f.branch,"minimumExploredManhattanDistance",Integer.valueOf(local==0?Integer.MAX_VALUE:0));
        set(get(f.session,"budget"),"expanded",Integer.valueOf(total));
    }
    private static void frontier(Object branch,Object[] nodes) {
        Object queue=get(branch,"queue"),index=get(branch,"best");
        Arrays.fill((Object[])get(queue,"values"),null); set(queue,"size",Integer.valueOf(0));
        Arrays.fill((int[])get(index,"keys"),0); Arrays.fill((Object[])get(index,"values"),null);
        set(index,"size",Integer.valueOf(0));
        for(Object n:nodes) {
            invoke(queue,"add",new Class<?>[]{ownerType("Node")},n);
            invoke(index,"put",new Class<?>[]{Integer.TYPE,ownerType("Node")},(int)number(n,"key"),n);
        }
    }
    private static final class RawFrame {
        final Object[] queue,index; final int[] keys; final int qsize,isize;
        final long expanded,total; final Object start,root,net;
        RawFrame(Frame f) {
            Object q=get(f.branch,"queue"),ix=get(f.branch,"best");
            queue=((Object[])get(q,"values")).clone(); index=((Object[])get(ix,"values")).clone();
            keys=((int[])get(ix,"keys")).clone(); qsize=(int)number(q,"size"); isize=(int)number(ix,"size");
            expanded=number(f.branch,"expanded"); total=number(get(f.session,"budget"),"expanded");
            start=get(f.branch,"start"); root=get(f.branch,"root"); net=get(f.branch,"net");
        }
        void same(Frame f) {
            Object q=get(f.branch,"queue"),ix=get(f.branch,"best");
            check(qsize==number(q,"size") && isize==number(ix,"size"),"observer preserves raw queue/index sizes");
            check(Arrays.equals(queue,(Object[])get(q,"values")) && Arrays.equals(index,(Object[])get(ix,"values")) &&
                Arrays.equals(keys,(int[])get(ix,"keys")),"observer preserves every raw queue/index slot identity");
            check(expanded==number(f.branch,"expanded") && total==number(get(f.session,"budget"),"expanded") &&
                start==get(f.branch,"start") && root==get(f.branch,"root") && net==get(f.branch,"net"),
                "observer preserves branch counters and endpoint context");
        }
    }
    private static class OldSink implements PcbLayerRoutingPrototype.TerminalOnlyDiagnostics {
        final ArrayList<PcbLayerRoutingPrototype.BranchSnapshot> events=new ArrayList<PcbLayerRoutingPrototype.BranchSnapshot>();
        public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot s) { events.add(s); }
    }
    private static final class Marker extends OldSink implements PcbLayerRoutingPrototype.BranchLimitSelectionDiagnostics {
        int selected;
        public void onBranchLimitSelected(PcbLayerRoutingPrototype.BranchSnapshot s,
                PcbLayerRoutingPrototype.BranchLimitSelection h) { selected++; events.add(s); }
    }
    private static final class Delegate implements PcbLayerRoutingPrototype.BranchLimitSelectionDiagnostics {
        Frame frame; Object chosen; String reason; int calls,selected; Actual actual; TerminalSink sink;
        public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot s) { receive(s,null); }
        public void onBranchLimitSelected(PcbLayerRoutingPrototype.BranchSnapshot s,
                PcbLayerRoutingPrototype.BranchLimitSelection h) { selected++; receive(s,h); }
        private void receive(PcbLayerRoutingPrototype.BranchSnapshot s,PcbLayerRoutingPrototype.BranchLimitSelection h) {
            calls++;
            check(calls==1 && reason.equals(s.terminalReason),"actual delegate binds single requested terminal reason");
            check(get(frame.session,"search")==frame.search && get(frame.search,"branch")==frame.branch &&
                bool(frame.branch,"terminalEmitted"),"consumer runs in actual live owner callback");
            check(s.pass==number(frame.search,"pass") && s.netId.equals(get(frame.branch,"net")) &&
                s.branchExpanded==number(frame.branch,"expanded") &&
                s.totalExpanded==number(get(frame.session,"budget"),"expanded"),"actual snapshot and head share live branch context");
            if(h!=null) {
                check(h.cell==7 && h.direction==1 && h.transitions==0 && h.cost==11 &&
                    h.storedHeuristic==13 && h.serial==17 && h.storedPriority==24 &&
                    h.layer==number(frame.search,"primary") && h.branchOrdinal==number(frame.branch,"branchOrdinal"),
                    "actual selected DTO binds injected literal node in same callback");
                check(h.key==(((h.layer*5+1)*(int)number(frame.search,"cells"))+7),"actual selected literal key");
            }
            RawFrame before=new RawFrame(frame);
            try {
                actual=new Actual(); ObservationSpec spec=actualSpec(frame,s,actual.port);
                TerminalTelemetryObserver o=new TerminalTelemetryObserver(actual.port,spec);
                sink=new TerminalSink(actual.port,o,REQUEST); Ledger ledger=new Ledger(actual.port);
                if(h==null) sink.onTerminalBranch(s); else sink.onBranchLimitSelected(s,h);
                detachedCopies+=(int)number(o,"headCopies");
                check(get(o,"selected")!=s && reason.equals(get(get(o,"selected"),"terminalReason")),
                    "actual terminal selected only as detached scalar context");
                equalFields(s,get(o,"selected"),"actual unchanged33-field copy");
                if(h==null) check(get(o,"selectedHead")==null,"actual ordinary terminal has no head");
                else {
                    Object d=get(o,"selectedHead");
                    check(number(d,"cost")==11 && number(d,"storedPriority")==24 &&
                        number(d,"branchOrdinal")==h.branchOrdinal && number(d,"key")==h.key,
                        "actual consumer commits matching detached head atomically");
                }
                check(actual.port.units()-ledger.units<=512 && actual.port.copiedEntries()-ledger.copies==(h==null?33:43) &&
                    actual.port.copyChunks()-ledger.chunks==(h==null?1:2),"actual owner callback remains bounded and exact-copy metered");
            } catch(Exception e) { throw new AssertionError("actual injected consumer setup",e); }
            before.same(frame);
        }
    }
    private static ObservationSpec actualSpec(Frame frame,PcbLayerRoutingPrototype.BranchSnapshot s,TerminalObserverMeter meter) {
        PcbPadPlacement start=(PcbPadPlacement)get(frame.branch,"start");
        PcbPadPlacement root=(PcbPadPlacement)get(frame.branch,"root");
        String net=(String)get(frame.branch,"net");
        check(s.netId.equals(net) && s.startPadId.equals(start.getPadId()) &&
            s.rootPadId.equals(root.getPadId()) && s.startX==start.getX() &&
            s.startY==start.getY() && s.rootX==root.getX() && s.rootY==root.getY(),
            "actual expected pad identities/coordinates come from owner branch, independently of DTO");
        Map<String,PadIdentity> p=new LinkedHashMap<String,PadIdentity>();
        p.put(start.getPadId(),new PadIdentity(net,start.getX(),start.getY()));
        p.put(root.getPadId(),new PadIdentity(net,root.getX(),root.getY()));
        Map<String,Integer> o=new LinkedHashMap<String,Integer>(); o.put(net,Integer.valueOf(1));
        Map<String,Integer> c=new LinkedHashMap<String,Integer>(); c.put(net,Integer.valueOf(2));
        check(s.pass==2,"PASS_AND_CAP_FRAME_INJECTED actual Search preserves injected pass two");
        return ObservationSpec.create(meter,REQUEST,2,2,1,p,o,c);
    }
    private static PcbLayerRoutingPrototype.Result route(PcbLayerRoutingPrototype.Diagnostics sink,int maximum) {
        P06FactoryLinkFixtures.Fixture f=fixture(); String before=f.layout.geometryFingerprint();
        PcbLayerRoutingPrototype.Policy p=PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER;
        beginCalls++;
        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(f.board,f.layout,p,
            P06FactoryLinkFixtures.OBSERVER,maximum,p.vias,sink);
        boolean done=false;
        for(int i=0;i<DRAIN_GUARD;i++) if(session.advanceSlice(PcbLayerRoutingPrototype.WORK_QUANTUM)) { done=true; break; }
        check(done,"actual small route completes inside finite slice guard");
        PcbLayerRoutingPrototype.Result r=session.result();
        check(r!=null && r.expansions<=maximum && before.equals(f.layout.geometryFingerprint()) &&
            f.layout.getTraces().isEmpty() && f.layout.getHoles().isEmpty(),"actual route bounded with unchanged caller geometry");
        return r;
    }
    private static void sameResult(PcbLayerRoutingPrototype.Result a,PcbLayerRoutingPrototype.Result b) {
        check(a.accepted()==b.accepted() && a.outcome.equals(b.outcome) && a.expansions==b.expansions &&
            a.orderings==b.orderings && a.vias==b.vias && a.links==b.links,"default/old/selected bounded result parity");
        if(a.accepted()) check(a.layout.geometryFingerprint().equals(b.layout.geometryFingerprint()) &&
            a.layout.traceGeometryFingerprint().equals(b.layout.traceGeometryFingerprint()),"accepted geometry parity");
    }
    private static void equalFields(Object a,Object b,String label) {
        for(Field f:a.getClass().getDeclaredFields()) if(!Modifier.isStatic(f.getModifiers())) {
            Object x=get(a,f.getName()),y=get(b,f.getName());
            check(x==null?y==null:x.equals(y),label+" "+f.getName());
        }
    }
    private interface Action { void run(); }
    private static abstract class ActionObserver implements Action {
        final TerminalTelemetryObserver value; ActionObserver(TerminalTelemetryObserver o) { value=o; }
    }
    private static Throwable failure(Action a) {
        try { a.run(); } catch(RuntimeException t) { return t; } catch(Error t) { return t; }
        throw new AssertionError("expected bounded component rejection");
    }
    private static void raise(Throwable t) {
        if(t instanceof RuntimeException) throw (RuntimeException)t;
        if(t instanceof Error) throw (Error)t;
        throw new AssertionError("missing TEST failure sentinel");
    }
    private static Class<?> ownerType(String nested) { return type(PcbLayerRoutingPrototype.class.getName()+"$"+nested); }
    private static Class<?> studyType(String nested) { return type(CohortTerminalTelemetryStudy.class.getName()+"$"+nested); }
    private static Class<?> type(String name) {
        try { return Class.forName(name); } catch(Exception e) { throw new AssertionError("missing pinned type "+name,e); }
    }
    private static Object construct(Class<?> t,Class<?>[] types,Object... args) {
        try { Constructor<?> c=t.getDeclaredConstructor(types); c.setAccessible(true); return c.newInstance(args); }
        catch(InvocationTargetException e) { raise(e.getCause()); return null; }
        catch(Exception e) { throw new AssertionError("cannot construct TEST fixture "+t.getName(),e); }
    }
    private static Object invoke(Object o,String name,Class<?>[] types,Object... args) {
        try { Method m=o.getClass().getDeclaredMethod(name,types); m.setAccessible(true); return m.invoke(o,args); }
        catch(InvocationTargetException e) { raise(e.getCause()); return null; }
        catch(Exception e) { throw new AssertionError("cannot invoke pinned method "+name,e); }
    }
    private static Field declared(Class<?> t,String name) {
        try { Field f=t.getDeclaredField(name); f.setAccessible(true); return f; }
        catch(Exception e) { throw new AssertionError("missing pinned field "+name,e); }
    }
    private static Object get(Object o,String name) {
        try { return declared(o.getClass(),name).get(o); }
        catch(Exception e) { throw new AssertionError("cannot inspect pinned field "+name,e); }
    }
    private static long number(Object o,String name) { return ((Number)get(o,name)).longValue(); }
    private static boolean bool(Object o,String name) { return ((Boolean)get(o,name)).booleanValue(); }
    private static void set(Object o,String name,Object value) {
        try { declared(o.getClass(),name).set(o,value); }
        catch(Exception e) { throw new AssertionError("cannot inject TEST field "+name,e); }
    }
    private static void check(boolean ok,String message) { assertions++; if(!ok) throw new AssertionError(message); }
}
