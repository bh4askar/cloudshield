"""Create the worker deployment archive, excluding local settings and secrets."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
root=Path(__file__).resolve().parents[1]
source=root/'worker/target/azure-functions/cloudshield-worker'
assert (source/'host.json').is_file(), 'Run mvn clean verify first'
target=root/'worker/target/cloudshield-function.zip'
with ZipFile(target,'w',ZIP_DEFLATED) as z:
    for p in source.rglob('*'):
        if p.is_file() and p.name!='local.settings.json':z.write(p,p.relative_to(source))
print(target)
