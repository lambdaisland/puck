(ns lambdaisland.puck.types
  "Extend Pixi and DOM types with ClojureScript protocols."
  (:require ["pixi.js" :as pixi]
            ["resource-loader" :as resource-loader]
            [clojure.string :as str]
            [applied-science.js-interop :as j]
            [camel-snake-kebab.core :as csk]
            [lambdaisland.data-printers :as data-printers]
            [lambdaisland.dom-types]
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
(register-keys-printer pixi/AnimatedSprite 'pixi/AnimatedSprite [:position :anchor :scale :textures :animationSpeed :autoUpdate :currentFrame :loop :playing :totalFrames])
(register-keys-printer pixi/Texture 'pixi/Texture [:baseTexture :orig :trim])
(register-keys-printer pixi/BaseTexture 'pixi/BaseTexture [:width :height :resolution :resource])
(register-keys-printer pixi/Rectangle 'pixi/Rectangle [:x :y :width :height])
(register-keys-printer pixi/Graphics 'pixi/Graphics [:fill :line :tint])
(register-keys-printer pixi/FillStyle 'pixi/FillStyle [:alpha :color :matrix :texture :visible])
(register-keys-printer pixi/LineStyle 'pixi/LineStyle [:alignment :cap :join :miterLimit :native :width :alpha :color :matrix :texture :visible])

(register-keys-printer resource-loader/Resource 'resource-loader/Resource [:name :url :type :metadata :spritesheet :textures])
(register-keys-printer pixi/resources.Resource 'pixi/Resource [:name :url :type :metadata :spritesheet :textures])
(register-keys-printer pixi/resources.ImageResource 'pixi/ImageResource [:url])

(register-keys-printer pixi/Ticker 'pixi/Ticker [:deltaTime :deltaMS :elapsedMS :lastTime :speed :started])


(extend-type pixi/Container
  ISeqable
  (-seq [this]
    (seq (.-children this)))
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

(doseq [t [pixi/Application pixi/Renderer pixi/Loader pixi/resources.Resource pixi/Point pixi/ObservablePoint
           pixi/Matrix pixi/Transform #_pixi/Container pixi/Sprite pixi/Texture pixi/BaseTexture pixi/Rectangle
           resource-loader/Resource pixi/resources.Resource pixi/resources.ImageResource pixi/Ticker
           pixi/InteractionEvent pixi/InteractionData js/Window js/PointerEvent
           js/MouseEvent js/WheelEvent js/DragEvent]]
  (lookupify t))

(extend-type js/Node
  ITransientCollection
  (-conj! [^js this child]
    (.appendChild this child)))
