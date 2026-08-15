package com.lushprojects.circuitjs1.client;

class PowerInputNameplate {
    private final String powerInputId;
    private final double nominalVoltage;

    PowerInputNameplate(String powerInputId, double nominalVoltage) {
        if (powerInputId == null || powerInputId.length() == 0 ||
            Double.isNaN(nominalVoltage) || Double.isInfinite(nominalVoltage) ||
            nominalVoltage <= 0)
            throw new IllegalArgumentException("Invalid power input nameplate");
        this.powerInputId = powerInputId;
        this.nominalVoltage = nominalVoltage;
    }

    String getPowerInputId() { return powerInputId; }
    double getNominalVoltage() { return nominalVoltage; }

    String getDisplayLabel() {
        if (nominalVoltage == Math.rint(nominalVoltage))
            return "+" + (long) nominalVoltage + "V";
        return "+" + nominalVoltage + "V";
    }
}
