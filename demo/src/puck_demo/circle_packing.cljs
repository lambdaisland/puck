(ns puck-demo.circle-packing
  (:require [lambdaisland.puck :as p]
            [lambdaisland.puck.math :as m]
            [lambdaisland.thicc :as thicc]
            [kitchen-async.promise :as promise]
            [applied-science.js-interop :as j]))

(defonce game (p/full-screen-app {}))
(defonce g (p/graphics))
(def padding 1)
(defonce init
  (do
    (conj! (thicc/query "body") (:view game))
    (conj! (:stage game) g)))

(defn rand-circle []
  (let [{:keys [width height]} (-> game :renderer :screen)
        radius 2]
    {:x (+ radius (rand-int (- width radius radius)))
     :y (+ radius (rand-int (- height radius radius)))
     :r radius
     :line (+ 0x098555 (rand-int 100))
     :fill (- 0x098555 (rand-int 100))}))

(defn draw-circle [{:keys [x y r line fill]}]
  (p/line-style g {:color 0xFFFFFF #_line :width 0.5 :alpha 1})
  (p/with-fill [g {:color fill
                   :alpha 0.9}]
    (p/draw-circle g x y r)))

(defn can-grow? [{:keys [x y r done?] :as this} circles]
  (and (not done?)
       (let [{:keys [width height]} (-> game :renderer :screen)]
         (and (< padding (- x r) (+ x r) (+ padding width))
              (< padding (- y r) (+ y r) (+ padding height))
              (not (some (fn [that]
                           (and (not= this that)
                                (< (m/distance this that)
                                   (+ (:r this) (:r that) 1 padding))))
                         circles))))))

(defn safe-spot? [this circles]
  (not (some (fn [that]
               (< (m/distance this that)
                  (+ (:r this) (:r that) 2)))
             circles)))

(defn grow-circles [circles]
  (for [this circles]
    (if (can-grow? this circles)
      (update this :r inc)
      (assoc this :done? true))))

(defn add-circle [circles]
  (loop [circle (rand-circle)]
    (if (safe-spot? circle circles)
      (conj circles circle)
      (recur (rand-circle)))))

(defonce circles (atom [(rand-circle)]))

(p/unlisten! (:ticker game) ::tick
             #_      (fn [_]
                       (p/clear! g)
                       (swap! circles (comp grow-circles #_#_ grow-circles add-circle))
                       (run! draw-circle @circles)
                       ))


(swap! circles #(nth (iterate add-circle %) 200))
(p/clear! g)
(run! draw-circle @circles)
