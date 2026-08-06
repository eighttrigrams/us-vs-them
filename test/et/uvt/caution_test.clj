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
    ;; line of ours counts for three of theirs. Their one line among our three
    ;; barely moves it. Mirror the case and the weight tells: our one line among
    ;; their three lifts that island to `0.5`, contested, rather than leaving it
    ;; near enough theirs to edit freely — which is the whole point, since a line
    ;; we wrote by hand must not be made cheap by its neighbours.
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
