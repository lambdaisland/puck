(ns puck-demo.pixel-editor
  (:require [clojure.datafy :refer [datafy]]
            [clojure.string :as str]
            [lambdaisland.puck :as p]
            [lambdaisland.puck.math :as m]
            [lambdaisland.thicc :as thicc]
            [kitchen-async.promise :as promise]
            [applied-science.js-interop :as j]))

;; https://www.procjam.com/tutorials/wfc/
;; https://github.com/mxgmn/WaveFunctionCollapse

(defn fixed-color-pixels [width height rgba]
  (vec
   (for [x (range width)]
     (vec
      (for [y (range height)]
        rgba)))))

(def size 8)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Viewers

(defn hiccup [html]
  (with-meta
    html
    {:portal.viewer/default :portal.viewer/hiccup}))

(defn pixel-grid [pixels]
  (hiccup
   [:div {:style {:display "flex"}}
    (for [col pixels]
      [:div {:style {:display "flex" :flex-direction "column"}}
       (for [color col]
         [:div {:style {:width "5px" :height "5px" :background-color (str "rgba(" (str/join "," color) ")")}}])])]))

(def pixels> (comp tap> pixel-grid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn pixel-buffer-resource [{:keys [width height pixels]}]
  (p/buffer-resource
   (p/->u8a
    (for [y (range height)
          x (range width)
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

(defn editor-handlers
  "Generate stateful handlers by closing over the state atoms"
  [state pixi-state id]
  (let [handle-draw-event
        (fn [e]
          (let [{:keys [sprite buffer]} (get @pixi-state id)]
            (when (= sprite (:target e))
              (j/let [{:keys [x y]} (p/local-position (:data e) sprite)]
                (swap! state assoc-in
                       [id :pixels (Math/floor x) (Math/floor y)]
                       (-> @state id :color))
                (update-buffer buffer (get @state id))))))]
    {:mousedown (fn [e]
                  (swap! state assoc-in [id :drawing?] true)
                  (handle-draw-event e))
     :mousemove (fn [e]
                  (when (:drawing? (get @state id))
                    (handle-draw-event e)))
     :mouseup (fn [e]
                (swap! state assoc-in [id :drawing?] false))}))

;; End stateful editor functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init function, take [state pixi-state options],
;; return updates [state pixi-state]

(defn init-pixi-app [state pixi-state {:keys [parent]}]
  (p/pixelate!)
  (let [app (p/full-screen-app {})]
    (conj! parent (:view app))
    [state
     (assoc pixi-state :app app)]))

(defn init-pixel-sprite [state
                         {:keys [app] :as pixi-state}
                         {:keys [id
                                 x y scale
                                 width height
                                 background foreground
                                 handlers
                                 pixels]
                          :or {x 10 y 10 scale 20}}]
  (let [my-state (merge {:width width
                         :height height
                         :pixels (fixed-color-pixels width height background)
                         :color foreground}
                        (get state id))
        buffer (pixel-buffer-resource my-state)
        sprite (doto (-> buffer
                         p/base-texture
                         (p/texture (p/rectangle 0 0 width height))
                         p/sprite)
                 (p/assign! {:x x
                             :y y
                             :scale {:x scale :y scale}
                             :interactive true}))
        {:keys [mousedown mousemove mouseup]} handlers]

    (conj! (:stage app) sprite)

    (when mousedown
      (p/listen! sprite
                 [:mousedown :touchstart]
                 mousedown))
    (when mousemove
      (p/listen! sprite
                 [:mousemove :touchmove]
                 mousemove))
    (when mouseup
      (p/listen! sprite
                 [:mouseup :mouseupoutside :touchend :touchendoutside]
                 mouseup))

    [(assoc state id my-state)
     (assoc pixi-state id {:sprite sprite :buffer buffer})]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Wave function collapse

(defn pixel-patterns
  ([state size]
   (pixel-patterns state size size))
  ([{:keys [width height pixels]} pattern-width pattern-height]
   (for [x (range (- width (dec pattern-width)))
         y (range (- height (dec pattern-height)))]
     (vec
      (for [x (range x (+ x pattern-width))]
        (vec
         (for [y (range y (+ y pattern-height))]
           (get-in pixels [x y]))))))))

(comment
  (pixel-patterns {:width 4
                   :height 4
                   :pixels
                   (vec
                    (for [x (range 4)]
                      (vec
                       (for [y (range 4)]
                         (symbol (str (nth "abcd" x) (nth "abcd" y)))))))}
                  3))

(defn composit
  "Composit two rgba pixels
  https://ciechanow.ski/alpha-compositing/"
  [d s]
  (let [da (/ (last d) 255)
        sa (/ (last s) 255)
        ra (+ sa (* da (- 1 sa)))]
    (conj
     (mapv
      (fn [d s]
        (/ (+ (* s sa) (* d da (- 1 sa))) ra))
      (butlast d)
      (butlast s))
     ra)))

#_(tap> (map pixel-grid (set (pixel-patterns @state 3))))

(defn init-wave
  "Initial \"wave\", completely unobserved, all patterns are still possible at all
  positions."
  [{:keys [width height pattern-width pattern-height pattern-count]}]
  (vec
   (for [x (range (- width (dec pattern-width)))]
     (vec
      (for [y (range (- height (dec pattern-height)))]
        (vec
         (for [z (range pattern-count)]
           true)))))))

#_(init-wave {:width 3
              :height 3
              :pattern-width 3
              :pattern-height 3
              :pattern-count 1})

(defonce state (atom {}))
(defonce pixi-state (atom {}))

(defn init1! [f opts]
  (let [[s ps] (f @state @pixi-state opts)]
    (reset! state s)
    (reset! pixi-state ps)))

(defn init-all! []
  (init1! init-pixi-app {:parent (thicc/query "body")})
  (init1! init-pixel-sprite {:id :editor
                             :x 10
                             :y 10
                             :scale 20
                             :width 7
                             :height 7
                             :background [255 255 255 255]
                             :foreground [0 0 125 255]
                             :handlers (editor-handlers state pixi-state :editor)})
  (init1! init-pixel-sprite {:id :wfc
                             :x 10
                             :y 170
                             :scale 20
                             :width 10
                             :height 10
                             :background [0 100 0 255]}))

(defn reinit!
  ([]
   (reinit! false))
  ([hard?]
   (when hard?
     (reset! state {}))
   (reset! pixi-state {})
   (run! #(.remove %) (thicc/query-all "canvas"))
   (init-all!)))

(defonce init-once (init-all!))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; REPL API

(defn clear! [id color]
  (let [{:keys [width height]} (get @state id)
        {:keys [buffer]} (get @pixi-state id)]
    (swap! state
           assoc-in [id :pixels]
           (fixed-color-pixels width height color))
    (update-buffer buffer (get @state id))))

#_(clear! :editor [100 0 0 255])

(comment
  (tap> (pixel-grid pixels1))
  (tap> (pixel-grid (mapv (fn [col1 col2]
                            (map composit col1 col2))
                          pixels1 pixels2)))
  (tap> @state)

  (tap> (pixel-grid (get-in @state [:editor :pixels])))


  (run! pixels> (pixel-patterns (:editor @state) 3 3))
  (count (pixel-patterns (:editor @state) 3 3)))

(let [{:keys [editor wfc]} @state
      {:keys [width height]} wfc
      pwidth 3
      pheight 3
      patterns (vec (pixel-patterns editor pwidth pheight))
      wfc (assoc wfc
                 :patterns patterns
                 :pattern-width pwidth
                 :pattern-height pheight
                 :pattern-count (count patterns))]
  (swap! state assoc :wfc (assoc wfc :wave (init-wave wfc))))

(defn composit-equal [pixels]
  (reduce composit
          (map #(assoc % 3 112) pixels)))

(defn render-wfc [{:keys [width
                          height
                          pattern-width
                          pattern-height
                          wave
                          patterns
                          pattern-count] :as wfc}]
  (vec
   (for [x (range width)]
     (vec
      (for [y (range height)]
        (composit-equal
         (for [px (range (min (inc x) pattern-width (- width x)))
               py (range (min (inc y) pattern-height (- height y)))
               pz (range pattern-count)
               :when (get-in wave [(+ x px) (+ y py) pz])]
           (get-in patterns [pz
                             (- (dec pattern-width) px)
                             (- (dec pattern-height) py)]))))))))


(defn collapse [{:keys [pattern-count] :as wfc} x y pz]
  (update wfc
          :wave
          assoc-in [x y]
          (assoc (vec (repeat pattern-count false))
                 pz true)))
(comment
  (doseq [x (range 10)
          y (range 10)]
    (swap! state update :wfc collapse x y 2))

  (run! pixels> (:patterns (:wfc @state)))

  (pixels> (get-in @state [:wfc :patterns 2]))
  (pixels> (render-wfc (:wfc @state)))

  (get-in (:patterns (:wfc @state)) [23 2 2])

  (:wave (:wfc @state))

  (swap! state update-in [:wfc :wave] (fn [w] (mapv #(mapv (constantly [false false false false false false false false false false false false false false false false false false false false false false true false false]) %) w)))

  (swap! state assoc-in [:wfc :wave 1 0] [false false true false false false false false false false false false false false false false false false false false false false false false false])
  (get-in @state [:wfc :wave])
  [false false true false false false false false false false false false false false false false false false false false false false false false false]
;; =>
;; => true
;;=> 47

  #_(tap> (:wfc @state))
  )
(tap> @pixi-state)
