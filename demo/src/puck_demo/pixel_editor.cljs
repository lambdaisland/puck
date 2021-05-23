(ns puck-demo.pixel-editor
  (:require [clojure.datafy :refer [datafy]]
            [lambdaisland.puck :as p]
            [lambdaisland.puck.math :as m]
            [lambdaisland.thicc :as thicc]
            [kitchen-async.promise :as promise]
            [applied-science.js-interop :as j]))

(defn fixed-color-pixels [width height rgba]
  (vec
   (for [x (range width)]
     (vec
      (for [y (range height)]
        rgba)))))

(def size 8)

(def state (atom {:width size
                  :height size
                  :pixels (fixed-color-pixels size size [255 255 255 255])
                  :color [0 0 0 255]}))

(def pixi-state (atom {}))

(defn pixels->resource [{:keys [width height pixels]}]
  (p/buffer-resource
   (p/->u8a
    (for [x (range width)
          y (range height)
          x (get-in pixels [x y])]
      x))
   {:width width
    :height height}))

(defn update-buffer [buffer {:keys [width height pixels]}]
  (let [u8a (.-data buffer)]
    (doseq [x (range width)
            y (range height)
            z (range 4)]
      (aset u8a (+ z (* (+ x (* y height)) 4)) (get-in pixels [x y z]))))
  (.update ^js buffer))

(defn draw-pixel! [e]
  (let [{:keys [canvas canvas-buffer]} @pixi-state]
    (when (= canvas (:target e))
      (j/let [{:keys [x y]} (p/local-position (:data e) canvas)]
        (swap! state assoc-in
               [:pixels (Math/floor x) (Math/floor y)]
               (:color @state))
        (update-buffer canvas-buffer @state)))))

(defn init! [{:keys [width height]}]
  (p/pixelate!)
  (let [app (p/full-screen-app {})
        canvas-buffer (pixels->resource @state)
        canvas (doto (-> canvas-buffer
                         p/base-texture
                         (p/texture (p/rectangle 0 0 width height))
                         p/sprite)
                 (p/assign! {:x 10
                             :y 10
                             :scale {:x 20 :y 20}
                             :interactive true}))]
    (conj! (:stage app) canvas)
    (conj! (thicc/query "body") (:view app))

    (doto canvas
      (p/listen!
       [:mousedown :touchstart]
       (fn [e]
         (swap! state assoc :drawing? true)
         (draw-pixel! e)))

      (p/listen!
       [:mousemove :touchmove]
       (fn [e]
         (when (:drawing? @state)
           (draw-pixel! e))))

      (p/listen!
       [:mouseup :mouseupoutside :touchend :touchendoutside]
       (fn [e]
         (swap! state assoc :drawing? false))))

    {:app app
     :canvas canvas
     :canvas-buffer canvas-buffer}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Wave function collapse

(defn init-wfc [{:keys [app] :as state}]
  (let [size 50
        buffer (pixels->resource {:width size :height size :pixels (fixed-color-pixels size size [50 200 50 255])})
        sprite (doto (-> buffer
                         p/base-texture
                         (p/texture (p/rectangle 0 0 size size))
                         p/sprite)
                 (p/assign! {:x 10
                             :y 200
                             :scale {:x 20 :y 20}}))]
    (conj! (:stage app) sprite)
    (assoc state
           :wfc {:buffer buffer
                 :sprite sprite})))

(defn pixel-slices [{:keys [width height pixels]} size]
  (for [x (range (- width (dec size)))
        y (range (- height (dec size)))]
    (vec
     (for [x (range x (+ x size))]
       (vec
        (for [y (range y (+ y size))]
          (get-in pixels [x y])))))))

(comment
  (pixel-slices {:width 4
                 :height 4
                 :pixels
                 (vec
                  (for [x (range 4)]
                    (vec
                     (for [y (range 4)]
                       (symbol (str (nth "abcd" x) (nth "abcd" y)))))))}
                3))

(defonce init-once (reset! pixi-state (-> @state
                                          init!
                                          init-wfc)))

#_(tap> @pixi-state)

#_(swap! state assoc :color [100 255 0 255])
