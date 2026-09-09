package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable physical materialization metadata for one resolved package.
 *
 * <p>This type intentionally contains no CircuitJS object, inventory, slot,
 * runtime, or mutation owner.  CircuitJS element ownership is represented by
 * stable owner/id pairs and is resolved only by the materializer after the
 * electrical construction receipt has been frozen.</p>
 */
final class PhysicalConstructionPartDeclaration {
    enum PartPolicy {
        FIXED,
        MUTABLE_RESISTOR
    }

    private final String constructionOwnerKey;
    private final String ownerKey;
    private final String providerId;
    private final int providerVersion;
    private final String runtimeProviderId;
    private final String componentId;
    private final PhysicalPackage physicalPackage;
    private final String publicType;
    private final String designator;
    private final PhysicalSpecification specification;
    private final PhysicalNameplate nameplate;
    private final String backingOwnerKey;
    private final String backingElementId;
    private final String secondaryOwnerKey;
    private final String secondaryElementId;
    private final String firstAttachmentOwnerKey;
    private final String firstAttachmentElementId;
    private final String secondAttachmentOwnerKey;
    private final String secondAttachmentElementId;
    private final String faultOwnerKey;
    private final String faultElementId;
    private final PartPolicy policy;
    private final ComposedBlockContribution.FaultSpec.Kind faultKind;
    private final String faultLocalId;
    private final double faultEffectiveOhms;
    private final String faultFamilyId;
    private final String repairOwnerKey;
    private final String repairLocalComponentId;
    private final String repairComponentId;
    private final boolean countInMappedIdentity;
    private final List<PhysicalConstructionTerminalDeclaration> terminals;

    private PhysicalConstructionPartDeclaration(Builder builder) {
        constructionOwnerKey = required(builder.constructionOwnerKey,
            "constructionOwnerKey");
        ownerKey = required(builder.ownerKey, "ownerKey");
        providerId = required(builder.providerId, "providerId");
        if (builder.providerVersion < 1)
            throw new IllegalArgumentException("Invalid physical provider version");
        providerVersion = builder.providerVersion;
        runtimeProviderId = required(builder.runtimeProviderId, "runtimeProviderId");
        componentId = required(builder.componentId, "componentId");
        if (builder.physicalPackage == null)
            throw new IllegalArgumentException("Physical package is required");
        physicalPackage = builder.physicalPackage;
        publicType = required(builder.publicType, "publicType");
        designator = required(builder.designator, "designator");
        if (builder.specification == null || builder.nameplate == null)
            throw new IllegalArgumentException("Physical specification and nameplate are required");
        specification = builder.specification;
        nameplate = builder.nameplate;
        backingOwnerKey = required(builder.backingOwnerKey, "backingOwnerKey");
        backingElementId = required(builder.backingElementId, "backingElementId");
        secondaryOwnerKey = builder.secondaryOwnerKey;
        secondaryElementId = builder.secondaryElementId;
        firstAttachmentOwnerKey = builder.firstAttachmentOwnerKey;
        firstAttachmentElementId = builder.firstAttachmentElementId;
        secondAttachmentOwnerKey = builder.secondAttachmentOwnerKey;
        secondAttachmentElementId = builder.secondAttachmentElementId;
        faultOwnerKey = builder.faultOwnerKey;
        faultElementId = builder.faultElementId;
        if (builder.policy == null)
            throw new IllegalArgumentException("Physical part policy is required");
        policy = builder.policy;
        if (policy == PartPolicy.MUTABLE_RESISTOR &&
                !(specification instanceof ResistorNameplate))
            throw new IllegalArgumentException("Only resistor parts may be mutable");
        faultKind = builder.faultKind;
        faultLocalId = builder.faultLocalId;
        faultEffectiveOhms = builder.faultEffectiveOhms;
        faultFamilyId = builder.faultFamilyId;
        repairOwnerKey = builder.repairOwnerKey;
        repairLocalComponentId = builder.repairLocalComponentId;
        repairComponentId = builder.repairComponentId;
        countInMappedIdentity = builder.countInMappedIdentity;
        if (faultKind == null) {
            if (faultLocalId != null || faultFamilyId != null ||
                    repairOwnerKey != null || repairLocalComponentId != null ||
                    repairComponentId != null || faultOwnerKey != null ||
                    faultElementId != null)
                throw new IllegalArgumentException("Incomplete non-fault physical declaration");
        } else {
            if (policy != PartPolicy.MUTABLE_RESISTOR)
                throw new IllegalArgumentException("Only mutable resistors may own physical faults");
            required(faultLocalId, "faultLocalId");
            required(faultFamilyId, "faultFamilyId");
            required(repairOwnerKey, "repairOwnerKey");
            required(repairLocalComponentId, "repairLocalComponentId");
            required(repairComponentId, "repairComponentId");
            if (!finitePositive(faultEffectiveOhms))
                throw new IllegalArgumentException("Invalid physical fault value");
            if (faultKind == ComposedBlockContribution.FaultSpec.Kind.OPEN) {
                required(faultOwnerKey, "faultOwnerKey");
                required(faultElementId, "faultElementId");
            }
            if (faultKind == ComposedBlockContribution.FaultSpec.Kind.INCORRECT_RESISTANCE &&
                    (faultOwnerKey != null || faultElementId != null))
                throw new IllegalArgumentException("Incorrect-resistance fault cannot use an open switch");
            if (faultKind != ComposedBlockContribution.FaultSpec.Kind.OPEN &&
                    faultKind != ComposedBlockContribution.FaultSpec.Kind.INCORRECT_RESISTANCE)
                throw new IllegalArgumentException("Unsupported physical fault kind");
            if (faultLocalId.length() == 0 || faultFamilyId.length() == 0 ||
                    repairOwnerKey.length() == 0 || repairLocalComponentId.length() == 0 ||
                    repairComponentId.length() == 0)
                throw new IllegalArgumentException("Incomplete physical fault ownership");
        }
        if ((secondaryOwnerKey == null) != (secondaryElementId == null) ||
                (firstAttachmentOwnerKey == null) != (firstAttachmentElementId == null) ||
                (secondAttachmentOwnerKey == null) != (secondAttachmentElementId == null))
            throw new IllegalArgumentException("Incomplete physical attachment ownership");
        if (builder.terminals == null || builder.terminals.isEmpty())
            throw new IllegalArgumentException("Physical package terminals are required");
        terminals = immutableTerminals(builder.terminals);
        if (terminals.size() != physicalPackage.getTerminalCount())
            throw new IllegalArgumentException("Physical declaration terminal count mismatch: " +
                componentId);
    }

    static Builder builder(String constructionOwnerKey, String ownerKey,
            String providerId, int providerVersion, String runtimeProviderId,
            String componentId, PhysicalPackage physicalPackage, String publicType,
            String designator, PhysicalSpecification specification,
            PhysicalNameplate nameplate, String backingOwnerKey,
            String backingElementId, PartPolicy policy) {
        return new Builder(constructionOwnerKey, ownerKey, providerId, providerVersion,
            runtimeProviderId, componentId, physicalPackage, publicType, designator,
            specification, nameplate, backingOwnerKey, backingElementId, policy);
    }

    static final class Builder {
        private final String constructionOwnerKey;
        private final String ownerKey;
        private final String providerId;
        private final int providerVersion;
        private final String runtimeProviderId;
        private final String componentId;
        private final PhysicalPackage physicalPackage;
        private final String publicType;
        private final String designator;
        private final PhysicalSpecification specification;
        private final PhysicalNameplate nameplate;
        private final String backingOwnerKey;
        private final String backingElementId;
        private final PartPolicy policy;
        private String secondaryOwnerKey;
        private String secondaryElementId;
        private String firstAttachmentOwnerKey;
        private String firstAttachmentElementId;
        private String secondAttachmentOwnerKey;
        private String secondAttachmentElementId;
        private String faultOwnerKey;
        private String faultElementId;
        private ComposedBlockContribution.FaultSpec.Kind faultKind;
        private String faultLocalId;
        private double faultEffectiveOhms;
        private String faultFamilyId;
        private String repairOwnerKey;
        private String repairLocalComponentId;
        private String repairComponentId;
        private boolean countInMappedIdentity;
        private final ArrayList<PhysicalConstructionTerminalDeclaration> terminals =
            new ArrayList<PhysicalConstructionTerminalDeclaration>();

        private Builder(String constructionOwnerKey, String ownerKey,
                String providerId, int providerVersion, String runtimeProviderId,
                String componentId, PhysicalPackage physicalPackage, String publicType,
                String designator, PhysicalSpecification specification,
                PhysicalNameplate nameplate, String backingOwnerKey,
                String backingElementId, PartPolicy policy) {
            this.constructionOwnerKey = constructionOwnerKey;
            this.ownerKey = ownerKey;
            this.providerId = providerId;
            this.providerVersion = providerVersion;
            this.runtimeProviderId = runtimeProviderId;
            this.componentId = componentId;
            this.physicalPackage = physicalPackage;
            this.publicType = publicType;
            this.designator = designator;
            this.specification = specification;
            this.nameplate = nameplate;
            this.backingOwnerKey = backingOwnerKey;
            this.backingElementId = backingElementId;
            this.policy = policy;
        }

        Builder secondary(String owner, String elementId) {
            secondaryOwnerKey = owner;
            secondaryElementId = elementId;
            return this;
        }

        Builder attachments(String firstOwner, String firstElement,
                String secondOwner, String secondElement) {
            firstAttachmentOwnerKey = firstOwner;
            firstAttachmentElementId = firstElement;
            secondAttachmentOwnerKey = secondOwner;
            secondAttachmentElementId = secondElement;
            return this;
        }

        Builder fault(ComposedBlockContribution.FaultSpec.Kind kind,
                String localId, double effectiveOhms, String familyId,
                String owner, String elementId, String repairOwner,
                String repairLocal, String repairComponent) {
            faultKind = kind;
            faultLocalId = localId;
            faultEffectiveOhms = effectiveOhms;
            faultFamilyId = familyId;
            faultOwnerKey = owner;
            faultElementId = elementId;
            repairOwnerKey = repairOwner;
            repairLocalComponentId = repairLocal;
            repairComponentId = repairComponent;
            return this;
        }

        Builder countInMappedIdentity(boolean value) {
            countInMappedIdentity = value;
            return this;
        }

        Builder terminal(PhysicalConstructionTerminalDeclaration terminal) {
            if (terminal == null)
                throw new IllegalArgumentException("Physical terminal is required");
            terminals.add(terminal);
            return this;
        }

        PhysicalConstructionPartDeclaration build() {
            return new PhysicalConstructionPartDeclaration(this);
        }
    }

    private static List<PhysicalConstructionTerminalDeclaration> immutableTerminals(
            List<PhysicalConstructionTerminalDeclaration> source) {
        TreeMap<String, PhysicalConstructionTerminalDeclaration> byPad =
            new TreeMap<String, PhysicalConstructionTerminalDeclaration>();
        TreeMap<String, PhysicalConstructionTerminalDeclaration> byPackageTerminal =
            new TreeMap<String, PhysicalConstructionTerminalDeclaration>();
        TreeMap<String, PhysicalConstructionTerminalDeclaration> byTerminal =
            new TreeMap<String, PhysicalConstructionTerminalDeclaration>();
        for (PhysicalConstructionTerminalDeclaration terminal : source) {
            if (byPad.put(terminal.getPadId(), terminal) != null ||
                    byPackageTerminal.put(terminal.getPackageTerminalId(), terminal) != null ||
                    byTerminal.put(terminal.getTerminalId(), terminal) != null)
                throw new IllegalArgumentException("Duplicate physical terminal declaration");
        }
        return Collections.unmodifiableList(new ArrayList<PhysicalConstructionTerminalDeclaration>(source));
    }

    String getConstructionOwnerKey() { return constructionOwnerKey; }
    String getOwnerKey() { return ownerKey; }
    String getProviderId() { return providerId; }
    int getProviderVersion() { return providerVersion; }
    String getRuntimeProviderId() { return runtimeProviderId; }
    String getComponentId() { return componentId; }
    PhysicalPackage getPhysicalPackage() { return physicalPackage; }
    String getPublicType() { return publicType; }
    String getDesignator() { return designator; }
    PhysicalSpecification getSpecification() { return specification; }
    PhysicalNameplate getNameplate() { return nameplate; }
    String getBackingOwnerKey() { return backingOwnerKey; }
    String getBackingElementId() { return backingElementId; }
    boolean hasSecondary() { return secondaryElementId != null; }
    String getSecondaryOwnerKey() { return secondaryOwnerKey; }
    String getSecondaryElementId() { return secondaryElementId; }
    boolean hasAttachments() { return firstAttachmentElementId != null; }
    String getFirstAttachmentOwnerKey() { return firstAttachmentOwnerKey; }
    String getFirstAttachmentElementId() { return firstAttachmentElementId; }
    String getSecondAttachmentOwnerKey() { return secondAttachmentOwnerKey; }
    String getSecondAttachmentElementId() { return secondAttachmentElementId; }
    boolean hasFault() { return faultKind != null; }
    String getFaultOwnerKey() { return faultOwnerKey; }
    String getFaultElementId() { return faultElementId; }
    PartPolicy getPolicy() { return policy; }
    boolean isMutableResistor() { return policy == PartPolicy.MUTABLE_RESISTOR; }
    ComposedBlockContribution.FaultSpec.Kind getFaultKind() { return faultKind; }
    String getFaultLocalId() { return faultLocalId; }
    double getFaultEffectiveOhms() { return faultEffectiveOhms; }
    String getFaultFamilyId() { return faultFamilyId; }
    String getRepairOwnerKey() { return repairOwnerKey; }
    String getRepairLocalComponentId() { return repairLocalComponentId; }
    String getRepairComponentId() { return repairComponentId; }
    boolean isCountedInMappedIdentity() { return countInMappedIdentity; }
    List<PhysicalConstructionTerminalDeclaration> getTerminals() { return terminals; }

    private static String required(String value, String field) {
        if (value == null || value.length() == 0)
            throw new IllegalArgumentException("Missing physical declaration " + field);
        return value;
    }

    private static boolean finitePositive(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value > 0.0;
    }
}

/** One board pad, public package terminal, resolved net, and endpoint identity. */
final class PhysicalConstructionTerminalDeclaration {
    private final String padId;
    private final String terminalId;
    private final String packageTerminalId;
    private final String netId;
    private final String endpointId;
    private final String manifestKey;
    private final String manifestBlockKey;

    PhysicalConstructionTerminalDeclaration(String padId, String terminalId,
            String packageTerminalId, String netId, String endpointId,
            String manifestKey, String manifestBlockKey) {
        this.padId = required(padId, "padId");
        this.terminalId = required(terminalId, "terminalId");
        this.packageTerminalId = required(packageTerminalId, "packageTerminalId");
        this.netId = required(netId, "netId");
        this.endpointId = required(endpointId, "endpointId");
        this.manifestKey = required(manifestKey, "manifestKey");
        this.manifestBlockKey = required(manifestBlockKey, "manifestBlockKey");
    }

    String getPadId() { return padId; }
    String getTerminalId() { return terminalId; }
    String getPackageTerminalId() { return packageTerminalId; }
    String getNetId() { return netId; }
    String getEndpointId() { return endpointId; }
    String getManifestKey() { return manifestKey; }
    String getManifestBlockKey() { return manifestBlockKey; }

    private static String required(String value, String field) {
        if (value == null || value.length() == 0)
            throw new IllegalArgumentException("Missing physical terminal " + field);
        return value;
    }
}

/** Device-owned external input declaration; the connector remains physical-owner scoped. */
final class PhysicalExternalInputDeclaration {
    private final String providerOwnerKey;
    private final String inputId;
    private final String positivePadId;
    private final String returnPadId;
    private final String positiveNetId;
    private final String returnNetId;
    private final double nominalVoltage;

    PhysicalExternalInputDeclaration(String providerOwnerKey, String inputId,
            String positivePadId, String returnPadId, String positiveNetId,
            String returnNetId, double nominalVoltage) {
        this.providerOwnerKey = required(providerOwnerKey, "providerOwnerKey");
        this.inputId = required(inputId, "inputId");
        this.positivePadId = required(positivePadId, "positivePadId");
        this.returnPadId = required(returnPadId, "returnPadId");
        this.positiveNetId = required(positiveNetId, "positiveNetId");
        this.returnNetId = required(returnNetId, "returnNetId");
        if (Double.isNaN(nominalVoltage) || Double.isInfinite(nominalVoltage) ||
                nominalVoltage <= 0.0)
            throw new IllegalArgumentException("Invalid external input voltage");
        this.nominalVoltage = nominalVoltage;
    }

    String getProviderOwnerKey() { return providerOwnerKey; }
    String getInputId() { return inputId; }
    String getPositivePadId() { return positivePadId; }
    String getReturnPadId() { return returnPadId; }
    String getPositiveNetId() { return positiveNetId; }
    String getReturnNetId() { return returnNetId; }
    double getNominalVoltage() { return nominalVoltage; }

    private static String required(String value, String field) {
        if (value == null || value.length() == 0)
            throw new IllegalArgumentException("Missing external input " + field);
        return value;
    }
}

/** Immutable provider output preserving local/device declaration order separately. */
final class PhysicalConstructionDeclarations {
    private final List<PhysicalConstructionPartDeclaration> localParts;
    private final List<PhysicalConstructionPartDeclaration> deviceParts;
    private final List<PhysicalExternalInputDeclaration> externalInputs;

    PhysicalConstructionDeclarations(List<PhysicalConstructionPartDeclaration> localParts,
            List<PhysicalConstructionPartDeclaration> deviceParts,
            List<PhysicalExternalInputDeclaration> externalInputs) {
        this.localParts = immutable(localParts, "localParts");
        this.deviceParts = immutable(deviceParts, "deviceParts");
        this.externalInputs = immutable(externalInputs, "externalInputs");
    }

    List<PhysicalConstructionPartDeclaration> getLocalParts() { return localParts; }
    List<PhysicalConstructionPartDeclaration> getDeviceParts() { return deviceParts; }
    List<PhysicalExternalInputDeclaration> getExternalInputs() { return externalInputs; }

    List<PhysicalConstructionPartDeclaration> getBoardParts() {
        ArrayList<PhysicalConstructionPartDeclaration> result =
            new ArrayList<PhysicalConstructionPartDeclaration>();
        result.addAll(localParts);
        result.addAll(deviceParts);
        return Collections.unmodifiableList(result);
    }

    List<PhysicalConstructionPartDeclaration> getSlotParts(boolean controlled) {
        ArrayList<PhysicalConstructionPartDeclaration> result =
            new ArrayList<PhysicalConstructionPartDeclaration>();
        if (controlled) {
            result.addAll(localParts);
            result.addAll(deviceParts);
        } else {
            result.addAll(deviceParts);
            result.addAll(localParts);
        }
        return Collections.unmodifiableList(result);
    }

    private static <T> List<T> immutable(List<T> values, String field) {
        if (values == null)
            throw new IllegalArgumentException("Missing physical declarations " + field);
        for (T value : values)
            if (value == null)
                throw new IllegalArgumentException("Null physical declaration in " + field);
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }
}

/** Board/specification envelope produced once before any physical runtime mutation. */
final class PhysicalConstructionMetadata {
    private final TroubleshootBoard board;
    private final BoardPhysicalSpecifications specifications;
    private final PhysicalConstructionDeclarations declarations;

    PhysicalConstructionMetadata(TroubleshootBoard board,
            BoardPhysicalSpecifications specifications,
            PhysicalConstructionDeclarations declarations) {
        if (board == null || specifications == null || declarations == null)
            throw new IllegalArgumentException("Incomplete physical construction metadata");
        this.board = board;
        this.specifications = specifications;
        this.declarations = declarations;
    }

    TroubleshootBoard getBoard() { return board; }
    BoardPhysicalSpecifications getSpecifications() { return specifications; }
    PhysicalConstructionDeclarations getDeclarations() { return declarations; }
}

/** Receipt returned by the generic physical materializer. */
final class PhysicalMaterializationReceipt {
    private final PhysicalBoardRuntime runtime;
    private final Map<String, BoundedGeneratedBoardAssembler.RuntimeTarget> runtimeTargets;
    private final List<GeneratedFaultCandidate> candidates;
    private final GeneratedFaultCandidate selectedCandidate;
    private final Map<String, LEDElm> operationalLeds;

    PhysicalMaterializationReceipt(PhysicalBoardRuntime runtime,
            Map<String, BoundedGeneratedBoardAssembler.RuntimeTarget> runtimeTargets,
            List<GeneratedFaultCandidate> candidates,
            GeneratedFaultCandidate selectedCandidate, Map<String, LEDElm> operationalLeds) {
        if (runtime == null || runtimeTargets == null || candidates == null ||
                selectedCandidate == null || operationalLeds == null)
            throw new IllegalArgumentException("Incomplete physical materialization receipt");
        this.runtime = runtime;
        this.runtimeTargets = Collections.unmodifiableMap(
            new TreeMap<String, BoundedGeneratedBoardAssembler.RuntimeTarget>(runtimeTargets));
        this.candidates = Collections.unmodifiableList(
            new ArrayList<GeneratedFaultCandidate>(candidates));
        this.selectedCandidate = selectedCandidate;
        this.operationalLeds = Collections.unmodifiableMap(
            new TreeMap<String, LEDElm>(operationalLeds));
    }

    PhysicalBoardRuntime getRuntime() { return runtime; }
    Map<String, BoundedGeneratedBoardAssembler.RuntimeTarget> getRuntimeTargets() {
        return runtimeTargets;
    }
    List<GeneratedFaultCandidate> getCandidates() { return candidates; }
    GeneratedFaultCandidate getSelectedCandidate() { return selectedCandidate; }

    void bindOperationalStates(GeneratedComponentOperationalStates states) {
        if (states == null)
            throw new IllegalArgumentException("Operational state registry is required");
        for (Map.Entry<String, LEDElm> entry : operationalLeds.entrySet())
            states.bindLed(entry.getKey(), entry.getValue());
    }
}
