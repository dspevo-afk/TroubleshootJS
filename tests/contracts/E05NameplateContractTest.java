package com.lushprojects.circuitjs1.client;

/** Independent waveform-convention and existing-DC marking canaries. */
public final class E05NameplateContractTest {
    private static int assertions;
    public static void main(String[] args) {
        PowerInputNameplate dc = new PowerInputNameplate("VIN", 5);
        check("+5V".equals(dc.getDisplayLabel()), "existing integer DC label");
        check("+3.3V".equals(new PowerInputNameplate("VIN", 3.3).getDisplayLabel()),
            "existing fractional DC label");
        check(dc.getConvention() == PowerInputNameplate.Convention.DC && dc.getFrequencyHz() == 0,
            "old constructor declares DC");
        PowerInputNameplate ac = PowerInputNameplate.acRms("AC_INPUT", 120, 60);
        check(ac.getNominalVoltage() == 120 && ac.getFrequencyHz() == 60 &&
            ac.getConvention() == PowerInputNameplate.Convention.AC_RMS, "AC convention is RMS");
        check("120 VAC RMS / 60 Hz".equals(ac.getDisplayLabel()), "explicit AC marking");
        check(!ac.getDisplayLabel().equals(new PowerInputNameplate("AC_INPUT", 120).getDisplayLabel()),
            "AC and DC identities differ");
        check(!ac.getDisplayLabel().equals(PowerInputNameplate.acRms("AC_INPUT", 120, 50).getDisplayLabel()),
            "frequency is bound by display identity");
        check("12.5 VAC RMS / 59.5 Hz".equals(
            PowerInputNameplate.acRms("AC_INPUT", 12.5, 59.5).getDisplayLabel()), "fractional AC marking");
        for (final double bad : new double[] {0, -1, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            reject(new Runnable() { public void run() { PowerInputNameplate.acRms("AC_INPUT", bad, 60); } });
            reject(new Runnable() { public void run() { PowerInputNameplate.acRms("AC_INPUT", 120, bad); } });
            reject(new Runnable() { public void run() { new PowerInputNameplate("VIN", bad); } });
        }
        reject(new Runnable() { public void run() { PowerInputNameplate.acRms(null, 120, 60); } });
        reject(new Runnable() { public void run() { PowerInputNameplate.acRms("", 120, 60); } });
        System.out.println("PASS: E05 nameplate contracts assertions=" + assertions);
    }
    private static void reject(Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid input convention rejected");
    }
    private static void check(boolean value, String reason) {
        assertions++; if (!value) throw new AssertionError(reason);
    }
}
