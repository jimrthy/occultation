;;   Copyright (c) 2012 Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.example.gpgpu.mandelbrot
  (:use [penumbra compute])
  (:require [penumbra.app :as app]
            [penumbra.data :as data]
            [penumbra.opengl :as gl]
            [schema.core :as s])
  (:import [penumbra.app App]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def point {:x float :y float})

(def state {:upper-left point
            :lower-right point
            :zoom float
            :offset point
            (s/optional-key :iterations) s/Int
            (s/optional-key :image) s/Any
            (s/optional-key :data) s/Any
            (s/optional-key :repaint) s/Bool})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(s/defn init 
  [app :- App
   state :- state]
  
  (app/title! app "Mandelbrot Viewer")

  (defmap initialize-fractal
    (float3 (mix upper-left lower-right (/ :coord :dim)) 0))

  (defmap iterate-fractal
    (let [val %
          c (mix upper-left lower-right (/ :coord :dim))]   
      (dotimes [i num-iterations]
        (let [z (.xy val)
              iterations (.z val)]
          (<- val
            (if (< 4 (dot z z))
               val
               (float3
                (+ c
                   (float2
                    (- (* (.x z) (.x z)) (* (.y z) (.y z)))
                    (* 2 (.x z) (.y z))))
                (+ 1 iterations))))))
      val))

  (defmap color-fractal
    (let [val %
          z (.xy val)
          n (.z val)
          escape (-> n (- (-> z length log2 log2)) (/ (float max-iterations)))]
      (if (< 4 (dot z z))
         (color3 (mix [0 0 1] [1 1 1] escape))
         (color3 0 0 0))))

  state)

(s/defn reset-fractal :- state
  [state :- state]
  (when (:data state)
    (data/release! (:data state)))
  (when (:image state)
    (data/release! (:image state)))
  (assoc state
    :iterations 0
    :image nil
    :data nil))

(s/defn update-bounds :- state
 [state :- state]
  (let [{:keys [x y]}  (:dim state)
        center (:offset state)
        radius (map #(/ % (:zoom state)) [(/ (float x) y) 1])
        ul     (map - center radius)
        lr     (map + center radius)]
    (assoc (reset-fractal state)
      :upper-left ul
      :lower-right lr)))

(s/defn mouse-down :- state
  [[x y]
   button
   state :- state]
  (let [ul    (:upper-left state)
        lr    (:lower-right state)
        [mx my] (map / [x y] (:dim state))
        [nx ny] (map + ul (map * [mx (- 1 my)] (map - lr ul)))]
    (update-bounds
      (assoc state
        :zoom (max 1
                   (* (:zoom state)
                      (if (= button :left) 2 0.5)))
        :offset {:x nx, :y ny}))))

(s/defn reshape :- state
  [[x y w h]
   state :- state]
  ;; TODO: This isn't going to fly
  (gl/ortho-view 0 1 1 0 -1 1)
  (update-bounds
    (assoc state
           :dim {:x w :y h})))

(s/defn key-press :- state
  [key state :- state]
  (cond
    ;; pause! seems to be the wrong name for what should happen here
   (= key :escape) (app/pause!)
   :else state))

(def iterations-per-frame 60)

(s/defn app-update :- state
  [_ state :- state]
  (let [max-iterations (* 20 (Math/pow (:zoom state) 0.5))]
    (if (< (:iterations state) max-iterations)
      (gl/with-frame-buffer
        (let [ul      (:upper-left state)
              lr      (:lower-right state)
              iters   (+ (:iterations state) iterations-per-frame)
              data    (or
                       (:data state)
                       (initialize-fractal {:upper-left ul :lower-right lr} (:dim state)))
              next    (iterate-fractal {:upper-left ul :lower-right lr :num-iterations iterations-per-frame} data)
              image   (color-fractal {:max-iterations max-iterations} [next])]
          (assoc state
            :repaint true
            :iterations iters
            :data next
            :image image)))
      (assoc state
        :repaint false))))

(s/defn display
  [_ state :- state]
  (when (:repaint state)
    (app/repaint!))
  (gl/blit! (:image state)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn callbacks []
  {:init init, :reshape reshape, :update app-update, :display display, :mouse-down mouse-down, :key-press key-press})

(defn initial-state []
  (reset-fractal {:upper-left [-2.0 1.0] :lower-right [1.0 -1.0] :zoom 1 :offset [-0.5 0]}))
