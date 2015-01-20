;;   Copyright (c) Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.app.controller
  (:require [com.stuartsierra.component :as component]
 [schema.core :as s])
  (:import [clojure.lang Ref]
           [java.util.concurrent CountDownLatch]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

;;; TODO: How many of these actually need to be Ref's?
(s/defrecord Controller [paused? :- Ref
                         stopped? :- Ref
                         invalidated? :- Ref
                         ;; This is really a wrapper around a CountDownLatch
                         latch :- Ref]
  component/Lifecycle
  (start
   [this]
   (into this {:paused (ref false)
               :stopped? (ref :initializing)
               :invalidated? (ref true)
               :latch (ref (CountDownLatch. 1))}))
  (stop
   [this]
   (dosync
    (alter paused? (constantly true))
    (alter stopped? (constantly true))
    (alter invalidated? (constantly false))
    ;; Q: What should latch be set to?
    )
   this))

(comment (defprotocol Controller
           (paused? [c] "Returns true if the application is paused.")
           (pause! [c] "Pauses the application.")
           (stopped? [c] "Returns true if the application is stopped.")
           (stop! [c] [c flag] "Stops the application.")
           (resume! [c] "Resumes the application.  If the application is currently running, this is a no-op.")
           (invalidated? [c] "Returns true if the application needs to be repainted.")
           (invalidated! [c flag] "Sets whether the application needs to be repainted.")
           (wait! [c] "Halts execution of the thread if application is paused or stops.  The thread will resume once the application does.")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Obsolete

(comment (defn create []
           (let [paused? (ref false)
                 stopped? (ref :initializing)
                 invalidated? (ref true)
                 latch (ref (CountDownLatch. 1))]
             (reify
               Controller
               (paused? [_] @paused?)
               (stopped? [_] @stopped?)
               (invalidated? [_] @invalidated?)
               (invalidated! [_ flag]
                 (dosync (ref-set invalidated? flag))
                 nil)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; API

(s/defn invalidated? :- s/Bool
  [component :- Controller]
  @(:invalidated? component))

(s/defn invalidated!
  [component :- Controller
   x :- s/Bool]
  (let [invalid-ref (:invalidated? component)]
    (ref-set invalid-ref x)))

(s/defn paused?
  [component :- Controller]
  @(:paused? component))

(s/defn pause!
  [component :- Controller]
  (dosync
   (ref-set (:paused? component) true)
   (let [latch (:latch component)]
     (when-not @latch
       (ref-set latch (CountDownLatch. 1))))))

(s/defn resume!
  [component :- Controller]
  (dosync
   (ref-set (:paused? component) false)
   (ref-set (:stopped? component) false)
   (when-let [latch (:latch component)]
     (.countDown @latch)
     (ref-set latch nil))))

(s/defn stop!
  ([component :- Controller]
   (stop! component true))
  ([component :- Controller
    reason :- s/Any]
   (let [stopped? (:stopped? component)]
     (dosync
      (ref-set stopped? reason)))))

(s/defn stopped?
  [component :- Controller]
  @(:stopped? component))

(s/defn wait!
  [component :- Controller]
  (when-let [l @(:latch component)]
    (.await l)))

