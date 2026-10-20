"""Generate a private, pinned verifier adapter for one task-owned normal-board observation class."""
import argparse,hashlib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
SOURCE_SHA="b87cd0569c5a3f263aa08d9fb5ed07d201f2c37e2934be2c393015ab03c129d3"
def sha(b):return hashlib.sha256(b).hexdigest()
def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--output",required=True,type=Path)
    parser.add_argument("--manifest",required=True,type=Path)
    args=parser.parse_args()
    assert not args.output.exists() and not args.manifest.exists(),"Preserve generated adapter/manifest"
    raw=(ROOT/"scripts/verify-current-contracts.ps1").read_bytes()
    assert sha(raw)==SOURCE_SHA
    source=raw.decode("utf-8")
    nl="\r\n" if b"\r\n" in raw else "\n"
    changes=[]
    def replace(label,old,new):
        nonlocal source
        old=old.replace("\n",nl);new=new.replace("\n",nl)
        assert source.count(old)==1,label
        source=source.replace(old,new)
        changes.append({"label":label,"replacements":1,"oldSha256":sha(old.encode()),"newSha256":sha(new.encode())})
    replace("explicit task inputs and canonical signed-long validation",'param(\n    [string]$JavaHome',
        '''param(
    [Parameter(Mandatory=$true)][string]$TaskRepositoryRoot,
    [Parameter(Mandatory=$true)][string]$TaskExportSource,
    [Parameter(Mandatory=$true)][ValidateSet('RC_DELAY','COMPOSED_CONTROLLED_INDICATOR')][string]$TaskExportFamily,
    [Parameter(Mandatory=$true)][ValidateScript({
        $taskSeedValue=[long]::Parse($_,[Globalization.CultureInfo]::InvariantCulture)
        $taskSeedValue.ToString([Globalization.CultureInfo]::InvariantCulture) -ceq $_
    })][string]$TaskExportSeed,
    [string]$JavaHome''')
    replace("explicit repository and maintained process-module owner",
        "    Import-Module (Join-Path $PSScriptRoot 'VerifierIsolation.psm1') -Force\n    $repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))",
        "    $repositoryRoot = [IO.Path]::GetFullPath($TaskRepositoryRoot)\n    Import-Module (Join-Path $repositoryRoot 'scripts/VerifierIsolation.psm1') -Force")
    replace("add one task-owned class/marker without changing maintained definitions",
        "    $testDefinitions = @(\n",
        "    $testDefinitions = @(\n        @{ Name = 'SparseNormalBoardDiagnostic'; Marker = 'sparse normal board diagnostic ' },\n")
    replace("resolve only task-owned class from frozen external source",
        "        $testSource = Join-Path $repositoryRoot ('tests/contracts/' + $testClass + '.java')",
        "        $testSource = if ($testClass -ceq 'SparseNormalBoardDiagnostic') {\n            (Resolve-Path -LiteralPath $TaskExportSource).Path\n        } else { Join-Path $repositoryRoot ('tests/contracts/' + $testClass + '.java') }")
    replace("pass exact seed only to selected task class",
        "        $testArguments = @('-ea', '-cp', $classPath,\n            ('com.lushprojects.circuitjs1.client.' + $testClass))",
        "        $testArguments = @('-ea', '-cp', $classPath,\n            ('com.lushprojects.circuitjs1.client.' + $testClass))\n        if ($testClass -ceq 'SparseNormalBoardDiagnostic') {\n            $testArguments += @('--family', $TaskExportFamily, '--seed', $TaskExportSeed)\n        }")
    assert len(changes)==5 and "$PSScriptRoot" not in source
    generated=source.encode("utf-8")
    args.output.write_bytes(generated)
    args.manifest.write_text(json.dumps({"status":"GENERATED_PRIVATE_TASK_ADAPTER","maintainedSourceSha256":sha(raw),"generatedSha256":sha(generated),"generatedBytes":len(generated),"changes":changes,"limits":"Private generated adapter, not an unchanged maintained verifier invocation. Existing JDK8/compiler/native jobs/budgets/cleanup and all maintained class definitions remain; only one task-owned class registration/source/family+seed hook and explicit paths added. No production/script/test checkout edits"},indent=2)+"\n",encoding="utf-8")
    print("PASS generated private task adapter;5 exact guarded replacements")
if __name__=="__main__":main()
