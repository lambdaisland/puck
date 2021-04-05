(ns lambdaisland.puck.math
  "Operations over pixi/Point and pixi/Matrix"
  (:require ["pixi.js" :as pixi]
            [applied-science.js-interop :as j]))

(defn point [x y]
  (pixi/Point. x y))

(defn distance [this that]
  (let [dx (- (:x that) (:x this))
        dy (- (:y that) (:y this))]
    (Math/sqrt (+ (* dx dx) (* dy dy)))))

(defn length [v]
  (distance {:x 0 :y 0} v))

(defn !v+ [a b] (j/assoc! a :x (+ (:x a) (:x b)) :y (+ (:y a) (:y b))))
(defn v+ [a b] (doto (.clone a) (!v+ b)))
(defn !v- [a b] (j/assoc! a :x (- (:x a) (:x b)) :y (- (:y a) (:y b))))
(defn v- [a b] (doto (.clone a) (!v- b)))
(defn !v* [a b] (j/assoc! a :x (* (:x a) b) :y (* (:y a) b)))
(defn v* [a b] (doto (.clone a) (!v* b)))
(defn !vdiv [a b] (j/assoc! a :x (/ (:x a) b) :y (/ (:y a) b)))
(defn vdiv [a b] (doto (.clone a) (!vdiv b)))
