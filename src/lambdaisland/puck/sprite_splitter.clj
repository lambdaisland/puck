(ns lambdaisland.puck.sprite-splitter
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import java.awt.Image
           java.awt.image.BufferedImage
           java.awt.image.Raster
           java.awt.image.DataBufferByte
           java.io.IOException
           javax.imageio.ImageIO))

(set! *warn-on-reflection* true)

(def ^"[F" float4 (make-array Float/TYPE 4))

(defn image-raster ^Raster [^BufferedImage img]
  (.. img getRaster))

(defn raster-data [img]
  (.getData ^DataBufferByte (.getDataBuffer (image-raster img))))

(defn find-slices [raster-data width]
  (let [alpha (vec (map first (partition 4 raster-data)))]
    (loop [idx 0
           slices []
           start nil]
      (if (<= (count alpha) idx)
        (if start
          (conj slices [start (dec idx)])
          slices)
        (let [v (get alpha idx)]
          (if (= 0 v)
            (if start
              (recur (inc idx) (conj slices [start (dec idx)]) nil)
              (recur (inc idx) slices nil))
            (if start
              (if (= 0 (mod idx width))
                (recur (inc idx) (conj slices [start (dec idx)]) idx)
                (recur (inc idx) slices start))
              (recur (inc idx) slices idx))))))))

(defn join-region-slice [[x y w h] [start end] width]
  (let [start (mod start width)
        end (mod end width)]
    (if (or (<= x start (+ x w))
            (<= x end (+ x w))
            (< x start end (+ x w)))
      (let [new-x (min x start)
            new-w (- (max (+ x w) end) new-x)
            new-h (inc h)]
        [new-x new-h  ]))))

(defn touches-region? [[start end] [x1 y1 x2 y2]]
  (or (<= x1 start x2)
      (<= x1 end x2)
      (< x1 start end x2)))

(defn regions-touch? [[ax1 ay1 ax2 ay2]
                      [bx1 by1 bx2 by2]]
  (not (or (< (inc ax2) bx1)
           (< (inc bx2) ax1)
           (< (inc ay2) by1)
           (< (inc by2) ay1))))

(defn merge-regions
  ([r1 r2]
   (let [[x11 y11 x12 y12] r1
         [x21 y21 x22 y22] r2]
     [(min x11 x21) (min y11 y21) (max x12 x22) (max y12 y22)]))
  ([[region & rs]]
   (if (seq rs)
     (if (next rs)
       (merge-regions [region (merge-regions rs)])
       (merge-regions region (first rs)))
     region)))

(defn slice->region [[start end] width]
  [(mod start width)
   (quot start width)
   (mod end width)
   (quot end width)])

(defn find-sprite [result]
  (loop [sprite #{(first result)}
         remain (disj (set result) (first sprite))]
    (let [matches (filter (fn [region]
                            (some #(regions-touch? % region) sprite))
                          remain)]
      (if (seq matches)
        (recur (into sprite matches)
               (apply disj remain matches))
        [(reduce merge-regions sprite) remain]))))

(defn all-sprite-regions [^BufferedImage img]
  (let [width (.getWidth img)]
    (loop [sprites #{}
           regions (-> img
                       raster-data
                       (find-slices width)
                       (->> (map #(slice->region % width))))]
      (if (seq regions)
        (let [[sprite remain] (find-sprite regions)]
          (recur (conj sprites sprite)
                 remain))
        sprites))))

(defn img-slice [img [x1 y1 x2 y2]]
  (let [src-raster (image-raster img)
        width (inc (- x2 x1))
        height (inc (- y2 y1))
        dest (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)
        ^java.awt.image.WritableRaster dest-raster (image-raster dest)]
    (doseq [x (range width)
            y (range height)
            px (.getPixel src-raster (int (+ x1 x)) (int (+ y1 y)) float4)]
      (.setPixel dest-raster (int x) (int y) float4))
    dest))


(defn split-img! [path dest]
  (let [img (ImageIO/read (io/file path))]
    (doseq [[x1 y1 x2 y2 :as sprite] (all-sprite-regions img)]
      (try
        (ImageIO/write
         ^java.awt.image.RenderedImage (img-slice img sprite)
         "png"
         (io/file (str dest "_" x1 "_" y1 "_" (inc (- x2 x1)) "x" (inc (- y2 y1)) ".png")))
        (catch Exception e
          (prn sprite))))))

(defn pack-tiles [path dest min-size max-size]
  (let [img (ImageIO/read (io/file path))]
    (doseq [[x1 y1 x2 y2 :as sprite] (all-sprite-regions img)]
      (try
        (ImageIO/write
         ^java.awt.image.RenderedImage (img-slice img sprite)
         "png"
         (io/file (str dest "_" x1 "_" y1 "_" (inc (- x2 x1)) "x" (inc (- y2 y1)) ".png")))
        (catch Exception e
          (prn sprite))))))

(comment
  (split-img! "/tmp/minispel/tekening.png" "/tmp/minispel/sprite_")
  (split-img! "/home/arne/GameAssets/tavern inn by CBL/sprites/tilableFloorsAndWalls.png"
              "/home/arne/GameAssets/tavern inn by CBL/individual/floors_walls_")
  (split-img! "/home/arne/GameAssets/tavern inn by CBL/sprites/originalIndoorSprites.png"
              "/home/arne/GameAssets/tavern inn by CBL/individual/indoor_")
  (split-img! "/home/arne/GameAssets/tavern inn by CBL/sprites/update01.png"
              "/home/arne/GameAssets/tavern inn by CBL/individual/indoor_update_"))
