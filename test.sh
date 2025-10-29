#!/bin/bash

# 解析命令行参数
if [ "$#" -lt 2 ]; then
    echo "Usage: $0 <run|sim|gpio|nvboard> <initHexFile> [--freq=27M] [--baud=9600] [--rvDebug]"
    exit 1
fi

TestType=$1
HexName=$2
freq="36M"
baud="115200"
rvDebug=false

# 解析可选参数
shift 2
while [ "$#" -gt 0 ]; do
    case "$1" in
        --rvDebug)
            rvDebug=true
            shift
            ;;
        --freq=*)
            freq="${1#*=}"  # 提取 --freq= 后面的值
            shift
            ;;
        --baud=*)
            baud="${1#*=}"  # 提取 --baud= 后面的值
            shift
            ;;
        *)
            echo "Usage: $0 <run|sim|gpio|nvboard> <initHexFile> [--freq=27M] [--baud=9600] [--rvDebug]"
            exit 1
            ;;
    esac
done

# 解析 freq 参数并转换为数值
freqValue=$((${freq%M} * 1000000))  # 去掉 'M' 并乘以 1000000

function Set-EnvAndRun {
    local CommandBlock=$1

    if [ -n "$HexName" ]; then
        export PASOC_INIT_HEX="$HexName.hex"
    fi

    if $rvDebug; then
        export PASORV_DEBUG="1"
    fi

    if [ "$TestType" == "run" ]; then
        export PASOC_CLOCK_FREQ="$freqValue"
        export PASOC_SIM="0"
        export PASOC_BAUD_RATE="$baud"
    else
        # nvboard规定频率必须是波特率的16倍
        export PASOC_CLOCK_FREQ=1600
        export PASOC_SIM="1"
        export PASOC_BAUD_RATE=100
    fi

    eval $CommandBlock

    unset PASOC_INIT_HEX
    unset PASORV_DEBUG
    unset PASOC_CLOCK_FREQ
    unset PASOC_SIM
    unset PASOC_BAUD_RATE
}

case $TestType in
    "run")
        rm -f "PaSoC.sv" "PaSoC.v"
        clear
        Set-EnvAndRun 'sbt "runMain PaSoC.Main"'
        ;;
    "sim")
        rm -f test_run_dir/Hex_Test_should_pass/PaSoCsim.*
        clear
        Set-EnvAndRun 'sbt "testOnly PaSoC.HexTest"'
        ;;
    "gpio")
        rm -f test_run_dir/GPIO_Test_should_pass/PaSoCsim.*
        clear
        Set-EnvAndRun 'sbt "testOnly PaSoC.GpioTest"'
        ;;
    "nvboard")
        rm -f "nvboard/PaSoCsim.sv"
        clear
        Set-EnvAndRun 'sbt "runMain PaSoC.nvboard"'
        mv "PaSoCsim.sv" "nvboard/PaSoCsim.sv"
        ;;
    *)
        echo "Usage: $0 <run|sim|gpio|nvboard> <initHexFile> [--freq=27M] [--baud=9600] [--rvDebug]"
        exit 1
        ;;
esac
