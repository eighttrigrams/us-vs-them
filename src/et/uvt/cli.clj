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

(defn- report
  [ranges]
  (doseq [{:keys [from to caution]} ranges]
    (println (format "%-12s %.2f"
                     (if (= from to) (str from) (str from "-" to))
                     caution))))

(defn- usage []
  (println "usage: us-vs-them [--theirs EMAIL]... FILE")
  (println)
  (println "  Ranges of FILE, each with how careful an agent should be in it —")
  (println "  1 sacred, 0 up for grabs — read off the file's git history.")
  (println)
  (println "  --theirs EMAIL   a committer that is a machine. Repeatable.")
  (println "                   Everyone not named is taken to be a person."))

(defn -main [& args]
  (loop [args args
         theirs #{}]
    (cond
      (empty? args)
      (do (usage) (System/exit 1))

      (= "--theirs" (first args))
      (recur (drop 2 args) (conj theirs (second args)))

      (#{"-h" "--help"} (first args))
      (usage)

      :else
      (let [path (str/trim (git "ls-files" "--full-name" "--" (first args)))
            history (versions path)]
        (if (empty? history)
          (binding [*out* *err*]
            (println (str "no git history for " (first args)))
            (System/exit 1))
          (report (caution/assess history
                                  {:ours (into #{} (remove theirs) (map :source history))})))))))
