;;   Copyright (c) Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

;;;; Seems to be a basic window manager. When you press the <ESC> key, it
;;;; looks like it should switch back and forth between App1 and App2.

(ns penumbra.example.app.switch
  (:use [penumbra opengl text])
  (:require [penumbra.app :as app]))

(comment (declare apps))

(defn switch [a apps]
  (println "switch " a)
  (loop [app (apps a)]
    (when app
      (recur (-> app app/start deref :goto apps)))))

;; Controller

(defn controller-init [state]
  (println "Initializing controller")
  (switch :first state))

(defn controller-draw [_]
  (println "Drawing controller")
  (translate 0 -0.93 -3)
  (draw-triangles
   (color 1 0 0) (vertex 1 0)
   (color 0 1 0) (vertex -1 0)
   (color 0 0 1) (vertex 0 1.86))
  (app/repaint!))

;; First app

(defn first-init [state]
  (assoc state :goto nil))

(defn first-key-press [key state]
  (println "1: " key)
  (when (= key :escape)
    (app/stop!)
    (assoc state :goto :second)))

(defn first-display [_ state]
  (comment (println "Display First"))
  (write-to-screen "first app" 0 0)
  (app/repaint!))

;; Second app

(defn second-init [state]
  (assoc state :goto nil))

(defn second-key-press [key state]
  (println "2: " key)
  (when (= key :escape)
    (app/stop!)
    (assoc state :goto :first)))

(defn second-display [_ state]
  (comment (println "Display second"))
  (write-to-screen "second app" 0 0)
  (app/repaint!))

;;

(defn start []
  (println "Begin")
  (let [first (app/create {:init first-init, :key-press first-key-press, :display first-display} {})
        second (app/create {:init second-init, :key-press second-key-press :display second-display} {})]
    (app/start {:init controller-init :display controller-draw} 
               {:first first :second second})))
