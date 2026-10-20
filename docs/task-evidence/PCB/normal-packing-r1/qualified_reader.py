"""Qualify only the aggregate Java-int expansion counter's offline bound."""
import hashlib
import json
import sys
from pathlib import Path

import assess_normal as original

HERE = Path(__file__).resolve().parent
JAVA_INT_MAX = 2147483647
_original_counts = original._validate_count_fields
_original_common = original._common_evidence


def validate_count_fields(row, fields):
    others = tuple(field for field in fields if field != 'generationRoutingExpansions')
    _original_counts(row, others)
    if 'generationRoutingExpansions' in fields:
        original._bounded_int(row['generationRoutingExpansions'],
                              'generationRoutingExpansions', 0, JAVA_INT_MAX)


def common_evidence(*args, **kwargs):
    result = _original_common(*args, **kwargs)
    result['readerQualification'] = {
        'scope': 'Only generationRoutingExpansions bound: nonnegative Java int',
        'qualifiedReaderSha256': original._sha(Path(__file__).read_bytes()),
        'originalReaderSha256': original._sha((HERE / 'assess_normal.py').read_bytes()),
        'nativeLimitsChanged': False,
    }
    return result


original._validate_count_fields = validate_count_fields
original._common_evidence = common_evidence


def audit_qualification():
    qualification = original._parse_json(
        (HERE / 'reader-qualification.json').read_bytes(), 'reader qualification')
    assert qualification['status'] == 'PASS'
    for name, digest in qualification['files'].items():
        assert hashlib.sha256((HERE / name).read_bytes()).hexdigest() == digest, name


if __name__ == '__main__':
    audit_qualification()
    raise SystemExit(original.main())
