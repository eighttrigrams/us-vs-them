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
alpha
beta
# v2 Agent
alpha
beta
gamma
# v3 Human
alpha
beta
gamma
delta
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
