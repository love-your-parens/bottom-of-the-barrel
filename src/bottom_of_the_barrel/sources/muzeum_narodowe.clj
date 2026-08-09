(ns bottom-of-the-barrel.sources.muzeum-narodowe
  "Scrapes all events currently advertised by Muzeum Narodowe.

  Needed to be rebuilt following a redesign of the source portal.
  Far less effective than before since the new page relies a
  whole lot on JS/AJAX to load content, which we can't do
  without something like a headless browser – Selenium etc.
  Results are thus limited to events above the fold."
  (:require
   [bottom-of-the-barrel.sources :refer [register-source!]]
   [clojure.string :as s]
   [net.cgrand.enlive-html :as h]))


(def root-url "https://mnk.pl")


(defn absolute-url
  [relative-url]
  (str root-url relative-url))


(def seeds (map absolute-url
                ["/wystawy-category/wystawa-stala"
                 "/wystawy-category/wystawa-czasowa"]))


(defn url->URL
  [url]
  (java.net.URL. url))


(defn get-event-nodes-on-page
  "Scrapes the provided page for all event nodes. "
  [page]
  (h/select (h/html-resource page) [:#post-wystawy-results :.article-card--wystawy]))


(defn get-event-nodes
  "Retrieves all event nodes from all known pages."
  []
  (reduce into (map get-event-nodes-on-page
                    (map url->URL seeds))))


(defn parse-date-string
  "Converts a date string to a range of java.time.LocalDate objects."
  [date-string]
  (let [^java.time.format.DateTimeFormatter dtf (doto (java.time.format.DateTimeFormatter/ofLocalizedDate
                                                       java.time.format.FormatStyle/LONG)
                                                  (.withLocale (java.util.Locale. "pl")))
        range (-> date-string (s/split #" – "))]
    (for [r range]
      (when r (java.time.LocalDate/parse r dtf)))))

(comment
  (parse-date-string "29 stycznia 2026 – 31 grudnia 2026"))


(defn get-name
  [event-node]
  (-> (h/select event-node [:.article-title :h3]) first h/text))


(defn get-url
  [event-node]
  (-> (h/select event-node [:a.article-card-inner]) first :attrs :href))


(defn get-thumbnail
  [event-node]
  (-> (h/select event-node [:.featured-image :img]) first :attrs :src))


(defn get-description
  [event-node]
  (when-let [url (get-url event-node)]
    (let  [page (h/html-resource (url->URL url))]
      (s/join (->> (h/select page [:.main-wrap-content])
                   h/texts
                   (map s/trim))))))


(defn get-location
  [event-node]
  (some-> (h/select event-node [:.place-cat]) first h/text (s/replace-first #"Lokalizacja:" "") s/trim))


(defn get-dates
  [event-node]
  (when-let [s (some-> (h/select event-node [:.date-sec]) first h/text s/trim)]
    (when (not (s/blank? s))
      (parse-date-string s))))


(defn event-node->event-map
  "Converts a raw event node container into a structured map."
  [event-node]
  {:url (get-url event-node)
   :thumbnail (get-thumbnail event-node)
   :name (get-name event-node)
   :type :museum
   :description (get-description event-node)
   :date (get-dates event-node)
   :place (str "MNK " (get-location event-node))
   :address nil})


(defn fetch []
  (pmap event-node->event-map (get-event-nodes)))


(comment
  ;; simple test
  (require '[bottom-of-the-barrel.schema]
           '[clojure.spec.alpha :as spec])
  (every? (partial spec/valid? :bottom-of-the-barrel.schema/event)
          (fetch)))


;; NB: effects on load/require!
(register-source! fetch)
