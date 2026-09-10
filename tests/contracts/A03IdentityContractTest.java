package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** JVM entry point for the shared A03 identity corpus and pure replay seam. */
public final class A03IdentityContractTest {
    private static int assertions;
    private static long encodingNanos;
    private static long captureNanos;
    private static long resolveNanos;
    private static String parityPrefix;

    private A03IdentityContractTest() {
    }

    public static void main(String[] args) {
        parityPrefix = args.length == 1 ? args[0] : null;
        long encodingStart = System.nanoTime();
        String first = A03IdentityContractVectors.run();
        String second = A03IdentityContractVectors.run();
        encodingNanos = System.nanoTime() - encodingStart;
        require(first.equals(second), "A03 vector receipt was not deterministic");
        require(first.startsWith("A03_IDENTITY_VECTORS_BEGIN\n")
                && first.endsWith("A03_IDENTITY_VECTORS_END\n"),
                "A03 vector markers were not complete");
        System.out.print(first);
        writeParity("vectors", first);
        boundedReplayCanaries();
        System.out.println("A03_TIMING encoding-ns=" + encodingNanos
                + " capture-ns=" + captureNanos
                + " resolve-ns=" + resolveNanos);
        System.out.println("PASS: A03IdentityContractTest assertions=" + assertions);
    }

    /** Exercise the actual pure adapter for each current bounded intent. */
    private static void boundedReplayCanaries() {
        BoundedAssemblyRequest[] requests = {
            BoundedAssemblyRequest.forCanary(1L),
            BoundedAssemblyRequest.forControlledIndicator(2L),
            BoundedAssemblyRequest.forControlledIndicator(-1L)
        };
        List<RealizationManifest> manifests =
                new ArrayList<RealizationManifest>();
        for (BoundedAssemblyRequest request : requests) {
            BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(request);
            long captureStart = System.nanoTime();
            RealizationManifest manifest = A03RealizationReplay.capture(plan);
            captureNanos += System.nanoTime() - captureStart;
            RealizationManifest parsed = RealizationManifest.parse(
                    manifest.toCanonical());
            long resolveStart = System.nanoTime();
            BoundedAssemblyPlan resolved = A03RealizationReplay.resolve(parsed);
            resolveNanos += System.nanoTime() - resolveStart;
            resolveStart = System.nanoTime();
            BoundedAssemblyPlan decoded = A03RealizationReplay.decodeAndResolve(
                    manifest.toCanonical());
            resolveNanos += System.nanoTime() - resolveStart;
            require(parsed.identityCanonical().equals(manifest.identityCanonical()),
                    "bounded manifest identity changed after parse");
            require(resolved.getSemanticSignature().equals(
                    plan.getSemanticSignature()),
                    "resolved bounded plan changed semantic signature");
            require(decoded.getSemanticSignature().equals(
                    plan.getSemanticSignature()),
                    "decoded bounded plan changed semantic signature");
            manifests.add(manifest);
        }
        require(manifests.get(0).getDescriptor().getGenerator().getVersion()
                == BoundedAssemblyRequest.GENERATOR_VERSION
                && manifests.get(1).getDescriptor().getGenerator().getVersion()
                == BoundedAssemblyRequest.GENERATOR_VERSION,
                "bounded replay did not preserve current generator version");
        require(manifests.get(0).getDescriptor().getRootSeed() == 1L
                && manifests.get(1).getDescriptor().getRootSeed() == 2L,
                "bounded replay did not preserve exact seeds");
        for (RealizationManifest manifest : manifests)
            emitReplayManifest(manifest);

        final RealizationManifest base = manifests.get(0);

        // Retired future-state metadata is rejected at the current parser
        // boundary instead of being silently retained or interpreted.
        final String retiredFuture = base.toCanonical() + "\nfuture=1:~";
        expectContractFailure(new Action() {
            @Override public void run() {
                RealizationManifest.parse(retiredFuture);
            }
        }, ChallengeContractException.Code.UNKNOWN_FIELD, "future", null,
                "retired future-state field was accepted");

        // Each mutation is structurally valid but semantically different. The
        // adapter must reject it before entering the mutable assembler.
        String numericKey = findChoiceKey(base,
                RealizationManifest.Choice.Kind.NUMBER, null);
        RealizationManifest.Choice numeric = findChoice(base, numericKey);
        expectReplayMismatch(replaceChoice(base, numericKey,
                RealizationManifest.Choice.number(numericKey,
                        numeric.getNumberValue() + 1.0)),
                "choice." + numericKey, "numeric choice");

        RealizationManifest values = manifests.get(1);
        String modelKey = findChoiceKey(values,
                RealizationManifest.Choice.Kind.TOKEN, ".model");
        RealizationManifest.Choice model = findChoice(values, modelKey);
        expectReplayMismatch(replaceChoice(values, modelKey,
                RealizationManifest.Choice.token(modelKey,
                        model.getTokenValue() + "-changed")),
                "choice." + modelKey, "model choice");
        String catalogKey = findChoiceKey(values,
                RealizationManifest.Choice.Kind.TOKEN, ".catalog");
        RealizationManifest.Choice catalog = findChoice(values, catalogKey);
        expectReplayMismatch(replaceChoice(values, catalogKey,
                RealizationManifest.Choice.token(catalogKey,
                        catalog.getTokenValue() + "-changed")),
                "choice." + catalogKey, "catalog choice");
        String packageKey = findChoiceKey(values,
                RealizationManifest.Choice.Kind.TOKEN, ".package");
        RealizationManifest.Choice packageChoice = findChoice(values, packageKey);
        expectReplayMismatch(replaceChoice(values, packageKey,
                RealizationManifest.Choice.token(packageKey,
                        packageChoice.getTokenValue() + "-changed")),
                "choice." + packageKey, "package choice");

        final RealizationManifest.VersionPin routing = findVersion(base,
                RealizationManifest.VersionPin.Concern.ROUTING);
        final int unsupportedRoutingVersion = routing.getOwnerVersionNumber() + 1;
        expectContractFailure(new Action() {
            @Override public void run() {
                replaceVersion(base,
                        RealizationManifest.VersionPin.Concern.ROUTING,
                        new ChallengeDescriptor.VersionedId(
                                routing.getOwnerId(), unsupportedRoutingVersion));
            }
        }, ChallengeContractException.Code.UNSUPPORTED_VERSION,
                "version.owner", Integer.toString(unsupportedRoutingVersion),
                "unsupported version pin was not rejected at construction");

        RealizationManifest unknownChoice = appendChoice(base,
                RealizationManifest.Choice.token("unknown.injected.choice", "poison"));
        expectReplayMismatch(unknownChoice, "choice.unknown.injected.choice",
                "unknown choice");

        modelMutationCanary(values);
        npnModelMutationCanary(manifests.get(2));
        choiceCapacityCanaries(base);
    }

    private static void choiceCapacityCanaries(final RealizationManifest base) {
        final ArrayList<RealizationManifest.Choice> choices =
                new ArrayList<RealizationManifest.Choice>();
        for (int index = 0; index < 2048; index++)
            choices.add(RealizationManifest.Choice.integer("bounded.choice." + index, index));
        RealizationManifest boundary = new RealizationManifest(base.getSchemaVersion(),
                base.getDescriptor(), base.getBlocks(), base.getVersionPins(), choices,
                base.getNetBindings(), base.getTargets());
        require(RealizationManifest.parse(boundary.toCanonical()).identityCanonical()
                .equals(boundary.identityCanonical()),
                "current maximum choice population did not round-trip");
        final RealizationManifest.Choice overflow =
                RealizationManifest.Choice.integer("bounded.choice.overflow", 2048);
        choices.add(overflow);
        expectContractFailure(new Action() {
            @Override public void run() {
                new RealizationManifest(base.getSchemaVersion(), base.getDescriptor(),
                        base.getBlocks(), base.getVersionPins(), choices,
                        base.getNetBindings(), base.getTargets());
            }
        }, ChallengeContractException.Code.INVALID_ENCODING, "choices", null,
                "over-bound choice construction was accepted");
        String payload = overflow.toCanonical();
        final String oversized = boundary.toCanonical() + "\nchoice=" +
                payload.length() + ":" + payload;
        expectContractFailure(new Action() {
            @Override public void run() { RealizationManifest.parse(oversized); }
        }, ChallengeContractException.Code.INVALID_ENCODING, "choices", null,
                "over-bound encoded choice population was accepted");
    }

    private static void npnModelMutationCanary(RealizationManifest manifest) {
        TransistorModel.createModelMap();
        final TransistorModel model = TransistorModel.modelMap.get("default");
        require(model != null, "default NPN model is not registered");
        final long saturation = Double.doubleToLongBits(model.satCur);
        final String key = findChoiceKeyContaining(manifest,
                RealizationManifest.Choice.Kind.NUMBER, "element.Q1.parameter.", "model-saturation-current");
        try {
            model.satCur = Double.longBitsToDouble(saturation ^ 1L);
            expectReplayMismatch(manifest, "choice." + key, "NPN primitive model mutation");
        } finally {
            model.satCur = Double.longBitsToDouble(saturation);
        }
        require(Double.doubleToLongBits(model.satCur) == saturation,
                "NPN primitive model was not restored exactly");
    }

    private static void modelMutationCanary(RealizationManifest manifest) {
        DiodeModel.createModelMap();
        final DiodeModel model = DiodeModel.modelMap.get("default-led");
        require(model != null, "default-led model was not registered");
        final long saturation = Double.doubleToLongBits(model.saturationCurrent);
        final long series = Double.doubleToLongBits(model.seriesResistance);
        final long emission = Double.doubleToLongBits(model.emissionCoefficient);
        final long breakdown = Double.doubleToLongBits(model.breakdownVoltage);
        final long vscale = Double.doubleToLongBits(model.vscale);
        final long vdcoef = Double.doubleToLongBits(model.vdcoef);
        final long fwdrop = Double.doubleToLongBits(model.fwdrop);
        final String key = findChoiceKeyContaining(manifest,
                RealizationManifest.Choice.Kind.NUMBER, "element.LED1.parameter.", "saturation-current");
        try {
            model.saturationCurrent = Double.longBitsToDouble(saturation ^ 1L);
            model.updateModel();
            expectReplayMismatch(manifest, "choice." + key,
                    "default-led primitive model mutation");
        } finally {
            model.saturationCurrent = Double.longBitsToDouble(saturation);
            model.seriesResistance = Double.longBitsToDouble(series);
            model.emissionCoefficient = Double.longBitsToDouble(emission);
            model.breakdownVoltage = Double.longBitsToDouble(breakdown);
            model.vscale = Double.longBitsToDouble(vscale);
            model.vdcoef = Double.longBitsToDouble(vdcoef);
            model.fwdrop = Double.longBitsToDouble(fwdrop);
        }
        require(Double.doubleToLongBits(model.saturationCurrent) == saturation
                && Double.doubleToLongBits(model.seriesResistance) == series
                && Double.doubleToLongBits(model.emissionCoefficient) == emission
                && Double.doubleToLongBits(model.breakdownVoltage) == breakdown
                && Double.doubleToLongBits(model.vscale) == vscale
                && Double.doubleToLongBits(model.vdcoef) == vdcoef
                && Double.doubleToLongBits(model.fwdrop) == fwdrop,
                "default-led primitive model was not restored exactly");
    }

    /** Emit a length frame for parity with the compiled runtime verifier. */
    private static void emitReplayManifest(RealizationManifest manifest) {
        String canonical = manifest.identityCanonical();
        System.out.println("A03_REPLAY_MANIFEST="
                + manifest.getDescriptor().getGenerator().getVersion() + "|"
                + manifest.getDescriptor().getRootSeed() + "|" + canonical.length());
        System.out.println(canonical);
        String artifact = BoundedAssemblyRequest.CONTROLLED_INTENT_ID.equals(
                manifest.getDescriptor().getDeviceIntent().getId())
                ? (manifest.getDescriptor().getRootSeed() == -1L ?
                    "manifest-controlled-npn" : "manifest-controlled") : "manifest-resistive";
        writeParity(artifact, canonical);
    }

    private static void writeParity(String name, String canonical) {
        if (parityPrefix == null) return;
        try {
            java.io.FileOutputStream stream = new java.io.FileOutputStream(
                    parityPrefix + "." + name + ".txt");
            try { stream.write(canonical.getBytes("UTF-8")); }
            finally { stream.close(); }
        } catch (java.io.IOException failure) {
            throw new AssertionError("Exact parity artifact write failed", failure);
        }
    }

    private static RealizationManifest.Choice findChoice(
            RealizationManifest manifest, String key) {
        for (RealizationManifest.Choice choice : manifest.getChoices())
            if (key.equals(choice.getKey())) return choice;
        throw new AssertionError("choice not found: " + key);
    }

    private static String findChoiceKey(RealizationManifest manifest,
            RealizationManifest.Choice.Kind kind, String suffix) {
        for (RealizationManifest.Choice choice : manifest.getChoices()) {
            if (choice.getKind() == kind
                    && (suffix == null || choice.getKey().endsWith(suffix)))
                return choice.getKey();
        }
        throw new AssertionError("choice not found: " + kind + ":" + suffix);
    }

    private static String findChoiceKeyContaining(RealizationManifest manifest,
            RealizationManifest.Choice.Kind kind, String first, String second) {
        for (RealizationManifest.Choice choice : manifest.getChoices()) {
            if (choice.getKind() == kind && choice.getKey().indexOf(first) >= 0
                    && choice.getKey().indexOf(second) >= 0)
                return choice.getKey();
        }
        throw new AssertionError("choice not found: " + first + ":" + second);
    }

    private static RealizationManifest.VersionPin findVersion(
            RealizationManifest manifest, RealizationManifest.VersionPin.Concern concern) {
        for (RealizationManifest.VersionPin pin : manifest.getVersionPins())
            if (pin.getConcern() == concern) return pin;
        throw new AssertionError("version pin not found: " + concern);
    }

    private static RealizationManifest replaceChoice(RealizationManifest base,
            String key, RealizationManifest.Choice replacement) {
        ArrayList<RealizationManifest.Choice> choices =
                new ArrayList<RealizationManifest.Choice>(base.getChoices());
        for (int i = 0; i < choices.size(); i++) {
            if (key.equals(choices.get(i).getKey())) {
                choices.set(i, replacement);
                return new RealizationManifest(base.getSchemaVersion(),
                        base.getDescriptor(), base.getBlocks(), base.getVersionPins(),
                        choices, base.getNetBindings(), base.getTargets());
            }
        }
        throw new AssertionError("choice not found: " + key);
    }

    private static RealizationManifest appendChoice(RealizationManifest base,
            RealizationManifest.Choice choice) {
        ArrayList<RealizationManifest.Choice> choices =
                new ArrayList<RealizationManifest.Choice>(base.getChoices());
        choices.add(choice);
        return new RealizationManifest(base.getSchemaVersion(), base.getDescriptor(),
                base.getBlocks(), base.getVersionPins(), choices,
                base.getNetBindings(), base.getTargets());
    }

    private static RealizationManifest replaceVersion(RealizationManifest base,
            RealizationManifest.VersionPin.Concern concern,
            ChallengeDescriptor.VersionedId owner) {
        ArrayList<RealizationManifest.VersionPin> versions =
                new ArrayList<RealizationManifest.VersionPin>(base.getVersionPins());
        for (int i = 0; i < versions.size(); i++) {
            if (versions.get(i).getConcern() == concern) {
                versions.set(i, new RealizationManifest.VersionPin(concern, owner));
                return new RealizationManifest(base.getSchemaVersion(),
                        base.getDescriptor(), base.getBlocks(), versions,
                        base.getChoices(), base.getNetBindings(), base.getTargets());
            }
        }
        throw new AssertionError("version pin not found: " + concern);
    }

    private static void expectReplayMismatch(final RealizationManifest manifest,
            String field, String label) {
        expectContractFailure(new Action() {
            @Override public void run() { A03RealizationReplay.resolve(manifest); }
        }, ChallengeContractException.Code.CONTRADICTORY_CONSTRAINT, field, null,
                label);
    }

    private interface Action {
        void run();
    }

    private static void expectContractFailure(Action action,
            ChallengeContractException.Code code, String field, String message,
            String label) {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (ChallengeContractException expected) {
            require(expected.getCode() == code && field.equals(expected.getFieldId()),
                    label + " wrong failure: " + expected.getCode() + ":"
                            + expected.getFieldId());
            if (message != null) {
                require(expected.getMessage().indexOf(message) >= 0,
                        label + " missing stable detail: " + message);
            }
        }
    }

    private static void require(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
}
