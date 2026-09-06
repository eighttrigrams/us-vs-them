.PHONY: test test-cljs dist install uninstall

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

# The other host. `caution` and `core` are .cljc so that a ClojureScript consumer
# can compile them — cookbook computes the provenance split in the browser now,
# because its Recipes are encrypted and its server cannot read them — and a
# library that only ever runs its suite on one of two hosts is a library that
# will drift on the other.
#
# There is deliberately no npm and no shadow-cljs in this repo: it is a library,
# and a second toolchain here would be a second thing to keep in step with the
# one that actually ships the code. So this points at the consumer that has one.
# `cookbook/shadow-cljs.edn` carries this checkout's `src` *and* `test` on its
# source paths, so its `make test-cljs` runs the suite above on node — every
# assertion, unchanged, plus `hosts_test`, which is about the two hosts and
# exists because of this.
UVT_CLJS_HOST ?= ../cookbook

test-cljs:
	$(MAKE) -C $(UVT_CLJS_HOST) test-cljs

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
