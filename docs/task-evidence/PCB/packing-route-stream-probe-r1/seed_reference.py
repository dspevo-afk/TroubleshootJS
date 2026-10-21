"""Independent length-prefixed ASCII FNV64 reference for the private stream."""

import json


MASK = (1 << 64) - 1
FNV_OFFSET_BASIS = 0xcbf29ce484222325
FNV_PRIME = 0x100000001b3
DERIVATION_VERSION = 1
DEVICE_INTENT = "pcb-packing-route"
DEVICE_INTENT_VERSION = 1
CONCERN = "routing"
CONCERN_REVISION = 1
SEMANTIC_KEY = "optional-p05-proposal"
PREDECLARED_SOURCE_SEEDS = (
    0,
    1,
    -1,
    -(1 << 63),
    (1 << 63) - 1,
)
ACTUAL_SOURCE_SEED = 3078526323410797175


def _signed_long(value: int) -> int:
    value &= MASK
    return value - (1 << 64) if value >= (1 << 63) else value


def _hash_bytes(hash_value: int, data: bytes) -> int:
    for byte in data:
        hash_value = ((hash_value ^ byte) * FNV_PRIME) & MASK
    return hash_value


def _hash_field(hash_value: int, field: str) -> int:
    encoded = field.encode("ascii")
    return _hash_bytes(hash_value, str(len(encoded)).encode("ascii") + b":" + encoded)


def derive_seed(source_seed: int) -> int:
    """Derive the signed Java-long stream seed for one source seed."""
    if isinstance(source_seed, bool) or not isinstance(source_seed, int):
        raise TypeError("source_seed must be an integer")
    if source_seed < -(1 << 63) or source_seed > (1 << 63) - 1:
        raise ValueError("source_seed is outside the signed Java-long range")
    fields = (
        "tsj-named-seed",
        str(DERIVATION_VERSION),
        str(source_seed),
        DEVICE_INTENT,
        str(DEVICE_INTENT_VERSION),
        "device",
        "-",
        CONCERN,
        str(CONCERN_REVISION),
        SEMANTIC_KEY,
    )
    result = FNV_OFFSET_BASIS
    for field in fields:
        result = _hash_field(result, field)
    return _signed_long(result)


def vector_payload() -> dict:
    roots = PREDECLARED_SOURCE_SEEDS + (ACTUAL_SOURCE_SEED,)
    vectors = [
        {"sourceSeed": str(root), "derivedSeed": str(derive_seed(root))}
        for root in roots
    ]
    return {
        "derivationVersion": DERIVATION_VERSION,
        "root": "session.routingSeed",
        "intent": DEVICE_INTENT,
        "intentVersion": DEVICE_INTENT_VERSION,
        "scope": "device",
        "blockKey": "-",
        "concern": CONCERN,
        "concernRevision": CONCERN_REVISION,
        "semanticKey": SEMANTIC_KEY,
        "vectors": vectors,
        "actualSourceSeed": str(ACTUAL_SOURCE_SEED),
        "actualDerivedSeed": str(derive_seed(ACTUAL_SOURCE_SEED)),
    }


if __name__ == "__main__":
    print(json.dumps(vector_payload(), indent=2, sort_keys=True))
