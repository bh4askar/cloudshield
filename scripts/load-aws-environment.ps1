$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $projectRoot 'infra')
try {
    $raw = & terraform output -json dashboard_environment
    if ($LASTEXITCODE -ne 0) { throw 'Terraform outputs are unavailable. Apply the infrastructure first.' }
    $values = $raw | ConvertFrom-Json
    foreach ($property in $values.PSObject.Properties) {
        [Environment]::SetEnvironmentVariable($property.Name, [string]$property.Value, 'Process')
    }
    Write-Host 'AWS environment loaded. Set CLOUDSHIELD_PASSWORD and AWS_PROFILE, then start the web JAR directly.'
} finally { Pop-Location }
