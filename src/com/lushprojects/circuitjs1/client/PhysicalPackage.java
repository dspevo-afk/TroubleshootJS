package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Vector;

/** Typed package definition shared by placement, routing, and installed parts. */
final class PhysicalPackage {
    private final String id;
    private final Vector<String> terminalIds;
    private final Vector<String> internalConnections;
    private final boolean connector;

    PhysicalPackage(String id, int terminalCount) {
        this(id, terminalIdsForCount(terminalCount), new Vector<String>(), false);
    }

    PhysicalPackage(String id, Vector<String> terminalIds, Vector<String> internalConnections) {
        this(id, terminalIds, internalConnections, false);
    }

    PhysicalPackage(String id, Vector<String> terminalIds, Vector<String> internalConnections,
            boolean connector) {
        if (id == null || id.length() == 0 || terminalIds == null || terminalIds.size() < 1 ||
                internalConnections == null)
            throw new IllegalArgumentException("Invalid physical package");
        this.id = id;
        this.terminalIds = new Vector<String>(terminalIds);
        this.connector = connector;
        for (int index = 0; index < this.terminalIds.size(); index++) {
            String terminalId = this.terminalIds.get(index);
            if (terminalId == null || terminalId.length() == 0)
                throw new IllegalArgumentException("Invalid physical package terminal");
            for (int previous = 0; previous < index; previous++)
                if (terminalId.equals(this.terminalIds.get(previous)))
                    throw new IllegalArgumentException("Duplicate physical package terminal: " +
                        terminalId);
        }
        Vector<String> normalizedConnections = new Vector<String>();
        for (String connection : internalConnections) {
            if (connection == null)
                throw new IllegalArgumentException("Invalid physical package connectivity");
            int separator = connection.indexOf('=');
            if (separator <= 0 || separator != connection.lastIndexOf('=') ||
                    separator == connection.length() - 1)
                throw new IllegalArgumentException("Invalid physical package connectivity: " +
                    connection);
            String first = connection.substring(0, separator);
            String second = connection.substring(separator + 1);
            if (first.equals(second) || !this.terminalIds.contains(first) ||
                    !this.terminalIds.contains(second))
                throw new IllegalArgumentException("Physical package connectivity references " +
                    "invalid terminals: " + connection);
            String normalized = first.compareTo(second) < 0 ? first + "=" + second :
                second + "=" + first;
            if (normalizedConnections.contains(normalized))
                throw new IllegalArgumentException("Duplicate physical package connectivity: " +
                    connection);
            normalizedConnections.add(normalized);
        }
        Collections.sort(normalizedConnections);
        this.internalConnections = normalizedConnections;
    }

    String getId() { return id; }
    int getTerminalCount() { return terminalIds.size(); }
    Vector<String> getTerminalIds() { return new Vector<String>(terminalIds); }
    boolean isConnector() { return connector; }

    /** Package identity is declared by ID, while compatibility includes its definition. */
    boolean isEquivalentTo(PhysicalPackage other) {
        return other != null && id.equals(other.id) && connector == other.connector &&
            terminalIds.equals(other.terminalIds) && internalConnections.equals(
                other.internalConnections);
    }

    /** Returns true only for a declared package-internal connection. */
    boolean isInternallyConnected(String firstTerminal, String secondTerminal) {
        if (firstTerminal == null || secondTerminal == null || firstTerminal.equals(secondTerminal) ||
                !terminalIds.contains(firstTerminal) || !terminalIds.contains(secondTerminal))
            return false;
        String forward = firstTerminal + "=" + secondTerminal;
        String reverse = secondTerminal + "=" + firstTerminal;
        return internalConnections.contains(forward) || internalConnections.contains(reverse);
    }

    private static Vector<String> terminalIdsForCount(int count) {
        if (count < 1)
            throw new IllegalArgumentException("Physical package must have a terminal");
        Vector<String> result = new Vector<String>();
        for (int index = 1; index <= count; index++)
            result.add(String.valueOf(index));
        return result;
    }
}
