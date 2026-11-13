#!/usr/bin/env python3
"""Recover a snapshot directly from secondary S3 when the primary control plane is unavailable.
Requires Python 3.10+, boto3, and a role with ListBucket/GetObject on the secondary bucket.
Writes only to a NEW local destination; never overwrites an existing directory.
"""
import argparse
import hashlib
import json
import pathlib
import re
import boto3

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bucket', required=True)
    parser.add_argument('--region', required=True)
    parser.add_argument('--tenant', default='demo', help='demo or the 32-character workspace ID from an exported report')
    parser.add_argument('--backup', help='Exact backup ID; omit to list available manifests')
    parser.add_argument('--destination', type=pathlib.Path)
    parser.add_argument('--manifest-sha256', help='Optional trusted manifest digest from an exported report')
    args = parser.parse_args()
    if args.tenant != 'demo' and not re.fullmatch(r'[a-f0-9]{32}', args.tenant):
        parser.error('Invalid workspace ID')
    backup_root = ('tenants/' + args.tenant + '/' if args.tenant != 'demo' else '') + 'backups/'
    client = boto3.client('s3', region_name=args.region)
    if not args.backup:
        for page in client.get_paginator('list_objects_v2').paginate(Bucket=args.bucket, Prefix=backup_root):
            for obj in page.get('Contents', []):
                if obj['Key'].endswith('/manifest.json'):
                    print(obj['Key'][len(backup_root):].split('/')[0])
        return
    if not re.fullmatch(r'[a-zA-Z0-9-]{1,100}', args.backup) or args.destination is None:
        parser.error('Provide a valid backup ID and --destination')
    prefix = backup_root + args.backup + '/'
    def read(key):
        response = client.get_object(Bucket=args.bucket, Key=key)
        with response['Body'] as stream:
            if response['ContentLength'] > 20 * 1024 * 1024:
                raise ValueError('Object exceeds the 20 MiB recovery limit')
            data = stream.read(20 * 1024 * 1024 + 1)
            if len(data) > 20 * 1024 * 1024:
                raise ValueError('Object exceeds recovery limit')
            return data
    document = read(prefix + 'manifest.json')
    if args.manifest_sha256 and hashlib.sha256(document).hexdigest() != args.manifest_sha256:
        raise ValueError('Manifest does not match trusted digest')
    manifest = json.loads(document)
    if manifest.get('schemaVersion') != 1 or manifest.get('id') != args.backup:
        raise ValueError('Invalid manifest')
    entries = manifest['entries']
    if not 0 < len(entries) <= 500:
        raise ValueError('Invalid object count')
    objects = {}
    total = 0
    for entry in entries:
        key = entry['key']
        if not key.startswith('application/') or '\\' in key or ':' in key or any(p in ('', '.', '..') for p in key.split('/')) or key in objects:
            raise ValueError('Unsafe or duplicate object key')
        data = read(prefix + 'files/' + key)
        total += len(data)
        if total > 20 * 1024 * 1024 or len(data) != entry['bytes'] or hashlib.sha256(data).hexdigest() != entry['sha256']:
            raise ValueError('Snapshot integrity or size check failed: ' + key)
        objects[key] = data
    args.destination.mkdir(parents=True, exist_ok=False)
    for key, data in objects.items():
        target = args.destination.joinpath(*key.split('/'))
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    print(f'Recovered and verified {len(objects)} objects ({total} bytes) to {args.destination.resolve()}')
    if not args.manifest_sha256:
        print('Integrity checked against stored manifest; no external trusted manifest digest was supplied.')

if __name__ == '__main__':
    main()
