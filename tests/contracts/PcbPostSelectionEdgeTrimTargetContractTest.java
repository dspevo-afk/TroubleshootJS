package com.lushprojects.circuitjs1.client;

/** Literal drawing-grid arithmetic expectations, independent of routed examples. */
public final class PcbPostSelectionEdgeTrimTargetContractTest {
    private static int assertions;
    public static void main(String[] args) {
        expect(50,30,30); expect(50,35,40); expect(50,34,40); expect(50,44,50);
        expect(65,65,65); expect(65,50,55); expect(50,26,30); expect(50,51,50);
        expect(65,26,35); expect(26,26,26); expect(Integer.MAX_VALUE,26,27);
        expect(50,Integer.MAX_VALUE,50);
        reject(25,26); reject(50,25); reject(-1,26);
        System.out.println("PASS: PCB post-selection edge trim target contracts assertions="+assertions);
    }
    private static void expect(int oldMargin,int required,int expected) {
        int actual=SeededPcbLayoutGenerator.finalEdgeTrimTarget(oldMargin,required);
        if(actual!=expected)throw new AssertionError(oldMargin+"/"+required+" expected="+expected+" actual="+actual);
        assertions++;
    }
    private static void reject(int oldMargin,int required) {
        try {SeededPcbLayoutGenerator.finalEdgeTrimTarget(oldMargin,required);}
        catch(IllegalArgumentException expected){assertions++;return;}
        throw new AssertionError("invalid margin admitted "+oldMargin+"/"+required);
    }
}
