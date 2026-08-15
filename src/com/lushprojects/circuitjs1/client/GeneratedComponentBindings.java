package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

class GeneratedComponentBindings {
    private final TroubleshootBoard board;
    private final HashMap<String, Vector<CircuitElm>> componentElements =
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
        componentElements.put(componentId, new Vector<CircuitElm>(elements));
    }

    Vector<CircuitElm> getElements(String componentId) {
        Vector<CircuitElm> elements = componentElements.get(componentId);
        if (elements == null)
            throw new IllegalArgumentException("Unknown component simulation binding: " + componentId);
        return new Vector<CircuitElm>(elements);
    }

    CircuitElm getSingleElement(String componentId) {
        Vector<CircuitElm> elements = getElements(componentId);
        if (elements.size() != 1)
            throw new IllegalStateException("Expected one simulation element for component: " + componentId);
        return elements.firstElement();
    }

    boolean isElementBoundToComponent(String componentId, CircuitElm element) {
        Vector<CircuitElm> elements = componentElements.get(componentId);
        return elements != null && elements.contains(element);
    }

    void replaceSingleElement(String componentId, CircuitElm element) {
        if (element == null || !componentElements.containsKey(componentId))
            throw new IllegalArgumentException("Invalid component replacement binding: " + componentId);
        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        elements.add(element);
        componentElements.put(componentId, elements);
    }

    void validateElementsAreOwnedBy(Vector<CircuitElm> simulationElements) {
        for (String componentId : componentElements.keySet()) {
            for (CircuitElm element : componentElements.get(componentId)) {
                if (!simulationElements.contains(element))
                    throw new IllegalStateException("Component binding is not owned by generated board: " + componentId);
            }
        }
    }
}