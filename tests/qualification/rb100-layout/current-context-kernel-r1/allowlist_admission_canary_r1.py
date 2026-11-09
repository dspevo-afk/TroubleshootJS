"""Explicit metadata-only canary. Never reads any path found in the metadata."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

assert sys.flags.isolated and sys.dont_write_bytecode
parser = argparse.ArgumentParser()
parser.add_argument('--normalizer-source', required=True)
parser.add_argument('--normalizer-sha256', required=True)
parser.add_argument('--allowlist', required=True)
args = parser.parse_args()
source = Path(args.normalizer_source)
raw_source = source.read_bytes()
assert len(raw_source) <= 4194304
assert hashlib.sha256(raw_source).hexdigest() == args.normalizer_sha256
metadata = Path(args.allowlist).read_bytes()
assert len(metadata) <= 262144
assert hashlib.sha256(metadata).hexdigest() == 'c487eae6ed0e070aaad75dccd14d23313ed9da2c6bda7f93bfeb886109f20f91'
spec = importlib.util.spec_from_file_location('_metadata_only_current_normalizer', source)
normalizer = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = normalizer
spec.loader.exec_module(normalizer)
try:
    value, pins = normalizer._validate_allowlist_bytes(metadata)
    assert len(pins) == value['roleCount'] == 20
    kernels = {name: (lambda *a, **k: None) for name in normalizer.KERNEL_CALLBACK_KEYS}
    codecs = {name: (lambda *a, **k: None) for name in normalizer.CODEC_CALLBACK_KEYS}
    try:
        normalizer.normalize_prior({}, metadata, {}, kernels, codecs)
    except normalizer.PriorNormalizationError as failure:
        assert failure.code == 'ROLE_BYTE_MAP'
    else:
        raise AssertionError('EMPTY_ROLE_MAP_ADMITTED')
except normalizer.PriorNormalizationError as failure:
    print(failure.code, file=sys.stderr)
    raise SystemExit(2)
print(json.dumps(dict(status='PASS_ACTUAL_PINNED_METADATA_ONLY', roleCount=20,
    actualPriorBodiesRead=0, publicEntryStopsAt='ROLE_BYTE_MAP', normalPriorAccepted=False),
    separators=(',', ':')))
