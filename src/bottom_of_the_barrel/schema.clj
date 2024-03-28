(ns bottom-of-the-barrel.schema
  (:require [clojure.spec.alpha :as s]))


(s/def ::single-date (s/or :local-date (partial instance? java.time.LocalDate)
                           :zoned-date-time (partial instance? java.time.ZonedDateTime)))
(s/def ::date (s/or :nil nil?
                    :coll-of-dates (s/coll-of ::single-date :min-count 1)))

(s/def ::event (s/and
                (s/keys :req-un [::name ::date ::place]
                        :opt-un [::description ::thumbnail
                                 ::type ::url ::address])
                (some-fn :url :address)))
