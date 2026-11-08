package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Real private-method guard fixtures; never invokes capture, execute or a Session. */
public final class SharedCaptureBudgetCanary {
    private static final String P="com.lushprojects.circuitjs1.client.FailureCohortRoutingStudy";
    private static final String A="com.lushprojects.circuitjs1.client.ActualGeneratorBranchOrderStudy";
    private static Class<?> meterType,studyType,armType,budgetType;
    private static Constructor<?> meterConstructor,studyConstructor,armConstructor,defaultConstructor;
    private static Method beginOwner,adoptOwner,beginGroup,finishGroup,charge,admit,newGroup,deadline,clear;
    private static Object mode;
    private static Path data;
    private static int assertions,frames,wrapperGroups,ownerAdmissions;
    private static final int LIMIT=640;
    private static final long OP=90000000000L;
    private SharedCaptureBudgetCanary() { }
    interface Action { void run() throws Exception; }

    private static final class Frame {
        final Object meter,arm;
        Frame(int wrappers) throws Exception {
            meter=meterConstructor.newInstance(Long.valueOf(System.nanoTime()));
            for(int i=0;i<wrappers;i++) wrapper(meter);
            arm=makeArm(meter);frames++;
        }
        void start() throws Exception { call(beginOwner,meter); }
        Object group() throws Exception {
            Object result=call(newGroup,arm,"GUARD_FIXTURE",Long.valueOf(System.nanoTime()));
            ownerAdmissions++;
            check(number(result,"ordinal")==number(arm,"nextGroupOrdinal")-1,"admitted ordinal mismatch");
            return result;
        }
        void adopt(int count) throws Exception { call(adoptOwner,meter,Integer.valueOf(count)); }
        void release() throws Exception {
            call(clear,arm);call(clear,arm);
            check(field(arm,"sharedOwnerBudget")==null,"guard reference not actually cleared");
            noGeneration(arm);
        }
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private static void initialize() throws Exception {
        Class<?> parent=Class.forName(P);
        meterType=Class.forName(P+"$Meter");studyType=Class.forName(A+"$Study");
        armType=Class.forName(A+"$ArmRun");budgetType=Class.forName(A+"$SharedOwnerBudget");
        Class<?> modeType=Class.forName(A+"$Mode");mode=Enum.valueOf((Class)modeType,"AUTHORED");
        meterConstructor=meterType.getDeclaredConstructor(long.class);meterConstructor.setAccessible(true);
        studyConstructor=studyType.getDeclaredConstructor(modeType,String.class,String.class,String.class,Path.class);
        studyConstructor.setAccessible(true);
        Class<?> graph=Class.forName("com.lushprojects.circuitjs1.client.PcbLayerRoutingPrototype$FixedPrefixGraphObserver");
        Class<?> terminal=Class.forName(A+"$TerminalPrefixInspector");
        armConstructor=armType.getDeclaredConstructor(studyType,int.class,graph,terminal,budgetType);
        armConstructor.setAccessible(true);
        defaultConstructor=armType.getDeclaredConstructor(studyType,int.class,graph,terminal);
        defaultConstructor.setAccessible(true);
        beginOwner=method(meterType,"beginOriginalOwner");adoptOwner=method(meterType,"adoptOriginalOwner",int.class);
        beginGroup=method(meterType,"beginGroup");finishGroup=method(meterType,"finishGroup");
        charge=method(meterType,"charge",long.class);admit=method(meterType,"admitOwnerGroup",int.class);
        newGroup=method(armType,"newGroup",String.class,long.class);
        deadline=method(armType,"checkOperationDeadline");clear=method(armType,"clearSharedOwnerBudget");
        check(staticNumber(parent,"MAX_GROUPS")==LIMIT && staticNumber(parent,"OP_NANOS")==OP,
            "native global limits changed");
        check(staticNumber(parent,"STEP_LIMIT")==32768 && staticNumber(parent,"GROUP_NANOS")==5000000000L,
            "native per-group limits changed");
        Class<?> owner=Class.forName(A);
        check(staticNumber(owner,"GROUP_LIMIT")==LIMIT && staticNumber(owner,"OPERATION_LIMIT_NANOS")==OP,
            "owner limits changed");
    }
    private static Object makeStudy() throws Exception {
        return studyConstructor.newInstance(mode,"00000000-0000-0000-0000-000000000001",
            "0000000000000000000000000000000000000000000000000000000000000000",
            "0000000000000000000000000000000000000000000000000000000000000000",data);
    }
    private static Object makeArm(Object guard) throws Exception {
        return armConstructor.newInstance(makeStudy(),Integer.valueOf(1),null,null,guard);
    }
    private static void wrapper(Object meter) throws Exception {
        call(beginGroup,meter);call(charge,meter,Long.valueOf(1));call(finishGroup,meter);wrapperGroups++;
    }
    private static void noGeneration(Object arm) throws Exception {
        for(String name:new String[]{"sink","nativeContext","electricalCandidate","board","generator","ownerSession"})
            check(field(arm,name)==null,"guard fixture unexpectedly created "+name);
    }
    private static void emptyData() throws Exception {
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(data)) {
            check(!entries.iterator().hasNext(),"guard-only fixture unexpectedly wrote data");
        }
    }

    public static void main(String[] args) throws Exception {
        check(args.length==1,"one existing empty owned data directory is required");
        data=Paths.get(args[0]);
        check(data.isAbsolute() && data.normalize().equals(data) && Files.isDirectory(data) &&
            data.toRealPath().equals(data),"invalid fixture directory");
        emptyData();initialize();

        Object legacy=defaultConstructor.newInstance(makeStudy(),Integer.valueOf(1),null,null);
        check(field(legacy,"sharedOwnerBudget")==null,"default constructor acquired a guard");
        call(newGroup,legacy,"GUARD_FIXTURE",Long.valueOf(System.nanoTime()));
        check(number(legacy,"nextGroupOrdinal")==1,"default ordinal behavior changed");
        call(clear,legacy);noGeneration(legacy);

        final Frame zero=new Frame(640);zero.start();
        expect(IllegalStateException.class,new Action(){public void run() throws Exception {zero.group();}});
        check(number(zero.arm,"nextGroupOrdinal")==0 && number(zero.meter,"inFlightOwnerGroups")==0,
            "zero capacity rejection changed counters");
        zero.adopt(0);zero.release();

        final Frame exact=new Frame(638);exact.start();exact.group();exact.group();
        check(number(exact.meter,"groups")==638 && number(exact.meter,"priorGroups")==0 &&
            number(exact.meter,"inFlightOwnerGroups")==2,"live owner count was dropped or prematurely adopted");
        expect(IllegalStateException.class,new Action(){public void run() throws Exception {exact.group();}});
        check(number(exact.arm,"nextGroupOrdinal")==2,"one-over ordinal was allocated");
        expect(IOException.class,new Action(){public void run() throws Exception {call(beginGroup,exact.meter);}});
        exact.adopt(2);
        check(number(exact.meter,"priorGroups")==2 && number(exact.meter,"inFlightOwnerGroups")==0 &&
            !bool(exact.meter,"originalOwnerActive") && bool(exact.meter,"originalOwnerAdopted"),
            "actual owner ordinal was not adopted once");
        expect(IllegalStateException.class,new Action(){public void run() throws Exception {exact.adopt(2);}});
        expect(IllegalStateException.class,new Action(){public void run() throws Exception {exact.group();}});
        expect(IOException.class,new Action(){public void run() throws Exception {call(beginGroup,exact.meter);}});
        check(number(exact.meter,"groups")==638 && number(exact.meter,"priorGroups")==2,
            "rejected follow-up work doubled or lost groups");
        exact.release();

        final Frame spare=new Frame(637);spare.start();spare.group();spare.group();spare.adopt(2);
        wrapper(spare.meter);
        check(number(spare.meter,"groups")==638 && number(spare.meter,"priorGroups")==2,"remaining wrapper credit");
        expect(IOException.class,new Action(){public void run() throws Exception {wrapper(spare.meter);}});
        spare.release();

        final Frame mismatch=new Frame(0);mismatch.start();
        expect(IllegalStateException.class,new Action(){public void run() throws Exception {mismatch.start();}});
        expect(IllegalStateException.class,new Action(){public void run() throws Exception {call(admit,mismatch.meter,Integer.valueOf(-1));}});
        expect(IllegalStateException.class,new Action(){public void run() throws Exception {call(admit,mismatch.meter,Integer.valueOf(1));}});
        mismatch.group();
        expect(IllegalStateException.class,new Action(){public void run() throws Exception {mismatch.adopt(2);}});
        check(number(mismatch.meter,"priorGroups")==0 && number(mismatch.meter,"inFlightOwnerGroups")==1 &&
            bool(mismatch.meter,"originalOwnerActive"),"failed adoption mutated live counts");
        mismatch.adopt(1);
        expect(IllegalStateException.class,new Action(){public void run() throws Exception {mismatch.start();}});
        mismatch.release();

        final Frame open=new Frame(0);call(beginGroup,open.meter);call(charge,open.meter,Long.valueOf(1));
        expect(IllegalStateException.class,new Action(){public void run() throws Exception {open.start();}});
        check(!bool(open.meter,"originalOwnerStarted"),"open-wrapper start changed lifecycle");
        call(finishGroup,open.meter);wrapperGroups++;open.start();open.adopt(0);open.release();

        final Frame mixed=new Frame(638);mixed.start();mixed.group();
        call(beginGroup,mixed.meter);call(charge,mixed.meter,Long.valueOf(1));
        expect(IllegalStateException.class,new Action(){public void run() throws Exception {mixed.group();}});
        check(bool(mixed.meter,"open") && number(mixed.arm,"nextGroupOrdinal")==1,
            "open wrapper credit was omitted or excess ordinal changed");
        call(finishGroup,mixed.meter);wrapperGroups++;mixed.adopt(1);mixed.release();

        Object valid=meterConstructor.newInstance(Long.valueOf(System.nanoTime()-1000000000L));
        call(beginOwner,valid);Object young=makeArm(valid);call(deadline,young);call(adoptOwner,valid,Integer.valueOf(0));
        call(clear,young);noGeneration(young);
        final Object expired=meterConstructor.newInstance(Long.valueOf(System.nanoTime()-OP-1000000000L));
        final Object youngExpiredArm=makeArm(expired);
        check(System.nanoTime()-number(youngExpiredArm,"operationStarted")<OP/2,"owner clock was not young");
        Throwable expiry=failure(new Action(){public void run() throws Exception {call(deadline,youngExpiredArm);}});
        check(expiry instanceof IllegalStateException && "failed-first process exceeded 90 seconds".equals(expiry.getMessage()),
            "young owner ignored the already-expired producer deadline");
        check(number(expired,"inFlightOwnerGroups")==0 && number(youngExpiredArm,"nextGroupOrdinal")==0,
            "deadline failure mutated group counts");
        call(clear,youngExpiredArm);check(field(youngExpiredArm,"sharedOwnerBudget")==null,"expired guard retained");
        noGeneration(youngExpiredArm);

        sentinel(new IllegalStateException("SENTINEL_RUNTIME"));sentinel(new AssertionError("SENTINEL_ERROR"));
        emptyData();
        System.out.println("SHARED_CAPTURE_BUDGET_CANARY_PASS assertions="+assertions+" frames="+frames+
            " wrapperGroups="+wrapperGroups+" ownerAdmissions="+ownerAdmissions+
            " actualCaptureCalls=0 authoredSessions=0 automaticExecuteCleanup=SOURCE_ONLY");
    }

    private static void sentinel(final Throwable sentinel) throws Exception {
        Object guard=Proxy.newProxyInstance(budgetType.getClassLoader(),new Class<?>[]{budgetType},
            new InvocationHandler(){public Object invoke(Object proxy,Method method,Object[] args) throws Throwable {
                if("checkOuterDeadline".equals(method.getName()))throw sentinel;
                return null;
            }});
        final Object arm=makeArm(guard);
        check(failure(new Action(){public void run() throws Exception {call(deadline,arm);}})==sentinel,
            "shared callback changed Runtime/Error identity");
        check(number(arm,"nextGroupOrdinal")==0,"sentinel allocated a group");
        call(clear,arm);check(field(arm,"sharedOwnerBudget")==null,"sentinel guard retained");noGeneration(arm);
    }
    private static Method method(Class<?> type,String name,Class<?>... types) throws Exception {
        Method result=type.getDeclaredMethod(name,types);result.setAccessible(true);return result;
    }
    private static Object call(Method method,Object receiver,Object... args) throws Exception {
        try{return method.invoke(receiver,args);}
        catch(InvocationTargetException wrapped) {
            Throwable cause=wrapped.getCause();if(cause instanceof Exception)throw (Exception)cause;
            if(cause instanceof Error)throw (Error)cause;throw new IllegalStateException(cause);
        }
    }
    private static Throwable failure(Action action) throws Exception {
        try{action.run();return null;}catch(Throwable error){return error;}
    }
    private static void expect(Class<?> type,Action action) throws Exception {
        Throwable result=failure(action);check(result!=null && type.isInstance(result),"expected "+type.getName()+", got "+result);
    }
    private static Object field(Object value,String name) throws Exception {
        Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);
    }
    private static long number(Object value,String name) throws Exception {return ((Number)field(value,name)).longValue();}
    private static boolean bool(Object value,String name) throws Exception {return ((Boolean)field(value,name)).booleanValue();}
    private static long staticNumber(Class<?> type,String name) throws Exception {
        Field f=type.getDeclaredField(name);f.setAccessible(true);return ((Number)f.get(null)).longValue();
    }
    private static void check(boolean value,String message) {assertions++;if(!value)throw new AssertionError(message);}
}
