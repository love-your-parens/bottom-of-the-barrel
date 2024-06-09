(ns bottom-of-the-barrel.sources.teatr-bagatela
  "Collects all theatrical spectacles in Teatr Bagatela w Krakowie."
  (:require
   [bottom-of-the-barrel.sources :refer [register-source!]]
   [clojure.string :as s]
   [net.cgrand.enlive-html :as h]
   [bottom-of-the-barrel.schema :as schema]))


(def root-url "https://bagatela.pl")


(defn absolute-url
  [relative-url]
  (str root-url relative-url))


(def seed (absolute-url "/repertuar-teatru/repertuar-bagateli/"))


(def zone-id (java.time.ZoneId/of "Europe/Warsaw"))


(defn url->URL
  [url]
  (java.net.URL. url))


(defn get-page []
  (h/html-resource
   (url->URL seed)))


(defn get-play-nodes
  [page]
  (h/select page [:.spectacles-list-section :.spectacle-tile]))


(defn get-date-time
  [play-node]
  (let [date-string (h/text (first (h/select play-node [:.data-container :.date])))
        [_ dd MM] (re-find #"(\d\d)\.(\d\d)" date-string)
        time-string (h/text (first (h/select play-node [:.data-container :.title])))
        [_ hhmm] (re-find #".*(\d\d:\d\d)\s*" time-string)]
    (when (and dd MM hhmm)
      (try
        (java.time.ZonedDateTime/of (-> (java.time.LocalDate/now)
                                        (.withMonth (Integer/parseInt MM))
                                        (.withDayOfMonth (Integer/parseInt dd)))
                                    (java.time.LocalTime/parse hhmm)
                                    zone-id)
        (catch Exception _)))))


(defn extract-play
  [play-node]
  {:name (s/join " " (h/texts (h/select play-node
                                        [:.title-part])))
   :type :theatre
   :place "Teatr Bagatela"
   :date [(get-date-time play-node)]
   :address (s/trim (reduce str (h/texts (h/select play-node
                                                   [:.stage-container :.value-container]))))
   :url (get-in (first (h/select play-node [:.spectacle-title-container]))
                [:attrs :href])
   :thumbnail (get-in (first (h/select play-node
                                       [:.banner-container-in :img.attachment-post-thumbnail]))
                      [:attrs :src])
  ;; Not immediately available. Would require an additional HTTP request.
   :description nil})


(defn fetch
  "Retrieves all events."
  []
  (map extract-play (get-play-nodes (get-page))))


(comment
  "Spec check"
  (require '[clojure.spec.alpha :as spec])
  (spec/explain ::schema/event (first (fetch)))
  (every? (partial spec/valid? ::schema/event) (fetch)))


(register-source! fetch)
