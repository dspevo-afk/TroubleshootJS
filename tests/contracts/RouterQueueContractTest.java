package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Random;

/** Exact object/order oracle against the replaced JDK priority queue. */
public final class RouterQueueContractTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        Class<?> type=Class.forName("com.lushprojects.circuitjs1.client.PcbNetRouter$Router$SearchNode");
        Constructor<?> node=type.getDeclaredConstructor(int.class,int.class,int.class,double.class,double.class,int.class);
        node.setAccessible(true);
        final Method compare=type.getDeclaredMethod("compareTo",type);compare.setAccessible(true);
        Class<?> queueType=Class.forName("com.lushprojects.circuitjs1.client.PcbNetRouter$Router$SearchQueue");
        Constructor<?> queueCtor=queueType.getDeclaredConstructor();queueCtor.setAccessible(true);
        Method add=queueType.getDeclaredMethod("add",type),poll=queueType.getDeclaredMethod("poll"),empty=queueType.getDeclaredMethod("isEmpty");
        add.setAccessible(true);poll.setAccessible(true);empty.setAccessible(true);
        for(int cohort=0;cohort<3;cohort++) {
            Object queue=queueCtor.newInstance();
            PriorityQueue<Object> reference=new PriorityQueue<Object>(128,new Comparator<Object>() {
                public int compare(Object a,Object b) {
                    try { return (Integer)compare.invoke(a,b); }
                    catch(Exception e) { throw new AssertionError(e); }
                }
            });
            check(poll.invoke(queue)==null && (Boolean)empty.invoke(queue),"Empty queue mismatch");
            Random random=new Random(741991L+cohort);
            for(int i=0;i<50000;i++) {
                if(reference.isEmpty() || random.nextInt(4)!=0) {
                    // The tied cohort reaches the sequence tie-breaker on every insert.
                    Object value=node.newInstance(cohort==0?0:random.nextInt(200),cohort==0?0:random.nextInt(200),
                        cohort==0?0:random.nextInt(5),cohort==0?10.0:random.nextInt(10000)*2.0,
                        cohort==0?20.0:random.nextInt(1000)*2.0,i);
                    reference.add(value);add.invoke(queue,value);
                } else check(poll.invoke(queue)==reference.poll(),"Priority order changed");
                check((Boolean)empty.invoke(queue)==reference.isEmpty(),"Empty state changed");
            }
            while(!reference.isEmpty())check(poll.invoke(queue)==reference.poll(),"Drain order changed");
            check(poll.invoke(queue)==null && (Boolean)empty.invoke(queue),"Drained queue retained an entry");
        }
        System.out.println("PASS: router queue parity assertions="+assertions+" cohorts=3 operations=150000");
    }
    private static void check(boolean value,String detail) { assertions++;if(!value)throw new AssertionError(detail); }
}
