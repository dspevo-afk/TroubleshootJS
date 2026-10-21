"""Add passive private catch observations; preserve every original throw/return and source owner."""
import argparse
import difflib
import hashlib
import json
import runpy
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
GENERATOR = 'src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--previous-adapter', required=True, type=Path)
    p.add_argument('--private-root', required=True, type=Path)
    a = p.parse_args()
    baseline = runpy.run_path(str(HERE / 'inputs.py'))
    assert baseline['record'](a.previous_adapter) == baseline['read'](HERE / 'baseline-inputs.json')
    assert a.private_root.is_dir() and not any(a.private_root.iterdir())
    changes = []

    def change(source, old, new, label):
        nl = '\r\n' if '\r\n' in source else '\n'
        old, new = old.replace('\n', nl), new.replace('\n', nl)
        assert source.count(old) == 1, label
        changes.append({'label': label, 'replacements': 1, 'oldSha256': sha(old.encode()), 'newSha256': sha(new.encode())})
        return source.replace(old, new)

    original = (ROOT / GENERATOR).read_bytes().decode('utf-8')
    generator = original
    # Scope to this exact method so identically named catches elsewhere remain untouched.
    start, end = generator.index('    private boolean advancePackingProbe(Session session) {'), generator.index('    private boolean finishPackingProbe(Session session, String disposition) {')
    probe = generator[start:end]
    cases = (
        ('CandidateRejected', 'rejectedRoute', 'rejectedRoute.getKind().name()', 'null', 'rejectedRoute.recovery'),
        ('PcbPlacementPlanner.Rejected', 'rejectedAccess', '"PLACEMENT"', 'rejectedAccess.reason', 'null'),
        ('SupportedEnvelope.Rejected', 'rejectedEnvelope', '"ENVELOPE"', 'rejectedEnvelope.reason.name()', 'null'),
        ('PcbBoardLayout.RouteQualityRejectedException', 'rejectedQuality', '"QUALITY"', 'null', 'null'),
    )
    for typename, name, kind, reason, recovery in cases:
        old = '        } catch (' + typename + ' ' + name + ') {\n'
        hook = ('            TaskPackingObservation.emit("PACKING_CAUGHT", "PACKING_EXPECTED_CATCH",\n'
                '                session.packingPlan, session.seed, session.routingSeed,\n'
                '                session.packingBaselineHash, session.packingRoutePolicy,\n'
                '                ' + name + ', ' + kind + ', ' + reason + ', null, ' + recovery + ');\n')
        probe = change(probe, old, old + hook, 'observe existing outer ' + typename + ' catch before unchanged return')
    generator = generator[:start] + probe + generator[end:]
    old = '        catch(PcbNetRouter.Rejected failure) {\n            throw new CandidateRejected(PcbRoutingRejectedException.Kind.ROUTING,\n'
    new = ('        catch(PcbNetRouter.Rejected failure) {\n'
           '            if (compactProbe) TaskPackingObservation.emit("COMPACT_ROUTER_REJECTED",\n'
           '                "P05_ROUTE_SEARCH", plan, seed, routingSeed, null,\n'
           '                MediumBoardPhysicalPolicy.P05_ONE_FACE, failure, "ROUTING",\n'
           '                failure.reason.name(), failure.blockedNet, failure.statistics);\n'
           '            throw new CandidateRejected(PcbRoutingRejectedException.Kind.ROUTING,\n')
    generator = change(generator, old, new, 'observe typed router failure before unchanged CandidateRejected wrap')
    assert len(changes) == 5
    generator_changes = changes[:]
    adapter_original = a.previous_adapter.read_bytes().decode('utf-8')
    adapter = change(adapter_original,
        '    [Parameter(Mandatory=$true)][string]$TaskExportSource,',
        '    [Parameter(Mandatory=$true)][string]$TaskExportSource,\n'
        '    [Parameter(Mandatory=$true)][ValidateScript({Test-Path -LiteralPath $_ -PathType Leaf})][string]$TaskGeneratorSource,',
        'bind existing private-generator source through real host file validation')
    adapter = change(adapter,
        "        Where-Object { $_.Name -ne 'PhysicalSpecificationDeveloperVerifier.java' -and",
        "        Where-Object { $_.Name -ne 'SeededPcbLayoutGenerator.java' -and $_.Name -ne 'PhysicalSpecificationDeveloperVerifier.java' -and",
        'exclude only checked-in generator from private native compile')
    adapter = change(adapter, '    $sourcePaths += $stub',
        '    $sourcePaths += $stub\n    $sourcePaths += (Resolve-Path -LiteralPath $TaskGeneratorSource).Path',
        'include sealed copied same-owner generator')
    exporter_original = (HERE.parent / 'random-composed-whitespace-r1/export-source/SparseNormalBoardDiagnostic.java').read_bytes()
    observer = (HERE / 'observer-source/TaskPackingObservation.java.inc').read_bytes()
    # The original exporter is an exact byte prefix, including all validation and cleanup.
    exporter = exporter_original + b'\n' + observer
    outputs = {'SeededPcbLayoutGenerator.java': generator.encode(),
               'verify-composed-rejection.ps1': adapter.encode(),
               'SparseNormalBoardDiagnostic.java': exporter}
    for name, data in outputs.items():
        (a.private_root / name).write_bytes(data)
    manifest = {'status': 'GENERATED_PASSIVE_PRIVATE_CATCH_OBSERVATIONS',
        'publishedGeneratorSha256': sha(original.encode()), 'previousAdapterSha256': sha(adapter_original.encode()),
        'publishedExporterSha256': sha(exporter_original), 'appendedObserverSha256': sha(observer),
        'privateInputs': {n: sha(b) for n, b in outputs.items()},
        'generatorChanges': generator_changes, 'adapterChanges': changes[5:],
        'limits': 'Five passive catch hooks only; all original throws/returns/budgets/order/validators remain. '
                  'Three already-proven private-generator substitution hooks. Exporter exact original prefix '
                  'plus task-only serializer. No production checkout edit, extra route or acceptance predicate.'}
    (HERE / 'private-inputs.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    for name, before, after in (('generator', original, generator), ('adapter', adapter_original, adapter)):
        diff = ''.join(difflib.unified_diff(before.splitlines(True), after.splitlines(True),
                    fromfile='published-' + name, tofile='private-observed-' + name))
        (HERE / (name + '-diff.txt')).write_text(diff, encoding='utf-8', newline='\n')
    print('PASS prepared five passive catch hooks, three private-source bindings, exact exporter prefix')


if __name__ == '__main__':
    main()
