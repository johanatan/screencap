 
.PHONY: run watch

run:
	lumo -K -m screencap.core

watch:
	./watch "make run" 60 
