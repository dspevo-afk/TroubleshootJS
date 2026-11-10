"""Pure transformation of the reviewed current qualification Sources.

No filesystem, CLI, producer, class loading, or graph interface is owned here.
The caller supplies bytes through its existing admitted Source reader.
"""

import hashlib

SOURCE_CAP = 4 * 1024 * 1024
STUDY_SHA = "90f4c227d125587a7d1c8d3b324fe7fd375fd89af7a8a34a83a65a3c0d310bad"
CODEC_SHA = "04b016afa690d2154f0b970640bc30b7c4887b450622444f8f18329bc04c3672"
FRAGMENT_SHA = "380970e513160d5f27dd8b673a404cf491b3285f5dc4374ddd86e02c49a4f970"
ANCHOR = b"    private CurrentV2CanonicalCodec() {\n"
OLD_CALL = b"CurrentV2CanonicalCodec.writeBody(result,"
NEW_CALL = b"CurrentV2CanonicalCodec.writeCorrelatedBody(result,"


class SourceError(ValueError):
    """Stable Source-only admission failure."""


def checked(raw, size, expected, code):
    if type(raw) is not bytes or len(raw) != size or len(raw) > SOURCE_CAP:
        raise SourceError(code)
    if hashlib.sha256(raw).hexdigest() != expected:
        raise SourceError(code)
    return raw


def derive(study, codec, fragment):
    """Return (current Study, current codec); never consume files or data."""
    study = checked(study, 236944, STUDY_SHA, "STUDY_SOURCE")
    codec = checked(codec, 20119, CODEC_SHA, "CODEC_SOURCE")
    fragment = checked(fragment, FRAGMENT_BYTES, FRAGMENT_SHA, "FRAGMENT_SOURCE")
    if codec.count(ANCHOR) != 1 or b"writeCorrelatedBody" in codec:
        raise SourceError("CODEC_ANCHOR")
    if study.count(OLD_CALL) != 3 or b"writeCorrelatedBody" in study:
        raise SourceError("STUDY_CALLS")
    for sink in (b"count", b"writer", b"reader"):
        if study.count(OLD_CALL + sink + b");") != 1:
            raise SourceError("STUDY_CALLS")
    new_codec = codec.replace(ANCHOR, fragment + ANCHOR, 1)
    new_study = study.replace(OLD_CALL, NEW_CALL)
    if len(new_codec) > SOURCE_CAP or len(new_study) > SOURCE_CAP:
        raise SourceError("OUTPUT_CAP")
    return new_study, new_codec


# Exact reviewed fragment size, not a dynamic acceptance of arbitrary Source.
FRAGMENT_BYTES = 3350
