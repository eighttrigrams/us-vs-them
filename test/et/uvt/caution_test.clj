(ns et.uvt.caution-test
  "The seed tests for the layer we actually want to think from: not *whose is this
  line* but *how careful should an agent be here*, over a whole text.

  Each history is written out flat above its test — `# v1 Agent` and then that
  version's lines — because what a test here turns on is which line moved between
  two versions, and that is visible when the versions stack up and invisible in a
  vector of escaped strings. The blocks have to sit flush left, so they live in
  their own `def` rather than halfway down an indented form. See
  `et.uvt.test-helpers`."
  (:require [clojure.test :refer [deftest testing is]]
            [et.uvt.caution :as caution]
            [et.uvt.test-helpers :as h]))

;; Three versions and not two, which is the point. Attributing only the last pair
;; would see a text whose previous version was written by the agent, and would hand
;; the agent's source to every line that survived into the last one — including the
;; two we wrote at the start and nobody has touched since. A pair can only ever see
;; one change back; the answer to *how careful* has to see all the way down.
(def replayed-history "
# v1 Human
h1
h2
# v2 Agent
h1
h2
a1
# v3 Human
h1
h2
a1
h3
")

(deftest assess-test
  (testing "a history replayed to the end, in ranges, with the agent's own work marked out"
    (is (= [{:from 1 :to 2 :caution 1.0}
            {:from 3 :to 3 :caution 0.0}
            {:from 4 :to 4 :caution 1.0}]
           (caution/assess (h/history replayed-history) {:ours #{:human}})))))

;; Agent text, three lines. We replace the middle one with three of our own — an
;; island. Then the agent puts one line down inside that island.
;;
;; The island holds. Splitting it would make that one line an island of its own at
;; 0 — free to edit — at the very spot where it is most tangled up in our work,
;; which is backwards: a line sitting inside something we built should inherit our
;; caution, because changing it can break what surrounds it. So it takes the
;; island's value rather than its author's, and the island's value falls to the
;; share of it that is still ours.
;;
;; Three of its four lines are ours, so `0.75`. The first thing that puts a line
;; strictly between the two ends of the scale, which is what the scale was for.
(def contamination-history "
# v1 Agent
a1
a2
a3
# v2 Human
a1
h1
h2
h3
a3
# v3 Agent
a1
h1
h2
x
h3
a3
")

(deftest contamination-test
  (testing "an agent line landing inside our island joins it and dilutes it, rather than splitting it"
    (is (= [{:from 1 :to 1 :caution 0.0}
            {:from 2 :to 5 :caution 0.75}
            {:from 6 :to 6 :caution 0.0}]
           (caution/assess (h/history contamination-history) {:ours #{:human}})))))

;; The case above, turned around: our text, the agent replaces the middle of it with
;; three lines of its own, and then we put one line down inside that.
;;
;; It does **not** get taken up into their island, and that is the one place the two
;; sides are treated differently. Absorption runs one way only. Their line landing
;; in our island is contamination, and we hold it close so that it cannot be edited
;; freely in the middle of our work. Our line landing in theirs is a landing, not an
;; assimilation: it stays wholly ours at `1`, because nothing we write by hand is
;; made cheap by what happens to surround it.
;;
;; So the text comes back interleaved rather than in one diluted block — their lines
;; still at `0`, ours standing at `1` in the middle of them. Their island is
;; untouched by our arrival; we simply did not join it.
(def landing-history "
# v1 Human
h1
h2
h3
# v2 Agent
h1
a1
a2
a3
h3
# v3 Human
h1
a1
a2
x
a3
h3
")

(deftest landing-test
  (testing "our line put down inside their island is an island of ours, not part of theirs"
    (is (= [{:from 1 :to 1 :caution 1.0}
            {:from 2 :to 3 :caution 0.0}
            {:from 4 :to 4 :caution 1.0}
            {:from 5 :to 5 :caution 0.0}
            {:from 6 :to 6 :caution 1.0}]
           (caution/assess (h/history landing-history) {:ours #{:human}})))))

;; Where `landing-history` left off, plus two more versions.
;;
;; In v4 we delete `a3`, and with it the last thing standing between our line `x`
;; and our original `h3`. Nothing separates them now, so they stop being two islands
;; and become one. That merge is invisible in v4's own answer — both read `1`
;; either way — and the only reason to make it is what it changes about v5.
;;
;; In v5 the agent puts a line down between them. Coalesced, it lands strictly
;; inside one island of ours, three lines against its one, and is absorbed: the
;; island dilutes to `0.75` and holds the new line close. Left as two islands it
;; would have been flanked by two *different* ones, formed an island of its own, and
;; sat there at `0` — freely editable, between two lines we wrote by hand.
;;
;; Note that `h1` comes down to `0.75` as well, though it is nowhere near the
;; agent's line. Islands are not contiguous: `h1` and `h3` have been one island
;; since v2 put the agent's block through the middle of them, and dilution is a
;; property of the island, not of the neighbourhood.
(def rejoining-history "
# v1 Human
h1
h2
h3
# v2 Agent
h1
a1
a2
a3
h3
# v3 Human
h1
a1
a2
x
a3
h3
# v4 Human
h1
a1
a2
x
h3
# v5 Agent
h1
a1
a2
x
y
h3
")

(deftest rejoining-test
  (testing "two islands of ours become one when what stood between them goes away"
    (is (= [{:from 1 :to 1 :caution 0.75}
            {:from 2 :to 3 :caution 0.0}
            {:from 4 :to 6 :caution 0.75}]
           (caution/assess (h/history rejoining-history) {:ours #{:human}})))))

;; The agent writes two paragraphs with a blank line between them. We replace both
;; paragraphs wholesale — but not the blank line, because we typed a blank line
;; there too.
;;
;; The diff matches it. It is the same string, so an alignment that works on strings
;; has no grounds to say otherwise, and our replacement comes back cut into three:
;; ours, a surviving line of theirs, ours. That one line then holds two islands
;; apart, and drags the section off `1` for no better reason than that an empty line
;; looks like an empty line.
;;
;; The rule that fixes it says nothing about blank lines, which is how you know it
;; is the right rule. **An isolated match is not a survival, if the line recurs.**
;; A line that aligned while both its neighbours changed, and whose text turns up
;; elsewhere in the document anyway, did not survive our edit — it coincided with
;; it. Nothing in that reasoning mentions whitespace; it would catch a stray `}` or
;; `end` matching across a rewritten block just the same.
;;
;; The document has blank lines in it more than once, which is the whole point and
;; the reason this test is shaped the way it is. A separator that occurs *once* in a
;; text is not evidence of nothing — matching it means something. It is the line
;; that turns up forty times whose match proves nothing at all.
(def coincidence-history "
# v1 Agent
intro

a1
a2

a3
a4

outro
# v2 Human
intro

h1
h2

h3
h4

outro
")

(deftest coincidence-test
  (testing "a recurring line that aligned while both its neighbours changed did not survive, it coincided"
    (is (= [{:from 1 :to 2 :caution 0.0}
            {:from 3 :to 7 :caution 1.0}
            {:from 8 :to 9 :caution 0.0}]
           (caution/assess (h/history coincidence-history) {:ours #{:human}})))))

;; And the other side of that rule, which is what stops it eating real work.
;;
;; We wrote three lines. The agent then rewrote the first and the third and left the
;; middle one exactly as it stood. By position it looks just like the blank line
;; above — an isolated match with both neighbours changed — and the rule as first
;; written would have handed it to the agent along with everything around it.
;;
;; But it is a sentence, and it occurs once. A line that appears one time in the
;; document and turns up again unchanged did survive; there is nothing else it could
;; have been mistaken for. So it is still counted as ours, and the island it is part
;; of comes out at `0.25` — one line in four.
;;
;; `0.25` and not `0` is the whole of what this test is for. Without the recurrence
;; clause the sentence would be handed to the agent along with everything around it,
;; the island would be theirs entire, and a line we wrote and never touched again
;; would read as free to edit.
;;
;; It does not stay at `1` on its own, and should not. Both agent runs replaced
;; lines of our island, so both landed in it; the island is one thing and carries
;; one value, and two thirds of it having been rewritten is exactly what `0.25` is
;; reporting.
(def survivor-history "
# v1 Human
h1
h2
h3
# v2 Agent
x1
h2
x2
x3
")

(deftest survivor-test
  (testing "a line that occurs once and came through unchanged really did survive"
    (is (= [{:from 1 :to 4 :caution 0.25}]
           (caution/assess (h/history survivor-history) {:ours #{:human}})))))

;; The case the README turned up, which flanking alone gets wrong.
;;
;; We write four lines. The agent adds two of its own below them — its island, ours
;; above it. Then the agent rewrites the *tail* of our island, the two lines that sat
;; right against its own block.
;;
;; By its neighbours that run is at a boundary: our island above, theirs below, no
;; single island around it. Flanking says it landed between two things and must be a
;; third. But it did not land between anything — it turned out two lines of ours and
;; sat down in their place. What a run displaced is direct evidence of where it
;; landed, and it beats the guess its neighbours offer.
;;
;; So our island holds, at `0.5`: four lines, two still ours. Without this it would
;; break in half, our two survivors left at `1` and the agent's rewrite sitting at
;; `0` beside them as if it had come from nowhere.
;;
;; And it is weighed against the island as it *was*, four lines, not against the two
;; that survived. Counting the remnant would charge the island twice for one edit —
;; a two-line replacement against two survivors reads as an even match, when it is
;; plainly a dilution of a four-line island.
(def replacement-history "
# v1 Human
h1
h2
h3
h4
# v2 Agent
h1
h2
h3
h4
a1
a2
# v3 Agent
h1
h2
x1
x2
a1
a2
")

(deftest replacement-test
  (testing "a run that displaced our lines landed in our island, whatever it abuts"
    (is (= [{:from 1 :to 4 :caution 0.5}
            {:from 5 :to 6 :caution 0.0}]
           (caution/assess (h/history replacement-history) {:ours #{:human}})))))

;; There needs to be a 'force' to counteract dilution and coalescing.
;; Since islands can be joined, and any agent change landing inside one
;; would otherwise merely become a dilution of an island, there must be a way
;; to break up islands again.
;;
;; When a range of agentic lines gets inserted into a human island
;; and that range consists of more lines than the lines left on the smaller side
;; the island gets split into two, one on each side of the new 'water'.
(def channel-history "
# v1 Human
h1
h2
h3
h4
h5
h6
# v2 Agent
h1
h2
h3
h4
a1
a2
a3
h5
h6
")

(deftest channel-test
  (testing "a block bigger than the land it strands is water, and the island parts around it"
    (is (= [{:from 1 :to 4 :caution 1.0}
            {:from 5 :to 7 :caution 0.0}
            {:from 8 :to 9 :caution 1.0}]
           (caution/assess (h/history channel-history) {:ours #{:human}})))))
