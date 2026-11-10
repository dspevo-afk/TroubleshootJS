"""Pure current-codec Source transformation; no I/O, CLI or producer."""
import hashlib

SOURCE_CAP = 4 * 1024 * 1024
CODEC_BYTES = 23469
CODEC_SHA = "e67dccf469fe8c8fe01812dcd28fd47463f0d9e2e4e1c451b9c65497d43c13a5"
CHECKS_BYTES = 1256
CHECKS_SHA = "4a04c09d87d54f6be11209f408c50870d7a1e879f02feaaf9e6074e446142bbb"
READER_BYTES = 430
READER_SHA = "0d655c75197a983d9145bc8c7d913ad9e4cc187940d9a7279fb18eb98ee0f121"
CHECK_ANCHOR = b'        if (cleanupNanos != workNanos) throw failure("BODY_CLEANUP_NANOS");\n'
READER_ANCHOR = b'    private CurrentV2CanonicalCodec() {\n'


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
    if codec.count(CHECK_ANCHOR) != 1 or codec.count(READER_ANCHOR) != 1:
        raise SourceError('CODEC_ANCHOR')
    if b'correlationBoolean(' in codec:
        raise SourceError('CODEC_ALREADY_DERIVED')
    result = codec.replace(CHECK_ANCHOR, CHECK_ANCHOR + checks, 1)
    result = result.replace(READER_ANCHOR, reader + b'\n' + READER_ANCHOR, 1)
    if len(result) > SOURCE_CAP:
        raise SourceError('OUTPUT_CAP')
    return result
