"""Pure pinned cohort contract transform. No I/O or producer execution."""
import hashlib
SOURCE_CAP=4*1024*1024
CODEC_BYTES=32425
CODEC_SHA='a4b387e6f55cc4cce8802fa97cc1bb05f8852b016065cc17a799a2f36b99b652'
CHECKS_BYTES=5017
CHECKS_SHA='7eec89ee4b7b5bcd6c1a0ea7310be34f46f019291dd4da9c74f5e5edd3d26746'
ANCHOR=b'        requireFixedString(cleanupSchema, "tsj-cohort-terminal-cleanup-v1", sink,\n                "BODY_CLEANUP_SCHEMA");\n'
HELPER_ANCHOR=b'    private static String correlationString(Map<?, ?> values, String key,\n'
CALL=b'        Map<?, ?> cohort = correlationMap(correlationRead(body, "cohort", sink), sink);\n        validateCohortProjection(cohort, prior, sink);\n'

class SourceError(ValueError):pass

def checked(raw,n,pin,code):
    if type(raw) is not bytes or len(raw)!=n or len(raw)>SOURCE_CAP:
        raise SourceError(code)
    if hashlib.sha256(raw).hexdigest()!=pin:raise SourceError(code)
    return raw

def derive(codec,checks):
    codec=checked(codec,CODEC_BYTES,CODEC_SHA,'CODEC_SOURCE')
    checks=checked(checks,CHECKS_BYTES,CHECKS_SHA,'CHECKS_SOURCE')
    if codec.count(ANCHOR)!=1 or codec.count(HELPER_ANCHOR)!=1 or b'validateCohortProjection' in codec:
        raise SourceError('CODEC_ANCHOR')
    result=codec.replace(ANCHOR,ANCHOR+CALL,1).replace(HELPER_ANCHOR,checks+HELPER_ANCHOR,1)
    if len(result)>SOURCE_CAP:raise SourceError('OUTPUT_CAP')
    return result
