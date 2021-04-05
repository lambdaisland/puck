(ns lambdaisland.puck.util)

(defn keypaths [m]
  (mapcat (fn [[k v]]
            (if (map? v)
              (map #(into [k] %) (keypaths v))
              [[k]]))
          m))
