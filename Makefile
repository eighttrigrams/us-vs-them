.PHONY: test install uninstall

test:
ifdef NS
	clojure -M:test -n $(NS)
else
	clojure -M:test
endif

install:
	bbin install . --as us-vs-them --main-opts '["-m" "et.uvt.cli"]'

uninstall:
	bbin uninstall us-vs-them
