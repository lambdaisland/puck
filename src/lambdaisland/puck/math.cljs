(ns lambdaisland.puck.math
  "Operations over pixi/Point and pixi/Matrix"
  (:require ["pixi.js" :as pixi]
            [applied-science.js-interop :as j]))

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

(defn perpendicular [{:keys [x y]}]
  (pixi/Point. (- y) x))

(defn dot-product [a b]
  (+ (* (:x a) (:x b))
     (* (:y a) (:y b))))

(defn winding
  "AKA the Perp Dot Product AKA the Cross Product on the XY-plane with Z=0,
  equivalent to |a| × |b| × sin(θ)"
  [a b]
  (dot-product a (perpendicular b)))

(defn clockwise?
  ([path]
   (let [triples (partition 3 1 (concat path (take 2 path)))
         ;; Find the three vertices that form the top-right corner
         [a b c] (reduce (fn [[_ a _ :as ta] [_ b _ :as tb]]
                           ;; Get the lowest y. In case of tie, get highest x
                           (cond
                             (< (:y a) (:y b))
                             ta
                             (> (:y a) (:y b))
                             tb
                             :else
                             (if (< (:x a) (:x b))
                               tb
                               ta)))
                         triples)]
     ;; Check the angle between the two edges formed by these three vertices
     (clockwise? (v- b c) (v- b a))))
  ([v1 v2]
   (pos-int? (winding v1 v2))))
