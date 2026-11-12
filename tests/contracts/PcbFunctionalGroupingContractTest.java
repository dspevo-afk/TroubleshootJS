package com.lushprojects.circuitjs1.client;

import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

/** Six predeclared roots; actual routed locality without a packing optimum claim. */
public final class PcbFunctionalGroupingContractTest {
    private static final long[] ROOTS={-1017L,-1004L,-1022L,0L,17L,42L};
    private static final int MEASURED_VERSION=12;
    private static final int[] PART_COUNTS={40,44,60,42,53,54};
    private static final int[] PAD_COUNTS={96,104,143,100,129,131};
    // Area/void arrays are the earlier accepted receipts and stay as regression bounds.
    // Exact geometry literals were re-pinned on 2026-11-12 to generator layout 26 after the
    // intentional dependency-v26 placement/routing correction (34ff05d0); every saved
    // representative and holdout area stayed equal or shrank under that correction.
    private static final long[] SAVED6_AREA={5122184,4622400,8443994,4679776,7708262,6371585};
    private static final long[] SAVED7_AREA={5122184,4622400,7395541,4679776,7708262,6371585};
    private static final long[] SAVED8_AREA={4529785,4466664,6943752,4679776,5814368,6211431};
    private static final long[] SAVED11_AREA={4529785,4466664,6943752,4358809,5814368,6036952};
    // Accepted private12 receipts are an independent upper bound for private13.
    private static final long[] SAVED12_AREA={4529785,4466664,6943752,4358809,5814368,6036952};
    private static final long[] SAVED12_LARGEST_VOID={555900,193200,729600,179200,416000,225500};
    private static final long[] CURRENT12_AREA={4529785,4466664,6310794,4358809,5814368,5924664};
    private static final String[] SAVED12_GEOMETRY_SHA256={
        "9483cf817b65feb34fe9098a5029c68803cac6ee35e1e8fb765f2ef7c3c4adf6",
        "d26e38ec83afe724f08a1a9de01481faf30fb3ecf5639a0ad1f0fcdb02311bf2",
        "ab3df68605819080a11503bb6fe4662345bfcf0c6cc8f31009780229e31152ad",
        "25066a2cd47572edc679087bf3585723b163ec9e8cca83d261996715669b4779",
        "80452a7089dc9a200a9c6d4737389b8afcb354cf1b278285bbac87809a447f18",
        "af1e4a8ba011ffaa5b6a269f0e5dfc19ab7bc0feeb07392aa766c50c7566c213"
    };
    private static final long[] ROLE_WIDTH_HOLDOUT_SEEDS={1L,7L,5L,14L};
    private static final long[] SAVED12_HOLDOUT_AREA={4615974,6397152,9445744,7347009};
    private static final long[] SAVED12_HOLDOUT_LARGEST_VOID={474000,965600,1367100,894600};
    private static final long[] CURRENT12_HOLDOUT_AREA={4615974,5713720,6107220,6102705};
    private static final long[] CURRENT12_HOLDOUT_LARGEST_VOID={474000,408100,293400,401800};
    private static final String[] SAVED12_HOLDOUT_GEOMETRY_SHA256={
        "146ad5ebfcc11bc77bdcc8ff1acda17bb15926ae6c1838e714046899afb12e14",
        "f58e95a2626247b03b05e17f74a5b57b33c3a4a287dbc2f867cf179cf0e3c3e3",
        "dba3253eed877d49e4d490d9ecc3468735b4da2f71ba209db883043ef90a9a92",
        "7cbeab8990576876a3eca83fe4a4c7b7220f53684a70df62351b33f667ea7a0c"
    };
    private static final String[] SAVED8_GEOMETRY_SHA256={
        "c42e71efc9f75f567c31aca1c6e4e5ce18af847e0c531fa6df3efb52bb2001fa",
        "7e75e4689fdb167de969c24b6d6249a9f715139df8f3fc9807955d201e9e78cb",
        "bf18244e5819eb19e6d1edb030d4e0d279fa752be318216081599203315c4413",
        "d0a2a978dfb55b4c2f06d638298efa130328aa7ad936d57b12785d350181477f",
        "fe35af4d26052967f32a3582ae4c4e52ef6d459b3632ea4f02c7c5ed390ee427",
        "f4bacc137506c1864294167fe56bedde03e5ec0fc9214902e884ea380aa38ffe"
    };
    private static final String[] SAVED7_GEOMETRY_SHA256={
        "95f5571afda376ebc7380d6a514570f200c549a5f383cde6a6a92fafd6597dfd",
        "627d2e6b0b7bd36a420d46df799d0c5d64de0b325d410f63ab205187e445eaf7",
        "891a0ce38c2d4519074e32fc1dc7fc27a74feae66885d2b1ac9775f9e4e19daf",
        "d0a2a978dfb55b4c2f06d638298efa130328aa7ad936d57b12785d350181477f",
        "40cd0788880b740a1390665ca54d9dad031501f69d4ec8acdba354bcd02c1b47",
        "ab29410c572f9681a721ee1832ed577a297d65ca91213532d9283c7e7b83e06c"
    };
    private static final String[] SAVED6_GEOMETRY_SHA256={
        "95f5571afda376ebc7380d6a514570f200c549a5f383cde6a6a92fafd6597dfd",
        "627d2e6b0b7bd36a420d46df799d0c5d64de0b325d410f63ab205187e445eaf7",
        "eed3faa7beb6fed8eafda7a397302a20ae2283b11985d505ab692d2cdc0a65d3",
        "d0a2a978dfb55b4c2f06d638298efa130328aa7ad936d57b12785d350181477f",
        "7583c5466034748abcef19d244a1c620309cbdab71e4d355f20c1b0fd8a549ec",
        "ab29410c572f9681a721ee1832ed577a297d65ca91213532d9283c7e7b83e06c"
    };
    private static final String[] SAVED3_GEOMETRY_SHA256={
        "7465434f9d1ead61e74beb68e02770c651f6a68260c69303f56561ea3244bd41",
        "423f74fac65524f1758fe085e2ec652c3d046aa4335e384b14a5df2b5b9719a5",
        "a713acba0dac5dbafd6835830fb8a3685561a591fdf35686495b81d263c0d502",
        "35b0e7e1d16d8476a440a9e7103f71a7b9e61119bf5be9eadf25059fda75da4c",
        "8e2ad5628f24f82577aea80b48b57a436dc5c665b5f161a5045d45de8c486b8d",
        "f68d89e0bc918794eafd0cf0d23462ea9b36558537f1286cf99a82e687162bf6"
    };
    private static final String[] SAVED4_GEOMETRY_SHA256={
        "3c3d878386669a701467e6c833036e698cc030c5b90fc04357debb01c2392e2d",
        "73530143c5399c687514024541ca9e0f7aafb5286e07573e284f3e5b65eaafe2",
        "e19cd411ea12deb14d63fec844213b124ed830edf8f6ea8a9612f36b39be82ca",
        "b140a3b9b7cc7840bfbecc3b3b373ae0b92417683888604a0a73e9bafdb0603d",
        "fdd27272fd3a947b85e008b12893ce75d805dbd5c80995564238d081c6cf2551",
        "af202a064ea9d90f36724dd1fd38cfe1b6312a221162e9c8c2f8d6d65f810291"
    };
    private static final String[] SAVED5_GEOMETRY_SHA256={
        "3c3d878386669a701467e6c833036e698cc030c5b90fc04357debb01c2392e2d",
        "73530143c5399c687514024541ca9e0f7aafb5286e07573e284f3e5b65eaafe2",
        "eed3faa7beb6fed8eafda7a397302a20ae2283b11985d505ab692d2cdc0a65d3",
        "b140a3b9b7cc7840bfbecc3b3b373ae0b92417683888604a0a73e9bafdb0603d",
        "7583c5466034748abcef19d244a1c620309cbdab71e4d355f20c1b0fd8a549ec",
        "ab29410c572f9681a721ee1832ed577a297d65ca91213532d9283c7e7b83e06c"
    };
    private static int checks;
    public static void main(String[] args) {
        verifySavedUniformProfiles();
        int failedCases=0;
        for(int index=0;index<ROOTS.length;index++) {
            try { measure(ROOTS[index],index,MEASURED_VERSION); }
            catch(AssertionError failure) {
                failedCases++;
                System.out.println("PCB_GROUPING_CASE_FAILURE seed="+ROOTS[index]+" version="+
                    MEASURED_VERSION+" message="+failure.getMessage());
                failure.printStackTrace(System.out);
            }
        }
        if(failedCases>0)throw new AssertionError("representative acceptance failures="+failedCases);
        System.out.println("PASS: PCB functional grouping contracts roots="+ROOTS.length+" assertions="+checks);
    }
    /** Separate existing 60-second JVM bound for the six compatibility cases. */
    static void verifySavedRealismGeometry() {
        for(int index=0;index<ROOTS.length;index++) measure(ROOTS[index],index,3);
        System.out.println("PASS: saved placement3 geometry contracts roots="+ROOTS.length+" assertions="+checks);
    }
    static void verifySavedDensityGeometry() {
        for(int index=0;index<ROOTS.length;index++) measure(ROOTS[index],index,4);
        System.out.println("PASS: saved placement4 geometry contracts roots="+ROOTS.length+" assertions="+checks);
    }
    static void verifyFocusedSingleGroup() {
        for(int index:new int[]{1,3}) measure(ROOTS[index],index,MEASURED_VERSION);
        System.out.println("PASS: focused single-group density contracts roots=2 assertions="+checks);
    }
    static void verifySavedLargeGeometry() {
        for(int index=0;index<ROOTS.length;index++) measure(ROOTS[index],index,5);
        System.out.println("PASS: saved placement5 geometry contracts roots=6 assertions="+checks);
    }
    static void verifySavedSingleGroupGeometry() {
        for(int index=0;index<ROOTS.length;index++) measure(ROOTS[index],index,6);
        System.out.println("PASS: saved placement6 geometry contracts roots=6 assertions="+checks);
    }
    static void verifySavedGuardedGeometry() {
        for(int index=0;index<ROOTS.length;index++) measure(ROOTS[index],index,7);
        System.out.println("PASS: saved placement7 geometry contracts roots=6 assertions="+checks);
    }
    static void verifySavedNarrowGeometry() {
        for(int index=0;index<ROOTS.length;index++) measure(ROOTS[index],index,8);
        System.out.println("PASS: saved placement8 geometry contracts roots=6 assertions="+checks);
    }
    static void verifySavedConnectedGeometry() {
        for(int index=0;index<ROOTS.length;index++) measure(ROOTS[index],index,9);
        System.out.println("PASS: saved placement9 geometry contracts roots=6 assertions="+checks);
    }
    static int verifyRoleWidthHoldouts() {
        int firstCheck=checks;
        long[] seeds={1L,7L,5L,14L};
        long[] accepted11Areas={4615974,6587808,9445744,8585034};
        for(int index=0;index<seeds.length;index++) {
            long seed=seeds[index];
            Rb56Plan plan=Rb56Plan.resolve(seed,12);
            TroubleshootBoard board=plan.board();
            MediumBoardPhysicalPolicy.Result result=new SeededPcbLayoutGenerator()
                .generateWithPolicyResult(board,plan.layoutSeed,plan.routingSeed);
            check(result.accepted(),"private12 role-width holdout route accepted: "+seed+
                " "+result.toCanonical());
            PcbBoardLayout layout=result.getLayout();
            GeneratedPhysicalAdmission admission=MediumBoardNormalAdmission.fromAcceptedRb56Route(
                board,result,plan.layoutSeed,plan.routingSeed);
            admission.requireConstruction(board,layout);
            layout.validateGeometry(board);layout.validateRoutingGeometry(board);layout.validateRouteQuality();
            new PcbTwoLayerRules(board,layout).validate(layout);
            PcbTraceClearance.validate(layout.getTraces());
            PcbPlacementConstraints constraints=board.getPlacementConstraints();
            check(constraints.requiresTwoLayerRouting,"holdout retains P07 two-layer admission: "+seed);
            check(constraints.placementDensityVersion==11,"role-width placement retains density11: "+seed);
            check(layout.getComponents().size()==board.getComponentIds().size(),
                "holdout retains every source component: "+seed);
            TreeSet<String> componentIds=new TreeSet<String>();
            for(PcbComponentPlacement part:layout.getComponents())
                check(componentIds.add(part.getComponentId()),"holdout component identity is unique: "+seed);
            check(componentIds.equals(new TreeSet<String>(board.getComponentIds())),
                "holdout component inventory is exact: "+seed);
            check(layout.getPads().size()==board.getPadIds().size(),
                "holdout retains every source pad: "+seed);
            TreeSet<String> padIds=new TreeSet<String>();
            for(PcbPadPlacement pad:layout.getPads())
                check(padIds.add(pad.getPadId()),"holdout physical pad identity is unique: "+seed);
            check(padIds.equals(new TreeSet<String>(board.getPadIds())),
                "holdout physical pad inventory is exact: "+seed);
            verifyRoleTraceWidths(board,layout);
            Rectangle outline=layout.getBoardOutline();
            long area=(long)outline.width*outline.height;
            check(area*100L<=accepted11Areas[index]*101L,
                "private12 holdout outline is within 101% of accepted11: "+seed+
                " area="+area+" baseline="+accepted11Areas[index]);
            MediumBoardPhysicalPolicy.Statistics stats=result.getStatistics();
            check(stats.placementCandidates==6&&stats.oneFaceAttempts<=3&&stats.twoLayerAttempts<=3,
                "holdout preserves the existing placement and routing caps: "+seed);
            long copperLength=0;
            int segments=0,vias=0;
            for(PcbTraceGeometry trace:layout.getTraces()) {
                int[] x=trace.getXPoints(),y=trace.getYPoints();
                for(int i=1;i<x.length;i++) {
                    copperLength+=Math.abs((long)x[i]-x[i-1])+Math.abs((long)y[i]-y[i-1]);
                    segments++;
                }
            }
            for(PcbBoardHole hole:layout.getHoles()) if(hole.kind==PcbBoardHole.Kind.VIA)vias++;
            System.out.println("PCB_ROLE_WIDTH_HOLDOUT_ROUTE seed="+seed+" "+result.toCanonical());
            reportWhitespace(board,layout,seed,12);
            System.out.println("PCB_ROLE_WIDTH_HOLDOUT {\"seed\":\""+seed+"\",\"area\":"+area+
                ",\"accepted11Area\":"+accepted11Areas[index]+",\"copperLength\":"+copperLength+
                ",\"segments\":"+segments+",\"vias\":"+vias+",\"geometrySha256\":\""+
                PlayerSessionFingerprint.of(layout.geometryFingerprint())+"\"}");
        }
        return checks-firstCheck;
    }

    static int verifyNearestVacancyRepresentatives() {
        int firstCheck=checks,failures=0;
        for(int index=0;index<ROOTS.length;index++) {
            try { measure(ROOTS[index],index,13); }
            catch(AssertionError failure) {
                failures++;
                System.out.println("PCB_NEAREST_VACANCY_FAILURE seed="+ROOTS[index]+
                    " message="+failure.getMessage());
            }
            catch(RuntimeException failure) {
                failures++;
                System.out.println("PCB_NEAREST_VACANCY_FAILURE seed="+ROOTS[index]+
                    " exception="+failure);
            }
        }
        if(failures>0)throw new AssertionError("private13 nearest-vacancy representative failures="+failures);
        return checks-firstCheck;
    }

    static int verifyNearestVacancyHoldouts() {
        return verifyHoldoutCohort(13,false);
    }

    static int verifySavedRoleWidthRepresentatives() {
        int firstCheck=checks,failures=0;
        for(int index=0;index<ROOTS.length;index++) {
            try {
                PcbBoardLayout layout=measure(ROOTS[index],index,12);
                long area=(long)layout.getBoardOutline().width*layout.getBoardOutline().height;
                String hash=PlayerSessionFingerprint.of(layout.geometryFingerprint());
                check(area==CURRENT12_AREA[index],"published12 representative area remains exact: "+ROOTS[index]);
                check(SAVED12_GEOMETRY_SHA256[index].equals(
                    PcbCompactnessContractTest.physicalGeometrySha256(layout)),
                    "published12 representative physical geometry remains exact apart from reference x/y and epoch: "+ROOTS[index]);
            }
            catch(AssertionError failure) {
                failures++;
                System.out.println("PCB_SAVED_ROLE_WIDTH_FAILURE seed="+ROOTS[index]+
                    " message="+failure.getMessage());
            }
            catch(RuntimeException failure) {
                failures++;
                System.out.println("PCB_SAVED_ROLE_WIDTH_FAILURE seed="+ROOTS[index]+
                    " exception="+failure);
            }
        }
        if(failures>0)throw new AssertionError("saved12 representative failures="+failures);
        return checks-firstCheck;
    }

    static int verifySavedRoleWidthHoldouts() {
        return verifyHoldoutCohort(12,true);
    }

    private static int verifyHoldoutCohort(int version,boolean exactSaved12) {
        int firstCheck=checks,failures=0;
        for(int index=0;index<ROLE_WIDTH_HOLDOUT_SEEDS.length;index++) {
            long seed=ROLE_WIDTH_HOLDOUT_SEEDS[index];
            try { measureHoldout(seed,index,version,exactSaved12); }
            catch(AssertionError failure) {
                failures++;
                System.out.println("PCB_"+(version==13?"NEAREST_VACANCY":"SAVED_ROLE_WIDTH")+
                    "_FAILURE seed="+seed+" message="+failure.getMessage());
            }
            catch(RuntimeException failure) {
                failures++;
                System.out.println("PCB_"+(version==13?"NEAREST_VACANCY":"SAVED_ROLE_WIDTH")+
                    "_FAILURE seed="+seed+" exception="+failure);
            }
        }
        if(failures>0)throw new AssertionError((version==13?"private13 nearest vacancy":"saved12")+
            " holdout failures="+failures);
        return checks-firstCheck;
    }

    private static void measureHoldout(long seed,int index,int version,boolean exactSaved12) {
        Rb56Plan plan=Rb56Plan.resolve(seed,version);
        TroubleshootBoard board=plan.board();
        MediumBoardPhysicalPolicy.Result result=new SeededPcbLayoutGenerator()
            .generateWithPolicyResult(board,plan.layoutSeed,plan.routingSeed);
        check(result.accepted(),"holdout route accepted: "+seed+"/"+version+" "+result.toCanonical());
        PcbBoardLayout layout=result.getLayout();
        GeneratedPhysicalAdmission admission=MediumBoardNormalAdmission.fromAcceptedRb56Route(
            board,result,plan.layoutSeed,plan.routingSeed);
        admission.requireConstruction(board,layout);
        layout.validateGeometry(board);layout.validateRoutingGeometry(board);layout.validateRouteQuality();
        new PcbTwoLayerRules(board,layout).validate(layout);
        PcbTraceClearance.validate(layout.getTraces());
        PcbPlacementConstraints constraints=board.getPlacementConstraints();
        check(constraints.requiresTwoLayerRouting,"holdout retains P07 two-layer admission: "+seed);
        check(constraints.placementDensityVersion==(version==13?12:11),
            "holdout uses the interpretation's literal packing density: "+seed+"/"+version);
        check(layout.getComponents().size()==board.getComponentIds().size(),
            "holdout retains every source component: "+seed);
        TreeSet<String> componentIds=new TreeSet<String>();
        for(PcbComponentPlacement part:layout.getComponents())
            check(componentIds.add(part.getComponentId()),"holdout component identity is unique: "+seed);
        check(componentIds.equals(new TreeSet<String>(board.getComponentIds())),
            "holdout component inventory is exact: "+seed);
        check(layout.getPads().size()==board.getPadIds().size(),
            "holdout retains every source pad: "+seed);
        TreeSet<String> padIds=new TreeSet<String>();
        for(PcbPadPlacement pad:layout.getPads())
            check(padIds.add(pad.getPadId()),"holdout physical pad identity is unique: "+seed);
        check(padIds.equals(new TreeSet<String>(board.getPadIds())),
            "holdout physical pad inventory is exact: "+seed);
        verifyRoleTraceWidths(board,layout);
        for(PcbTraceGeometry trace:layout.getTraces()) {
            if(trace.getStartPadId()!=null)
                check(board.getPad(trace.getStartPadId()).getNetId().equals(trace.getNetId()),
                    "holdout start endpoint net binding: "+seed+"/"+trace.getNetId());
            if(trace.getEndPadId()!=null)
                check(board.getPad(trace.getEndPadId()).getNetId().equals(trace.getNetId()),
                    "holdout end endpoint net binding: "+seed+"/"+trace.getNetId());
        }
        Rectangle outline=layout.getBoardOutline();
        long area=(long)outline.width*outline.height;
        int largestVoid=reportWhitespace(board,layout,seed,version);
        MediumBoardPhysicalPolicy.Statistics stats=result.getStatistics();
        check(stats.placementCandidates==6&&stats.oneFaceAttempts<=3&&stats.twoLayerAttempts<=3,
            "holdout preserves original placement and per-policy route caps: "+seed);
        long copperLength=0;int segments=0,vias=0;
        for(PcbTraceGeometry trace:layout.getTraces()) {
            int[] x=trace.getXPoints(),y=trace.getYPoints();
            for(int i=1;i<x.length;i++) {
                copperLength+=Math.abs((long)x[i]-x[i-1])+Math.abs((long)y[i]-y[i-1]);
                segments++;
            }
        }
        for(PcbBoardHole hole:layout.getHoles())if(hole.kind==PcbBoardHole.Kind.VIA)vias++;
        String hash=PlayerSessionFingerprint.of(layout.geometryFingerprint());
        if(version==13) {
            System.out.println("PCB_NEAREST_VACANCY_ROUTE seed="+seed+" "+result.toCanonical());
            System.out.println("PCB_NEAREST_VACANCY_CASE {\"seed\":\""+seed+
                "\",\"version\":13,\"density\":"+constraints.placementDensityVersion+
                ",\"area\":"+area+",\"areaBaseline\":"+SAVED12_HOLDOUT_AREA[index]+
                ",\"largestProtectedEmptyRectangleArea\":"+largestVoid+
                ",\"largestProtectedEmptyRectangleBaseline\":"+
                SAVED12_HOLDOUT_LARGEST_VOID[index]+",\"copperLength\":"+copperLength+
                ",\"segments\":"+segments+",\"vias\":"+vias+",\"geometrySha256\":\""+
                hash+"\"}");
            check(area<=SAVED12_HOLDOUT_AREA[index],
                "private13 holdout outline does not exceed published12: "+seed+" area="+area+
                " baseline="+SAVED12_HOLDOUT_AREA[index]);
            check(largestVoid<=SAVED12_HOLDOUT_LARGEST_VOID[index],
                "private13 holdout protected void does not exceed published12: "+seed+
                " void="+largestVoid+" baseline="+SAVED12_HOLDOUT_LARGEST_VOID[index]);
        } else if(exactSaved12) {
            System.out.println("PCB_SAVED_ROLE_WIDTH_ROUTE seed="+seed+" "+result.toCanonical());
            check(area==CURRENT12_HOLDOUT_AREA[index],"published12 holdout area remains exact: "+seed);
            check(largestVoid==CURRENT12_HOLDOUT_LARGEST_VOID[index],
                "published12 holdout protected void remains exact: "+seed);
            check(SAVED12_HOLDOUT_GEOMETRY_SHA256[index].equals(
                PcbCompactnessContractTest.physicalGeometrySha256(layout)),
                "published12 holdout physical geometry remains exact apart from reference x/y and epoch: "+seed);
            System.out.println("PCB_SAVED_ROLE_WIDTH_CASE {\"seed\":\""+seed+
                "\",\"version\":12,\"density\":"+constraints.placementDensityVersion+
                ",\"area\":"+area+",\"largestProtectedEmptyRectangleArea\":"+largestVoid+
                ",\"copperLength\":"+copperLength+",\"segments\":"+segments+
                ",\"vias\":"+vias+",\"geometrySha256\":\""+hash+"\"}");
        }
    }

    private static void verifySavedUniformProfiles() {
        for(int version=1;version<=11;version++) {
            PcbPlacementConstraints constraints=Rb56Plan.resolve(ROOTS[0],version).board()
                .getPlacementConstraints();
            PcbTraceWidthProfile profile=constraints.getTraceWidthProfile();
            check(profile==PcbTraceWidthProfile.UNIFORM&&profile.isLegacyUniform()&&
                "UNIFORM_TRACE@1".equals(profile.getIdentity())&&profile.maximumWidth()==9,
                "saved RB56 placement version retains uniform9 profile: "+version);
        }
    }

    static void verifySavedUniformTraceWidths(TroubleshootBoard board,PcbBoardLayout layout) {
        PcbTraceWidthProfile profile=board.getPlacementConstraints().getTraceWidthProfile();
        check(profile==PcbTraceWidthProfile.UNIFORM&&profile.maximumWidth()==9,
            "saved placement keeps the uniform9 trace profile");
        for(PcbTraceGeometry trace:layout.getTraces())
            check(trace.getWidth()==9,"saved placement trace remains width9: "+trace.getNetId());
    }
    static PcbBoardLayout measure(long seed,int index,int version) {
        Rb56Plan plan=Rb56Plan.resolve(seed,version);
        TroubleshootBoard board=plan.board();
        MediumBoardPhysicalPolicy.Result result=new SeededPcbLayoutGenerator()
            .generateWithPolicyResult(board,plan.layoutSeed,plan.routingSeed);
        if(!result.accepted()) {
            throw new AssertionError("accepted baseline route lost: "+seed+" "+result.toCanonical());
        }
        PcbBoardLayout layout=result.getLayout();
        GeneratedPhysicalAdmission admission=MediumBoardNormalAdmission.fromAcceptedRb56Route(
            board,result,plan.layoutSeed,plan.routingSeed);
        admission.requireConstruction(board,layout);
        if(version<=11) verifySavedUniformTraceWidths(board,layout);
        check(board.getComponentIds().size()==PART_COUNTS[index],"literal representative part census");
        check(board.getPadIds().size()==PAD_COUNTS[index],"literal representative pad census");
        check(board.getPlacementConstraints().requiresTwoLayerRouting,"RB56 current placement requires P07");
        layout.validateGeometry(board);layout.validateRoutingGeometry(board);layout.validateRouteQuality();
        new PcbTwoLayerRules(board,layout).validate(layout);
        PcbTraceClearance.validate(layout.getTraces());
        check(layout.getComponents().size()==board.getComponentIds().size(),"every component retained");
        check(layout.getPads().size()==board.getPadIds().size(),"every pad retained");
        if(version==13) {
            TreeSet<String> componentIds=new TreeSet<String>();
            for(PcbComponentPlacement component:layout.getComponents())
                check(componentIds.add(component.getComponentId()),"unique physical component identity");
            check(componentIds.equals(new TreeSet<String>(board.getComponentIds())),
                "exact physical component census");
        }
        TreeSet<String> pads=new TreeSet<String>();
        for(PcbPadPlacement pad:layout.getPads()) {
            check(pads.add(pad.getPadId()),"unique physical pad");
        }
        check(pads.equals(new TreeSet<String>(board.getPadIds())),"exact pad census");
        long regionArea=0,regionPairs=0,regionSpan=0,directNetSpan=0;
        TreeMap<String,Rectangle> regions=new TreeMap<String,Rectangle>();
        Vector<PcbComponentPlacement> parts=layout.getComponents();
        for(int i=0;i<parts.size();i++) {
            PcbComponentPlacement a=parts.get(i);
            PcbPlacementConstraints.Part da=board.getPlacementConstraints().get(a.getComponentId());
            Rectangle court=a.getRoutingCourtyard();String key=da.domainId+"/"+da.regionId;
            Rectangle prior=regions.get(key);
            if(prior==null)regions.put(key,court);else {
                int x=Math.min(prior.x,court.x),y=Math.min(prior.y,court.y);
                regions.put(key,new Rectangle(x,y,Math.max(prior.x+prior.width,court.x+court.width)-x,
                    Math.max(prior.y+prior.height,court.y+court.height)-y));
            }
            if(a.getPhysicalPackage().isConnector()) {
                Rectangle outline=layout.getBoardOutline();
                check(a.getX()>=outline.x&&a.getX()+a.getWidth()<=outline.x+outline.width,"connector inside board");
            }
            for(int j=i+1;j<parts.size();j++) {
                PcbComponentPlacement b=parts.get(j);
                PcbPlacementConstraints.Part db=board.getPlacementConstraints().get(b.getComponentId());
                if(da.domainId.equals(db.domainId)&&da.regionId.equals(db.regionId)) {
                    regionPairs++;regionSpan+=Math.abs((a.getX()*2+a.getWidth())-(b.getX()*2+b.getWidth()))+
                        Math.abs((a.getY()*2+a.getHeight())-(b.getY()*2+b.getHeight()));
                }
            }
        }
        for(Rectangle region:regions.values())regionArea+=(long)region.width*region.height;
        int directNets=0;
        for(String id:board.getNetIds()) {
            Vector<String> netPads=board.getNet(id).getPadIds();
            if(netPads.size()!=2)continue;
            PcbPadPlacement a=layout.getPad(netPads.get(0)),b=layout.getPad(netPads.get(1));
            if(board.getPad(a.getPadId()).getComponentId().equals(board.getPad(b.getPadId()).getComponentId()))continue;
            directNets++;directNetSpan+=Math.abs(a.getX()-b.getX())+Math.abs(a.getY()-b.getY());
        }
        long copperLength=0;int segments=0;
        for(PcbTraceGeometry trace:layout.getTraces()) {
            if(trace.getStartPadId()!=null)
                check(board.getPad(trace.getStartPadId()).getNetId().equals(trace.getNetId()),"start endpoint net binding");
            if(trace.getEndPadId()!=null)
                check(board.getPad(trace.getEndPadId()).getNetId().equals(trace.getNetId()),"end endpoint net binding");
            int[] x=trace.getXPoints(),y=trace.getYPoints();
            for(int i=1;i<x.length;i++) {
                copperLength+=Math.abs(x[i]-x[i-1])+Math.abs(y[i]-y[i-1]);segments++;
            }
        }
        if(version==3) check(SAVED3_GEOMETRY_SHA256[index].equals(
            PcbCompactnessContractTest.physicalGeometrySha256(layout)),
            "published saved3 physical geometry remains exact apart from reference x/y and epoch: "+seed);
        Rectangle outline=layout.getBoardOutline();MediumBoardPhysicalPolicy.Statistics stats=result.getStatistics();
        if(version==12)System.out.println("PCB_ROLE_WIDTH_MEASUREMENT {\"seed\":\""+seed+
            "\",\"version\":12,\"area\":"+((long)outline.width*outline.height)+
            ",\"copperLength\":"+copperLength+",\"segments\":"+segments+
            ",\"vias\":"+layout.getHoles().size()+",\"geometrySha256\":\""+
            PlayerSessionFingerprint.of(layout.geometryFingerprint())+"\"}");
        if(version==13) System.out.println("PCB_NEAREST_VACANCY_ROUTE seed="+seed+" "+result.toCanonical());
        else if(version>=4) System.out.println("PCB_GROUPING_ROUTE_CASE seed="+seed+" "+result.toCanonical());
        int largestProtectedVoid=0;
        if(version>=4) largestProtectedVoid=reportWhitespace(board,layout,seed,version);
        if(version==6) check(SAVED6_GEOMETRY_SHA256[index].equals(
            PcbCompactnessContractTest.physicalGeometrySha256(layout)),"saved6 physical geometry stays exact apart from reference x/y and epoch: "+seed);
        if(version==7) {
            check(SAVED7_GEOMETRY_SHA256[index].equals(PcbCompactnessContractTest.physicalGeometrySha256(layout)),
                "saved7 physical geometry stays exact apart from reference x/y and epoch: "+seed);
            check((long)outline.width*outline.height<=SAVED6_AREA[index],"no representative outline regression: "+seed);
            check(stats.placementCandidates==6 && stats.oneFaceAttempts<=3 && stats.twoLayerAttempts<=3,
                "guarded selection keeps original placement and per-policy route caps");
        }
        if(version==8) check(SAVED8_GEOMETRY_SHA256[index].equals(
            PcbCompactnessContractTest.physicalGeometrySha256(layout)),"saved8 physical geometry stays exact apart from reference x/y and epoch: "+seed);
        if(version==9) {
            long area=(long)outline.width*outline.height;
            check(area<=SAVED8_AREA[index],"no representative regression against accepted8: "+seed);
            if(index==3) check(area<SAVED8_AREA[index],"connected packing reduces the retained42 fixture");
            else check(SAVED8_GEOMETRY_SHA256[index].equals(
                PcbCompactnessContractTest.physicalGeometrySha256(layout)),
                "other representative physical layouts (reference x/y and epoch excluded) remain exact accepted8: "+seed);
        }
        if(version==12) {
            long area=(long)outline.width*outline.height;
            check(board.getPlacementConstraints().placementDensityVersion==11,
                "private12 role widths retain accepted11 packing density: "+seed);
            check(area*100L<=SAVED11_AREA[index]*101L,
                "private12 representative outline is within 101% of accepted11: "+seed+
                " area="+area+" baseline="+SAVED11_AREA[index]);
            verifyRoleTraceWidths(board,layout);
        }
        if(version==13) {
            long area=(long)outline.width*outline.height;
            check(board.getPlacementConstraints().placementDensityVersion==12,
                "private13 nearest vacancy uses declared density12: "+seed);
            verifyRoleTraceWidths(board,layout);
            System.out.println("PCB_NEAREST_VACANCY_CASE {\"seed\":\""+seed+
                "\",\"version\":13,\"density\":"+
                board.getPlacementConstraints().placementDensityVersion+",\"area\":"+area+
                ",\"areaBaseline\":"+SAVED12_AREA[index]+
                ",\"largestProtectedEmptyRectangleArea\":"+largestProtectedVoid+
                ",\"largestProtectedEmptyRectangleBaseline\":"+SAVED12_LARGEST_VOID[index]+
                ",\"copperLength\":"+copperLength+",\"segments\":"+segments+
                ",\"vias\":"+layout.getHoles().size()+",\"geometrySha256\":\""+
                PlayerSessionFingerprint.of(layout.geometryFingerprint())+"\"}");
            check(area<=SAVED12_AREA[index],
                "private13 representative outline does not exceed published12: "+seed+
                " area="+area+" baseline="+SAVED12_AREA[index]);
            check(largestProtectedVoid<=SAVED12_LARGEST_VOID[index],
                "private13 representative protected void does not exceed published12: "+seed+
                " void="+largestProtectedVoid+" baseline="+SAVED12_LARGEST_VOID[index]);
        }
        if(version>=8) {
            long area=(long)outline.width*outline.height;
            check(area<=SAVED7_AREA[index],"no representative outline regression against accepted7: "+seed);
            if(index==0 || index==1 || index==2 || index==4 || index==5)
                check(area<SAVED7_AREA[index],"narrow alternatives improve the declared density cases: "+seed);
            check(stats.placementCandidates==6 && stats.oneFaceAttempts<=3 && stats.twoLayerAttempts<=3,
                "narrow exploration retains original placement and per-policy route caps");
        }
        if(version==5 && (index==0 || index==1 || index==3))
            check(SAVED4_GEOMETRY_SHA256[index].equals(
                PcbCompactnessContractTest.physicalGeometrySha256(layout)),
                "published single-group40/44/42 geometry must not regress: "+seed);
        if(version==5) check(SAVED5_GEOMETRY_SHA256[index].equals(
            PcbCompactnessContractTest.physicalGeometrySha256(layout)),
            "published saved5 physical geometry remains exact apart from reference x/y and epoch: "+seed);
        if(version==4) check(SAVED4_GEOMETRY_SHA256[index].equals(
            PcbCompactnessContractTest.physicalGeometrySha256(layout)),
            "published saved4 physical geometry remains exact apart from reference x/y and epoch: "+seed);
        String caseMarker=version==13?"PCB_NEAREST_VACANCY_REPORT":"PCB_GROUPING_CASE";
        System.out.println(caseMarker+" {\"seed\":\""+seed+"\",\"version\":"+version+
            ",\"accepted\":true,\"parts\":"+parts.size()+",\"pads\":"+pads.size()+
            ",\"width\":"+outline.width+",\"height\":"+outline.height+",\"area\":"+((long)outline.width*outline.height)+
            ",\"courtyardArea\":"+layout.getRoutingCourtyardArea()+",\"regionBoundingAreaSum\":"+regionArea+
            ",\"sameRegionCenterManhattanMean\":"+(regionPairs==0?0:regionSpan/(2.0*regionPairs))+
            ",\"twoPadNetSpanSum\":"+directNetSpan+",\"twoPadNetCount\":"+directNets+
            ",\"copperManhattanLength\":"+copperLength+",\"traceSegments\":"+segments+
            ",\"vias\":"+layout.getHoles().size()+",\"selectedAttempt\":"+stats.selectedPlacementAttempt+
            ",\"routingExpansions\":"+stats.routingExpansions+
            ",\"geometrySha256\":\""+PlayerSessionFingerprint.of(layout.geometryFingerprint())+"\"}");
        return layout;
    }
    /** Drawing-space sampling, not a statement that free cells are packable. */
    static int reportWhitespace(TroubleshootBoard board,PcbBoardLayout layout,long seed,int version) {
        Rectangle outline=layout.getBoardOutline();int step=10,cols=outline.width/step,rows=outline.height/step;
        check(cols>0&&rows>0&&(long)cols*rows<=250000,"bounded whitespace sampling");
        boolean[] blocked=new boolean[cols*rows];
        for(PcbComponentPlacement part:layout.getComponents()) {
            Vector<PcbPadPlacement> pads=new Vector<PcbPadPlacement>();
            for(String id:board.getComponent(part.getComponentId()).getPadIds())pads.add(layout.getPad(id));
            PcbFootprint footprint=new PcbFootprint(part,pads);
            Rectangle envelope=PcbPlacementPlanner.envelope(footprint,
                board.getPlacementConstraints().get(part.getComponentId()).accessMargin);
            mark(blocked,cols,rows,outline,envelope,step);
        }
        int packageOccupied=0;for(boolean cell:blocked)if(cell)packageOccupied++;
        PcbTraceWidthProfile profile=board.getPlacementConstraints().getTraceWidthProfile();
        for(PcbTraceGeometry trace:layout.getTraces()) {
            int guard=PcbTraceRules.clearance(trace.getWidth(),profile.maximumWidth());
            int[] x=trace.getXPoints(),y=trace.getYPoints();
            for(int i=1;i<x.length;i++)mark(blocked,cols,rows,outline,new Rectangle(
                Math.min(x[i-1],x[i])-guard,Math.min(y[i-1],y[i])-guard,
                Math.abs(x[i]-x[i-1])+guard*2,Math.abs(y[i]-y[i-1])+guard*2),step);
        }
        int holeGuard=PcbTraceRules.clearance(profile.maximumWidth(),profile.maximumWidth());
        for(PcbBoardHole hole:layout.getHoles()) {
            Rectangle r=hole.getBounds();mark(blocked,cols,rows,outline,
                new Rectangle(r.x-holeGuard,r.y-holeGuard,r.width+holeGuard*2,r.height+holeGuard*2),step);
        }
        for(PcbSilkscreenLabel label:layout.getSilkscreenLabels())
            mark(blocked,cols,rows,outline,label.getBounds(),step);
        // Count the actual declared domain isolation strip as reserved, even
        // where the normal front view paints it as bare green substrate.
        TreeMap<String,Rectangle> domains=new TreeMap<String,Rectangle>();
        for(PcbComponentPlacement part:layout.getComponents())
            for(java.util.Map.Entry<String,Rectangle> entry:board.getPlacementConstraints().domainCourtyards(part).entrySet()) {
                Rectangle prior=domains.get(entry.getKey()),r=entry.getValue();
                if(prior==null)domains.put(entry.getKey(),new Rectangle(r.x,r.y,r.width,r.height));
                else {int left=Math.min(prior.x,r.x),top=Math.min(prior.y,r.y);
                    domains.put(entry.getKey(),new Rectangle(left,top,
                        Math.max(prior.x+prior.width,r.x+r.width)-left,
                        Math.max(prior.y+prior.height,r.y+r.height)-top));}
            }
        for(PcbPlacementConstraints.Barrier barrier:board.getPlacementConstraints().getBarriers()) {
            Rectangle a=domains.get(barrier.firstDomain),b=domains.get(barrier.secondDomain);int gap=barrier.clearance;
            Rectangle reserved;
            if(a.x+a.width+gap<=b.x || b.x+b.width+gap<=a.x) {
                int lo=a.x<b.x?a.x+a.width:b.x+b.width,hi=a.x<b.x?b.x:a.x;
                int mid=lo+(hi-lo)/2;reserved=new Rectangle(mid-(gap+1)/2,outline.y,gap,outline.height);
            } else {
                int lo=a.y<b.y?a.y+a.height:b.y+b.height,hi=a.y<b.y?b.y:a.y;
                int mid=lo+(hi-lo)/2;reserved=new Rectangle(outline.x,mid-(gap+1)/2,outline.width,gap);
            }
            mark(blocked,cols,rows,outline,reserved,step);
        }
        int free=0,bestArea=0,bestX=0,bestY=0,bestW=0,bestH=0;
        int[] height=new int[cols],stack=new int[cols+1];
        for(int y=0;y<rows;y++) {
            for(int x=0;x<cols;x++) {boolean occupied=blocked[y*cols+x];if(!occupied)free++;
                height[x]=occupied?0:height[x]+1;}
            int top=-1;
            for(int x=0;x<=cols;x++) {
                int current=x==cols?0:height[x];
                while(top>=0&&height[stack[top]]>current) {
                    int h=height[stack[top--]],left=top<0?0:stack[top]+1,w=x-left;
                    if(h*w>bestArea) {bestArea=h*w;bestX=left;bestY=y-h+1;bestW=w;bestH=h;}
                }
                if(x<cols)stack[++top]=x;
            }
        }
        System.out.println("PCB_WHITESPACE_CASE {\"seed\":\""+seed+"\",\"version\":"+version+
            ",\"parts\":"+layout.getComponents().size()+",\"gridUnits\":10,\"sampledCells\":"+(cols*rows)+
            ",\"packageAccessOccupiedCells\":"+packageOccupied+",\"protectedFreeCells\":"+free+
            ",\"protectedFreeFraction\":"+(free/(double)(cols*rows))+",\"largestProtectedEmptyRectangle\":{\"x\":"+
            (outline.x+bestX*step)+",\"y\":"+(outline.y+bestY*step)+",\"width\":"+(bestW*step)+
            ",\"height\":"+(bestH*step)+",\"area\":"+(bestArea*step*step)+"},\"scope\":\"GRID10_FULL_PACKAGE_ACCESS_BOTH_COPPER_FACES_VIAS_SILK_AND_DECLARED_ISOLATION;FREE_IS_NOT_PACKABLE_PROOF\"}");
        // Independent pre-primary trial receipt: 1130x1050 at (60,776).
        // This bounds the dominant empty patch, not a manufacturing packing optimum.
        if(version>=8 && seed==17L)check(bestArea*step*step<=1186500/2,
            "the targeted primary-side void shrinks by at least half");
        if(version>=11 && seed==42L)check(bestArea*step*step<327600,
            "vacancy probes improve the frozen10 protected empty rectangle");
        return bestArea*step*step;
    }
    private static void mark(boolean[] blocked,int cols,int rows,Rectangle outline,Rectangle r,int step) {
        int x0=Math.max(0,(r.x-outline.x)/step),y0=Math.max(0,(r.y-outline.y)/step);
        int x1=Math.min(cols-1,(r.x+r.width-outline.x+step-1)/step),y1=Math.min(rows-1,(r.y+r.height-outline.y+step-1)/step);
        for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++)blocked[y*cols+x]=true;
    }
    private static void verifyRoleTraceWidths(TroubleshootBoard board,PcbBoardLayout layout) {
        PcbTraceWidthProfile profile=board.getPlacementConstraints().getTraceWidthProfile();
        check("ROLE_TRACE@1".equals(profile.getIdentity()),"current route identifies ROLE_TRACE@1");
        check(profile.maximumWidth()==13,"ROLE_TRACE maximum drawing width is13");
        for(BoardNet.RoutingRole role:BoardNet.RoutingRole.values())
            check(profile.widthFor(role)==literalRoleWidth(role),
                "profile uses literal width for role "+role);
        for(PcbTraceGeometry trace:layout.getTraces()) {
            BoardNet net=board.getNet(trace.getNetId());
            BoardNet.RoutingRole role=net.getRoutingRole();
            int expected=literalRoleWidth(role);
            check(profile.widthFor(role)==expected,"profile width agrees with net role: "+role);
            check(trace.getWidth()==expected,"actual routed width agrees with net role: "+
                trace.getNetId()+"/"+role+" expected="+expected+" actual="+trace.getWidth());
        }
    }
    private static int literalRoleWidth(BoardNet.RoutingRole role) {
        switch(role) {
        case HIGH_CURRENT:return 13;
        case SUPPLY:
        case RETURN:return 11;
        case CONTROL:
        case SIGNAL:return 9;
        default:throw new AssertionError("Unexpected PCB routing role: "+role);
        }
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
