(ns bottom-of-the-barrel.sources.teatr-stu
  "Collects all theatrical spectacles in Teatr Stu w Krakowie."
  (:require
   [bottom-of-the-barrel.sources :refer [register-source!]]
   [clojure.string :as s]
   [net.cgrand.enlive-html :as h]
   [bottom-of-the-barrel.schema :as schema]))


(def zone-id (java.time.ZoneId/of "Europe/Warsaw"))
(def root-url "https://scenastu.pl")


(def polish->latin
  {#"ę" "e"
   #"ó" "o"
   #"ą" "a"
   #"ś" "s"
   #"ł" "l"
   #"ż" "z"
   #"ź" "z"
   #"ć" "c"
   #"ń" "n"})

(defn- latinize-polish-text
  "ą => a, ó => o, etc."
  ([text] (latinize-polish-text text polish->latin))
  ([text letter-conversions]
   (if-let [[from to] (first letter-conversions)]
     (recur (s/replace text from to)
            (next letter-conversions))
     text)))

(comment
  (latinize-polish-text "zażółć gęślą jaźń")
  )


(defn- title->uri-key
  [title]
  (-> title
      s/trim
      s/lower-case
      latinize-polish-text
      (s/replace #"[^A-z\s]" "")
      (s/replace #"\s+" "-")))

(comment
  (title->uri-key "Zażółćże gęsią jaźń, huncwocie jeden!")
  )


(defn absolute-url
  [relative-url]
  (str root-url relative-url))


(def seed (absolute-url "/calendar"))
(def play-list-url (absolute-url "/spektakle"))


(defn get-play-page-url
  [title]
  (absolute-url (str "/spektakl/" (title->uri-key title))))


(defn url->URL
  [url]
  (java.net.URL. url))


(defn get-page
  ([url]
   (h/html-resource
    (url->URL url)))
  ([] (get-page seed)))


(defn get-play-page
  [name]
  (try
    (get-page (get-play-page-url name))
    (catch Exception _)))


(def select-first
  "Select only the first match.
  Common enough to be abstracted away."
  (comp first h/select))


(defn get-play-nodes
  [page]
  (h/select page [:tr.grid]))


(defn get-date-time
  [play-node]
  (let [date-string (h/text (select-first play-node [:.monthday]))
        [_ dd] (re-find #"(\d\d)" date-string)
        time-string (h/text (select-first play-node [:time.time]))
        [_ hhmm] (re-find #".*(\d\d:\d\d)\s*" time-string)]
    (when (and dd hhmm)
      (try
        (java.time.ZonedDateTime/of (-> (java.time.LocalDate/now)
                                        (.withDayOfMonth (Integer/parseInt dd)))
                                    (java.time.LocalTime/parse hhmm)
                                    zone-id)
        (catch Exception _)))))


(defn get-thumbnail
  [page play-name]
  (get-in (select-first (filter (fn [node]
                                  (= (h/text (select-first node [:.title]))
                                     play-name))
                                (h/select page [:li]))
                        [:.thumbnail :img])
          [:attrs :src]))


(defn get-description
  [play-page]
  (s/trim (h/text (select-first play-page [:.module.main-content-section :.grid]))))

(comment
  (get-play-page-url "Przybory Wasowskiego. Dom wyobraźni")
  (get-description (get-play-page "trzy siostry"))
  )


(defn extract-play
  [play-node]
  (let [a-node (select-first play-node [:.title :a])
        title (some-> a-node :content first s/trim)]
    {:name title
     :type :theatre
     :place "Teatr Stu"
     :date [(get-date-time play-node)]
     :address "Scena Stu, Al. Krasińskiego 16-18 30-101 Kraków"
     :url (get-in a-node [:attrs :href])
     ;; These are not provided directly and require additional requests.
     :thumbnail nil
     :description nil}))


(defn fetch
  "Retrieves all events.

  Notes on implementation:

  The calendar page only offers a slice of the information we'd like to retrieve.
  It's missing: event descriptions and thumbnails.
  To collect them, additional HTTP requests must be made. This is inherently expensive.
  Thumbnails are quite light as we can collect the whole lot from just one extra page.
  Descriptions on the other hand must be scraped from event-specific sub-pages.
  At minimum, this implies one extra request per unique event.

  A naive implementation would require 2 extra requests per every event.
  This is unacceptable, but can be mitigated.

  For thumbnails: the appropriate source page needs only be retrieved once and held in memory.
  Moreover, to cut down on traversing the DOM, we can memoize results per event-name.

  Memoizing by name also helps cut down on requests pertaining to descriptions.
  Ideally: only one request should be made for each unique event name.

  A lot of this work - especially I/O - can be performed in parallel.
  We leverage this opportunity via futures and pmap."
  []
  (let [page (future (get-page seed))
        play-list-page (future (get-page play-list-url))
        get-description (memoize (fn [name]
                                   (get-description
                                    (get-play-page name))))
        get-thumbnail (memoize (partial get-thumbnail @play-list-page))
        append-extras (fn [event]
                        (if-let [title (:name event)]
                          (let [description (future (get-description title))
                                thumbnail (future (get-thumbnail title))]
                            (assoc event
                                   :description @description
                                   :thumbnail @thumbnail))
                          event))
        get-event (comp append-extras extract-play)]
    (pmap get-event (get-play-nodes @page))))


(comment
  "Spec check"
  (require '[clojure.spec.alpha :as spec])
  (spec/explain ::schema/event (first (fetch)))
  (every? (partial spec/valid? ::schema/event) (fetch)))


(register-source! fetch)
