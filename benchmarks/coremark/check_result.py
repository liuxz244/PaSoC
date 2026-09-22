#!/usr/bin/env python3
import re
import sys
from pathlib import Path
text = Path(sys.argv[1]).read_text()
mode, iterations, hz = sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
expected = {'performance': ['e9f5', 'e714', '1fd7', '8e3a'], 'validation': ['18f2', 'e3c1', '0747', '8d84']}[mode]
for name, value in zip(['seedcrc', 'crclist', 'crcmatrix', 'crcstate'], expected):
    match = re.search(name + r'\s*:\s*0x([0-9a-f]+)', text)
    if not match or match[1] != value:
        sys.exit(f'FAIL: {name}, expected {value}')
duration_error = 'ERROR! Must execute for at least 10 secs for a valid result!'
errors = [line for line in text.splitlines() if 'ERROR!' in line and line != duration_error]
if 'SIM_EXIT' not in text or errors or 'Cannot validate operation' in text:
    sys.exit(f'FAIL: incomplete run or validation error: {errors}')
m = re.search(r'PASOC_CYCLES_HI=(\d+) PASOC_CYCLES_LO=(\d+)', text)
if not m: sys.exit('FAIL: missing 64-bit cycle measurement')
cycles = (int(m[1]) << 32) | int(m[2])
if cycles <= 0:
    sys.exit('FAIL: nonpositive cycle count')
if not re.search(r'Iterations\s*:\s*' + str(iterations) + r'\b', text):
    sys.exit('FAIL: iteration count mismatch')
if f'PaSoC CoreMark clock Hz: {hz}\n' not in text:
    sys.exit('FAIL: clock frequency mismatch')
seconds = cycles / hz
valid = seconds >= 10 and 'Correct operation validated.' in text
if seconds >= 10 and not valid:
    sys.exit('FAIL: missing upstream validation success')
print(f'CRC PASS ({mode}); cycles={cycles}; iterations={iterations}; seconds={seconds:.6f}')
print(f'CoreMark/MHz = {iterations * 1e6 / cycles:.6f}; at {hz/1e6:g} MHz = {iterations*hz/cycles:.6f} iterations/s')
print('Valid duration and upstream validation PASS' if valid else 'SHORT SIMULATION: normalized estimate only; below 10 seconds, not a reportable CoreMark score')
