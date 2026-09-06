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

(defn- strands
  "How much of `island` the run at `i` would leave on its smaller side, or `nil` if
  the run is not between two pieces of it at all.

  Counted over the island's own lines rather than the text between them, because an
  island is not contiguous: another island's block may sit in the middle of one and
  neither piece is thereby smaller."
  [runs i island]
  (let [of-island (fn [rs] (count (filter #(= island (:island %)) (mapcat identity rs))))
        above (of-island (subvec runs 0 i))
        below (of-island (subvec runs (inc i)))]
    (when (and (pos? above) (pos? below))
      (min above below))))

(defn- settle
  "Put every line that has just arrived into an island.

  A line back from `core/attribute` with no `:island` is one this change brought
  into being; a line that kept its island survived. New lines come in contiguous
  runs, one run per place the change touched, and each run goes one of two ways.

  **It joins the island around it** when it is theirs, landed inside one, and is
  outweighed there. Then the island holds its identity and merely gets diluted,
  which is the whole reason islands are here.

  *Inside* is answered two ways, and the second matters more than the first. The
  same island above and below is the obvious evidence, and it is all a pure
  insertion leaves. But most edits are replacements, and a replacement's own
  neighbours may be anything at all — rewrite the last paragraph of somebody's
  section and what sits below it is whatever came next in the file. What that run
  did is written in `:displaced`: it took away lines, and those lines belonged
  somewhere. A run that turned out a stretch of an island landed in that island,
  whatever it happens to abut.

  **Outweighs what it was, not what is left of it.** The size to beat comes from
  before the change, because the deletion and the insertion are one edit and
  counting the remnant charges the island twice for it — six lines replacing six of
  thirteen would be read as an even match against the seven survivors, when it is
  plainly a dilution of a thirteen-line island.

  **And outweighed by the piece it would strand, not only by the whole.** Weighing
  against the aggregate alone is what makes a long-lived file converge: no single
  edit ever beats the island, so everything is absorbed, and after a hundred
  versions one island covers the file at some middling number — a true statement
  about the file and a useless one about any part of it.

  What was never pinned down is *which* island does the outweighing. Not the
  aggregate: what is at stake when something lands in the middle is the smaller of
  the two pieces it would cut the island into. A line dropped in the middle of your
  paragraph cannot be edited without touching your work, so you keep hold of it. A
  block bigger than the land it separates is not a stain on anything — it has its
  own beginning and its own end, and it can be rewritten whole without disturbing a
  line of yours. So the island parts around it and it becomes water. A run at the
  end of an island strands nothing and cuts nothing, and this says nothing about it.

  **Otherwise it becomes an island of its own.** That covers a run at either end of
  the text, a run between two different islands that displaced nothing, a run so
  large that calling it an insertion would be a fiction — three lines dropped into a
  three-line island is not a dilution of that island, it is a new one — and, the
  asymmetric case, *any run of ours*.

  That last clause is the one place the two sides are treated differently, and it is
  where the metaphor is taken at its word. Their line landing in our island is
  contamination: we keep hold of it, so that it cannot be edited freely in the
  middle of our work. Our line landing in theirs is a landing and not an
  assimilation — it stays ours, whole, because nothing we write by hand is made
  cheap by what happens to surround it. Absorption runs one way only.

  Everything else here is blind to whose a line is; only this is not."
  [ours sizes lines]
  (let [runs (vec (partition-by :island lines))]
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
                  displaced (:displaced (first run))
                  inside (cond
                           (and above (= above below)) above
                           (= 1 (count displaced)) (first displaced))
                  stranded (when inside (strands runs i inside))
                  host (when (and inside
                                  (> (get sizes inside 0) (count run))
                                  (or (nil? stranded) (<= (count run) stranded))
                                  (not (contains? ours (:source (first run)))))
                         inside)]
              (recur (inc i)
                     (if host id (inc id))
                     (into out (map #(assoc % :island (or host id)) run))))))))))

(defn- deorphan
  "Take back the matches the diff should not have made.

  A line comes back from `core/attribute` still carrying its island when the
  alignment found it on both sides of the change. Usually that means what it says:
  the line was left alone. But an alignment works on strings, and some strings recur
  — a blank line, a lone `}`, an `end`. Replace a whole block of text with a whole
  block of your own and the diff will happily match the blank line in the middle of
  it, because a blank line does look exactly like a blank line. Your one edit comes
  back cut into three, with a line of theirs wedged in the middle holding the pieces
  apart and dragging them off `1`.

  So: **an isolated match is not a survival, if the line recurs.** A line that
  aligned while both its neighbours changed, and whose text turns up elsewhere in
  the document anyway, did not survive the edit — it coincided with it, and it is
  handed to the change like everything around it.

  That second clause is doing as much work as the first, and it was missing at
  first. Position alone cannot tell a coincidence from a survival: a sentence left
  standing in the middle of a rewritten passage sits in exactly the same place as a
  blank line the diff happened to match, and the rule without it confiscates real
  work. What separates them is that the sentence occurs *once*. A line that appears
  one time in a document and turns up again unchanged did survive — there is nothing
  else it could have been mistaken for. It is the line occurring forty times whose
  match proves nothing.

  Note what none of this mentions: whitespace, emptiness, length, or any property of
  the line's content. That is the point. Special-casing blank lines would be a guess
  about content dressed up as a rule; this is a statement about the alignment, and
  it catches a stray brace across a rewritten block just as well.

  Recurrence is counted in the new version only, where patience diff would ask it of
  both. Enough here, and it needs no more than what is in hand.

  Only runs of exactly one, which falls out of asking that *both* neighbours be new.
  Two surviving lines together are already a thin thread of agreement, but they are
  a thread; one is a coincidence. And never at either end of the text, where a line
  has only one neighbour and there is nothing to be isolated between."
  [source lines]
  (let [occurrences (frequencies (map :text lines))
        recurs? (fn [line] (> (occurrences (:text line)) 1))
        new? (fn [i] (and (contains? lines i)
                          (nil? (:island (nth lines i)))))]
    (into []
          (map-indexed (fn [i line]
                         (if (and (:island line)
                                  (recurs? line)
                                  (new? (dec i))
                                  (new? (inc i)))
                           {:text (:text line) :source source}
                           line)))
          lines)))

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
  (let [step (fn [before lines {:keys [source]}]
               (coalesce ours
                         (settle ours
                                 (frequencies (keep :island before))
                                 (deorphan source lines))))]
    (reduce (fn [lines version] (step lines (core/attribute lines version) version))
            (step [] (core/attribute oldest) oldest)
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
