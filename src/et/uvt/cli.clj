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

  There is no logic in here worth a test — the arithmetic all lives in `caution`,
  and this only fetches the versions and prints the answer.

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

(defn- versions
  "Every revision of `path`, oldest first, each marked with the email of whoever
  committed it.

  The trailing newline goes, because git keeps one and it would otherwise show up as
  an empty last line in every version and put every line number one out."
  [path]
  (->> (str/split-lines (git "log" "--reverse" "--format=%H%x09%ae" "--" path))
       (remove str/blank?)
       (mapv (fn [line]
               (let [[sha email] (str/split line #"\t")]
                 {:text (str/replace (git "show" (str sha ":" path)) #"\n\z" "")
                  :source email})))))

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
