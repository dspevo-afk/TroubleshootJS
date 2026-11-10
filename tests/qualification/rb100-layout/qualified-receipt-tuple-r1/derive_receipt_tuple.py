"""Pure current-Source receipt-tuple transformation; no I/O or producer."""
import hashlib

SOURCE_CAP = 4 * 1024 * 1024
METADATA_BYTES = 9970
METADATA_SHA = "a98ea9290f8fcdb86be4377698acbe012292972c56016383a9f2684bd40b9c7c"
FRAGMENT_BYTES = 2443
FRAGMENT_SHA = "ad5cb569f418f16cf82470d8c7b93d77da03c7f7d0e9f32c3057500be73db76b"
CALL_ANCHOR = b"        Map<?,?> prior = priorShape(meter, priorValue);\n"
CALL = b"        validateReceiptTuple(meter, prior, expectedBytes, expectedSha);\n"
HELPER_ANCHOR = b"    /** Same 6 + 5n ordered-shape census as the validated input helper. */\n"


class SourceError(ValueError):
    """Stable Source admission failure."""


def checked(raw, size, pin, code):
    if type(raw) is not bytes or len(raw) != size or len(raw) > SOURCE_CAP:
        raise SourceError(code)
    if hashlib.sha256(raw).hexdigest() != pin:
        raise SourceError(code)
    return raw


def derive(metadata, fragment):
    metadata = checked(metadata, METADATA_BYTES, METADATA_SHA, "METADATA_SOURCE")
    fragment = checked(fragment, FRAGMENT_BYTES, FRAGMENT_SHA, "FRAGMENT_SOURCE")
    if metadata.count(CALL_ANCHOR) != 1 or metadata.count(HELPER_ANCHOR) != 1:
        raise SourceError("METADATA_ANCHOR")
    if b"validateReceiptTuple" in metadata:
        raise SourceError("METADATA_ALREADY_DERIVED")
    result = metadata.replace(CALL_ANCHOR, CALL_ANCHOR + CALL, 1)
    result = result.replace(HELPER_ANCHOR, fragment + b"\n" + HELPER_ANCHOR, 1)
    if len(result) > SOURCE_CAP:
        raise SourceError("OUTPUT_CAP")
    return result
