(ns user)

(defmacro jit [sym]
  `(requiring-resolve '~sym))

(defn start-cljs
  ([]
   (start-cljs :main))
  ([build-id]
   (when (nil? @@(jit shadow.cljs.devtools.server.runtime/instance-ref))
     ((jit shadow.cljs.devtools.server/start!))
     ((jit shadow.cljs.devtools.api/watch) build-id)
     (loop []
       (when (nil? @@(jit shadow.cljs.devtools.server.runtime/instance-ref))
         (Thread/sleep 250)
         (recur))))))

(defn cljs-repl
  ([]
   (cljs-repl :main))
  ([build-id]
   (start-cljs build-id)
   ((jit shadow.cljs.devtools.api/nrepl-select) build-id)))

(defn browse []
  ((jit clojure.java.browse/browse-url) "http://localhost:8008"))

(def portal-instance (atom nil))

(defn portal
  "Open a Portal window and register a tap handler for it. The result can be
  treated like an atom."
  []
  ;; Portal is both an IPersistentMap and an IDeref, which confuses pprint.
  (prefer-method @(jit clojure.pprint/simple-dispatch) clojure.lang.IPersistentMap clojure.lang.IDeref)
  ;; Portal doesn't recognize records as maps, make them at least datafiable
  (extend-protocol clojure.core.protocols/Datafiable
    clojure.lang.IRecord
    (datafy [r] (into {} r)))
  (let [p ((jit portal.api/open) @portal-instance)]
    (reset! portal-instance p)
    (add-tap (jit portal.api/submit))
    p))
