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

  **Caution is measured over islands, not lines.** A stretch of text written in one
  go is one thing, and it goes on being one thing while later changes push its
  borders about — seven lines grown to ten and cut back to six is still the island
  it was. So an island is given an identity when it forms and carries it through the
  fold, and a line's caution is its island's. That is what makes a change landing
  inside an island *dilute* it rather than cut it in two, which matters because
  cutting would leave the newly-arrived line freest to edit at exactly the spot
  where it is most entangled with what surrounds it.

  And the answer comes out as **ranges**, because that is the unit an agent can be
  told about. Per-line output would be technically the same information and useless
  in a prompt."
  (:require [et.uvt.core :as core]))

(defn- fresh-id
  "An island id nothing is using yet."
  [lines]
  (inc (reduce max -1 (keep :island lines))))

(defn- settle
  "Put every line that has just arrived into an island.

  A line back from `core/attribute` with no `:island` is one this change brought
  into being; a line that kept its island survived. New lines come in contiguous
  runs, one run per place the change touched, and each run goes one of two ways.

  **It joins the island around it** when it landed strictly inside one — the same
  island above and below — and that island still outweighs it. Then the island holds
  its identity and merely gets diluted, which is the whole reason islands are here.

  **Otherwise it becomes an island of its own.** That covers a run at either end of
  the text, a run between two different islands, and — the interesting case — a run
  so large that calling it an insertion would be a fiction. Three lines dropped into
  a three-line island is not a dilution of that island, it is a new one; thirty
  agent lines in the middle of our seven really is two fragments with a block
  between them. Where the line falls is the one number this rule turns on, and it is
  the only place a threshold lives."
  [lines]
  (let [runs (vec (partition-by :island lines))
        sizes (frequencies (keep :island lines))]
    (loop [i 0
           id (fresh-id lines)
           out []]
      (if (= i (count runs))
        out
        (let [run (nth runs i)]
          (if (:island (first run))
            (recur (inc i) id (into out run))
            (let [above (when (pos? i)
                          (:island (last (nth runs (dec i)))))
                  below (when (< (inc i) (count runs))
                          (:island (first (nth runs (inc i)))))
                  host (when (and above
                                  (= above below)
                                  (> (sizes above) (count run)))
                         above)]
              (recur (inc i)
                     (if host id (inc id))
                     (into out (map #(assoc % :island (or host id)) run))))))))))

(defn- heritage
  "Every line of the newest version, in its island, by replaying the history from
  the beginning. Each version is diffed against what the fold has built so far and
  then settled, because an island can only be recognised at the moment it forms."
  [[oldest & later]]
  (reduce (fn [lines version] (settle (core/attribute lines version)))
          (settle (core/attribute oldest))
          later))

(defn- caution-of
  "Each island's caution, on a scale from `0` to `1` — `1` sacred, `0` up for grabs.

  A number rather than a name, because caution is a spectrum and a name has no
  middle to put anything in. The share of an island's lines that are ours is what
  puts it in that middle: an island wholly ours stands at `1`, and every line of
  theirs that lands in it moves it down without ever quite reaching `0`, which is
  the right shape — a place we made does not become theirs outright by being edited.

  What this still does not weigh is *when*. A line of ours from a year ago counts
  exactly as much here as one from this morning."
  [ours lines]
  (update-vals (group-by :island lines)
               (fn [island]
                 (double (/ (count (filter (comp ours :source) island))
                            (count island))))))

(defn assess
  "Ranges of `history`'s newest text, each with how careful an agent should be in it.

  `history` is versions oldest first, each `{:text \"…\" :source <marker>}`; `:ours`
  in the options is the set of markers that count as us. Ranges are `:from`/`:to`,
  one-based and inclusive, in the numbering an editor and an agent already share.

  Adjacent lines that come out at the same value come back as one range. That
  collapse happens here, at the end, over final values — not while folding — because
  a line's value is not settled until the last version has been replayed."
  [history {:keys [ours]}]
  (let [lines (heritage history)
        value (caution-of ours lines)]
    (->> lines
         (map-indexed (fn [i line] {:line (inc i) :caution (value (:island line))}))
         (partition-by :caution)
         (mapv (fn [run] {:from (:line (first run))
                          :to (:line (last run))
                          :caution (:caution (first run))})))))
