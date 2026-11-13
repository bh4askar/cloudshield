$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $root
$settings=Join-Path $root '.env.azure'
if(Test-Path -LiteralPath $settings){
 foreach($line in Get-Content -LiteralPath $settings){
  if($line -match '^([A-Z][A-Z0-9_]+)=(.*)$'){[Environment]::SetEnvironmentVariable($Matches[1],$Matches[2],'Process')}
 }
}
if($env:CLOUDSHIELD_AZURE_CLI_DIR){$env:Path=$env:CLOUDSHIELD_AZURE_CLI_DIR+';'+$env:Path}
if(!$env:AZURE_PRIMARY_ACCOUNT){. "$PSScriptRoot/load-azure-environment.ps1"}
$env:CLOUDSHIELD_MODE='azure'
if(!$env:CLOUDSHIELD_PASSWORD){
 $secure=Read-Host 'Choose a unique administrator password (12+ characters)' -AsSecureString
 $ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
 try{$env:CLOUDSHIELD_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)}
}
$java=Join-Path $root 'runtime/windows/bin/java.exe'
if(!(Test-Path -LiteralPath $java)){if($env:JAVA_HOME){$java=Join-Path $env:JAVA_HOME 'bin/java.exe'}else{$java='java'}}
Write-Host 'CloudShield with Azure storage: http://localhost:8080'
Write-Host 'Keep this window open. Use Google sign-in or your administrator password.'
& $java -Xms128m -Xmx512m -jar "$root/web/target/cloudshield-web-1.0.0.jar"
exit $LASTEXITCODE
