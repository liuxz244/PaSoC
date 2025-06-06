#!/bin/bash

freq_define=""
input=""

# 只支持“<file> [--freq=NNM]”参数顺序（先文件，后可选参数）
for arg in "$@"; do
    if [[ -z "$input" && ! "$arg" =~ ^--freq= ]]; then
        input="$arg"
    elif [[ "$arg" =~ ^--freq=([0-9]+)[Mm]$ ]]; then
        mhz="${BASH_REMATCH[1]}"
        freq_define="-DFREQ_HZ=$((mhz * 1000000))UL"
        echo "频率指定: ${mhz} MHz (${mhz}000000 Hz)"
    elif [[ "$arg" =~ ^-- ]]; then
        echo "未知参数: $arg"
        exit 8
    fi
done

if [ -z "$input" ]; then
    echo "Usage: $0 <input_asm_file.s|input_c_file.c> [--freq=64M]"
    exit 1
fi

filename=$(basename "$input")
ext="${filename##*.}"
base="${filename%.*}"

indir=$(dirname "$input")
tmpdir=$(mktemp -d)
outdir_asm="src/test/asm"
outdir_dmp="src/test/dmp"
outdir_inst="src/test/hex/inst"
outdir_data="src/test/hex/data"
mkdir -p "$outdir_asm" "$outdir_dmp" "$outdir_inst" "$outdir_data"

output_dmp="$outdir_dmp/$base.dmp"
output_asm="$outdir_asm/$base.s"  # 如果输入为C，则生成汇编

inst_bin="$tmpdir/inst.bin"  # 仅临时！ 
data_bin="$tmpdir/data.bin"  # 仅临时！
inst_hex="$outdir_inst/$base.hex"
data_hex="$outdir_data/$base.hex"

obj="$tmpdir/$base.o"
elf="$tmpdir/$base.elf"
startup_obj="$tmpdir/startup.o"

startup_s="src/test/asm/startup.s"
PASOC_C="$indir/PaSoC.c"
PASOC_OBJ="$tmpdir/PaSoC.o"
LINKER_SCRIPT="src/test/C/link.ld"

# === 1. 编译阶段 ===
if [ "$ext" = "c" ]; then
    echo "检测到C源文件, 开始C语言编译流程 ..."

    if [ ! -f "$startup_s" ]; then
        echo "找不到startup.s: $startup_s"
        rm -rf "$tmpdir"
        exit 10
    fi
    riscv32-unknown-elf-gcc -nostdlib -march=rv32i_zicsr -c "$startup_s" -o "$startup_obj"
    if [ $? -ne 0 ]; then
        echo "startup.s编译失败"
        rm -rf "$tmpdir"
        exit 2
    fi

    if [ ! -f "$PASOC_C" ]; then
        echo "找不到PaSoC.c: $PASOC_C"
        rm -rf "$tmpdir"
        exit 11
    fi
    riscv32-unknown-elf-gcc -nostdlib -march=rv32i_zicsr -ffunction-sections -fdata-sections \
        $freq_define -c "$PASOC_C" -o "$PASOC_OBJ"
    if [ $? -ne 0 ]; then
        echo "PaSoC.c编译失败"
        rm -rf "$tmpdir"
        exit 12
    fi

    riscv32-unknown-elf-gcc -S -nostdlib -march=rv32i_zicsr $freq_define "$input" -o "$output_asm"
    if [ $? -ne 0 ]; then
        echo "C编译生成汇编失败"
        rm -rf "$tmpdir"
        exit 2
    fi

    riscv32-unknown-elf-gcc -c -nostdlib -march=rv32i_zicsr $freq_define "$output_asm" -o "$obj"
    if [ $? -ne 0 ]; then
        echo "汇编转目标文件失败"
        rm -rf "$tmpdir"
        exit 2
    fi

elif [ "$ext" = "s" ]; then
    echo "检测到汇编源文件，开始汇编流程 ..."

    riscv32-unknown-elf-gcc -nostdlib -march=rv32i_zicsr -c "$input" -o "$obj"
    if [ $? -ne 0 ]; then
        echo "GCC编译失败。"
        rm -rf "$tmpdir"
        exit 2
    fi
else
    echo "不支持的文件类型: $ext, 只接受.c或.s"
    rm -rf "$tmpdir"
    exit 6
fi

# === 2. 链接阶段（关键：使用自定义link.ld） ===

if [ "$ext" = "c" ]; then
    riscv32-unknown-elf-gcc -nostdlib -march=rv32i_zicsr -T $LINKER_SCRIPT \
        -Wl,--gc-sections "$startup_obj" "$PASOC_OBJ" "$obj" -o "$elf"
else
    riscv32-unknown-elf-gcc -nostdlib -march=rv32i_zicsr -T $LINKER_SCRIPT \
        -Wl,--gc-sections "$obj" -o "$elf"
fi
if [ $? -ne 0 ]; then
  echo "链接失败。"
  rm -rf "$tmpdir"
  exit 3
fi

# === 3. 生成反汇编文件（可选/调试） ===
riscv32-unknown-elf-objdump -D "$elf" > "$output_dmp"
if [ $? -ne 0 ]; then
    echo "生成dmp(反汇编)文件失败。"
    rm -rf "$tmpdir"
    exit 7
fi

# === 4. 拆分指令区/数据区bin文件 ===
riscv32-unknown-elf-objcopy -O binary -j .text "$elf" "$inst_bin"
riscv32-unknown-elf-objcopy -O binary -j .data -j .rodata "$elf" "$data_bin"

# === 5. 转为小端HEX，每行为一32位指令/数据（适用Chisel MEM INIT） ===
xxd -p -c 4 "$inst_bin" | awk '{printf "%s%s%s%s\n", substr($1,7,2), substr($1,5,2), substr($1,3,2), substr($1,1,2)}' > "$inst_hex"
xxd -p -c 4 "$data_bin" | awk '{printf "%s%s%s%s\n", substr($1,7,2), substr($1,5,2), substr($1,3,2), substr($1,1,2)}' > "$data_hex"

echo "反汇编已保存至 $output_dmp"
echo "指令hex已保存到 $inst_hex"
echo "数据hex已保存到 $data_hex"

if command -v stat &>/dev/null; then
    echo "指令大小: $(stat -c %s "$inst_bin") bytes"
    echo "数据大小: $(stat -c %s "$data_bin") bytes"
fi

rm -rf "$tmpdir"
