package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;

/**
 * Private candidate construction context.  The context is the only owner of
 * the candidate element collection and global binding registries.  Providers
 * see only a scope and opaque handles.
 */
final class ElectricalConstructionContext {
    enum Boundary { ALLOCATE, BIND, JOIN, FINISH }

    interface FailureProbe {
        void after(Boundary boundary);
    }

    interface Scope {
        ElementHandle allocate(String elementId);
        ElementHandle resistor(String elementId, int x, int y, int x2, int y2,
                double resistance);
        ElementHandle wire(String elementId, int x, int y, int x2, int y2);
        ElementHandle switchElement(String elementId, int x, int y, int x2, int y2);
        ElementHandle voltageSource(String elementId, int x, int y, int x2, int y2,
                double voltage);
        ElementHandle ground(String elementId, int x, int y, int x2, int y2);
        ElementHandle nmos(String elementId, int x, int y, int x2, int y2,
                double threshold, double beta);
        ElementHandle led(String elementId, int x, int y, int x2, int y2,
                String model, int red, int green, int blue);
        Point point(ElementHandle handle, int postIndex);
        SecondaryHandle secondaryOpenPath(String helperId, ElementHandle upstream,
                int postIndex);
        TerminalHandle terminal(String elementId, String terminalId);
        void bindComponent(String componentLocalId, ElementHandle primary,
                ElementHandle auxiliary);
        void bindPad(String padLocalId, TerminalHandle endpoint);
        PhysicalUnitReceipt declareUnit(String unitId);
        ContributionConstructionReceipt finish();
    }

    interface DeviceScope {
        ElementHandle allocate(String elementId);
        ElementHandle resistor(String elementId, int x, int y, int x2, int y2,
                double resistance);
        ElementHandle wire(String elementId, int x, int y, int x2, int y2);
        ElementHandle switchElement(String elementId, int x, int y, int x2, int y2);
        ElementHandle voltageSource(String elementId, int x, int y, int x2, int y2,
                double voltage);
        ElementHandle ground(String elementId, int x, int y, int x2, int y2);
        Point point(ElementHandle handle, int postIndex);
        TerminalHandle terminal(String elementId, String terminalId);
        TerminalHandle terminal(ElementHandle element, String terminalId);
        void bindPad(String ownerKey, String padLocalId, TerminalHandle endpoint);
        void bindComponentConnection(String componentOwner, String componentLocalId,
                String padLocalId, TerminalHandle boardEndpoint,
                TerminalHandle componentEndpoint, ElementHandle connectionElement);
        void bindComponent(String componentOwner, String componentLocalId,
                ElementHandle primary, ElementHandle auxiliary);
        void bindPower(String powerInputId, ElementHandle backing,
                ElementHandle control);
        ElementHandle join(String bridgeElementId, TerminalHandle first,
                TerminalHandle second);
        ControlCommandHandle command(String joinId, ElementHandle switchElement);
        ControlCommandHandle command(String joinId);
        DeviceJoinReceipt finish();
    }

    private final ElectricalRealizationSpec spec;
    private final TroubleshootBoard board;
    private final Vector<CircuitElm> elements = new Vector<CircuitElm>();
    private final FailureProbe probe;
    private final GeneratedComponentBindings componentBindings;
    private final GeneratedExternalPowerBindings powerBindings;
    private final GeneratedComponentConnectionBindings connectionBindings;
    private final TreeMap<String, ElementHandle> handles =
            new TreeMap<String, ElementHandle>();
    private final TreeMap<String, SecondaryHandle> secondaryPaths =
            new TreeMap<String, SecondaryHandle>();
    private final TreeMap<String, ContributionConstructionReceipt> contributionReceipts =
            new TreeMap<String, ContributionConstructionReceipt>();
    private final HashSet<String> openedScopes = new HashSet<String>();
    private final TreeMap<String, HashSet<String>> coordinateOwners =
            new TreeMap<String, HashSet<String>>();
    private final TreeMap<String, Integer> joinCounts = new TreeMap<String, Integer>();
    private final TreeMap<String, Integer> bridgeCounts = new TreeMap<String, Integer>();
    private final TreeMap<String, ControlCommandHandle> commands =
            new TreeMap<String, ControlCommandHandle>();
    private final HashSet<String> completedScopes = new HashSet<String>();
    private final HashSet<String> boundPadIds = new HashSet<String>();
    private final HashSet<String> boundComponentIds = new HashSet<String>();
    private final HashSet<String> boundConnectionPadIds = new HashSet<String>();
    private final HashSet<String> boundPowerIds = new HashSet<String>();
    private final HashSet<String> allocatedBridgeIds = new HashSet<String>();
    private DeviceJoinReceipt deviceReceipt;
    private boolean finished;
    private boolean aborted;
    private int frozenAllocatedCount;
    private int frozenBindingCount;
    private int frozenJoinCount;
    private Throwable abortFailure;
    private boolean cleanupSucceeded = true;
    /* One canonical receipt is issued per construction attempt.  Keeping the
     * identity on the context lets provenance checks reject package-local
     * copies that reuse this attempt's spec/board with another receipt body. */
    private ConstructionReceipt issuedReceipt;

    private ElectricalConstructionContext(ElectricalRealizationSpec spec,
            TroubleshootBoard board, FailureProbe probe) {
        if (spec == null || board == null)
            throw new IllegalArgumentException("Electrical construction inputs are required");
        this.spec = spec;
        this.board = board;
        this.probe = probe;
        this.componentBindings = new GeneratedComponentBindings(board);
        this.powerBindings = new GeneratedExternalPowerBindings(board);
        this.connectionBindings = new GeneratedComponentConnectionBindings(board);
    }

    static ElectricalConstructionContext begin(ElectricalRealizationSpec spec,
            TroubleshootBoard board, FailureProbe probe) {
        ElectricalConstructionContext context = new ElectricalConstructionContext(spec, board, probe);
        context.validateBoardInventory();
        return context;
    }

    Scope scope(String ownerKey, String providerId, int providerVersion) {
        ensureOpen();
        ElectricalRealizationSpec.ProviderDeclaration declaration =
                spec.getProviderDeclaration(ownerKey);
        if (declaration == null || declaration.isDeviceOwner() ||
                !declaration.getProviderId().equals(providerId) ||
                declaration.getProviderVersion() != providerVersion)
            throw new IllegalArgumentException("Unknown or mismatched provider scope " +
                ownerKey + " " + providerId + "@" + providerVersion);
        openScope(ownerKey);
        return new LocalScope(declaration);
    }

    DeviceScope deviceScope(String ownerKey) {
        ensureOpen();
        if (!"device".equals(ownerKey))
            throw new IllegalArgumentException("Only the device owner may open a device scope");
        ElectricalRealizationSpec.ProviderDeclaration declaration =
                spec.getProviderDeclaration(ownerKey);
        if (declaration == null || !declaration.isDeviceOwner())
            throw new IllegalArgumentException("Device construction declaration is missing");
        openScope(ownerKey);
        return new DeviceLocalScope(declaration);
    }

    private void openScope(String ownerKey) {
        if (!openedScopes.add(ownerKey))
            throw new IllegalStateException("Construction scope already opened: " + ownerKey);
    }

    void finish() {
        ensureOpen();
        if (completedScopes.size() != spec.getProviderDeclarations().size())
            throw new IllegalStateException("Not all construction scopes were finished");
        for (String key : spec.getElementDeclarations().keySet())
            if (!handles.containsKey(key))
                throw new IllegalStateException("Declared element was not allocated: " + key);
        for (String bridgeId : spec.getBridgeSpecs().keySet())
            if (!allocatedBridgeIds.contains(bridgeId))
                throw new IllegalStateException("Declared bridge was not joined: " + bridgeId);
        for (String padId : spec.getPadBindings().keySet())
            if (!boundPadIds.contains(padId))
                throw new IllegalStateException("Declared board pad was not bound: " + padId);
        for (String componentId : spec.getPackageMap().getPackages().keySet())
            if (!boundComponentIds.contains(componentId))
                throw new IllegalStateException("Declared component was not bound: " + componentId);
        for (String padId : spec.getRequiredConnectionPadIds())
            if (!boundConnectionPadIds.contains(padId))
                throw new IllegalStateException("Required detachable pad was not bound: " + padId);
        if (boundPadIds.size() != spec.getPadBindings().size())
            throw new IllegalStateException("Unexpected board pad binding count");
        if (!boundComponentIds.equals(spec.getPackageMap().getPackages().keySet()))
            throw new IllegalStateException("Unexpected component binding inventory");
        if (!boundConnectionPadIds.equals(spec.getRequiredConnectionPadIds()))
            throw new IllegalStateException("Unexpected detachable connection inventory");
        if (!boundPowerIds.equals(spec.getPowerInputs().keySet()))
            throw new IllegalStateException("External power bindings are incomplete");
        validateResolvedElementChoices();
        validateSemanticJoins();
        validateAllCoordinatesAndPosts();
        finished = true;
        freezeCounts();
        after(Boundary.FINISH);
    }

    ConstructionReceipt getReceipt() {
        if (!finished && !aborted)
            throw new IllegalStateException("Construction is not finished");
        if (issuedReceipt == null) {
            issuedReceipt = new ConstructionReceipt(spec, elements, handles, secondaryPaths,
                    contributionReceipts, componentBindings, powerBindings,
                    connectionBindings, deviceReceipt, frozenAllocatedCount,
                    frozenBindingCount, frozenJoinCount, bridgeCounts, aborted, abortFailure,
                    this);
        }
        return issuedReceipt;
    }

    boolean isIssuedReceipt(ConstructionReceipt candidate) {
        return candidate != null && issuedReceipt == candidate;
    }

    boolean abort(Throwable failure) {
        if (aborted)
            return cleanupSucceeded;
        // Construction cleanup may never delete elements from the active owner.
        if (CircuitElm.sim != null && CircuitElm.sim.elmList != null)
            for (CircuitElm element : elements)
                if (CircuitElm.sim.elmList.contains(element)) {
                    abortFailure = retain(failure,
                        new IllegalStateException("Cannot abort an active candidate graph"));
                    cleanupSucceeded = false;
                    return false;
                }
        abortFailure = failure;
        freezeCounts();
        aborted = true;
        Throwable cleanupFailure = null;
        for (String inputId : boundPowerIds) {
            try {
                powerBindings.getBinding(inputId).setConnected(false);
            } catch (Throwable cleanup) {
                cleanupFailure = retain(cleanupFailure, cleanup);
            }
        }
        if (!elements.isEmpty() && CircuitElm.sim == null)
            cleanupFailure = retain(cleanupFailure,
                    new IllegalStateException("CircuitJS owner is unavailable during cleanup"));
        if (CircuitElm.sim != null) {
            for (int index = elements.size() - 1; index >= 0; index--) {
                try {
                    elements.get(index).delete();
                } catch (Throwable cleanup) {
                    cleanupFailure = retain(cleanupFailure, cleanup);
                }
            }
        }
        cleanupFailure = clearPrivateBindings(cleanupFailure);
        if (cleanupFailure != null)
            abortFailure = retain(abortFailure, cleanupFailure);
        cleanupSucceeded = cleanupFailure == null;
        elements.clear();
        handles.clear();
        secondaryPaths.clear();
        contributionReceipts.clear();
        commands.clear();
        coordinateOwners.clear();
        completedScopes.clear();
        boundPadIds.clear();
        boundComponentIds.clear();
        boundConnectionPadIds.clear();
        boundPowerIds.clear();
        allocatedBridgeIds.clear();
        return cleanupSucceeded;
    }

    private Throwable clearPrivateBindings(Throwable failure) {
        try { board.getSimulationBindings().clearForAbortedConstruction(board, this); }
        catch (Throwable problem) { failure = retain(failure, problem); }
        try { componentBindings.clearForAbortedConstruction(board); }
        catch (Throwable problem) { failure = retain(failure, problem); }
        try { powerBindings.clearForAbortedConstruction(board); }
        catch (Throwable problem) { failure = retain(failure, problem); }
        try { connectionBindings.clearForAbortedConstruction(board); }
        catch (Throwable problem) { failure = retain(failure, problem); }
        return failure;
    }

    boolean isFinished() { return finished; }
    boolean isAborted() { return aborted; }
    TroubleshootBoard getBoard() { return board; }
    ElectricalRealizationSpec getSpec() { return spec; }
    int getAllocatedElementCount() { return frozenOr(elements.size(), frozenAllocatedCount); }
    int getBindingCount() { return frozenBindingCount; }
    int getJoinCount() { return frozenJoinCount; }
    int getBridgeCount() { return bridgeCounts.size(); }
    boolean isCleanupSucceeded() { return cleanupSucceeded; }

    CircuitElm getElementForDeveloperVerification(String ownerKey, String elementId) {
        ensureLiveCandidate();
        return requireHandle(ownerKey, elementId).element;
    }

    Vector<CircuitElm> getElementsForDeveloperVerification() {
        ensureLiveCandidate();
        return new Vector<CircuitElm>(elements);
    }

    Vector<CircuitElm> getElements() {
        ensureFinished();
        return new Vector<CircuitElm>(elements);
    }
    GeneratedComponentBindings getComponentBindings() {
        ensureFinished();
        return componentBindings;
    }
    GeneratedExternalPowerBindings getPowerBindings() {
        ensureFinished();
        return powerBindings;
    }
    GeneratedComponentConnectionBindings getConnectionBindings() {
        ensureFinished();
        return connectionBindings;
    }
    ConstructionReceipt getConstructionReceipt() { return getReceipt(); }

    private int frozenOr(int current, int frozen) {
        return aborted || finished ? frozen : current;
    }

    private void freezeCounts() {
        frozenAllocatedCount = elements.size();
        frozenBindingCount = countBindings();
        frozenJoinCount = countJoins();
    }

    private int countBindings() {
        return boundPadIds.size();
    }

    private int countJoins() {
        int count = 0;
        for (Integer value : joinCounts.values())
            count += value.intValue();
        return count;
    }

    private void ensureOpen() {
        if (finished)
            throw new IllegalStateException("Construction is already finished");
        if (aborted)
            throw new IllegalStateException("Construction was aborted");
    }

    private void ensureFinished() {
        if (!finished || aborted)
            throw new IllegalStateException("Construction is not finished");
    }

    private void ensureLiveCandidate() {
        if (aborted)
            throw new IllegalStateException("Construction was aborted");
    }

    private int powerBindingsCount() {
        int count = 0;
        for (String inputId : spec.getPowerInputs().keySet()) {
            try {
                powerBindings.getBinding(inputId);
                count++;
            } catch (IllegalArgumentException ignored) { }
        }
        return count;
    }

    private void ElementDeclarationCheck(ElementHandle handle) {
        if (handle.declaration == null || handle.element == null)
            throw new IllegalStateException("Allocated element has no declaration");
    }

    /** A raw allocation path cannot bypass the resolved electrical choices. */
    private void validateResolvedElementChoices() {
        for (ElementHandle handle : handles.values()) {
            ElectricalRealizationSpec.ProviderDeclaration provider =
                spec.getProviderDeclaration(handle.ownerKey);
            if ("RESISTOR".equals(handle.getKind())) {
                requireRecipeResistance(provider, handle.elementId,
                    ((ResistorElm) handle.element).getResistance());
            } else if ("NMOS".equals(handle.getKind())) {
                requireNmosChoice(provider, handle.elementId,
                    ((NMosfetElm) handle.element).vt, ((NMosfetElm) handle.element).beta);
            } else if ("LED".equals(handle.getKind())) {
                LEDElm led = (LEDElm) handle.element;
                requireLedChoice(provider, handle.elementId, led.modelName,
                    led.colorR, led.colorG, led.colorB);
            } else if ("VOLTAGE".equals(handle.getKind()) &&
                    ((DCVoltageElm) handle.element).maxVoltage !=
                        ElectricalRealizationSpec.EXTERNAL_SUPPLY_VOLTS) {
                throw new IllegalArgumentException("Construction substituted its external supply value");
            }
        }
    }

    private void requireNmosChoice(ElectricalRealizationSpec.ProviderDeclaration provider,
            String elementId, double threshold, double beta) {
        ComposedBlockContribution contribution = provider.getContribution();
        ComposedBlockContribution.NmosRecipe recipe = contribution == null ? null :
            contribution.getNmosRecipes().get(elementId);
        if (recipe == null || !"NMOS".equals(recipe.getModelId()) ||
                threshold != ElectricalRealizationSpec.CONTROLLED_NMOS_THRESHOLD_VOLTS ||
                beta != ElectricalRealizationSpec.CONTROLLED_NMOS_BETA)
            throw new IllegalArgumentException("Construction substituted its resolved NMOS model");
    }

    private void requireLedChoice(ElectricalRealizationSpec.ProviderDeclaration provider,
            String elementId, String model, double red, double green, double blue) {
        ComposedBlockContribution contribution = provider.getContribution();
        ComposedBlockContribution.LedRecipe recipe = contribution == null ? null :
            contribution.getLedRecipes().get(elementId);
        if (recipe == null || (!"LED".equals(recipe.getModelId()) &&
                !ElectricalRealizationSpec.CONTROLLED_LED_MODEL.equals(recipe.getModelId())) ||
                !ElectricalRealizationSpec.CONTROLLED_LED_MODEL.equals(model) ||
                red != 1.0 || green != 0.0 || blue != 0.0)
            throw new IllegalArgumentException("Construction substituted its resolved LED model");
    }


    private void requireRecipeResistance(
            ElectricalRealizationSpec.ProviderDeclaration declaration, String elementId,
            double resistance) {
        if (declaration.getContribution() == null)
            throw new IllegalArgumentException("Provider has no resistor recipe");
        ComposedBlockContribution.ResistorRecipe recipe =
                declaration.getContribution().getResistor(elementId);
        if (recipe == null || Double.isNaN(resistance) || Double.isInfinite(resistance) ||
                recipe.getResistanceOhms() != resistance)
            throw new IllegalArgumentException("Construction substituted resolved resistor value: " +
                    declaration.getOwnerKey() + "/" + elementId);
    }

    private Point localPoint(ElementHandle handle, int postIndex) {
        ensureLiveCandidate();
        if (handle == null || handle.context != this || postIndex < 0 ||
                postIndex >= handle.element.getPostCount())
            throw new IllegalArgumentException("Invalid electrical post");
        requireConfigured(handle);
        Point point = handle.element.getPost(postIndex);
        if (point == null)
            throw new IllegalStateException("Electrical post is not configured");
        ElectricalRealizationSpec.SolverReservation reservation =
                reservationFor(handle.ownerKey);
        return new Point(point.x - reservation.getOriginX(),
                point.y - reservation.getOriginY());
    }

    private static Throwable retain(Throwable original, Throwable next) {
        if (original == null) return next;
        if (original != next) original.addSuppressed(next);
        return original;
    }

    private void after(Boundary boundary) {
        if (probe != null)
            probe.after(boundary);
    }

    private void validateBoardInventory() {
        board.validate();
        BoardSimulationBindings simulation = board.getSimulationBindings();
        if (simulation.isConstructionAborted())
            throw new IllegalArgumentException("Cannot reuse an aborted construction board");
        if (simulation.isDeveloperVerificationReady())
            throw new IllegalStateException("Cannot construct against a settled board");

        Set<String> expectedComponents = spec.getPackageMap().getPackages().keySet();
        Set<String> actualComponents = new HashSet<String>(board.getComponentIds());
        if (!expectedComponents.equals(actualComponents))
            throw new IllegalArgumentException("Board component inventory does not match electrical spec");
        for (String componentId : expectedComponents) {
            BoardComponent component = board.getComponent(componentId);
            PhysicalPackage expected = spec.getPackageMap().getPackages().get(componentId);
            if (component == null || expected == null ||
                    !expected.isEquivalentTo(component.getPhysicalPackage()))
                throw new IllegalArgumentException("Board physical package mismatch: " + componentId);
        }

        Set<String> expectedPads = spec.getPadBindings().keySet();
        Set<String> actualPads = new HashSet<String>(board.getPadIds());
        if (!expectedPads.equals(actualPads))
            throw new IllegalArgumentException("Board pad inventory does not match electrical spec");
        for (String padId : expectedPads) {
            BoardPad pad = board.getPad(padId);
            ElectricalRealizationSpec.PadBindingSpec expected = spec.getPadBinding(padId);
            if (pad == null || expected == null ||
                    !expected.getComponentId().equals(pad.getComponentId()) ||
                    !expected.getTerminalId().equals(pad.getTerminalId()) ||
                    !expected.getNetId().equals(pad.getNetId()))
                throw new IllegalArgumentException("Board pad declaration mismatch: " + padId);
            if (simulation.getEndpoint(padId) != null)
                throw new IllegalStateException("Board pad already has a simulation binding: " + padId);
        }

        Set<String> expectedNets = spec.getExpectedNetIds();
        Set<String> actualNets = new HashSet<String>(board.getNetIds());
        if (!expectedNets.equals(actualNets))
            throw new IllegalArgumentException("Board net inventory does not match electrical spec");

        Set<String> expectedPower = spec.getPowerInputs().keySet();
        Set<String> actualPower = new HashSet<String>(board.getPowerInputIds());
        if (!expectedPower.equals(actualPower))
            throw new IllegalArgumentException("Board power-input inventory does not match electrical spec");
        for (String inputId : expectedPower) {
            ElectricalRealizationSpec.PowerInputSpec expected = spec.getPowerInputs().get(inputId);
            ExternalBoardPowerInput actual = board.getPowerInput(inputId);
            if (actual == null || !expected.getPositivePadId().equals(actual.getPositivePadId()) ||
                    !expected.getReturnPadId().equals(actual.getReturnPadId()) ||
                    !expected.getPositiveNetId().equals(actual.getPositiveNetId()) ||
                    !expected.getReturnNetId().equals(actual.getReturnNetId()))
                throw new IllegalArgumentException("Board power-input declaration mismatch: " + inputId);
        }
    }

    private ElementHandle allocateElement(String ownerKey, String elementId,
            String expectedKind) {
        ensureOpen();
        ElectricalRealizationSpec.ElementDeclaration declaration =
                spec.getElementDeclaration(ownerKey, elementId);
        if (declaration == null)
            throw new IllegalArgumentException("Undeclared element " + ownerKey + "/" + elementId);
        if (expectedKind != null && !expectedKind.equals(declaration.getKind()))
            throw new IllegalArgumentException("Element kind mismatch " + ownerKey + "/" + elementId);
        String key = ElectricalRealizationSpec.elementKey(ownerKey, elementId);
        if (handles.containsKey(key))
            throw new IllegalStateException("Duplicate electrical element allocation: " + key);
        ElementHandle handle;
        if ("RESISTOR".equals(declaration.getKind()))
            handle = new ElementHandle(this, ownerKey, elementId, new ResistorElm(0, 0));
        else if ("WIRE".equals(declaration.getKind()))
            handle = new ElementHandle(this, ownerKey, elementId, new WireElm(0, 0));
        else if ("SWITCH".equals(declaration.getKind()))
            handle = new ElementHandle(this, ownerKey, elementId, new SwitchElm(0, 0));
        else if ("VOLTAGE".equals(declaration.getKind()))
            handle = new ElementHandle(this, ownerKey, elementId, new DCVoltageElm(0, 0));
        else if ("GROUND".equals(declaration.getKind()))
            handle = new ElementHandle(this, ownerKey, elementId, new GroundElm(0, 0));
        else if ("NMOS".equals(declaration.getKind()))
            handle = new ElementHandle(this, ownerKey, elementId, new NMosfetElm(0, 0));
        else if ("LED".equals(declaration.getKind()))
            handle = new ElementHandle(this, ownerKey, elementId, new LEDElm(0, 0));
        else if ("FAULT_HELPER".equals(declaration.getKind()))
            handle = new ElementHandle(this, ownerKey, elementId, new SwitchElm(0, 0));
        else
            throw new IllegalArgumentException("Unsupported electrical element kind " +
                    declaration.getKind());
        register(handle, false);
        return handle;
    }

    private ElementHandle allocateElement(String ownerKey, String elementId,
            CircuitElm element, boolean bridge) {
        if (element == null)
            throw new IllegalArgumentException("Null electrical element");
        ElectricalRealizationSpec.ElementDeclaration declaration =
                spec.getElementDeclaration(ownerKey, elementId);
        if (declaration == null)
            throw new IllegalArgumentException("Undeclared element " + ownerKey + "/" + elementId);
        String key = ElectricalRealizationSpec.elementKey(ownerKey, elementId);
        if (handles.containsKey(key))
            throw new IllegalStateException("Duplicate electrical element allocation: " + key);
        ElementHandle handle = new ElementHandle(this, ownerKey, elementId, element);
        register(handle, bridge);
        return handle;
    }

    private void register(ElementHandle handle, boolean bridge) {
        String key = handle.key();
        // Registration occurs before drag/configuration.  This is intentional:
        // a later CircuitJS operation cannot create an unowned candidate.
        elements.add(handle.element);
        handles.put(key, handle);
        // A fresh multi-terminal element has no derived posts until drag/setPoints.
        // Retain ownership first; never inspect virtual posts during registration.
        if (!bridge && !insideReservation(handle.ownerKey,
                new Point(handle.element.x, handle.element.y)))
            throw new IllegalArgumentException("Initial solver anchor escaped reservation: " + key);
        after(Boundary.ALLOCATE);
    }

    private void updateCoordinates(ElementHandle handle, boolean bridge) {
        validateCoordinates(handle, bridge);
        handle.coordinatesReady = true;
    }

    private void requireConfigured(ElementHandle handle) {
        if (!handle.coordinatesReady)
            throw new IllegalStateException("Electrical element posts are not configured: " + handle.key());
    }

    private void validateCoordinates(ElementHandle handle, boolean bridge) {
        purgeStaleCoordinateClaims();
        ArrayList<String> newKeys = new ArrayList<String>();
        for (int index = 0; index < handle.element.getPostCount(); index++) {
            Point point = handle.element.getPost(index);
            if (point == null)
                throw new IllegalStateException("Configured element has no declared post: " +
                        handle.key() + "/" + index);
            if (!bridge && !insideReservation(handle.ownerKey, point))
                throw new IllegalArgumentException("Solver coordinate escaped reservation for " +
                        handle.key() + ": " + pointKey(point));
            String pointKey = pointKey(point);
            HashSet<String> previous = coordinateOwners.get(pointKey);
            if (previous != null && !bridge) {
                for (String priorKey : previous) {
                    ElementHandle prior = handles.get(priorKey);
                    if (prior != null && !prior.ownerKey.equals(handle.ownerKey))
                        throw new IllegalStateException("Accidental solver coordinate contact at " +
                                pointKey + " between " + prior.ownerKey + " and " +
                                handle.ownerKey);
                }
            }
            newKeys.add(pointKey);
        }
        if (bridge && handle.element.getPost(0) != null &&
                handle.element.getPost(1) != null)
            validateBridgeEndpoints(handle);
        for (String priorKey : handle.coordinateKeys) {
            HashSet<String> owners = coordinateOwners.get(priorKey);
            if (owners != null) {
                owners.remove(handle.key());
                if (owners.isEmpty()) coordinateOwners.remove(priorKey);
            }
        }
        if (!bridge) {
            for (String pointKey : newKeys) {
                HashSet<String> owners = coordinateOwners.get(pointKey);
                if (owners == null) {
                    owners = new HashSet<String>();
                    coordinateOwners.put(pointKey, owners);
                }
                owners.add(handle.key());
            }
        }
        handle.coordinateKeys = newKeys;
    }

    private void purgeStaleCoordinateClaims() {
        ArrayList<String> empty = new ArrayList<String>();
        for (Map.Entry<String, HashSet<String>> entry : coordinateOwners.entrySet()) {
            HashSet<String> live = entry.getValue();
            ArrayList<String> stale = new ArrayList<String>();
            for (String key : live) {
                ElementHandle handle = handles.get(key);
                if (handle == null || !handle.coordinateKeys.contains(entry.getKey()))
                    stale.add(key);
            }
            live.removeAll(stale);
            if (live.isEmpty()) empty.add(entry.getKey());
        }
        for (String key : empty) coordinateOwners.remove(key);
    }

    private String pointKey(Point point) {
        return point.x + ":" + point.y;
    }

    private ElementHandle requireHandle(String ownerKey, String elementId) {
        ElementHandle result = handles.get(ElectricalRealizationSpec.elementKey(ownerKey, elementId));
        if (result == null)
            throw new IllegalArgumentException("Unknown electrical element " + ownerKey + "/" + elementId);
        return result;
    }

    private void configureDrag(ElementHandle handle, int x2, int y2, boolean bridge) {
        handle.element.drag(x2, y2);
        updateCoordinates(handle, bridge);
    }

    private void configureDrag(ElementHandle handle, String ownerKey,
            int x2, int y2, boolean bridge) {
        configureDrag(handle, worldX(ownerKey, x2), worldY(ownerKey, y2), bridge);
    }

    private int snap(int value) {
        if (CircuitElm.sim == null)
            throw new IllegalStateException("CircuitJS owner is required for graph allocation");
        return CircuitElm.sim.snapGrid(value);
    }

    private int worldX(String ownerKey, int localX) {
        ElectricalRealizationSpec.SolverReservation reservation =
                reservationFor(ownerKey);
        return checkedAdd(reservation.getOriginX(), localX, "x");
    }

    private int worldY(String ownerKey, int localY) {
        ElectricalRealizationSpec.SolverReservation reservation =
                reservationFor(ownerKey);
        return checkedAdd(reservation.getOriginY(), localY, "y");
    }

    private ElectricalRealizationSpec.SolverReservation reservationFor(String ownerKey) {
        ElectricalRealizationSpec.SolverReservation reservation =
                spec.getSolverReservations().get(ownerKey + "/local");
        if (reservation == null)
            reservation = spec.getSolverReservations().get(ownerKey + "/infrastructure");
        if (reservation == null)
            throw new IllegalArgumentException("Missing solver reservation for " + ownerKey);
        return reservation;
    }

    private int checkedAdd(int first, int second, String axis) {
        long result = (long) first + (long) second;
        if (result < 0L || result > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Solver " + axis + " coordinate overflow");
        return (int) result;
    }

    private boolean insideReservation(String ownerKey, Point point) {
        ElectricalRealizationSpec.SolverReservation reservation = reservationFor(ownerKey);
        long x = point.x;
        long y = point.y;
        return x >= reservation.getOriginX() &&
                x <= (long) reservation.getOriginX() + reservation.getWidth() &&
                y >= reservation.getOriginY() &&
                y <= (long) reservation.getOriginY() + reservation.getHeight();
    }

    private void validateBridgeEndpoints(ElementHandle handle) {
        ElectricalRealizationSpec.BridgeSpec bridge =
                spec.getBridgeSpecs().get(handle.elementId);
        if (bridge == null)
            throw new IllegalArgumentException("Undeclared bridge element " + handle.elementId);
        if (!(handle.element instanceof WireElm))
            throw new IllegalArgumentException("Bridge is not a wire " + handle.elementId);
        validateBridgeEndpoint(bridge.getFirst(), handle.element.getPost(0), handle);
        validateBridgeEndpoint(bridge.getSecond(), handle.element.getPost(1), handle);
    }

    private void validateBridgeEndpoint(ElectricalRealizationSpec.EndpointRef expected,
            Point actual, ElementHandle bridge) {
        if (actual == null)
            throw new IllegalStateException("Bridge endpoint has no configured point: " +
                    bridge.elementId);
        ElementHandle endpoint = handles.get(ElectricalRealizationSpec.elementKey(
                expected.getOwnerKey(), expected.getElementId()));
        if (endpoint == null)
            throw new IllegalStateException("Bridge endpoint element was not allocated: " +
                    expected);
        ElectricalRealizationSpec.ElementDeclaration declaration =
                spec.getElementDeclaration(expected.getOwnerKey(), expected.getElementId());
        int post = declaration.getPostIndex(expected.getTerminalId());
        Point expectedPoint = endpoint.element.getPost(post);
        if (expectedPoint == null || !expectedPoint.equals(actual))
            throw new IllegalArgumentException("Bridge endpoint mismatch for " +
                    bridge.elementId + ": expected " + expected + " at " +
                     (expectedPoint == null ? "null" : pointKey(expectedPoint)) +
                     " but got " + pointKey(actual));
    }

    private void validateSemanticJoins() {
        HashSet<String> semanticIds = new HashSet<String>();
        for (ElectricalRealizationSpec.BridgeSpec bridge : spec.getBridgeSpecs().values()) {
            String semantic = bridge.getSemanticJoinId();
            if (semantic == null) {
                String inputId = bridge.getExternalPowerInputId();
                if (inputId == null || !spec.getPowerInputs().containsKey(inputId) ||
                        !boundPowerIds.contains(inputId))
                    throw new IllegalStateException("Bridge has no declared external power input: " +
                            bridge.getBridgeElementId());
            } else {
                if (!spec.getDeviceJoins().containsKey(semantic) ||
                        bridge.getExternalPowerInputId() != null)
                    throw new IllegalStateException("Bridge has no declared semantic join: " +
                            bridge.getBridgeElementId());
                semanticIds.add(semantic);
            }
        }
        if (!semanticIds.equals(joinCounts.keySet()))
            throw new IllegalStateException("Semantic join accounting does not match bridges");
        if (!semanticIds.equals(spec.getDeviceJoins().keySet()))
            throw new IllegalStateException("Declared semantic joins were not realized");
    }

    private void validateAllCoordinatesAndPosts() {
        purgeStaleCoordinateClaims();
        for (ElementHandle handle : handles.values()) {
            requireConfigured(handle);
            boolean bridge = spec.getBridgeSpecs().containsKey(handle.elementId);
            ElectricalRealizationSpec.ElementDeclaration declaration = handle.declaration;
            if (bridge) validateBridgeEndpoints(handle);
            for (int index = 0; index < handle.element.getPostCount(); index++) {
                Point point = handle.element.getPost(index);
                if (point == null)
                    throw new IllegalStateException("Allocated element has no configured post: " +
                            handle.key() + "/" + index);
                if (!bridge && !insideReservation(handle.ownerKey, point))
                    throw new IllegalStateException("Allocated element escaped reservation: " +
                            handle.key());
                if (index >= declaration.getPostIndexByTerminal().size() &&
                        "GROUND".equals(declaration.getKind()))
                    throw new IllegalStateException("Ground post declaration is inconsistent: " +
                            handle.key());
            }
            for (Integer post : declaration.getPostIndexByTerminal().values())
                if (post.intValue() >= handle.element.getPostCount())
                    throw new IllegalStateException("Declared post is not present: " + handle.key());
        }
        for (String padId : spec.getPadBindings().keySet()) {
            CircuitMeasurementEndpoint endpoint = board.getSimulationBindings().getEndpoint(padId);
            if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
                throw new IllegalStateException("Board pad is not bound to a CircuitJS post: " + padId);
            CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
            if (!elements.contains(post.getElement()) || post.getPostIndex() < 0 ||
                    post.getPostIndex() >= post.getElement().getPostCount() ||
                    post.getElement().getPost(post.getPostIndex()) == null)
                throw new IllegalStateException("Board pad endpoint is not owned: " + padId);
        }
        connectionBindings.validateAgainst(board, elements, componentBindings,
                powerBindings, null);
        validateElectricalNetPartition();
    }

    private void validateElectricalNetPartition() {
        HashMap<String, String> parent = new HashMap<String, String>();
        for (CircuitElm element : elements)
            for (int index = 0; index < element.getPostCount(); index++) {
                Point point = element.getPost(index);
                if (point == null)
                    throw new IllegalStateException("Electrical partition has an unconfigured post");
                String key = pointKey(point);
                if (!parent.containsKey(key)) parent.put(key, key);
            }
        for (CircuitElm element : elements) {
            boolean conductive = element instanceof WireElm ||
                    (element instanceof SwitchElm && ((SwitchElm) element).position == 0);
            if (conductive && element.getPostCount() >= 2)
                union(parent, pointKey(element.getPost(0)), pointKey(element.getPost(1)));
        }
        HashMap<String, String> representativeByNet = new HashMap<String, String>();
        HashMap<String, String> netByRepresentative = new HashMap<String, String>();
        for (String padId : spec.getPadBindings().keySet()) {
            CircuitPostMeasurementEndpoint endpoint =
                    (CircuitPostMeasurementEndpoint) board.getSimulationBindings().getEndpoint(padId);
            String node = find(parent, pointKey(endpoint.getElement().getPost(endpoint.getPostIndex())));
            String net = board.getPad(padId).getNetId();
            String priorNode = representativeByNet.get(net);
            if (priorNode != null && !find(parent, priorNode).equals(node))
                throw new IllegalStateException("Pads on one board net are electrically disconnected: " +
                        net);
            String priorNet = netByRepresentative.get(node);
            if (priorNet != null && !priorNet.equals(net))
                throw new IllegalStateException("Distinct board nets were electrically shorted: " +
                        priorNet + " and " + net);
            representativeByNet.put(net, node);
            netByRepresentative.put(node, net);
        }
    }

    private static String find(HashMap<String, String> parent, String key) {
        String current = parent.get(key);
        if (current == null) throw new IllegalStateException("Unknown electrical contact point");
        while (!current.equals(parent.get(current))) current = parent.get(current);
        String root = current;
        current = key;
        while (!root.equals(current)) {
            String next = parent.get(current);
            parent.put(current, root);
            current = next;
        }
        return root;
    }

    private static void union(HashMap<String, String> parent, String first, String second) {
        String firstRoot = find(parent, first);
        String secondRoot = find(parent, second);
        if (!firstRoot.equals(secondRoot)) parent.put(firstRoot, secondRoot);
    }

    private TerminalHandle terminal(String ownerKey, String elementId, String terminalId) {
        ElementHandle handle = requireHandle(ownerKey, elementId);
        requireConfigured(handle);
        ElectricalRealizationSpec.ElementDeclaration declaration =
                spec.getElementDeclaration(ownerKey, elementId);
        int post = declaration.getPostIndex(terminalId);
        if (post >= handle.element.getPostCount())
            throw new IllegalStateException("Element does not expose declared post " +
                    ownerKey + "/" + elementId + "/" + terminalId);
        return new TerminalHandle(this, handle, terminalId, post);
    }

    private SecondaryHandle secondary(final String ownerKey, final String helperId,
            ElementHandle upstream, int postIndex) {
        ensureOwned(ownerKey, upstream);
        requireConfigured(upstream);
        if (postIndex < 0 || postIndex >= upstream.element.getPostCount())
            throw new IllegalArgumentException("Invalid secondary path post");
        if (secondaryPaths.containsKey(ElectricalRealizationSpec.elementKey(ownerKey, helperId)))
            throw new IllegalStateException("Duplicate secondary path " + helperId);
        final ElementHandle[] helperHolder = new ElementHandle[1];
        ResistorSecondaryOpenPath path = ResistorSecondaryOpenPath.create(
                new CircuitPostMeasurementEndpoint(upstream.element, postIndex),
                new ResistorSecondaryOpenPath.AllocationObserver() {
                    public void allocated(CircuitElm element) {
                        helperHolder[0] = allocateElement(ownerKey, helperId, element, false);
                    }
                });
        ElementHandle helper = helperHolder[0];
        if (helper == null)
            throw new IllegalStateException("Secondary path allocation was not registered");
        updateCoordinates(helper, false);
        SecondaryHandle result = new SecondaryHandle(this, ownerKey, helperId, helper, path);
        secondaryPaths.put(result.key(), result);
        return result;
    }

    private void ensureOwned(String ownerKey, ElementHandle handle) {
        if (handle == null || handle.context != this || !ownerKey.equals(handle.ownerKey))
            throw new IllegalArgumentException("Foreign electrical construction handle");
    }

    private void ensureContext(TerminalHandle handle) {
        if (handle == null || handle.context != this)
            throw new IllegalArgumentException("Foreign electrical terminal handle");
    }

    private void bindComponent(String ownerKey, String componentLocalId,
            ElementHandle primary, ElementHandle auxiliary) {
        ensureOwned(ownerKey, primary);
        if (auxiliary != null)
            ensureOwned(ownerKey, auxiliary);
        String componentId = spec.getComponentId(ownerKey, componentLocalId);
        if (!spec.getPackageMap().getPackages().containsKey(componentId))
            throw new IllegalArgumentException("Component is not in the physical inventory: " +
                    componentId);
        if (!componentId.equals(primary.declaration.getComponentId()))
            throw new IllegalArgumentException("Component handle does not own " + componentId);
        if (auxiliary != null && !componentId.equals(auxiliary.declaration.getComponentId()))
            throw new IllegalArgumentException("Auxiliary handle belongs to another physical component");
        componentBindings.bindComponent(componentId, primary.element);
        if (auxiliary != null)
            componentBindings.bindAuxiliaryComponentElement(componentId, auxiliary.element);
        if (!boundComponentIds.add(componentId))
            throw new IllegalStateException("Duplicate component binding: " + componentId);
        after(Boundary.BIND);
    }

    private void bindDeviceComponent(String componentOwner, String componentLocalId,
            ElementHandle primary, ElementHandle auxiliary) {
        ensureOwned("device", primary);
        if (auxiliary != null) ensureOwned("device", auxiliary);
        String componentId = spec.getComponentId(componentOwner, componentLocalId);
        if (!spec.getPackageMap().getPackages().containsKey(componentId))
            throw new IllegalArgumentException("Device component is not in the physical inventory: " +
                    componentId);
        if (!componentId.equals(primary.declaration.getComponentId()))
            throw new IllegalArgumentException("Device handle does not own " + componentId);
        componentBindings.bindComponent(componentId, primary.element);
        if (auxiliary != null)
            componentBindings.bindAuxiliaryComponentElement(componentId, auxiliary.element);
        if (!boundComponentIds.add(componentId))
            throw new IllegalStateException("Duplicate component binding: " + componentId);
        after(Boundary.BIND);
    }

    private void bindPad(String ownerKey, String padLocalId, TerminalHandle endpoint) {
        bindPad(ownerKey, padLocalId, endpoint, false);
    }

    private void bindPad(String ownerKey, String padLocalId, TerminalHandle endpoint,
            boolean deviceEndpoint) {
        ensureContext(endpoint);
        ElectricalRealizationSpec.TerminalMapping mapping =
                findMappingForPad(ownerKey, padLocalId);
        if (mapping != null) {
            if (!deviceEndpoint && !ownerKey.equals(endpoint.handle.ownerKey))
                throw new IllegalArgumentException("Pad endpoint belongs to another owner");
            if (!deviceEndpoint && !mapping.getTerminalId().equals(endpoint.terminalId))
                throw new IllegalArgumentException("Pad terminal mismatch " + padLocalId);
        } else if (!spec.hasPad(ownerKey, padLocalId)) {
            throw new IllegalArgumentException("Undeclared electrical pad " + ownerKey + "/" + padLocalId);
        }
        String padId = spec.getPadId(ownerKey, padLocalId);
        if (!spec.getPadBindings().containsKey(padId))
            throw new IllegalArgumentException("Pad has no exact electrical binding declaration: " + padId);
        board.getSimulationBindings().bindPad(this, padId, endpoint.asEndpoint());
        if (!boundPadIds.add(padId))
            throw new IllegalStateException("Duplicate board pad binding: " + padId);
        after(Boundary.BIND);
    }

    private ElectricalRealizationSpec.TerminalMapping findMappingForPad(
            String ownerKey, String padLocalId) {
        for (ElectricalRealizationSpec.TerminalMapping mapping :
                spec.getTerminalMappings().values()) {
            if (ownerKey.equals(mapping.getOwnerKey()) &&
                    padLocalId.equals(mapping.getLocalId() + "." + mapping.getTerminalId()))
                return mapping;
        }
        return null;
    }

    private void bindConnection(String componentOwner, String componentLocalId,
            String padLocalId, TerminalHandle boardEndpoint,
            TerminalHandle componentEndpoint, ElementHandle connectionElement) {
        ensureContext(boardEndpoint);
        ensureContext(componentEndpoint);
        if (connectionElement == null || connectionElement.context != this ||
                !("device".equals(connectionElement.ownerKey) ||
                    componentOwner.equals(connectionElement.ownerKey)))
            throw new IllegalArgumentException("Connection element is not owned by the device or component");
        if (!componentOwner.equals(componentEndpoint.handle.ownerKey))
            throw new IllegalArgumentException("Component endpoint owner mismatch");
        String padId = spec.getPadId(componentOwner, padLocalId);
        if (!spec.getRequiredConnectionPadIds().contains(padId))
            throw new IllegalArgumentException("Pad is not a declared detachable connection: " + padId);
        ElectricalRealizationSpec.PadBindingSpec pad = spec.getPadBinding(padId);
        ElectricalRealizationSpec.TerminalMapping mapping = pad == null ? null :
                spec.getTerminalMapping(componentOwner, componentLocalId, pad.getTerminalId());
        String componentId = spec.getComponentId(componentOwner, componentLocalId);
        ElectricalRealizationSpec.EndpointRef actualComponentEndpoint =
                new ElectricalRealizationSpec.EndpointRef(componentEndpoint.handle.ownerKey,
                    componentEndpoint.handle.elementId, componentEndpoint.terminalId);
        if (mapping == null || !componentId.equals(mapping.getComponentId()) ||
                !componentId.equals(pad.getComponentId()) ||
                !mapping.getComponentEndpoint().equals(actualComponentEndpoint))
            throw new IllegalArgumentException("Detachable connection has the wrong declared component terminal: " + padId);
        componentBindings.getElements(componentId);
        if (boundConnectionPadIds.contains(padId))
            throw new IllegalStateException("Duplicate detachable connection: " + padId);
        CircuitMeasurementEndpoint canonical = board.getSimulationBindings().getEndpoint(padId);
        if (!(canonical instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalArgumentException("Detachable pad has no persistent endpoint: " + padId);
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) canonical;
        if (post.getElement() != boardEndpoint.handle.element ||
                post.getPostIndex() != boardEndpoint.postIndex)
            throw new IllegalArgumentException("Connection board endpoint differs from declared pad: " + padId);
        connectionBindings.bind(componentId, padId, canonical, componentEndpoint.asEndpoint(),
                connectionElement.element);
        boundConnectionPadIds.add(padId);
        after(Boundary.BIND);
    }

    private void bindPower(String powerInputId, ElementHandle backing,
            ElementHandle control) {
        if (!spec.getPowerInputs().containsKey(powerInputId) ||
                boundPowerIds.contains(powerInputId))
            throw new IllegalArgumentException("Unexpected or duplicate power binding: " +
                    powerInputId);
        if (backing == null || control == null || backing.context != this ||
                control.context != this || !"device".equals(backing.ownerKey) ||
                !"device".equals(control.ownerKey) || !(control.element instanceof SwitchElm))
            throw new IllegalArgumentException("External power elements are not device-owned");
        Vector<CircuitElm> backingElements = new Vector<CircuitElm>();
        backingElements.add(backing.element);
        backingElements.add(control.element);
        powerBindings.bindPowerInput(powerInputId, new ExternalPowerSimulationBinding(
                backingElements, new SwitchExternalPowerControl((SwitchElm) control.element)));
        boundPowerIds.add(powerInputId);
        after(Boundary.BIND);
    }

    private ElementHandle join(String bridgeElementId, TerminalHandle first,
            TerminalHandle second) {
        ensureContext(first);
        ensureContext(second);
        ElectricalRealizationSpec.BridgeSpec bridge = spec.getBridgeSpecs().get(bridgeElementId);
        if (bridge != null) {
            if (allocatedBridgeIds.contains(bridgeElementId))
                throw new IllegalStateException("Duplicate device bridge " + bridgeElementId);
            validateEndpointPair(bridge.getFirst(), bridge.getSecond(), first, second);
            Point firstPoint = first.handle.element.getPost(first.postIndex);
            Point secondPoint = second.handle.element.getPost(second.postIndex);
            if (firstPoint == null || secondPoint == null)
                throw new IllegalStateException("Device bridge endpoint has no configured point: " +
                        bridgeElementId);
            ElementHandle bridgeHandle = allocateElement("device", bridgeElementId,
                    new WireElm(firstPoint.x, firstPoint.y), true);
            configureDrag(bridgeHandle, secondPoint.x, secondPoint.y, true);
            allocatedBridgeIds.add(bridgeElementId);
            String semantic = bridge.getSemanticJoinId();
            bridgeCounts.put(bridge.getBridgeElementId(), Integer.valueOf(1));
            if (semantic != null) {
                Integer count = joinCounts.get(semantic);
                joinCounts.put(semantic, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
            }
            after(Boundary.JOIN);
            return bridgeHandle;
        }
        throw new IllegalArgumentException("Device joins must name a declared bridge element: " +
                bridgeElementId);
    }

    private void validateEndpointPair(ElectricalRealizationSpec.EndpointRef expectedFirst,
            ElectricalRealizationSpec.EndpointRef expectedSecond, TerminalHandle first,
            TerminalHandle second) {
        ElectricalRealizationSpec.EndpointRef actualFirst = new ElectricalRealizationSpec.EndpointRef(
                first.handle.ownerKey, first.handle.elementId, first.terminalId);
        ElectricalRealizationSpec.EndpointRef actualSecond = new ElectricalRealizationSpec.EndpointRef(
                second.handle.ownerKey, second.handle.elementId, second.terminalId);
        if (!expectedFirst.equals(actualFirst) || !expectedSecond.equals(actualSecond))
            throw new IllegalArgumentException("Device bridge terminal pair does not match declaration");
    }

    private void rejectBridgeAllocation(String elementId) {
        if (elementId != null && spec.getBridgeSpecs().containsKey(elementId))
            throw new IllegalArgumentException("Bridge wires must be constructed through typed device wire: " +
                    elementId);
    }

    private ControlCommandHandle command(String joinId, ElementHandle element) {
        if (element == null || element.context != this || !"device".equals(element.ownerKey) ||
                !(element.element instanceof SwitchElm))
            throw new IllegalArgumentException("Command switch is not device-owned");
        if (!spec.getDeviceJoins().containsKey(joinId))
            throw new IllegalArgumentException("Undeclared command join " + joinId);
        if (commands.containsKey(joinId))
            throw new IllegalStateException("Duplicate command handle " + joinId);
        ControlCommandHandle result = new ControlCommandHandle(this, joinId, element);
        commands.put(joinId, result);
        return result;
    }

    private ControlCommandHandle command(String joinId) {
        ControlCommandHandle result = commands.get(joinId);
        if (result == null)
            throw new IllegalArgumentException("Command handle is not allocated: " + joinId);
        return result;
    }

    private boolean isDeviceOwner(String ownerKey) { return "device".equals(ownerKey); }

    private void requireBoundPhysicalUnits(Map<String, PhysicalUnitReceipt> units) {
        for (PhysicalUnitReceipt unit : units.values())
            if (!boundComponentIds.contains(unit.getComponentId()))
                throw new IllegalStateException("Physical unit has no component binding: " +
                        unit.getOwnerKey() + "/" + unit.getUnitId());
    }

    private final class LocalScope implements Scope {
        private final ElectricalRealizationSpec.ProviderDeclaration declaration;
        private final TreeMap<String, PhysicalUnitReceipt> declaredUnits =
                new TreeMap<String, PhysicalUnitReceipt>();
        private boolean done;

        LocalScope(ElectricalRealizationSpec.ProviderDeclaration declaration) {
            this.declaration = declaration;
        }
        private void open() {
            ensureOpen();
            if (done) throw new IllegalStateException("Provider scope is finished");
        }
        public ElementHandle allocate(String elementId) {
            open();
            return allocateDefault(declaration.getOwnerKey(), elementId, false);
        }
        public ElementHandle resistor(String id, int x, int y, int x2, int y2,
                double resistance) {
            open();
            requireRecipeResistance(declaration, id, resistance);
            ElementHandle result = allocateTyped(declaration.getOwnerKey(), id, "RESISTOR", false,
                    x, y);
            ResistorElm resistor = (ResistorElm) result.element;
            resistor.drag(worldX(declaration.getOwnerKey(), x2),
                    worldY(declaration.getOwnerKey(), y2));
            resistor.setResistance(resistance);
            updateCoordinates(result, false);
            return result;
        }
        public ElementHandle wire(String id, int x, int y, int x2, int y2) {
            open();
            ElementHandle result = allocateTyped(declaration.getOwnerKey(), id, "WIRE", false,
                    x, y);
            WireElm wire = (WireElm) result.element;
            wire.drag(worldX(declaration.getOwnerKey(), x2),
                    worldY(declaration.getOwnerKey(), y2));
            updateCoordinates(result, false);
            return result;
        }
        public ElementHandle switchElement(String id, int x, int y, int x2, int y2) {
            open();
            ElementHandle result = allocateTyped(declaration.getOwnerKey(), id, "SWITCH", false,
                    x, y);
            configureDrag(result, declaration.getOwnerKey(), x2, y2, false);
            return result;
        }
        public ElementHandle voltageSource(String id, int x, int y, int x2, int y2,
                double voltage) {
            open();
            ElementHandle result = allocateTyped(declaration.getOwnerKey(), id, "VOLTAGE", false,
                    x, y);
            DCVoltageElm source = (DCVoltageElm) result.element;
            source.drag(worldX(declaration.getOwnerKey(), x2),
                    worldY(declaration.getOwnerKey(), y2));
            source.maxVoltage = voltage;
            updateCoordinates(result, false);
            return result;
        }
        public ElementHandle ground(String id, int x, int y, int x2, int y2) {
            open();
            ElementHandle result = allocateTyped(declaration.getOwnerKey(), id, "GROUND", false,
                    x, y);
            configureDrag(result, declaration.getOwnerKey(), x2, y2, false);
            return result;
        }
        public ElementHandle nmos(String id, int x, int y, int x2, int y2,
                double threshold, double beta) {
            open();
            requireNmosChoice(declaration, id, threshold, beta);
            ElementHandle result = allocateTyped(declaration.getOwnerKey(), id, "NMOS", false,
                    x, y);
            NMosfetElm nmos = (NMosfetElm) result.element;
            nmos.drag(worldX(declaration.getOwnerKey(), x2),
                    worldY(declaration.getOwnerKey(), y2));
            nmos.vt = threshold;
            nmos.beta = beta;
            updateCoordinates(result, false);
            return result;
        }
        public ElementHandle led(String id, int x, int y, int x2, int y2,
                String model, int red, int green, int blue) {
            open();
            requireLedChoice(declaration, id, model, red, green, blue);
            ElementHandle result = allocateTyped(declaration.getOwnerKey(), id, "LED", false,
                    x, y);
            LEDElm led = (LEDElm) result.element;
            led.drag(worldX(declaration.getOwnerKey(), x2),
                    worldY(declaration.getOwnerKey(), y2));
            led.modelName = model;
            led.setup();
            led.colorR = red;
            led.colorG = green;
            led.colorB = blue;
            updateCoordinates(result, false);
            return result;
        }
        public Point point(ElementHandle handle, int postIndex) {
            open();
            ensureOwned(declaration.getOwnerKey(), handle);
            return localPoint(handle, postIndex);
        }
        public SecondaryHandle secondaryOpenPath(String id, ElementHandle upstream, int post) {
            open();
            return secondary(declaration.getOwnerKey(), id, upstream, post);
        }
        public TerminalHandle terminal(String elementId, String terminalId) {
            open();
            return ElectricalConstructionContext.this.terminal(declaration.getOwnerKey(),
                    elementId, terminalId);
        }
        public void bindComponent(String local, ElementHandle primary, ElementHandle auxiliary) {
            open();
            ElectricalConstructionContext.this.bindComponent(declaration.getOwnerKey(), local,
                    primary, auxiliary);
        }
        public void bindPad(String padLocal, TerminalHandle endpoint) {
            open();
            ElectricalConstructionContext.this.bindPad(declaration.getOwnerKey(),
                    padLocal, endpoint);
        }
        public TerminalHandle terminal(ElementHandle element, String terminalId) {
            open();
            ensureOwned(declaration.getOwnerKey(), element);
            return ElectricalConstructionContext.this.terminal(element.ownerKey,
                    element.elementId, terminalId);
        }
        public PhysicalUnitReceipt declareUnit(String unitId) {
            open();
            ElectricalRealizationSpec.PhysicalUnitSpec unit = spec.getPhysicalUnits()
                    .get(declaration.getOwnerKey() + "/" + unitId);
            if (unit == null)
                throw new IllegalArgumentException("Undeclared physical unit " + unitId);
            PhysicalUnitReceipt result = new PhysicalUnitReceipt(unit);
            if (declaredUnits.put(unitId, result) != null)
                throw new IllegalStateException("Duplicate physical unit " + unitId);
            return result;
        }
        public ContributionConstructionReceipt finish() {
            open();
            for (String elementId : declaration.getElementIds())
                requireHandle(declaration.getOwnerKey(), elementId);
            for (String unitId : declaration.getUnitIds())
                if (!declaredUnits.containsKey(unitId))
                    throw new IllegalStateException("Provider did not declare unit " + unitId);
            requireBoundPhysicalUnits(declaredUnits);
            done = true;
            completedScopes.add(declaration.getOwnerKey());
            ContributionConstructionReceipt result = new ContributionConstructionReceipt(
                    declaration.getOwnerKey(), declaration.getProviderId(),
                    declaration.getProviderVersion(), handlesFor(declaration.getOwnerKey()),
                    secondariesFor(declaration.getOwnerKey()), declaredUnits, thisContext());
            contributionReceipts.put(declaration.getOwnerKey(), result);
            return result;
        }

        private ElectricalConstructionContext thisContext() {
            return ElectricalConstructionContext.this;
        }
    }

    private final class DeviceLocalScope implements DeviceScope {
        private final ElectricalRealizationSpec.ProviderDeclaration declaration;
        private final TreeMap<String, PhysicalUnitReceipt> declaredUnits =
                new TreeMap<String, PhysicalUnitReceipt>();
        private boolean done;

        DeviceLocalScope(ElectricalRealizationSpec.ProviderDeclaration declaration) {
            this.declaration = declaration;
        }
        private void open() {
            ensureOpen();
            if (done) throw new IllegalStateException("Device scope is finished");
        }
        public ElementHandle allocate(String id) {
            open();
            rejectBridgeAllocation(id);
            return allocateDefault("device", id, false);
        }
        public ElementHandle resistor(String id, int x, int y, int x2, int y2,
                double resistance) {
            open();
            ElementHandle result = allocateTyped("device", id, "RESISTOR", false, x, y);
            ResistorElm resistor = (ResistorElm) result.element;
            resistor.drag(worldX("device", x2), worldY("device", y2));
            resistor.setResistance(resistance);
            updateCoordinates(result, false);
            return result;
        }
        public ElementHandle wire(String id, int x, int y, int x2, int y2) {
            open();
            ElementHandle result = allocateTyped("device", id, "WIRE", false, x, y);
            WireElm wire = (WireElm) result.element;
            wire.drag(worldX("device", x2), worldY("device", y2));
            updateCoordinates(result, false);
            return result;
        }
        public ElementHandle switchElement(String id, int x, int y, int x2, int y2) {
            open();
            ElementHandle result = allocateTyped("device", id, "SWITCH", false, x, y);
            configureDrag(result, "device", x2, y2, false);
            return result;
        }
        public ElementHandle voltageSource(String id, int x, int y, int x2, int y2,
                double voltage) {
            open();
            ElementHandle result = allocateTyped("device", id, "VOLTAGE", false, x, y);
            DCVoltageElm source = (DCVoltageElm) result.element;
            source.drag(worldX("device", x2), worldY("device", y2));
            source.maxVoltage = voltage;
            updateCoordinates(result, false);
            return result;
        }
        public ElementHandle ground(String id, int x, int y, int x2, int y2) {
            open();
            ElementHandle result = allocateTyped("device", id, "GROUND", false, x, y);
            configureDrag(result, "device", x2, y2, false);
            return result;
        }
        public Point point(ElementHandle handle, int postIndex) {
            open();
            return localPoint(handle, postIndex);
        }
        public TerminalHandle terminal(String id, String terminalId) {
            open();
            return ElectricalConstructionContext.this.terminal("device", id, terminalId);
        }
        public TerminalHandle terminal(ElementHandle element, String terminalId) {
            open();
            if (element == null || element.context != ElectricalConstructionContext.this)
                throw new IllegalArgumentException("Foreign device terminal handle");
            return ElectricalConstructionContext.this.terminal(element.ownerKey,
                    element.elementId, terminalId);
        }
        public void bindPad(String ownerKey, String padLocalId, TerminalHandle endpoint) {
            open();
            ElectricalConstructionContext.this.bindPad(ownerKey, padLocalId, endpoint, true);
        }
        public void bindComponentConnection(String componentOwner, String componentLocalId,
                String padLocalId, TerminalHandle boardEndpoint,
                TerminalHandle componentEndpoint, ElementHandle connectionElement) {
            open();
            ElectricalConstructionContext.this.bindConnection(componentOwner,
                    componentLocalId, padLocalId, boardEndpoint, componentEndpoint,
                    connectionElement);
        }
        public void bindComponent(String componentOwner, String componentLocalId,
                ElementHandle primary, ElementHandle auxiliary) {
            open();
            ElectricalConstructionContext.this.bindDeviceComponent(componentOwner,
                    componentLocalId, primary, auxiliary);
        }
        public void bindPower(String powerInputId, ElementHandle backing, ElementHandle control) {
            open();
            ElectricalConstructionContext.this.bindPower(powerInputId, backing, control);
        }
        public ElementHandle join(String joinId, TerminalHandle first, TerminalHandle second) {
            open();
            return ElectricalConstructionContext.this.join(joinId, first, second);
        }
        public ControlCommandHandle command(String joinId, ElementHandle switchElement) {
            open();
            return ElectricalConstructionContext.this.command(joinId, switchElement);
        }
        public ControlCommandHandle command(String joinId) {
            open();
            return ElectricalConstructionContext.this.command(joinId);
        }
        public DeviceJoinReceipt finish() {
            open();
            for (String elementId : declaration.getElementIds())
                requireHandle("device", elementId);
            for (String unitId : declaration.getUnitIds()) {
                // Device package units are materialized centrally; declaring
                // them here keeps the same ownership/accounting rule as local
                // providers without exposing a physical inventory.
                ElectricalRealizationSpec.PhysicalUnitSpec unit = deviceUnit(unitId);
                if (unit == null)
                    throw new IllegalStateException("Device unit declaration is missing: " +
                            unitId);
                declaredUnits.put(unitId, new PhysicalUnitReceipt(unit));
            }
            requireBoundPhysicalUnits(declaredUnits);
            done = true;
            completedScopes.add("device");
            deviceReceipt = new DeviceJoinReceipt("device", commands,
                    new TreeMap<String, Integer>(joinCounts),
                    new TreeMap<String, Integer>(bridgeCounts), declaredUnits, thisContext());
            return deviceReceipt;
        }

        private ElectricalConstructionContext thisContext() {
            return ElectricalConstructionContext.this;
        }

        private ElectricalRealizationSpec.PhysicalUnitSpec deviceUnit(String unitId) {
            ElectricalRealizationSpec.PhysicalUnitSpec direct = spec.getPhysicalUnits()
                    .get("device/" + unitId);
            if (direct != null)
                return direct;
            ElectricalRealizationSpec.PhysicalUnitSpec result = null;
            for (ElectricalRealizationSpec.PhysicalUnitSpec candidate :
                    spec.getPhysicalUnits().values()) {
                if (unitId.equals(candidate.getUnitId())) {
                    if (result != null)
                        throw new IllegalStateException("Ambiguous device unit declaration: " +
                                unitId);
                    result = candidate;
                }
            }
            return result;
        }
    }

    private ElementHandle allocateTyped(String owner, String id, String kind,
            boolean bridge) {
        // The typed allocator translates local coordinates exactly once.
        return allocateTyped(owner, id, kind, bridge, 0, 0);
    }

    private ElementHandle allocateTyped(String owner, String id, String kind,
            boolean bridge, int x, int y) {
        ensureOpen();
        if ("device".equals(owner) && !bridge)
            rejectBridgeAllocation(id);
        if (CircuitElm.sim == null)
            throw new IllegalStateException("CircuitJS owner is required for graph allocation");
        int originX = snap(worldX(owner, x));
        int originY = snap(worldY(owner, y));
        ElectricalRealizationSpec.ElementDeclaration declaration =
            spec.getElementDeclaration(owner, id);
        if (declaration == null || !kind.equals(declaration.getKind()))
            throw new IllegalArgumentException("Undeclared or mismatched element " + owner + "/" + id);
        CircuitElm element;
        if ("RESISTOR".equals(kind)) element = new ResistorElm(originX, originY);
        else if ("WIRE".equals(kind)) element = new WireElm(originX, originY);
        else if ("SWITCH".equals(kind)) element = new SwitchElm(originX, originY);
        else if ("VOLTAGE".equals(kind)) element = new DCVoltageElm(originX, originY);
        else if ("GROUND".equals(kind)) element = new GroundElm(originX, originY);
        else if ("NMOS".equals(kind)) element = new NMosfetElm(originX, originY);
        else if ("LED".equals(kind)) element = new LEDElm(originX, originY);
        else if ("FAULT_HELPER".equals(kind)) element = new SwitchElm(originX, originY);
        else throw new IllegalArgumentException("Unsupported electrical kind " + kind);
        return allocateElement(owner, id, element, bridge);
    }

    private ElementHandle allocateDefault(String owner, String id, boolean bridge) {
        ElectricalRealizationSpec.ElementDeclaration declaration =
                spec.getElementDeclaration(owner, id);
        if (declaration == null)
            throw new IllegalArgumentException("Undeclared element " + owner + "/" + id);
        return allocateTyped(owner, id, declaration.getKind(), bridge);
    }

    private TreeMap<String, ElementHandle> handlesFor(String ownerKey) {
        TreeMap<String, ElementHandle> result = new TreeMap<String, ElementHandle>();
        String prefix = ownerKey + "/";
        for (Map.Entry<String, ElementHandle> entry : handles.entrySet())
            if (entry.getKey().startsWith(prefix))
                result.put(entry.getValue().elementId, entry.getValue());
        return result;
    }

    private TreeMap<String, SecondaryHandle> secondariesFor(String ownerKey) {
        TreeMap<String, SecondaryHandle> result = new TreeMap<String, SecondaryHandle>();
        String prefix = ownerKey + "/";
        for (Map.Entry<String, SecondaryHandle> entry : secondaryPaths.entrySet())
            if (entry.getKey().startsWith(prefix))
                result.put(entry.getValue().helperId, entry.getValue());
        return result;
    }

    /** Opaque candidate element handle. */
    static final class ElementHandle {
        private final ElectricalConstructionContext context;
        private final String ownerKey;
        private final String elementId;
        private final CircuitElm element;
        private final ElectricalRealizationSpec.ElementDeclaration declaration;
        private List<String> coordinateKeys = Collections.emptyList();
        private boolean coordinatesReady;

        private ElementHandle(ElectricalConstructionContext context, String ownerKey,
                String elementId, CircuitElm element) {
            this.context = context;
            this.ownerKey = ownerKey;
            this.elementId = elementId;
            this.element = element;
            this.declaration = context.spec.getElementDeclaration(ownerKey, elementId);
        }
        String getOwnerKey() { return ownerKey; }
        String getElementId() { return elementId; }
        String getKind() { return declaration.getKind(); }
        private String key() { return ElectricalRealizationSpec.elementKey(ownerKey, elementId); }
        CircuitElm getElement() {
            context.ensureFinished();
            return element;
        }
    }

    /** Opaque terminal handle tied to one context and one owner scope. */
    static final class TerminalHandle {
        private final ElectricalConstructionContext context;
        private final ElementHandle handle;
        private final String terminalId;
        private final int postIndex;

        private TerminalHandle(ElectricalConstructionContext context, ElementHandle handle,
                String terminalId, int postIndex) {
            this.context = context;
            this.handle = handle;
            this.terminalId = terminalId;
            this.postIndex = postIndex;
        }
        String getOwnerKey() { return handle.ownerKey; }
        String getElementId() { return handle.elementId; }
        String getTerminalId() { return terminalId; }
        int getPostIndex() { return postIndex; }
        Point getPoint() {
            context.ensureLiveCandidate();
            Point point = handle.element.getPost(postIndex);
            if (point == null) throw new IllegalStateException("Electrical terminal has no point");
            return new Point(point.x, point.y);
        }
        CircuitPostMeasurementEndpoint asEndpoint() {
            context.ensureLiveCandidate();
            return new CircuitPostMeasurementEndpoint(handle.element, postIndex);
        }
    }

    static final class SecondaryHandle {
        private final ElectricalConstructionContext context;
        private final String ownerKey;
        private final String helperId;
        private final ElementHandle helper;
        private final ResistorSecondaryOpenPath path;

        private SecondaryHandle(ElectricalConstructionContext context, String ownerKey,
                String helperId, ElementHandle helper, ResistorSecondaryOpenPath path) {
            this.context = context;
            this.ownerKey = ownerKey;
            this.helperId = helperId;
            this.helper = helper;
            this.path = path;
        }
        String getOwnerKey() { return ownerKey; }
        String getHelperId() { return helperId; }
        CircuitPostMeasurementEndpoint getPublicTerminal() {
            context.ensureFinished();
            return path.getPublicTerminal();
        }
        ResistorSecondaryOpenPath getPath() {
            context.ensureFinished();
            return path;
        }
        ElementHandle getElementHandle() { return helper; }
        private String key() { return ElectricalRealizationSpec.elementKey(ownerKey, helperId); }
    }

    static final class ControlCommandHandle {
        private final ElectricalConstructionContext context;
        private final String joinId;
        private final ElementHandle element;
        private ControlCommandHandle(ElectricalConstructionContext context, String joinId,
                ElementHandle element) {
            this.context = context;
            this.joinId = joinId;
            this.element = element;
        }
        String getJoinId() { return joinId; }
        SwitchElm getSwitch() { context.ensureFinished(); return (SwitchElm) element.element; }
        CircuitElm getElement() { context.ensureFinished(); return element.element; }
    }
}

/** Frozen physical-unit declaration receipt. */
final class PhysicalUnitReceipt {
    private final ElectricalRealizationSpec.PhysicalUnitSpec specification;
    PhysicalUnitReceipt(ElectricalRealizationSpec.PhysicalUnitSpec specification) {
        if (specification == null)
            throw new IllegalArgumentException("Physical unit specification is required");
        this.specification = specification;
    }
    String getOwnerKey() { return specification.getOwnerKey(); }
    String getUnitId() { return specification.getUnitId(); }
    String getComponentId() { return specification.getComponentId(); }
    String getPackageId() { return specification.getPackageId(); }
    Map<String, String> getPackageTerminalByUnitTerminal() {
        return specification.getPackageTerminalByUnitTerminal();
    }
}

/** Receipt returned by one local provider; all handles remain owner-scoped. */
final class ContributionConstructionReceipt {
    private final String ownerKey;
    private final String providerId;
    private final int providerVersion;
    private final Map<String, ElectricalConstructionContext.ElementHandle> elements;
    private final Map<String, ElectricalConstructionContext.SecondaryHandle> secondary;
    private final Map<String, PhysicalUnitReceipt> units;
    private final ElectricalConstructionContext context;

    ContributionConstructionReceipt(String ownerKey, String providerId, int providerVersion,
            Map<String, ElectricalConstructionContext.ElementHandle> elements,
            Map<String, ElectricalConstructionContext.SecondaryHandle> secondary,
            Map<String, PhysicalUnitReceipt> units) {
        this(ownerKey, providerId, providerVersion, elements, secondary, units, null);
    }

    ContributionConstructionReceipt(String ownerKey, String providerId, int providerVersion,
            Map<String, ElectricalConstructionContext.ElementHandle> elements,
            Map<String, ElectricalConstructionContext.SecondaryHandle> secondary,
            Map<String, PhysicalUnitReceipt> units, ElectricalConstructionContext context) {
        this.ownerKey = ownerKey;
        this.providerId = providerId;
        this.providerVersion = providerVersion;
        this.elements = Collections.unmodifiableMap(new TreeMap<String,
                ElectricalConstructionContext.ElementHandle>(elements));
        this.secondary = Collections.unmodifiableMap(new TreeMap<String,
                ElectricalConstructionContext.SecondaryHandle>(secondary));
        this.units = Collections.unmodifiableMap(new TreeMap<String, PhysicalUnitReceipt>(units));
        this.context = context;
    }
    String getOwnerKey() { return ownerKey; }
    String getProviderId() { return providerId; }
    int getProviderVersion() { return providerVersion; }
    Map<String, ElectricalConstructionContext.ElementHandle> getElements() {
        ensureAccessible();
        return elements;
    }
    Map<String, PhysicalUnitReceipt> getUnits() { return units; }
    ElectricalConstructionContext.ElementHandle getElement(String localId) {
        ensureAccessible();
        ElectricalConstructionContext.ElementHandle result = elements.get(localId);
        if (result == null) throw new IllegalArgumentException("Unknown owned element " + localId);
        return result;
    }
    ElectricalConstructionContext.SecondaryHandle getSecondary(String helperId) {
        ensureAccessible();
        ElectricalConstructionContext.SecondaryHandle result = secondary.get(helperId);
        if (result == null) throw new IllegalArgumentException("Unknown owned secondary " + helperId);
        return result;
    }

    private void ensureAccessible() {
        if (context != null && context.isAborted())
            throw new IllegalStateException("Construction contribution was aborted");
    }
}

/** Device-owned join and control receipt. */
final class DeviceJoinReceipt {
    private final String ownerKey;
    private final Map<String, ElectricalConstructionContext.ControlCommandHandle> commands;
    private final Map<String, Integer> joins;
    private final Map<String, PhysicalUnitReceipt> units;
    private final Map<String, Integer> bridgeCounts;
    private final ElectricalConstructionContext context;

    DeviceJoinReceipt(String ownerKey,
            Map<String, ElectricalConstructionContext.ControlCommandHandle> commands,
            Map<String, Integer> joins, Map<String, PhysicalUnitReceipt> units) {
        this(ownerKey, commands, joins, Collections.<String, Integer>emptyMap(), units, null);
    }

    DeviceJoinReceipt(String ownerKey,
            Map<String, ElectricalConstructionContext.ControlCommandHandle> commands,
            Map<String, Integer> joins, Map<String, Integer> bridgeCounts,
            Map<String, PhysicalUnitReceipt> units, ElectricalConstructionContext context) {
        this.ownerKey = ownerKey;
        this.commands = Collections.unmodifiableMap(new TreeMap<String,
                ElectricalConstructionContext.ControlCommandHandle>(commands));
        this.joins = Collections.unmodifiableMap(new TreeMap<String, Integer>(joins));
        this.units = Collections.unmodifiableMap(new TreeMap<String, PhysicalUnitReceipt>(units));
        this.bridgeCounts = Collections.unmodifiableMap(new TreeMap<String, Integer>(bridgeCounts));
        this.context = context;
    }
    String getOwnerKey() { return ownerKey; }
    Map<String, Integer> getJoinCounts() { return joins; }
    Map<String, Integer> getBridgeCounts() { return bridgeCounts; }
    Map<String, PhysicalUnitReceipt> getUnits() { return units; }
    ElectricalConstructionContext.ControlCommandHandle getCommand(String joinId) {
        if (context != null && context.isAborted())
            throw new IllegalStateException("Construction device receipt was aborted");
        ElectricalConstructionContext.ControlCommandHandle result = commands.get(joinId);
        if (result == null) throw new IllegalArgumentException("Unknown command join " + joinId);
        return result;
    }
}

/** Complete immutable candidate receipt, with frozen failure counters. */
final class ConstructionReceipt {
    private final ElectricalRealizationSpec spec;
    private final Vector<CircuitElm> elements;
    private final Map<String, ElectricalConstructionContext.ElementHandle> handles;
    private final Map<String, ElectricalConstructionContext.SecondaryHandle> secondary;
    private final Map<String, ContributionConstructionReceipt> contributions;
    private final GeneratedComponentBindings componentBindings;
    private final GeneratedExternalPowerBindings powerBindings;
    private final GeneratedComponentConnectionBindings connectionBindings;
    private final DeviceJoinReceipt deviceReceipt;
    private final int allocatedCount;
    private final int bindingCount;
    private final int joinCount;
    private final boolean aborted;
    private final Throwable failure;
    private final Map<String, Integer> bridgeCounts;
    private final ElectricalConstructionContext context;

    ConstructionReceipt(ElectricalRealizationSpec spec, Vector<CircuitElm> elements,
            Map<String, ElectricalConstructionContext.ElementHandle> handles,
            Map<String, ElectricalConstructionContext.SecondaryHandle> secondary,
            Map<String, ContributionConstructionReceipt> contributions,
            GeneratedComponentBindings componentBindings,
            GeneratedExternalPowerBindings powerBindings,
            GeneratedComponentConnectionBindings connectionBindings,
            DeviceJoinReceipt deviceReceipt, int allocatedCount, int bindingCount,
            int joinCount, boolean aborted, Throwable failure) {
        this(spec, elements, handles, secondary, contributions, componentBindings,
                powerBindings, connectionBindings, deviceReceipt, allocatedCount,
                bindingCount, joinCount, Collections.<String, Integer>emptyMap(),
                aborted, failure, null);
    }

    ConstructionReceipt(ElectricalRealizationSpec spec, Vector<CircuitElm> elements,
            Map<String, ElectricalConstructionContext.ElementHandle> handles,
            Map<String, ElectricalConstructionContext.SecondaryHandle> secondary,
            Map<String, ContributionConstructionReceipt> contributions,
            GeneratedComponentBindings componentBindings,
            GeneratedExternalPowerBindings powerBindings,
            GeneratedComponentConnectionBindings connectionBindings,
            DeviceJoinReceipt deviceReceipt, int allocatedCount, int bindingCount,
            int joinCount, Map<String, Integer> bridgeCounts, boolean aborted,
            Throwable failure, ElectricalConstructionContext context) {
        this.spec = spec;
        this.elements = new Vector<CircuitElm>(elements);
        this.handles = Collections.unmodifiableMap(new TreeMap<String,
                ElectricalConstructionContext.ElementHandle>(handles));
        this.secondary = Collections.unmodifiableMap(new TreeMap<String,
                ElectricalConstructionContext.SecondaryHandle>(secondary));
        this.contributions = Collections.unmodifiableMap(new TreeMap<String,
                ContributionConstructionReceipt>(contributions));
        this.componentBindings = componentBindings;
        this.powerBindings = powerBindings;
        this.connectionBindings = connectionBindings;
        this.deviceReceipt = deviceReceipt;
        this.allocatedCount = allocatedCount;
        this.bindingCount = bindingCount;
        this.joinCount = joinCount;
        this.aborted = aborted;
        this.failure = failure;
        this.bridgeCounts = Collections.unmodifiableMap(new TreeMap<String, Integer>(bridgeCounts));
        this.context = context;
    }
    ElectricalRealizationSpec getSpec() { return spec; }
    boolean belongsToFinishedContext(ElectricalRealizationSpec expectedSpec,
            TroubleshootBoard expectedBoard) {
        return context != null && context.isIssuedReceipt(this)
                && context.isFinished() && !context.isAborted()
                && context.getSpec() == expectedSpec && context.getBoard() == expectedBoard;
    }
    TroubleshootBoard getBoard() {
        if (context != null) return context.getBoard();
        return componentBindings.getBoardForRuntimeValidation();
    }
    Vector<CircuitElm> getElements() { ensureAccessible(); return new Vector<CircuitElm>(elements); }
    int getAllocatedElementCount() { return allocatedCount; }
    int getBindingCount() { return bindingCount; }
    int getJoinCount() { return joinCount; }
    Map<String, Integer> getBridgeCounts() { return bridgeCounts; }
    boolean isAborted() { return aborted || (context != null && context.isAborted()); }
    Throwable getFailure() { return failure; }
    GeneratedComponentBindings getComponentBindings() { ensureAccessible(); return componentBindings; }
    GeneratedExternalPowerBindings getPowerBindings() { ensureAccessible(); return powerBindings; }
    GeneratedComponentConnectionBindings getConnectionBindings() { ensureAccessible(); return connectionBindings; }
    ContributionConstructionReceipt getContribution(String ownerKey) {
        ensureAccessible();
        ContributionConstructionReceipt result = contributions.get(ownerKey);
        if (result == null) throw new IllegalArgumentException("Unknown contribution owner " + ownerKey);
        return result;
    }
    DeviceJoinReceipt getDeviceReceipt() {
        ensureAccessible();
        if (deviceReceipt == null) throw new IllegalStateException("Device receipt is missing");
        return deviceReceipt;
    }
    ElectricalConstructionContext.ElementHandle getElement(String ownerKey, String elementId) {
        ensureAccessible();
        ElectricalConstructionContext.ElementHandle result = handles.get(
                ElectricalRealizationSpec.elementKey(ownerKey, elementId));
        if (result == null) throw new IllegalArgumentException("Unknown construction element " +
                ownerKey + "/" + elementId);
        return result;
    }
    ElectricalConstructionContext.SecondaryHandle getSecondary(String ownerKey,
            String helperId) {
        ensureAccessible();
        ElectricalConstructionContext.SecondaryHandle result = secondary.get(
                ElectricalRealizationSpec.elementKey(ownerKey, helperId));
        if (result == null) throw new IllegalArgumentException("Unknown construction secondary " +
                ownerKey + "/" + helperId);
        return result;
    }

    private void ensureAccessible() {
        if (aborted)
            throw new IllegalStateException("Construction receipt was aborted");
        if (context != null && context.isAborted())
            throw new IllegalStateException("Construction receipt was aborted");
    }
}
