package com.lushprojects.circuitjs1.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Focused native R7 guards. Every Meter used here is a real Meter created by
 * Parent.meter; no Session or producer run is constructed. All maps and files
 * are synthetic guard fixtures, not reference evidence. The failure-only mode
 * sets constructorEntries=2 solely as a synthetic scalar for export validation.
 *
 * Normal invocation: java ...GenericR7NativeGuard <existing-empty-canonical-scratch-dir>
 * <caller-owned-prepared-junction>. The junction is used read-only for negative
 * path checks and is never added to Scratch ownership or cleanup. Or run
 * --after-publication-failure <existing-empty-canonical-scratch-dir> as a
 * separate child to capture the real R5 marker and expected nonzero post-check.
 * The caller owns that directory. The normal mode records each scratch artifact
 * and revalidates its identity before nonrecursive cleanup after all guards pass.
 * Failed fixtures stay in place for inspection; the failure mode retains its
 * synthetic result file for the caller's rejection check.
 */
public final class GenericR7NativeGuard {
    private static int passed;
    private static final String RUN_ID="12345678-1234-4abc-8def-1234567890ab";
    private static final String MANIFEST=
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private GenericR7NativeGuard() { }

    private interface CheckedCall { void call() throws Exception; }

    private static final class OwnedPath {
        final Path path;
        final boolean directory,symlink;
        final CohortTerminalTelemetryStudy.R5FileIdentity identity;
        final Object fileKey;
        final java.nio.file.attribute.FileTime created;
        OwnedPath(Path path,boolean directory,boolean symlink,
                CohortTerminalTelemetryStudy.R5FileIdentity identity,
                Object fileKey,java.nio.file.attribute.FileTime created) {
            this.path=path; this.directory=directory; this.symlink=symlink;
            this.identity=identity; this.fileKey=fileKey; this.created=created;
        }
    }

    private static final class Scratch {
        final Path root;
        final CohortTerminalTelemetryStudy.R5FileIdentity rootIdentity;
        final List<OwnedPath> owned=new ArrayList<OwnedPath>();
        Scratch(Path root) throws Exception {
            this.root=root;
            rootIdentity=new CohortTerminalTelemetryStudy.R5FileIdentity(root,true);
        }
        Path dir(String name) throws Exception {
            Path path=root.resolve(name);
            require(path.normalize().equals(path) && path.getParent().equals(root),
                "GUARD_CHILD_PATH");
            Files.createDirectory(path);
            track(path);
            return path;
        }
        Path track(Path path) throws Exception {
            Path normalized=path.toAbsolutePath().normalize();
            require(normalized.startsWith(root) && !normalized.equals(root),
                "GUARD_TRACK_CONTAINMENT");
            verifyParents(normalized);
            java.nio.file.attribute.BasicFileAttributes attrs=Files.readAttributes(
                normalized,java.nio.file.attribute.BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
            boolean symlink=attrs.isSymbolicLink();
            boolean directory=attrs.isDirectory();
            CohortTerminalTelemetryStudy.R5FileIdentity identity=null;
            if (!symlink) identity=new CohortTerminalTelemetryStudy.R5FileIdentity(
                normalized,directory);
            require(!symlink || attrs.fileKey()!=null,"GUARD_SYMLINK_IDENTITY_UNAVAILABLE");
            forget(normalized);
            owned.add(new OwnedPath(normalized,directory,symlink,identity,
                attrs.fileKey(),attrs.creationTime()));
            return normalized;
        }
        void forget(Path path) {
            Path normalized=path.toAbsolutePath().normalize();
            for (int i=owned.size()-1;i>=0;i--)
                if (owned.get(i).path.equals(normalized)) owned.remove(i);
        }
        void relocate(Path from,Path to) throws Exception {
            Path source=from.toAbsolutePath().normalize();
            Path target=to.toAbsolutePath().normalize();
            require(source.startsWith(root) && !source.equals(root) &&
                target.startsWith(root) && !target.equals(root),"GUARD_MOVE_CONTAINMENT");
            List<Path> moved=new ArrayList<Path>();
            for (int i=owned.size()-1;i>=0;i--) {
                OwnedPath item=owned.get(i);
                if (item.path.startsWith(source)) {
                    moved.add(source.relativize(item.path));
                    owned.remove(i);
                }
            }
            require(!moved.isEmpty(),"GUARD_MOVE_UNOWNED_PATH");
            for (int i=moved.size()-1;i>=0;i--) track(target.resolve(moved.get(i)));
        }
        private void verifyParents(Path path) throws Exception {
            require(path.isAbsolute() && path.normalize().equals(path) &&
                path.startsWith(root) && !path.equals(root),"GUARD_TRACK_CONTAINMENT");
            rootIdentity.verify(root,true,false);
            Path parent=path.getParent();
            while (parent!=null && parent.startsWith(root)) {
                new CohortTerminalTelemetryStudy.R5FileIdentity(parent,true);
                if (parent.equals(root)) return;
                parent=parent.getParent();
            }
            throw new IOException("GUARD_TRACK_PARENT_ESCAPE");
        }
        private void verifyOwned(OwnedPath item) throws Exception {
            Path path=item.path;
            require(path.toAbsolutePath().normalize().equals(path) &&
                path.startsWith(root) && !path.equals(root),
                "GUARD_CLEANUP_CONTAINMENT");
            rootIdentity.verify(root,true,false);
            if (!Files.exists(path,LinkOption.NOFOLLOW_LINKS)) return;
            verifyParents(path);
            java.nio.file.attribute.BasicFileAttributes attrs=Files.readAttributes(
                path,java.nio.file.attribute.BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
            if (item.symlink) {
                require(attrs.isSymbolicLink() && item.fileKey!=null &&
                    item.fileKey.equals(attrs.fileKey()) &&
                    item.created.equals(attrs.creationTime()),
                    "GUARD_CLEANUP_REPARSE_REPLACED");
            } else {
                require(!attrs.isSymbolicLink() && item.identity!=null,
                    "GUARD_CLEANUP_REPARSE_UNEXPECTED");
                item.identity.verify(path,item.directory,false);
            }
        }
        Path write(Path dir,String name,byte[] bytes) throws Exception {
            Path path=dir.resolve(name);
            try (java.io.OutputStream out=Files.newOutputStream(path,
                    StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS)) {
                out.write(bytes);
            }
            track(path);
            return path;
        }
        void clean() throws Exception {
            for (int i=owned.size()-1;i>=0;i--) verifyOwned(owned.get(i));
            for (int i=owned.size()-1;i>=0;i--) {
                OwnedPath item=owned.get(i);
                verifyOwned(item); // recheck immediately before the nonrecursive unlink
                if (Files.exists(item.path,LinkOption.NOFOLLOW_LINKS)) Files.delete(item.path);
            }
            rootIdentity.verify(root,true,false);
            try (DirectoryStream<Path> entries=Files.newDirectoryStream(root)) {
                require(!entries.iterator().hasNext(),"GUARD_SCRATCH_NOT_EMPTY_AFTER_CLEANUP");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args!=null && args.length==2 &&
                "--after-publication-failure".equals(args[0])) {
            runAfterPublicationFailure(validateScratch(args[1]));
            throw new IOException("GUARD_AFTER_PUBLICATION_MODE_RETURNED");
        }
        if (args==null || args.length!=2)
            throw new IOException("GUARD_ARGS_EXPECT_SCRATCH_AND_PREPARED_JUNCTION");
        Path root=validateScratch(args[0]);
        Path junction=validatePreparedJunction(args[1],root);
        Scratch scratch=new Scratch(root);
        long started=System.nanoTime();
        runParserGuards(scratch,junction);
        CohortTerminalTelemetryStudy.Parent parent=
            new CohortTerminalTelemetryStudy.Parent();
        runMeterAndSnapshotGuards(parent,started);
        runMeterLimitGuards(parent);
        runWorkPlanGuards(parent,started);
        runOutputAndIdentityGuards(scratch,junction,parent,started);
        runGeometryGuards(scratch,parent,started);
        runPublicationTokenGuards(parent);
        scratch.clean();
        System.out.println("GENERIC_R7_NATIVE_GUARD PASS cases="+passed+
            " syntheticZeroSession=true producerRuns=0 sessionConstructions=0 "+
            "syntheticPathTree=true actualReferences=NONE hardData=UNREAD");
    }

    private static Path validateScratch(String raw) throws Exception {
        if (raw==null || raw.length()==0 || raw.indexOf('\0')>=0)
            throw new IOException("GUARD_SCRATCH_PATH");
        Path root;
        try { root=Paths.get(raw); }
        catch (RuntimeException invalid) {
            throw new IOException("GUARD_SCRATCH_PATH",invalid);
        }
        if (!root.isAbsolute() || !root.normalize().equals(root) ||
                Files.isSymbolicLink(root) || !root.toRealPath().equals(root))
            throw new IOException("GUARD_SCRATCH_PATH");
        for (Path parent=root;parent!=null;parent=parent.getParent())
            new CohortTerminalTelemetryStudy.R5FileIdentity(parent,true);
        try (DirectoryStream<Path> entries=Files.newDirectoryStream(root)) {
            if (entries.iterator().hasNext()) throw new IOException("GUARD_SCRATCH_NOT_EMPTY");
        }
        return root;
    }

    private static Path validatePreparedJunction(String raw,Path scratchRoot)
            throws Exception {
        if (raw==null || raw.length()==0 || raw.indexOf('\0')>=0)
            throw new IOException("GUARD_PREPARED_JUNCTION_PATH");
        Path junction;
        try { junction=Paths.get(raw); }
        catch (RuntimeException invalid) {
            throw new IOException("GUARD_PREPARED_JUNCTION_PATH",invalid);
        }
        Path fixtureRoot=scratchRoot.getParent();
        if (!junction.isAbsolute() || !junction.normalize().equals(junction) ||
                !raw.equals(junction.toString()) || fixtureRoot==null ||
                !junction.startsWith(fixtureRoot) || junction.equals(fixtureRoot) ||
                junction.equals(scratchRoot) || junction.startsWith(scratchRoot) ||
                !Files.exists(junction,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("GUARD_PREPARED_JUNCTION_PATH");
        return junction;
    }

    /**
     * Separate child mode. It uses exportR5 with a synthetic body and an actual
     * zero-Session Meter, captures the serializer-created marker, mutates the
     * Meter only from the guard-owned stream flush, then rethrows the actual
     * afterPublication failure. Its body/marker must never be accepted as
     * natural-reference evidence.
     */
    private static void runAfterPublicationFailure(Path root) throws Exception {
        Path output=root.resolve("after-publication-output");
        if (Files.exists(output,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("GUARD_AFTER_PUBLICATION_OUTPUT_EXISTS");
        Files.createDirectory(output);
        CohortTerminalTelemetryStudy.GenericR7Arguments args=
            CohortTerminalTelemetryStudy.GenericR7Arguments.parse(arguments(output));

        long started=System.nanoTime();
        CohortTerminalTelemetryStudy.Parent parent=
            new CohortTerminalTelemetryStudy.Parent();
        CohortTerminalTelemetryStudy.SetupMeter meter=meter(parent,started);
        ensureOpen(meter);
        meter.charge(1);
        CohortTerminalTelemetryStudy.R5Snapshot pre=
            CohortTerminalTelemetryStudy.allocateR5Snapshot(meter);
        /* Synthetic zero-Session scalar required by the export work validator.
         * It is deliberately not an observed Session constructor entry. */
        set(meter.meter,"sessionConstructorEntries",Integer.valueOf(2));
        meter.finish();
        pre.captureClosed(meter,started);

        Map<String,Object> work=syntheticExportWork(meter,pre);
        Map<String,Object> body=map();
        body.put("guard","SYNTHETIC_ZERO_SESSION_AFTER_MARKER");
        body.put("sessionConstructorCounterMeaning",
            "synthetic scalar guard only; no Session was constructed");
        body.put("work",work);
        SyntheticExportFinalizer finalizer=
            new SyntheticExportFinalizer(meter,pre,body,work,started);

        ByteArrayOutputStream captured=new ByteArrayOutputStream();
        PrintStream original=System.out;
        SyntheticAfterMarkerPrintStream guarded=
            new SyntheticAfterMarkerPrintStream(captured,meter.meter);
        Throwable failure=null;
        try {
            System.setOut(guarded);
            CohortTerminalTelemetryStudy.exportR5(args,body,meter,started,pre,finalizer);
        } catch (Throwable caught) {
            failure=unwrap(caught);
        } finally {
            System.setOut(original);
        }
        if (failure==null || !guarded.counterChanged ||
                !"R5_METER_CHANGED".equals(failure.getMessage()))
            throw new IOException("GUARD_AFTER_PUBLICATION_FAILURE_NOT_OBSERVED",
                failure);
        byte[] marker=captured.toByteArray();
        validateCapturedMarker(marker);
        original.write(marker,0,marker.length);
        original.flush();
        if (failure instanceof Exception) throw (Exception)failure;
        if (failure instanceof Error) throw (Error)failure;
        throw new IOException("GUARD_AFTER_PUBLICATION_FAILURE_TYPE",failure);
    }

    private static Map<String,Object> syntheticExportWork(
            CohortTerminalTelemetryStudy.SetupMeter meter,
            CohortTerminalTelemetryStudy.R5Snapshot pre) throws Exception {
        long units=number(pre,"units"),groups=number(pre,"groups");
        long copies=number(pre,"copies"),chunks=number(pre,"chunks");
        Map<String,Object> setup=phase(units,groups,copies,chunks);
        Map<String,Object> open=phase(0,0,0,0);
        Map<String,Object> blocked=phase(0,0,0,0);
        Map<String,Object> metadata=phase(0,0,0,0);
        Map<String,Object> preExport=map();
        preExport.put("units",Long.valueOf(units));
        preExport.put("groups",Long.valueOf(groups));
        preExport.put("copiedEntries",Long.valueOf(copies));
        preExport.put("copyChunks",Long.valueOf(chunks));
        preExport.put("maxGroupNanos",Long.valueOf(number(pre,"maxNanos")));
        preExport.put("maximumUnitsPerGroup",Long.valueOf(number(pre,"maxUnits")));
        preExport.put("sessionConstructorEntries",
            Long.valueOf(number(pre,"constructorEntries")));
        preExport.put("elapsedNanos",Long.valueOf(1));
        Map<String,Object> limits=map();
        limits.put("producerNanos",Long.valueOf(90000000000L));
        limits.put("maximumGroups",Long.valueOf(640));
        limits.put("maximumGroupNanos",Long.valueOf(5000000000L));
        limits.put("maximumUnitsPerGroup",Long.valueOf(32768));
        limits.put("maximumCopiedEntries",Long.valueOf(1048576));
        limits.put("copyChunkSize",Long.valueOf(4096));
        Map<String,Object> work=map();
        work.put("setup",setup); work.put("open",open); work.put("blocked",blocked);
        work.put("metadata",metadata); work.put("preExport",preExport);
        work.put("limits",limits);
        require(number(meter.meter,"sessionConstructorEntries")==2,
            "GUARD_SYNTHETIC_CONSTRUCTOR_SCALAR");
        return work;
    }

    private static Map<String,Object> phase(long units,long groups,long copies,long chunks) {
        Map<String,Object> result=map();
        result.put("units",Long.valueOf(units));
        result.put("groups",Long.valueOf(groups));
        result.put("copiedEntries",Long.valueOf(copies));
        result.put("copyChunks",Long.valueOf(chunks));
        return result;
    }

    private static final class SyntheticExportFinalizer
            implements CohortTerminalTelemetryStudy.R5WorkFinalizer {
        final CohortTerminalTelemetryStudy.SetupMeter meter;
        final CohortTerminalTelemetryStudy.R5Snapshot pre;
        final Map<String,Object> body,work,setup,open,blocked,metadata,preExport,limits;
        final long started;
        boolean finalized;
        SyntheticExportFinalizer(CohortTerminalTelemetryStudy.SetupMeter m,
                CohortTerminalTelemetryStudy.R5Snapshot p,Map<String,Object> b,
                Map<String,Object> w,long s) {
            meter=m; pre=p; body=b; work=w;
            setup=(Map<String,Object>)w.get("setup");
            open=(Map<String,Object>)w.get("open");
            blocked=(Map<String,Object>)w.get("blocked");
            metadata=(Map<String,Object>)w.get("metadata");
            preExport=(Map<String,Object>)w.get("preExport");
            limits=(Map<String,Object>)w.get("limits");
            started=s;
        }
        public void verifyBound(CohortTerminalTelemetryStudy.SetupMeter m,
                CohortTerminalTelemetryStudy.R5Snapshot p,Map<?,?> b,Map<?,?> w)
                throws Exception {
            require(!finalized && m==meter && p==pre && b==body && w==work &&
                b.get("work")==work && work.get("setup")==setup && work.get("open")==open &&
                work.get("blocked")==blocked && work.get("metadata")==metadata &&
                work.get("preExport")==preExport && work.get("limits")==limits,
                "GUARD_SYNTHETIC_FINALIZER_BINDING");
        }
        public void finalizeWork(long exportStartNanos) throws Exception {
            if (finalized) throw new IOException("GUARD_SYNTHETIC_FINALIZER_REUSED");
            finalized=true;
            long cutoff=exportStartNanos-started;
            if (cutoff<=0 || cutoff>90000000000L)
                throw new IOException("GUARD_SYNTHETIC_FINALIZER_CUTOFF");
            preExport.put("elapsedNanos",Long.valueOf(cutoff));
        }
    }

    private static final class SyntheticAfterMarkerPrintStream extends PrintStream {
        final ByteArrayOutputStream captured;
        final Object meter;
        boolean counterChanged;
        SyntheticAfterMarkerPrintStream(ByteArrayOutputStream output,Object actualMeter)
                throws Exception {
            super(output,false,StandardCharsets.US_ASCII.name());
            captured=output; meter=actualMeter;
        }
        @Override public void flush() {
            super.flush();
            if (!counterChanged && captured.size()>0) {
                try {
                    long copied=number(meter,"copiedEntries");
                    set(meter,"copiedEntries",Long.valueOf(copied+1));
                    counterChanged=true;
                } catch (Exception failure) {
                    throw new IllegalStateException(
                        "GUARD_AFTER_PUBLICATION_INJECTION_FAILED",failure);
                }
            }
        }
    }

    private static void validateCapturedMarker(byte[] bytes) throws Exception {
        require(bytes!=null && bytes.length>1 && bytes.length<=666 &&
            bytes[bytes.length-1]==10 && bytes[bytes.length-2]=='}',
            "GUARD_CAPTURED_MARKER_LENGTH");
        for (int i=0;i<bytes.length-1;i++)
            require((bytes[i]&255)>=32 && (bytes[i]&255)<=126,
                "GUARD_CAPTURED_MARKER_ASCII");
        String line=new String(bytes,StandardCharsets.US_ASCII);
        require(line.indexOf('\n')==line.length()-1 && line.indexOf('\r')<0,
            "GUARD_CAPTURED_MARKER_LINE");
        String json=line.substring(0,line.length()-1);
        require(json.length()>=2 && json.charAt(0)=='{' &&
            json.charAt(json.length()-1)=='}',"GUARD_CAPTURED_MARKER_OBJECT");
        String[] keys={
            "schema","status","runId","sourceManifestSha256","wireContractSha256",
            "fixtureOracleSha256","resultSha256","resultBytes","elapsedNanos",
            "finalExportNanos","totalGroups","totalUnits","maxGroupNanos",
            "maximumUnitsPerGroup"
        };
        int cursor=1;
        for (int i=0;i<keys.length;i++) {
            if (i>0) {
                require(cursor<json.length() && json.charAt(cursor)==',',
                    "GUARD_CAPTURED_MARKER_FIELD_COUNT");
                cursor++;
            }
            String key="\""+keys[i]+"\":";
            require(json.startsWith(key,cursor),"GUARD_CAPTURED_MARKER_KEYS");
            cursor+=key.length();
            int valueEnd=json.indexOf(i==keys.length-1?'}':',',cursor);
            require(valueEnd>cursor,"GUARD_CAPTURED_MARKER_VALUE");
            cursor=valueEnd;
        }
        require(cursor==json.length()-1 &&
            json.contains("\"schema\":\"tsj-generic-routing-r7-marker-v1\"") &&
            json.contains("\"status\":\"GENERIC_NATURAL_R7_REFERENCE_EXPORTED\""),
            "GUARD_CAPTURED_MARKER_STATUS_OR_SHAPE");
    }
    private static void runParserGuards(final Scratch scratch,
            final Path preparedJunction) throws Exception {
        final Path valid=scratch.dir("arguments-valid");
        final String[] good=arguments(valid);
        CohortTerminalTelemetryStudy.GenericR7Arguments parsed=
            CohortTerminalTelemetryStudy.GenericR7Arguments.parse(good);
        require(parsed.outputDirectory.equals(valid),"GUARD_PARSE_VALID_PATH");
        passed++;

        expectCode("parser-old-mode","GENERIC_R7_ARGUMENTS",new CheckedCall() {
            public void call() throws Exception {
                String[] bad=good.clone(); bad[0]="GENERIC_ROUTING_REFERENCE_V1";
                CohortTerminalTelemetryStudy.GenericR7Arguments.parse(bad);
            }
        });
        expectCode("parser-four-args","GENERIC_R7_ARGUMENTS",new CheckedCall() {
            public void call() throws Exception {
                CohortTerminalTelemetryStudy.GenericR7Arguments.parse(
                    new String[]{good[0],good[1],good[2],good[3]});
            }
        });
        expectCode("parser-five-args","GENERIC_R7_ARGUMENTS",new CheckedCall() {
            public void call() throws Exception {
                CohortTerminalTelemetryStudy.GenericR7Arguments.parse(
                    new String[]{good[0],good[1],good[2],good[3],good[4]});
            }
        });
        expectCode("parser-seven-args","GENERIC_R7_ARGUMENTS",new CheckedCall() {
            public void call() throws Exception {
                CohortTerminalTelemetryStudy.GenericR7Arguments.parse(
                    new String[]{good[0],good[1],good[2],good[3],good[4],good[5],"extra"});
            }
        });
        expectCode("parser-uuid","GENERIC_R7_ARGUMENTS",new CheckedCall() {
            public void call() throws Exception {
                String[] bad=good.clone(); bad[1]="not-a-uuid";
                CohortTerminalTelemetryStudy.GenericR7Arguments.parse(bad);
            }
        });
        expectCode("parser-manifest-sha","GENERIC_R7_ARGUMENTS",new CheckedCall() {
            public void call() throws Exception {
                String[] bad=good.clone(); bad[2]="0";
                CohortTerminalTelemetryStudy.GenericR7Arguments.parse(bad);
            }
        });
        expectCode("parser-wire-sha","GENERIC_R7_ARGUMENTS",new CheckedCall() {
            public void call() throws Exception {
                String[] bad=good.clone(); bad[3]=MANIFEST;
                CohortTerminalTelemetryStudy.GenericR7Arguments.parse(bad);
            }
        });
        expectCode("parser-oracle-sha","GENERIC_R7_ARGUMENTS",new CheckedCall() {
            public void call() throws Exception {
                String[] bad=good.clone(); bad[4]=MANIFEST;
                CohortTerminalTelemetryStudy.GenericR7Arguments.parse(bad);
            }
        });
        expectCode("parser-path-alias-dot","GENERIC_R7_OUTPUT_PATH",new CheckedCall() {
            public void call() throws Exception {
                String[] bad=good.clone(); bad[5]=good[5]+"\\.";
                CohortTerminalTelemetryStudy.GenericR7Arguments.parse(bad);
            }
        });
        expectCode("parser-path-alias-forward-slash","GENERIC_R7_OUTPUT_PATH",
            new CheckedCall() {
                public void call() throws Exception {
                    String[] bad=good.clone(); bad[5]=good[5].replace('\\','/');
                    CohortTerminalTelemetryStudy.GenericR7Arguments.parse(bad);
                }
            });
        final Path occupied=scratch.dir("arguments-occupied");
        scratch.write(occupied,"extra.bin",ascii("synthetic\n"));
        expectCode("parser-occupied-output","GENERIC_R7_OUTPUT_NOT_EMPTY",
            new CheckedCall() {
                public void call() throws Exception {
                    CohortTerminalTelemetryStudy.GenericR7Arguments.parse(
                        arguments(occupied));
                }
            });

        expectCode("parser-reparse-output","GENERIC_R7_OUTPUT_PATH",new CheckedCall() {
            public void call() throws Exception {
                CohortTerminalTelemetryStudy.GenericR7Arguments.parse(
                    arguments(preparedJunction));
            }
        });
        expectCode("study-entry-old-mode","GENERIC_ROUTING_R7_REFERENCE_ONLY",
            new CheckedCall() {
                public void call() throws Exception {
                    CohortTerminalTelemetryStudy.main(new String[]{
                        "GENERIC_ROUTING_REFERENCE_V1",RUN_ID,MANIFEST,
                        CohortTerminalTelemetryStudy.GenericR7Arguments.WIRE,
                        CohortTerminalTelemetryStudy.GenericR7Arguments.ORACLE,
                        valid.toString()});
                }
            });
        expectCode("study-entry-path-alias","GENERIC_R7_OUTPUT_PATH",new CheckedCall() {
            public void call() throws Exception {
                String[] bad=good.clone(); bad[5]=good[5]+"\\.";
                CohortTerminalTelemetryStudy.main(bad);
            }
        });
        expectCode("study-entry-old-arity","GENERIC_ROUTING_R7_REFERENCE_ONLY",
            new CheckedCall() {
                public void call() throws Exception {
                    CohortTerminalTelemetryStudy.main(new String[]{
                        "GENERIC_ROUTING_R7_REFERENCE_V1",RUN_ID,MANIFEST,
                        CohortTerminalTelemetryStudy.GenericR7Arguments.WIRE});
                }
            });
        passed++;
    }

    private static String[] arguments(Path output) {
        return new String[]{
            CohortTerminalTelemetryStudy.GenericR7Arguments.MODE,
            RUN_ID,MANIFEST,
            CohortTerminalTelemetryStudy.GenericR7Arguments.WIRE,
            CohortTerminalTelemetryStudy.GenericR7Arguments.ORACLE,
            output.toString()
        };
    }

    private static void runMeterAndSnapshotGuards(
            CohortTerminalTelemetryStudy.Parent parent,final long started) throws Exception {
        final CohortTerminalTelemetryStudy.SetupMeter accepted=meter(parent,started);
        ensureOpen(accepted);
        accepted.charge(1);
        accepted.finish();
        require(!flag(accepted.meter,"open") &&
            !flag(accepted.meter,"originalOwnerStarted") &&
            !flag(accepted.meter,"originalOwnerActive") &&
            !flag(accepted.meter,"originalOwnerAdopted") &&
            number(accepted.meter,"priorGroups")==0 &&
            number(accepted.meter,"inFlightOwnerGroups")==0 &&
            number(accepted.meter,"sessionConstructorEntries")==0 &&
            number(accepted.meter,"groupUnits")>0,
            "GUARD_ZERO_SESSION_CLOSED_METER");
        final CohortTerminalTelemetryStudy.R7MeterPoint point=
            new CohortTerminalTelemetryStudy.R7MeterPoint(accepted.meter,started);
        point.capture(accepted);
        passed++;
        expectCode("point-reused","R7_POINT_BINDING",new CheckedCall() {
            public void call() throws Exception { point.capture(accepted); }
        });

        final CohortTerminalTelemetryStudy.SetupMeter foreign=meter(parent,started);
        ensureOpen(foreign);
        foreign.finish();
        final CohortTerminalTelemetryStudy.R7MeterPoint foreignPoint=
            new CohortTerminalTelemetryStudy.R7MeterPoint(accepted.meter,started);
        expectCode("point-foreign-meter","R7_POINT_BINDING",new CheckedCall() {
            public void call() throws Exception { foreignPoint.capture(foreign); }
        });

        final CohortTerminalTelemetryStudy.SetupMeter open=meter(parent,started);
        ensureOpen(open);
        final CohortTerminalTelemetryStudy.R7MeterPoint openPoint=
            new CohortTerminalTelemetryStudy.R7MeterPoint(open.meter,started);
        expectCode("point-open-group","R7_CLOSED_PHASE_POINT",new CheckedCall() {
            public void call() throws Exception { openPoint.capture(open); }
        });
        open.finish();

        final CohortTerminalTelemetryStudy.SetupMeter snapshotMeter=meter(parent,started);
        ensureOpen(snapshotMeter);
        final CohortTerminalTelemetryStudy.R5Snapshot snapshot=
            CohortTerminalTelemetryStudy.allocateR5Snapshot(snapshotMeter);
        snapshotMeter.finish();
        snapshot.captureClosed(snapshotMeter,started);
        passed++;
        final CohortTerminalTelemetryStudy.SetupMeter foreignSnapshotMeter=
            meter(parent,started);
        expectCode("snapshot-foreign-meter","R5_SNAPSHOT_NOT_READY",
            new CheckedCall() {
                public void call() throws Exception {
                    snapshot.verifyReady(foreignSnapshotMeter,started);
                }
            });
        foreignSnapshotMeter.finish();
        expectCode("snapshot-reused-capture","R5_SNAPSHOT_CAPTURE_STATE",
            new CheckedCall() {
                public void call() throws Exception {
                    snapshot.captureClosed(snapshotMeter,started);
                }
            });
        ensureOpen(snapshotMeter);
        snapshotMeter.charge(1);
        snapshotMeter.finish();
        expectCode("snapshot-stale-meter","R5_METER_CHANGED",new CheckedCall() {
            public void call() throws Exception { snapshot.verifyReady(snapshotMeter,started); }
        });
        require(number(accepted.meter,"sessionConstructorEntries")==0 &&
            number(snapshotMeter.meter,"sessionConstructorEntries")==0,
            "GUARD_NO_SESSION_CONSTRUCTION");
        passed++;
    }
    private static void runMeterLimitGuards(
            CohortTerminalTelemetryStudy.Parent parent) throws Exception {
        final long unitStarted=System.nanoTime();
        final CohortTerminalTelemetryStudy.SetupMeter unitMeter=meter(parent,unitStarted);
        ensureOpen(unitMeter);
        long groupUnits=number(unitMeter.meter,"groupUnits");
        require(groupUnits>=0 && groupUnits<32767,"GUARD_UNIT_BOUND_SETUP");
        unitMeter.charge(32767-groupUnits);
        CohortTerminalTelemetryStudy.exportCopyBlock(unitMeter,1);
        require(number(unitMeter.meter,"groupUnits")==32768,
            "GUARD_UNIT_CAP_ACCEPTED");
        final long copiesAtUnitCap=number(unitMeter.meter,"copiedEntries");
        final long chunksAtUnitCap=number(unitMeter.meter,"copyChunks");
        expectCode("export-after-unit-cap","EXPORT_COPY_CAPACITY",new CheckedCall() {
            public void call() throws Exception {
                CohortTerminalTelemetryStudy.exportCopyBlock(unitMeter,1);
            }
        });
        require(number(unitMeter.meter,"copiedEntries")==copiesAtUnitCap &&
            number(unitMeter.meter,"copyChunks")==chunksAtUnitCap,
            "GUARD_UNIT_CAP_NO_MUTATION");
        expectCode("export-zero-bytes","EXPORT_COPY_STATE",new CheckedCall() {
            public void call() throws Exception {
                CohortTerminalTelemetryStudy.exportCopyBlock(unitMeter,0);
            }
        });
        expectCode("export-over-chunk","EXPORT_COPY_STATE",new CheckedCall() {
            public void call() throws Exception {
                CohortTerminalTelemetryStudy.exportCopyBlock(unitMeter,4097);
            }
        });
        unitMeter.finish();

        final long copyStarted=System.nanoTime();
        final CohortTerminalTelemetryStudy.SetupMeter copyMeter=meter(parent,copyStarted);
        ensureOpen(copyMeter);
        require(number(copyMeter.meter,"copyAllowance")==1048576L,
            "GUARD_COPY_ALLOWANCE");
        for (int i=0;i<256;i++)
            CohortTerminalTelemetryStudy.exportCopyBlock(copyMeter,4096);
        require(number(copyMeter.meter,"copiedEntries")==1048576L &&
            number(copyMeter.meter,"copyChunks")==256,
            "GUARD_COPY_CAP_ACCEPTED");
        final long copiesAtLimit=number(copyMeter.meter,"copiedEntries");
        final long chunksAtLimit=number(copyMeter.meter,"copyChunks");
        expectCode("export-over-copy-cap","EXPORT_COPY_CAPACITY",new CheckedCall() {
            public void call() throws Exception {
                CohortTerminalTelemetryStudy.exportCopyBlock(copyMeter,1);
            }
        });
        require(number(copyMeter.meter,"copiedEntries")==copiesAtLimit &&
            number(copyMeter.meter,"copyChunks")==chunksAtLimit,
            "GUARD_COPY_CAP_NO_MUTATION");
        copyMeter.finish();

        final long groupStarted=System.nanoTime();
        final CohortTerminalTelemetryStudy.SetupMeter groups=meter(parent,groupStarted);
        for (int i=0;i<640;i++) {
            ensureOpen(groups);
            groups.charge(1);
            groups.finish();
        }
        require(number(groups.meter,"groups")==640 &&
            number(groups.meter,"sessionConstructorEntries")==0,
            "GUARD_GROUP_CAP_SETUP");
        final CohortTerminalTelemetryStudy.R7MeterPoint atGroupLimit=
            new CohortTerminalTelemetryStudy.R7MeterPoint(groups.meter,groupStarted);
        expectCode("phase-group-limit","R7_CLOSED_PHASE_POINT",new CheckedCall() {
            public void call() throws Exception { atGroupLimit.capture(groups); }
        });
        passed++;
    }
    private static void runWorkPlanGuards(
            CohortTerminalTelemetryStudy.Parent parent,long started) throws Exception {
        final CohortTerminalTelemetryStudy.SetupMeter meter=meter(parent,started);
        ensureOpen(meter);
        final CohortTerminalTelemetryStudy.R7MeterLedger ledger=
            CohortTerminalTelemetryStudy.R7MeterLedger.prepare(meter,started);
        final CohortTerminalTelemetryStudy.R5Snapshot snapshot=
            CohortTerminalTelemetryStudy.allocateR5Snapshot(meter);
        final Map<String,Object> work=map();
        final Map<String,Object> setup=map(),open=map(),blocked=map(),metadata=map(),
            preExport=map(),limits=map();
        preExport.put("elapsedNanos",Long.valueOf(1));
        work.put("setup",setup); work.put("open",open); work.put("blocked",blocked);
        work.put("metadata",metadata); work.put("preExport",preExport);
        work.put("limits",limits);
        final Map<String,Object> body=map();
        body.put("work",work);

        /* Deliberately synthetic lifecycle state: no Session or producer result. */
        Constructor<?> constructor=CohortTerminalTelemetryStudy.R7WorkPlan.class
            .getDeclaredConstructor(CohortTerminalTelemetryStudy.SetupMeter.class,
                CohortTerminalTelemetryStudy.R5Snapshot.class,
                CohortTerminalTelemetryStudy.R7MeterLedger.class,
                Map.class,Map.class,Map.class,Map.class,Map.class,Map.class,Map.class,
                Map.class,long.class);
        constructor.setAccessible(true);
        final CohortTerminalTelemetryStudy.R7WorkPlan plan=
            (CohortTerminalTelemetryStudy.R7WorkPlan)constructor.newInstance(
                meter,snapshot,ledger,work,setup,open,blocked,metadata,preExport,limits,
                body,Long.valueOf(started));
        set(plan,"frozen",Boolean.TRUE);
        plan.verifyBound(meter,snapshot,body,work);
        passed++;

        final CohortTerminalTelemetryStudy.SetupMeter otherMeter=meter(parent,started);
        ensureOpen(otherMeter);
        final CohortTerminalTelemetryStudy.R5Snapshot otherSnapshot=
            CohortTerminalTelemetryStudy.allocateR5Snapshot(otherMeter);
        final Map<String,Object> otherBody=map();
        otherBody.put("work",work);
        final Map<String,Object> otherWork=map();
        expectCode("plan-foreign-meter","R7_FINALIZER_BINDING",new CheckedCall() {
            public void call() throws Exception {
                plan.verifyBound(otherMeter,snapshot,body,work);
            }
        });
        expectCode("plan-foreign-snapshot","R7_FINALIZER_BINDING",new CheckedCall() {
            public void call() throws Exception {
                plan.verifyBound(meter,otherSnapshot,body,work);
            }
        });
        expectCode("plan-foreign-body","R7_FINALIZER_BINDING",new CheckedCall() {
            public void call() throws Exception {
                plan.verifyBound(meter,snapshot,otherBody,work);
            }
        });
        expectCode("plan-foreign-work","R7_FINALIZER_BINDING",new CheckedCall() {
            public void call() throws Exception {
                plan.verifyBound(meter,snapshot,body,otherWork);
            }
        });

        expectPlanChildMap("setup",plan,meter,snapshot,body,work);
        expectPlanChildMap("open",plan,meter,snapshot,body,work);
        expectPlanChildMap("blocked",plan,meter,snapshot,body,work);
        expectPlanChildMap("metadata",plan,meter,snapshot,body,work);
        expectPlanChildMap("preExport",plan,meter,snapshot,body,work);
        expectPlanChildMap("limits",plan,meter,snapshot,body,work);
        Object savedBodyWork=body.put("work",otherWork);
        expectCode("plan-replaced-body-work","R7_FINALIZER_BINDING",new CheckedCall() {
            public void call() throws Exception {
                plan.verifyBound(meter,snapshot,body,work);
            }
        });
        body.put("work",savedBodyWork);
        passed++;

        long exportStart=System.nanoTime();
        plan.finalizeWork(exportStart);
        Object elapsedSlot=preExport.get("elapsedNanos");
        require(elapsedSlot instanceof Long && ((Long)elapsedSlot).longValue()==exportStart-started &&
            body.get("work")==work && work.get("preExport")==preExport,
            "GUARD_FINALIZER_EXISTING_SLOT");
        expectCode("plan-finalizer-reused","R7_FINALIZER_REUSED",new CheckedCall() {
            public void call() throws Exception { plan.finalizeWork(System.nanoTime()); }
        });
        expectCode("plan-bound-after-finalize","R7_FINALIZER_BINDING",new CheckedCall() {
            public void call() throws Exception { plan.verifyBound(meter,snapshot,body,work); }
        });
        passed++;
        meter.finish();
        otherMeter.finish();
    }

    private static void expectPlanChildMap(final String child,
            final CohortTerminalTelemetryStudy.R7WorkPlan plan,
            final CohortTerminalTelemetryStudy.SetupMeter meter,
            final CohortTerminalTelemetryStudy.R5Snapshot snapshot,
            final Map<String,Object> body,final Map<String,Object> work) throws Exception {
        final Object saved=work.get(child);
        work.put(child,map());
        try {
            expectCode("plan-replaced-"+child,"R7_FINALIZER_BINDING",new CheckedCall() {
                public void call() throws Exception {
                    plan.verifyBound(meter,snapshot,body,work);
                }
            });
        } finally { work.put(child,saved); }
    }
    private static Path outputDir(Scratch scratch,String name) throws Exception {
        return scratch.dir(name);
    }

    private static void runOutputAndIdentityGuards(
            Scratch scratch,final Path preparedJunction,
            CohortTerminalTelemetryStudy.Parent parent,long started)
            throws Exception {
        Path empty=outputDir(scratch,"output-empty");
        final CohortTerminalTelemetryStudy.R5Output emptyOutput=
            new CohortTerminalTelemetryStudy.R5Output(empty);
        emptyOutput.closure(false,-1);
        passed++;

        Path extraDir=outputDir(scratch,"output-extra");
        final CohortTerminalTelemetryStudy.R5Output extraOutput=
            new CohortTerminalTelemetryStudy.R5Output(extraDir);
        scratch.write(extraDir,"unlisted.txt",ascii("synthetic\n"));
        expectCode("output-extra-file","R5_OUTPUT_EXTRA_FILE",new CheckedCall() {
            public void call() throws Exception { extraOutput.closure(false,-1); }
        });

        expectCode("output-reparse","R5_NONCANONICAL_PATH",new CheckedCall() {
            public void call() throws Exception {
                new CohortTerminalTelemetryStudy.R5Output(preparedJunction);
            }
        });

        Path direct=scratch.dir("create-new");
        Path created=direct.resolve("once.bin");
        try (java.io.OutputStream output=Files.newOutputStream(created,
                StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS)) {
            output.write(ascii("synthetic\n"));
        }
        scratch.track(created);
        expectFileAlreadyExists("create-new-rejects-reuse",created);

        final String body="{\"guard\":true}\n";
        byte[] bytes=ascii(body);
        Path writerDir=outputDir(scratch,"output-write-readback");
        final CohortTerminalTelemetryStudy.R5Output output=
            new CohortTerminalTelemetryStudy.R5Output(writerDir);
        CohortTerminalTelemetryStudy.SetupMeter writerMeter=meter(parent,started);
        ensureOpen(writerMeter);
        long exportStart=number(writerMeter.meter,"groupStarted");
        CohortTerminalTelemetryStudy.R5CountSink count=
            new CohortTerminalTelemetryStudy.R5CountSink(writerMeter,started,exportStart);
        writeAscii(body,count);
        count.complete(count.position);
        require(count.position==bytes.length,"GUARD_COUNT_LENGTH");
        try (CohortTerminalTelemetryStudy.R5WriteSink writer=
                new CohortTerminalTelemetryStudy.R5WriteSink(
                    writerMeter,started,exportStart,bytes.length,output)) {
            writeAscii(body,writer);
            writer.complete(bytes.length);
            require(writer.stream==null && writer.block==null,"GUARD_WRITE_CLOSED");
        }
        scratch.track(output.result);
        output.closure(true,bytes.length);
        require(output.resultIdentity!=null &&
            Files.size(output.result)==bytes.length,"GUARD_RESULT_IDENTITY");
        CohortTerminalTelemetryStudy.R5CompareSink compare=
            new CohortTerminalTelemetryStudy.R5CompareSink(
                writerMeter,started,exportStart,bytes.length,output);
        writeAscii(body,compare);
        compare.complete(bytes.length);
        byte[] readDigest=compare.digest.digest();
        byte[] actualDigest=MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(output.result));
        require(Arrays.equals(readDigest,actualDigest),"GUARD_READBACK_DIGEST");
        output.closure(true,bytes.length);
        expectFileAlreadyExists("result-create-new-rejects-reuse",output.result);
        writerMeter.finish();
        passed++;

        Path corruptDir=outputDir(scratch,"output-readback-corrupt");
        final CohortTerminalTelemetryStudy.R5Output corruptOutput=
            new CohortTerminalTelemetryStudy.R5Output(corruptDir);
        byte[] corrupt=bytes.clone();
        corrupt[0]=(byte)'[';
        Path corruptFile=scratch.write(corruptDir,"result.json",corrupt);
        corruptOutput.resultIdentity=
            new CohortTerminalTelemetryStudy.R5FileIdentity(corruptFile,false);
        CohortTerminalTelemetryStudy.SetupMeter compareMeter=meter(parent,started);
        ensureOpen(compareMeter);
        final CohortTerminalTelemetryStudy.R5CompareSink badCompare=
            new CohortTerminalTelemetryStudy.R5CompareSink(compareMeter,started,
                number(compareMeter.meter,"groupStarted"),bytes.length,corruptOutput);
        expectCode("readback-content-mismatch","R5_READBACK_MISMATCH",new CheckedCall() {
            public void call() throws Exception { writeAscii(body,badCompare); }
        });
        badCompare.close();
        compareMeter.finish();

        Path identityDir=scratch.dir("identity-replacement");
        final Path original=identityDir.resolve("result.bin");
        Path spareDir=scratch.dir("identity-spare");
        Path spare=spareDir.resolve("replacement.bin");
        byte[] same=ascii("stable synthetic bytes\n");
        scratch.write(identityDir,"result.bin",same);
        final CohortTerminalTelemetryStudy.R5FileIdentity originalIdentity=
            new CohortTerminalTelemetryStudy.R5FileIdentity(original,false);
        scratch.write(spareDir,"replacement.bin",same);
        Files.delete(original);
        scratch.forget(original);
        Files.move(spare,original);
        scratch.forget(spare);
        scratch.track(original);
        expectCode("file-identity-replacement","R5_FILE_REPLACED",new CheckedCall() {
            public void call() throws Exception {
                originalIdentity.verify(original,false,false);
            }
        });
        passed++;

        Path dirA=outputDir(scratch,"identity-output-dir");
        Path dirB=outputDir(scratch,"identity-output-replacement");
        final CohortTerminalTelemetryStudy.R5Output directoryIdentity=
            new CohortTerminalTelemetryStudy.R5Output(dirA);
        Path saved=scratch.root.resolve("identity-output-saved");
        Files.move(dirA,saved);
        scratch.relocate(dirA,saved);
        Files.move(dirB,dirA);
        scratch.relocate(dirB,dirA);
        expectCode("directory-identity-replacement","R5_FILE_REPLACED",
            new CheckedCall() {
                public void call() throws Exception {
                    directoryIdentity.closure(false,-1);
                }
            });
        passed++;
    }
    private static void runGeometryGuards(
            Scratch scratch,CohortTerminalTelemetryStudy.Parent parent,long started)
            throws Exception {
        Map<String,Object> valid=geometryTree(repeat('g',4096));
        CohortTerminalTelemetryStudy.SetupMeter policyMeter=meter(parent,started);
        ensureOpen(policyMeter);
        CohortTerminalTelemetryStudy.validateR9CanaryTextPolicy(valid,policyMeter);
        policyMeter.finish();
        passed++;

        final Map<String,Object> tooLong=geometryTree(repeat('g',4097));
        final CohortTerminalTelemetryStudy.SetupMeter longPolicyMeter=meter(parent,started);
        ensureOpen(longPolicyMeter);
        expectCode("geometry-policy-4097","R9_TEXT_POLICY_LENGTH",new CheckedCall() {
            public void call() throws Exception {
                CohortTerminalTelemetryStudy.validateR9CanaryTextPolicy(
                    tooLong,longPolicyMeter);
            }
        });
        longPolicyMeter.finish();

        final Map<String,Object> wrongType=geometryTree(Integer.valueOf(7));
        final CohortTerminalTelemetryStudy.SetupMeter typePolicyMeter=meter(parent,started);
        ensureOpen(typePolicyMeter);
        expectCode("geometry-policy-type","R9_TEXT_POLICY_STRUCTURE",
            new CheckedCall() {
                public void call() throws Exception {
                    CohortTerminalTelemetryStudy.validateR9CanaryTextPolicy(
                        wrongType,typePolicyMeter);
                }
            });
        typePolicyMeter.finish();

        final Map<String,Object> wrongPath=map();
        wrongPath.put("geometryCanonical",repeat('g',257));
        final CohortTerminalTelemetryStudy.SetupMeter ordinaryPolicyMeter=meter(parent,started);
        ensureOpen(ordinaryPolicyMeter);
        expectCode("geometry-ordinary-string-257","R9_TEXT_POLICY_LENGTH",
            new CheckedCall() {
                public void call() throws Exception {
                    CohortTerminalTelemetryStudy.validateR9CanaryTextPolicy(
                        wrongPath,ordinaryPolicyMeter);
                }
            });
        ordinaryPolicyMeter.finish();

        Path sinkDir=outputDir(scratch,"geometry-sink");
        final CohortTerminalTelemetryStudy.R5Output sinkOutput=
            new CohortTerminalTelemetryStudy.R5Output(sinkDir);
        CohortTerminalTelemetryStudy.SetupMeter countMeter=meter(parent,started);
        ensureOpen(countMeter);
        long countStart=number(countMeter.meter,"groupStarted");
        CohortTerminalTelemetryStudy.R5CountSink count=
            new CohortTerminalTelemetryStudy.R5CountSink(countMeter,started,countStart);
        CohortTerminalTelemetryStudy.writeCanonicalJsonCanaryValues(valid,count);
        count.complete(count.position);
        long length=count.position;
        require(length>4096 && length<=1048576,"GUARD_GEOMETRY_BODY_LENGTH");
        countMeter.finish();

        CohortTerminalTelemetryStudy.SetupMeter writeMeter=meter(parent,started);
        ensureOpen(writeMeter);
        long writeStart=number(writeMeter.meter,"groupStarted");
        try (CohortTerminalTelemetryStudy.R5WriteSink writer=
                new CohortTerminalTelemetryStudy.R5WriteSink(
                    writeMeter,started,writeStart,length,sinkOutput)) {
            CohortTerminalTelemetryStudy.writeCanonicalJsonCanaryValues(valid,writer);
            writer.complete(length);
        }
        scratch.track(sinkOutput.result);
        sinkOutput.closure(true,length);
        writeMeter.finish();

        CohortTerminalTelemetryStudy.SetupMeter readMeter=meter(parent,started);
        ensureOpen(readMeter);
        long readStart=number(readMeter.meter,"groupStarted");
        CohortTerminalTelemetryStudy.R5CompareSink reader=
            new CohortTerminalTelemetryStudy.R5CompareSink(
                readMeter,started,readStart,length,sinkOutput);
        CohortTerminalTelemetryStudy.writeCanonicalJsonCanaryValues(valid,reader);
        reader.complete(length);
        readMeter.finish();
        passed++;

        final CohortTerminalTelemetryStudy.SetupMeter invalidCountMeter=meter(parent,started);
        ensureOpen(invalidCountMeter);
        final CohortTerminalTelemetryStudy.R5CountSink invalidCount=
            new CohortTerminalTelemetryStudy.R5CountSink(invalidCountMeter,started,
                number(invalidCountMeter.meter,"groupStarted"));
        expectMessage("geometry-count-4097","JSON string length",new CheckedCall() {
            public void call() throws Exception {
                CohortTerminalTelemetryStudy.writeCanonicalJsonCanaryValues(
                    tooLong,invalidCount);
            }
        });
        invalidCountMeter.finish();

        final CohortTerminalTelemetryStudy.SetupMeter invalidWriteMeter=meter(parent,started);
        ensureOpen(invalidWriteMeter);
        final CohortTerminalTelemetryStudy.R5Output invalidWriteOutput=
            new CohortTerminalTelemetryStudy.R5Output(
                outputDir(scratch,"geometry-invalid-write"));
        final CohortTerminalTelemetryStudy.R5WriteSink invalidWriter=
            new CohortTerminalTelemetryStudy.R5WriteSink(invalidWriteMeter,started,
                number(invalidWriteMeter.meter,"groupStarted"),length,invalidWriteOutput);
        expectMessage("geometry-write-4097","JSON string length",new CheckedCall() {
            public void call() throws Exception {
                CohortTerminalTelemetryStudy.writeCanonicalJsonCanaryValues(
                    tooLong,invalidWriter);
            }
        });
        invalidWriter.close();
        invalidWriteOutput.closure(false,-1);
        invalidWriteMeter.finish();

        final CohortTerminalTelemetryStudy.SetupMeter invalidReadMeter=meter(parent,started);
        ensureOpen(invalidReadMeter);
        final CohortTerminalTelemetryStudy.R5CompareSink invalidReader=
            new CohortTerminalTelemetryStudy.R5CompareSink(invalidReadMeter,started,
                number(invalidReadMeter.meter,"groupStarted"),length,sinkOutput);
        expectMessage("geometry-read-4097","JSON string length",new CheckedCall() {
            public void call() throws Exception {
                CohortTerminalTelemetryStudy.writeCanonicalJsonCanaryValues(
                    tooLong,invalidReader);
            }
        });
        invalidReader.close();
        invalidReadMeter.finish();

        final CohortTerminalTelemetryStudy.SetupMeter wrongTypeCountMeter=meter(parent,started);
        ensureOpen(wrongTypeCountMeter);
        final CohortTerminalTelemetryStudy.R5CountSink wrongTypeCount=
            new CohortTerminalTelemetryStudy.R5CountSink(wrongTypeCountMeter,started,
                number(wrongTypeCountMeter.meter,"groupStarted"));
        expectMessage("geometry-count-type","JSON canary path type",new CheckedCall() {
            public void call() throws Exception {
                CohortTerminalTelemetryStudy.writeCanonicalJsonCanaryValues(
                    wrongType,wrongTypeCount);
            }
        });
        wrongTypeCountMeter.finish();

        final CohortTerminalTelemetryStudy.SetupMeter wrongTypeReadMeter=meter(parent,started);
        ensureOpen(wrongTypeReadMeter);
        final CohortTerminalTelemetryStudy.SetupMeter wrongTypeWriteMeter=meter(parent,started);
        ensureOpen(wrongTypeWriteMeter);
        final CohortTerminalTelemetryStudy.R5Output wrongTypeWriteOutput=
            new CohortTerminalTelemetryStudy.R5Output(
                outputDir(scratch,"geometry-invalid-type-write"));
        final CohortTerminalTelemetryStudy.R5WriteSink wrongTypeWriter=
            new CohortTerminalTelemetryStudy.R5WriteSink(wrongTypeWriteMeter,started,
                number(wrongTypeWriteMeter.meter,"groupStarted"),length,wrongTypeWriteOutput);
        expectMessage("geometry-write-type","JSON canary path type",new CheckedCall() {
            public void call() throws Exception {
                CohortTerminalTelemetryStudy.writeCanonicalJsonCanaryValues(
                    wrongType,wrongTypeWriter);
            }
        });
        wrongTypeWriter.close();
        wrongTypeWriteOutput.closure(false,-1);
        wrongTypeWriteMeter.finish();
        final CohortTerminalTelemetryStudy.R5CompareSink wrongTypeReader=
            new CohortTerminalTelemetryStudy.R5CompareSink(wrongTypeReadMeter,started,
                number(wrongTypeReadMeter.meter,"groupStarted"),length,sinkOutput);
        expectMessage("geometry-read-type","JSON canary path type",new CheckedCall() {
            public void call() throws Exception {
                CohortTerminalTelemetryStudy.writeCanonicalJsonCanaryValues(
                    wrongType,wrongTypeReader);
            }
        });
        wrongTypeReader.close();
        wrongTypeReadMeter.finish();
    }

    private static void runPublicationTokenGuards(
            CohortTerminalTelemetryStudy.Parent parent) throws Exception {
        final TokenFixture exact=tokenFixture(parent,7,1);
        expectCode("token-wrong-fee","R5_METER_CHANGED",new CheckedCall() {
            public void call() throws Exception { tokenConstruction(exact,exact.fee-1); }
        });
        final TokenFixture foreignMeter=tokenFixture(parent,7,1);
        final TokenFixture foreignSnapshot=tokenFixture(parent,7,1);
        expectCode("token-foreign-snapshot","R5_SNAPSHOT_NOT_READY",
            new CheckedCall() {
                public void call() throws Exception {
                    tokenConstruction(foreignMeter.meter,foreignSnapshot.snapshot,
                        foreignMeter.started,foreignMeter.exportStarted,foreignMeter.fee,
                        foreignMeter.resultBytes,foreignMeter.blocks,foreignMeter.measure,
                        foreignMeter.closedNanos);
                }
            });
        foreignMeter.meter.finish(); foreignSnapshot.meter.finish();
        final CohortTerminalTelemetryStudy.R5PublicationToken token=
            tokenConstruction(exact,exact.fee);
        token.admit(1);
        require(number(exact.meter.meter,"copiedEntries")==
                exact.preCopies+2*exact.resultBytes+32+1 &&
            number(exact.meter.meter,"copyChunks")==exact.preChunks+2*exact.blocks+2,
            "GUARD_TOKEN_ACTUAL_COPY_ADMISSION");
        token.afterPublication(1);
        expectCode("token-reused-admission","R5_PUBLICATION_TOKEN_USED",
            new CheckedCall() {
                public void call() throws Exception { token.admit(1); }
            });
        passed++;

        final TokenFixture capacity=tokenFixture(parent,7,1);
        final CohortTerminalTelemetryStudy.R5PublicationToken full=
            tokenConstruction(capacity,capacity.fee);
        expectCode("token-copy-capacity","R5_MARKER_COPY_CAPACITY",
            new CheckedCall() {
                public void call() throws Exception { full.admit(2); }
            });
        expectCode("token-capacity-failure-consumes-token","R5_PUBLICATION_TOKEN_USED",
            new CheckedCall() {
                public void call() throws Exception { full.admit(1); }
            });

        final TokenFixture post=tokenFixture(parent,7,2);
        final CohortTerminalTelemetryStudy.R5PublicationToken postToken=
            tokenConstruction(post,post.fee);
        postToken.admit(1); // synthetic post-marker boundary; no marker is emitted
        set(post.meter.meter,"copiedEntries",
            Long.valueOf(number(post.meter.meter,"copiedEntries")+1));
        expectCode("token-post-publication-counter","R5_METER_CHANGED",
            new CheckedCall() {
                public void call() throws Exception { postToken.afterPublication(1); }
            });
        passed++;
        require(number(exact.meter.meter,"sessionConstructorEntries")==0 &&
            number(capacity.meter.meter,"sessionConstructorEntries")==0 &&
            number(post.meter.meter,"sessionConstructorEntries")==0,
            "GUARD_TOKEN_ZERO_SESSION");
        exact.meter.finish(); capacity.meter.finish(); post.meter.finish();
    }

    private static final class TokenFixture {
        final CohortTerminalTelemetryStudy.SetupMeter meter;
        final CohortTerminalTelemetryStudy.R5Snapshot snapshot;
        final long started,exportStarted,fee,resultBytes,blocks,preCopies,preChunks;
        final Object measure;
        final long closedNanos;
        TokenFixture(CohortTerminalTelemetryStudy.SetupMeter m,
                CohortTerminalTelemetryStudy.R5Snapshot s,long st,long es,long f,
                long bytes,long blockCount,long pc,long pch,Object measured,long nanos) {
            meter=m; snapshot=s; started=st; exportStarted=es; fee=f; resultBytes=bytes;
            blocks=blockCount; preCopies=pc; preChunks=pch; measure=measured;
            closedNanos=nanos;
        }
    }

    private static TokenFixture tokenFixture(
            CohortTerminalTelemetryStudy.Parent parent,int bodyBytes,int markerHeadroom)
            throws Exception {
        long started=System.nanoTime();
        final CohortTerminalTelemetryStudy.SetupMeter meter=meter(parent,started);
        ensureOpen(meter);
        long allowance=number(meter.meter,"copyAllowance");
        long baseCopies=2L*bodyBytes+32L;
        long initial=allowance-baseCopies-markerHeadroom;
        /* Scalar capacity fixture: all increments pass through this Meter owner.
         * No reference bytes or producer-owned object are created. */
        require(initial>=0 && initial<=Integer.MAX_VALUE,"GUARD_TOKEN_COPY_SETUP");
        int remaining=(int)initial;
        while (remaining>0) {
            int next=Math.min(4096,remaining);
            meter.copy(next);
            remaining-=next;
        }
        final CohortTerminalTelemetryStudy.R5Snapshot snapshot=
            CohortTerminalTelemetryStudy.allocateR5Snapshot(meter);
        long preCopies=number(meter.meter,"copiedEntries");
        long preChunks=number(meter.meter,"copyChunks");
        meter.finish();
        snapshot.captureClosed(meter,started);

        meter.method("beginGroup",new Class<?>[0]);
        long exportStarted=number(meter.meter,"groupStarted");
        long blocks=(bodyBytes+4095L)/4096L;
        long fee=5L*blocks+3L;
        CohortTerminalTelemetryStudy.exportCopyBlock(meter,bodyBytes);
        CohortTerminalTelemetryStudy.exportCopyBlock(meter,bodyBytes);
        CohortTerminalTelemetryStudy.exportCopyBlock(meter,32);
        meter.charge(fee-3L);
        meter.finish();
        @SuppressWarnings("unchecked")
        List<Object> measures=(List<Object>)CohortTerminalTelemetryStudy.field(
            meter.meter,"groupMeasures");
        Object measured=measures.get((int)number(snapshot,"measures"));
        long closedNanos=number(measured,"nanos");
        require(number(measured,"units")==fee &&
            number(meter.meter,"copiedEntries")==preCopies+baseCopies &&
            number(meter.meter,"copyChunks")==preChunks+2*blocks+1,
            "GUARD_TOKEN_CLOSED_MEASURE");
        return new TokenFixture(meter,snapshot,started,exportStarted,fee,bodyBytes,
            blocks,preCopies,preChunks,measured,closedNanos);
    }

    private static CohortTerminalTelemetryStudy.R5PublicationToken tokenConstruction(
            TokenFixture fixture,long fee) throws Exception {
        return tokenConstruction(fixture.meter,fixture.snapshot,fixture.started,
            fixture.exportStarted,fee,fixture.resultBytes,fixture.blocks,
            fixture.measure,fixture.closedNanos);
    }

    private static CohortTerminalTelemetryStudy.R5PublicationToken tokenConstruction(
            CohortTerminalTelemetryStudy.SetupMeter meter,
            CohortTerminalTelemetryStudy.R5Snapshot snapshot,long started,
            long exportStarted,long fee,long resultBytes,long blocks,Object measure,
            long closedNanos) throws Exception {
        Constructor<?> constructor=
            CohortTerminalTelemetryStudy.R5PublicationToken.class.getDeclaredConstructor(
                CohortTerminalTelemetryStudy.SetupMeter.class,
                CohortTerminalTelemetryStudy.R5Snapshot.class,
                long.class,long.class,long.class,long.class,long.class,
                Object.class,long.class);
        constructor.setAccessible(true);
        return (CohortTerminalTelemetryStudy.R5PublicationToken)constructor.newInstance(
            meter,snapshot,Long.valueOf(started),Long.valueOf(exportStarted),
            Long.valueOf(fee),Long.valueOf(resultBytes),Long.valueOf(blocks),
            measure,Long.valueOf(closedNanos));
    }

    private static Map<String,Object> geometryTree(Object geometry) {
        /* Only the exact geometry leaf is under test; this synthetic map is not
         * a reference outcome or evidence fixture. */
        Map<String,Object> component=map();
        component.put("geometryCanonical",geometry);
        List<Object> components=new ArrayList<Object>();
        components.add(component);
        Map<String,Object> layout=map();
        layout.put("components",components);
        Map<String,Object> run=map();
        run.put("layout",layout);
        Map<String,Object> open=map();
        open.put("run",run);
        Map<String,Object> references=map();
        references.put("OPEN",open);
        Map<String,Object> root=map();
        root.put("references",references);
        return root;
    }

    private static void writeAscii(String text,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink) throws Exception {
        byte[] bytes=ascii(text);
        for (int i=0;i<bytes.length;i++) sink.writeByte(bytes[i]&255);
    }
    private static void expectFileAlreadyExists(String label,final Path path)
            throws Exception {
        Throwable caught=null;
        try {
            Files.newOutputStream(path,StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS).close();
        } catch (Throwable failure) { caught=unwrap(failure); }
        if (!(caught instanceof FileAlreadyExistsException))
            throw new AssertionError(label+": expected FileAlreadyExistsException, got "+
                describe(caught));
        passed++;
    }

    private static void expectCode(String label,String expected,CheckedCall call)
            throws Exception {
        Throwable caught=null;
        try { call.call(); }
        catch (Throwable failure) { caught=unwrap(failure); }
        if (caught==null || !expected.equals(caught.getMessage()))
            throw new AssertionError(label+": expected "+expected+", got "+describe(caught));
        passed++;
    }

    private static void expectMessage(String label,String expected,CheckedCall call)
            throws Exception {
        Throwable caught=null;
        try { call.call(); }
        catch (Throwable failure) { caught=unwrap(failure); }
        if (caught==null || !expected.equals(caught.getMessage()))
            throw new AssertionError(label+": expected message "+expected+", got "+
                describe(caught));
        passed++;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable result=failure;
        while (result instanceof InvocationTargetException &&
                ((InvocationTargetException)result).getCause()!=null)
            result=((InvocationTargetException)result).getCause();
        return result;
    }

    private static String describe(Throwable failure) {
        if (failure==null) return "no exception";
        return failure.getClass().getName()+": "+failure.getMessage();
    }

    private static void ensureOpen(CohortTerminalTelemetryStudy.SetupMeter meter)
            throws Exception {
        if (!flag(meter.meter,"open"))
            meter.method("beginGroup",new Class<?>[0]);
    }

    private static CohortTerminalTelemetryStudy.SetupMeter meter(
            CohortTerminalTelemetryStudy.Parent parent,long started) throws Exception {
        return new CohortTerminalTelemetryStudy.SetupMeter(parent,parent.meter(started));
    }

    private static Map<String,Object> map() {
        return new LinkedHashMap<String,Object>();
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static String repeat(char c,int count) {
        char[] chars=new char[count];
        Arrays.fill(chars,c);
        return new String(chars);
    }

    private static boolean flag(Object object,String name) throws Exception {
        return Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(object,name));
    }

    private static long number(Object object,String name) throws Exception {
        Object value=CohortTerminalTelemetryStudy.field(object,name);
        if (!(value instanceof Number))
            throw new IOException("GUARD_FIELD_TYPE_"+name);
        return ((Number)value).longValue();
    }

    private static void set(Object object,String name,Object value) throws Exception {
        Field field=object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object,value);
    }

    private static void require(boolean condition,String code) throws IOException {
        if (!condition) throw new IOException(code);
    }
}
