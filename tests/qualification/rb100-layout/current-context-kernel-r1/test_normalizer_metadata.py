"""Independent synthetic metadata cases; no real role or referenced file reads."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import unittest

spec = importlib.util.spec_from_file_location(
    '_synthetic_metadata_normalizer', Path(__file__).with_name('normalize_prior.py'))
n = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = n
spec.loader.exec_module(n)


def encode(value):
    return json.dumps(value, separators=(',', ':'), allow_nan=False).encode('ascii') + b'\n'


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def fixture():
    source = dict.fromkeys(n.N_SOURCE_KEYS, 'SYNTHETIC_METADATA_ONLY')
    source.update(schema='tsj-failure-cohort-source-input-v1',
                  sourceParent='474a8354ceb2e50fa2adbb31e23cf73a0d9c86fd',
                  compilerSourceCount=1090,
                  sources=[dict(path='C:/synthetic/source/%04d.java' % i,
                                bytes=1, sha256='a' * 64) for i in range(1090)],
                  tools=[dict(path='C:/synthetic/tool/%d' % i, bytes=1,
                              sha256='b' * 64) for i in range(4)],
                  wire=dict(sha256='c7bf55d15f8166e7d8c767abd3f068ee88087b6a9132f9331af7d21c5c202362'))
    source_raw = encode(source)
    entries = [dict(path='p/Class%04d.class' % i,
                    bytes=(19289113 - 4138 if i == 0 else 1),
                    sha256='c' * 64) for i in range(4139)]
    inventory = dict(schema='tsj-failure-cohort-classes-v1',
                     sourceManifestSha256=sha(source_raw), classCount=4139,
                     totalBytes=19289113, entries=entries)
    inventory_raw = encode(inventory)
    run = copy.deepcopy(source)
    run['schema'] = 'tsj-failure-cohort-source-v1'
    run['build'] = dict.fromkeys(n.N_BUILD_KEYS, 'SYNTHETIC_METADATA_ONLY')
    run['build'].update(sourceManifestSha256=sha(source_raw),
                        classInventorySha256=sha(inventory_raw),
                        classCount=4139, classBytes=19289113)
    return source, inventory, run


def pack(source, inventory, run):
    # Refresh dependent hashes to isolate the semantic assertion being tested.
    source_raw = encode(source)
    inventory['sourceManifestSha256'] = sha(source_raw)
    inventory_raw = encode(inventory)
    run['build']['sourceManifestSha256'] = sha(source_raw)
    run['build']['classInventorySha256'] = sha(inventory_raw)
    bodies = dict(N_PRIOR_SOURCE_MANIFEST=source_raw,
                  N_PRIOR_CLASS_INVENTORY=inventory_raw,
                  N_PRIOR_RUN_MANIFEST=encode(run))
    pins = {role: dict(sha256=sha(raw)) for role, raw in bodies.items()}
    return bodies, pins


class MetadataTests(unittest.TestCase):
    def reject(self, documents, expected):
        with self.assertRaises(n.PriorNormalizationError) as caught:
            n._validate_prior_metadata(*pack(*documents))
        self.assertEqual(caught.exception.code, expected)

    def test_complete_synthetic_metadata_positive(self):
        documents = fixture()
        result = n._validate_prior_metadata(*pack(*documents))
        self.assertEqual(result, (documents[2], documents[0], documents[1]))
        self.assertEqual(len(result[1]['sources']), 1090)
        self.assertEqual(sum(row['bytes'] for row in result[2]['entries']), 19289113)

    def test_source_alias_case_and_separator_duplicate_rejected(self):
        source, inventory, run = fixture()
        source['sources'][1]['path'] = source['sources'][0]['path'].upper().replace('/', '\\')
        run['sources'] = copy.deepcopy(source['sources'])
        self.reject((source, inventory, run), 'PRIOR_SOURCE_ROW_DUPLICATE')

    def test_run_source_mismatch_rejected(self):
        source, inventory, run = fixture()
        run['sources'][0]['sha256'] = 'd' * 64
        self.reject((source, inventory, run), 'PRIOR_RUN_SOURCE_BINDING')

    def test_class_order_rejected(self):
        source, inventory, run = fixture()
        inventory['entries'][0:2] = reversed(inventory['entries'][0:2])
        self.reject((source, inventory, run), 'PRIOR_CLASS_ENTRY_ORDER')

    def test_class_path_escape_rejected(self):
        source, inventory, run = fixture()
        inventory['entries'][0]['path'] = '../escape.class'
        self.reject((source, inventory, run), 'PRIOR_CLASS_ENTRY_PATH')

    def test_class_total_not_trusted(self):
        source, inventory, run = fixture()
        inventory['entries'][0]['bytes'] -= 1
        self.reject((source, inventory, run), 'PRIOR_CLASS_TOTAL_BYTES')

    def test_boolean_entry_size_rejected(self):
        source, inventory, run = fixture()
        inventory['entries'][0]['bytes'] = True
        self.reject((source, inventory, run), 'PRIOR_CLASS_ENTRY_FIELDS')

    def test_extra_source_field_rejected(self):
        source, inventory, run = fixture()
        source['followThisPath'] = 'C:/synthetic/DO_NOT_READ'
        self.reject((source, inventory, run), 'PRIOR_SOURCE_MANIFEST_KEYS')

    def test_role_sha_rechecked_at_metadata_boundary(self):
        bodies, pins = pack(*fixture())
        pins['N_PRIOR_RUN_MANIFEST']['sha256'] = 'd' * 64
        with self.assertRaises(n.PriorNormalizationError) as caught:
            n._validate_prior_metadata(bodies, pins)
        self.assertEqual(caught.exception.code, 'PRIOR_METADATA_ROLE_BINDING')


if __name__ == '__main__':
    unittest.main()
