(ns bottom-of-the-barrel.sources.zamek-wawelski
  "Collects all events from Zamek Królewski na Wawelu w Krakowie.
  Focuses on temporary events; Skips permanent ones."
  (:require
   [bottom-of-the-barrel.sources :refer [register-source!]]
   [clojure.string :as s]
   [net.cgrand.enlive-html :as h]
   [bottom-of-the-barrel.schema :as schema]))


(def root-url "https://wawel.krakow.pl")


(defn absolute-url
  [relative-url]
  (str root-url relative-url))


(def seed (absolute-url "/co-zwiedzac"))


(defn url->URL
  [url]
  (java.net.URL. url))


(defn get-page
  ([] (get-page seed))
  ([url]
   (h/html-resource
    (url->URL url))))


(defn a->url
  [a]
  (get-in a [:attrs :href]))


(defn- coalesce-start-year
  "Fills in the year in the first date of a span when it's omitted.
  The year is taken from the second date."
  [date-span]
  (let [[[d1 m1 y1] [_ _ y2 :as date2]] date-span]
    [[d1 m1 (or y1 y2)] date2]))


(defn parse-date-span
  "Attempts to process an arbitrary string into a span of dates.
  These dates are expressed as: [day month year]
  A span is a pair of such dates (boundaries).
  Mismatch yields nil."
  [s]
  (some->> (s/replace s (str (char 0xA0)) " ") ; non-breaking space doesn't count as \s
           (re-seq #"\s*(\d\d?)\s+(\D+)\s+(\d+)?\s*\D*\s+(\d\d?)\s+(\D+)\s+(\d+)\s*")
           first
           (drop 1)
           (partition 3)
           coalesce-start-year))

(comment
  (parse-date-span "21 czerwca – 15 września 2024")
  (parse-date-span "Tell you what, this is definitely not a date span")
)


(def month-regexes
  [#"(?i)stycz.*"
   #"(?i)lut.*"
   #"(?i)mar.*"
   #"(?i)kwie.*"
   #"(?i)maj.*"
   #"(?i)czerw.*"
   #"(?i)lip.*"
   #"(?i)sierp.*"
   #"(?i)wrze.*"
   #"(?i)październik.*"
   #"(?i)listopad.*"
   #"(?i)grud.*"])


(def month-matchers
  "(matching-fn month-number)..."
  (partition 2 (interleave
                (map #(partial re-matches %) month-regexes)
                (range 1 13))))


(defn match-month-by-name
  "Attempts to match a month's ordinal number by name."
  [name]
  (let [name* (str name)]
    (first (keep (fn [[m n]]
                   (when (m name*) n))
                 month-matchers))))


(defn date->Date
  [[day month-name year]]
  (when-let [month (match-month-by-name month-name)]
    (let [->int (fn [x] (if (int? x) x (Integer/parseInt x)))]
      (try
        (java.time.LocalDate/of (->int year)
                                (->int month)
                                (->int day))
        (catch java.lang.NumberFormatException _)))))

(comment
  (date->Date ["20" "września" "2024"]) ;ok
  (date->Date [1 "września" 2024]) ;ok
  (date->Date [1 "wiśni" 2024]) ;not ok
  (date->Date [1 9 2024]) ;not ok
)


(defn a->event-node
  [a]
  (let [url (absolute-url (a->url a))
        page (get-page url)]
    (when page
      {:url url
       :thumbnail (absolute-url (get-in (first (h/select page [:picture :img])) [:attrs :src]))
       :name (h/text (first (h/select page [:h2])))
       :type :museum
       :description (s/join " "
                            (filter (comp not parse-date-span)
                                    (h/select page [:.article-detail :.wrap h/text-node])))
       :date (map date->Date
                  (first (keep parse-date-span
                               (h/select page [:.article-detail :.wrap h/text-node]))))
       :place "Zamek Królewski na Wawelu"
       :address "Wawel 5, 31-001 Kraków"})))

(defn fetch
  []
  (let [page (get-page)
        event-anchors (h/select page [:section.explore-list :a])
        temporary (filter #(s/includes? (a->url %) "wystawa-czasowa")
                          event-anchors)]
    (pmap a->event-node temporary)))

(comment
  (fetch)
)

(comment
  "Spec check"
  (require '[clojure.spec.alpha :as spec])
  (spec/explain ::schema/event (first (fetch)))
  (every? (partial spec/valid? ::schema/event) (fetch))
)


(register-source! fetch)
