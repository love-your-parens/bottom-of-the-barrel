(ns bottom-of-the-barrel.core
  (:require [clojure.tools.logging :as log]
            [bottom-of-the-barrel.sources :refer [sources]]
            ;; For side-effects, i.e. self-registration:
            [bottom-of-the-barrel.sources.muzeum-krakowa]
            [bottom-of-the-barrel.sources.muzeum-narodowe]
            [bottom-of-the-barrel.sources.manggha]
            [bottom-of-the-barrel.sources.kino-pod-baranami]
            [bottom-of-the-barrel.sources.teatr-stary]
            [bottom-of-the-barrel.sources.teatr-slowackiego]
            [bottom-of-the-barrel.sources.teatr-bagatela]
            [bottom-of-the-barrel.sources.teatr-stu]))


(defn fetch-one!
  "Given a dereferenced(!) source - calls it and returns the result.
  Catches & logs errors."
  [source]
  (try
    ((val source))
    (catch Exception e
      (log/error e))))

(comment
  (fetch-one! (first @sources))
)


(defn fetch-all
  "Retrieves all events from all registered sources."
  []
  (reduce concat (pmap fetch-one! @sources)))

(comment
  (fetch-all)
)


(defn- now
  []
  (/ (System/currentTimeMillis) 1000))


(def ^:dynamic *cache-expiry* 300)

(defrecord Cache [time content])

;; Perhaps move this to e.g. impl.cache to avoid sealing internals?
(def cache
  "Retrieves local cache.
  If cache does not exist, it will be built.
  If cache age exceeds given `max-age`, it will first be refreshed.
  `max-age` is expressed in seconds."
  (let [state (atom (delay (->Cache (now) (fetch-all))))
        refresh-pending? (atom false)
        valid? (fn [max-age] (when-let [t (:time @@state)]
                               (> (+ t max-age) (now))))
        refresh! #(when (compare-and-set! refresh-pending? false true)
                    (let [p (promise)]
                      (reset! state p)
                      (future
                        (try
                          (let [content (fetch-all)
                                result (->Cache (now) content)]
                            (deliver p result))
                          (catch Exception _
                            (deliver p nil))
                          (finally (reset! refresh-pending? false))))
                      p))]
    (fn [max-age]
      (when-not (valid? max-age)
        (refresh!))
      @@state)))

(comment
  (let [c (cache 5)]
    [(int (- (now) (:time c)))
     (first (:content c))])
  )


(defn fetch-all-with-cache!
  "Retrieves all events from all registered sources employing local cache.
  Cache duration is defined by *cache-expiry*."
  []
  (:content (cache *cache-expiry*)))

(comment
  (binding [*cache-expiry* 30]
    (fetch-all-with-cache!))
  )
