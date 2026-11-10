"""Pure pinned cleanup projection transform; no I/O or producer."""
import hashlib
SOURCE_CAP=4*1024*1024
CODEC_BYTES=39600
CODEC_SHA='943319921ee22bdb25d96e2469c958ca02a8da83cd0d9e83e2f0e8c1deff0db9'
CHECKS_BYTES=1539
CHECKS_SHA='8e5c99e7ed51a6f84c95715f8f80366c6f6480c6845348c4cd329bdd67068972'
ANCHOR=b'        validateArmProjections(arms, nullArm, observeArm, sink);\n'
HELPER_ANCHOR=b'    private static String correlationString(Map<?, ?> values, String key,\n'
CALL=b'        validateCleanupProjection(cleanup, sink);\n'

class SourceError(ValueError):pass

def checked(raw,n,pin,code):
    if type(raw) is not bytes or len(raw)!=n or len(raw)>SOURCE_CAP:
        raise SourceError(code)
    if hashlib.sha256(raw).hexdigest()!=pin:raise SourceError(code)
    return raw

def derive(codec,checks):
    codec=checked(codec,CODEC_BYTES,CODEC_SHA,'CODEC_SOURCE')
    checks=checked(checks,CHECKS_BYTES,CHECKS_SHA,'CHECKS_SOURCE')
    if codec.count(ANCHOR)!=1 or codec.count(HELPER_ANCHOR)!=1 or b'validateCleanupProjection' in codec:
        raise SourceError('CODEC_ANCHOR')
    result=codec.replace(ANCHOR,ANCHOR+CALL,1).replace(HELPER_ANCHOR,checks+HELPER_ANCHOR,1)
    if len(result)>SOURCE_CAP:raise SourceError('OUTPUT_CAP')
    return result
