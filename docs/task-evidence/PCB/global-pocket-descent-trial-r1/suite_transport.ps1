function Get-GlobalPocketSuiteNames {
    [CmdletBinding()]
    param([Parameter(Mandatory=$true)][string]$JsonText)
    $rows=ConvertFrom-Json -InputObject $JsonText -NoEnumerate -ErrorAction Stop
    if($rows -isnot [System.Array] -or $rows.Count -eq 0){throw 'Suite manifest must be a nonempty array of pairs'}
    $names=[Collections.Generic.List[string]]::new()
    $markers=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach($row in $rows){
        if($row -isnot [System.Array] -or $row.Count -ne 2){throw 'Suite entry must remain a two-value array'}
        if($row[0] -isnot [string] -or $row[0] -cnotmatch '\A[A-Za-z_$][A-Za-z0-9_$]*\z'){
            throw 'Suite name must be a canonical class name'
        }
        if($row[1] -isnot [string] -or $row[1].Length -eq 0 -or $row[1].Contains("`n") -or $row[1].Contains("`r")){
            throw 'Suite marker must be bounded single-line text'
        }
        if($names.Contains($row[0]) -or -not $markers.Add($row[1])){throw 'Suite names and markers must be unique'}
        $names.Add($row[0])
    }
    $names.ToArray()
}
