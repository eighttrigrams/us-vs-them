# Us vs. Them

Line-level provenance for text under agentic editing — **who wrote this line, us or
them?** — derived from a text's version history. Use it as library or as CLI tool.

![alt](./title_image.png)

## The problem

With agentic coding and editing, provenance becomes a pertinent question. Text a
human wrote or edited should be considered close to sacred: an agent should 
be hesitant and have a very good reason to touch it. Slop another agent has produced,
on the other hand, is completely up for grabs.

A use case for this: Take a mostly vibecoded in which you want to establish
some corners in the code where you want to assert your ideas and ownership.
You surely don't want another agent bulldoze over *this* piece of code 
in the next session. 

Another use case: the README.md, originally generated, where you rewrite 
the opening paragraphs. The agent should feel free to redo or append parts 
further downwars but should really think twice changing anything in the opener.

## How it works

The main constraint under which this should work is that this should not require
for text to be marked up specifically for that. Omnipresent plain text (markdown)
should be supported as is.

The only thing to leverage then, is that each new version of a text is created
under identifable authorship - of either a human or an agent.

The output of an evaluation over a given text is a set of ranges — "islands" of
human-authored lines inside a "sea" of machine generated text.

Technically based on simple diffing, this is the guiding metaphor 
for development of the algorithm. We don't want to track authorship
of individual lines only, but of meaningfully coherent pieces of text.
So joining, splitting apart, and dilution of authorship are behaviours
to be factored in, also in such a manner that we don't 
converge in full sea or full island.

## Usage

Using us-vs-them as CLI tool requires [bbin](https://github.com/babashka/bbin) for a local install.

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

## Behaviour

The best way to understand the behaviour is to have a look at
[caution_test.clj](test/et/uvt/caution_test.clj).
