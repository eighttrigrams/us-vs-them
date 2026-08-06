(ns et.uvt.core
  "Attributing the lines of a text to whoever put them there.

  A **version** here is `{:text \"…\" :source <marker>}` — a whole text, and one
  marker saying where that whole text's change came from. That is the shape the
  surrounding system already has: a commit and its author, a version row and its
  `source` column. The marker is opaque to us. We never ask whether it means a human
  or an agent, only whether two of them are the same, so a caller can spell the two
  sides however it already spells them.

  A **line** of the result has the very same shape, and that is the whole move: an
  attribution takes one text carrying one source and gives back its lines, each
  carrying the source it actually came from. Because the two shapes agree, the
  result of attributing a pair is the kind of thing that can be attributed against
  the next version in turn — which is how a whole history will eventually be folded
  into one answer.

  Right now there is one function and it handles one pair. What it does not yet do
  is the gradient: a line here is simply *whose*, where the point of the library is
  ultimately *how much* whose, and how long ago. That arrives when there are tests
  for it.")

(defn- lines
  "The lines of a text, as a vector. Splitting rather than parsing, because a line is
  the unit the caller reasons about — an agent is told 'be careful around 5–17' —
  and it is the unit a diff aligns on."
  [text]
  (if (= "" text)
    []
    (vec (.split ^String text "\n" -1))))

(defn- attributed
  "The `before` side, as attributed lines.

  A version — one text under one source — spreads that source over every line it
  has. An attribution is already a sequence of attributed lines and is taken as it
  stands, each line keeping the source it earned. That second case is what makes the
  fold possible: one attribution becomes the `before` of the next version, and lines
  that keep surviving keep the source they had, however far back it was set."
  [before]
  (if (map? before)
    (mapv (fn [line] {:text line :source (:source before)})
          (lines (:text before)))
    (vec before)))

(defn- lcs-table
  "The classic longest-common-subsequence length table over two vectors of lines,
  as a vector of rows, `(inc (count a))` by `(inc (count b))`.

  This is what buys us alignment instead of a positional walk: the longest run of
  lines the two versions agree on is the run that *survived* the change, and
  everything else is what the change did. Quadratic in both dimensions, which is
  fine at the size of a source file and is a thing to revisit when it is not."
  [a b]
  (let [m (count b)]
    (reduce (fn [rows i]
              (let [above (peek rows)
                    row (reduce (fn [row j]
                                  (conj row
                                        (if (= (nth a i) (nth b j))
                                          (inc (nth above j))
                                          (max (nth above (inc j)) (peek row)))))
                                [0]
                                (range m))]
                (conj rows row)))
            [(vec (repeat (inc m) 0))]
            (range (count a)))))

(defn attribute
  "The lines of `after`, each with the source it came from.

  A line that also stands in `before` — in the sense that the diff aligns the two —
  is attributed to `before`'s source: the later change left it alone, so it is still
  whoever's it was. A line that only stands in `after` is attributed to `after`'s
  source, because that change is where it came into being. Lines that were in
  `before` and are gone are simply not in the result; there is no line left to be
  careful about.

  Note what the attribution of a surviving line is **not**: it is not a claim that
  `before`'s source wrote it. It is a claim that `before` is as far back as this
  function can see. Folding the version before that one in is what pushes the answer
  further back, and it is why `before` may itself be an attribution — pass one, and
  a surviving line keeps whatever source it already had rather than collapsing to a
  single marker for the whole of the earlier text.

  One argument is the degenerate case: a lone version, its source spread over its
  own lines, which is where a fold over a history starts."
  ([before]
   (attributed before))
  ([before {after :text after-source :source}]
   (let [a (attributed before)
         a-text (mapv :text a)
         b (lines after)
         table (lcs-table a-text b)]
     (loop [i (count a)
            j (count b)
            acc ()]
       (cond
         (zero? j)
         (vec acc)

         (and (pos? i) (= (nth a-text (dec i)) (nth b (dec j))))
         (recur (dec i) (dec j) (conj acc (nth a (dec i))))

         ;; Not aligned here, so one side moves. Prefer stepping back through `after`
         ;; — calling this line an addition — unless dropping a line of `before`
         ;; instead leaves a longer agreement below, in which case that deletion is
         ;; the better reading of what the change did.
         (and (pos? i)
              (> (nth (nth table (dec i)) j)
                 (nth (nth table i) (dec j))))
         (recur (dec i) j acc)

         :else
         (recur i (dec j) (conj acc {:text (nth b (dec j)) :source after-source})))))))
