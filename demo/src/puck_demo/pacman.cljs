(ns puck-demo.pacman
  (:require [lambdaisland.puck :as p]
            [lambdaisland.thicc :as thicc]
            [kitchen-async.promise :as promise]
            [applied-science.js-interop :as j]))

(def state (atom {}))

(def resources
  {:sprites "pacman_sprites.min.json"})

;; Initialize the pixi/Application, full-screen-app makes sure you get a canvas
;; that is always exactly the size of the browser viewport. We also add some CSS
;; to make sure we never get scrollbars or stray maring/padding.
(defonce game (p/full-screen-app {}))

;; Tell pixi not to blur when scaling
(p/pixelate!)

;; Add the view (canvas) to the DOM
(conj! (thicc/query "body") (:view game))

;; Load the sprite sheet, this returns a promise, but thanks to kitchen-async
;; that makes little difference.
;; This spritesheet JSON was created with TexturePacker.
(defn move-sprite
  "Move a sprite based on its current velocity, and the amount of time that has
  passed."
  [sprite delta]
  (j/update! sprite :x + (* (j/get-in sprite [:velocity :x] 0) delta))
  (j/update! sprite :y + (* (j/get-in sprite [:velocity :y] 0) delta)))

;; p/let is like regular let, unless you sprinkle in some ^await and ^js, then
;; it gets magical powers.
(p/let [;; load-resources takes a map from keyword to resource-url, and returns
        ;; a promise to a map with the same keys, but with the loaded resources
        ;; as values
        {:keys [sprites]} ^await (p/load-resources! game resources)

        ;; A lot of pixi object can already be destructured because we make them
        ;; implement ILookup, but if not then p/let can help you. Just tag it as
        ;; ^js and you can now destructure to your heart's content.
        {:keys [pacman__281_41 pacman__281_1]} ^js (:textures sprites)

        ;; Create an animation out of these frames
        pacman (p/animated-sprite [pacman__281_41 pacman__281_1])]
  (tap> pacman)
  ;; assign! deeply assigns values, you do a lot of this in pixi, so we added a
  ;; macro to make this easy. When you pass assign! a literal map it will emit
  ;; efficient code to set each nested property individually
  (p/assign! pacman {:animationSpeed 0.1
                     :anchor {:x 0.5 :y 0.5}
                     :position {:x 200 :y 200}
                     ;; Velocity is not a pixi thing, it's something we add
                     ;; ourselves to know which direction a sprite should move
                     :velocity {:x 1 :y 1}})
  ;; Start the animation
  (p/play! pacman)
  ;; Add the sprite to the stage container
  (conj! (:stage game) pacman)
  ;; Also store it for later reference
  (swap! state assoc :pacman pacman)

  (p/listen! (:ticker game) ::on-tick
             (fn [delta]
               (run! #(move-sprite % delta) (:stage game)))))

;; Listen to game "ticks" to do animation and handle game events
