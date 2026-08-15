package com.lushprojects.circuitjs1.client;

class ResistorColorCode {
    static ResistorColorBand[] getFourBandCode(ResistorNameplate nameplate) {
        if (nameplate == null || nameplate.getTolerancePercent() != 5)
            throw new IllegalArgumentException("Only +/-5% four-band resistors are supported");
        int nominalResistance = (int) nameplate.getNominalResistanceOhms();
        if (nominalResistance != nameplate.getNominalResistanceOhms() || nominalResistance < 10)
            throw new IllegalArgumentException("Unsupported resistor value for four-band code");
        int resistance = nominalResistance;
        int multiplier = 0;
        while (resistance >= 100) {
            resistance /= 10;
            multiplier++;
        }
        if (resistance < 10 || resistance > 99 || multiplier > 9)
            throw new IllegalArgumentException("Unsupported resistor value for four-band code");
        if (resistance * powerOfTen(multiplier) != nominalResistance)
            throw new IllegalArgumentException("Unsupported resistor value for four-band code");
        return new ResistorColorBand[] { digit(resistance / 10), digit(resistance % 10),
            digit(multiplier), ResistorColorBand.GOLD };
    }

    private static int powerOfTen(int exponent) {
        int value = 1;
        for (int index = 0; index < exponent; index++)
            value *= 10;
        return value;
    }

    private static ResistorColorBand digit(int value) {
        ResistorColorBand[] colors = { ResistorColorBand.BLACK, ResistorColorBand.BROWN,
            ResistorColorBand.RED, ResistorColorBand.ORANGE, ResistorColorBand.YELLOW,
            ResistorColorBand.GREEN, ResistorColorBand.BLUE, ResistorColorBand.VIOLET,
            ResistorColorBand.GRAY, ResistorColorBand.WHITE };
        return colors[value];
    }
}
