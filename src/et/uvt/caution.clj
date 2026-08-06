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

  **It joins the island around it** when it is theirs, and landed strictly inside
  one — the same island above and below — and that island still outweighs it. Then
  the island holds its identity and merely gets diluted, which is the whole reason
  islands are here.

  **Otherwise it becomes an island of its own.** That covers a run at either end of
  the text, a run between two different islands, a run so large that calling it an
  insertion would be a fiction — three lines dropped into a three-line island is not
  a dilution of that island, it is a new one — and, the asymmetric case, *any run of
  ours*.

  That last clause is the one place the two sides are treated differently, and it is
  where the metaphor is taken at its word. Their line landing in our island is
  contamination: we keep hold of it, so that it cannot be edited freely in the
  middle of our work. Our line landing in theirs is a landing and not an
  assimilation — it stays ours, whole, because nothing we write by hand is made
  cheap by what happens to surround it. Absorption runs one way only.

  Everything else here is blind to whose a line is; only this is not."
  [ours lines]
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
                                  (> (sizes above) (count run))
                                  (not (contains? ours (:source (first run)))))
                         above)]
              (recur (inc i)
                     (if host id (inc id))
                     (into out (map #(assoc % :island (or host id)) run))))))))))

(defn- side
  "Which side an island is on — `true` ours, `false` theirs, `nil` if it holds both."
  [ours island]
  (let [sides (set (map #(contains? ours (:source %)) island))]
    (when (= 1 (count sides))
      (first sides))))

(defn- coalesce
  "Make one island of two that have come to lie against each other with nothing
  between them and nothing to tell them apart.

  Islands are born separate because something separated them — our line went down
  inside their block, so it was its own island and not part of theirs. Delete the
  block and that reason is gone: two stretches of ours now run straight into one
  another, and there is nothing left that made them two.

  **Only unmixed islands of the same side merge**, which is exactly the condition
  under which merging cannot change what either one is worth. Both stand at `1`, or
  both at `0`, before and after. So this never moves a number by itself — it only
  changes what happens next, because absorption asks for the *same* island above and
  below and weighs itself against that island's size. Two of our islands lying
  side by side will take nothing in between them; one island of the same lines
  will.

  Merging into the lower id keeps this from oscillating, and each pass strictly
  reduces the number of islands, so the repeat terminates."
  [ours lines]
  (let [sides (update-vals (group-by :island lines) #(side ours %))
        merges (into {}
                     (for [[a b] (partition 2 1 (partition-by :island lines))
                           :let [ia (:island (first a))
                                 ib (:island (first b))]
                           :when (and (some? (sides ia))
                                      (= (sides ia) (sides ib)))]
                       [(max ia ib) (min ia ib)]))]
    (if (empty? merges)
      lines
      (recur ours (mapv #(update % :island (fn [id] (get merges id id))) lines)))))

(defn- heritage
  "Every line of the newest version, in its island, by replaying the history from
  the beginning. Each version is diffed against what the fold has built so far and
  then settled and coalesced, in that order and inside the fold rather than after
  it. An island can only be recognised at the moment it forms, and its size decides
  what the *next* version is allowed to put inside it, so neither step can wait for
  the end."
  [ours [oldest & later]]
  (let [step (fn [lines] (coalesce ours (settle ours lines)))]
    (reduce (fn [lines version] (step (core/attribute lines version)))
            (step (core/attribute oldest))
            later)))

(defn- caution-of
  "Each island's caution, on a scale from `0` to `1` — `1` sacred, `0` up for grabs.

  A number rather than a name, because caution is a spectrum and a name has no
  middle to put anything in. The share of an island's lines that are ours is what
  puts it in that middle: wholly ours it stands at `1`, and every line of theirs
  landing in it moves it down without ever quite reaching `0`, which is the right
  shape — a place we made does not become theirs outright by being edited.

  A plain share, unweighted, because the asymmetry is already spent in `settle`. It
  was tempting to put it here instead — to count a line of ours for three of theirs
  — and that works, but it buys the same protection twice and leaves an arbitrary
  number lying in the arithmetic. Only their lines are ever absorbed, so an island
  of theirs cannot be lifted by ours arriving, and there is nothing left for a
  weight to defend against.

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
  (let [lines (heritage ours history)
        value (caution-of ours lines)]
    (->> lines
         (map-indexed (fn [i line] {:line (inc i) :caution (value (:island line))}))
         (partition-by :caution)
         (mapv (fn [run] {:from (:line (first run))
                          :to (:line (last run))
                          :caution (:caution (first run))})))))
