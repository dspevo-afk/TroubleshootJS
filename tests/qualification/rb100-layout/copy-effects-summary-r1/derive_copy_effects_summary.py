"""Pure pinned current-codec copy-effects transform; no I/O or producer."""
import hashlib

SOURCE_CAP=4*1024*1024
CODEC_BYTES=31250
CODEC_SHA='193b8b3d56aa909a87c825055c6f4fd47502e75107318eb936c5888de4fa1d64'
CHECKS_BYTES=844
CHECKS_SHA='34e44bdbd2185355485e5c2c69dcd76f3739731eb0e90ad80ab7543ca6d6e9bd'
READER_BYTES=511
READER_SHA='eee90389528f5e059e66e171103acf13708822c2e2c7916b8eca86cdc2b364aa'
ANCHOR=b'        if (geometrySummary != geometryUnchanged) throw failure("BODY_GEOMETRY_SUMMARY");\n'
OLD_READER=b'''    private static String correlationString(Map<?, ?> values, String key,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink) throws Exception {
        Object value = correlationRead(values, key, sink);
        sink.beforeDataOperation();
        if (!(value instanceof String)) throw failure("BODY_GEOMETRY_STRING");
        String text = (String) value;
        sink.beforeDataOperation();
        if (text.length() > BODY_STRING_LIMIT) throw failure("BODY_GEOMETRY_STRING");
        return text;
    }
'''
OLD_CALLS=(
    b'correlationString(\n                geometryTemplate, "geometryBeforeSha256", sink)',
    b'correlationString(\n                geometryTemplate, "geometryAfterSha256", sink)',
    b'correlationString(\n                nullCleanup, "sourceGeometryBeforeSha256", sink)',
    b'correlationString(\n                nullCleanup, "sourceGeometryAfterSha256", sink)',
    b'correlationString(\n                observeCleanup, "sourceGeometryBeforeSha256", sink)',
    b'correlationString(\n                observeCleanup, "sourceGeometryAfterSha256", sink)',
)


class SourceError(ValueError):
    """Stable Source-only admission failure."""


def checked(raw,n,pin,code):
    if type(raw) is not bytes or len(raw)!=n or len(raw)>SOURCE_CAP:
        raise SourceError(code)
    if hashlib.sha256(raw).hexdigest()!=pin:raise SourceError(code)
    return raw


def new_call(old):return old[:-1]+b', "BODY_GEOMETRY_STRING")'


def derive(codec,checks,reader):
    codec=checked(codec,CODEC_BYTES,CODEC_SHA,'CODEC_SOURCE')
    checks=checked(checks,CHECKS_BYTES,CHECKS_SHA,'CHECKS_SOURCE')
    reader=checked(reader,READER_BYTES,READER_SHA,'READER_SOURCE')
    if codec.count(ANCHOR)!=1 or codec.count(OLD_READER)!=1 or b'BODY_EFFECTS_' in codec:
        raise SourceError('CODEC_ANCHOR')
    for call in OLD_CALLS:
        if codec.count(call)!=1:raise SourceError('STRING_CALLS')
    result=codec
    for call in OLD_CALLS:result=result.replace(call,new_call(call),1)
    result=result.replace(OLD_READER,reader,1).replace(ANCHOR,ANCHOR+checks,1)
    if len(result)>SOURCE_CAP:raise SourceError('OUTPUT_CAP')
    return result
