#!/usr/bin/env python3
"""Exercise a running LOCAL CloudShield demo through HTTP. Creates synthetic data, backups and restores.
Python standard library only. Credentials come from CLOUDSHIELD_USERNAME/PASSWORD or demo defaults.
"""
import argparse
import http.cookiejar
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from datetime import datetime, timezone

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url',default='http://127.0.0.1:8080')
    parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    base=args.url.rstrip('/')
    session=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    checks=[]
    def get(path):
        with session.open(base+path,timeout=20) as response:
            return json.load(response)
    def check(label,condition):
        if not condition: raise AssertionError(label)
        checks.append(label)
    check('Public liveness endpoint',get('/health')['status']=='UP')
    check('Smoke suite targets local mode',get('/public/mode')['demo'])
    try:
        urllib.request.urlopen(base+'/api/overview',timeout=10)
        raise AssertionError('Anonymous API access allowed')
    except urllib.error.HTTPError as error:
        check('Anonymous API access rejected',error.code==401)
    with session.open(base+'/api/csrf',timeout=20) as response:
        cookies='; '.join(response.headers.get_all('Set-Cookie',[])).lower()
        check('OAuth callback session cookie is Lax and HTTP-only',
              'samesite=lax' in cookies and 'httponly' in cookies)
        csrf=json.load(response)
    login=urllib.parse.urlencode({'username':os.environ.get('CLOUDSHIELD_USERNAME','admin'),
        'password':os.environ.get('CLOUDSHIELD_PASSWORD','CloudShield-Demo-2026!'),csrf['parameterName']:csrf['token']}).encode()
    session.open(urllib.request.Request(base+'/login',data=login),timeout=20).close()
    check('Administrator session established',get('/api/overview')['account']['id']=='demo')
    csrf=get('/api/csrf')
    def post(path,payload,with_csrf=True):
        headers={'Content-Type':'application/json'}
        if with_csrf: headers[csrf['headerName']]=csrf['token']
        request=urllib.request.Request(base+path,data=json.dumps(payload).encode(),headers=headers,method='POST')
        with session.open(request,timeout=20) as response: return json.load(response)
    try:
        post('/api/jobs',{'type':'BACKUP'},False)
        raise AssertionError('CSRF bypass accepted')
    except urllib.error.HTTPError as error:
        check('Mutation without CSRF rejected',error.code==403)
    def wait(job):
        deadline=time.monotonic()+120
        while time.monotonic()<deadline:
            result=get('/api/jobs/'+job['id'])
            if result['status'] in ('SUCCEEDED','FAILED'):
                if result['status']!='SUCCEEDED': raise AssertionError(result['message'])
                return result
            time.sleep(.2)
        raise TimeoutError('Worker did not complete')
    original=get('/api/sample')
    backup=wait(post('/api/jobs',{'type':'BACKUP'}))
    check('Backup completed',backup['status']=='SUCCEEDED')
    snapshot=next(b for b in get('/api/overview')['backups'] if b['id']==backup['backupId'])
    check('Both regional copies verified',snapshot['status']=='REPLICATED')
    baseline=wait(post('/api/jobs',{'type':'TEST','backupId':backup['backupId']}))
    check('Initial restore test and cleanup passed',baseline['cleanupStatus']=='CLEANED' and baseline['changedObjects']==0)
    post('/api/sample/change',{})
    deadline=time.monotonic()+30
    while time.monotonic()<deadline:
        changed=get('/api/sample')
        if len(changed['registrations'])==len(original['registrations'])+1: break
        time.sleep(.2)
    check('Sample application mutation persisted',len(changed['registrations'])==len(original['registrations'])+1)
    recovery_test=wait(post('/api/jobs',{'type':'TEST','backupId':backup['backupId']}))
    check('Changed data measured',recovery_test['changedObjects']==1 and recovery_test['changedBytes']>0)
    check('Recovery time measured',recovery_test['rtoMillis']>0)
    check('Temporary objects cleaned',recovery_test['cleanupStatus']=='CLEANED')
    restore=wait(post('/api/jobs',{'type':'RESTORE','backupId':backup['backupId']}))
    restored=get('/api/sample?restoreJobId='+restore['id'])
    check('Earlier application data restored',restored==original)
    check('Live source preserved',get('/api/sample')==changed)
    check('Audit export contains the job',any(j['id']==restore['id'] for j in get('/api/report')['jobs']))
    result={'checkedAt':datetime.now(timezone.utc).isoformat(),'mode':'local','status':'PASSED','checks':checks,
        'snapshot':snapshot,'baselineTest':baseline,'changedDataTest':recovery_test,'restore':restore}
    if args.output:
        args.output.parent.mkdir(parents=True,exist_ok=True)
        args.output.write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps({'status':'PASSED','checks':len(checks),'rtoMillis':recovery_test['rtoMillis'],
        'rpoSeconds':recovery_test['rpoSeconds'],'changedObjects':recovery_test['changedObjects'],'cleanupStatus':recovery_test['cleanupStatus']},indent=2))

if __name__=='__main__': main()
