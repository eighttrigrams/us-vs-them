(ns et.uvt.cli
  "Asking the question of a file in a git repository, from a shell.

  The library needs a history of versions each carrying a provenance marker, and a
  git repository is already exactly that: every revision of a file, in order, each
  with the author of the change that made it. Nothing has to be recorded specially
  and nothing can drift, because git recorded it at the time.

  **Being human is the default.** You name the machines with `--theirs` and everyone
  else is us. That is the right way round: a new committer nobody has classified
  should be treated as a person until someone says otherwise, because the failure
  that costs something is an agent editing your work freely, not an agent being
  needlessly careful with its own.

  None of the arithmetic lives here; that is all `caution`'s. What is left is two
  decisions that turned out to be worth a test each — who counts as us, and where a
  file's history actually begins.

  Runs under babashka, which is why nothing in the library reaches for Java."
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]
            [et.uvt.caution :as caution]))

(defn- git
  "One git command, or out with the message it gave."
  [& args]
  (let [{:keys [exit out err]} (apply shell/sh "git" args)]
    (when-not (zero? exit)
      (binding [*out* *err*]
        (println (str/trim err)))
      (System/exit 1))
    out))

;; Put in front of each commit so the commit lines can be told from the file names
;; interleaved between them. A control character because nothing else will do: a
;; path may contain a tab, and it may contain a newline.
(def ^:private rs "\u0001")

(defn revisions
  "Parse `git log --follow --name-only` into revisions, oldest first — a sha, the
  email of whoever committed it, and **the path the file had at that commit**.

  A file's history does not begin where its name does. `git log -- <path>` stops at
  the rename, so everything written under the old name is lost and the rename reads
  as the moment the whole file came into being — rename someone's file and every
  line in it becomes yours, which is the exact failure this library exists to
  prevent. `--follow` is git's answer.

  It brings the path along with it. The file did not have today's name back then, so
  each revision has to carry the name it had or there is nothing for `git show` to
  fetch; `--name-only` is what says so, per commit.

  And the order is reversed here rather than by `--reverse`, because asking git for
  both quietly gives up on following the rename and returns the single commit — a
  truncation indistinguishable from a file with no history."
  [log]
  (->> (str/split log (re-pattern rs))
       (remove str/blank?)
       (map (fn [block]
              (let [[header & files] (str/split-lines block)
                    [sha email] (str/split header #"\t")]
                {:sha sha
                 :source email
                 :path (first (remove str/blank? files))})))
       reverse
       vec))

(defn- versions
  "Every revision of `path`, oldest first, each marked with the email of whoever
  committed it.

  The trailing newline goes, because git keeps one and it would otherwise show up as
  an empty last line in every version and put every line number one out."
  [path]
  (mapv (fn [{:keys [sha source] then :path}]
          {:text (str/replace (git "show" (str sha ":" then)) #"\n\z" "")
           :source source})
        (revisions (git "log" "--follow" (str "--format=" rs "%H%x09%ae")
                        "--name-only" "--" path))))

(defn audience
  "Which of a file's committers count as us.

  You name whichever side is the shorter list, and the two flags are not mirror
  images of each other — they read differently on purpose.

  `--theirs` is a **blacklist**: these are agents, everyone else is a human. A
  committer nobody has classified comes out as one of us, which is the way round
  that costs least when you are wrong — the mistake is an agent being needlessly
  careful with its own work rather than editing yours freely.

  `--ours` is a **guest list**: these are the people, and saying it means saying it.
  Nobody unnamed gets on. That is the flag for when the humans are few and the
  agents are many or nameless, and it is not softened by the default above,
  because a guest list that quietly admits strangers is not one.

  **Naming both is refused, not resolved.** The two disagree precisely about
  everyone unnamed — the blacklist says treat them as people, the guest list says
  they are not on it — so there is no reading of the pair that is not a silent
  choice between them. Any rule picked here would be arbitrary and invisible, and
  the one thing worse than being asked to say it again is being told confidently the
  wrong thing about who wrote what."
  [authors {:keys [ours theirs]}]
  (when (and (seq ours) (seq theirs))
    (throw (ex-info "Name either --ours or --theirs, not both." {:ours ours :theirs theirs})))
  (into #{} (remove (set theirs)) (if (seq ours) ours authors)))

(defn- report
  [ranges]
  (doseq [{:keys [from to caution]} ranges]
    (println (format "%-12s %.2f"
                     (if (= from to) (str from) (str from "-" to))
                     caution))))

(defn- usage []
  (println "usage: us-vs-them [--ours EMAIL]... [--theirs EMAIL]... FILE")
  (println)
  (println "  Ranges of FILE, each with how careful an agent should be in it —")
  (println "  1 sacred, 0 up for grabs — read off the file's git history.")
  (println)
  (println "  Name whichever side is the shorter list. Repeat either flag.")
  (println)
  (println "  --theirs EMAIL   a committer that is an agent. Everyone not")
  (println "                   named is taken to be a human.")
  (println "  --ours EMAIL     a committer that is a human, and a guest list:")
  (println "                   nobody unnamed gets on it.")
  (println)
  (println "  One or the other, not both — they disagree about everyone unnamed."))

(defn -main [& args]
  (loop [args args
         sides {}]
    (cond
      (empty? args)
      (do (usage) (System/exit 1))

      (#{"--ours" "--theirs"} (first args))
      (recur (drop 2 args)
             (update sides (keyword (subs (first args) 2)) (fnil conj #{}) (second args)))

      (#{"-h" "--help"} (first args))
      (usage)

      :else
      (let [file (first args)]
        ;; Checked before a single git call, because a contradiction in the flags
        ;; is not something a file's history could settle.
        (try
          (audience [] sides)
          (catch clojure.lang.ExceptionInfo e
            (binding [*out* *err*] (println (ex-message e)))
            (System/exit 1)))
        (let [path (str/trim (git "ls-files" "--full-name" "--" file))
              history (versions path)]
          (if (empty? history)
            (binding [*out* *err*]
              (println (str "no git history for " file))
              (System/exit 1))
            (report (caution/assess history
                                    {:ours (audience (map :source history) sides)}))))))))
