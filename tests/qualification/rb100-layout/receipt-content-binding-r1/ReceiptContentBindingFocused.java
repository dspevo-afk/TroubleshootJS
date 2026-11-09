package com.lushprojects.circuitjs1.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;

/** Root-owned focused real-host control. The three-byte fixture is not JSON. */
public final class ReceiptContentBindingFocused {
    private static final String SHA_ABC=
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static int assertions;
    private interface Action { void run(); }

    public static void main(String[] args) throws Exception {
        if (args.length!=1) throw new IllegalArgumentException("FOCUSED_ARGUMENTS");
        Path root=Paths.get(args[0]);
        if (!root.isAbsolute() || !root.normalize().equals(root))
            throw new IllegalArgumentException("FOCUSED_PATH");
        Files.createDirectory(root);
        if (!root.toRealPath().equals(root)) throw new IllegalArgumentException("FOCUSED_PATH");
        for (int index=0;index<2;index++) {
            Path directory=root.resolve("case-"+index);
            Files.createDirectory(directory);
            Path receipt=directory.resolve("op-1.jsonl");
            Files.write(receipt,new byte[]{'a','b','c'},
                StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            final long started=System.nanoTime();
            CohortTerminalTelemetryStudy.Parent parent=new CohortTerminalTelemetryStudy.Parent();
            final CohortTerminalTelemetryStudy.SetupMeter meter=
                new CohortTerminalTelemetryStudy.SetupMeter(parent,parent.meter(started));
            final ReceiptContentBinding binding=
                ReceiptContentBinding.prepare(meter,started,receipt,3L,SHA_ABC);
            if (index==1) {
                CohortTerminalTelemetryStudy.R5FileIdentity baseline=
                    new CohortTerminalTelemetryStudy.R5FileIdentity(receipt,false);
                FileTime modified=Files.getLastModifiedTime(receipt);
                Files.write(receipt,new byte[]{'a','b','d'},
                    StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING);
                Files.setLastModifiedTime(receipt,modified);
                baseline.verify(receipt,false,true);
                assertions++;
            }
            meter.finish();
            long units=number(meter,"units"),copies=number(meter,"copiedEntries"),
                chunks=number(meter,"copyChunks"),groups=number(meter,"groups");
            if (index==0) {
                binding.verifyFinal(meter,started);
                assertions++;
            } else {
                expect("FINAL_SHA_MISMATCH",new Action() {
                    public void run() { binding.verifyFinal(meter,started); }
                });
                expect("FINAL_ATTEMPT_REUSED",new Action() {
                    public void run() { binding.verifyFinal(meter,started); }
                });
            }
            if (units!=number(meter,"units") || copies!=number(meter,"copiedEntries") ||
                    chunks!=number(meter,"copyChunks") || groups!=number(meter,"groups"))
                throw new AssertionError("FOCUSED_METER_CHANGED");
            assertions++;
        }
        System.out.println("SYNTHETIC_RECEIPT_BINDING_FOCUSED_ONLY; assertions="+assertions+
            "; normalProof=NONE");
    }

    private static long number(CohortTerminalTelemetryStudy.SetupMeter meter,String field)
            throws Exception {
        return ((Number)CohortTerminalTelemetryStudy.field(meter.meter,field)).longValue();
    }

    private static void expect(String code,Action action) {
        try { action.run(); }
        catch (ReceiptContentBinding.BindingFailure expected) {
            if (!code.equals(expected.code)) throw new AssertionError("WRONG_FOCUSED_CODE",expected);
            assertions++;
            return;
        }
        throw new AssertionError("MISSING_FOCUSED_CODE_"+code);
    }
}
