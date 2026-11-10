"""Pure pinned arm projection transform; no I/O or producer."""
import hashlib
SOURCE_CAP=4*1024*1024
CODEC_BYTES=37585
CODEC_SHA='8995d04ad4fc354e51b77adb8055ddc7a5a0cb48011a2d5186f5911e0458f9b7'
CHECKS_BYTES=1950
CHECKS_SHA='499b0bc19543ca386393c77d3211d0b1a97049f7be6a5d639c94613a435850ce'
ANCHOR=b'        validateCohortProjection(cohort, prior, sink);\n'
HELPER_ANCHOR=b'    private static String correlationString(Map<?, ?> values, String key,\n'
CALL=b'        validateArmProjections(arms, nullArm, observeArm, sink);\n'

class SourceError(ValueError):pass

def checked(raw,n,pin,code):
    if type(raw) is not bytes or len(raw)!=n or len(raw)>SOURCE_CAP:
        raise SourceError(code)
    if hashlib.sha256(raw).hexdigest()!=pin:raise SourceError(code)
    return raw

def derive(codec,checks):
    codec=checked(codec,CODEC_BYTES,CODEC_SHA,'CODEC_SOURCE')
    checks=checked(checks,CHECKS_BYTES,CHECKS_SHA,'CHECKS_SOURCE')
    if codec.count(ANCHOR)!=1 or codec.count(HELPER_ANCHOR)!=1 or b'validateArmProjections' in codec:
        raise SourceError('CODEC_ANCHOR')
    result=codec.replace(ANCHOR,ANCHOR+CALL,1).replace(HELPER_ANCHOR,checks+HELPER_ANCHOR,1)
    if len(result)>SOURCE_CAP:raise SourceError('OUTPUT_CAP')
    return result
