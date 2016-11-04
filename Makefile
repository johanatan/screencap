
.PHONY: run watch

run:
	mkdir -p cache/
	planck -k cache/ -c ./deps/andare-0.1.0.jar:src/ -m screencap.core

watch:
	./watch "make run" 60 
