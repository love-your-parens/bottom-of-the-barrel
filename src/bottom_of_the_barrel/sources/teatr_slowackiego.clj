(ns bottom-of-the-barrel.sources.teatr-slowackiego
  "Collects all theatrical spectacles in Teatr Słowackiego w Krakowie."
  (:require
   [bottom-of-the-barrel.sources :refer [register-source!]]
   [clojure.string :as s]
   [net.cgrand.enlive-html :as h]
   [bottom-of-the-barrel.schema :as schema]))


(def root-url "https://teatrwkrakowie.pl")


(defn absolute-url
  [relative-url]
  (str root-url relative-url))


(def seed (absolute-url "/repertuar"))


(def zone-id (java.time.ZoneId/of "Europe/Warsaw"))


(defn url->URL
  [url]
  (java.net.URL. url))


(defn get-page []
  (h/html-resource
   (url->URL seed)))


(defn get-day-nodes
  [page-node]
  (h/select page-node [:.repertoire :.day-wrap]))


(defn get-play-nodes
  [day-node]
  (h/select day-node [:.spektakle-list :.block]))


(defn day-node->date-string
  [day-node]
  (let [s (reduce str (h/texts (h/select day-node [:.day])))
        [_ d m y] (re-find #"\s*(\d\d)\s*(\w+)\s*(\d+)" s)]
    (format "%s %s %s" d m y)))


(def date-formatter
  (java.time.format.DateTimeFormatter/ofPattern "dd LLLL yyyy" (java.util.Locale. "PL")))


(defn parse-date
  [text]
  (java.time.LocalDate/parse
   (s/lower-case text)
   date-formatter))


(defn day-node->date
  [day-node]
  (-> day-node
      day-node->date-string
      parse-date))


(defn play-node->time
  [play-node]
  (let [nodes (h/select play-node [:.time])]
    (for [n nodes]
      (try
        (java.time.LocalTime/parse (s/trim (h/text n)))
        (catch Exception _ nil)))))

(comment
  (play-node->time (first (get-play-nodes (second (get-day-nodes (get-page))))))
  )


(defn get-thumbnail
  [play-node]
  (absolute-url
   (second
    (re-find #"background-image:url\('(.*)'\)"
             (-> play-node
                 (h/select [:.link :.img-container])
                 first
                 (get-in [:attrs :style]))))))


(defn get-description
  [play-node]
  (let [nodes (butlast (h/select play-node [:.desc :p]))]
    (s/join "\n" (map #(s/replace (s/trim (h/text %)) #"\s+" " ")
                      nodes))))


(defn get-address
  [play-node]
  (let [node (last (h/select play-node [:.desc :p]))]
    (s/replace (s/trim (h/text node)) #"\s+" " ")))


(defn extract-play
  ([play-node]
   (extract-play nil play-node))
  ([base-date play-node]
   {:name (->> [:.upper.h3]
               (h/select play-node)
               h/texts
               (reduce str)
               s/trim)
    :type :theatre
    :place "Teatr Słowackiego"
    :date (when base-date
            (for [t (play-node->time play-node)]
              (java.time.ZonedDateTime/of base-date t zone-id)))
    :address (get-address play-node)
    :url (absolute-url
          (get-in (first
                   (h/select play-node [:.upper.h3 :a]))
                  [:attrs :href]))
    :thumbnail (get-thumbnail play-node)
    :description (get-description play-node)}))


(defn get-plays
  [day-node]
  (let [base-date (day-node->date day-node)]
    (map (partial extract-play base-date)
         (get-play-nodes day-node))))


(defn fetch
  "Retrieves all events."
  []
  (get-plays
   (get-day-nodes
    (get-page))))


(comment
  "Spec check"
  (require '[clojure.spec.alpha :as spec])
  (spec/explain ::schema/event (first (fetch)))
  (every? (partial spec/valid? ::schema/event) (fetch)))


(register-source! fetch)
