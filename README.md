# us-vs-them

Line-level provenance for text under agentic editing — **who wrote this line, us or
them?** — derived from a file's version history rather than from markup in the file.

## The problem

With agentic coding and editing, provenance becomes the pertinent question. Text a
human wrote or edited is close to sacred: an agent needs a very good reason to touch
it. Text an agent produced in the first place, or has since edited into something
predominantly its own, is up for grabs — an agent should feel free there.

That is not a binary, though. A line can be of mixed heritage, and it moves:

- Lines 5–17 were written by a human. Sacred.
- An agent then makes fifteen changes that land in that range. By now those lines are
  mostly of agentic origin, whatever their beginning was.
- And time counts. Something a human touched *recently* takes a very good reason
  indeed; the further back a human edit sits, the less it should hold up an agent.

So what we want out of a file is not a yes/no but a **gradient of caution** over its
ranges of lines.

## The approach: no markup

The thing we deliberately do **not** do is annotate the text. No provenance comments,
no sidecar of per-line markers to keep in sync, nothing in the file that is not the
file. Text stays text.

What we build on instead is a single assumption: **for any one change to a text, we
can tell whether it came from a human or from an agent.** That is a fact the
surrounding system already has — a git author, a commit signature, a `source` column
on a version row. It costs nothing to record and it cannot drift, because it is
recorded at the moment the change is made.

Everything else is then diffing. Replay the versions, attribute each line of each new
version to whoever's change it survived or arrived in, and accumulate that into a
per-line heritage. The caution heuristic reads off the accumulation: how much of this
line is human, how much agentic, and how long ago.

## Status

Early. One function, built out test-first.

```clojure
(require '[et.uvt.core :as uvt])

(uvt/attribute {:text "alpha\nbeta\n"          :source :human}
               {:text "alpha\nnew line\nbeta\n" :source :agent})
;=> [{:text "alpha"    :source :human}
;    {:text "new line" :source :agent}
;    {:text "beta"     :source :human}]
```

`:source` is whatever marker the caller uses; the library does not care what the two
sides are called, only that they can be told apart. (Cookbook, the first consumer,
calls them `"ui"` and `"machine"`.)

## Where it is going

- Fold a whole version history, not just a pair, into one attribution.
- Weight by *how many* changes touched a line and by *how recently*, to get the
  gradient rather than the last writer.
- Roll lines up into ranges an agent can be told about.
- A command line tool over a git history, so any repo can be asked the question.

Consumed as a library via `deps.edn`; local consumers point at it with
`:local/root`.

## Development

```sh
make test
```
