(ns lambdaisland.puck
  (:require ["pixi.js" :as pixi]
            ["hammerjs" :as Hammer]
            [clojure.string :as str]
            [cljs-bean.core :as bean]
            [applied-science.js-interop :as j]
            [kitchen-async.promise :as p]
            [lambdaisland.puck.types]
            [camel-snake-kebab.core :as csk]
            [lambdaisland.puck.util :as util])
  (:require-macros [lambdaisland.puck :refer [assign!]]))

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
   (let [app (application (merge {:width js/window.innerWidth
                                  :height js/window.innerHeight
                                  :resolution js/window.devicePixelRatio
                                  :auto-density true
                                  :auto-resize true
                                  :resize-to js/window}
                                 opts))]
     (js/document.body.appendChild (:view app))
     app)))

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
  type you have to distinguish them by key."
  ([source type callback]
   (-listen! source type type callback))
  ([source type key callback]
   (-listen! source type key callback)))

(defn unlisten!
  "Unregister a previously registered listener."
  ([source type]
   (-unlisten! source type type))
  ([source type key]
   (-unlisten! source type key)))

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
  pixi/DisplayObject
  (-listen! [obj signal key callback]
    (-unlisten! obj signal key)
    (j/assoc-in! obj [:__listeners (key-str key)] callback)
    (.on ^js obj (key-str signal) callback))
  (-unlisten! [obj signal key]
    (when-let [callback (j/get-in obj [:__listeners (key-str key)])]
      (js-delete (j/get obj :__listeners) (key-str key))
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
    (j/assoc-in! el [:__listeners (key-str key)] cb)
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
