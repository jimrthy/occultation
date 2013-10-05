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
  (:require [penumbra.app :as app]
            [clojure.pprint :refer [pprint with-pprint-dispatch]]
            [slingshot.slingshot :refer [try+ throw+]]))

(defn switch [a apps]
  (println "Switching to " a)
  (try+
   (with-pprint-dispatch print
     (pprint apps))
   (catch Exception ex
     (println "Pretty-printing apps failed:\n" ex)
     ;; TODO: Start here with this.
     (throw (RuntimeException. "Yes. Right here."))
     (println "Apparently I need to pick a priority for printing an App."))
   (catch Object ex
     (println "Pretty-printing apps failed unexpectedly:\n" ex)
     (throw+)))
  (try
    (loop [app (apps a)]
      (when app
        (recur (-> app app/start deref :goto apps))))
    (catch Exception ex
      (println "Unhandled exception in switch:")
      (pprint ex))))

;; Controller

(defn controller-init [state]
  (println "Initializing controller")
  (switch :first state))

(comment (defn controller-draw [state]
           (println "Drawing controller")
           (translate 0 -0.93 -3)
           (draw-triangles
            (color 1 0 0) (vertex 1 0)
            (color 0 1 0) (vertex -1 0)
            (color 0 0 1) (vertex 0 1.86))
           ;; Not that we ever get to here.
           (when-let [next (:goto state)]
             (switch next state))
           (app/repaint!)))

;; First app

(defn first-init [state]
  (assoc state :goto nil))

(defn first-key-press [key state]
  (println "1: " key)
  (when (= key :escape)
    ;; Actually stopping the app seems like a mistake.
    (comment
      ;; Getting rid of this at least keeps the app from crashing.
      ;; It also eliminates the switch. I wonder what was planned for this.
      ;; Not a matter of planned. This was pretty obviously working.
      )
    (do
      (println "Stopping current app")
      (app/stop!))
    (println "Ordering change to to second window")
    (assoc state :goto :second)))

(defn first-display [_ state]
  (write-to-screen "first app" 0 0)
  (comment (app/repaint!)))

;; Second app

(defn second-init [state]
  (assoc state :goto nil))

(defn second-key-press [key state]
  (println "2: " key)
  (when (= key :escape)
    (app/stop!)
    (assoc state :goto :first)))

(defn second-display [_ state]
  (comment) (println "Display second")
  (write-to-screen "second app" 0 0)
  (comment (app/repaint!)))

;;

(defn start []
  (println "Begin")
  (let [first (app/create {:init first-init, :key-press first-key-press, :display first-display} {})
        second (app/create {:init second-init, :key-press second-key-press :display second-display} {})
        controller (app/create {:init controller-init} {:first first :second second})]
    (comment (app/start {:init controller-init :display controller-draw} 
                        {:first first :second second}))
    (app/start controller)))
