/* Nested members for the disconnected compact ASCII JSON formatter. */
interface CanonicalJsonSink {
    void beforeDataOperation() throws Exception;
    void writeByte(int ascii) throws Exception;
}
static final class CanonicalJsonVisits {
    long count;
}
static void writeCanonicalJson(Object value,CanonicalJsonSink sink) throws Exception {
    if (sink==null) throw new IllegalArgumentException("missing JSON sink");
    sink.beforeDataOperation();
    CanonicalJsonVisits visits=new CanonicalJsonVisits();
    writeCanonicalValue(value,sink,visits,0);
    sink.writeByte('\n');
}
static void writeCanonicalValue(Object value,CanonicalJsonSink sink,
        CanonicalJsonVisits visits,int depth) throws Exception {
    sink.beforeDataOperation();
    if (depth>64) throw new IllegalArgumentException("JSON depth");
    sink.beforeDataOperation();
    if (visits.count>=1048576L) throw new IllegalArgumentException("JSON visits");
    sink.beforeDataOperation();
    visits.count++;
    sink.beforeDataOperation();
    if (value==null) { writeCanonicalLiteral(sink,"null"); return; }
    sink.beforeDataOperation();
    if (value instanceof String) { writeCanonicalString((String)value,sink); return; }
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
            if (!first) sink.writeByte(',');
            first=false;
            writeCanonicalString((String)key,sink);
            sink.writeByte(':');
            sink.beforeDataOperation();
            Object child=entry.getValue();
            sink.beforeDataOperation();
            writeCanonicalValue(child,sink,visits,depth+1);
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
            writeCanonicalValue(child,sink,visits,depth+1);
        }
        sink.writeByte(']');
        return;
    }
    throw new IllegalArgumentException("JSON value");
}
static void writeCanonicalString(String value,CanonicalJsonSink sink) throws Exception {
    sink.beforeDataOperation();
    int length=value.length();
    sink.beforeDataOperation();
    if (length>256) throw new IllegalArgumentException("JSON string length");
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
