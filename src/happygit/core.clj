(ns happygit.core
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [clojure.string :refer [split]]
            [environ.core :refer [env]]
            [java-time :refer [local-date minus days adjust]]))


(defn days-ago
  "Given an integer, returns a string representing the date that
  many days ago."
  [how-long-ago]
  (-> (local-date)
      (minus (days how-long-ago))
      .toString))


(defn adjusted-date
  "Given one of the adjustment constants listed in
  (https://github.com/dm3/clojure.java-time/blob/master/src/java_time/adjuster.clj)
  returns a string representation of the date representing the date of the
  requested adjustment."
  [adjusted-to]
  (-> (local-date)
      (adjust adjusted-to)
      .toString))


(defn links
  "Utility to quickly pluck link data from a
   Github API response"
  [resp]
  (some-> resp
          :headers
          (get "Link")
          (split #",")))


(defn next-page-link
  "Given a Github API response, extracts a link to the
   next page of data, if one exists."
  [resp]
  (->> resp
       links
       (keep #(re-matches #"<(.+)>;.+rel=\"next\".*" %))
       first
       second))


(defn query->results
  "Given a Github API url and associated params, queries
   the Github API, converts any results into a map and
   extracts a link to the next page of data, if there is
   one."
  [url params]
  (let [resp (client/get url params)
        next-page-link (next-page-link resp)
        results (-> resp
                    :body
                    (json/parse-string true))]
    {:results results
     :next-page-link next-page-link}))


(defn github-seq
  "Given a Github API query URL and any required parameters,
  (typically an authorization token), returns a sequence of results
  from the query.  Github results are returned in paginated sets;
  this sequence automagically detects when a page has been fully
  traversed and fetches the next page of results, continuing
  the sequence until all pages have been iterated over ."
  ([url params]
   (let [{:keys [next-page-link results]} (query->results url params)]
       (github-seq results params next-page-link)))
  ([results params next-page-link]
   (if-let [current (first results)]
     (cons current (lazy-seq (github-seq (rest results) params next-page-link)))
     (if next-page-link
       (github-seq next-page-link params)
       nil))))


(defn happiness
  "Given a Github commit data structure, plucks out a happiness score if one
   exists, attempts to convert it to an integer.   Any intermediate oddness
   is returned as a nil"
  [c]
  (try
    (Integer/parseInt (->> c
                           :commit
                           :message
                           (re-find #".+h:(.).*")
                           second))
    (catch Exception e
      nil)))



(def query-string-template
  "https://api.github.com/repos/opploans/%s/commits?since=%s")


;; Every Github API request requires at least a auth token.
;; Std headers attempts to read this token from the env
;; variable GITHUB_TOKEN.
(def std-headers {:headers {:authorization
                            (format "token %s" (env :github-token))}})


(defn happiness-since
  "Given a Github repo name and starting date, calculate the average
   happiness of all commits made to the repo since that date"
  [repo since]
  (let [query-string (format query-string-template repo since)
        happy-scores (keep happiness (github-seq query-string std-headers))
        score-count (count happy-scores)]
    {:avg (/ (float (reduce + happy-scores)) score-count)
     :count score-count
     :since since}))


(defn otter-happiness-since
  "Calculate happiness averages for some of our
   favorite repos."
  [since]
  (->> ["loanarranger" "bankbucl" "laalaaland" "leadzeppelin" "audit" "hammurabi"]
       (map #(future {(keyword %) (happiness-since % since)}))
       (map deref)))


;; Some self-explanatory helpers.
(defn otter-happiness-since-a-year-ago []
  (otter-happiness-since (days-ago 365)))


(defn otter-happiness-since-a-week-ago []
  (otter-happiness-since (days-ago 7)))


(defn otter-happiness-since-30-days-ago []
  (otter-happiness-since (days-ago 30)))


(defn otter-happiness-this-year []
  (otter-happiness-since (adjusted-date :first-day-of-year)))


(defn otter-happiness-this-month []
  (otter-happiness-since (adjusted-date :first-day-of-month)))


(defn -main
  [action & rest]
  (case action
    "past-month" (do (println (otter-happiness-since-30-days-ago))
                     (System/exit 0))
    (println "Unknown action:" action)))


(comment
  (otter-happiness-since-30-days-ago)
  (otter-happiness-since-a-year-ago)
  (otter-happiness-this-year)
  (otter-happiness-this-month)
  )
