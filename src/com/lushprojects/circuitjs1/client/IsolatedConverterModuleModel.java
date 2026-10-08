package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** One isolated opaque module: six converter ports plus its actual powered bias port. */
final class IsolatedConverterModuleModel {
    static final int TERMINAL_COUNT = 7, BIAS = 6;
    private static final String[] TERMINAL_IDS = {"IN+", "IN-", "PRE_L+", "OUT-", "EN", "FB", "BIAS"};
    final E06AveragedConverterElm converter;
    final E06PwmControllerElm.BiasElm bias;
    private final Vector<CircuitElm> backing = new Vector<CircuitElm>();

    IsolatedConverterModuleModel(int x, int y) {
        converter = new E06AveragedConverterElm(x, y, new E06ConverterContract());
        bias = new E06PwmControllerElm.BiasElm(x, y + 256);
        backing.add(converter); backing.add(bias);
        backing.add(wire(converter.getPost(E06ConverterContract.IN_PLUS), bias.getPost(0)));
        backing.add(wire(converter.getPost(E06ConverterContract.IN_MINUS), bias.getPost(1)));
    }

    Vector<CircuitElm> elements() { return new Vector<CircuitElm>(backing); }

    CircuitPostMeasurementEndpoint terminal(int index) {
        requireTerminal(index);
        return index == BIAS ? new CircuitPostMeasurementEndpoint(bias, 2) :
            new CircuitPostMeasurementEndpoint(converter, index);
    }

    String terminalId(int index) { requireTerminal(index); return TERMINAL_IDS[index]; }

    Point terminalPoint(int index) {
        CircuitPostMeasurementEndpoint endpoint = terminal(index);
        return endpoint.getElement().getPost(endpoint.getPostIndex());
    }

    private static void requireTerminal(int index) {
        if (index < 0 || index >= TERMINAL_COUNT)
            throw new IllegalArgumentException("Invalid isolated converter terminal");
    }

    private static WireElm wire(Point from, Point to) {
        WireElm result = new WireElm(from.x, from.y);
        result.x2 = to.x; result.y2 = to.y; result.setPoints(); return result;
    }
}