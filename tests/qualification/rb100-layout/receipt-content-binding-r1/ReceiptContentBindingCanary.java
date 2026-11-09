package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Synthetic-only native canary for ReceiptContentBinding. All fixture files
 * are created beneath the root-owned argv[0] directory. The root owns that
 * directory and its junction cleanup. This canary never reads a real receipt,
 * creates an arm/Session, invokes a producer, or publishes a result/marker.
 *
 * Source-only component canary. Root owns all compilation and execution gates;
 * this source does not certify any normal receipt, producer, or export.
 */
public final class ReceiptContentBindingCanary {
    private static final String RECEIPT_NAME="op-1.jsonl";
    private static final long MAX_RECEIPT_BYTES=8388608L;
    private static final int HASH_BLOCK_BYTES=4096;
    private static final long PRODUCER_LIMIT_NANOS=90000000000L;
    private static int assertions;
    private static int cases;

    private interface Action { void run() throws Exception; }

    private ReceiptContentBindingCanary() { }

    public static void main(String[] args) throws Exception {
        FixtureRoot fixtureRoot=FixtureRoot.fromArgs(args);
        runCanaries(fixtureRoot);
        System.out.println("SYNTHETIC_RECEIPT_BINDING_COMPONENT_ONLY cases="+cases+
            " assertions="+assertions+"; normalReceipt/host/exportProof=NONE");
    }

    private static void runCanaries(FixtureRoot root) throws Exception {
        testPositiveBoundaries(root);
        testInitialShaMismatch(root);
        testInitialLengthMismatch(root);
        testLengthCap(root);
        testSameSizeChangedContent(root);
        testFinalLengthChange(root);
        testFileReplacement(root);
        testAncestorReplacement(root);
        testMissingFile(root);
        testJunctionAlias(root);
        testForeignAndRetiredTickets(root);
        testForeignStartedTicket(root);
        testPrepareMeterStartMismatch(root);
        testExpiredMeterDeadline(root);
        testHashDeadlineGuardOnly();
        testOpenGroupPlacement(root);
    }

    private static void testPositiveBoundaries(FixtureRoot root) throws Exception {
        int[] lengths={4095,4096,4097,8388608};
        for (int i=0;i<lengths.length;i++) {
            byte[] bytes=syntheticBytes(lengths[i],17+i);
            CaseFile fixture=root.newCase("positive-"+lengths[i],bytes);
            MeterFixture meter=new MeterFixture();
            MeterState before=MeterState.capture(meter.setup.meter);
            String sha=independentSha256(bytes);
            long started=System.nanoTime();
            ReceiptContentBinding binding=ReceiptContentBinding.prepare(
                meter.setup,meter.started,fixture.receipt,(long)bytes.length,sha);
            long completed=System.nanoTime();
            MeterState prepared=MeterState.capture(meter.setup.meter);
            check(completed>=started && completed-started<=PRODUCER_LIMIT_NANOS,
                "POSITIVE_PREPARE_WALL_BOUND_"+lengths[i]);
            assertPreparationDelta(before,prepared,fixture.receipt,bytes.length);

            /* A returned prepare call must not retain an open file handle. */
            moveFileRoundTrip(root,fixture);
            meter.setup.finish();
            MeterState closed=MeterState.capture(meter.setup.meter);
            long verifyStarted=System.nanoTime();
            binding.verifyFinal(meter.setup,meter.started);
            long verifyCompleted=System.nanoTime();
            check(verifyCompleted>=verifyStarted,"FINAL_VERIFY_CLOCK_"+lengths[i]);
            check(closed.same(MeterState.capture(meter.setup.meter)),
                "CLOSED_METER_UNCHANGED_"+lengths[i]);
            deleteOwnedFile(root,fixture.directory,fixture.receipt);
            cases++;
        }
    }

    private static void testInitialShaMismatch(FixtureRoot root) throws Exception {
        final byte[] bytes=syntheticBytes(4097,51);
        final CaseFile fixture=root.newCase("initial-sha",bytes);
        final MeterFixture meter=new MeterFixture();
        expectCode("INITIAL_SHA_MISMATCH",new Action() {
            public void run() throws Exception {
                ReceiptContentBinding.prepare(meter.setup,meter.started,fixture.receipt,
                    (long)bytes.length,flipSha(independentSha256(bytes)));
            }
        });
        meter.setup.finish();
        deleteOwnedFile(root,fixture.directory,fixture.receipt);
        cases++;
    }

    private static void testInitialLengthMismatch(FixtureRoot root) throws Exception {
        final byte[] bytes=syntheticBytes(4095,71);
        final CaseFile fixture=root.newCase("initial-length",bytes);
        final MeterFixture meter=new MeterFixture();
        expectCode("INITIAL_LENGTH_MISMATCH",new Action() {
            public void run() throws Exception {
                ReceiptContentBinding.prepare(meter.setup,meter.started,fixture.receipt,
                    (long)bytes.length+1L,independentSha256(bytes));
            }
        });
        meter.setup.finish();
        deleteOwnedFile(root,fixture.directory,fixture.receipt);
        cases++;
    }

    private static void testLengthCap(FixtureRoot root) throws Exception {
        final byte[] bytes=syntheticBytes(1,97);
        final CaseFile fixture=root.newCase("length-cap",bytes);
        final MeterFixture meter=new MeterFixture();
        expectCode("EXPECTED_LENGTH_OUT_OF_RANGE",new Action() {
            public void run() throws Exception {
                ReceiptContentBinding.prepare(meter.setup,meter.started,fixture.receipt,
                    MAX_RECEIPT_BYTES+1L,independentSha256(bytes));
            }
        });
        meter.setup.finish();
        deleteOwnedFile(root,fixture.directory,fixture.receipt);
        cases++;
    }

    private static void testSameSizeChangedContent(FixtureRoot root) throws Exception {
        final byte[] original=syntheticBytes(4097,121);
        CaseFile fixture=root.newCase("same-size-change",original);
        final MeterFixture meter=new MeterFixture();
        final ReceiptContentBinding binding=ReceiptContentBinding.prepare(
            meter.setup,meter.started,fixture.receipt,(long)original.length,
            independentSha256(original));
        CohortTerminalTelemetryStudy.R5FileIdentity metadataOnlyBaseline=
            new CohortTerminalTelemetryStudy.R5FileIdentity(fixture.receipt,false);
        FileTime modified=Files.getLastModifiedTime(fixture.receipt,LinkOption.NOFOLLOW_LINKS);
        byte[] changed=original.clone();
        changed[changed.length/2]=(byte)(changed[changed.length/2]^0x41);
        Files.write(fixture.receipt,changed,StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING);
        Files.setLastModifiedTime(fixture.receipt,modified);
        metadataOnlyBaseline.verify(fixture.receipt,false,true);
        meter.setup.finish();
        MeterState beforeFinal=MeterState.capture(meter.setup.meter);
        expectCode("FINAL_SHA_MISMATCH",new Action() {
            public void run() throws Exception { binding.verifyFinal(meter.setup,meter.started); }
        });
        check(beforeFinal.same(MeterState.capture(meter.setup.meter)),
            "CHANGED_CONTENT_FINAL_DOES_NOT_CHARGE_METER");
        expectCode("FINAL_ATTEMPT_REUSED",new Action() {
            public void run() throws Exception { binding.verifyFinal(meter.setup,meter.started); }
        });
        deleteOwnedFile(root,fixture.directory,fixture.receipt);
        cases++;
    }

    private static void testFinalLengthChange(FixtureRoot root) throws Exception {
        byte[] original=syntheticBytes(4096,143);
        CaseFile fixture=root.newCase("final-length-change",original);
        final MeterFixture meter=new MeterFixture();
        final ReceiptContentBinding binding=ReceiptContentBinding.prepare(
            meter.setup,meter.started,fixture.receipt,(long)original.length,
            independentSha256(original));
        byte[] grown=new byte[original.length+1];
        System.arraycopy(original,0,grown,0,original.length);
        grown[grown.length-1]=(byte)0x5a;
        Files.write(fixture.receipt,grown,StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING);
        meter.setup.finish();
        MeterState beforeFinal=MeterState.capture(meter.setup.meter);
        expectCode("FINAL_LENGTH_MISMATCH",new Action() {
            public void run() throws Exception { binding.verifyFinal(meter.setup,meter.started); }
        });
        check(beforeFinal.same(MeterState.capture(meter.setup.meter)),
            "GROWN_CONTENT_FINAL_DOES_NOT_CHARGE_METER");
        deleteOwnedFile(root,fixture.directory,fixture.receipt);
        cases++;

        final byte[] truncationOriginal=syntheticBytes(4096,149);
        final CaseFile truncatedFixture=root.newCase("final-length-truncation",truncationOriginal);
        final MeterFixture truncationMeter=new MeterFixture();
        final ReceiptContentBinding truncationBinding=ReceiptContentBinding.prepare(
            truncationMeter.setup,truncationMeter.started,truncatedFixture.receipt,
            (long)truncationOriginal.length,independentSha256(truncationOriginal));
        byte[] truncated=new byte[truncationOriginal.length-1];
        System.arraycopy(truncationOriginal,0,truncated,0,truncated.length);
        Files.write(truncatedFixture.receipt,truncated,StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING);
        truncationMeter.setup.finish();
        MeterState beforeTruncatedFinal=MeterState.capture(truncationMeter.setup.meter);
        expectCode("FINAL_LENGTH_MISMATCH",new Action() {
            public void run() throws Exception {
                truncationBinding.verifyFinal(truncationMeter.setup,truncationMeter.started);
            }
        });
        check(beforeTruncatedFinal.same(MeterState.capture(truncationMeter.setup.meter)),
            "TRUNCATED_CONTENT_FINAL_DOES_NOT_CHARGE_METER");
        deleteOwnedFile(root,truncatedFixture.directory,truncatedFixture.receipt);
        cases++;
    }

    private static void testFileReplacement(FixtureRoot root) throws Exception {
        byte[] bytes=syntheticBytes(4096,181);
        CaseFile fixture=root.newCase("file-replacement",bytes);
        final MeterFixture meter=new MeterFixture();
        final ReceiptContentBinding binding=ReceiptContentBinding.prepare(
            meter.setup,meter.started,fixture.receipt,(long)bytes.length,
            independentSha256(bytes));
        Path held=fixture.directory.resolve("op-1.held.jsonl").toAbsolutePath().normalize();
        moveOwnedCaseEntry(root,fixture.directory,fixture.receipt,held);
        writeNewReceipt(fixture.directory,fixture.receipt,bytes);
        meter.setup.finish();
        MeterState beforeFinal=MeterState.capture(meter.setup.meter);
        expectCode("RECEIPT_IDENTITY_MISMATCH",new Action() {
            public void run() throws Exception { binding.verifyFinal(meter.setup,meter.started); }
        });
        check(beforeFinal.same(MeterState.capture(meter.setup.meter)),
            "REPLACED_FILE_FINAL_DOES_NOT_CHARGE_METER");
        deleteOwnedFile(root,fixture.directory,fixture.receipt);
        deleteOwnedFile(root,fixture.directory,held);
        cases++;
    }

    private static void testAncestorReplacement(FixtureRoot root) throws Exception {
        byte[] bytes=syntheticBytes(4097,199);
        Path caseDirectory=root.newDirectory("ancestor-replacement");
        Path parent=caseDirectory.resolve("parent").toAbsolutePath().normalize();
        Files.createDirectory(parent);
        Path receipt=parent.resolve(RECEIPT_NAME).toAbsolutePath().normalize();
        writeNewReceipt(parent,receipt,bytes);
        final MeterFixture meter=new MeterFixture();
        final ReceiptContentBinding binding=ReceiptContentBinding.prepare(
            meter.setup,meter.started,receipt,(long)bytes.length,independentSha256(bytes));
        Path held=caseDirectory.resolve("parent.held").toAbsolutePath().normalize();
        moveOwnedCaseEntry(root,caseDirectory,parent,held);
        Files.createDirectory(parent);
        Path replacement=parent.resolve(RECEIPT_NAME).toAbsolutePath().normalize();
        writeNewReceipt(parent,replacement,bytes);
        meter.setup.finish();
        MeterState beforeFinal=MeterState.capture(meter.setup.meter);
        expectCode("RECEIPT_IDENTITY_MISMATCH",new Action() {
            public void run() throws Exception { binding.verifyFinal(meter.setup,meter.started); }
        });
        check(beforeFinal.same(MeterState.capture(meter.setup.meter)),
            "REPLACED_ANCESTOR_FINAL_DOES_NOT_CHARGE_METER");
        deleteOwnedFile(root,parent,replacement);
        deleteOwnedFile(root,held,held.resolve(RECEIPT_NAME));
        deleteOwnedDirectory(root,caseDirectory,parent);
        deleteOwnedDirectory(root,caseDirectory,held);
        cases++;
    }

    private static void testMissingFile(FixtureRoot root) throws Exception {
        byte[] bytes=syntheticBytes(4096,211);
        CaseFile fixture=root.newCase("missing-file",bytes);
        final MeterFixture meter=new MeterFixture();
        final ReceiptContentBinding binding=ReceiptContentBinding.prepare(
            meter.setup,meter.started,fixture.receipt,(long)bytes.length,
            independentSha256(bytes));
        deleteOwnedFile(root,fixture.directory,fixture.receipt);
        meter.setup.finish();
        MeterState beforeFinal=MeterState.capture(meter.setup.meter);
        expectCode("RECEIPT_IDENTITY_MISMATCH",new Action() {
            public void run() throws Exception { binding.verifyFinal(meter.setup,meter.started); }
        });
        check(beforeFinal.same(MeterState.capture(meter.setup.meter)),
            "MISSING_FILE_FINAL_DOES_NOT_CHARGE_METER");
        cases++;
    }

    private static void testJunctionAlias(FixtureRoot root) throws Exception {
        final byte[] bytes=syntheticBytes(4096,229);
        Path directory=root.newJunctionTargetCase("junction-alias");
        Path targetReceipt=directory.resolve(RECEIPT_NAME).toAbsolutePath().normalize();
        writeNewReceipt(directory,targetReceipt,bytes);
        Path aliasedDirectory=root.junctionAlias.resolve(directory.getFileName()).normalize();
        final Path aliasedReceipt=aliasedDirectory.resolve(RECEIPT_NAME).toAbsolutePath().normalize();
        check(Files.isRegularFile(aliasedReceipt,LinkOption.NOFOLLOW_LINKS) &&
            aliasedReceipt.toRealPath().equals(targetReceipt.toRealPath()),
            "JUNCTION_ALIAS_RESOLVES_TO_EXISTING_PHYSICAL_RECEIPT");
        final MeterFixture meter=new MeterFixture();
        expectCode("RECEIPT_PATH_INVALID",new Action() {
            public void run() throws Exception {
                ReceiptContentBinding.prepare(meter.setup,meter.started,aliasedReceipt,
                    (long)bytes.length,independentSha256(bytes));
            }
        });
        meter.setup.finish();
        deleteOwnedFile(root,directory,targetReceipt);
        deleteOwnedDirectory(root,root.junctionTarget,directory);
        cases++;
    }

    private static void testForeignAndRetiredTickets(FixtureRoot root) throws Exception {
        byte[] bytes=syntheticBytes(4095,241);
        CaseFile fixture=root.newCase("foreign-ticket",bytes);
        final MeterFixture meter=new MeterFixture();
        final MeterFixture foreign=new MeterFixture();
        final ReceiptContentBinding binding=ReceiptContentBinding.prepare(
            meter.setup,meter.started,fixture.receipt,(long)bytes.length,
            independentSha256(bytes));
        meter.setup.finish();
        foreign.setup.finish();
        MeterState ownerBefore=MeterState.capture(meter.setup.meter);
        MeterState foreignBefore=MeterState.capture(foreign.setup.meter);
        expectCode("FINAL_AUTHORITY_MISMATCH",new Action() {
            public void run() throws Exception { binding.verifyFinal(foreign.setup,foreign.started); }
        });
        check(ownerBefore.same(MeterState.capture(meter.setup.meter)),"FOREIGN_OWNER_METER_UNCHANGED");
        check(foreignBefore.same(MeterState.capture(foreign.setup.meter)),"FOREIGN_METER_UNCHANGED");
        expectCode("FINAL_ATTEMPT_REUSED",new Action() {
            public void run() throws Exception { binding.verifyFinal(meter.setup,meter.started); }
        });
        deleteOwnedFile(root,fixture.directory,fixture.receipt);
        cases++;
    }

    private static void testForeignStartedTicket(FixtureRoot root) throws Exception {
        byte[] bytes=syntheticBytes(4096,247);
        CaseFile fixture=root.newCase("foreign-started-ticket",bytes);
        final MeterFixture meter=new MeterFixture();
        final ReceiptContentBinding binding=ReceiptContentBinding.prepare(
            meter.setup,meter.started,fixture.receipt,(long)bytes.length,
            independentSha256(bytes));
        meter.setup.finish();
        MeterState before=MeterState.capture(meter.setup.meter);
        expectCode("FINAL_AUTHORITY_MISMATCH",new Action() {
            public void run() throws Exception {
                binding.verifyFinal(meter.setup,meter.started+1L);
            }
        });
        check(before.same(MeterState.capture(meter.setup.meter)),
            "FOREIGN_STARTED_METER_UNCHANGED");
        expectCode("FINAL_ATTEMPT_REUSED",new Action() {
            public void run() throws Exception { binding.verifyFinal(meter.setup,meter.started); }
        });
        deleteOwnedFile(root,fixture.directory,fixture.receipt);
        cases++;
    }

    private static void testPrepareMeterStartMismatch(FixtureRoot root) throws Exception {
        final byte[] bytes=syntheticBytes(1,249);
        final Path directory=root.newDirectory("prepare-start-mismatch");
        final Path missingReceipt=directory.resolve(RECEIPT_NAME).toAbsolutePath().normalize();
        final MeterFixture meter=new MeterFixture();
        check(!Files.exists(missingReceipt,LinkOption.NOFOLLOW_LINKS),
            "PREPARE_START_MISMATCH_RECEIPT_IS_MISSING");
        MeterState before=MeterState.capture(meter.setup.meter);
        expectCode("PREPARE_AUTHORITY_MISMATCH",new Action() {
            public void run() throws Exception {
                ReceiptContentBinding.prepare(meter.setup,meter.started+1L,missingReceipt,
                    (long)bytes.length,independentSha256(bytes));
            }
        });
        MeterState rejected=MeterState.capture(meter.setup.meter);
        check(rejected.units-before.units==1L &&
            rejected.copiedEntries==before.copiedEntries &&
            rejected.copyChunks==before.copyChunks,
            "PREPARE_START_REJECTION_PREPAY_ONLY");
        meter.setup.finish();
        deleteOwnedDirectory(root,root.directory,directory);
        cases++;
    }

    private static void testExpiredMeterDeadline(FixtureRoot root) throws Exception {
        final byte[] bytes=syntheticBytes(4096,251);
        final CaseFile fixture=root.newCase("expired-meter-deadline",bytes);
        final long expired=System.nanoTime()-PRODUCER_LIMIT_NANOS-1L;
        final MeterFixture meter=new MeterFixture(expired);
        final String sha=independentSha256(bytes);
        MeterState before=MeterState.capture(meter.setup.meter);
        expectCode("METER_REJECTION",new Action() {
            public void run() throws Exception {
                ReceiptContentBinding.prepare(meter.setup,expired,fixture.receipt,
                    (long)bytes.length,sha);
            }
        });
        check(before.same(MeterState.capture(meter.setup.meter)),
            "EXPIRED_METER_REJECTS_BEFORE_CHARGING_OR_OPENING_GROUP");
        meter.setup.finish();
        deleteOwnedFile(root,fixture.directory,fixture.receipt);
        cases++;
    }

    /**
     * White-box guard-only coverage for the exact clock predicate called by
     * hashContent. This does not simulate slow I/O or claim a timeout run.
     */
    private static void testHashDeadlineGuardOnly() throws Exception {
        final long now=System.nanoTime();
        final long producerStarted=now;
        final long hashStarted=now-5000000000L-1L;
        Method guard=ReceiptContentBinding.class.getDeclaredMethod(
            "checkHashDeadline",Long.TYPE,Long.TYPE);
        guard.setAccessible(true);
        try {
            guard.invoke(null,Long.valueOf(producerStarted),Long.valueOf(hashStarted));
        } catch (InvocationTargetException failed) {
            Throwable cause=failed.getCause();
            if (cause instanceof ReceiptContentBinding.BindingFailure &&
                    "HASH_DEADLINE".equals(
                        ((ReceiptContentBinding.BindingFailure)cause).code)) {
                assertions++;
                cases++;
                return;
            }
            throw new AssertionError("EXPECTED_HASH_DEADLINE_GOT_"+
                (cause==null?"NULL":cause.getClass().getName()),cause);
        }
        throw new AssertionError("MISSING_HASH_DEADLINE");
    }

    private static void testOpenGroupPlacement(FixtureRoot root) throws Exception {
        byte[] bytes=syntheticBytes(4096,263);
        CaseFile fixture=root.newCase("open-group-placement",bytes);
        MeterFixture meter=new MeterFixture();
        ReceiptContentBinding binding=ReceiptContentBinding.prepare(
            meter.setup,meter.started,fixture.receipt,(long)bytes.length,
            independentSha256(bytes));
        meter.setup.finish();
        meter.setup.charge(1L); // synthetic export-like group; not export accounting evidence
        MeterState before=MeterState.capture(meter.setup.meter);
        long started=System.nanoTime();
        binding.verifyFinal(meter.setup,meter.started);
        long completed=System.nanoTime();
        check(before.same(MeterState.capture(meter.setup.meter)),
            "OPEN_GROUP_FINAL_VERIFY_DOES_NOT_CHARGE_METER");
        meter.setup.finish();
        MeterState finished=MeterState.capture(meter.setup.meter);
        check(completed>=started && finished.maxGroupNanos>=completed-started,
            "FINAL_VERIFY_WALL_TIME_FITS_SYNTHETIC_OPEN_GROUP");
        deleteOwnedFile(root,fixture.directory,fixture.receipt);
        cases++;
    }

    private static void assertPreparationDelta(MeterState before,
            MeterState after,Path receipt,long bytes) {
        long blockCount=bytes/(long)HASH_BLOCK_BYTES+
            (bytes%(long)HASH_BLOCK_BYTES==0L?0L:1L);
        int pathEntries=independentPathEntryCount(receipt);
        check(pathEntries>=2 && pathEntries<=4096,"PREPARE_PATH_ENTRY_BOUNDS");
        check(blockCount>=1L && blockCount<=2048L,"PREPARE_HASH_BLOCK_BOUNDS");
        long expectedUnits=116L+(10L*(long)pathEntries)+(6L*blockCount);
        long expectedCopies=(2L*(long)pathEntries)+8L;
        check(after.units-before.units==expectedUnits,"PREPARE_EXACT_METER_UNITS");
        check(after.copiedEntries-before.copiedEntries==expectedCopies,
            "PREPARE_EXACT_COPY_ENTRIES");
        check(after.copyChunks-before.copyChunks==3,
            "PREPARE_EXACT_COPY_CHUNKS");
    }

    private static int independentPathEntryCount(Path path) {
        if (path==null || !path.isAbsolute() || !path.normalize().equals(path))
            throw new AssertionError("PREPARE_PATH_COUNT_INPUT");
        int count=0;
        Path part=path;
        while (part!=null) {
            count++;
            part=part.getParent();
        }
        return count;
    }

    private static byte[] syntheticBytes(int length,int salt) {
        byte[] bytes=new byte[length];
        for (int i=0;i<length;i++) bytes[i]=(byte)(salt+31*i+(i>>>7));
        return bytes;
    }

    private static String independentSha256(byte[] bytes) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        byte[] hash=digest.digest(bytes);
        char[] hex="0123456789abcdef".toCharArray();
        char[] output=new char[hash.length*2];
        for (int i=0;i<hash.length;i++) {
            int value=hash[i]&255;
            output[2*i]=hex[value>>>4];
            output[2*i+1]=hex[value&15];
        }
        return new String(output);
    }

    private static String flipSha(String sha) {
        char replacement=sha.charAt(0)=='0'?'1':'0';
        return replacement+sha.substring(1);
    }

    private static void moveFileRoundTrip(FixtureRoot root,CaseFile fixture) throws Exception {
        Path held=fixture.directory.resolve("receipt.held.jsonl").toAbsolutePath().normalize();
        FileTime modified=Files.getLastModifiedTime(fixture.receipt,LinkOption.NOFOLLOW_LINKS);
        moveOwnedCaseEntry(root,fixture.directory,fixture.receipt,held);
        moveOwnedCaseEntry(root,fixture.directory,held,fixture.receipt);
        Files.setLastModifiedTime(fixture.receipt,modified);
        check(Files.isRegularFile(fixture.receipt,LinkOption.NOFOLLOW_LINKS),"PREPARE_HANDLE_CLOSED");
    }

    private static void writeNewReceipt(Path parent,Path receipt,byte[] bytes) throws Exception {
        Path normalizedParent=parent.toAbsolutePath().normalize();
        Path normalizedReceipt=receipt.toAbsolutePath().normalize();
        if (!normalizedReceipt.getParent().equals(normalizedParent) ||
                !RECEIPT_NAME.equals(normalizedReceipt.getFileName().toString()))
            throw new IOException("CANARY_FILE_CONFINEMENT");
        Files.write(normalizedReceipt,bytes,StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE);
    }

    private static void moveOwnedCaseEntry(FixtureRoot root,Path caseDirectory,
            Path source,Path target) throws Exception {
        Path normalizedCase=caseDirectory.toAbsolutePath().normalize();
        Path normalizedSource=source.toAbsolutePath().normalize();
        Path normalizedTarget=target.toAbsolutePath().normalize();
        if (!normalizedCase.getParent().equals(root.directory) ||
                !normalizedSource.getParent().equals(normalizedCase) ||
                !normalizedTarget.getParent().equals(normalizedCase) ||
                !normalizedSource.startsWith(root.directory) ||
                !normalizedTarget.startsWith(root.directory) ||
                normalizedSource.equals(root.junctionAlias) || normalizedTarget.equals(root.junctionAlias))
            throw new IOException("CANARY_MOVE_CONFINEMENT");
        Files.move(normalizedSource,normalizedTarget);
    }

    private static void deleteOwnedFile(FixtureRoot root,Path ownerDirectory,Path file)
            throws Exception {
        Path directory=ownerDirectory.toAbsolutePath().normalize();
        Path normalized=file.toAbsolutePath().normalize();
        if (!directory.startsWith(root.directory) || !normalized.startsWith(root.directory) ||
                !normalized.getParent().equals(directory) ||
                !RECEIPT_NAME.equals(normalized.getFileName().toString()) &&
                    !normalized.getFileName().toString().endsWith(".jsonl"))
            throw new IOException("CANARY_DELETE_CONFINEMENT");
        Files.deleteIfExists(normalized);
    }

    private static void deleteOwnedDirectory(FixtureRoot root,Path ownerDirectory,Path directory)
            throws Exception {
        Path owner=ownerDirectory.toAbsolutePath().normalize();
        Path normalized=directory.toAbsolutePath().normalize();
        if (!owner.startsWith(root.directory) || !normalized.startsWith(root.directory) ||
                !normalized.getParent().equals(owner) || normalized.equals(root.junctionAlias) ||
                normalized.equals(root.junctionTarget))
            throw new IOException("CANARY_DIRECTORY_DELETE_CONFINEMENT");
        Files.delete(normalized); // exact empty directory only; never recursive
    }

    private static void expectCode(String expected,Action action) throws Exception {
        try {
            action.run();
        } catch (ReceiptContentBinding.BindingFailure failure) {
            if (expected.equals(failure.code)) { assertions++; return; }
            throw new AssertionError("EXPECTED_"+expected+"_GOT_"+failure.code,failure);
        }
        throw new AssertionError("MISSING_"+expected);
    }

    private static void check(boolean condition,String code) {
        assertions++;
        if (!condition) throw new AssertionError(code);
    }

    private static final class FixtureRoot {
        final Path directory,junctionAlias,junctionTarget;

        private FixtureRoot(Path directory,Path junctionAlias,Path junctionTarget) {
            this.directory=directory; this.junctionAlias=junctionAlias;
            this.junctionTarget=junctionTarget;
        }

        static FixtureRoot fromArgs(String[] args) throws Exception {
            if (args==null || args.length!=2) throw new IllegalArgumentException("CANARY_ARGUMENTS");
            Path directory=Paths.get(args[0]).toAbsolutePath().normalize();
            Path alias=Paths.get(args[1]).toAbsolutePath().normalize();
            Path expectedAlias=directory.resolve("junction-alias").normalize();
            Path target=directory.resolve("junction-target").normalize();
            if (!directory.equals(Paths.get(args[0])) || !directory.toRealPath().equals(directory) ||
                    !alias.equals(Paths.get(args[1])) || !alias.equals(expectedAlias) ||
                    !Files.exists(alias,LinkOption.NOFOLLOW_LINKS) ||
                    !Files.isDirectory(alias) || !Files.isDirectory(target,LinkOption.NOFOLLOW_LINKS) ||
                    !alias.toRealPath().equals(target.toRealPath()))
                throw new IllegalArgumentException("CANARY_ROOT_OR_JUNCTION");
            new CohortTerminalTelemetryStudy.R5FileIdentity(directory,true);
            new CohortTerminalTelemetryStudy.R5FileIdentity(target,true);
            return new FixtureRoot(directory,alias,target);
        }

        Path newDirectory(String label) throws Exception {
            Path child=directory.resolve(label+"-"+UUID.randomUUID().toString())
                .toAbsolutePath().normalize();
            if (!child.getParent().equals(directory) || !child.startsWith(directory))
                throw new IOException("CANARY_CASE_CONFINEMENT");
            Files.createDirectory(child);
            return child;
        }

        CaseFile newCase(String label,byte[] bytes) throws Exception {
            Path child=newDirectory(label);
            Path receipt=child.resolve(RECEIPT_NAME).toAbsolutePath().normalize();
            writeNewReceipt(child,receipt,bytes);
            return new CaseFile(child,receipt,bytes);
        }

        Path newJunctionTargetCase(String label) throws Exception {
            Path child=junctionTarget.resolve(label+"-"+UUID.randomUUID().toString())
                .toAbsolutePath().normalize();
            if (!child.getParent().equals(junctionTarget) || !child.startsWith(directory) ||
                    child.equals(junctionAlias))
                throw new IOException("CANARY_JUNCTION_CASE_CONFINEMENT");
            Files.createDirectory(child);
            return child;
        }
    }

    private static final class CaseFile {
        final Path directory,receipt;
        final byte[] bytes;
        CaseFile(Path directory,Path receipt,byte[] bytes) {
            this.directory=directory; this.receipt=receipt; this.bytes=bytes;
        }
    }

    private static final class MeterFixture {
        final long started;
        final CohortTerminalTelemetryStudy.Parent parent;
        final CohortTerminalTelemetryStudy.SetupMeter setup;

        MeterFixture() throws Exception {
            this(System.nanoTime());
        }

        MeterFixture(long configuredStarted) throws Exception {
            started=configuredStarted;
            parent=new CohortTerminalTelemetryStudy.Parent();
            setup=new CohortTerminalTelemetryStudy.SetupMeter(parent,parent.meter(started));
        }
    }

    private static final class MeterState {
        final long units,copiedEntries,maxGroupNanos,groupUnits,started,groupStarted;
        final int groups,priorGroups,copyChunks,maxGroupUnits,copyAllowance,inFlightOwnerGroups;
        final int groupMeasuresSize,sessionConstructorEntries;
        final Object groupMeasures;
        final boolean open,ownerStarted,ownerActive,ownerAdopted;

        private MeterState(Object meter) throws Exception {
            units=number(meter,"units");
            groups=(int)number(meter,"groups");
            priorGroups=(int)number(meter,"priorGroups");
            copiedEntries=number(meter,"copiedEntries");
            copyChunks=(int)number(meter,"copyChunks");
            maxGroupNanos=number(meter,"maxGroupNanos");
            maxGroupUnits=(int)number(meter,"maxGroupUnits");
            copyAllowance=(int)number(meter,"copyAllowance");
            groupUnits=number(meter,"groupUnits");
            groupStarted=number(meter,"groupStarted");
            started=number(meter,"started");
            inFlightOwnerGroups=(int)number(meter,"inFlightOwnerGroups");
            sessionConstructorEntries=(int)number(meter,"sessionConstructorEntries");
            groupMeasures=CohortTerminalTelemetryStudy.field(meter,"groupMeasures");
            groupMeasuresSize=((List<?>)groupMeasures).size();
            open=flag(meter,"open");
            ownerStarted=flag(meter,"originalOwnerStarted");
            ownerActive=flag(meter,"originalOwnerActive");
            ownerAdopted=flag(meter,"originalOwnerAdopted");
        }

        static MeterState capture(Object meter) throws Exception { return new MeterState(meter); }

        boolean same(MeterState other) {
            return units==other.units && groups==other.groups && priorGroups==other.priorGroups &&
                copiedEntries==other.copiedEntries && copyChunks==other.copyChunks &&
                maxGroupNanos==other.maxGroupNanos && maxGroupUnits==other.maxGroupUnits &&
                copyAllowance==other.copyAllowance && groupUnits==other.groupUnits &&
                groupStarted==other.groupStarted && started==other.started &&
                inFlightOwnerGroups==other.inFlightOwnerGroups &&
                sessionConstructorEntries==other.sessionConstructorEntries &&
                groupMeasures==other.groupMeasures && groupMeasuresSize==other.groupMeasuresSize &&
                open==other.open && ownerStarted==other.ownerStarted &&
                ownerActive==other.ownerActive && ownerAdopted==other.ownerAdopted;
        }

        private static long number(Object meter,String name) throws Exception {
            return ((Number)CohortTerminalTelemetryStudy.field(meter,name)).longValue();
        }
        private static boolean flag(Object meter,String name) throws Exception {
            return Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(meter,name));
        }
    }
}
