(ns et.uvt.caution-test
  "The seed test for the layer we actually want to think from: not *whose is this
  line* but *how careful should an agent be here*, over a whole text.

  The history is three versions and not two, which is the point. Attributing only
  the last pair would see a text whose previous version was written by the agent,
  and would hand the agent's source to every line that survived into the last one —
  including the two the human wrote at the start and nobody has touched since. A
  pair can only ever see one change back; the answer to *how careful* has to see all
  the way down.

  The verdicts are two because only two are reachable yet. `:sacred` here means no
  more than *the last hand on this line was ours*, and `:up-for-grabs` that it was
  theirs. The middle of the spectrum — a line of mixed heritage, a human edit old
  enough to have stopped mattering — needs accumulation and recency, and there is
  nothing here to produce it yet."
  (:require [clojure.test :refer [deftest testing is]]
            [et.uvt.caution :as caution]))

(deftest assess-test
  (testing "a history replayed to the end, in ranges, with the agent's own work marked out"
    (is (= [{:from 1 :to 2 :caution :sacred}
            {:from 3 :to 3 :caution :up-for-grabs}
            {:from 4 :to 4 :caution :sacred}]
           (caution/assess [{:text "alpha\nbeta"                    :source :human}
                            {:text "alpha\nbeta\ngamma"             :source :agent}
                            {:text "alpha\nbeta\ngamma\ndelta"      :source :human}]
                           {:ours #{:human}})))))
