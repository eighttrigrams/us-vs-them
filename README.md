# us-vs-them

Line-level provenance for text under agentic editing — **who wrote this line, us or
them?** — derived from a text's version history.

![alt](./title_image.png)

## The problem

With agentic coding and editing, provenance becomes a pertinent question. Text a
human wrote or edited should be considered close to sacred: an agent should 
be hesitant and have a very good reason to touch it. Slop another agent has produced,
on the other hand, is completely up for grabs.

The main constraint under which this should work is that each new version of a
text is created under identifiable authorship of either a human or an agent.
As plain text (markdown) is everywhere, we don't want to build on specially marked-up text.

The output of an evaluation over a given text is a set of ranges — "islands" of
human-authored lines — where human-authored is a gradient insofar as agent edits
slightly degrade the sacredness of that island. Conversely, a human insertion into
a stretch of agentic text becomes a new island.

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

## Usage

The tool is meant to be used as a library as well as from the command line.

The latter requires [bbin](https://github.com/babashka/bbin) for a local install.

```sh
make install
```

Then, anywhere inside a git repository:

```sh
us-vs-them --ours dan@eighttrigrams.net README.md
```

```
1-3          0.00
4            1.00
5-7          0.00
8-20         0.46
21-164       0.00
```

A git repository is already a history of versions each carrying a provenance
marker — every revision of the file, in order, with the author of the change that
made it.

Name whichever side is the shorter list. The two flags are repeatable, and they are
not mirror images of each other — they read differently on purpose.

`--theirs` is a **blacklist**: these are machines, everyone else is considered human.

`--ours` is a **guest list**: these are the humans, and saying it means saying it.

Passing both arguments at the same time will be rejected.

## Development

```sh
make test
```

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

**Outweighs which island, though.** Weighed against the whole, nothing ever wins:
no single edit beats a long file, so everything is absorbed and after a hundred
versions one island covers the text at some middling number — true about the file,
useless about any part of it. What is actually at stake when something lands in the
middle is the smaller of the two pieces it would cut the island into. A block bigger
than the land it separates is not a stain on anything: it has its own beginning and
its own end and can be rewritten whole without disturbing a line of yours, so the
island parts around it and it becomes water. A run at the end of an island strands
nothing and cuts nothing.

A slow drip stays a drip, deliberately. Single lines arriving one per version are
each outweighed by whatever piece they land in, forever, so an island can walk a
long way down without ever coming apart — and that is what an island is for.
Dilution is the answer to being edited a little at a time; parting is the answer to
having a section put through you.

**Absorption runs one way only**, and that is where the two sides are told apart.
Their line landing in your island is contamination and gets held close. Your line
landing in theirs is a landing, not an assimilation: it stays an island of its own
at `1`, because nothing you write by hand should be made cheap by what happens to
surround it. Every other part of the mechanism is blind to whose a line is.

"Inside" is answered by what a change **displaced**, not by what the new lines
happen to sit between. Most edits are replacements, and a replacement's neighbours
may be anything at all — rewrite the last paragraph of a section and what follows it
is whatever came next in the file. A run that turned out a stretch of an island
landed in that island, whatever it abuts. And it is weighed against the island as it
*was*, not against the lines left after the deletion, because the deletion and the
insertion are one edit and counting the remnant charges the island twice.

Islands also **rejoin**. Two of yours separated by a block of theirs become one
again when that block goes away — they were only ever two because something stood
between them. That never moves a number on its own, since only unmixed islands of
the same side merge; it changes what happens next, because absorption weighs itself
against the island's size.

One heuristic sits on top of the diff: **an isolated match is not a survival, if the
line recurs.** Replace a block of text and the aligner will happily match the blank
line in the middle of it, cutting one edit into three. But a line occurring *once* in
a document and turning up again unchanged really did survive — there is nothing else
it could have been mistaken for. It is the line occurring forty times whose match
proves nothing. Nothing in that rule mentions whitespace.

What is still missing is **when**. A line of yours from a year ago counts exactly as
much as one from this morning, and it shouldn't.

- Decay by age, so an old edit of yours stops holding an agent up.
- A command line tool over a git history, so any repo can be asked the question.

Consumed as a library via `deps.edn`; local consumers point at it with
`:local/root`.
