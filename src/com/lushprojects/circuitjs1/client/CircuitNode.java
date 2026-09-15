/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.

    CircuitJS1 is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 2 of the License, or
    (at your option) any later version.

    CircuitJS1 is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with CircuitJS1.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class CircuitNode {
    Vector<CircuitNodeLink> links;
    CircuitElm[] voltageElements;
    int[] voltagePosts;
    boolean[] idealWires;
    boolean internal;
    CircuitNode() { links = new Vector<CircuitNodeLink>(); }

    /** Frozen by analysis; a new graph gets new CircuitNode instances. */
    void prepareVoltageRecipients() {
        voltageElements = new CircuitElm[links.size()];
        voltagePosts = new int[links.size()];
        idealWires = new boolean[links.size()];
        for (int i = 0; i < links.size(); i++) {
            CircuitNodeLink link = links.get(i);
            voltageElements[i] = link.elm;
            voltagePosts[i] = link.num;
            idealWires[i] = link.elm.getClass() == WireElm.class;
        }
    }

    void applyVoltage(double value) {
        for (int i = 0; i < voltageElements.length; i++) {
            CircuitElm element = voltageElements[i];
            // Exact ideal wires inherit an empty calculateCurrent callback.
            // All other models keep their original voltage setter and order.
            if (idealWires[i]) element.volts[voltagePosts[i]] = value;
            else element.setNodeVoltage(voltagePosts[i], value);
        }
    }
}
