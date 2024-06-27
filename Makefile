MAKEFLAGS += --silent

all: start_scripts

start_scripts:
	echo "Starting scripts with interleaved output..."
	sh run.sh start

.PHONY: all start_scripts
