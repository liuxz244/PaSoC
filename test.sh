#!/bin/bash

rm test_run_dir/Hex_Test_should_pass/PaSoC.*
clear
sbt "testOnly PaSoC.HexTest"
