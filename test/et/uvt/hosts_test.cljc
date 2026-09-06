(ns et.uvt.hosts-test
  "The two hosts, and what the port had to find out about them.

  `caution_test` is the specification of what the numbers mean and it is
  deliberately silent about where they are computed — every case in it reads the
  same on a JVM and in a browser, which is the whole claim the `.cljc` port
  makes. This file is the other half of that claim: the three places where the
  hosts *could* have parted company, asked directly, so that a divergence shows
  up as a red test here rather than as a wrong colour on somebody's Recipe.

  Cookbook is why it matters. Since its bodies are sealed, the split over an
  unpublished Recipe is computed in the browser and the split over a published
  one is still computed on the server — two hosts answering the same question
  about the same kind of text, which is exactly the arrangement this repo's
  README warns about in the other direction. What holds them together is that
  the arithmetic is one source file. What could still come apart is arithmetic
  the *language* spells differently, and that is what is written down below."
  (:require #?(:clj  [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]])
            [et.uvt.caution :as caution]))

(def ^:private ours {:ours #{:human}})

(deftest a-trailing-newline-is-a-line
  ;; `core/lines` splits with a limit of `-1` so that a text ending in a newline
  ;; comes out one line longer than one that does not. That is the single line
  ;; of this library the port had most reason to doubt: Java's `split` and
  ;; JavaScript's disagree about trailing empties by default, and agree only
  ;; because a negative limit means "keep them" in both.
  ;;
  ;; It is not a detail. Most bodies typed into a textarea end in a newline, and
  ;; a host that swallowed the last line would hand back ranges one short — an
  ;; off-by-one at the bottom of the text, silent, and in the direction that
  ;; leaves the final line with no attribution at all.
  (testing "a text ending in a newline has an empty last line, and it is counted"
    (is (= [{:from 1 :to 3 :caution 1.0}]
           (caution/assess [{:text "one\ntwo\n" :source :human}] ours))))
  (testing "and one that does not, does not"
    (is (= [{:from 1 :to 2 :caution 1.0}]
           (caution/assess [{:text "one\ntwo" :source :human}] ours))))
  (testing "the empty text is one line and not none"
    (is (= [{:from 1 :to 1 :caution 1.0}]
           (caution/assess [{:text "" :source :human}] ours)))))

(deftest an-island-of-both-sides-is-a-fraction
  ;; Two lines of his, one of theirs dropped between them: the run is smaller
  ;; than the island and smaller than the piece it would strand, so it is
  ;; absorbed, and the island is two thirds his. `caution_test` never lands on a
  ;; value like this — every case there comes out `0.0`, `1.0` or `0.5`, all of
  ;; which are exact in binary and cannot tell the hosts apart.
  ;;
  ;; **Two thirds can, and does.** `caution-of` is `(double (/ ours all))`. On
  ;; the JVM that division makes a `Ratio` and `Ratio.doubleValue` goes through
  ;; a 16-significant-digit `BigDecimal`, so `2/3` arrives as
  ;; `0.6666666666666667`. ClojureScript has no ratios: `(/ 2 3)` is IEEE
  ;; division and `double` is a no-op, so the same expression is
  ;; `0.6666666666666666`, one unit in the last place away — and it is the JVM
  ;; that is the odd one out, since the JavaScript answer is the nearest double
  ;; to two thirds and the JVM's is not.
  ;;
  ;; Left as it is rather than made to agree, because making them agree means
  ;; changing one host's numbers, and the JVM's are what cookbook's server has
  ;; been serving. It is pinned here instead, both spellings, so that the
  ;; divergence is a thing this repo has written down rather than a thing a
  ;; reader of a JSON body one day notices.
  ;;
  ;; Nothing downstream can see it. Ranges are collapsed with `partition-by`
  ;; over one host's own values, so the boundaries are wherever the underlying
  ;; fractions differ, and two fractions this coarse are millions of ulps apart.
  ;; Both surfaces that show the number round it to two decimals.
  (is (= [{:from 1 :to 3
           :caution #?(:clj 0.6666666666666667 :cljs 0.6666666666666666)}]
         (caution/assess [{:text "h1\nh2" :source :human}
                          {:text "h1\na1\nh2" :source :agent}]
                         ours))))

(deftest the-fold-runs-the-same-way-round-on-both
  ;; The one mistake that returns a well-formed answer, asked here because the
  ;; browser is a second caller and a caller is what gets the order wrong. Not a
  ;; host difference — a host-independent statement, which is the point of
  ;; having it in this file: it is the assertion that would go red if the port
  ;; had quietly reordered anything.
  (is (= [{:from 1 :to 1 :caution 1.0}
          {:from 2 :to 2 :caution 0.0}]
         (caution/assess [{:text "his line" :source :human}
                          {:text "his line\ntheir line" :source :agent}]
                         ours))))
