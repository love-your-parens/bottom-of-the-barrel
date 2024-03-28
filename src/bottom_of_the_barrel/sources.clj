(ns bottom-of-the-barrel.sources
  "Public, mutable registry of data sources.
  Each provider should be a zero-arity fn returning a ::schema/event.")

(def sources (atom {}))

(defn register-source!
  [provider]
  (:pre [(fn? provider)])
  (swap! sources assoc *ns* provider))
