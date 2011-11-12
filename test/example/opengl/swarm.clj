;;   Copyright (c) Kyle Harrington, 2011. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns example.opengl.swarm
  (:use [penumbra opengl compute]
        [penumbra.opengl core]
        [cantor])
  (:require [penumbra.app :as app]
            [penumbra.text :as text]
            [penumbra.data :as data]))

(def num-birds 10)

(defn random-position
  []
  (vec3 (rand 2) (rand 2) (rand 2)))

(defn random-velocity
  []
  (vec3 (- (rand 2) 1) (- (rand 2) 1) (- (rand 2) 1)))

(defn random-bird
  []
  {:position (random-position)
   :velocity (random-velocity)})

(defn update-bird
  [dt bird]
  (assoc bird
    :position (add (:position bird) (mul (:velocity bird) 0.001))))

(defn reset
  [state]
  (assoc state
    :birds (repeatedly num-birds random-bird)))

(defn init-bird-graphic 
  []
  (def bird-graphic (create-display-list
		     (color 1 1 0)
		     (draw-quads
		      (vertex 0 0 1) (vertex 0 1 1) (vertex 0 1 0) (vertex 0 0 0)
		      (vertex 1 0 1) (vertex 1 1 1) (vertex 1 1 0) (vertex 1 0 0)
		      (vertex 1 0 0) (vertex 1 0 1) (vertex 0 0 1) (vertex 0 0 0)
		      (vertex 1 1 0) (vertex 1 1 1) (vertex 0 1 1) (vertex 0 1 0)		      
		      (vertex 0 1 0) (vertex 1 1 0) (vertex 1 0 0) (vertex 0 0 0)
		      (vertex 0 1 1) (vertex 1 1 1) (vertex 1 0 1) (vertex 0 0 1)))))
;;		      (vertex 0 0 1) (vertex 1 0 1) (vertex 1 0 0) (vertex 0 0 0)

(defn init [state]
  (app/title! "Swarm")
  (app/vsync! true)
  (app/key-repeat! false)
  (init-bird-graphic)
  (enable :blend)
  (blend-func :src-alpha :one-minus-src-alpha)
;;  (app/periodic-update! 25 update-collisions)
  (reset state))

(defn reshape [[x y w h] state]
  (let [dim (vec2 (* (/ w h) 10) 10)]
    (frustum-view 45 (/ w h) 0.1 100)
    (load-identity)
    (translate 0 0 (- (* 2.165 (.y dim))))
    (assoc state
      :dim dim)))

(defn update [[dt time] state]
  (assoc state
    :birds 
    (doall (for [b (:birds state)]
	     (update-bird dt b)))))

(defn draw-bird [bird]
  (push-matrix
   (translate (:position bird))
;   (rotate 
   (call-display-list bird-graphic)))

(defn display [[dt time] state]
  (text/write-to-screen (str (int (/ 1 dt)) " fps") 0 0)
  (rotate (:rot-x state) 1 0 0)
  (rotate (:rot-y state) 0 1 0)
  (with-disabled :texture-2d
    (doseq [b (:birds state)]
      (draw-bird b)))
  (app/repaint!))

(defn mouse-drag [[dx dy] _ button state]
  (assoc state
    :rot-x (+ (:rot-x state) dy)
    :rot-y (+ (:rot-y state) dx)))

(defn start []
  (app/start
   {:reshape reshape, :init init, :mouse-drag mouse-drag, :update update, :display display}
   {:rot-x 0
    :rot-y 0}))