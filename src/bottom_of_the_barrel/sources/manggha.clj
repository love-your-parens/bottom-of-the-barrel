(ns bottom-of-the-barrel.sources.manggha
  "Scapes all exhibitions currently advertised by Manggha museum."
  (:require
   [bottom-of-the-barrel.sources :refer [register-source!]]
   [clojure.string :as s]
   [net.cgrand.enlive-html :as h]))

(def root-url "https://manggha.pl")

(defn absolute-url
  [relative-url]
  (str root-url relative-url))

(def seeds (map absolute-url
                ["/wystawy-czasowe"]))

(defn url->URL
  [url]
  (java.net.URL. url))

(defn get-exhibitions-on-page
  "Scrapes the provided page for all exhibition nodes.
   Each is then scraped for relevant topics, e.g. exhibition title.
   Note that there may be multiple matching child nodes per topic."
  [page]
  (let [p (h/html-resource page)]
    (pmap (fn [a b] (cons a b))
          (h/select p [:a.item]) ;url
          (for [container (h/select p [:div.content-bottom])]
            (for [selector [[:header :h4] ;title
                            [:div.paragraph-text] ;description
                            [:figure :img] ;thumbnail
                            [:span.date] ;dates
                            ]]
              (first (h/select container selector)))))))

(comment
  (get-exhibitions-on-page (url->URL (first seeds))))

(defn get-exhibitions
  "Retrieves all exhibitions from all known pages."
  []
  (reduce into
          (map get-exhibitions-on-page
               (map url->URL seeds))))

(comment
  (get-exhibitions))

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
   :thumbnail (absolute-url (get-in thumbnail [:attrs :src]))
   :name (-> title h/text s/trim)
   :type :museum
   :description (-> description h/text s/trim)
   :date (parse-date-string (h/text dates))
   :place "Manggha"
   :address "ul. M. Konopnickiej 26 30-302 Kraków"})

(defn fetch []
  (pmap exhibition->map (get-exhibitions)))

(comment
  (tap> (fetch)))

;; NB: effects on load/require!
(register-source! fetch)
