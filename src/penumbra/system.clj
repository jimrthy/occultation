;;   Copyright (c) 2015 James Gatannah. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

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
         :window (window/ctor (select-keys overriding-config-options [:hints
                                                                      :position
                                                                      :title]))))

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




