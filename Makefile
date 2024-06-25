MAKEFLAGS += --silent

all: start_scripts

start_scripts:
	echo "Starting scripts with interleaved output..."
	sh run.sh start

stop_scripts:
	echo "Stopping scripts..."
	sh run.sh stop

.PHONY: all start_scripts stop_scripts
