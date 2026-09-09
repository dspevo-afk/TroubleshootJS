package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

class BoardSimulationBindings {
    private final TroubleshootBoard board;
    private boolean constructionAborted;
    private final HashMap<String, CircuitMeasurementEndpoint> padEndpoints =
        new HashMap<String, CircuitMeasurementEndpoint>();
    /**
     * Construction contexts share this board-level endpoint map.  Retain the
     * exact context that supplied each endpoint so aborting one speculative
     * construction cannot revoke another context's live bindings.
     */
    private final HashMap<String, ElectricalConstructionContext> padOwners =
        new HashMap<String, ElectricalConstructionContext>();
    private final Set<ElectricalConstructionContext> abortedOwners =
        new HashSet<ElectricalConstructionContext>();
    private boolean developerVerificationReady;
    private ElectricalConstructionContext developerVerificationOwner;

    BoardSimulationBindings(TroubleshootBoard board) {
        this.board = board;
    }

    void bindPad(String padId, CircuitMeasurementEndpoint endpoint) {
        bindPad(null, padId, endpoint);
    }

    void bindPad(ElectricalConstructionContext owner, String padId,
            CircuitMeasurementEndpoint endpoint) {
        if (constructionAborted || (owner == null && !abortedOwners.isEmpty()))
            throw new IllegalStateException("Construction bindings were revoked");
        if (owner != null && (owner.getBoard() != board || abortedOwners.contains(owner)))
            throw new IllegalStateException("Construction context does not own this board binding");
        if (board.getPad(padId) == null)
            throw new IllegalArgumentException("Unknown board pad: " + padId);
        if (endpoint == null)
            throw new IllegalArgumentException("Missing measurement endpoint for pad: " + padId);
        if (padEndpoints.containsKey(padId))
            throw new IllegalArgumentException("Duplicate simulation binding for pad: " + padId);
        padEndpoints.put(padId, endpoint);
        if (owner == null)
            padOwners.remove(padId);
        else
            padOwners.put(padId, owner);
    }

    CircuitMeasurementEndpoint getEndpoint(String padId) {
        return padEndpoints.get(padId);
    }

    /**
     * Captures the authoritative generated pad map without going through the
     * live lookup method.  GeneratedBoardInstance calls this exactly at its
     * composition boundary so developer-only source experiments can mutate
     * later observation lookups without changing the retained oracle.
     */
    GeneratedBoardEndpointOracle captureGeneratedBoardEndpointOracle() {
        HashMap<String, CircuitMeasurementEndpoint> captured =
            new HashMap<String, CircuitMeasurementEndpoint>();
        for (String boardPadId : board.getPadIds()) {
            CircuitMeasurementEndpoint endpoint = padEndpoints.get(boardPadId);
            if (endpoint != null)
                captured.put(boardPadId, endpoint);
        }
        return GeneratedBoardEndpointOracle.fromGeneratedBoardBindings(captured);
    }

    /** Marks the end of board composition for the developer-only verifier seam. */
    void markDeveloperVerificationReady() {
        if (constructionAborted)
            throw new IllegalStateException("Construction bindings were revoked");
        ElectricalConstructionContext owner = null;
        for (ElectricalConstructionContext candidate : padOwners.values()) {
            if (candidate == null) {
                owner = null;
                break;
            }
            if (owner == null)
                owner = candidate;
            else if (owner != candidate) {
                owner = null;
                break;
            }
        }
        developerVerificationOwner = owner;
        developerVerificationReady = true;
    }

    boolean isDeveloperVerificationReady() {
        return developerVerificationReady;
    }

    /**
     * Resolves an exposed CircuitJS post back to its logical board net.  A
     * physical board can expose one net from several backing elements, so
     * policy code must not assume a chosen connector post is the only public
     * representation of that net.
     */
    String getNetIdForEndpoint(CircuitPostMeasurementEndpoint endpoint) {
        if (endpoint == null)
            return null;
        String netId = null;
        for (String padId : board.getPadIds()) {
            CircuitMeasurementEndpoint candidate = padEndpoints.get(padId);
            if (!(candidate instanceof CircuitPostMeasurementEndpoint) ||
                    !sameEndpoint(endpoint, (CircuitPostMeasurementEndpoint) candidate))
                continue;
            String candidateNetId = board.getPad(padId).getNetId();
            if (netId != null && !netId.equals(candidateNetId))
                throw new IllegalStateException("Board endpoint is bound to multiple nets");
            netId = candidateNetId;
        }
        return netId;
    }

    Vector<CircuitMeasurementEndpoint> getEndpointsForNet(String netId) {
        BoardNet net = board.getNet(netId);
        if (net == null)
            throw new IllegalArgumentException("Unknown board net: " + netId);
        Vector<CircuitMeasurementEndpoint> endpoints = new Vector<CircuitMeasurementEndpoint>();
        for (String padId : net.getPadIds()) {
            CircuitMeasurementEndpoint endpoint = padEndpoints.get(padId);
            if (endpoint != null)
                endpoints.add(endpoint);
        }
        return endpoints;
    }

    private boolean sameEndpoint(CircuitPostMeasurementEndpoint first,
            CircuitPostMeasurementEndpoint second) {
        return first.getElement() == second.getElement() &&
            first.getPostIndex() == second.getPostIndex();
    }

    /** Clears only this exact private candidate owner after failed construction. */
    void clearForAbortedConstruction(TroubleshootBoard expectedBoard) {
        clearForAbortedConstruction(expectedBoard, null);
    }

    /** Clears only endpoints owned by this exact failed construction context. */
    void clearForAbortedConstruction(TroubleshootBoard expectedBoard,
            ElectricalConstructionContext owner) {
        if (expectedBoard == null || board != expectedBoard)
            throw new IllegalArgumentException("Foreign construction binding owner");
        if (owner == null) {
            constructionAborted = true;
            padEndpoints.clear();
            padOwners.clear();
            developerVerificationReady = false;
            developerVerificationOwner = null;
        } else {
            if (owner.getBoard() != board)
                throw new IllegalArgumentException("Foreign construction context owner");
            abortedOwners.add(owner);
            boolean removed = false;
            Iterator<Map.Entry<String, ElectricalConstructionContext>> iterator =
                padOwners.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, ElectricalConstructionContext> entry = iterator.next();
                if (entry.getValue() == owner) {
                    padEndpoints.remove(entry.getKey());
                    iterator.remove();
                    removed = true;
                }
            }
            if (developerVerificationOwner == owner ||
                    (developerVerificationOwner == null && removed)) {
                developerVerificationReady = false;
                developerVerificationOwner = null;
            }
        }
    }

    boolean isConstructionAborted() {
        return constructionAborted || !abortedOwners.isEmpty();
    }
}
