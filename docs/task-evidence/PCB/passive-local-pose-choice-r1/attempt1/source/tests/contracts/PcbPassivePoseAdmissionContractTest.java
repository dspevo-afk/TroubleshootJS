package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Vector;

/** Pure identity and actual-owner pose-boundary fixtures; no route admission is inferred. */
public final class PcbPassivePoseAdmissionContractTest {
    private static int assertions;

    private static final PhysicalPackage[] CANONICAL_PASSIVES = new PhysicalPackage[] {
        PhysicalPackages.AXIAL_RESISTOR,
        PhysicalPackages.AXIAL_DIODE,
        PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
        PhysicalPackages.RADIAL_CERAMIC_CAPACITOR
    };

    public static void main(String[] args) throws Exception {
        verifyVersionedIdentitySelection();
        verifyCanonicalPassivePoses();
        verifyForeignAndMixedPassives();
        verifyDependencyCanonicalField();
        verifyAdmissionCanonicalIdentity();
        System.out.println("PASS: PCB passive pose admission contracts " + assertions +
            " assertions");
    }

    private static void verifyVersionedIdentitySelection() {
        require(Rb56Placement.CURRENT_VERSION == 15,
            "the default RB56 placement interpretation remains version15");
        require(Rb56Placement.PASSIVE_POSE_VERSION16 == 16,
            "the passive-pose interpretation has its explicit version16 identity");
        require(Rb56Placement.SIZING_CONTROL_VERSION17 == 17 &&
                Rb56Placement.supportsVersion(17) &&
                Rb56Placement.SOURCE_SIZED_VERSION18 == 18 &&
                Rb56Placement.supportsVersion(18) &&
                Rb56Placement.LOCAL_POSE_CHOICE_VERSION19 == 19 &&
                Rb56Placement.supportsVersion(19) && !Rb56Placement.supportsVersion(20),
            "direct-native placements17/18/19 are explicit; placement20 remains unknown");
        require(Rb56Placement.passivePosePolicyVersion(15) == PcbPassivePosePolicy.LEGACY,
            "default version15 maps to the legacy pose policy");
        require(Rb56Placement.passivePosePolicyVersion(16) == PcbPassivePosePolicy.ORTHOGONAL,
            "explicit version16 maps to the orthogonal pose policy");
        require(Rb56Placement.passivePosePolicyVersion(17) == PcbPassivePosePolicy.SIZING_ONLY,
            "explicit version17 maps to the sizing-only pose policy");
        require(Rb56Placement.passivePosePolicyVersion(18) ==
                PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL,
            "direct-native version18 maps to source-sized orthogonal policy3");
        require(Rb56Placement.passivePosePolicyVersion(19) ==
                PcbPassivePosePolicy.LOCAL_POSE_CHOICE,
            "direct-native version19 maps to local pose-choice policy4");
        require(Rb56Placement.densityVersion(15) == 14 &&
                Rb56Placement.densityVersion(16) == 14 &&
                Rb56Placement.densityVersion(17) == 14 &&
                Rb56Placement.densityVersion(18) == 14 &&
                Rb56Placement.densityVersion(19) == 14,
            "current15 and explicit16/17/18/19 retain density14");
        require("RB56_BOARD_NORMAL@1".equals(
                MediumBoardNormalAdmission.rb56IdentityForPlacement(15)),
            "placement15 selects the literal legacy admission identity");
        require("RB56_BOARD_NORMAL@2".equals(
                MediumBoardNormalAdmission.rb56IdentityForPlacement(16)),
            "placement16 selects the literal orthogonal admission identity");
        require("RB56_BOARD_NORMAL@3".equals(
                MediumBoardNormalAdmission.rb56IdentityForPlacement(17)),
            "placement17 selects the literal sizing-control admission identity");
        require("RB56_BOARD_NORMAL@4".equals(
                MediumBoardNormalAdmission.rb56IdentityForPlacement(18)),
            "placement18 selects the literal source-sized admission identity");
        require("RB56_BOARD_NORMAL@5".equals(
                MediumBoardNormalAdmission.rb56IdentityForPlacement(19)),
            "placement19 selects the literal local-choice admission identity");
        require("RB56_BOARD_NORMAL@1".equals(MediumBoardNormalAdmission.rb56IdentityFor(
                constraints(PcbPassivePosePolicy.LEGACY,
                    new Vector<PcbPlacementConstraints.Part>()))),
            "legacy MEDIUM_BOARD constraints select admission identity @1");
        require("RB56_BOARD_NORMAL@2".equals(MediumBoardNormalAdmission.rb56IdentityFor(
                constraints(PcbPassivePosePolicy.ORTHOGONAL,
                    new Vector<PcbPlacementConstraints.Part>()))),
            "explicit pose1 MEDIUM_BOARD constraints select admission identity @2");
        require("RB56_BOARD_NORMAL@3".equals(MediumBoardNormalAdmission.rb56IdentityFor(
                constraints(PcbPassivePosePolicy.SIZING_ONLY,
                    new Vector<PcbPlacementConstraints.Part>()))),
            "policy2 MEDIUM_BOARD constraints select admission identity @3");
        require("RB56_BOARD_NORMAL@4".equals(MediumBoardNormalAdmission.rb56IdentityFor(
                constraints(PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL,
                    new Vector<PcbPlacementConstraints.Part>()))),
            "policy3 MEDIUM_BOARD constraints select admission identity @4");
        require("RB56_BOARD_NORMAL@5".equals(MediumBoardNormalAdmission.rb56IdentityFor(
                constraints(PcbPassivePosePolicy.LOCAL_POSE_CHOICE,
                    new Vector<PcbPlacementConstraints.Part>()))),
            "policy4 MEDIUM_BOARD constraints select admission identity @5");
        require(MediumBoardNormalAdmission.Contract.RB56 ==
                MediumBoardNormalAdmission.rb56ContractForPlacement(15),
            "placement15 selects the legacy RB56 contract");
        require(MediumBoardNormalAdmission.Contract.RB56_ORTHOGONAL ==
                MediumBoardNormalAdmission.rb56ContractForPlacement(16),
            "placement16 selects the orthogonal RB56 contract");
        require(MediumBoardNormalAdmission.Contract.RB56_SIZING_CONTROL ==
                MediumBoardNormalAdmission.rb56ContractForPlacement(17),
            "placement17 selects the sizing-control RB56 contract");
        require(MediumBoardNormalAdmission.Contract.RB56_SOURCE_SIZED ==
                MediumBoardNormalAdmission.rb56ContractForPlacement(18),
            "placement18 selects the distinct source-sized RB56 contract");
        require(MediumBoardNormalAdmission.Contract.RB56_LOCAL_POSE_CHOICE ==
                MediumBoardNormalAdmission.rb56ContractForPlacement(19),
            "placement19 selects its distinct direct-native RB56 contract");
        require(MediumBoardNormalAdmission.Contract.RB56_SIZING_CONTROL.minimumParts == 40 &&
                MediumBoardNormalAdmission.Contract.RB56_SIZING_CONTROL.maximumParts == 60 &&
                MediumBoardNormalAdmission.Contract.RB56_SIZING_CONTROL.maximumTerminals == 7,
            "sizing-control admission keeps the existing RB56 mechanical bounds");
        require(MediumBoardNormalAdmission.Contract.RB56_SOURCE_SIZED.minimumParts == 40 &&
                MediumBoardNormalAdmission.Contract.RB56_SOURCE_SIZED.maximumParts == 60 &&
                MediumBoardNormalAdmission.Contract.RB56_SOURCE_SIZED.maximumTerminals == 7 &&
                MediumBoardNormalAdmission.Contract.RB56_SOURCE_SIZED.isRb56(),
            "source-sized admission retains the RB56 40..60/2..7 mechanical bounds");
        require(MediumBoardNormalAdmission.Contract.RB56_LOCAL_POSE_CHOICE.minimumParts == 40 &&
                MediumBoardNormalAdmission.Contract.RB56_LOCAL_POSE_CHOICE.maximumParts == 60 &&
                MediumBoardNormalAdmission.Contract.RB56_LOCAL_POSE_CHOICE.maximumTerminals == 7 &&
                MediumBoardNormalAdmission.Contract.RB56_LOCAL_POSE_CHOICE.isRb56(),
            "local-choice admission retains the same RB56 mechanical bounds");
        Rb56Plan directNativeControl = Rb56Plan.resolve(42017L, 17);
        require(directNativeControl.placementInterpretationVersion == 17,
            "placement17 remains available through the direct native plan path");
        Rb56Plan directNativeTrial = Rb56Plan.resolve(42017L, 18);
        require(directNativeTrial.placementInterpretationVersion == 18,
            "placement18 remains available through the direct native plan path");
        PcbPlacementConstraints trialConstraints = Rb56Placement.create(directNativeTrial);
        require(trialConstraints.placementDensityVersion == 14 &&
                trialConstraints.passivePosePolicyVersion ==
                    PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL &&
                trialConstraints.requiresTwoLayerRouting,
            "placement18 maps policy3, density14, and required P07 two-layer routing");
        require(trialConstraints.getTraceWidthProfile() == PcbTraceWidthProfile.ROLE_WIDTHS &&
                Rb56Placement.widthProfile(18) == PcbTraceWidthProfile.ROLE_WIDTHS,
            "placement18 retains the current role-width profile");
        Rb56Plan directNativeLocalChoice = Rb56Plan.resolve(42017L, 19);
        require(directNativeLocalChoice.placementInterpretationVersion == 19,
            "placement19 remains available through direct native plans");
        PcbPlacementConstraints localChoiceConstraints =
            Rb56Placement.create(directNativeLocalChoice);
        require(localChoiceConstraints.placementDensityVersion == 14 &&
                localChoiceConstraints.passivePosePolicyVersion ==
                    PcbPassivePosePolicy.LOCAL_POSE_CHOICE &&
                localChoiceConstraints.requiresTwoLayerRouting &&
                localChoiceConstraints.getTraceWidthProfile() == PcbTraceWidthProfile.ROLE_WIDTHS,
            "placement19 maps policy4, density14, two-layer routing, and role widths");
        reject(new Runnable() {
            public void run() {
                MediumBoardNormalAdmission.rb56IdentityForPlacement(20);
            }
        }, "unknown placement20 does not receive an admission identity");

        final GenerationRequest current = GenerationRequest.forFamilyQualification(
            Rb56Plan.FAMILY_ID, 42017L);
        final GenerationRequest explicitPose = current.withRb56PlacementInterpretationVersion(16);
        reject(new Runnable() {
            public void run() {
                current.withRb56PlacementInterpretationVersion(17);
            }
        }, "direct-native placement17 cannot create a private replay request");
        reject(new Runnable() {
            public void run() {
                current.withRb56PlacementInterpretationVersion(18);
            }
        }, "direct-native placement18 cannot create a private replay request");
        reject(new Runnable() {
            public void run() {
                current.withRb56PlacementInterpretationVersion(19);
            }
        }, "direct-native placement19 cannot create a private replay request");
        rejectPrivateRequestConstruction(17);
        rejectPrivateRequestConstruction(18);
        rejectPrivateRequestConstruction(19);
        require("RB56_BOARD_NORMAL@1".equals(current.getRequiredPhysicalAdmissionIdentity()),
            "default private qualification retains admission identity @1");
        require("RB56_BOARD_NORMAL@2".equals(explicitPose.getRequiredPhysicalAdmissionIdentity()),
            "explicit private16 qualification selects admission identity @2");
        require(MediumBoardNormalAdmission.RB56_IDENTITY.equals(
                new Rb56PlayerFamilyCapability().physicalAdmissionIdentity()),
            "normal/default family registration remains pinned to admission identity @1");
        String oldRequest = current.canonical();
        String poseRequest = explicitPose.canonical();
        String namespace = "tsj-generation-request/4;native;qualification-only;";
        require(oldRequest.startsWith(namespace) && poseRequest.startsWith(namespace),
            "private pose selection does not change the replay namespace");
        require(poseRequest.contains("physicalAdmission=RB56_BOARD_NORMAL@2"),
            "private16 canonical request binds the explicit admission identity");
    }

    private static void rejectPrivateRequestConstruction(int version) {
        try {
            Constructor<GenerationRequest> constructor =
                GenerationRequest.class.getDeclaredConstructor(ChallengeDescriptor.class,
                    Boolean.TYPE, Boolean.TYPE, DifficultyProfile.class, Boolean.TYPE,
                    Boolean.TYPE, GenerationExecutionPolicy.class,
                    StagedFamilyCapability.class,
                    PlayerFamilyCatalog.ExecutionDeclaration.class, Integer.TYPE);
            constructor.setAccessible(true);
            PlayerFamilyCatalog.RegistrationBoundary registration =
                PlayerFamilyCatalog.newRegistrationBoundary();
            constructor.newInstance(ChallengeDescriptor.current(Rb56Plan.FAMILY_ID, 42017L),
                Boolean.FALSE, Boolean.FALSE, null, Boolean.FALSE, Boolean.TRUE,
                GenerationExecutionPolicy.SMALL_BOARD,
                registration.stagedCapability(Rb56Plan.FAMILY_ID), null,
                Integer.valueOf(version));
            throw new AssertionError("private replay request constructor accepted placement" +
                version);
        } catch (InvocationTargetException expected) {
            require(expected.getCause() instanceof IllegalArgumentException,
                "private replay constructor rejects placement" + version);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("private replay constructor fixture failed", failure);
        }
    }

    private static void verifyCanonicalPassivePoses() {
        PcbRotation[] rotations = new PcbRotation[] {
            PcbRotation.DEG_0, PcbRotation.DEG_90,
            PcbRotation.DEG_180, PcbRotation.DEG_270
        };
        for (int packageIndex = 0; packageIndex < CANONICAL_PASSIVES.length; packageIndex++) {
            final PhysicalPackage physical = CANONICAL_PASSIVES[packageIndex];
            for (int rotationIndex = 0; rotationIndex < rotations.length; rotationIndex++) {
                final PcbRotation rotation = rotations[rotationIndex];
                Fixture explicit = fixture(PcbPassivePosePolicy.ORTHOGONAL,
                    "PASSIVE_" + packageIndex, passiveType(physical), physical, physical,
                    rotation, PcbBoardSide.TOP);
                if (rotation == PcbRotation.DEG_0 || rotation == PcbRotation.DEG_90) {
                    accept(explicit, MediumBoardNormalAdmission.Contract.RB56_ORTHOGONAL,
                        "explicit16 accepts canonical " + physical.getId() + " TOP" +
                            rotation);
                } else {
                    rejectPose(explicit, MediumBoardNormalAdmission.Contract.RB56_ORTHOGONAL,
                        "explicit16 rejects canonical " + physical.getId() + " TOP" +
                            rotation);
                }
                Fixture sizingOnly = fixture(PcbPassivePosePolicy.SIZING_ONLY,
                    "SIZING_ONLY_" + packageIndex, passiveType(physical), physical,
                    physical, rotation, PcbBoardSide.TOP);
                if (rotation == PcbRotation.DEG_0 || rotation == PcbRotation.DEG_90) {
                    accept(sizingOnly, MediumBoardNormalAdmission.Contract.RB56_SIZING_CONTROL,
                        "control17 keeps the same mechanical cap for canonical " +
                            physical.getId() + " TOP" + rotation);
                } else {
                    rejectPose(sizingOnly,
                        MediumBoardNormalAdmission.Contract.RB56_SIZING_CONTROL,
                        "control17 rejects canonical " + physical.getId() + " TOP" +
                            rotation);
                }

                Fixture sourceSized = fixture(
                    PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL,
                    "SOURCE_SIZED_" + packageIndex, passiveType(physical), physical,
                    physical, rotation, PcbBoardSide.TOP);
                if (rotation == PcbRotation.DEG_0 || rotation == PcbRotation.DEG_90) {
                    accept(sourceSized,
                        MediumBoardNormalAdmission.Contract.RB56_SOURCE_SIZED,
                        "placement18 admits canonical " + physical.getId() + " TOP" +
                            rotation);
                } else {
                    rejectPose(sourceSized,
                        MediumBoardNormalAdmission.Contract.RB56_SOURCE_SIZED,
                        "placement18 rejects canonical " + physical.getId() + " TOP" +
                            rotation);
                }
                Fixture localChoice = fixture(PcbPassivePosePolicy.LOCAL_POSE_CHOICE,
                    "LOCAL_CHOICE_" + packageIndex, passiveType(physical), physical,
                    physical, rotation, PcbBoardSide.TOP);
                if (rotation == PcbRotation.DEG_0 || rotation == PcbRotation.DEG_90) {
                    accept(localChoice,
                        MediumBoardNormalAdmission.Contract.RB56_LOCAL_POSE_CHOICE,
                        "placement19 admits canonical " + physical.getId() + " TOP" +
                            rotation);
                } else {
                    rejectPose(localChoice,
                        MediumBoardNormalAdmission.Contract.RB56_LOCAL_POSE_CHOICE,
                        "placement19 rejects canonical " + physical.getId() + " TOP" +
                            rotation);
                }

                Fixture legacy = fixture(PcbPassivePosePolicy.LEGACY,
                    "LEGACY_PASSIVE_" + packageIndex, passiveType(physical), physical, physical,
                    rotation, PcbBoardSide.TOP);
                if (rotation == PcbRotation.DEG_0) {
                    accept(legacy, MediumBoardNormalAdmission.Contract.RB56,
                        "legacy accepts canonical " + physical.getId() + " TOP0");
                } else {
                    rejectPose(legacy, MediumBoardNormalAdmission.Contract.RB56,
                        "legacy rejects turned canonical " + physical.getId());
                }
            }
        }

        for (PhysicalPackage physical : CANONICAL_PASSIVES) {
            Fixture explicitBottom = fixture(PcbPassivePosePolicy.ORTHOGONAL,
                "BOTTOM_" + physical.getId(), passiveType(physical),
                PhysicalPackages.RAISED_FACTORY_LINK,
                PhysicalPackages.RAISED_FACTORY_LINK,
                PcbRotation.DEG_0, PcbBoardSide.BOTTOM);
            rejectPose(explicitBottom, MediumBoardNormalAdmission.Contract.RB56_ORTHOGONAL,
                "explicit16 rejects bottom-mounted passive geometry");
            Fixture sizingBottom = fixture(PcbPassivePosePolicy.SIZING_ONLY,
                "SIZING_BOTTOM_" + physical.getId(), passiveType(physical),
                PhysicalPackages.RAISED_FACTORY_LINK,
                PhysicalPackages.RAISED_FACTORY_LINK,
                PcbRotation.DEG_0, PcbBoardSide.BOTTOM);
            rejectPose(sizingBottom,
                MediumBoardNormalAdmission.Contract.RB56_SIZING_CONTROL,
                "control17 rejects bottom-mounted passive geometry");
            Fixture sourceSizedBottom = fixture(
                PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL,
                "SOURCE_SIZED_BOTTOM_" + physical.getId(), passiveType(physical),
                PhysicalPackages.RAISED_FACTORY_LINK,
                PhysicalPackages.RAISED_FACTORY_LINK,
                PcbRotation.DEG_0, PcbBoardSide.BOTTOM);
            rejectPose(sourceSizedBottom,
                MediumBoardNormalAdmission.Contract.RB56_SOURCE_SIZED,
                "placement18 rejects bottom-mounted canonical passive geometry");
            Fixture legacyBottom = fixture(PcbPassivePosePolicy.LEGACY,
                "LEGACY_BOTTOM_" + physical.getId(), passiveType(physical),
                PhysicalPackages.RAISED_FACTORY_LINK,
                PhysicalPackages.RAISED_FACTORY_LINK,
                PcbRotation.DEG_0, PcbBoardSide.BOTTOM);
            rejectPose(legacyBottom, MediumBoardNormalAdmission.Contract.RB56,
                "legacy rejects bottom-mounted passive geometry");
        }
    }

    private static void verifyForeignAndMixedPassives() {
        Fixture foreignTop0 = fixture(PcbPassivePosePolicy.ORTHOGONAL,
            "FOREIGN_PASSIVE_TOP0", "RESISTOR", PhysicalPackages.RAISED_FACTORY_LINK,
            PhysicalPackages.RAISED_FACTORY_LINK, PcbRotation.DEG_0, PcbBoardSide.TOP);
        accept(foreignTop0, MediumBoardNormalAdmission.Contract.RB56_ORTHOGONAL,
            "foreign passive packages retain their original TOP0 pose");
        Fixture controlForeignTop0 = fixture(PcbPassivePosePolicy.SIZING_ONLY,
            "CONTROL_FOREIGN_PASSIVE_TOP0", "RESISTOR",
            PhysicalPackages.RAISED_FACTORY_LINK, PhysicalPackages.RAISED_FACTORY_LINK,
            PcbRotation.DEG_0, PcbBoardSide.TOP);
        accept(controlForeignTop0,
            MediumBoardNormalAdmission.Contract.RB56_SIZING_CONTROL,
            "control17 keeps foreign passive packages at provider TOP0");
        Fixture sourceSizedForeignTop0 = fixture(
            PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL,
            "SOURCE_SIZED_FOREIGN_PASSIVE_TOP0", "RESISTOR",
            PhysicalPackages.RAISED_FACTORY_LINK, PhysicalPackages.RAISED_FACTORY_LINK,
            PcbRotation.DEG_0, PcbBoardSide.TOP);
        accept(sourceSizedForeignTop0,
            MediumBoardNormalAdmission.Contract.RB56_SOURCE_SIZED,
            "placement18 keeps foreign passive packages at provider TOP0");

        Fixture foreignTop90 = fixture(PcbPassivePosePolicy.ORTHOGONAL,
            "FOREIGN_PASSIVE_TOP90", "RESISTOR", PhysicalPackages.RAISED_FACTORY_LINK,
            PhysicalPackages.RAISED_FACTORY_LINK, PcbRotation.DEG_90, PcbBoardSide.TOP);
        rejectPose(foreignTop90, MediumBoardNormalAdmission.Contract.RB56_ORTHOGONAL,
            "explicit16 does not rotate a foreign passive package");
        Fixture controlForeignTop90 = fixture(PcbPassivePosePolicy.SIZING_ONLY,
            "CONTROL_FOREIGN_PASSIVE_TOP90", "RESISTOR",
            PhysicalPackages.RAISED_FACTORY_LINK, PhysicalPackages.RAISED_FACTORY_LINK,
            PcbRotation.DEG_90, PcbBoardSide.TOP);
        rejectPose(controlForeignTop90,
            MediumBoardNormalAdmission.Contract.RB56_SIZING_CONTROL,
            "control17 does not rotate a foreign passive package");
        Fixture sourceSizedForeignTop90 = fixture(
            PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL,
            "SOURCE_SIZED_FOREIGN_PASSIVE_TOP90", "RESISTOR",
            PhysicalPackages.RAISED_FACTORY_LINK, PhysicalPackages.RAISED_FACTORY_LINK,
            PcbRotation.DEG_90, PcbBoardSide.TOP);
        rejectPose(sourceSizedForeignTop90,
            MediumBoardNormalAdmission.Contract.RB56_SOURCE_SIZED,
            "placement18 does not rotate a foreign passive package");
        Fixture localChoiceForeignTop0 = fixture(
            PcbPassivePosePolicy.LOCAL_POSE_CHOICE,
            "LOCAL_CHOICE_FOREIGN_PASSIVE_TOP0", "RESISTOR",
            PhysicalPackages.RAISED_FACTORY_LINK, PhysicalPackages.RAISED_FACTORY_LINK,
            PcbRotation.DEG_0, PcbBoardSide.TOP);
        accept(localChoiceForeignTop0,
            MediumBoardNormalAdmission.Contract.RB56_LOCAL_POSE_CHOICE,
            "placement19 retains a foreign package at TOP0");
        Fixture localChoiceForeignTop90 = fixture(
            PcbPassivePosePolicy.LOCAL_POSE_CHOICE,
            "LOCAL_CHOICE_FOREIGN_PASSIVE_TOP90", "RESISTOR",
            PhysicalPackages.RAISED_FACTORY_LINK, PhysicalPackages.RAISED_FACTORY_LINK,
            PcbRotation.DEG_90, PcbBoardSide.TOP);
        rejectPose(localChoiceForeignTop90,
            MediumBoardNormalAdmission.Contract.RB56_LOCAL_POSE_CHOICE,
            "placement19 does not rotate a foreign passive package");

        Fixture foreignBottom = fixture(PcbPassivePosePolicy.ORTHOGONAL,
            "FOREIGN_PASSIVE_BOTTOM", "RESISTOR", PhysicalPackages.RAISED_FACTORY_LINK,
            PhysicalPackages.RAISED_FACTORY_LINK, PcbRotation.DEG_0, PcbBoardSide.BOTTOM);
        rejectPose(foreignBottom, MediumBoardNormalAdmission.Contract.RB56_ORTHOGONAL,
            "explicit16 rejects a bottom-mounted foreign passive package");

        Fixture foreignOwner = fixture(PcbPassivePosePolicy.ORTHOGONAL,
            "FOREIGN_PLACEMENT_OWNER", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
            PhysicalPackages.RAISED_FACTORY_LINK, PcbRotation.DEG_0, PcbBoardSide.TOP);
        rejectPose(foreignOwner, MediumBoardNormalAdmission.Contract.RB56_ORTHOGONAL,
            "passive placement cannot substitute a foreign physical package");

        Fixture[] allowedMixed = fixture(PcbPassivePosePolicy.ORTHOGONAL,
            new String[] {"MIXED_CANONICAL", "MIXED_FOREIGN"},
            new String[] {"RESISTOR", "RESISTOR"},
            new PhysicalPackage[] {PhysicalPackages.AXIAL_RESISTOR,
                PhysicalPackages.RAISED_FACTORY_LINK},
            new PhysicalPackage[] {PhysicalPackages.AXIAL_RESISTOR,
                PhysicalPackages.RAISED_FACTORY_LINK},
            new PcbRotation[] {PcbRotation.DEG_90, PcbRotation.DEG_0},
            new PcbBoardSide[] {PcbBoardSide.TOP, PcbBoardSide.TOP});
        accept(allowedMixed[0], MediumBoardNormalAdmission.Contract.RB56_ORTHOGONAL,
            "explicit16 permits a mixed canonical-quarter-turn and foreign-TOP0 owner");
        Fixture[] sourceSizedAllowedMixed = fixture(
            PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL,
            new String[] {"SOURCE_SIZED_MIXED_CANONICAL",
                "SOURCE_SIZED_MIXED_FOREIGN"},
            new String[] {"RESISTOR", "RESISTOR"},
            new PhysicalPackage[] {PhysicalPackages.AXIAL_RESISTOR,
                PhysicalPackages.RAISED_FACTORY_LINK},
            new PhysicalPackage[] {PhysicalPackages.AXIAL_RESISTOR,
                PhysicalPackages.RAISED_FACTORY_LINK},
            new PcbRotation[] {PcbRotation.DEG_90, PcbRotation.DEG_0},
            new PcbBoardSide[] {PcbBoardSide.TOP, PcbBoardSide.TOP});
        accept(sourceSizedAllowedMixed[0],
            MediumBoardNormalAdmission.Contract.RB56_SOURCE_SIZED,
            "placement18 permits a canonical quarter-turn beside foreign TOP0");
        Fixture[] localChoiceAllowedMixed = fixture(
            PcbPassivePosePolicy.LOCAL_POSE_CHOICE,
            new String[] {"LOCAL_CHOICE_MIXED_CANONICAL", "LOCAL_CHOICE_MIXED_FOREIGN"},
            new String[] {"RESISTOR", "RESISTOR"},
            new PhysicalPackage[] {PhysicalPackages.AXIAL_RESISTOR,
                PhysicalPackages.RAISED_FACTORY_LINK},
            new PhysicalPackage[] {PhysicalPackages.AXIAL_RESISTOR,
                PhysicalPackages.RAISED_FACTORY_LINK},
            new PcbRotation[] {PcbRotation.DEG_90, PcbRotation.DEG_0},
            new PcbBoardSide[] {PcbBoardSide.TOP, PcbBoardSide.TOP});
        accept(localChoiceAllowedMixed[0],
            MediumBoardNormalAdmission.Contract.RB56_LOCAL_POSE_CHOICE,
            "placement19 permits canonical TOP90 beside foreign TOP0");
        Fixture[] controlAllowedMixed = fixture(PcbPassivePosePolicy.SIZING_ONLY,
            new String[] {"CONTROL_MIXED_CANONICAL", "CONTROL_MIXED_FOREIGN"},
            new String[] {"RESISTOR", "RESISTOR"},
            new PhysicalPackage[] {PhysicalPackages.AXIAL_RESISTOR,
                PhysicalPackages.RAISED_FACTORY_LINK},
            new PhysicalPackage[] {PhysicalPackages.AXIAL_RESISTOR,
                PhysicalPackages.RAISED_FACTORY_LINK},
            new PcbRotation[] {PcbRotation.DEG_90, PcbRotation.DEG_0},
            new PcbBoardSide[] {PcbBoardSide.TOP, PcbBoardSide.TOP});
        accept(controlAllowedMixed[0],
            MediumBoardNormalAdmission.Contract.RB56_SIZING_CONTROL,
            "control17 preserves the same canonical 0/90 mechanical capability");

        Fixture[] disallowedMixed = fixture(PcbPassivePosePolicy.ORTHOGONAL,
            new String[] {"MIXED_CANONICAL_BAD", "MIXED_FOREIGN_BAD"},
            new String[] {"RESISTOR", "RESISTOR"},
            new PhysicalPackage[] {PhysicalPackages.AXIAL_RESISTOR,
                PhysicalPackages.RAISED_FACTORY_LINK},
            new PhysicalPackage[] {PhysicalPackages.AXIAL_RESISTOR,
                PhysicalPackages.RAISED_FACTORY_LINK},
            new PcbRotation[] {PcbRotation.DEG_90, PcbRotation.DEG_90},
            new PcbBoardSide[] {PcbBoardSide.TOP, PcbBoardSide.TOP});
        rejectPose(disallowedMixed[0], MediumBoardNormalAdmission.Contract.RB56_ORTHOGONAL,
            "explicit16 rejects a mixed pose set when its foreign passive is turned");
        Fixture[] sourceSizedDisallowedMixed = fixture(
            PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL,
            new String[] {"SOURCE_SIZED_MIXED_CANONICAL_BAD",
                "SOURCE_SIZED_MIXED_FOREIGN_BAD"},
            new String[] {"RESISTOR", "RESISTOR"},
            new PhysicalPackage[] {PhysicalPackages.AXIAL_RESISTOR,
                PhysicalPackages.RAISED_FACTORY_LINK},
            new PhysicalPackage[] {PhysicalPackages.AXIAL_RESISTOR,
                PhysicalPackages.RAISED_FACTORY_LINK},
            new PcbRotation[] {PcbRotation.DEG_90, PcbRotation.DEG_90},
            new PcbBoardSide[] {PcbBoardSide.TOP, PcbBoardSide.TOP});
        rejectPose(sourceSizedDisallowedMixed[0],
            MediumBoardNormalAdmission.Contract.RB56_SOURCE_SIZED,
            "placement18 still rejects a turned foreign passive beside a canonical passive");
        Fixture[] localChoiceDisallowedMixed = fixture(
            PcbPassivePosePolicy.LOCAL_POSE_CHOICE,
            new String[] {"LOCAL_CHOICE_MIXED_CANONICAL_BAD",
                "LOCAL_CHOICE_MIXED_FOREIGN_BAD"},
            new String[] {"RESISTOR", "RESISTOR"},
            new PhysicalPackage[] {PhysicalPackages.AXIAL_RESISTOR,
                PhysicalPackages.RAISED_FACTORY_LINK},
            new PhysicalPackage[] {PhysicalPackages.AXIAL_RESISTOR,
                PhysicalPackages.RAISED_FACTORY_LINK},
            new PcbRotation[] {PcbRotation.DEG_90, PcbRotation.DEG_90},
            new PcbBoardSide[] {PcbBoardSide.TOP, PcbBoardSide.TOP});
        rejectPose(localChoiceDisallowedMixed[0],
            MediumBoardNormalAdmission.Contract.RB56_LOCAL_POSE_CHOICE,
            "placement19 rejects a turned foreign passive beside a canonical passive");
        rejectPose(sourceSizedForeignTop0,
            MediumBoardNormalAdmission.Contract.MEDIUM,
            "generic medium rejects policy3 despite the source TOP0 pose");
        Fixture[] controlDisallowedMixed = fixture(PcbPassivePosePolicy.SIZING_ONLY,
            new String[] {"CONTROL_MIXED_CANONICAL_BAD", "CONTROL_MIXED_FOREIGN_BAD"},
            new String[] {"RESISTOR", "RESISTOR"},
            new PhysicalPackage[] {PhysicalPackages.AXIAL_RESISTOR,
                PhysicalPackages.RAISED_FACTORY_LINK},
            new PhysicalPackage[] {PhysicalPackages.AXIAL_RESISTOR,
                PhysicalPackages.RAISED_FACTORY_LINK},
            new PcbRotation[] {PcbRotation.DEG_90, PcbRotation.DEG_90},
            new PcbBoardSide[] {PcbBoardSide.TOP, PcbBoardSide.TOP});
        rejectPose(controlDisallowedMixed[0],
            MediumBoardNormalAdmission.Contract.RB56_SIZING_CONTROL,
            "control17 rejects a turned foreign passive in mixed-package geometry");

        Fixture genericTop0 = fixture(PcbPassivePosePolicy.LEGACY,
            "GENERIC_PASSIVE_TOP0", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
            PhysicalPackages.AXIAL_RESISTOR, PcbRotation.DEG_0, PcbBoardSide.TOP);
        accept(genericTop0, MediumBoardNormalAdmission.Contract.MEDIUM,
            "generic medium retains canonical passive TOP0");
        Fixture genericTurned = fixture(PcbPassivePosePolicy.LEGACY,
            "GENERIC_PASSIVE_TURNED", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
            PhysicalPackages.AXIAL_RESISTOR, PcbRotation.DEG_90, PcbBoardSide.TOP);
        rejectPose(genericTurned, MediumBoardNormalAdmission.Contract.MEDIUM,
            "generic medium retains passive TOP0 only");
        rejectPose(foreignTop0, MediumBoardNormalAdmission.Contract.MEDIUM,
            "generic medium rejects the explicit pose policy");

        Fixture legacy = fixture(PcbPassivePosePolicy.LEGACY,
            "CONTRACT_LEGACY", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
            PhysicalPackages.AXIAL_RESISTOR, PcbRotation.DEG_0, PcbBoardSide.TOP);
        rejectPose(legacy, MediumBoardNormalAdmission.Contract.RB56_ORTHOGONAL,
            "new RB56 contract rejects legacy constraints");
        Fixture explicit = fixture(PcbPassivePosePolicy.ORTHOGONAL,
            "CONTRACT_EXPLICIT", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
            PhysicalPackages.AXIAL_RESISTOR, PcbRotation.DEG_0, PcbBoardSide.TOP);
        rejectPose(explicit, MediumBoardNormalAdmission.Contract.RB56,
            "legacy RB56 contract rejects pose1 constraints");
        rejectPose(explicit, MediumBoardNormalAdmission.Contract.RB56_SIZING_CONTROL,
            "control17 contract rejects policy1 constraints");
        Fixture sizing = fixture(PcbPassivePosePolicy.SIZING_ONLY,
            "CONTRACT_SIZING_ONLY", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
            PhysicalPackages.AXIAL_RESISTOR, PcbRotation.DEG_0, PcbBoardSide.TOP);
        rejectPose(sizing, MediumBoardNormalAdmission.Contract.RB56_ORTHOGONAL,
            "policy1 contract rejects policy2 constraints");
        Fixture sourceSized = fixture(
            PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL,
            "CONTRACT_SOURCE_SIZED", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
            PhysicalPackages.AXIAL_RESISTOR, PcbRotation.DEG_0, PcbBoardSide.TOP);
        rejectPose(sourceSized, MediumBoardNormalAdmission.Contract.RB56_SIZING_CONTROL,
            "control17 contract rejects policy3 constraints");
        rejectPose(sourceSized, MediumBoardNormalAdmission.Contract.RB56,
            "legacy contract rejects policy3 constraints");
        rejectPose(explicit, MediumBoardNormalAdmission.Contract.RB56_SOURCE_SIZED,
            "policy3 contract rejects policy1 constraints");
        Fixture localChoiceFixture = fixture(PcbPassivePosePolicy.LOCAL_POSE_CHOICE,
            "CONTRACT_LOCAL_CHOICE", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
            PhysicalPackages.AXIAL_RESISTOR, PcbRotation.DEG_0, PcbBoardSide.TOP);
        rejectPose(localChoiceFixture, MediumBoardNormalAdmission.Contract.RB56_SOURCE_SIZED,
            "policy3 admission@4 rejects policy4 constraints");
        rejectPose(sourceSized, MediumBoardNormalAdmission.Contract.RB56_LOCAL_POSE_CHOICE,
            "policy4 admission@5 rejects policy3 constraints");
    }

    private static void verifyDependencyCanonicalField() throws Exception {
        TroubleshootBoard legacy = emptyDependencyBoard(PcbPassivePosePolicy.LEGACY);
        TroubleshootBoard explicit = emptyDependencyBoard(PcbPassivePosePolicy.ORTHOGONAL);
        TroubleshootBoard sizingOnly = emptyDependencyBoard(PcbPassivePosePolicy.SIZING_ONLY);
        TroubleshootBoard sourceSized = emptyDependencyBoard(
            PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL);
        TroubleshootBoard localChoice = emptyDependencyBoard(
            PcbPassivePosePolicy.LOCAL_POSE_CHOICE);
        String legacyCanonical = appendBoard(legacy);
        String explicitCanonical = appendBoard(explicit);
        String sizingCanonical = appendBoard(sizingOnly);
        String sourceSizedCanonical = appendBoard(sourceSized);
        String localChoiceCanonical = appendBoard(localChoice);
        String policyField = dependencyField("placement.passive-pose-policy", "1");
        String sizingField = dependencyField("placement.passive-pose-policy", "2");
        String sourceSizedField = dependencyField("placement.passive-pose-policy", "3");
        String localChoiceField = dependencyField("placement.passive-pose-policy", "4");
        require(legacyCanonical.equals(expectedLegacyEmptyBoardCanonical(legacy)),
            "pose0 appendBoard bytes retain the pre-policy canonical sequence exactly");
        require(legacyCanonical.indexOf("placement.passive-pose-policy") < 0,
            "pose0 leaves the dependency canonical unchanged by policy metadata");
        require(explicitCanonical.contains(policyField),
            "pose1 appends the explicit dependency policy field");
        require(explicitCanonical.replace(policyField, "").equals(legacyCanonical),
            "paired appendBoard canonical differs only by the pose1 field");
        require(sizingCanonical.contains(sizingField) &&
                sizingCanonical.replace(sizingField, "").equals(legacyCanonical),
            "policy2 appends its own namespace without changing the prior dependency fields");
        require(sourceSizedCanonical.contains(sourceSizedField) &&
                sourceSizedCanonical.replace(sourceSizedField, "").equals(legacyCanonical),
            "policy3 appends its own dependency namespace without changing prior fields");
        require(localChoiceCanonical.contains(localChoiceField) &&
                localChoiceCanonical.replace(localChoiceField, "").equals(legacyCanonical),
            "policy4 appends its own dependency namespace without changing prior fields");
    }

    private static void verifyAdmissionCanonicalIdentity() throws Exception {
        MediumBoardNormalAdmission legacy = admission(MediumBoardNormalAdmission.Contract.RB56);
        MediumBoardNormalAdmission explicit = admission(
            MediumBoardNormalAdmission.Contract.RB56_ORTHOGONAL);
        MediumBoardNormalAdmission sizing = admission(
            MediumBoardNormalAdmission.Contract.RB56_SIZING_CONTROL);
        MediumBoardNormalAdmission sourceSized = admission(
            MediumBoardNormalAdmission.Contract.RB56_SOURCE_SIZED);
        MediumBoardNormalAdmission localChoice = admission(
            MediumBoardNormalAdmission.Contract.RB56_LOCAL_POSE_CHOICE);
        String priorPhysicalContract = "board=RB56_CONTROL_BOARD;parts=40..60;terminals=2..7;" +
            "additional-packages=ISOLATED_CONVERTER_7,OPTOCOUPLER_4,RADIAL_INDUCTOR_2;" +
            "barrier=PRIMARY|SECONDARY|80-drawing-units;UAC=TOP/0;UFB=TOP/180;" +
            "manufacturing-rating=NONE";
        require("RB56_BOARD_NORMAL@1".equals(legacy.identity()),
            "legacy admission retains its literal identity");
        require("RB56_BOARD_NORMAL@2".equals(explicit.identity()),
            "explicit pose admission has a separate literal identity");
        require("RB56_BOARD_NORMAL@3".equals(sizing.identity()),
            "control17 admission has its separate literal identity");
        require("RB56_BOARD_NORMAL@4".equals(sourceSized.identity()),
            "placement18 admission has a separate literal identity");
        require("RB56_BOARD_NORMAL@5".equals(localChoice.identity()),
            "placement19 admission has a separate literal identity");
        require(legacy.canonical().contains(admissionField("rb56-physical-contract",
                priorPhysicalContract)),
            "legacy RB56 canonical retains the exact prior physical contract field");
        String explicitWithoutPose = explicit.canonical().replace(
            admissionField("rb56-passive-pose-policy",
                MediumBoardNormalAdmission.RB56_PASSIVE_POSE_POLICY), "");
        require(legacy.canonical().substring(MediumBoardNormalAdmission.RB56_IDENTITY.length())
                .equals(explicitWithoutPose.substring(
                    MediumBoardNormalAdmission.RB56_POSE_IDENTITY.length())),
            "policy1 preserves every legacy canonical field after its versioned identity");
        require(explicit.canonical().contains(admissionField("rb56-passive-pose-policy",
                "policy=1;passives=AXIAL_RESISTOR,AXIAL_DIODE," +
                    "RADIAL_ELECTROLYTIC_CAPACITOR,RADIAL_CERAMIC_CAPACITOR;" +
                    "allowed-poses=TOP/0,TOP/90")),
            "policy1 canonical admission retains its exact bounded pose policy");
        String sizingWithoutPolicy = sizing.canonical().replace(
            admissionField("rb56-passive-pose-policy",
                MediumBoardNormalAdmission.RB56_SIZING_CONTROL_POLICY), "");
        require(legacy.canonical().substring(MediumBoardNormalAdmission.RB56_IDENTITY.length())
                .equals(sizingWithoutPolicy.substring(
                    MediumBoardNormalAdmission.RB56_SIZING_CONTROL_IDENTITY.length())),
            "control admission preserves every legacy mechanical bound after its identity");
        require(sizing.canonical().contains(admissionField("rb56-passive-pose-policy",
                "policy=2;passives=AXIAL_RESISTOR,AXIAL_DIODE," +
                    "RADIAL_ELECTROLYTIC_CAPACITOR,RADIAL_CERAMIC_CAPACITOR;" +
                    "allowed-poses=TOP/0,TOP/90;sizing-only=true;visit-source=TOP/0")),
            "control canonical binds policy2, sizing-only use, and TOP0 visits");
        String sourceSizedWithoutPolicy = sourceSized.canonical().replace(
            admissionField("rb56-passive-pose-policy",
                MediumBoardNormalAdmission.RB56_SOURCE_SIZED_POLICY), "");
        require(legacy.canonical().substring(MediumBoardNormalAdmission.RB56_IDENTITY.length())
                .equals(sourceSizedWithoutPolicy.substring(
                    MediumBoardNormalAdmission.RB56_SOURCE_SIZED_IDENTITY.length())),
            "source-sized admission preserves every prior mechanical bound after its identity");
        require(sourceSized.canonical().contains(admissionField("rb56-physical-contract",
                priorPhysicalContract)),
            "placement18 preserves the 40..60/2..7 RB56 physical gate with UAC TOP0/UFB TOP180");
        require(sourceSized.canonical().contains(admissionField("rb56-passive-pose-policy",
                "policy=3;passives=AXIAL_RESISTOR,AXIAL_DIODE," +
                    "RADIAL_ELECTROLYTIC_CAPACITOR,RADIAL_CERAMIC_CAPACITOR;" +
                    "allowed-poses=TOP/0,TOP/90;sizing=source-TOP/0;" +
                    "visits=even-TOP/0,odd-TOP/90")),
            "placement18 canonical binds policy3 source sizing and the even/odd TOP0/TOP90 schedule");
        String localChoiceWithoutPolicy = localChoice.canonical().replace(
            admissionField("rb56-passive-pose-policy",
                MediumBoardNormalAdmission.RB56_LOCAL_POSE_CHOICE_POLICY), "");
        require(legacy.canonical().substring(MediumBoardNormalAdmission.RB56_IDENTITY.length())
                .equals(localChoiceWithoutPolicy.substring(
                    MediumBoardNormalAdmission.RB56_LOCAL_POSE_CHOICE_IDENTITY.length())),
            "placement19 preserves the prior RB56 admission fields after its identity");
        require(localChoice.canonical().contains(admissionField("rb56-physical-contract",
                priorPhysicalContract)),
            "placement19 retains the RB56 40..60/2..7 physical bounds");
        require(localChoice.canonical().contains(admissionField("rb56-passive-pose-policy",
                "policy=4;passives=AXIAL_RESISTOR,AXIAL_DIODE," +
                    "RADIAL_ELECTROLYTIC_CAPACITOR,RADIAL_CERAMIC_CAPACITOR;" +
                    "allowed-poses=TOP/0,TOP/90;sizing=source-TOP/0;" +
                    "selection=candidate1,repeated-control,medium,nonanchor;" +
                    "origins=existing;pose-order=TOP/0,TOP/90;ties=first;work=count-before-fit")),
            "placement19 canonical binds source sizing and strict per-origin TOP0-first choice");
    }

    private static void accept(Fixture fixture,
            MediumBoardNormalAdmission.Contract contract, String why) {
        PcbTwoLayerRules.requirePassivePoseAdmission(fixture.board, fixture.layout, contract);
        require(true, why);
    }

    private static void rejectPose(final Fixture fixture,
            final MediumBoardNormalAdmission.Contract contract, String why) {
        reject(new Runnable() {
            public void run() {
                PcbTwoLayerRules.requirePassivePoseAdmission(
                    fixture.board, fixture.layout, contract);
            }
        }, why);
    }

    private static Fixture fixture(int policyVersion, String id, String type,
            PhysicalPackage boardPackage, PhysicalPackage placementPackage,
            PcbRotation rotation, PcbBoardSide side) {
        Fixture[] fixtures = fixture(policyVersion, new String[] {id}, new String[] {type},
            new PhysicalPackage[] {boardPackage}, new PhysicalPackage[] {placementPackage},
            new PcbRotation[] {rotation}, new PcbBoardSide[] {side});
        return fixtures[0];
    }

    private static Fixture[] fixture(int policyVersion, String[] ids, String[] types,
            PhysicalPackage[] boardPackages, PhysicalPackage[] placementPackages,
            PcbRotation[] rotations, PcbBoardSide[] sides) {
        if (ids.length != types.length || ids.length != boardPackages.length ||
                ids.length != placementPackages.length || ids.length != rotations.length ||
                ids.length != sides.length)
            throw new IllegalArgumentException("Mismatched passive fixture inventory");
        TroubleshootBoard board = new TroubleshootBoard("RB56_CONTROL_BOARD");
        Vector<PcbPlacementConstraints.Part> declarations =
            new Vector<PcbPlacementConstraints.Part>();
        for (int index = 0; index < ids.length; index++) {
            board.addComponent(new BoardComponent(ids[index], types[index], boardPackages[index]));
            declarations.add(new PcbPlacementConstraints.Part(ids[index], "passives",
                "Passive parts", "PRIMARY", PcbPlacementConstraints.Anchor.NONE, 20));
        }
        PcbPlacementConstraints constraints = constraints(policyVersion, declarations);
        board.setPlacementConstraints(constraints);
        PcbBoardLayout layout = new PcbBoardLayout(3200, 1000,
            new Rectangle(100, 100, 2800, 700), new Rectangle(2950, 100, 200, 700));
        for (int index = 0; index < ids.length; index++) {
            PcbPackagePose pose = new PcbPackagePose(180 + index * 220, 180,
                rotations[index], sides[index]);
            layout.addComponent(PcbComponentPlacement.fromPhysicalGeometry(ids[index], pose,
                placementPackages[index], placementPackages[index].getGeometry()));
        }
        return new Fixture[] {new Fixture(board, layout)};
    }

    private static String passiveType(PhysicalPackage physical) {
        if (physical == PhysicalPackages.AXIAL_RESISTOR) return "RESISTOR";
        if (physical == PhysicalPackages.AXIAL_DIODE) return "DIODE";
        if (physical == PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR ||
                physical == PhysicalPackages.RADIAL_CERAMIC_CAPACITOR) return "CAPACITOR";
        throw new IllegalArgumentException("Unknown canonical passive package");
    }

    private static PcbPlacementConstraints constraints(int policyVersion,
            Vector<PcbPlacementConstraints.Part> declarations) {
        return new PcbPlacementConstraints(declarations,
            new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.BOTTOM,
            MediumBoardPhysicalPolicy.ID, MediumBoardPhysicalPolicy.VERSION, 14, true,
            PcbTraceWidthProfile.ROLE_WIDTHS, policyVersion);
    }

    private static TroubleshootBoard emptyDependencyBoard(int policyVersion) {
        TroubleshootBoard board = new TroubleshootBoard("PASSIVE_POSE_CANONICAL_FIXTURE");
        board.setPlacementConstraints(constraints(policyVersion,
            new Vector<PcbPlacementConstraints.Part>()));
        return board;
    }

    private static String appendBoard(TroubleshootBoard board) throws Exception {
        Method method = GenerationDependencyContext.class.getDeclaredMethod("appendBoard",
            StringBuilder.class, TroubleshootBoard.class, BoardPhysicalSpecifications.class);
        method.setAccessible(true);
        StringBuilder canonical = new StringBuilder();
        method.invoke(null, canonical, board, new BoardPhysicalSpecifications());
        return canonical.toString();
    }

    private static String expectedLegacyEmptyBoardCanonical(TroubleshootBoard board) {
        StringBuilder expected = new StringBuilder();
        emptyList(expected, "board.components");
        emptyList(expected, "board.pads");
        emptyList(expected, "board.nets");
        dependencyField(expected, "routing.score", PcbRouteMetrics.SCORE_VERSION);
        frame(expected, "single-copper-layer");
        frame(expected, PcbCopperLayer.BOTTOM.name());
        PcbPlacementConstraints placement = board.getPlacementConstraints();
        dependencyField(expected, "physical.policy", placement.getPhysicalPolicyIdentity());
        if (placement.placementDensityVersion != 1)
            dependencyField(expected, "placement.density",
                Integer.toString(placement.placementDensityVersion));
        if (!placement.getTraceWidthProfile().isLegacyUniform())
            dependencyField(expected, "physical.trace-width-profile",
                placement.getTraceWidthProfile().canonical());
        if (placement.requiresTwoLayerRouting)
            dependencyField(expected, "placement.required-route",
                MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER);
        if (MediumBoardPhysicalPolicy.selected(placement))
            dependencyField(expected, "physical.policy.contract",
                MediumBoardPhysicalPolicy.canonical());
        emptyList(expected, "board.power-inputs");
        return expected.toString();
    }

    private static void emptyList(StringBuilder out, String key) {
        out.append('F');
        frame(out, key);
        frame(out, "0");
    }

    private static void dependencyField(StringBuilder out, String key, String value) {
        out.append('F');
        frame(out, key);
        frame(out, value);
    }

    private static String dependencyField(String key, String value) {
        StringBuilder out = new StringBuilder();
        dependencyField(out, key, value);
        return out.toString();
    }

    private static void frame(StringBuilder out, String value) {
        out.append('V').append(value.length()).append(':').append(value).append(';');
    }

    private static MediumBoardNormalAdmission admission(
            MediumBoardNormalAdmission.Contract contract) throws Exception {
        Constructor<MediumBoardNormalAdmission> constructor =
            MediumBoardNormalAdmission.class.getDeclaredConstructor(
                MediumBoardNormalAdmission.Contract.class, Long.TYPE, Long.TYPE,
                String.class, String.class, String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(contract, Long.valueOf(17L), Long.valueOf(-29L),
            MediumBoardPhysicalPolicy.identity(), "receipt", "board", "layout");
    }

    private static String admissionField(String name, String value) {
        return name.length() + ":" + name + "=" + value.length() + ":" + value + ";";
    }

    private static void reject(final Runnable action, String why) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, why);
    }

    private static void require(boolean condition, String why) {
        assertions++;
        if (!condition) throw new AssertionError(why);
    }

    private static final class Fixture {
        final TroubleshootBoard board;
        final PcbBoardLayout layout;
        Fixture(TroubleshootBoard board, PcbBoardLayout layout) {
            this.board = board;
            this.layout = layout;
        }
    }
}
