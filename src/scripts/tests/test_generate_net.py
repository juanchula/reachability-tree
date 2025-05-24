#!/usr/bin/env python3
import subprocess
import json
import sys

# Run the generator
cmd = [
    sys.executable, 'generate_net.py',
    '--branches', '3',
    '--branches-len', '1,1,1',
    '--tail-length', '2'
]
result = subprocess.run(cmd, capture_output=True, text=True)
if result.returncode != 0:
    print('generate_net.py failed:', result.stderr)
    sys.exit(1)

generated = json.loads(result.stdout)
with open('net_1.json') as f:
    reference = json.load(f)

if generated == reference:
    print('Test passed: generated net matches net_1.json')
    sys.exit(0)
else:
    print('Test failed: generated net does not match net_1.json')
    sys.exit(1) 