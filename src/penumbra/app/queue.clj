;;   Copyright (c) 2012 Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.app.queue
  (:require [com.stuartsierra.component :as component]
            [penumbra.app.loop :as loop]
            [penumbra.time :as time]
            [penumbra.app.core :as app]
            [schema.core :as s])
  (:import [clojure.lang Atom]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(defrecord PeriodicActionQueue [clock controller event-loop heap]
  component/Lifecycle
  (start [this]
    (let [heap (ref (sorted-set-by #(- (compare (first %2) (first %1)))))
          event-loop (future (loop/basic-loop
                              ;; TODO: Need a way to signal exit
                              controller
                              (fn [x] (x))
                              (fn []
                                (if-let [actions
                                         (dosync
                                          (let [now @clock
                                                top (take-while #(>= now (first %)) @heap)]
                                            (when-not (empty? top)
                                              (alter heap #(apply disj (list* % top)))
                                              top)))]
                                  (doseq [a (map second actions)]
                                    (a))
                                  (Thread/sleep 1)))))]
      (assoc this :event-loop event-loop)))
  (stop [this]
    ;; Q: What's the equiv for refs?
    ;; More importantly, have to kill the event loop thread
    (assoc this :heap nil)))

(defrecord ActionQueue [controller hash clock]
  component/Lifecycle
  (start [this]
    (assoc this
           :clock (time/clock)
           :hash (atom {})))
  (stop [this]
    (let [periods (vals @hash)]
      (doseq [p periods]
        (component/stop p)))
    (reset! hash nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Obsolete

(comment (defn create [app]
           (let [hash (ref {})]
             (reify
               QueueHash
               (init! [_]
                 '(doseq [q (vals @hash)]
                    (init- q)))
               (enqueue! [_ clock delay f]
                 (enqueue- (find-or-create clock) delay f))
               (periodic-enqueue! [_ clock hz f]
                 (periodic-enqueue- (find-or-create clock) hz f))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(s/defn find-or-create :- Atom
  [component :- ActionQueue, clock]
  (let [controller (:controller component)]
    (dosync
     (if-let [q (get @hash clock)]
       q
       (let [dead-q (map->PeriodicActionQueue {:clock clock
                                               :controller controller})
             q (component/start dead-q)]
         (alter hash #(assoc % clock q))
         q)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn enqueue!
  "Enqueues an action to be executed in 'delay' milliseconds"
  [this :- ActionQueue
   delay f]
  (let [clock (find-or-create (:hash this) )
        heap (:heap this)]
    (swap! heap #(conj % [(+ @clock delay) f])))
  nil)

(s/defn periodic-enqueue!
  "Creates a recurring action to be executed 'hz' times a second"
  [this :- ActionQueue
   hz f]
  (let [clock (:clock this)
        hz (atom hz)
        target (atom (+ @clock (/ 1 @hz)))]
    (letfn [(f* []
              (let [start @clock]
                (binding [app/*hz* hz]
                  (f))
                (let [hz @hz]
                  (when (pos? hz)
                    (enqueue! this (+ (/ 1 hz) (- @target start)) f*)
                    (swap! target #(+ % (/ 1 hz)))))))]
      (enqueue! this (/ 1 @hz) f*)))
  nil)

(defn ctor
  [{:keys []}]
  (map->ActionQueue {}))
