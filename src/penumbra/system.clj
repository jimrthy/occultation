(ns penumbra.system
  (:require [clojure.core.async :as async]
            ;; Q: Debug only??
            #_[clojure.tools.trace :as trace]
            [com.stuartsierra.component :as component]
            [penumbra
             [app :as app]
             [configuration :as config]]
            [penumbra.app.input :as input]))

(defn base-map
  [overriding-config-options]
  (component/system-map
         :app (app/ctor (select-keys overriding-config-options [:callbacks
                                                                :main-loop
                                                                :state
                                                                :threading]))
         :config overriding-config-options
         :done (promise)
         :input (input/ctor {})))

(defn dependencies
  [base]
  (component/system-using base
   {:app [:done :input]
    :input [:config]}))

(defn init
  [overriding-config-options]
  (set! *warn-on-reflection* true)

  (let [cfg (into (config/defaults) overriding-config-options)]
    ;; TODO: I really need to configure logging...don't I?
    (-> (base-map overriding-config-options)
        (dependencies))))




