(ns et.uvt.core-test
  "The seed test for the whole library: one human version, one agent version, and
  the question of which lines of the result are whose.

  The case chosen is an **insertion**, and deliberately so. A version pair could be
  attributed by walking both texts position by position, and that would answer the
  easy case — line 2 was replaced, so line 2 is theirs. It would also be wrong the
  moment a line is added or removed, because everything below the seam shifts and a
  positional walk hands the agent lines it never touched. Handing an agent the whole
  tail of a human's file, labelled agentic, is exactly the failure this library
  exists to prevent, so the first test rules it out rather than leaving it for later."
  (:require [clojure.test :refer [deftest testing is]]
            [et.uvt.core :as uvt]))

(deftest attribute-test
  (testing "a line the agent inserted is theirs; the human's lines around it stay his"
    (is (= [{:text "alpha"    :source :human}
            {:text "new line" :source :agent}
            {:text "beta"     :source :human}]
           (uvt/attribute {:text "alpha\nbeta" :source :human}
                          {:text "alpha\nnew line\nbeta" :source :agent})))))
