package com.lushprojects.circuitjs1.client;

public final class E01SourceContractTest {
    private static int assertions;
    public static void main(String[] args) {
        LowVoltageSourceModel.validate(0,.001); LowVoltageSourceModel.validate(24,.5);
        for (double v : new double[]{-1,24.001,Double.NaN,Double.POSITIVE_INFINITY}) reject(v,.1);
        for (double a : new double[]{0,.0009,.501,Double.NaN,Double.POSITIVE_INFINITY}) reject(5,a);
        near(LowVoltageSourceModel.current(.0025,.1),.05,1e-12);
        near(LowVoltageSourceModel.current(5,.1),.100004995,1e-12);
        near(LowVoltageSourceModel.current(-7,.1),-.000026999999,1e-12);
        // Passive series compliance: monotone, dissipative and continuous at both knees.
        double last = -1;
        for (int n=-24000;n<=24000;n++) {
            double drop=n*.001, current=LowVoltageSourceModel.current(drop,.1);
            check(current>=last && drop*current>=0); last=current;
            check(current<=.100024 && current>=-.000044001);
        }
        near(LowVoltageSourceModel.current(.005-1e-10,.1),LowVoltageSourceModel.current(.005+1e-10,.1),3e-9);
        System.out.println("PASS: E01 source contracts " + assertions + " assertions");
    }
    private static void reject(double v,double a) {
        try { LowVoltageSourceModel.validate(v,a); throw new AssertionError("unsafe envelope accepted"); }
        catch(IllegalArgumentException expected) { assertions++; }
    }
    private static void near(double a,double b,double tolerance) { check(Math.abs(a-b)<=tolerance); }
    private static void check(boolean value) { assertions++; if(!value) throw new AssertionError("E01 contract"); }
}
