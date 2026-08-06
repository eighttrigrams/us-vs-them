(ns et.uvt.test-helpers
  "Writing a version history down the way you would sketch one on paper.

  A history is the input to everything here, and as a vector of maps of escaped
  text it is unreadable at exactly the moment it matters — when you are checking by
  eye which line moved between two versions, which is the whole substance of a test
  in this library. Written out flat, the versions stack up and the movement is
  visible:

  ```
  # v1 Agent          # v2 Human
  abc                 abc
  def                 def
  dhk                 ggh
                      dhk
  ```

  So a test writes the two columns one after the other in one string and this turns
  it into the history. A line starting with `#` opens a version and its last word
  names the source; every line after it, until the next `#`, is that version's text.

  Nothing is trimmed but the ends of the whole string, so the blocks have to sit
  flush left. That is deliberate rather than lazy: leading whitespace is text, a
  change to it is a change like any other, and a helper that quietly ate it would be
  lying to the one test that eventually cares."
  (:require [clojure.string :as str]))

(defn- opener?
  [line]
  (str/starts-with? line "#"))

(defn- source
  "The last word of a `# v2 Human` line, as a marker."
  [line]
  (-> line (str/split #"\s+") last str/lower-case keyword))

(defn history
  "The versions written out in `s`, oldest first, in the shape `assess` takes."
  [s]
  (->> (str/split-lines (str/trim s))
       (partition-by opener?)
       (partition 2)
       (mapv (fn [[[opener] text]]
               {:text (str/join "\n" text)
                :source (source opener)}))))
