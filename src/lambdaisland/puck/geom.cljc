(ns lambdaisland.puck.geom
  "Several libraries implement some version of geometric shapes, this namespace
  introduces protocols so we can treat them uniformly. It's also meant to make
  conversion between them easier. Not sure yet if this will hold water.")

(defprotocol IPosition
  (x [o])
  (y [o]))

(defprotocol IScale
  (scale [o]))

(defprotocol IScaleXY
  (scale-x [o])
  (scale-y [o]))

(defprotocol IRect
  (width [o])
  (height [o]))

(defprotocol ICircle
  (radius [o]))

(defprotocol IPolygon
  (vertices [o]))
