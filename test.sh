#!/bin/bash

# 查看用户传入参数
case "$1" in
  hex)
    rm test_run_dir/Hex_Test_should_pass/PaSoC.*
    clear
    sbt "testOnly PaSoC.HexTest"
    ;;
  irq)
    rm test_run_dir/Irq_Test_should_pass/PaSoC.*
    clear
    sbt "testOnly PaSoC.IrqTest"
    ;;
  *)
    echo "Usage: $0 [hex|irq]"
    exit 1
    ;;
esac
