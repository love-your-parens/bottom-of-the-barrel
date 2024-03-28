(ns morse
  (:require [dev.nu.morse :as morse]))

(morse/launch-in-proc)

(comment
  (morse/inspect (range 10))
  )
