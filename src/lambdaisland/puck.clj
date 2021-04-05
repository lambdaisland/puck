(ns lambdaisland.puck
  (:require [applied-science.js-interop :as j]
            [lambdaisland.puck.util :as util]))

(defmacro assign!
  "Recursively merge values into a JavaScript object. Useful interop addition
  since pixi often requires setting values several levels deep into an object
  hierarchy."
  [obj m]
  (if (map? m)
    (cons 'do
          (for [p (util/keypaths m)
                :let [v (get-in m p)]]
            `(j/assoc-in! ~obj ~p ~v)))
    `(doseq [p# (util/keypaths ~m)
             :let [v# (get-in ~m p#)]]
       (j/assoc-in! ~obj p# v#))))
