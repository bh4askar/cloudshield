param([string]$ResourceGroup='cloudshield-g360',[string]$Deployment='cloudshield-azure')
$ErrorActionPreference='Stop'
$result=az deployment group show --resource-group $ResourceGroup --name $Deployment --query properties.outputs -o json | ConvertFrom-Json
if($LASTEXITCODE -ne 0){throw 'Sign into Azure CLI with az login, then retry.'}
$env:CLOUDSHIELD_MODE='azure'
$env:AZURE_PRIMARY_ACCOUNT=$result.primary.value
$env:AZURE_SECONDARY_ACCOUNT=$result.secondary.value
$env:AZURE_RECOVERY_ACCOUNT=$result.recovery.value
$env:AZURE_PRIMARY_REGION=(az storage account show -g $ResourceGroup -n $env:AZURE_PRIMARY_ACCOUNT --query location -o tsv)
$env:AZURE_SECONDARY_REGION=(az storage account show -g $ResourceGroup -n $env:AZURE_SECONDARY_ACCOUNT --query location -o tsv)
$env:MAX_ACCOUNTS='10'
$env:MAX_BACKUPS_PER_ACCOUNT='240'
Write-Host 'Azure storage configuration loaded. Set CLOUDSHIELD_PASSWORD before starting the dashboard.'
