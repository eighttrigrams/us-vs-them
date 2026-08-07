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

;; A file's history does not begin where its name does, and asking git for a path
;; alone says otherwise: `git log -- <path>` stops at the rename, so everything
;; written under the old name is gone and the rename itself reads as the moment the
;; whole file came into being. Rename someone's file and every line in it becomes
;; yours — which is the exact failure this library exists to prevent.
;;
;; `--follow` is git's answer, and it brings a second problem with it: the file did
;; not have today's name back then, so a revision has to carry the name it had or
;; there is nothing to fetch. `--name-only` says what that was, per commit.
;;
;; Newest first, and reversed here rather than by `--reverse`, because git quietly
;; gives up on following a rename when both are asked for and returns the one
;; commit — a truncation that looks exactly like a file with no history.

;; The record separator git is asked to put in front of each commit. Nothing else
;; in the output can be it, which a tab or a newline cannot promise: a path may
;; contain either.
(def ^:private rs "\u0001")

(def renamed-log
  (str rs "sha2\tclaude@example.net\n\nnew-name.sh\n"
       rs "sha1\tdan@example.net\n\nold-name.sh\n"))

(deftest revisions-test
  (testing "a renamed file keeps its history, and every revision the name it had then"
    (is (= [{:sha "sha1" :path "old-name.sh" :source "dan@example.net"}
            {:sha "sha2" :path "new-name.sh" :source "claude@example.net"}]
           (cli/revisions renamed-log)))))
