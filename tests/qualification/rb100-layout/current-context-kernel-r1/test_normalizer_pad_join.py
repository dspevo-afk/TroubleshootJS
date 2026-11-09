"""Synthetic independent physical/P07 joins, never a real PAD identity oracle."""
import importlib.util
from pathlib import Path
import sys
import unittest

spec = importlib.util.spec_from_file_location(
    '_synthetic_pad_normalizer', Path(__file__).with_name('normalize_prior.py'))
n = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = n
spec.loader.exec_module(n)


def fixture():
    lands, pads = [], []
    for i in range(246):
        pad, net, x, y = 'P%03d.1' % i, 'N%02d' % (i % 60), i * 3, i * 2
        lands.append(dict(padId=pad, netId=net, x=x, y=y,
                          bounds=[x - 1, y - 1, 2, 2],
                          attachment='PLATED_THROUGH_HOLE', layer='TOP+BOTTOM'))
        pads.append(dict(record='PAD', group=i // 10, padId=pad, netId=net,
                         componentId='C%03d' % i, x=x, y=y, role='SIGNAL'))
    return lands, pads


class PadJoinTests(unittest.TestCase):
    def reject(self, mutate, code):
        lands, pads = fixture()
        mutate(lands, pads)
        with self.assertRaises(n.PriorNormalizationError) as caught:
            n._validate_pad_lands(lands, pads)
        self.assertEqual(caught.exception.code, code)

    def test_independent_246_pad_60_net_positive(self):
        lands, pads = fixture()
        result = n._validate_pad_lands(lands, pads)
        self.assertEqual(len(result['byId']), 246)
        self.assertEqual(len(result['roleByNet']), 60)
        self.assertEqual(result['byId']['P245.1']['x'], 735)
        self.assertEqual(result['p07ById']['P245.1']['y'], 490)

    def test_coordinate_cross_join_rejects(self):
        self.reject(lambda lands, pads: pads[0].update(x=1), 'PAD_LAND_P07_BINDING')

    def test_net_cross_join_rejects(self):
        self.reject(lambda lands, pads: pads[0].update(netId='WRONG'), 'PAD_LAND_P07_BINDING')

    def test_land_must_contain_center(self):
        self.reject(lambda lands, pads: lands[0].update(bounds=[10, 10, 2, 2]),
                    'PAD_LAND_BOUNDS_BINDING')

    def test_through_hole_face_contract_rejects(self):
        self.reject(lambda lands, pads: lands[0].update(layer='TOP'), 'PAD_LAND_FIELDS')

    def test_duplicate_p07_identity_rejects(self):
        self.reject(lambda lands, pads: pads[1].update(padId=pads[0]['padId']),
                    'P07_PAD_ORDER_OR_DUPLICATE')

    def test_p07_group_order_rejects(self):
        self.reject(lambda lands, pads: pads[0].update(group=1), 'P07_PAD_ORDER_OR_DUPLICATE')

    def test_one_net_cannot_have_two_roles(self):
        self.reject(lambda lands, pads: pads[60].update(role='POWER'), 'P07_NET_ROLE_INCONSISTENT')

    def test_boolean_coordinate_rejects(self):
        self.reject(lambda lands, pads: lands[0].update(x=False), 'PAD_LAND_FIELDS')


if __name__ == '__main__':
    unittest.main()
