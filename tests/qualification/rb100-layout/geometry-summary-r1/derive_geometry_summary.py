"""Pure pinned current-codec geometry summary transform; no I/O or producer."""
import hashlib

SOURCE_CAP = 4 * 1024 * 1024
CODEC_BYTES = 28660
CODEC_SHA = '0faa081daf3c6b478424c088759488fa2d6ae784574b5e3362993a8ef4b732c7'
CHECKS_BYTES = 2070
CHECKS_SHA = '93c4c6e7d981f39f0f7bb9eaecc5e19efba107695666684fc65b06bd0ff0bdbe'
READER_BYTES = 519
READER_SHA = '0f5ec750cd999c325dff541cb2653649ddf36c3ea584dd7ffef93c11d6050644'
ANCHOR = b'        if (observeAliasSummary != observeAllDropped) throw failure("BODY_OWNERSHIP_OBSERVE");\n'
HELPER_ANCHOR = b'    private static Object correlationRead(Map<?, ?> values, String key,\n'


class SourceError(ValueError):
    """Stable Source-only admission failure."""


def checked(raw, n, pin, code):
    if type(raw) is not bytes or len(raw) != n or len(raw) > SOURCE_CAP:
        raise SourceError(code)
    if hashlib.sha256(raw).hexdigest() != pin:
        raise SourceError(code)
    return raw


def derive(codec, checks, reader):
    codec = checked(codec, CODEC_BYTES, CODEC_SHA, 'CODEC_SOURCE')
    checks = checked(checks, CHECKS_BYTES, CHECKS_SHA, 'CHECKS_SOURCE')
    reader = checked(reader, READER_BYTES, READER_SHA, 'READER_SOURCE')
    if codec.count(ANCHOR) != 1 or codec.count(HELPER_ANCHOR) != 1 or b'BODY_GEOMETRY_' in codec:
        raise SourceError('CODEC_ANCHOR')
    result = codec.replace(ANCHOR, ANCHOR + checks, 1)
    result = result.replace(HELPER_ANCHOR, reader + b'\n' + HELPER_ANCHOR, 1)
    if len(result) > SOURCE_CAP:
        raise SourceError('OUTPUT_CAP')
    return result
