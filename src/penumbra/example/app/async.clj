;;   Copyright (c) Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.example.app.async
  (:use [penumbra opengl])
  (:require [penumbra.app :as app]))

(defn quad []
  (push-matrix
   (translate -0.5 -0.5 0.5)
   (normal 0 0 1)
   (vertex 0 0) (vertex 1 0)
   (vertex 1 1) (vertex 0 1)))

(defn cube []
  (push-matrix
   (draw-quads
    (dotimes [_ 4]
      (rotate 90 0 1 0)
      (quad))
    (rotate 90 1 0 0)
    (quad)
    (rotate 180 1 0 0)
    (quad))))

(defn init [state]
  (render-mode :wireframe)
  (app/periodic-update! 60 #(update-in % [:rot] inc))
  (app/vsync! true)
  (assoc state
    :cube (create-display-list (cube))))

(defn reshape [[x y width height] state]
  (frustum-view 60.0 (/ (double width) height) 1.0 100.0)
  (load-identity)
  (translate 0 0 -4)
  (light 0 :position [1 1 1 0])
  state)

(defn display [[dt t] state]
  (rotate (:rot state) 0 1 0)
  (call-display-list (:cube state)))

(defn start
  "This doesn't seem to work.
Closing the generated window leads to the messages.
After Pause 1, it closes.
IOW...this very much does not seem to be happening in a different thread."
  []
  (let [app (app/start {:init init, :reshape reshape, :display display} {:rot 0})]
    (println "Starting")
    (flush)
    (Thread/sleep 5000)
    (println "Pause 1")
    (flush)
    (app/pause! app)
    (Thread/sleep 1000)
    (println "Resume")
    (flush)
    (app/start app)
    (Thread/sleep 2000)
    (println "Stopping")
    (flush)
    (app/stop! app)
    (Thread/sleep 1000)
    (println "Restarting")
    (flush)
    (app/start app)
    (Thread/sleep 1000)
    (println "Exiting")
    (flush)
    (app/stop! app)))

