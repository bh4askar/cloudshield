param([string]$ResourceGroup='cloudshield-g360',[string]$PrimaryRegion='centralindia',[string]$SecondaryRegion='koreacentral',[Parameter(Mandatory=$true)][string]$BudgetEmail)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$operator=az ad signed-in-user show --query id -o tsv
if($LASTEXITCODE -ne 0){throw 'Run az login using your Azure for Students account.'}
az group create -n $ResourceGroup -l $PrimaryRegion --tags project=CloudShield environment=student -o none
if($LASTEXITCODE -ne 0){throw 'Resource group creation failed.'}
$start=Get-Date -Day 1 -Format 'yyyy-MM-ddT00:00:00Z'
az deployment group create -g $ResourceGroup -n cloudshield-azure --template-file "$root/infra/azure/main.json" --parameters "operatorObjectId=$operator" "budgetEmail=$BudgetEmail" "budgetStartDate=$start" "primaryRegion=$PrimaryRegion" "secondaryRegion=$SecondaryRegion" -o none
if($LASTEXITCODE -ne 0){throw 'Azure resource deployment failed.'}
& python "$PSScriptRoot/package-function.py"
if($LASTEXITCODE -ne 0){throw 'Build the project using mvn clean verify first.'}
$app=az deployment group show -g $ResourceGroup -n cloudshield-azure --query properties.outputs.function.value -o tsv
az functionapp deployment source config-zip -g $ResourceGroup -n $app --src "$root/worker/target/cloudshield-function.zip" --build-remote false
if($LASTEXITCODE -ne 0){throw 'Function package deployment failed.'}
Write-Host 'Worker deployed; schedules remain disabled until acceptance tests pass. See docs/AZURE_SETUP.md.'
