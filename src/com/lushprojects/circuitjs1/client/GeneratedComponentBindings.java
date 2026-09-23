package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

class GeneratedComponentBindings {
    private final TroubleshootBoard board;
    private boolean constructionAborted;
    private final HashMap<String, Vector<CircuitElm>> componentElements =
        new HashMap<String, Vector<CircuitElm>>();
    private final HashMap<String, Vector<CircuitElm>> auxiliaryComponentElements =
        new HashMap<String, Vector<CircuitElm>>();
    /** Immutable generated bindings used when a mutable slot is empty. */
    private final HashMap<String, Vector<CircuitElm>> canonicalComponentElements =
        new HashMap<String, Vector<CircuitElm>>();
    private final HashMap<String, Vector<CircuitElm>> canonicalAuxiliaryComponentElements =
        new HashMap<String, Vector<CircuitElm>>();

    GeneratedComponentBindings(TroubleshootBoard board) {
        this.board = board;
    }

    void bindComponent(String componentId, CircuitElm element) {
	Vector<CircuitElm> elements = new Vector<CircuitElm>();
	elements.add(element);
	bindComponentElements(componentId, elements);
    }

    void bindComponentElements(String componentId, Vector<CircuitElm> elements) {
        if (constructionAborted)
            throw new IllegalStateException("Construction bindings were revoked");
        if (board.getComponent(componentId) == null)
            throw new IllegalArgumentException("Unknown board component: " + componentId);
        if (componentElements.containsKey(componentId))
            throw new IllegalArgumentException("Duplicate component simulation binding: " + componentId);
	if (elements == null || elements.isEmpty())
	    throw new IllegalArgumentException("Missing simulation elements for component: " + componentId);
	for (CircuitElm element : elements) {
	    if (element == null)
		throw new IllegalArgumentException("Missing simulation element for component: " + componentId);
	}
        Vector<CircuitElm> copy = new Vector<CircuitElm>(elements);
        componentElements.put(componentId, copy);
        canonicalComponentElements.put(componentId, new Vector<CircuitElm>(copy));
    }

    Vector<CircuitElm> getElements(String componentId) {
        Vector<CircuitElm> elements = componentElements.get(componentId);
        if (elements == null)
            throw new IllegalArgumentException("Unknown component simulation binding: " + componentId);
        return new Vector<CircuitElm>(elements);
    }

    void completeServiceBinding(String componentId, CircuitElm primary, CircuitElm auxiliary) {
        if (constructionAborted || board.getSimulationBindings().isConstructionComplete() ||
                primary == null || !componentElements.containsKey(componentId))
            throw new IllegalStateException("Invalid service construction binding");
        Vector<CircuitElm> first = new Vector<CircuitElm>(); first.add(primary);
        componentElements.put(componentId, first);
        canonicalComponentElements.put(componentId, new Vector<CircuitElm>(first));
        Vector<CircuitElm> second = new Vector<CircuitElm>();
        if (auxiliary != null) second.add(auxiliary);
        auxiliaryComponentElements.put(componentId, second);
        canonicalAuxiliaryComponentElements.put(componentId, new Vector<CircuitElm>(second));
    }

    /** Returns whether the generator supplied a primary binding for the component. */
    boolean hasComponentBinding(String componentId) {
        return componentElements.containsKey(componentId);
    }

    /** Returns the board identity that owns this binding registry. */
    TroubleshootBoard getBoardForRuntimeValidation() {
        return board;
    }

    Vector<CircuitElm> getAuxiliaryElements(String componentId) {
        if (!componentElements.containsKey(componentId))
            throw new IllegalArgumentException("Unknown component simulation binding: " + componentId);
        Vector<CircuitElm> elements = auxiliaryComponentElements.get(componentId);
        return elements == null ? new Vector<CircuitElm>() : new Vector<CircuitElm>(elements);
    }

    CircuitElm getSingleElement(String componentId) {
        Vector<CircuitElm> elements = getElements(componentId);
        if (elements.size() != 1)
            throw new IllegalStateException("Expected one simulation element for component: " + componentId);
        return elements.firstElement();
    }

    boolean isElementBoundToComponent(String componentId, CircuitElm element) {
        Vector<CircuitElm> elements = componentElements.get(componentId);
        if (elements != null && elements.contains(element))
            return true;
        elements = auxiliaryComponentElements.get(componentId);
        return elements != null && elements.contains(element);
    }

    void bindAuxiliaryComponentElement(String componentId, CircuitElm element) {
        if (constructionAborted)
            throw new IllegalStateException("Construction bindings were revoked");
        if (board.getComponent(componentId) == null || element == null)
            throw new IllegalArgumentException("Invalid auxiliary component binding: " + componentId);
        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        elements.add(element);
        auxiliaryComponentElements.put(componentId, elements);
        if (!canonicalAuxiliaryComponentElements.containsKey(componentId))
            canonicalAuxiliaryComponentElements.put(componentId,
                new Vector<CircuitElm>(elements));
    }

    void replaceAuxiliaryComponentElement(String componentId, CircuitElm element) {
        if (constructionAborted)
            throw new IllegalStateException("Construction bindings were revoked");
        if (!componentElements.containsKey(componentId) || element == null)
            throw new IllegalArgumentException("Invalid auxiliary component replacement: " + componentId);
        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        elements.add(element);
        auxiliaryComponentElements.put(componentId, elements);
    }

    /** A valid physical part may have no auxiliary damage/open-path element. */
    void clearAuxiliaryComponentElements(String componentId) {
        if (constructionAborted)
            throw new IllegalStateException("Construction bindings were revoked");
        if (!componentElements.containsKey(componentId))
            throw new IllegalArgumentException("Invalid auxiliary component clear: " + componentId);
        auxiliaryComponentElements.remove(componentId);
    }

    void replaceSingleElement(String componentId, CircuitElm element) {
        if (element == null || !componentElements.containsKey(componentId))
            throw new IllegalArgumentException("Invalid component replacement binding: " + componentId);
        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        elements.add(element);
        componentElements.put(componentId, elements);
    }

    /** Restores the generated backing retained by an empty mutable slot. */
    void restoreCanonicalForMutation(String componentId) {
        Vector<CircuitElm> canonical = canonicalComponentElements.get(componentId);
        if (componentId == null || canonical == null || canonical.isEmpty() ||
                !componentElements.containsKey(componentId))
            throw new IllegalArgumentException("Invalid canonical component restoration: " +
                componentId);
        componentElements.put(componentId, new Vector<CircuitElm>(canonical));
        Vector<CircuitElm> auxiliary = canonicalAuxiliaryComponentElements.get(componentId);
        if (auxiliary == null || auxiliary.isEmpty())
            auxiliaryComponentElements.remove(componentId);
        else
            auxiliaryComponentElements.put(componentId, new Vector<CircuitElm>(auxiliary));
    }

    /** Restores the exact two binding vectors captured by a resistor scope. */
    void restoreForMutation(String componentId, Vector<CircuitElm> elements,
            Vector<CircuitElm> auxiliaryElements) {
        if (componentId == null || !componentElements.containsKey(componentId) ||
                elements == null || elements.isEmpty())
            throw new IllegalArgumentException("Invalid component binding restoration: " + componentId);
        for (CircuitElm element : elements)
            if (element == null)
                throw new IllegalArgumentException("Missing component binding element: " + componentId);
        componentElements.put(componentId, new Vector<CircuitElm>(elements));
        if (auxiliaryElements == null || auxiliaryElements.isEmpty())
            auxiliaryComponentElements.remove(componentId);
        else {
            for (CircuitElm element : auxiliaryElements)
                if (element == null)
                    throw new IllegalArgumentException("Missing auxiliary binding element: " + componentId);
            auxiliaryComponentElements.put(componentId,
                new Vector<CircuitElm>(auxiliaryElements));
        }
    }

    void validateElementsAreOwnedBy(Vector<CircuitElm> simulationElements) {
        for (String componentId : componentElements.keySet()) {
            for (CircuitElm element : componentElements.get(componentId)) {
                if (!simulationElements.contains(element))
                    throw new IllegalStateException("Component binding is not owned by generated board: " + componentId);
            }
        }
        for (String componentId : auxiliaryComponentElements.keySet()) {
            for (CircuitElm element : auxiliaryComponentElements.get(componentId)) {
                if (!simulationElements.contains(element))
                    throw new IllegalStateException("Auxiliary component binding is not owned by generated board: " + componentId);
            }
        }
    }

    /** Clears only this exact private candidate owner after failed construction. */
    void clearForAbortedConstruction(TroubleshootBoard expectedBoard) {
        if (expectedBoard == null || board != expectedBoard)
            throw new IllegalArgumentException("Foreign construction binding owner");
        constructionAborted = true;
        componentElements.clear();
        auxiliaryComponentElements.clear();
        canonicalComponentElements.clear();
        canonicalAuxiliaryComponentElements.clear();
    }
}
