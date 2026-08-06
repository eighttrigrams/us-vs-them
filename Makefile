.PHONY: test dist install uninstall

# Where `dist` writes. Override it to build the script somewhere else —
# `make dist DIST=/tmp/us-vs-them` — which is what a deployment of your own
# would do rather than reaching into this directory afterwards.
DIST ?= target/us-vs-them

test:
ifdef NS
	clojure -M:test -n $(NS)
else
	clojure -M:test
endif

# The whole tool as one self-contained babashka script: every namespace inlined,
# a shebang on top, nothing left pointing back at this checkout. That is what
# makes it a thing you can copy — onto a PATH, into a container, over an ssh
# connection — rather than something that only runs where the source happens to
# sit.
dist:
	@mkdir -p $(dir $(DIST))
	bb --classpath src uberscript $(DIST).tmp -m et.uvt.cli
	@printf '#!/usr/bin/env bb\n' | cat - $(DIST).tmp > $(DIST)
	@rm -f $(DIST).tmp
	@chmod +x $(DIST)

# Installing the built script rather than the project, deliberately. `bbin
# install .` would put a *loader* on your PATH — a shim that shells out to `bb
# --deps-root <this directory>` — which is convenient while you are working on
# it here, and useless the moment the tool has to run anywhere this directory
# is not. The cost is that it is a snapshot: edit the source and run this again.
install: dist
	bbin install $(DIST) --as us-vs-them

uninstall:
	bbin uninstall us-vs-them
