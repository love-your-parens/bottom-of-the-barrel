(ns bottom-of-the-barrel.core
  (:require [bottom-of-the-barrel.sources :refer [sources]]
            ;; For side-effects, i.e. self-registration:
            [bottom-of-the-barrel.sources.muzeum-krakowa]
            [bottom-of-the-barrel.sources.muzeum-narodowe]
            [bottom-of-the-barrel.sources.manggha]
            [bottom-of-the-barrel.sources.kino-pod-baranami]))

(def ^:dynamic *cache-expiry* 300)
(def !cache (atom {:time nil
                  :content nil}))

(defn fetch-all
  "Retrieves all events from all registered sources."
  []
  (reduce concat (map #((val %)) @sources)))

(comment
  (fetch-all)
  )

(defn- now
  []
  (/ (System/currentTimeMillis) 1000))

(defn fetch-all-with-cache!
  []
  (let [c @!cache]
    (if (and (:content c)
             (>= (+ (:time c) *cache-expiry*) (now)))
      (:content c)
      (:content (reset! !cache {:time (now)
                               :content (fetch-all)})))))

(comment
  (binding [*cache-expiry* 30]
    (fetch-all-with-cache!))
  (:time @!cache)
  )
