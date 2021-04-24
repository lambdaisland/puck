(ns cljs.user
  (:require [portal.web]))

(add-tap #'portal.web/submit)

(defn portal []
  (portal.web/open))
