;; for node and jvm
(require '[portal.api :as p])

;; for web
;; NOTE: you might need to enable popups for the portal ui to work in the
;; browser.
;; (require '[portal.web :as p])


;; (def p (p/open)) ; Open a new inspector

;; or with an extension installed, do:
(def p (p/open {:launcher :vs-code}))  ; jvm / node only
;; (def p (p/open {:launcher :intellij})) ; jvm / node only
;; (def p (p/open {:launcher :emacs})) ; jvm / node only

(add-tap #'p/submit) ; Add portal as a tap> target

(comment 
   ; Start tapping out values
  (tap> :hello)
  
   ; Clear all values
  (p/clear)
  
   ; Tap out more values
  (tap> :world)
  
   ; bring selected value back into repl
  (prn @p)
  
   ; Remove portal from tap> targetset
  (remove-tap #'p/submit)
  
   ; Close the inspector when done
  (p/close)
  
  ; View docs locally via Portal - jvm / node only
  (p/docs)
  )
