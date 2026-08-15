package com.lushprojects.circuitjs1.client;

class ResistorColorCode {
    static ResistorColorBand[] getFourBandCode(ResistorNameplate nameplate) {
        if (nameplate == null || nameplate.getTolerancePercent() != 5)
            throw new IllegalArgumentException("Only +/-5% four-band resistors are supported");
        int resistance = (int) nameplate.getNominalResistanceOhms();
        if (resistance != nameplate.getNominalResistanceOhms() || resistance < 10)
            throw new IllegalArgumentException("Unsupported resistor value for four-band code");
        int multiplier = 0;
        while (resistance >= 100) {
            resistance /= 10;
            multiplier++;
        }
        if (resistance < 10 || resistance > 99 || multiplier > 9)
            throw new IllegalArgumentException("Unsupported resistor value for four-band code");
        return new ResistorColorBand[] { digit(resistance / 10), digit(resistance % 10),
            digit(multiplier), ResistorColorBand.GOLD };
    }

    private static ResistorColorBand digit(int value) {
        ResistorColorBand[] colors = { ResistorColorBand.BLACK, ResistorColorBand.BROWN,
            ResistorColorBand.RED, ResistorColorBand.ORANGE, ResistorColorBand.YELLOW,
            ResistorColorBand.GREEN, ResistorColorBand.BLUE, ResistorColorBand.VIOLET,
            ResistorColorBand.GRAY, ResistorColorBand.WHITE };
        return colors[value];
    }
}