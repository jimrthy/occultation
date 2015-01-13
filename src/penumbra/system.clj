(ns penumbra.system
  (:require [clojure.core.async :as async]
            ;; Q: Debug only??
            [clojure.tools.trace :as trace]
            [com.stuartsierra.component :as component]
            [penumbra
             [app :as app]
             [configuration :as config]]
            [penumbra.app.input :as input]))

(defn init
  [overriding-config-options]
  (set! *warn-on-reflection* true)

  (let [cfg (into (config/defaults) overriding-config-options)]
    ;; TODO: I really need to configure logging...don't I?
    (-> (component/system-map
         :app (app/ctor {})
         :config cfg
         :done (promise)
         :input (input/ctor {}))
        (component/system-using
         {:app [:done :input]
          :input [:config]}))))




