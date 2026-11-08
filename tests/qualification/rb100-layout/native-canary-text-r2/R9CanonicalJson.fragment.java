/* Nested members for the disconnected compact ASCII JSON formatter. */
interface CanonicalJsonSink {
    void beforeDataOperation() throws Exception;
    void writeByte(int ascii) throws Exception;
}
static final class CanonicalJsonVisits {
    long count;
}
/* Pure shared path policy; metadata validator meters these calls separately. */
static final int R9_PATH_ROOT=0, R9_PATH_ARM=1, R9_PATH_OPTIONAL_ARM=2,
    R9_PATH_FRESH=3, R9_PATH_LAYOUT=4, R9_PATH_COMPONENTS=5,
    R9_PATH_COMPONENT=6, R9_PATH_GEOMETRY=7, R9_PATH_OTHER=8;
static int r9PathMapChild(int state,String key) {
    switch (state) {
    case R9_PATH_ROOT:
        if ("complete".equals(key)) return R9_PATH_ARM;
        if ("blocked".equals(key)) return R9_PATH_ARM;
        if ("nonBranchQuality".equals(key)) return R9_PATH_OPTIONAL_ARM;
        return R9_PATH_OTHER;
    case R9_PATH_ARM:case R9_PATH_OPTIONAL_ARM:
        return "freshRun".equals(key)?R9_PATH_FRESH:R9_PATH_OTHER;
    case R9_PATH_FRESH:
        return "layout".equals(key)?R9_PATH_LAYOUT:R9_PATH_OTHER;
    case R9_PATH_LAYOUT:
        return "components".equals(key)?R9_PATH_COMPONENTS:R9_PATH_OTHER;
    case R9_PATH_COMPONENT:
        return "geometryCanonical".equals(key)?R9_PATH_GEOMETRY:R9_PATH_OTHER;
    default:return R9_PATH_OTHER;
    }
}
static int r9PathListChild(int state) {
    return state==R9_PATH_COMPONENTS?R9_PATH_COMPONENT:R9_PATH_OTHER;
}
static boolean r9PathTypeValid(int state,Object value) {
    switch (state) {
    case R9_PATH_ROOT:case R9_PATH_ARM:case R9_PATH_FRESH:case R9_PATH_COMPONENT:
        return value instanceof java.util.Map;
    case R9_PATH_OPTIONAL_ARM:case R9_PATH_LAYOUT:
        return value==null || value instanceof java.util.Map;
    case R9_PATH_COMPONENTS:return value instanceof java.util.List;
    case R9_PATH_GEOMETRY:return value instanceof String;
    case R9_PATH_OTHER:return true;
    default:return false;
    }
}
static int r9PathStringLimit(int state) { return state==R9_PATH_GEOMETRY?4096:256; }
static void writeCanonicalJson(Object value,CanonicalJsonSink sink) throws Exception {
    writeCanonicalJsonWithValueLimit(value,sink,false);
}
/* Caller must validate exact permitted long-value paths and own the body exclusively. */
static void writeCanonicalJsonCanaryValues(Object value,CanonicalJsonSink sink) throws Exception {
    writeCanonicalJsonWithValueLimit(value,sink,true);
}
private static void writeCanonicalJsonWithValueLimit(Object value,CanonicalJsonSink sink,boolean canary) throws Exception {
    if (sink==null) throw new IllegalArgumentException("missing JSON sink");
    sink.beforeDataOperation();
    CanonicalJsonVisits visits=new CanonicalJsonVisits();
    writeCanonicalValueWithLimit(value,sink,visits,0,canary,R9_PATH_ROOT);
    sink.writeByte('\n');
}
static void writeCanonicalValue(Object value,CanonicalJsonSink sink,
        CanonicalJsonVisits visits,int depth) throws Exception {
    writeCanonicalValueWithLimit(value,sink,visits,depth,false,R9_PATH_OTHER);
}
private static void writeCanonicalValueWithLimit(Object value,CanonicalJsonSink sink,
        CanonicalJsonVisits visits,int depth,boolean canary,int pathState) throws Exception {
    sink.beforeDataOperation();
    if (depth>64) throw new IllegalArgumentException("JSON depth");
    sink.beforeDataOperation();
    if (visits.count>=1048576L) throw new IllegalArgumentException("JSON visits");
    sink.beforeDataOperation();
    visits.count++;
    if (canary) {
        sink.beforeDataOperation();
        if (!r9PathTypeValid(pathState,value)) throw new IllegalArgumentException("JSON canary path type");
    }
    sink.beforeDataOperation();
    if (value==null) { writeCanonicalLiteral(sink,"null"); return; }
    sink.beforeDataOperation();
    if (value instanceof String) {
        int limit=256;
        if (canary) { sink.beforeDataOperation(); limit=r9PathStringLimit(pathState); }
        writeCanonicalStringWithLimit((String)value,sink,limit); return;
    }
    sink.beforeDataOperation();
    if (value instanceof Integer || value instanceof Long) {
        sink.beforeDataOperation();
        writeCanonicalInteger(((Number)value).longValue(),sink);
        return;
    }
    sink.beforeDataOperation();
    if (value instanceof Boolean) {
        sink.beforeDataOperation();
        if (((Boolean)value).booleanValue()) writeCanonicalLiteral(sink,"true");
        else writeCanonicalLiteral(sink,"false");
        return;
    }
    sink.beforeDataOperation();
    if (value instanceof java.util.Map) {
        sink.writeByte('{');
        sink.beforeDataOperation();
        java.util.Iterator<? extends java.util.Map.Entry<?,?>> it=
            ((java.util.Map<?,?>)value).entrySet().iterator();
        boolean first=true;
        while (true) {
            sink.beforeDataOperation();
            if (!it.hasNext()) break;
            sink.beforeDataOperation();
            java.util.Map.Entry<?,?> entry=it.next();
            sink.beforeDataOperation();
            Object key=entry.getKey();
            sink.beforeDataOperation();
            if (!(key instanceof String)) throw new IllegalArgumentException("JSON key");
            int childState=R9_PATH_OTHER;
            if (canary) { sink.beforeDataOperation(); childState=r9PathMapChild(pathState,(String)key); }
            if (!first) sink.writeByte(',');
            first=false;
            writeCanonicalString((String)key,sink);
            sink.writeByte(':');
            sink.beforeDataOperation();
            Object child=entry.getValue();
            sink.beforeDataOperation();
            writeCanonicalValueWithLimit(child,sink,visits,depth+1,canary,childState);
        }
        sink.writeByte('}');
        return;
    }
    sink.beforeDataOperation();
    if (value instanceof java.util.List) {
        sink.writeByte('[');
        sink.beforeDataOperation();
        java.util.Iterator<?> it=((java.util.List<?>)value).iterator();
        boolean first=true;
        while (true) {
            sink.beforeDataOperation();
            if (!it.hasNext()) break;
            sink.beforeDataOperation();
            Object child=it.next();
            if (!first) sink.writeByte(',');
            first=false;
            sink.beforeDataOperation();
            int childState=R9_PATH_OTHER;
            if (canary) { sink.beforeDataOperation(); childState=r9PathListChild(pathState); }
            writeCanonicalValueWithLimit(child,sink,visits,depth+1,canary,childState);
        }
        sink.writeByte(']');
        return;
    }
    throw new IllegalArgumentException("JSON value");
}
static void writeCanonicalString(String value,CanonicalJsonSink sink) throws Exception {
    writeCanonicalStringWithLimit(value,sink,256);
}
private static void writeCanonicalStringWithLimit(String value,CanonicalJsonSink sink,int limit) throws Exception {
    sink.beforeDataOperation();
    int length=value.length();
    sink.beforeDataOperation();
    if (length>limit) throw new IllegalArgumentException("JSON string length");
    sink.writeByte('"');
    int i=0;
    while (true) {
        sink.beforeDataOperation();
        if (i>=length) break;
        sink.beforeDataOperation();
        char c=value.charAt(i++);
        sink.beforeDataOperation();
        if (c<32 || c>126) throw new IllegalArgumentException("JSON string ASCII");
        sink.beforeDataOperation();
        if (c=='"' || c=='\\') sink.writeByte('\\');
        sink.writeByte(c);
    }
    sink.writeByte('"');
}
static void writeCanonicalLiteral(CanonicalJsonSink sink,String literal) throws Exception {
    sink.beforeDataOperation();
    int length=literal.length();
    int i=0;
    while (true) {
        sink.beforeDataOperation();
        if (i>=length) break;
        sink.beforeDataOperation();
        sink.writeByte(literal.charAt(i++));
    }
}
static void writeCanonicalInteger(long value,CanonicalJsonSink sink) throws Exception {
    sink.beforeDataOperation();
    boolean negative=value<0;
    sink.beforeDataOperation();
    long remaining=negative?value:-value;
    if (negative) sink.writeByte('-');
    long divisor=1;
    while (true) {
        sink.beforeDataOperation();
        if (remaining/divisor>-10) break;
        sink.beforeDataOperation();
        divisor*=10;
    }
    while (true) {
        sink.beforeDataOperation();
        int digit=(int)(-(remaining/divisor));
        sink.writeByte('0'+digit);
        sink.beforeDataOperation();
        remaining%=divisor;
        sink.beforeDataOperation();
        if (divisor==1) break;
        sink.beforeDataOperation();
        divisor/=10;
    }
}
