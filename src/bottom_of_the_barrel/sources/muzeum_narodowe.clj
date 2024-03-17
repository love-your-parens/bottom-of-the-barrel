(ns bottom-of-the-barrel.sources.muzeum-narodowe
  "Scapes all exhibitions currently advertised by Muzeum Narodowe."
  (:require [net.cgrand.enlive-html :as h]
            [clojure.string :as s]))

(def root-url "https://mnk.pl")

(defn absolute-url
  [relative-url]
  (str root-url relative-url))

(def seeds (map absolute-url
                ["/wystawy/czasowe"
                 "/wystawy/stale"]))

(defn url->URL
  [url]
  (java.net.URL. url))

(defn get-exhibitions-on-page
  "Scrapes the provided page for all exhibition nodes.
   Each is then scraped for relevant topics, e.g. exhibition title.
   Note that there may be multiple matching child nodes per topic."
  [page]
  (filter
   #(let [i (first %)]
      (if (sequential? i) (seq i) i))
   (for [container (h/select
                    (h/html-resource page)
                    [:#content-page :li])]
     (for [selector [[:.title :a] ;title+url
                     #{[:p] [:.description]} ;descriptions
                     [:a :img] ;thumbnail
                     [:.event-time] ;dates
                     [:.place] ;map
                     [:span.street] ;address
                     ]]
       ;; NB: each selection is a seq!
       (h/select container selector)))))

(comment
  (get-exhibitions-on-page (url->URL (first seeds)))
  (get-exhibitions-on-page (url->URL (second seeds)))
  )

(defn get-exhibitions
  "Retrieves all exhibitions from all known pages."
  []
  (reduce into
          (map get-exhibitions-on-page
               (map url->URL seeds))))

(comment
  (get-exhibitions)
  )

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
  (parse-date-string "\n                        16.02.2024\n                        04.08.2024\n                    ")
  )

(defn exhibition->map
  "Converts a raw exhibition container into a structured map."
  [[[title] descriptions [thumbnail] [dates] [venue] [address]]]
  {:url (absolute-url (get-in title [:attrs :href]))
   :thumbnail (get-in thumbnail [:attrs :src])
   :name (-> title h/text s/trim)
   :description (s/trim (h/text (or (second descriptions) (first descriptions))))
   :date (parse-date-string (h/text dates))
   :place (s/trim (h/text venue))
   :address (s/trim (h/text address))})

(defn exhibitions->maps
  [exhibitions]
  (map exhibition->map exhibitions))

(defn fetch []
  (exhibitions->maps 
   (get-exhibitions)))

(comment
  (tap> (fetch))
  )
