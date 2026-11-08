/* Nested Java7 members. Prospective R9 canary-only text policy, metadata use.
 * Uses R9CanonicalJson's shared pure path helpers; no duplicate whitelist,
 * default formatter change, copied tree, path String, or retained body. */
private static final class R9TextPolicyLedger { long visits; }
private static void r9TextPolicyRequire(boolean ok,String reason) throws java.io.IOException {
    if (!ok) throw new java.io.IOException(reason);
}
static void validateR9CanaryTextPolicy(Object body,SetupMeter meter) throws Exception {
    if (meter==null) throw new java.io.IOException("R9_TEXT_POLICY_METER_MISSING");
    meter.charge(1); // before control ledger allocation
    R9TextPolicyLedger ledger=new R9TextPolicyLedger();
    r9ValidateTextNode(body,R9_PATH_ROOT,0,ledger,meter);
}
private static void r9ValidatePolicyString(String text,int state,SetupMeter meter)
        throws Exception {
    meter.charge(1); int limit=r9PathStringLimit(state);
    meter.charge(1); int length=text.length();
    meter.charge(1); r9TextPolicyRequire(length<=limit,"R9_TEXT_POLICY_LENGTH");
    int i=0;
    while (true) {
        meter.charge(1); if (i==length) break;
        meter.charge(1); char c=text.charAt(i++);
        meter.charge(1);
        r9TextPolicyRequire(c>=32 && c<=126,"R9_TEXT_POLICY_ASCII");
    }
}
private static void r9ValidateTextNode(Object value,int state,int depth,
        R9TextPolicyLedger ledger,SetupMeter meter) throws Exception {
    meter.charge(1); r9TextPolicyRequire(depth<=64,"R9_TEXT_POLICY_DEPTH");
    meter.charge(1); r9TextPolicyRequire(ledger.visits<1048576L,"R9_TEXT_POLICY_VISITS");
    meter.charge(1); ledger.visits++;
    // Fixed R9 metadata operation: shared dispatch + optional null/type tests.
    meter.charge(3);
    r9TextPolicyRequire(r9PathTypeValid(state,value),"R9_TEXT_POLICY_STRUCTURE");
    meter.charge(1);
    if (value instanceof java.util.Map) {
        meter.charge(1);
        java.util.Iterator<? extends java.util.Map.Entry<?,?>> entries=
            ((java.util.Map<?,?>)value).entrySet().iterator();
        while (true) {
            meter.charge(1); if (!entries.hasNext()) break;
            meter.charge(1); java.util.Map.Entry<?,?> entry=entries.next();
            meter.charge(1); Object key=entry.getKey();
            meter.charge(1); r9TextPolicyRequire(key instanceof String,"R9_TEXT_POLICY_KEY_TYPE");
            r9ValidatePolicyString((String)key,R9_PATH_OTHER,meter); // keys never inherit value exemption
            // Fixed R9 metadata operation: shared dispatch + at most 3 equalities.
            meter.charge(4);
            int childState=r9PathMapChild(state,(String)key);
            meter.charge(1); Object child=entry.getValue();
            r9ValidateTextNode(child,childState,depth+1,ledger,meter);
        }
        return;
    }
    meter.charge(1);
    if (value instanceof java.util.List) {
        meter.charge(1);
        java.util.Iterator<?> entries=((java.util.List<?>)value).iterator();
        meter.charge(1);
        int childState=r9PathListChild(state);
        while (true) {
            meter.charge(1); if (!entries.hasNext()) break;
            meter.charge(1); Object child=entries.next();
            // Only an actual components List iteration creates COMPONENT state.
            r9ValidateTextNode(child,childState,depth+1,ledger,meter);
        }
        return;
    }
    meter.charge(1);
    if (value instanceof String) {
        r9ValidatePolicyString((String)value,state,meter);
        return;
    }
    meter.charge(1); if (value instanceof Integer) return;
    meter.charge(1); if (value instanceof Long) return;
    meter.charge(1); if (value instanceof Boolean) return;
    meter.charge(1); if (value==null) return;
    throw new java.io.IOException("R9_TEXT_POLICY_TYPE");
}
