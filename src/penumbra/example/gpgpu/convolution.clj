;;   Copyright (c) 2012 Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.example.gpgpu.convolution
  (:use [penumbra compute])
  (:require [penumbra.app :as app]
            [penumbra.opengl :as gl]
            [schema.core :as s])
  (:import [penumbra.app App]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

;;; I *think* the tex is a texture handle. Which should be
;;; a short int. I *think*
(def state {(s/optional-key :tex) s/Any})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(defn draw-rect [x y w h]
  (gl/with-disabled :texture-rectangle
    (gl/draw-quads
     (gl/vertex x y)
     (gl/vertex (+ x w) y)
     (gl/vertex (+ x w) (+ y h))
     (gl/vertex x (+ y h)))))

(defn reset-image [tex]
  (gl/render-to-texture tex
    (gl/clear)                 
    (gl/with-projection (gl/ortho-view 0 2 0 2 -1 1)
      (dotimes [_ 100]
        (apply gl/color (take 3 (repeatedly rand)))
        (apply draw-rect (take 4 (repeatedly rand))))))
  (app/repaint!)
  tex)

(s/defn init
  [app :- App
   state]

  (app/title! app "Convolution")

  (defmap detect-edges
    (let [a (float4 0.0)
          b (float4 0.0)]
      (convolve %2
        (+= a (* %2 %1)))
      (convolve %3
        (+= b (* %3 %1)))
      (sqrt (+ (* a a) (* b b)))))

  (def filter-1
    (wrap (map float
               [-1 0 1
                -2 0 2
                -1 0 1])))

  (def filter-2
    (wrap (map float
               [1 2 1
                0 0 0
                -1 -2 -1])))

  (gl/enable :texture-rectangle)
  (assoc state
    :tex (reset-image (gl/create-byte-texture :texture-rectangle 512 512))))

(s/defn key-press :- state
  [key state :- state]
  (let [tex (:tex state)]
    (cond
     (= key " ")
     (assoc state
       :tex (reset-image tex))
     (= key :return)
     (assoc state
       :tex (detect-edges tex [filter-1] [filter-2]))
     :else
     state)))

(defn display [_ state]
  (gl/blit (:tex state)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn callbacks []
  {:display display, :key-press key-press, :init init})

(defn initial-state []
  {})
