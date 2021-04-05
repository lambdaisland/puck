(ns lambdaisland.puck.types
  "Extend Pixi and DOM types with ClojureScript protocols."
  (:require ["pixi.js" :as pixi]
            ["resource-loader" :as resource-loader]
            [clojure.string :as str]
            [applied-science.js-interop :as j]
            [camel-snake-kebab.core :as csk]
            [lambdaisland.data-printers :as data-printers]
            [clojure.core.protocols :as protocols]))

(defn lookupify
  "Access object properties via keyword access, and allow destructuring with
  `:keys`"
  [type]
  (extend-type type
    ILookup
    (-lookup
      ([this k]
       (j/get this k))
      ([this k not-found]
       (j/get this k not-found)))
    ITransientAssociative
    (-assoc! [this k v]
      (j/assoc! this k v)
      this)
    ITransientMap
    (-dissoc! [this k]
      (js-delete this k)
      this)))

(defn register-printer [type tag to-edn]
  (data-printers/register-print type tag to-edn)
  (data-printers/register-pprint type tag to-edn))

(defn register-keys-printer [type tag keys]
  (register-printer type tag (fn [obj]
                               (reduce (fn [m k]
                                         (assoc m k (j/get obj k)))
                                       {}
                                       keys)))
  (extend-type type
    protocols/Datafiable
    (datafy [obj]
      (into ^{:type type} {} (map (juxt identity #(j/get obj %))) keys))
    protocols/Navigable
    (nav [obj k v]
      (j/get obj k))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pixi

(register-keys-printer pixi/Application 'pixi/Application [:stage :renderer])
(register-keys-printer pixi/Renderer 'pixi/Renderer [:view :screen :options])
(register-keys-printer pixi/Loader 'pixi/Loader [:baseUrl :resources :progress :loading])
(register-keys-printer pixi/resources.Resource 'pixi/Resource [:name :url :type :metadata :spritesheet :textures])

(register-keys-printer pixi/Point 'pixi/Point [:x :y])

(extend-type pixi/Point
  protocols/Datafiable
  (datafy [obj]
    {:x (j/get obj :x)
     :y (j/get obj :y)}))

(register-keys-printer pixi/ObservablePoint 'pixi/ObservablePoint [:x :y])
(register-keys-printer pixi/Matrix 'pixi/Matrix [:a :b :c :d :tx :ty])
(register-keys-printer pixi/Transform 'pixi/Transform [:worldTransform :localTransform :position :scale :pivot :skew])

(register-keys-printer pixi/Container 'pixi/Container [:children :transform :visible])
(register-keys-printer pixi/Sprite 'pixi/Sprite [:position :anchor :scale :texture])
(register-keys-printer pixi/Texture 'pixi/Texture [:baseTexture :orig :trim])
(register-keys-printer pixi/BaseTexture 'pixi/BaseTexture [:width :height :resolution :resource])
(register-keys-printer pixi/Rectangle 'pixi/Rectangle [:x :y :width :height])


(register-keys-printer resource-loader/Resource 'resource-loader/Resource [:name :url :type :metadata :spritesheet :textures])
(register-keys-printer pixi/resources.Resource 'pixi/Resource [:name :url :type :metadata :spritesheet :textures])
(register-keys-printer pixi/resources.ImageResource 'pixi/ImageResource [:url])

(register-keys-printer pixi/Ticker 'pixi/Ticker [:deltaTime :deltaMS :elapsedMS :lastTime :speed :started])


(extend-type pixi/Container
  ;; Lookup by keyword for js obj attributes, but also lookup display objects as
  ;; if it's a set, so you can use `contains?`
  ILookup
  (-lookup
    ([this k]
     (if (keyword? k)
       (j/get this k)
       (when (= -1 (.indexOf (.-children this) k))
         k)))
    ([this k not-found]
     (if (keyword? k)
       (j/get this k)
       (if (not= -1 (.indexOf (.-children this) k))
         k
         not-found))))
  ITransientAssociative
  (-assoc! [this k v]
    (j/assoc! this k v)
    this)
  ITransientMap
  (-dissoc! [this k]
    (js-delete this k)
    this)
  ITransientCollection
  (-conj! [^js this v]
    (.addChild this v)
    this)
  ITransientSet
  (-disjoin! [^js this v]
    (.removeChild this v)))

(extend-type pixi/Loader
  ITransientCollection
  (-conj! [^js this [k v]]
    (.add this k v)))

(register-keys-printer pixi/InteractionEvent 'pixi/InteractionEvent [:type :target :stopped :stopsPropagationAt :stopPropagationHint :currentTarget :date])

(register-keys-printer pixi/InteractionData 'pixi/InteractionData [:global :target :originalEvent :identifier :isPrimary :button :buttons :width :height :tiltX :tiltY :pointerType :pressure :rotationAngle :twist :tangentialPressure])

(extend-protocol IAssociative
  pixi/Point
  (-assoc [coll k v]
    (doto (.clone coll)
      (j/assoc! k v)))
  pixi/Matrix
  (-assoc [coll k v]
    (doto (.clone coll)
      (j/assoc! k v)))
  pixi/Rectangle
  (-assoc [coll k v]
    (doto (.clone coll)
      (j/assoc! k v))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DOM / browser types

(def ^:dynamic *max-attr-length* 250)

(defn- truncate-attr [attr-val]
  (if (and (string? attr-val) (< *max-attr-length* (.-length attr-val)))
    (str (subs attr-val 0 *max-attr-length*) "...")
    attr-val))

(register-printer js/Text js/Text #(.-data %))

(register-printer
 js/Element
 'js/Element (fn hiccupize [^js e]
               (cond
                 (string? e)
                 e
                 (instance? js/Text e)
                 (.-data e)
                 :else
                 (let [el [(keyword (str/lower-case (.-tagName e)))]]
                   (into (if-let [attrs (seq (.getAttributeNames e))]
                           (conj el (into {}
                                          (map (juxt csk/->kebab-case-keyword
                                                     #(truncate-attr (.getAttribute e %))))
                                          attrs))
                           el)
                         (map hiccupize)
                         (.-childNodes e))))))

(register-printer js/HTMLDocument 'js/HTMLDocument (fn [^js d] {:root (.-documentElement d)}))
(register-printer js/XMLDocument 'js/XMLDocument (fn [^js d] {:root (.-documentElement d)}))
(register-printer js/Document 'js/Document (fn [^js d] {:root (.-documentElement d)}))

(extend-type js/Node
  ITransientCollection
  (-conj! [^js this child]
    (.appendChild this child)))

(register-keys-printer js/Window 'js/Window [:location :document :devicePixelRatio :innerWidth :innerHeight])

(register-printer js/Location 'js/Location str)

(register-printer js/HTMLCollection 'js/HTMLCollection #(into [] %))
(register-printer js/NodeList 'js/NodeList #(into [] %))


(when (exists? js/KeyboardEvent)
  (register-keys-printer js/KeyboardEvent 'js/KeyboardEvent [:type :code :key :ctrlKey :altKey :metaKey :shiftKey :isComposing :location :repeat]))

(when (exists? js/TouchEvent)
  (register-keys-printer js/TouchEvent 'js/TouchEvent [:altKey :changedTouches :ctrlKey :metaKey :shiftKey :targetTouches :touches])
  (register-keys-printer js/Touch 'js/Touch [:identifier :screenX :screenY :clientX :clientY :pageX :pageY :target])
  (register-printer js/TouchList 'js/TouchList (comp vec seq))
  (lookupify js/TouchEvent)
  (lookupify js/Touch)
  (lookupify js/TouchList))

(register-keys-printer js/PointerEvent 'js/PointerEvent [:pointerId :width :height :pressure
                                                         :tangentialPressure :tiltX :tiltY
                                                         :twist :pointerType :isPrimary])

(def mouse-event-keys [:altKey :button :buttons :clientX :clientY :ctrlKey :metaKey :movementX :movementY
                       ;; "These are experimental APIs that should not be used in production code" -- MDN
                       ;; :offsetX :offsetY :pageX :pageY
                       :region :relatedTarget :screenX :screenY :shiftKey])

(register-keys-printer js/MouseEvent 'js/MouseEvent mouse-event-keys)
(register-keys-printer js/WheelEvent 'js/WheelEvent (conj mouse-event-keys :deltaX :deltaY :deltaZ :deltaMode))
(register-keys-printer js/DragEvent 'js/DragEvent (conj mouse-event-keys :dataTransfer))

(doseq [t [pixi/Application pixi/Renderer pixi/Loader pixi/resources.Resource pixi/Point pixi/ObservablePoint
           pixi/Matrix pixi/Transform #_pixi/Container pixi/Sprite pixi/Texture pixi/BaseTexture pixi/Rectangle
           resource-loader/Resource pixi/resources.Resource pixi/resources.ImageResource pixi/Ticker
           pixi/InteractionEvent pixi/InteractionData js/Window js/PointerEvent
           js/MouseEvent js/WheelEvent js/DragEvent]]
  (lookupify t))
