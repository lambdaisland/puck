(ns lambdaisland.puck
  (:require ["pixi.js" :as pixi]
            ["@pixi/utils" :as pixi-utils]
            [clojure.string :as str]
            [applied-science.js-interop :as j]
            [kitchen-async.promise :as p]
            [lambdaisland.puck.types]
            [camel-snake-kebab.core :as csk]
            [lambdaisland.puck.util :as util])
  (:require-macros [lambdaisland.puck :refer [assign! with-fill]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers

(defn opts->js [m]
  (clj->js m :keyword-fn csk/->camelCaseString))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Initialization

(defn application ^js [pixi-opts]
  (pixi/Application. (opts->js pixi-opts)))

(defn full-screen-app
  ([]
   (full-screen-app nil))
  ([opts]
   (application (merge {:width js/window.innerWidth
                        :height js/window.innerHeight
                        :resolution js/window.devicePixelRatio
                        :auto-density true
                        :auto-resize true
                        :resize-to js/window}
                       opts))))

(defn say-hello! []
  (.sayHello ^js pixi/utils (if (.isWebGLSupported ^js pixi/utils)
                              "WebGL"
                              "canvas")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Settings

(defn pixelate!
  "Tell pixi to not blur when scaling."
  []
  (j/assoc-in! pixi/settings [:SCALE_MODE] pixi/SCALE_MODES.NEAREST))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Event / callback handling

(defonce listeners (atom {}))

(defprotocol EventSource
  (-listen! [source type key callback])
  (-unlisten! [source type key]))

(defn listen!
  "Add a callback/event listener to an event source. Works on pixi/Application, or
  on display objects like sprites.

  Adding a new listener for the same source/type/key will replace the previous
  one (similar to `add-watch`, handy for REPL or hot reload). The key defaults
  to `type`, so if you want multiple listeners with the same source and event
  type you have to distinguish them by key.

  You can pass a collection for `type` to listen to multiple signals in one go."
  ([source type callback]
   (if (coll? type)
     (run! #(listen! source % % callback) type)
     (-listen! source type type callback)))
  ([source type key callback]
   (if (coll? type)
     (run! #(listen! source % key callback) type)
     (-listen! source type key callback))))

(defn unlisten!
  "Unregister a previously registered listener."
  ([source type]
   (if (coll? type)
     (run! #(unlisten! source % %) type)
     (-unlisten! source type type)))
  ([source type key]
   (if (coll? type)
     (run! #(unlisten! source % key) type)
     (-unlisten! source type key))))

(defn- key-str [k]
  (cond
    (string? k)
    k
    (simple-keyword? k)
    (name k)
    (qualified-keyword? k)
    (str (namespace k) "/" (name k))
    :else
    (str k)))

(extend-protocol EventSource
  pixi/Application
  (-listen! [app signal key callback]
    (-listen! (j/get (get-in app [:renderer :runners]) signal) signal key callback))
  (-unlisten! [app signal key]
    (-unlisten! (j/get (get-in app [:renderer :runners]) signal) signal key))

  ;; Hook into pixi's \"Runners\", event listeners attached to the renderer. Known signals:
  ;; - `:destroy`
  ;; - `:contextChange`
  ;; - `:reset`
  ;; - `:update`
  ;; - `:postrender`
  ;; - `:prerender`
  ;; - `:resize`
  pixi/Runner
  (-listen! [runner signal key callback]
    (-unlisten! runner signal key)
    (let [signal (key-str signal)
          key    (key-str key)
          item   (j/lit {signal callback})]
      (j/assoc-in! runner [:__listeners signal key] item)
      (.add ^js runner item)))
  (-unlisten! [runner signal key]
    (let [signal (key-str signal)
          key    (key-str key)]
      (when-let [item (j/get-in runner [:__listeners signal key])]
        (js-delete (j/get-in runner [:__listeners signal]) key)
        (.remove ^js runner item))))

  ;; In the case of Ticker there is no signal as such, it's only used to
  ;; differentiate listeners
  pixi/Ticker
  (-listen! [ticker signal key callback]
    (-unlisten! ticker signal key)
    (let [signal (key-str signal)
          key    (key-str key)]
      (j/assoc-in! ticker [:__listeners signal key] callback)
      (.add ^js ticker callback)))
  (-unlisten! [ticker signal key]
    (let [signal (key-str signal)
          key    (key-str key)]
      (when-let [callback (j/get-in ticker [:__listeners signal key])]
        (js-delete (j/get-in ticker [:__listeners signal]) key)
        (.remove ^js ticker callback))))

  ;; Listen to events like :mousedown or :touchstart. Works on sprites,
  ;; graphics, containers, etc. Make sure to `(j/assoc! sprite :interactive
  ;; true)` so the InteractionManager kicks in.
  pixi-utils/EventEmitter
  (-listen! [obj signal key callback]
    (-unlisten! obj signal key)
    (j/assoc-in! obj [:__listeners (key-str signal) (key-str key)] callback)
    (.on ^js obj (key-str signal) callback))
  (-unlisten! [obj signal key]
    (when-let [callback (j/get-in obj [:__listeners (key-str signal) (key-str key)])]
      (js-delete (j/get-in obj [:__listeners (key-str signal)]) (key-str key))
      (.removeListener ^js obj (key-str signal) callback)))

  ;; Add listener to a Loader, signal can be one of `:error`, `:load`, `:start`,
  ;; `:complete`, `:progress`
  pixi/Loader
  (-listen! [loader signal key callback]
    (-unlisten! loader signal key)
    (let [binding (.add ^js (j/get loader (case signal
                                            :error :onError
                                            :load :onLoad
                                            :start :onStart
                                            :complete :onComplete
                                            :progress :onProgress))
                        callback)]
      (j/assoc-in! loader [:__listeners (key-str signal) (key-str key)] binding)))
  (-unlisten! [loader signal key]
    (when-let [binding (j/get-in loader [:__listeners (key-str signal) (key-str key)])]
      (js-delete (j/get-in loader [:__listeners (key-str signal)]) (key-str key))
      (.detach ^js binding)))

  ;; Also implement listen! / unlisten! for dom elements, so you can use it e.g.
  ;; for global keyboard events.
  js/Element
  (-listen! [el sig key cb]
    (-unlisten! el sig key)
    (j/assoc-in! el [:__listeners (key-str sig) (key-str key)] cb)
    (.addEventListener el (key-str sig) cb))
  (-unlisten! [el sig key]
    (when-let [listener (j/get-in el [:__listeners (key-str sig) (key-str key)])]
      (js-delete (j/get-in el [:__listeners (key-str sig)]) (key-str key))
      (.removeEventListener el sig listener)))

  js/Window
  (-listen! [win sig key cb]
    (-unlisten! win sig key)
    (j/assoc-in! win [:__listeners (key-str key)] cb)
    (.addEventListener win (key-str sig) cb))
  (-unlisten! [win sig key]
    (when-let [listener (j/get-in win [:__listeners (key-str sig) (key-str key)])]
      (js-delete (j/get-in win [:__listeners (key-str sig)]) (key-str key))
      (.removeEventListener win sig listener))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Resources / Textures / Resource loading

(defn resource
  "Gets a resource from the app's resource loader by name, only does a simple key
  lookup, you have to make sure the resource is loaded first."
  [app resource-name]
  (j/get (get-in app [:loader :resources]) (key-str resource-name)))

(defn resource-texture
  "Get a texture by name from a resource that contains multiple textures (i.e. a
  sprite sheet)."
  [app resource-name texture-name]
  (j/get (:textures (resource app resource-name)) texture-name))

(defn base-texture
  "Construct a new BaseTexture"
  ([resource]
   (base-texture resource nil))
  ([resource options]
   (pixi/BaseTexture. resource options)))

(defn texture
  "Construct a texture based on a base texture and a rect."
  [base rect]
  (pixi/Texture. base rect))

(defn load-resources!
  "Convenience helper for loading resources, takes a map of resource-name => url,
  returns a promise which resolves to a map of resource-name => resource when
  the loading has finished."
  [app resources]
  (let [rkeys (keys resources)
        ^js loader (:loader app)]
    (p/promise [resolve reject]
      (when-let [missing (seq (remove #(resource app %) rkeys))]
        (doseq [k missing]
          (.add loader (name k) (get resources k))))
      (.load loader
             (fn []
               (resolve (into {} (map (juxt identity #(resource app %))) rkeys)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Geometry

(defn bounds
  [^js renderable]
  (.getBounds renderable))

(defn local-bounds
  "Retrieves the local bounds of the displayObject or container as a rectangle object.

  - `^pixi/Rectangle rect` Optional rectangle to store the result of the bounds calculation
  - `^boolean skip-update-children?` Only for containers, stop re-calculation of children transforms, defaults to false
  - returns `pixi/Rectangle`"
  ([^js renderable]
   (.getLocalBounds renderable))
  ([^js renderable ^js rect]
   (.getLocalBounds renderable rect))
  ([^js renderable ^js rect ^boolean skip-update-children?]
   (.getLocalBounds renderable rect skip-update-children?)))

(defn local-position [interaction-data display-object]
  (.getLocalPosition ^js interaction-data ^js display-object))

(defn global-position [display-object]
  (.getGlobalPosition display-object))

(defn point
  "Create a pixi/Point (2D vector)"
  [x y]
  (pixi/Point. x y))

(defn rectangle
  "Create a new pixi/Rectangle"
  [x y w h]
  (pixi/Rectangle. x y w h))

(defn rect-overlap?
  "Do the AABB boxes of the two display objects overlap/touch. Useful for
  rudimentary collision detection."
  [a b]
  (let [ab (bounds a)
        bb (bounds b)]
    (and (< (:x bb) (+ (:x ab) (:width ab)))
         (< (:x ab) (+ (:x bb) (:width bb)))
         (< (:y bb) (+ (:y ab) (:height ab)))
         (< (:y ab) (+ (:y bb) (:height bb))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Display objects

(defn sprite
  "Turn a resource or texture into a sprite"
  [resource-or-texture]
  (pixi/Sprite. (if-let [texture (:texture resource-or-texture)]
                  texture
                  resource-or-texture)))

(defn animated-sprite
  "Create an animated sprite from a sequence of textures"
  [textures]
  (pixi/AnimatedSprite. (into-array textures)))

(defn text
  "Create a text display object with a given message and, optionally, style."
  ([msg] (pixi/Text. msg))
  ([msg style] (pixi/Text. msg style)))

(defn container
  "Create a container and populate it"
  [opts & children]
  (let [c (pixi/Container.)]
    (assign! c opts)
    (apply conj! c children)
    c))

(defn graphics
  "Create a new Graphics drawing context"
  ([]
   (pixi/Graphics.))
  ([geometry]
   (pixi/Graphics. geometry)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Operations

(defn remove-children
  "Remove all children from a container"
  [^js container]
  (.removeChildren container))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Graphics API

(defn js-object [m]
  (let [obj #js {}]
    (reduce-kv (fn [_ k v]
                 (unchecked-set obj
                                (if (keyword? k)
                                  (name k)
                                  (str k))
                                v))
               nil
               m)
    obj))

(defn begin-fill [g fill-opts]
  (if (number? fill-opts)
    (.beginFill ^js g fill-opts)
    (.beginTextureFill ^js g (js-object fill-opts))))

(defn end-fill [g]
  (.endFill ^js g))

(defn line-style
  "- alignment
   - alpha
   - cap
   - color
   - join
   - matrix
   - miterLimit
   - native
   - texture
   - visible
   - width"
  [g line-opts]
  (.lineStyle g (js-object line-opts)))

(defn draw-line
  ([g p1 p2]
   (.drawPolygon ^js g (j/lit [p1 p2])))
  ([g x1 y1 x2 y2]
   (draw-line g (point x1 y1) (point x2 y2))))

(defn draw-rect [g x y width height]
  (.drawRect ^js g x y width height))

(defn draw-circle [g x y radius]
  (.drawCircle ^js g x y radius))

(defn draw-ellipse [g x y width height]
  (.drawEllipse ^js g x y width height))

(defn clear!
  "Clear the graphics object and reset fill/line styles"
  [g]
  (.clear ^js g))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Animation

(defn play! [animated-sprite]
  (.play ^js animated-sprite))

(defn stop! [animated-sprite]
  (.stop ^js animated-sprite))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Direct manipulation of textures

(defn ->u32a
  "Convert to Uint32Array"
  [coll]
  (cond
    (and (or (seq? coll) (seqable? coll))
         (not (array? coll)))
    (->u32a (into-array coll))
    (instance? js/ArrayBuffer coll)
    (js/Uint32Array. coll)
    :else
    (js/Uint32Array.from coll)))

(defn ->u8a
  "Convert a seq/seqable with 32-bit integers into a Uint8Array"
  [coll]
  (cond
    (and (or (seq? coll) (seqable? coll))
         (not (array? coll)))
    (->u8a (into-array coll))
    (instance? js/ArrayBuffer coll)
    (js/Uint8Array. coll)
    :else
    (js/Uint8Array.from coll)))

(defn buffer-resource
  "Create a BufferResource from a Uint8Array

  size-options:
  - `:width`
  - `:height`"
  [u8a size-options]
  (pixi/resources.BufferResource.
   u8a
   (if (map? size-options)
     #js {:width (:width size-options)
          :height (:height size-options)}
     size-options)))
