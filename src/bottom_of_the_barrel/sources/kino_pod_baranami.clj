(ns bottom-of-the-barrel.sources.kino-pod-baranami
  "Scapes all exhibitions currently advertised by Manggha museum."
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

(defn get-exhibitions
  "Scrapes the provided page for all exhibition nodes.
   Each is then scraped for relevant topics, e.g. exhibition title.
   Note that there may be multiple matching child nodes per topic."
  [page]
  (partition 2
             (let [p (-> (h/html-resource page) (h/select [:#program]))]
     (h/select p #{[:h3] [:ul.when]}))))

(comment
  (get-exhibitions (url->URL seed)))

(defn -get-exhibitions
  "Retrieves all exhibitions from all known pages."
  []
  (reduce into
          (map get-exhibitions
               (map url->URL seed))))

(comment
  (-get-exhibitions))

(defn parse-date-string
  "Converts a date string to a range of java.time.LocalDate objects."
  [date]
  (let [rs (take 2 (re-seq #"[0-9]+\.[0-9]+\.[0-9]+"
                           (s/replace date "\n" "")))]
    (for [r rs]
      (when r
        (let [[day month year] (map #(Integer/parseInt %) (s/split r #"\."))]
          (java.time.LocalDate/of year month day))))))

(comment
  (parse-date-string "26.11.2023 - 05.05.2024"))

(defn exhibition->map
  "Converts a raw exhibition container into a structured map."
  [[url title description thumbnail dates]]
  {:url (absolute-url (get-in url [:attrs :href]))
   :thumbnail (get-in thumbnail [:attrs :src])
   :name (-> title h/text s/trim)
   :description (-> description h/text s/trim)
   :date (parse-date-string (h/text dates))
   :place "Manggha"
   :address "ul. M. Konopnickiej 26 30-302 Kraków"})

(defn exhibitions->maps
  [exhibitions]
  (map exhibition->map exhibitions))

(defn fetch []
  (exhibitions->maps
   (get-exhibitions)))

(comment
  (tap> (fetch)))
