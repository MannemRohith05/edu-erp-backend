$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$lastStamp = ''
Write-Host 'EduERP backend compiler watcher started.' -ForegroundColor Cyan
while ($true) {
    $latest = Get-ChildItem -Path (Join-Path $PSScriptRoot 'src') -Recurse -File |
        Where-Object { $_.Extension -in '.java', '.properties', '.yml', '.yaml', '.xml', '.sql' } |
        Sort-Object LastWriteTimeUtc |
        Select-Object -Last 1
    $stamp = if ($latest) { $latest.LastWriteTimeUtc.Ticks.ToString() } else { 'empty' }
    if ($stamp -ne $lastStamp) {
        Write-Host "Compiling backend changes: $($latest.FullName)" -ForegroundColor DarkCyan
        & (Join-Path $PSScriptRoot 'mvnw.cmd') -q -DskipTests compile
        if ($LASTEXITCODE -eq 0) {
            $lastStamp = $stamp
            Write-Host 'Backend classes/resources are up to date; DevTools will restart the app.' -ForegroundColor Green
        } else {
            Write-Warning 'Backend compilation failed. The application was not replaced; fix the compiler error and save again.'
            Start-Sleep -Seconds 2
        }
    }
    Start-Sleep -Milliseconds 750
}
