(ns et.uvt.caution-test
  "The seed tests for the layer we actually want to think from: not *whose is this
  line* but *how careful should an agent be here*, over a whole text.

  Each history is written out flat — `# v1 Agent` and then that version's lines —
  because what a test here turns on is which line moved between two versions, and
  that is visible when the versions stack up and invisible in a vector of escaped
  strings. See `et.uvt.test-helpers`."
  (:require [clojure.test :refer [deftest testing is]]
            [et.uvt.caution :as caution]
            [et.uvt.test-helpers :as h]))

(deftest assess-test
  (testing "a history replayed to the end, in ranges, with the agent's own work marked out"
    ;; Three versions and not two, which is the point. Attributing only the last
    ;; pair would see a text whose previous version was written by the agent, and
    ;; would hand the agent's source to every line that survived into the last one
    ;; — including the two we wrote at the start and nobody has touched since. A
    ;; pair can only ever see one change back; the answer to *how careful* has to
    ;; see all the way down.
    (is (= [{:from 1 :to 2 :caution 1.0}
            {:from 3 :to 3 :caution 0.0}
            {:from 4 :to 4 :caution 1.0}]
           (caution/assess
            (h/history "
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
            {:ours #{:human}})))))

(deftest contamination-test
  (testing "an agent line landing inside our island joins it and dilutes it, rather than splitting it"
    ;; Agent text, three lines. We replace the middle one with three of our own —
    ;; an island. Then the agent puts one line down inside that island.
    ;;
    ;; The island holds. Splitting it would make that one line an island of its
    ;; own at 0 — free to edit — at the very spot where it is most tangled up in
    ;; our work, which is backwards: a line sitting inside something we built
    ;; should inherit our caution, because changing it can break what surrounds
    ;; it. So it takes the island's value rather than its author's, and the
    ;; island's value falls to the share of it that is still ours.
    ;;
    ;; Falls to `0.9`, not to three-quarters, because that share is weighted: a
    ;; line of ours counts for three of theirs, so their one line among our three
    ;; barely moves it. What the weight is *for* is the mirror of this case, which
    ;; is the test below.
    ;;
    ;; This is also the first thing that puts a line strictly between the two ends
    ;; of the scale, which is what the scale was for.
    (is (= [{:from 1 :to 1 :caution 0.0}
            {:from 2 :to 5 :caution 0.9}
            {:from 6 :to 6 :caution 0.0}]
           (caution/assess
            (h/history "
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
            {:ours #{:human}})))))

(deftest mirrored-contamination-test
  (testing "our line landing inside their island is not made cheap by its neighbours"
    ;; The case above, turned around: our text, the agent replaces the middle of
    ;; it with three lines of its own, and then we put one line down inside that.
    ;;
    ;; Everything about the mechanism is symmetric — `settle` never so much as
    ;; looks at a source — so without the weight this island would come out at
    ;; `0.25`, the exact mirror of `0.75`. That number would say an agent may
    ;; fairly freely change a line we had *just written by hand*, on the strength
    ;; of nothing but what surrounds it. Which is the founding principle inverted.
    ;;
    ;; The weight is the one place that asymmetry lives, and it is why this reads
    ;; `0.5`: contested ground, be careful, rather than theirs to do with as they
    ;; like. Note it is still a dilution and not a floor — the agent's three lines
    ;; are held at `0.5` too, because the island is one thing and carries one
    ;; value. A per-line floor would have split it.
    (is (= [{:from 1 :to 1 :caution 1.0}
            {:from 2 :to 5 :caution 0.5}
            {:from 6 :to 6 :caution 1.0}]
           (caution/assess
            (h/history "
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
            {:ours #{:human}})))))
