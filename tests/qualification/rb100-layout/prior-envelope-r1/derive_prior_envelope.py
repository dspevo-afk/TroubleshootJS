"""Pure pinned prior-envelope transform; no I/O or producer."""
import hashlib
SOURCE_CAP=4*1024*1024
CODEC_BYTES=41189
CODEC_SHA='8f617b6e71f6fae29bee1ece0e5bdaf69d3742ba198f2b14edc4e94217e63b73'
CHECKS_BYTES=1862
CHECKS_SHA='e4ae2f6b34949b034def1d8465f045b87e5344325d8d6a6fe742e1cdeef1d128'
ANCHOR=b'        validateCleanupProjection(cleanup, sink);\n'
HELPER_ANCHOR=b'    private static String correlationString(Map<?, ?> values, String key,\n'
CALL=b'        validatePriorEnvelope(prior, sink);\n'

class SourceError(ValueError):pass

def checked(raw,n,pin,code):
    if type(raw) is not bytes or len(raw)!=n or len(raw)>SOURCE_CAP:
        raise SourceError(code)
    if hashlib.sha256(raw).hexdigest()!=pin:raise SourceError(code)
    return raw

def derive(codec,checks):
    codec=checked(codec,CODEC_BYTES,CODEC_SHA,'CODEC_SOURCE')
    checks=checked(checks,CHECKS_BYTES,CHECKS_SHA,'CHECKS_SOURCE')
    if codec.count(ANCHOR)!=1 or codec.count(HELPER_ANCHOR)!=1 or b'validatePriorEnvelope' in codec:
        raise SourceError('CODEC_ANCHOR')
    result=codec.replace(ANCHOR,ANCHOR+CALL,1).replace(HELPER_ANCHOR,checks+HELPER_ANCHOR,1)
    if len(result)>SOURCE_CAP:raise SourceError('OUTPUT_CAP')
    return result
