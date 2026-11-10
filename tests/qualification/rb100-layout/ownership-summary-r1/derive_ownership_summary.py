"""Pure current-codec ownership summary transform; no I/O or producer."""
import hashlib

SOURCE_CAP = 4 * 1024 * 1024
CODEC_BYTES = 25156
CODEC_SHA = '344223e4f3d12bc9c1bb219bca80a8c711ce99aed8fe4b309c1043da6b5f4dc4'
CHECKS_BYTES = 3363
CHECKS_SHA = 'fc23a80575de4c16caac770bf7561799f778e116e89d1ae33bcb4bccfd539bcd'
READER_BYTES = 431
READER_SHA = 'c86e298ac292d94d9adf1a37a5a5db19e5e0d9833eef67a43bd86422ee41214e'
ANCHOR = b'        if (totalSummary != allRestored) throw failure("BODY_RESTORATION_TOTAL");\n'
OLD_READER = b'''    private static boolean correlationBoolean(Map<?, ?> values, String key,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink) throws Exception {
        Object value = correlationRead(values, key, sink);
        sink.beforeDataOperation();
        if (!(value instanceof Boolean)) throw failure("BODY_RESTORATION_BOOLEAN");
        sink.beforeDataOperation();
        return ((Boolean) value).booleanValue();
    }
'''
OLD_CALLS = (
    b'correlationBoolean(capture, "contextRestored", sink)',
    b'correlationBoolean(nullCleanup, "contextRestored", sink)',
    b'correlationBoolean(observeCleanup, "contextRestored", sink)',
    b'correlationBoolean(\n                cleanup, "originalCaptureContextRestored", sink)',
    b'correlationBoolean(cleanup, "contextRestored", sink)',
)


class SourceError(ValueError):
    """Stable Source-only admission failure."""


def checked(raw, n, pin, code):
    if type(raw) is not bytes or len(raw) != n or len(raw) > SOURCE_CAP:
        raise SourceError(code)
    if hashlib.sha256(raw).hexdigest() != pin:
        raise SourceError(code)
    return raw


def new_call(old):
    return old[:-1] + b', "BODY_RESTORATION_BOOLEAN")'


def derive(codec, checks, reader):
    codec=checked(codec,CODEC_BYTES,CODEC_SHA,'CODEC_SOURCE')
    checks=checked(checks,CHECKS_BYTES,CHECKS_SHA,'CHECKS_SOURCE')
    reader=checked(reader,READER_BYTES,READER_SHA,'READER_SOURCE')
    if codec.count(ANCHOR)!=1 or codec.count(OLD_READER)!=1 or b'BODY_OWNERSHIP_' in codec:
        raise SourceError('CODEC_ANCHOR')
    for call in OLD_CALLS:
        if codec.count(call)!=1:raise SourceError('BOOLEAN_CALLS')
    result=codec
    for call in OLD_CALLS:result=result.replace(call,new_call(call),1)
    result=result.replace(OLD_READER,reader,1).replace(ANCHOR,ANCHOR+checks,1)
    if len(result)>SOURCE_CAP:raise SourceError('OUTPUT_CAP')
    return result
