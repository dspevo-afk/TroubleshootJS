package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;

/** Root-only selected-host probe. It supplies no receipt-content proof. */
public final class ReceiptIdentityHostProbe {
    public static void main(String[] args) throws Exception {
        if (args.length!=1) throw new IllegalArgumentException("PROBE_ARGUMENTS");
        Path root=Paths.get(args[0]);
        if (!root.isAbsolute() || !root.normalize().equals(root))
            throw new IllegalArgumentException("PROBE_ROOT");
        Files.createDirectory(root);
        Path file=root.resolve("op-1.jsonl"),retained=root.resolve("retained-synthetic.txt");
        byte[] bytes="SYNTHETIC_IDENTITY_PROBE_NOT_A_NORMAL_RECEIPT\n".getBytes("US-ASCII");
        Files.write(file,bytes,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        Path ancestor=root;
        while (ancestor!=null) {
            new CohortTerminalTelemetryStudy.R5FileIdentity(ancestor,true);
            ancestor=ancestor.getParent();
        }
        CohortTerminalTelemetryStudy.R5FileIdentity identity=
            new CohortTerminalTelemetryStudy.R5FileIdentity(file,false);
        identity.verify(file,false,true);
        FileTime originalTime=Files.getLastModifiedTime(file);
        bytes[0]='X';
        Files.write(file,bytes,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE);
        Files.setLastModifiedTime(file,originalTime);
        // Expected PASS demonstrates why unchanged metadata is not content proof.
        identity.verify(file,false,true);
        Files.move(file,retained,StandardCopyOption.ATOMIC_MOVE);
        Files.write(file,bytes,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        Files.setLastModifiedTime(file,originalTime);
        boolean rejected=false;
        try { identity.verify(file,false,true); }
        catch (IOException expected) {
            if (!"R5_FILE_REPLACED".equals(expected.getMessage())) throw expected;
            rejected=true;
        }
        if (!rejected) throw new AssertionError("REPLACED_FILE_ACCEPTED");
        Files.delete(file);
        boolean missing=false;
        try { new CohortTerminalTelemetryStudy.R5FileIdentity(file,false); }
        catch (IOException expected) { missing=true; }
        if (!missing) throw new AssertionError("MISSING_FILE_ACCEPTED");
        System.out.println("SYNTHETIC_RECEIPT_IDENTITY_HOST_PROBE_ONLY; assertions=4; contentProof=NONE");
    }
}
