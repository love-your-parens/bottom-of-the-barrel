(ns bottom-of-the-barrel.sources.kino-pod-baranami
  "Scapes all movies currently screened in Kino Pod Baranami."
  (:require [net.cgrand.enlive-html :as h]
            [clojure.string :as s]))

(def root-url "https://www.kinopodbaranami.pl/")

(defn absolute-url
  [relative-url]
  (str root-url relative-url))

(def seed (absolute-url "/repertuar.php?wersja=wg_filmow"))

(defn url->URL
  [url]
  (java.net.URL. url))

(defn get-movie-pages
  "Scrapes the programme page for all movies and retrieves their subpage URLs."
  []
  (let [p (-> (url->URL seed)
              h/html-resource
              (h/select [:#program :h3 :a]))]
    (map (comp url->URL absolute-url s/trim)
         (into #{} (filter string?
                           (map #(get-in % [:attrs :href]) p))))))

(comment
  (get-movie-pages)
  )

(def month-names ["january"
                  "february"
                  "march"
                  "april"
                  "may"
                  "june"
                  "july"
                  "august"
                  "september"
                  "october"
                  "november"
                  "december"])

(def month-regex (format "(%s)" (s/join "|" month-names)))
(def date-regex (re-pattern (format "(?i)%s\\s+(\\d+)" month-regex)))
(def timezone (java.time.ZoneId/of "Europe/Warsaw"))

(comment
  (let [search "\n\t\t\t\t\t\t\t\t\tNiedziela 24 marca\t\t\t\t\t\t\t\t\tSunday March 24\t\t\t\t\t\t\t\t"]
    (re-seq date-regex search))
  )

(defn month-name->index
  [name]
  (let [month (s/lower-case name)]
    (reduce-kv (fn [_ k v]
                 (when (= v month)
                   (reduced (inc k))))
               nil
               month-names)))
(comment
  (month-name->index "August")
  )

(defn parse-date-string
  [date-string]
  (let [[_ month-name day*] (re-find date-regex date-string)
        month (month-name->index month-name)
        day (try (Integer/parseInt day*) (catch Exception _))]
    (when (and month day)
      (java.time.LocalDate/of (.getValue (java.time.Year/now))
                              month
                              day))))

(defn parse-time-string
  [time-string]
  (let [[_ h m] (re-find #"(\d+):(\d+)" time-string)]
    (java.time.LocalTime/of (Integer/parseInt h) (Integer/parseInt m) 0)))

(comment
  (parse-time-string "10:23")
  )

(defn scrape-screening-dates
  "Given a DOM node (`page`) extracted from a movie's webpage,
  extracts all screening dates and times as raw strings.
  Returns a collection of: [date-string [time-string...]]"
  [page]
  (map (fn [[header timeslots]]
         [(h/text header)
          (re-seq #"\d+:\d+" (h/text timeslots))])
       (partition 2 (h/select page
                              [:ul.when #{:div.head :div.cont}]))))

(comment
  (let [node (h/select (h/html-resource (second (get-movie-pages))) [:div.movie])]
    (scrape-screening-dates node)))

(defn extract-screenings-from-scraped-date
  "Takes a date string plus a collection of time strings,
  and combines them into discrete instances of ZonedDateTime.
  Date and time values are expected to have been scraped
  directly from the movie's page."
  [[date-string time-strings]]
  (when-let [date (parse-date-string date-string)]
    (for [t time-strings]
      (when-let [time (parse-time-string t)]
        (java.time.ZonedDateTime/of date time timezone)))))

(comment
  (extract-screenings-from-scraped-date
   (first (scrape-screening-dates
     (h/select (h/html-resource (second (get-movie-pages))) [:div.movie]))))
  )

(defn extract-screenings-from-page
  [page]
  (flatten
   (for [d (scrape-screening-dates page)]
             (extract-screenings-from-scraped-date d))))

(comment
  (extract-screenings-from-page
     (h/select (h/html-resource (second (get-movie-pages))) [:div.movie]))
  )

(defn extract-movie-from-url
  "Extracts an exhibition map from a movie URL."
  [URL]
  (let [container (h/select (h/html-resource URL)
                            [:div.movie])]
    {:title (-> container
                (h/select [[:h2 (h/but :.unas)]])
                h/texts
                first
                s/trim)

     :thumbnail (-> container
                    (h/select [:table :img])
                    first
                    :attrs
                    :src
                    absolute-url)
     :description (-> container
                      (h/select [:div.bot :> :p :> :strong])
                      first
                      h/text)
     :url (str URL)
     :type :movie
     :place "Kino pod Baranami"
     :date (extract-screenings-from-page container)}))

(comment
  (extract-movie-from-url
   (url->URL "https://www.kinopodbaranami.pl/film.php?film_id=11865"))
  )

(defn retrieve-movies
  [URLs]
  (map extract-movie-from-url URLs))

(defn fetch []
  (retrieve-movies (get-movie-pages)))

(comment
  (fetch)
  )
