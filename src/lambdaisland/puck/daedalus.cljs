(ns lambdaisland.puck.daedalus
  "Bridging Pixi/Puck and Daedalus path finding"
  (:require [lambdaisland.daedalus :as dae]
            [lambdaisland.puck :as puck]
            [lambdaisland.puck.math :as math]))

(defrecord PixiBasicCanvas [graphics]
  Object
  (clear [this]
    (.clear graphics))
  (lineStyle [this thickness color alpha]
    (.lineStyle graphics thickness color alpha))
  (beginFill [this color alpha]
    (.beginFill graphics color alpha))
  (endFill [this]
    (.endFill graphics))
  (moveTo [this x y]
    (.moveTo graphics x y))
  (lineTo [this x y]
    (.lineTo graphics x y))
  (quadTo [this cx cy ax ay]
    (.quadraticCurveTo graphics cx cy ax ay))
  (drawCircle [this cx cy radius]
    (.drawCircle graphics cx cy radius))
  (drawRect [this x y width height]
    (.drawRect graphics x y width height))
  (drawTri [this points]
    (.drawPolygon graphics (into-array (map (fn [[x y]] (puck/point x y))
                                            (partition 2 points))))))

(defn simple-view
  "Wraps a pixi Graphics object so that it can be passed
  to [[lambdaisland.daedalus/draw-mesh]], [[lambdaisland.daedalus/draw-entity]],
  and [[lambdaisland.daedalus/draw-path]], for easy debugging."
  [pixi-graphics]
  (dae/simple-view (PixiBasicCanvas. pixi-graphics) {}))

(defn with-radius
  "Add a get_radius() method to the given JavaScript object. This is enough to
  make a pixi DisplayObject (like a Sprite) compatible with Daedalus's
  path-finder/path-sampler, so you can forgo copying coordinates between a
  Daedalus EntityAI and your display-object."
  [entity radius]
  (specify! entity
    Object
    (get_radius [this] radius)))
