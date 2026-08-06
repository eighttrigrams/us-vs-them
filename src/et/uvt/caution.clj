(ns et.uvt.caution
  "How carefully an agent should treat each part of a text — the question the
  library exists to answer, and the one to think from.

  `core` is deliberately short of it in two ways, and this namespace is what closes
  both.

  **It sees one change back.** `core/attribute` takes a pair, and a surviving line
  can only be attributed to the earlier of the two, whoever actually last touched
  it. Replaying a whole history fixes that, and the replay is a plain fold, because
  an attribution is a legal `before`.

  **It does not take sides.** `core` never asks what a source marker means, only
  whether two are equal, which is what lets a caller keep its own vocabulary. But
  *careful* is not a symmetric notion — the whole point is that our lines and their
  lines are not owed the same deference — so somewhere the two sides have to be told
  apart. That happens here and nowhere below: `:ours` names the markers that are us.

  And the answer comes out as **ranges**, because that is the unit an agent can be
  told about. Per-line output would be technically the same information and useless
  in a prompt."
  (:require [et.uvt.core :as core]))

(defn- heritage
  "Every line of the newest version, with the source of the change it last came out
  of, by replaying the history from the beginning."
  [[oldest & later]]
  (reduce core/attribute (core/attribute oldest) later))

(defn- verdict
  "How careful to be about one line, on a scale from `0` to `1` — `1` sacred, `0` up
  for grabs.

  A number rather than a name, because caution is a spectrum and a name has no
  middle to put anything in. Only the ends are reachable from what the fold gives
  us: it hands each line one source, the last one to touch it, so the answer can
  only be *ours* or *theirs* and nothing in between. What lands a line between them
  is how much of its history is ours rather than just its last moment, and how long
  ago that moment was — neither of which is folded yet."
  [ours source]
  (if (contains? ours source) 1.0 0.0))

(defn assess
  "Ranges of `history`'s newest text, each with how careful an agent should be in it.

  `history` is versions oldest first, each `{:text \"…\" :source <marker>}`; `:ours`
  in the options is the set of markers that count as us. Ranges are `:from`/`:to`,
  one-based and inclusive, in the numbering an editor and an agent already share.

  Adjacent lines that earn the same verdict come back as one range. That collapse
  happens here, at the end, over final per-line values — not while folding — because
  a line's verdict is not settled until the last version has been replayed, and two
  lines that agree today may have got there by different routes that a later version
  will tell apart again."
  [history {:keys [ours]}]
  (->> (heritage history)
       (map-indexed (fn [i line] {:line (inc i) :caution (verdict ours (:source line))}))
       (partition-by :caution)
       (mapv (fn [run] {:from (:line (first run))
                        :to (:line (last run))
                        :caution (:caution (first run))}))))
