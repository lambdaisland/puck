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
