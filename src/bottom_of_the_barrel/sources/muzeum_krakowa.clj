(ns bottom-of-the-barrel.sources.muzeum-krakowa
  "Scapes all exhibitions currently advertised by Muzeum Krakowa."
  (:require [net.cgrand.enlive-html :as h]
            [clojure.string :as s]))

(def seed "https://muzeumkrakowa.pl/wystawy")

(defn get-exhibitions-on-page
  "Retrieves all raw exhibition nodes present on a page.
   Each node such node is a collection of DOM elements, conveying: 
   url, thumbnail, title, date and place.
   This information still needs to extracted down the line, thus: 'raw'"
  [^java.net.URL URL]
  (let [page (h/html-resource URL)
        exhibitions  (h/select page #{[:.events__list__item]
                                      [:.event__img]
                                      [:.event__title-box__title]
                                      [:.event__date]
                                      [:.event__place]})
        next (when-let [href (get-in (first (h/select page [:a.pagination__arrow--right]))
                                     [:attrs :href])]
               (java.net.URL. URL href))]
    [(partition 5 exhibitions) next]))

(defn get-exhibitions
  "Iterates over all exhibition-calendar pages, collecting raw exhibiton nodes."
  [^java.net.URL URL]
  (loop [exhbitions [] page URL]
    (if page
      (let [[exhibitions' page'] (get-exhibitions-on-page page)]
        (recur (concat exhbitions exhibitions') page'))
      exhbitions)))

(defn parse-date-string
  "Converts a date string to a range of java.time.LocalDate objects."
  [date]
  (let [rs (-> (re-matches #"([0-9]+\.[0-9]+\.[0-9]+)(\s*-\s*([0-9]+\.[0-9]+\.[0-9]+))?"
                           (str date))
               (select-keys [1 3])
               vals)]
    (for [r rs]
      (when r
        (let [[day month year] (map #(Integer/parseInt %) (s/split r #"\."))]
          (java.time.LocalDate/of year month day))))))

(comment
  (parse-date-string "01.01.2024 - 01.02.2024")
  (parse-date-string "01.01.2024")
  (parse-date-string nil)
  )

(def url
  "Resolves the given url relative to the seed."
  (memoize
   (let [seed-url (java.net.URL. seed)]
     (fn [ctx]
       (str (java.net.URL. seed-url ctx))))))

(defn exhibition->map
  "Converts a raw exhibition container into a structured map."
  [[a img title date place]]
  {:url (url (get-in a [:attrs :href]))
   :thumbnail (url (get-in img [:attrs :src]))
   :name (h/text title)
   :type :museum
   :date (parse-date-string (h/text date))
   :place (h/text (first (h/select place [:span :> :span])))})

(defn exhibitions->maps
  [exhibitions]
  (map exhibition->map exhibitions))

(defn fetch 
  "Resolves a complete list of exhibitions maps"
  []
  (-> seed
      java.net.URL.
      get-exhibitions
      exhibitions->maps))

(comment 
  (fetch)
  )
