(ns et.uvt.cli-test
  "The one thing in the tool that is a decision rather than plumbing: given the
  committers a file actually has, and whichever side you bothered to name, who
  counts as us."
  (:require [clojure.test :refer [deftest testing is]]
            [et.uvt.cli :as cli]))

(def authors ["dan" "claude" "someone-new"])

(deftest audience-test
  (testing "naming the machines leaves everyone else a person"
    ;; The way round that costs least when you are wrong. A committer nobody has
    ;; classified — a new colleague, a rename, an agent you have not met — comes out
    ;; as one of us, so the mistake is an agent being needlessly careful with its
    ;; own work rather than editing yours freely.
    (is (= #{"dan" "someone-new"}
           (cli/audience authors {:theirs #{"claude"}}))))

  (testing "naming us instead makes it a guest list, and nobody else is on it"
    ;; The other way round, and deliberately not softened: say who we are and you
    ;; have said it. `someone-new` is not on the list, so they are not on the list.
    ;; This is the flag to reach for when the humans are few and the machines are
    ;; many or unnamed.
    (is (= #{"dan"}
           (cli/audience authors {:ours #{"dan"}}))))

  (testing "naming both is refused, not resolved"
    ;; The two flags disagree about what to do with everyone unnamed — the blacklist
    ;; says treat them as people, the guest list says they are not on it — so there
    ;; is no reading of both together that is not a silent choice between them. Any
    ;; rule picked here would be arbitrary and invisible, and the one thing worse
    ;; than being asked to say it again is being told confidently the wrong thing
    ;; about who wrote what.
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"not both"
                          (cli/audience authors {:ours #{"dan"} :theirs #{"claude"}}))))

  (testing "naming nobody, everyone is a person"
    (is (= #{"dan" "claude" "someone-new"}
           (cli/audience authors {})))))
