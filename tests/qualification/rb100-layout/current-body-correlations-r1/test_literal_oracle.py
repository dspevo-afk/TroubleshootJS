"""Root's independent Python expectation for the Java literal oracle.

No Java/helper/producer execution. These are ordinary synthetic metadata.
"""
import hashlib
import json
from pathlib import Path
import re
import sys
import unittest

assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R = Path(__file__).resolve().parent
WIRE = '1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed'


def expected():
    return dict(schema='tsj-cohort-terminal-result-v2',
                mode='OBSERVED_COHORT_TERMINAL_TELEMETRY',
                status='COMPLETED_DIAGNOSTIC_ONLY',
                runId='00000000-0000-0000-0000-000000000001',
                sourceManifestSha256='1' * 64, wireContractSha256=WIRE,
                inputContextSha256='3' * 64,
                qualifiedPrior=dict(inputContextSha256='3' * 64),
                originalCapture=dict(contextRestored=True, ownersReleased=True, template=dict(geometryBeforeSha256='4' * 64, geometryAfterSha256='4' * 64, effectsBeforeSha256='6' * 64, effectsAfterSha256='6' * 64)), cohort={},
                arms={'null': dict(armRole='NULL',
                                   cleanup=dict(contextRestored=True, adapterSessionAliasDropped=True, adapterSearchAliasDropped=True, adapterCandidateAliasDropped=True, adapterRouteResultAliasDropped=True, adapterBoardTemplateAliasesDropped=True, sourceGeometryBeforeSha256='4' * 64, sourceGeometryAfterSha256='4' * 64)),
                      'observe': dict(armRole='OBSERVE',
                                      cleanup=dict(contextRestored=True, adapterSessionAliasDropped=True, adapterSearchAliasDropped=True, adapterCandidateAliasDropped=True, adapterRouteResultAliasDropped=True, adapterBoardTemplateAliasesDropped=True, sourceGeometryBeforeSha256='4' * 64, sourceGeometryAfterSha256='4' * 64))},
                work=dict(cleanupNanos=0, ordinaryList=['synthetic']),
                cleanup=dict(schema='tsj-cohort-terminal-cleanup-v1', cleanupNanos=0,
                             originalCaptureContextRestored=True,
                             contextRestored=True,
                             originalCaptureOwnersReleased=True,
                             nullArmAliasesDropped=True,
                             observeArmAliasesDropped=True, sourceGeometryUnchanged=True, effectsScope='ORIGINAL_CAPTURE_COPY_ONLY', originalCaptureCopyEffectsUnchanged=True))


def canonical(value):
    return (json.dumps(value, ensure_ascii=True, separators=(',', ':')) + '\n').encode('ascii')


class LiteralOracleTests(unittest.TestCase):
    def test_literal_is_independently_expected_canonical_bytes(self):
        source = (R / 'BodyCorrelationOracle.java').read_text(encoding='ascii')
        found = re.findall(r'static final String MINIMAL_BODY_JSON\s*=\s*("(?:[^"\\]|\\.)*")\s*;', source)
        self.assertEqual(len(found), 1)
        literal = json.loads(found[0]).encode('ascii')
        self.assertEqual(literal, canonical(expected()))
        self.assertEqual(json.loads(literal), expected())

    def test_wide_fixture_crosses_two_blocks(self):
        body = expected()
        body['work']['cleanupNanos'] = body['cleanup']['cleanupNanos'] = 120000000000
        for index in range(27):
            body['work'][f'padding{index:02d}'] = 'x' * 120
        raw = canonical(body)
        self.assertEqual((len(raw) + 4095) // 4096, 2)
        self.assertEqual(len(body['work']), 29)

    def test_transport_literal_oracles_remain_independent(self):
        # These are accepted old oracle identities, not regenerated goldens.
        pins = {'CurrentV2CodecOracle.java': (6196, '644cf35bb681d75dd6c97c1f881f12383c53f8eb3fb4fcfbcc4bccd5d529d4ab'),
                'CurrentV2CodecWideOracle.java': (7326, 'ba26f08bb2e003a381fe73e19915f1ef1fb1d1b2cca6d4e5a64b3e0f861ef559')}
        for name, (size, pin) in pins.items():
            raw = (ORACLES / name).read_bytes()
            self.assertEqual(len(raw), size)
            self.assertEqual(hashlib.sha256(raw).hexdigest(), pin)


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('--retained-oracle-root', required=True, type=Path)
    args, remaining = parser.parse_known_args()
    ORACLES = args.retained_oracle_root
    unittest.main(argv=[sys.argv[0]] + remaining)
