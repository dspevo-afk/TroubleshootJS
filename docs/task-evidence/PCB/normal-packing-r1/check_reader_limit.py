"""Pure independent type-bound canaries plus the unchanged original corpus."""
import hashlib
import json
from pathlib import Path

import qualified_reader as qualified
import check_normal_assessment as checks

HERE = Path(__file__).resolve().parent
original = qualified.original


def main():
    original_rejected = False
    try:
        qualified._original_counts({'generationRoutingExpansions': 1000001},
                                   ('generationRoutingExpansions',))
    except original.ReaderError:
        original_rejected = True
    assert original_rejected
    cases = []
    for value in (0, 1000000, 1000001, 2147483647):
        qualified.validate_count_fields({'generationRoutingExpansions': value},
                                        ('generationRoutingExpansions',))
        cases.append({'value': value, 'result': 'PASS'})
    for value in (-1, True, 1.0, '1000001', None, 2147483648):
        try:
            qualified.validate_count_fields({'generationRoutingExpansions': value},
                                            ('generationRoutingExpansions',))
        except original.ReaderError:
            cases.append({'value': value, 'result': 'REJECTED'})
        else:
            raise AssertionError('Invalid Java-int expansion counter accepted')
    try:
        qualified.validate_count_fields({'logicalParts': 1000001}, ('logicalParts',))
    except original.ReaderError:
        cases.append({'field': 'logicalParts', 'result': 'REJECTED'})
    else:
        raise AssertionError('Unrelated count bound changed')
    corpus = checks._run_canaries()
    assert corpus['canaryCount'] == 71 and corpus['negativeCount'] == 64
    result = {'status': 'PASS', 'scope': 'Offline aggregate int bound only; native unchanged',
              'originalRejectionReproduced': original_rejected,
              'originalCanaryCount': 71, 'additionalCanaryCount': len(cases),
              'cases': cases}
    dest = HERE / 'reader-limit-canaries.json'
    assert not dest.exists()
    dest.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    pins = ('assess_normal.py', 'check_normal_assessment.py', 'qualified_reader.py',
            'check_reader_limit.py', 'reader-limit-canaries.json')
    qualification = {'status': 'PASS', 'nativeRerunRequired': False,
                     'justification': 'PcbBoardLayout.getGenerationRoutingExpansions and '
                     'SeededPcbLayoutGenerator.GenerationStatistics.expansions are int; '
                     'the original generic one-million bound is not a product limit.',
                     'files': {name: hashlib.sha256((HERE / name).read_bytes()).hexdigest()
                               for name in pins}}
    seal = HERE / 'reader-qualification.json'
    assert not seal.exists()
    seal.write_text(json.dumps(qualification, indent=2) + '\n', encoding='utf-8')
    print('PASS reader qualification: unchanged71 plus11 independent type-bound canaries')


if __name__ == '__main__':
    main()
