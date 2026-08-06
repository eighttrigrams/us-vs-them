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

Early, and built out test-first. Two namespaces.

**`et.uvt.caution`** is the one to think from — a history in, ranges of the newest
text out, each saying how careful an agent should be there.

```clojure
(require '[et.uvt.caution :as caution])

(caution/assess [{:text "alpha\nbeta"               :source :human}
                 {:text "alpha\nbeta\ngamma"        :source :agent}
                 {:text "alpha\nbeta\ngamma\ndelta" :source :human}]
                {:ours #{:human}})
;=> [{:from 1 :to 2 :caution 1.0}
;    {:from 3 :to 3 :caution 0.0}
;    {:from 4 :to 4 :caution 1.0}]
```

**`et.uvt.core`** is the diff underneath it — one pair of versions in, the later
one's lines out, each with the source it came from. It has no test and no public
promise of its own: the `caution` test is the only spec, and core holds only what
that spec asks for.

Two things separate the layers. `core` sees exactly one change back, so a whole
history has to be replayed above it — an attribution is a legal `before`, so the
replay is a fold. And `core` never asks what a source marker *means*, only whether
two are equal, which is what lets a caller keep its own vocabulary; `caution` is
where the two sides get told apart, by `:ours`. (Cookbook, the first consumer, calls
its markers `"ui"` and `"machine"`.)

## Where it is going

`:caution` is a number from `0` to `1` — `1` sacred, `0` up for grabs — because
caution is a spectrum and a label has no middle to put anything in.

What puts a line in that middle is the **island** it belongs to. A stretch written
in one go is one thing, and goes on being one thing while later changes shove its
borders about; it gets an identity when it forms and keeps it through the fold. So
an agent line landing inside your island joins the island and dilutes it — three of
four lines still yours, `0.75` — rather than cutting it in two and leaving that one
line freest to edit exactly where it is most entangled with your work. An island
only breaks when what lands in it outweighs it, which is the difference between one
inserted line and thirty.

What is still missing is **when**. A line of yours from a year ago counts exactly as
much as one from this morning, and it shouldn't.

- Decay by age, so an old edit of yours stops holding an agent up.
- A command line tool over a git history, so any repo can be asked the question.

Consumed as a library via `deps.edn`; local consumers point at it with
`:local/root`.

## Development

```sh
make test
```
