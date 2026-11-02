package com.lushprojects.circuitjs1.client;

/** Actual-generator paired null/capture census for authored RB100 variants. */
public final class Rb100FixedPrefixCaptureContractTest {
    private Rb100FixedPrefixCaptureContractTest() { }

    public static void main(String[] args) {
        if(args==null) args=new String[0];
        boolean completedNoPath=false;
        if(args.length==1 && "--completed-no-path".equals(args[0]))
            completedNoPath=true;
        else if(args.length!=0)
            throw new IllegalArgumentException(
                "Rb100FixedPrefixCaptureContractTest accepts only --completed-no-path");
        Rb100PhysicalDiagnostics.runFixedPrefixCaptureContracts(completedNoPath);
    }
}
