(ns penumbra.system
  (:require [clojure.core.async :as async]
            ;; Q: Debug only??
            #_[clojure.tools.trace :as trace]
            [com.stuartsierra.component :as component]
            [penumbra
             [app :as app]
             [base :as base]
             [configuration :as config]]
            [penumbra.app
             [input :as input]
             [window :as window]]))

(defn base-map
  [overriding-config-options]
  (component/system-map
         :app (app/ctor (select-keys overriding-config-options [:callbacks
                                                                :clock
                                                                :controller
                                                                :event-handler
                                                                :input
                                                                :main-loop
                                                                :parent
                                                                :queue
                                                                :state
                                                                :threading]))
         :base (base/ctor (select-keys overriding-config-options [:error-callback]))
         :done (promise)
         :input (input/ctor overriding-config-options)
         :window (window/ctor overriding-config-options)))

(defn dependencies
  [initial]
  (component/system-using initial
   {:app [:done :input :window]  ; seems wrong to tie an app to a single window
    :window [:base]}))

(defn init
  [overriding-config-options]
  (set! *warn-on-reflection* true)
  (let [cfg (into (config/defaults) overriding-config-options)]
    ;; TODO: I really need to configure logging...don't I?
    (-> (base-map overriding-config-options)
        (dependencies))))




