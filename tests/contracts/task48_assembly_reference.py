#!/usr/bin/env python3
"""Independent literal Task48 decisions, checked against Java and GWT receipts."""
import importlib.util
import json
import sys
from pathlib import Path

sys.dont_write_bytecode = True
SEEDS = [0, 1, 2, 3, -(1 << 63), (1 << 63) - 1, -9007199254740993, 9007199254740993]
CONSTRAINTS = ('tsj-constraints/1;blocks=~;components=~;diagnostic-depth=~;domains=~;'
               'input-transitions=~;instruments=~;isolation-actions=~;parallel-ambiguity=~;'
               'plausible-owners=~;purposeful-auxiliaries=~;temporal-evidence=~;temporal-samples=~')


def reference():
    path = Path(__file__).with_name('task46_seed_reference.py')
    spec = importlib.util.spec_from_file_location('task46_reference', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def expected_fault(seed):
    ref = reference()
    derived = ref.fnv_seed(seed, 'controlled-indicator', 1, 'device', '-', 'fault', 1, 'selected-fault')
    return ['driver-rg-open', 'load-rload-open'][ref.next_int(ref.splitmix(derived), 2)]


def expected_descriptor(seed):
    return ('tsj-challenge/1\nconstraints=' + CONSTRAINTS +
            '\ndevice-intent=controlled-indicator@1\ndifficulty-profile=controlled-indicator@1'
            '\ngenerator=bounded-assembler@2\ngeometry=3\nroot-seed=' + str(seed))


def verify(path):
    content = Path(path).read_text(encoding='utf-8-sig')
    rows = []
    if content.lstrip().startswith('{'):
        report = json.loads(content)
        assert report.get('protocol') == 'TSJ-TASK48-1' and report.get('status') == 'PASS', 'runtime terminal is not PASS'
        for case in report['cases']:
            assert isinstance(case['seed'], str), 'runtime transported seed as a Number'
            physical_seed = case['physicalCorrespondence']['manifest']['seed']
            assert isinstance(physical_seed, str) and physical_seed == case['seed'], 'physical evidence lost canonical seed text'
            rows.append((case['seed'], case['faultDecision'], case['canonical']))
    else:
        for line in content.splitlines():
            if not line:
                continue
            seed, fault, descriptor = line.split(';', 2)
            assert seed.startswith('seed=') and fault.startswith('fault=') and descriptor.startswith('descriptor='), 'bad receipt fields'
            rows.append((seed[5:], fault[6:], descriptor[11:].replace('|', '\n')))
    assert len(rows) == len(SEEDS), 'receipt does not contain exactly eight rows'
    assert {int(seed) for seed, _, _ in rows} == set(SEEDS), 'receipt seed set changed'
    observed = set()
    for seed_text, fault, descriptor in rows:
        seed = int(seed_text)
        assert seed_text == str(seed), 'noncanonical signed seed'
        assert fault == expected_fault(seed), 'independent fault decision differs for ' + seed_text
        assert descriptor == expected_descriptor(seed), 'canonical descriptor differs for ' + seed_text
        observed.add(fault)
    assert observed == {'driver-rg-open', 'load-rload-open'}, 'seed corpus lacks a block owner'
    print('PASS: Task48 independent assembly oracle 8 seeds; two physical fault decisions')


if __name__ == '__main__':
    try:
        if len(sys.argv) != 2:
            raise AssertionError('usage: task48_assembly_reference.py RECEIPT')
        verify(sys.argv[1])
    except (AssertionError, OSError, ValueError, KeyError, TypeError) as error:
        print('FAIL: Task48 independent assembly oracle: ' + str(error))
        sys.exit(1)
