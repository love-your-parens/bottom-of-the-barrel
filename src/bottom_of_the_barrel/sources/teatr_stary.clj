(ns bottom-of-the-barrel.sources.teatr-stary
  "Collects all theatrical spectacles in Teatr Stary w Krakowie."
  (:require
   [bottom-of-the-barrel.sources :refer [register-source!]]
   [clojure.string :as s]
   [net.cgrand.enlive-html :as h]
   [bottom-of-the-barrel.schema :as schema]))

(def root-url "https://stary.pl")

(defn absolute-url
  [relative-url]
  (str root-url relative-url))

(def seed (absolute-url "/pl/repertuar_kalendarz/kalendarz/"))

(def zone-id (java.time.ZoneId/of "Europe/Warsaw"))

(defn url->URL
  [url]
  (java.net.URL. url))

(defn get-page []
  (h/html-resource
   (url->URL seed)))

(defn get-month-nodes [page]
  (h/select page [:.tab-content]))

(defn get-day-nodes
  [month-node]
  (h/select
   month-node
   [:div.single-day-list]))

(defn get-year-month
  "Whisks out the year and month a given month-node pertains to."
  [month-node]
  (when-let [yyyymm (get-in month-node [:attrs :data-tab])]
    [(subs yyyymm 0 4)
     (subs yyyymm 4 6)]))

(defn get-day-num
  "Whisks out the day number a day-node pertains to."
  [day-node]
  (h/text (first (h/select day-node [:div.day :div.number]))))

(defn get-play-name
  "Given the text-node part of a play-node, determines its name."
  [text-node]
  (apply str (h/texts (h/select text-node [:div.title :span]))))

(defn get-play-time
  "Given a contextual LocalDate, and the text-node part of a play-node,
  determines the time and merges it with the date to produce a ZonedDateTime."
  [date text-node]
  (let [hhmm (re-find #"\d\d:\d\d"
                      (-> (h/select text-node [:div.title__box])
                          h/texts
                          first
                          s/join))]
    (when-not (s/blank? hhmm)
      (java.time.ZonedDateTime/of date
                                  (java.time.LocalTime/parse hhmm)
                                  zone-id))))

(defn day->play-components-list
  "Splits a day-node into distinct components,
  grouped by the play they pertain to."
  [day-node]
  (partition 3 (h/select day-node #{[:a.row.item] ; a-node
                                    [:div.item-text] ; text-node
                                    [:div.item-picture] ; image-node
                                    })))

(defn components->play
  "Pulls together data from component-nodes to formulate a play map."
  [a text image]
  {:name (get-play-name text)
   :type :theatre
   :place "Teatr Stary"
   :address (-> (h/select text [:.details :.location])
                first
                h/text
                (s/replace #"[\t]" "")
                s/trim)
   :url (-> a :attrs :href)
   :thumbnail (-> (h/select image [:img]) first :attrs :src)
   :description (-> text
                    (h/select [:div.people])
                    first
                    h/text
                    (s/replace #"\s+" " ")
                    s/trim)})

(defn reduce-play-fn
  "Produces a reductor that builds up a collection of plays
  from component-groups, in the context of a single date."
  [date]
  (fn [plays play-components]
    (let [[a text image] play-components
          name (get-play-name text)
          time (get-play-time date text)]
      (if time
        (if (contains? plays name)
          ;; Add occurrence.
          (update-in plays [name :date]
                     (fn [old] (conj (or old [])
                                     time)))
          ;; Create event.
          (assoc plays name (-> (components->play a text image)
                                (assoc :date [time]))))
        plays))))

(defn reduce-day-fn
  "Produces a reductor that builds up a collection of plays
  from day-nodes, in the context of a single year & month."
  [y m]
  (fn [events day-node]
    (let [d (get-day-num day-node)
          date (java.time.LocalDate/of (Integer/parseInt y)
                                       (Integer/parseInt m)
                                       (Integer/parseInt d))]
      (reduce (reduce-play-fn date)
              events
              (day->play-components-list day-node)))))

(defn reduce-months
  "Reduces any number of month-nodes into a hashmap of events (plays)."
  [events month-node]
  (let [days (get-day-nodes month-node)
        [y m] (get-year-month month-node)]
    (reduce (reduce-day-fn y m)
            events
            days)))

(defn fetch
  "Retrieves all events."
  ([] (fetch (get-page)))
  ([page]
   (vals (let [events {}
               month-nodes (get-month-nodes page)]
           (reduce reduce-months events month-nodes)))))

(comment
  "Spec check"
  (require '[clojure.spec.alpha :as spec])
  (spec/explain ::schema/event (first (fetch)))
  (every? (partial spec/valid? ::schema/event) (fetch)))

(register-source! fetch)
