(ns cljs.user
  (:require [portal.web :as portal]))

(def portal-instance nil)

(defn portal
  "Open a Portal window and register a tap handler for it. The result can be
  treated like an atom."
  []
  (let [p (portal/open portal-instance)]
    (set! portal-instance p)
    (add-tap #'portal/submit)
    p))
