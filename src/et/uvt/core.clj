(ns et.uvt.core
  "Attributing the lines of a text to whoever put them there.

  A **version** here is `{:text \"…\" :source <marker>}` — a whole text, and one
  marker saying where that whole text's change came from. That is the shape the
  surrounding system already has: a commit and its author, a version row and its
  `source` column. The marker is opaque to us. We never ask whether it means a human
  or an agent, only whether two of them are the same, so a caller can spell the two
  sides however it already spells them.

  A **line** of the result is `{:text \"…\" :source <marker>}` — one text's worth of
  lines, each carrying the source it actually came from rather than the one marker
  the whole version arrived under.

  This namespace has no test of its own. `caution` is the only caller and its test
  is the only spec, so nothing lives here that the fold above does not ask for."
  (:require [clojure.string :as str]))

(defn- lines
  "The lines of a text, as a vector. Splitting rather than parsing, because a line is
  the unit the caller reasons about — an agent is told 'be careful around 5–17' —
  and it is the unit a diff aligns on.

  A limit of `-1` so a trailing newline yields a trailing empty line rather than
  being swallowed. Whitespace is text, and a change to it is a change."
  [text]
  (str/split text #"\n" -1))

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

  A surviving line comes back as **the very map it already was**, untouched. That is
  what carries anything a caller has hung on it — an island, a weight — across the
  fold without this namespace having to know the key exists. A new line comes back
  as `{:text :source}`, which is also how a caller tells that it is new, plus
  `:displaced` when the change that brought it also took lines away in the same
  place.

  **`:displaced` is what a replacement leaves behind.** Deleted lines are gone from
  the result, and with them every trace of where they had belonged — so a run that
  replaced somebody's paragraph looks exactly like a run that appeared out of
  nowhere between two other things. It is not the same, and the difference decides
  whether the change landed *inside* a stretch or beside it. So each new line
  carries whatever this namespace's own `:island` key held on the lines removed
  around it, and a caller that hangs no islands on anything simply never sees the
  key.

  One argument is the degenerate case: a lone version, its source spread over its own
  lines, which is where a fold over a history starts."
  ([{:keys [text source]}]
   (mapv (fn [line] {:text line :source source}) (lines text)))
  ([before {after :text after-source :source}]
   (let [a before
         a-text (mapv :text a)
         b (lines after)
         table (lcs-table a-text b)
         ;; One unaligned stretch of the walk, handed over to the result. Its added
         ;; lines and its deleted lines are the two halves of the same edit, so the
         ;; islands the deletions belonged to go onto the lines that took their
         ;; place. Held back until the stretch closes because, walking backwards,
         ;; the deletions can still be to come.
         close (fn [acc added displaced]
                 (let [displaced (disj displaced nil)]
                   (concat (if (seq displaced)
                             (map #(assoc % :displaced displaced) added)
                             added)
                           acc)))]
     (loop [i (count a)
            j (count b)
            acc ()
            added ()
            displaced #{}]
       (cond
         (zero? j)
         (vec (close acc added (into displaced (map :island) (take i a))))

         (and (pos? i) (= (nth a-text (dec i)) (nth b (dec j))))
         (recur (dec i) (dec j) (conj (close acc added displaced) (nth a (dec i))) () #{})

         ;; Not aligned here, so one side moves. Prefer stepping back through `after`
         ;; — calling this line an addition — unless dropping a line of `before`
         ;; instead leaves a longer agreement below, in which case that deletion is
         ;; the better reading of what the change did.
         (and (pos? i)
              (> (nth (nth table (dec i)) j)
                 (nth (nth table i) (dec j))))
         (recur (dec i) j acc added (conj displaced (:island (nth a (dec i)))))

         :else
         (recur i (dec j) acc
                (conj added {:text (nth b (dec j)) :source after-source})
                displaced))))))
