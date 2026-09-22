#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mode=${1:-performance}
iterations=${2:-1300}
case "$mode" in performance) run=PERFORMANCE_RUN;; validation) run=VALIDATION_RUN;; *) echo "Usage: $0 [performance|validation] [iterations]" >&2; exit 2;; esac
[[ "$iterations" =~ ^[1-9][0-9]*$ ]] && (( iterations <= 2147483647 )) || exit 2
prefix=${CROSS_COMPILE:-riscv64-unknown-elf-}
freq=${FREQ_HZ:-50000000}
baud=${BAUD_RATE:-3125000}
[[ "$freq" =~ ^[1-9][0-9]*$ ]] && (( freq <= 2147483647 )) || exit 2
[[ "$baud" =~ ^[1-9][0-9]*$ ]] && (( baud <= freq / 16 )) || { echo "BAUD_RATE must be positive and <= FREQ_HZ/16" >&2; exit 2; }
out=build/coremark
mkdir -p "$out"
trap 'echo "CoreMark failed; inspect build/coremark/{elaborate,verilator}.log and the run log" >&2' ERR
src=benchmarks/coremark
flags=(-O3 -march=rv32im_zicsr -mabi=ilp32 -msmall-data-limit=0 -ffreestanding -fno-builtin -fno-common -ffunction-sections -fdata-sections)
"${prefix}gcc" "${flags[@]}" -I"$src/port" -I"$src/upstream" -D"$run"=1 -DITERATIONS="$iterations" -DFREQ_HZ="$freq" -DBAUD_RATE="$baud" "-DFLAGS_STR=\"${flags[*]}\"" -nostdlib -Wl,--gc-sections,-Map="$out/coremark.map" -T"$src/port/link.ld" "$src/port/startup.S" "$src/port/core_portme.c" "$src"/upstream/*.c -lgcc -o "$out/coremark.elf"
cp "$out/coremark.elf" "$out/$mode-$iterations.elf"
cp "$out/coremark.map" "$out/$mode-$iterations.map"
"${prefix}size" "$out/coremark.elf"
"${prefix}objdump" -d "$out/coremark.elf" > "$out/coremark.dmp"
"${prefix}objcopy" -O binary -j .text "$out/coremark.elf" "$out/inst.bin"
"${prefix}objcopy" -O binary -j .data "$out/coremark.elf" "$out/data.bin"
python3 - <<'HEX'
from pathlib import Path
for name, size in [('inst',16384),('data',8192)]:
    b=Path(f'build/coremark/{name}.bin').read_bytes()
    assert len(b)<=size
    b=b.ljust(size,b'\0')
    Path(f'src/test/hex/{name}/coremark.hex').write_text(''.join(f'{int.from_bytes(b[i:i+4],"little"):08x}\n' for i in range(0,size,4)))
HEX
if [[ "${BUILD_ONLY:-0}" == 1 ]]; then exit 0; fi
PASOC_INIT_HEX=coremark.hex PASOC_SIM=1 PASOC_CLOCK_FREQ="$freq" PASOC_BAUD_RATE="$baud" sbt 'runMain PaSoC.nvboard' > "$out/elaborate.log" 2>&1
verilator --cc --exe --build -j 4 -O3 -Wno-fatal --top-module PaSoCsim --Mdir "$out/obj" -o coremark-sim PaSoCsim.sv "$(pwd)/benchmarks/coremark/sim.cpp" > "$out/verilator.log" 2>&1
"$out/obj/coremark-sim" "${MAX_CYCLES:-2000000000}" 2>&1 | tee "$out/$mode-$iterations.log"
python3 "$src/check_result.py" "$out/$mode-$iterations.log" "$mode" "$iterations" "$freq" | tee "$out/$mode-$iterations-summary.txt"
