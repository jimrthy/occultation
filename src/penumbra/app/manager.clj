;;   Copyright (c) 2015 James Gatannah. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.app.manager
  "This breaks a lot of the basic contract behind Components.

  It's fundamentally mutable.

  I'm "
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [penumbra.app :as app]
            #_[penumbra.app
             [window :as window]]
            [penumbra.utils :as util]
            [schema.core :as s])
  (:import [penumbra.app App]
           #_[penumbra.app.window Window]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(s/defrecord StageManager [done :- util/promise-class
                           stages :- clojure.lang.Atom]
  component/Lifecycle
  (start
   [this]
   (assoc this :done (promise)))
  (stop
   [this]
   ;; Very tempting to deref done here...that seems
   ;; like it would be a mistake.
   ;; OTOH...it might be appropriate to
   ;;(map #(deref (:done %)) stages)
   ;; For now, just stop each Stage:
   (dorun (map component/stop @stages))
   this))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn add-stage!
  [this :- StageManager
   stage :- App]
  (io!
   (swap! (:stages this) conj stage)))

(s/defn clear-stage!
  [this :- StageManager
   stage :- App]
  (io! (swap! (:stages this) disj stage)))

(defn ctor
  [_]
  ;; Vital:
  ;; Any mutable state that needs to be shared
  ;; among Components must be created here, not
  ;; during start
  ;; Making this a set really doesn't work. Each
  ;; Stage is really a bundle of mutable state/callbacks
  ;; (along with things that are inherently mutable,
  ;; like a Window).
  ;; It's really tempting to make this an atom that
  ;; holds a map of GUIDs to Stage atoms...but that
  ;; approach seems like madness.
  (map->StageManager {:stages (atom #{})}))
