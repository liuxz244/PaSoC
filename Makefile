BUILD_DIR  = ./build
TOPNAME    = PaSoCsim
NXDC_FILES = nvboard/nxdc/PaSoC.nxdc
INC_PATH  ?=

VERILATOR = verilator
VERILATOR_CFLAGS += -MMD --build -cc \
                    -O3 --x-assign fast --x-initial fast --noassert

OBJ_DIR = $(BUILD_DIR)/obj_dir
BIN = $(BUILD_DIR)/$(TOPNAME)

# constraint auto bind
SRC_AUTO_BIND = $(abspath $(BUILD_DIR)/auto_bind.cpp)
$(SRC_AUTO_BIND): $(NXDC_FILES)
	python3 $(NVBOARD_HOME)/scripts/auto_pin_bind.py $^ $@

# vsrc / csrc
VSRCS = $(shell find $(abspath ./nvboard) -name "*.sv" -or -name "*.v")
CSRCS = $(shell find $(abspath ./nvboard) -name "*.c" -or -name "*.cc" -or -name "*.cpp")
CSRCS += $(SRC_AUTO_BIND)

# nvboard rules
include $(NVBOARD_HOME)/scripts/nvboard.mk

INCFLAGS = $(addprefix -I, $(INC_PATH))
CXXFLAGS += $(INCFLAGS) -DTOP_NAME="\"V$(TOPNAME)\""

$(BIN): $(VSRCS) $(CSRCS) $(NVBOARD_ARCHIVE)
	@rm -rf $(OBJ_DIR)
	$(VERILATOR) $(VERILATOR_CFLAGS) \
		--top-module $(TOPNAME) $^ \
		$(addprefix -CFLAGS , $(CXXFLAGS)) $(addprefix -LDFLAGS , $(LDFLAGS)) \
		--Mdir $(OBJ_DIR) --exe -o $(abspath $(BIN))

.PHONY: clean sim

clean:
	@find $(BUILD_DIR) -mindepth 1 ! -name '.gitkeep' -delete

sim: $(BIN)
	@$^

