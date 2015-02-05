(ns penumbra.app.minimal
  "An attempt at a minimalist wrapper.
Because it's really nice to just be able to create examples
 without worrying about boilerplate."
  (:require [com.stuartsierra.component :as component]
            [penumbra.app :as app]
            [penumbra.system :as system]))

(defn start
  [title callbacks initial-state]
  (let [base (system/init {:callbacks callbacks, :state initial-state :title title})]
    (component/start base)))
