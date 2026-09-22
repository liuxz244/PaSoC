# PaSoC CoreMark RTL 仿真结果

测试日期：2026-09-22。未修改 CPU RTL；使用仓库现有 `PaSoCsim`。

**性能：2.345488 CoreMark/MHz；按 50 MHz 换算为 117.274401 CoreMark。**
这是周期精确 RTL 仿真结果，50 MHz 为配置频率，不是实测 FPGA Fmax 或板上成绩。

| 测试 | 迭代次数 | 计时周期 | 50 MHz 等效时长 | CRC / 上游校验 |
| --- | ---: | ---: | ---: | --- |
| Performance | 1300 | 554255656 | 11.085113 s | PASS |
| Validation | 1300 | 552301986 | 11.046040 s | PASS |

两组运行均输出 `Correct operation validated.`；均超过 10 秒。
CoreMark 分数取 performance 种子，validation 的吞吐量不用于代替性能成绩。
输出中的 `CoreMark Size: 666` 是每个算法分到的字节数；总缓冲区为标准 2000 字节。

## 配置

- CoreMark 1.0 官方 commit：`1f483d5b8316753a742cbf5590caf5bd0a4e4777`。
- GCC 14.2.0，RV32IM + Zicsr，ILP32，单核、单上下文、静态内存。
- Flags：`-O3 -march=rv32im_zicsr -mabi=ilp32 -msmall-data-limit=0 -ffreestanding -fno-builtin -fno-common -ffunction-sections -fdata-sections`。
- 无 LTO/PGO；所有 benchmark C 文件统一使用上述参数。
- Verilator `5.053 devel rev v5.052-22-g7cf8c5cca`，Chisel 6.6.0，Scala 2.13.12，sbt 1.9.7。
- 16 KiB ITCM 存放指令；8 KiB DTCM 存放数据和栈；本测试不访问外部 DRAM/cache。
- ELF `.text`：13800 字节。Performance `.data`：1956，`.bss`：2028；Validation `.data`：1964，`.bss`：2020。
- 栈顶 `0x10002000`，BSS 末端 `0x10000f94`，实际可用栈空间 4204 字节。
- UART 3,125,000 baud，配置 CPU 时钟 50,000,000 Hz。
- 计时直接读取每周期递增的 CLINT 64 位 `mtime`，不包含初始化及结果打印。

## 精确换算与日志

`CoreMark/MHz = 1300 × 1,000,000 / 554255656 = 2.345488`。

`CoreMark @ 50 MHz = 1300 × 50,000,000 / 554255656 = 117.274401`。

裸机端配置 `HAS_FLOAT=0`，上游输出的秒数向下取整为 11，因此它显示的整数
`Iterations/Sec: 118` 精度有限。以上成绩由宿主脚本使用完整周期数计算。

- [Performance 原始日志](results/performance-1300.log)
- [Performance 精确摘要](results/performance-1300-summary.txt)
- [Validation 原始日志](results/validation-1300.log)
- [Validation 精确摘要](results/validation-1300-summary.txt)

## 复现

在仓库根目录运行：

```sh
scripts/coremark.sh performance 1300
scripts/coremark.sh validation 1300
```

本次长测试复用了短测试生成的同配置 Verilator 模型，分别重新编译并加载两组
1300 次固件；模型每次启动通过 `$readmemh` 加载 HEX。上述脚本会重新生成并构建
相同硬件配置，执行相同固件和校验。

构建文件保留在 `build/coremark/`（Git 忽略），两组 ELF SHA-256 为：

- `performance-1300.elf`: `c485e4da03964570e49116a0e0c3df43e36ceb2aa24858da5a910ce1b9a0bb67`
- `validation-1300.elf`: `e66a10e5be8d9872e8a2932dda64d2b2004450944833265f68f91e1786a1afbd`

## 其他验证

- 两组 10 次短测试的全部标准 CRC 通过，按不足 10 秒明确标为短测试。
- 100 万周期超时测试返回非零。
- 结果校验器拒绝错误 CRC、截断日志、额外错误、迭代次数及频率不一致。
- 官方源码与固定 commit 完全一致，SHA-256 清单通过。
