.PHONY: test

test:
ifdef NS
	clojure -M:test -n $(NS)
else
	clojure -M:test
endif
