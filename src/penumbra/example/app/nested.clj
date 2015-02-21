;;   Copyright (c) 2012 Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.example.app.nested
  (:use [penumbra opengl text])
  (:require [com.stuartsierra.component :as component]
            [penumbra.app :as app]
            [penumbra.app.input :as input]))

(defrecord Nested [app count]
  component/Lifecycle
  (start [this]
         (assoc this :count 1))
  (stop [this]
        (assoc this :count 0)))

(defn pop-all-states!
  [app]
  (while (app/pop-state! app)))
 
(declare start)
(defn key-press [app key state]
  (cond
   (= key " ") (do
                 (start (update-in state [:count] inc))
                 (app/repaint!))
   ;; TODO: Still need a way to signal that we're done
   (= key :escape) (do
                     (pop-all-states! app)
                     (throw (RuntimeException. "Figure out what to do")))
   :else nil))
 
(defn display [_ state]
  (write-to-screen (str "nested " (:count state) " deep!") 0 0))

;;; This approach breaks the existing examples pretty thoroughly.
;;; They're desperately expecting a callbacks var and an initial-state
;;; fn.
;;; Then again, it's not like the version I'm replacing would have worked
;;; with those anyway.
;;; TODO: Figure out how to make this work again

;;; Important note based on spelunking the mailing list:
;;; the ultimate goal behind this seems to have been setting up
;;; switchable interfaces. Specifically game vs. menu

;;; Actually, it looks like the original version was an example
;;; of a State1/State2/State3 scenario, and I've misunderstood the entire
;;; point. This "orangelet" should be able to bounce between them
;;; seamlessly. That's why it called (stop!) at each transition

;;; At least, I think that's what I missed when I tried to translate
;;; it.
(defn system
  []
  (let [base (component/system-map :app (app/ctor {})
                                   :callbacks {:key-press key-press :display display}
                                   :input (input/ctor {})
                                   :nested (map->Nested {}))
        dependencies {:app [:callbacks :input]
                      :nested [:app]}]))

(defn start []
  (component/start (system)))
