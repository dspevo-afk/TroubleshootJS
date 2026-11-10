"""Pure pinned cleanup discriminator transform; no I/O or producer."""
import hashlib

SOURCE_CAP=4*1024*1024
CODEC_BYTES=32230
CODEC_SHA='eb35fcb9df7e73eb0cbe704633954bdb10b2832bfa897f610613a8020e2aab15'
CHECKS_BYTES=195
CHECKS_SHA='37bee17702de121450d76836ffd98f26e4b2dda99f3637ce4eb1ad2459e8957a'
ANCHOR=b'        if (copyEffectsSummary != copyEffectsUnchanged) throw failure("BODY_EFFECTS_SUMMARY");\n'


class SourceError(ValueError):
    """Stable Source-only admission failure."""


def checked(raw,n,pin,code):
    if type(raw) is not bytes or len(raw)!=n or len(raw)>SOURCE_CAP:
        raise SourceError(code)
    if hashlib.sha256(raw).hexdigest()!=pin:raise SourceError(code)
    return raw


def derive(codec,checks):
    codec=checked(codec,CODEC_BYTES,CODEC_SHA,'CODEC_SOURCE')
    checks=checked(checks,CHECKS_BYTES,CHECKS_SHA,'CHECKS_SOURCE')
    if codec.count(ANCHOR)!=1 or b'BODY_CLEANUP_SCHEMA' in codec:
        raise SourceError('CODEC_ANCHOR')
    result=codec.replace(ANCHOR,ANCHOR+checks,1)
    if len(result)>SOURCE_CAP:raise SourceError('OUTPUT_CAP')
    return result
