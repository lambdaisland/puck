(ns lambdaisland.puck.collisions
  (:require ["collisions" :as collisions]
            [applied-science.js-interop :as j]
            [lambdaisland.puck.types :as types]))

(def Collisions collisions/Collisions)
(def Circle collisions/Circle)
(def Polygon collisions/Polygon)
(def Point collisions/Point)
(def Result collisions/Result)

(types/register-keys-printer ^js Collisions 'collisions/Collisions [:_bvh])
(types/register-keys-printer ^js (type (.-_bvh (Collisions.))) 'collisions/BVH [:_bodies])
(types/register-keys-printer ^js Circle 'collisions/Circle [:x :y :radius :scale :padding])
(types/register-keys-printer ^js Point 'collisions/Point [:x :y :padding])
(types/register-keys-printer ^js Polygon 'collisions/Polygon [:x :y :_points :angle :scale_x :scale_y :padding])
(types/register-keys-printer ^js Result 'collisions/Result [:collision :a :b :a_in_b :b_in_a :overlap :overlap_x :overlap_y])

(extend-type Collisions
  ITransientCollection
  (-conj! [coll obj]
    (.insert ^js coll obj)
    coll)
  ITransientSet
  (-disjoin! [coll obj]
    (.remove ^js coll obj)
    coll))

(defn system []
  (Collisions.))

(defn update! [^js sys]
  (.update sys))

(defn potentials [^js obj]
  (.potentials obj))

(defn collides? [^js a b result]
  (.collides a b result))

(defn rectangle
  "Create a collisions rectangular polygon, you can pass a Pixi.js Rect in here."
  ([{:keys [x y width height]}]
   (rectangle x y width height))
  ([x y width height]
   (Polygon. x y (j/lit [[0 0] [width 0] [width height] [0 height]]))))

(defn polygon [x y points]
  (Polygon. x y (into-array (map #(j/lit [(:x %) (:y %)]) points))))

(comment
  (Circle. 10 20 30 40 50)
  ;; => #collisions/Circle {:x 10, :y 20, :radius 30, :scale 40, :padding 50}
  (Point. 10 20 30)
  ;; => #collisions/Point {:x 10, :y 20, :padding 30}
  (Polygon. 10 20 (j/lit [[1 2] [3 4]]) 30 40 50)
  ;; => #collisions/Polygon {:x 10, :y 20, :_points #object[Float64Array 1,2,3,4], :angle 30, :scale_x 40, :scale_y 50, :padding 0}

  (Result.)
  ;; => #collisions/Result {:collision false, :a nil, :b nil, :a_in_b false, :b_in_a nil, :overlap 0, :overlap_x 0, :overlap_y 0}

  (def player (Circle. 100 100 10))

  (def sys (doto (system)
             (conj!
              player
              (Polygon. 400 500 (j/lit [[-60 -20] [60 -20] [60 20] [-60 20]]) 1.7)
              (Polygon. 200 100 (j/lit [[-60 -20] [60 -20] [60 20] [-60 20]]) 2.2)
              (Polygon. 400 50 (j/lit [[-60 -20] [60 -20] [60 20] [-60 20]]) 0.7))))

  (update! sys)
  (let [r (Result.)]
    (doseq [obj (potentials player)
            :when (collision? player obj)]
      (j/let [{:keys [overlap overlap_x]} r]
        ))))
