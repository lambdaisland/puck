(ns lambdaisland.puck
  (:refer-clojure :exclude [let])
  (:require [applied-science.js-interop :as j]
            [kitchen-async.promise :as promise]
            [lambdaisland.puck.util :as util]
            [cljs.core :as cljs]))

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

(defmacro with-fill [[graphics fill-opts] & body]
  `(do
     (begin-fill ~graphics ~fill-opts)
     ~@body
     (end-fill ~graphics)))

(defmacro let
  "Like regular let, but you can mark up right-hand values with ^js or ^await. ^js
  wraps the object in a js-interop/lookup so you can destructure or do keyword
  lookup on it, ^await rewrites the code to resolve promise. If you use any
  ^await then the whole `let` will return a promise."
  [bindings & body]
  (reduce
   (fn [body [a b]]
     (if (= 'await (:tag (meta b)))
       `(promise/->promise (promise/then ~b (cljs/fn [~a] ~@body)))
       `((let* ~(cljs/destructure [a b]) ~@body))))
   body
   (reverse
    (map (fn [[a b]]
           [a (if (= 'js (:tag (meta b)))
                `(j/lookup ~b)
                b)])
         (partition 2 bindings)))))
