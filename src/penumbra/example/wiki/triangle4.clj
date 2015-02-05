(ns penumbra.example.wiki.triangle4
  (:use [penumbra.opengl])
  (:require [penumbra.app.minimal :as app]))

(defn init [state]
  state)

(defn reshape [[x y width height] state]
  (frustum-view 60.0 (/ (double width) height) 1.0 100.0)
  (load-identity)
  state)
  
(defn update-frame [[delta time] state]
  (update-in state [:rot-y] #(rem (+ % (* 90 delta)) 360)))

(defn display [[delta time] state]
  (translate 0 -0.93 -3)
  (rotate (:rot-y state) 0 1 0)
  (draw-triangles
   (color 1 0 0) (vertex 1 0)
   (color 0 1 0) (vertex -1 0)
   (color 0 0 1) (vertex 0 1.86))
  #_(app/repaint!))

(defn start []
  (app/start
   "Triangle 4"
   {:display display, :reshape reshape, :init init, :update update-frame}
   {:rot-y 0}))
