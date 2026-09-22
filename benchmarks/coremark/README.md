# PaSoC CoreMark

已测结果：**2.345488 CoreMark/MHz**，50 MHz 等效 **117.274401 CoreMark**。
完整配置、原始日志和限制见 [RESULTS.md](RESULTS.md)。

EEMBC CoreMark 1.0，官方源码固定于 commit
`1f483d5b8316753a742cbf5590caf5bd0a4e4777`：
https://github.com/eembc/coremark/tree/1f483d5b8316753a742cbf5590caf5bd0a4e4777

`upstream/` 源码未修改，保留 Apache-2.0 许可证。完整 SHA-256 清单为
`upstream.sha256`。上游 `coremark.md5` 中的 `coremark.h` 条目已过期；
本目录 header 与固定 commit 完全一致，五个算法/框架 C 文件通过上游 MD5。

## 运行

在仓库根目录执行（需要 RISC-V bare-metal GCC、sbt、Verilator、C++ 和 Python 3）：

```sh
scripts/coremark.sh performance 1300
scripts/coremark.sh validation 1300
```

默认迭代次数为 1300；快速冒烟测试可传入 `10`。

默认 `CROSS_COMPILE=riscv64-unknown-elf-`，但生成的机器码是
`rv32im_zicsr` / `ilp32`。可以设置 `CROSS_COMPILE=riscv32-unknown-elf-`。
`BUILD_ONLY=1` 只生成 ELF/HEX，适合上板。

计时使用 CLINT `mtime` 的 high-low-high 64 位一致性读取，单位为 CPU 周期。
默认 `FREQ_HZ=50000000`；这只是仿真计时换算频率，不是测得的 FPGA Fmax。
默认 `BAUD_RATE=3125000`，对应每位 16 周期，串口仅用于计时区间外的报告。
上板时必须让这两个值与生成硬件的配置一致。例如使用项目现有生成入口：

```sh
FREQ_HZ=36000000 BAUD_RATE=115200 BUILD_ONLY=1 scripts/coremark.sh performance 1300
./test.sh run coremark --freq=36M --baud=115200
```

实际 FPGA 时钟需由板级设计提供；上述命令不完成综合、时序收敛或烧录。

程序使用 16 KiB ITCM 与 8 KiB DTCM；CoreMark 数据区 2000 字节、
静态内存模式、单核单上下文，栈也放 DTCM。不经过外部 DRAM/cache。
启动代码清零 BSS，链接器为栈保留至少 2 KiB。
使用仓库现有 `PaSoCsim`，未替换 CPU、存储器或总线模型。

`build/coremark/` 保存 ELF、map、反汇编、构建日志及每次运行日志；
各组 ELF/map 也以 `performance-1300` / `validation-1300` 等名字单独保留。
`src/test/hex/{inst,data}/coremark.hex` 是本次固件。
运行器遇到超时、CRC 错误或输出不完整返回非零。
`MAX_CYCLES` 默认 2,000,000,000。

## 计分

- CoreMark/MHz = iterations × 1,000,000 / measured_cycles。
- 指定频率下 iterations/s = iterations × FREQ_HZ / measured_cycles。
- 官方报告要求计时区间至少 10 秒，并通过 performance 与 validation 两组种子的 CRC。
- 10 次迭代通常只适合冒烟测试，脚本会明确标注短仿真结果，保留上游时长错误提示。
- 增加迭代次数直至两组日志均出现 `Correct operation validated.`。
- `HAS_FLOAT=0` 避免引入浮点 printf；上游整数秒/整数吞吐量输出保留，
  宿主校验脚本用完整的 64 位周期数计算更精确的结果。
