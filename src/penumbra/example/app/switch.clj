;;   Copyright (c) 2012 Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.example.app.switch
  (:use [penumbra opengl text])
  (:require [penumbra.app :as app]
            [penumbra.app.window :as window]))

(declare apps)
(defn callbacks
  ([]
   (callbacks :controller))
  ([goto]
   (-> apps goto :callbacks)))

(defn initial-state 
  ([]
   initial-state :controller)
  ([goto]
   (-> apps goto :state)))


(defn switch [a]
  (loop [app (apps a)]
    (when app
      ;; deref retrieve's the App's state.
      ;; Which includes :goto.
      ;; That has the key for the apps hashmap that
      ;; tells us which one to switch to.
      ;; Q: Is this a toy example, or is this something that's
      ;; either fundamentally vital or incredibly useful?
      ;; This next approach will not work at all
      (recur (-> app #_app/start constantly
                 deref :goto apps)))))

;; Controller

(defn controller-init [state]
  (switch :first))

;; First app

(defn first-init [state]
  (assoc state :goto nil))

(defn first-key-press [app key state]
  (when (= key :escape)
    ;; This approach, as an alternative to the original app/stop!,
    ;; is broken.
    ;; We don't want this window to close...we just want this particular
    ;; state in the examples to transition to the next.
    ;; Having each example live in its own window would be cleaner, but
    ;; that doesn't particularly map well to my intended architecture
    (window/close! (:window app))
    (assoc state :goto :second)))

(defn first-display [_ state]
  (write-to-screen "first app" 0 0))

;; Second app

(defn second-init [state]
  (assoc state :goto nil))

(defn second-key-press [app key state]
  (when (= key :escape)
    (window/close! (:window app))
    (assoc state :goto :first)))

(defn second-display [_ state]
  (write-to-screen "second app" 0 0))

(def apps {:controller {:callbacks {:init controller-init}, :state {}}
           :first {:callbacks {:init first-init, :key-press first-key-press, :display first-display}, :state {}}
           :second {:callbacks {:init second-init, :key-press second-key-press :display second-display}, :state {}}})
