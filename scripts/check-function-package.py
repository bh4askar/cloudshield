"""Check the deployable Java Function artifact and its trigger bindings."""
import json
from pathlib import Path
base=Path(__file__).resolve().parents[1]/'worker/target/azure-functions/cloudshield-worker'
assert (base/'host.json').is_file()
for name,kind in [('RunJob','queueTrigger'),('PoisonJob','queueTrigger'),('DailyBackup','timerTrigger'),('WeeklyRecoveryTest','timerTrigger')]:
 data=json.loads((base/name/'function.json').read_text())
 assert any(b['type']==kind for b in data['bindings']),name
 script=(base/name/data['scriptFile']).resolve()
 assert script.is_file(),script
 assert data['entryPoint'].startswith('edu.gla.cloudshield.worker.Functions.')
for dependency in ('azure-identity','azure-storage-blob','azure-data-tables','azure-storage-queue'):
 assert list((base/'lib').glob(f'{dependency}-*.jar')),f'Missing Azure dependency: {dependency}'
print('Azure function package: all four triggers and dependencies verified')
