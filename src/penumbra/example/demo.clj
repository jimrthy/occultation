(ns penumbra.example.demo
  (:require [com.stuartsierra.component :as component]
            [penumbra.system :as system])
  (:gen-class))

(defn wrapper
  [options]
  (let [base-system (system/init options)]
    (component/start base-system)))

(defn -main [& args]
  "Run an individual demo, from the first ns in args"
  (let [namespace (symbol (first args))]
    (require [namespace])
    (let [cb (ns-resolve namespace 'callbacks)
          initial (ns-resolve namespace 'initial-state)]
      (future
        (let [base-callbacks {}
              app (wrapper {:callbacks (into base-callbacks cb)
                            :state (initial)})
              done (:done app)]
          @done)))))

