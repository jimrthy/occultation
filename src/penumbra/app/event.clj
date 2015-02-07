;;   Copyright (c) 2012 Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.app.event
  (:require [clojure.stacktrace]
            [com.stuartsierra.component :as component]
            [schema.core :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(defrecord EventHandler [subscriptions]
  component/Lifecycle
  (start
    [this]
    (assoc this :subscriptions (or subscriptions
                                   atom {})))

  (stop
    [this]
    (assoc this :subscriptions nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(s/defn alter-subscription
  "Alters the subscription associated with hook and f, by calling alteration"
  [component :- EventHandler
   alteration
   hook
   f]
  (swap! (:subscriptions component)
         (fn [e] 
           (update-in e [hook]
                      #(set (alteration % f)))))
  nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn publish!
  "Publishes an event. Any callbacks will be invoked with 'args'"
  [component :- EventHandler
   hook & args]
  (doseq [f (->> component :subscriptions deref hook)]
    (try
      (apply f args)
      (catch RuntimeException ex
        (let [msg
              (str "Exception handling an event:\n" ex
                   (with-out-str
                     (clojure.stacktrace/print-stack-trace ex)))]
          ;; Don't particularly want to propagate this.
          ;; Q: Is it worth adding a dependency on something
          ;; like ribol to handle it?
          (throw (ex-info msg {:cause ex
                               :component component
                               :hook hook})))))))

(s/defn subscribe!
  "Subscribe to event 'hook' with callback 'f'."
  [component :- EventHandler
   hook f]
  (alter-subscription component conj hook f))

(s/defn unsubscribe!
  "Unsubscribes callback 'f' from event 'hook'"
  [component :- EventHandler
   hook f]
  (alter-subscription component disj hook f))

(s/defn subscribe-once!
  "Subscribes callback 'f'. Once the callback is triggered, it is unsubscribed."
  [this :- EventHandler
   hook f]
  (subscribe!
   this hook
   (letfn [(f* [& args]
             (apply f args)
             (unsubscribe! this hook f*))]
     f*)))

(defn ctor
  [{:keys [subscriptions]}]
  (map->EventHandler {:subscriptions subscriptions}))
