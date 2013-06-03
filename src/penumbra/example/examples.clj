;;   Copyright (c) Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.example.examples
  (:use [clojure.test])
  (:require
   [penumbra.example.app.async :as async]
   [penumbra.example.app.switch :as switch]
   [penumbra.example.app.nested :as nested]
   [penumbra.example.opengl.text :as text]
   [penumbra.example.opengl.gears :as gears]
   [penumbra.example.opengl.sierpinski :as sierpinski]
   [penumbra.example.opengl.render-to-texture :as rtt]
   [penumbra.example.opengl.marble :as marble]
   [penumbra.example.opengl.shadow :as shadow]
   [penumbra.example.opengl.squares :as squares]
   [penumbra.example.opengl.accumulate :as accumulate]
   [penumbra.example.opengl.async :as gl-async]
   [penumbra.example.game.tetris :as tetris]
   [penumbra.example.game.asteroids :as asteroids]
   [penumbra.example.game.pong :as pong]
   [penumbra.example.gpgpu.mandelbrot :as mandelbrot]
   [penumbra.example.gpgpu.convolution :as convolution]
   [penumbra.example.gpgpu.brians-brain :as brian]
   [penumbra.example.gpgpu.fluid :as fluid]
   [penumbra.example.gpgpu.n-body :as nbody]))

#_(deftest run
    (testing "Async"
      (async/start))
    (testing "Switch"
      (switch/start))
    (testing "Nested"
      (nested/start))
    (testing "Text"
      (text/start))
    (testing "Gears"
      (gears/start))
    (testing "Sierpinski"
      (sierpinski/start))
    (testing "Render-to-Texture"
      (rtt/start))
    (testing "Marble"
      (marble/start))
    (testing "Shadow"
      (shadow/start))
    (testing "Squares"
      (squares/start))
    (testing "Accumulate"
      (accumulate/start))
    (testing "Async"
      (gl-async/start))
    (testing "Tetris"
      (tetris/start))
    (testing "Asteroids"
      (asteroids/start))
    (testing "Pong"
      (pong/start))
    (testing "Mandelbrot"
      (mandelbrot/start))
    (testing "Convolution"
      (convolution/start))
    (testing "Brian's Brains"
      (brian/start))
    (testing "Fluid"
      (fluid/start))
    (testing "N Body"
      (nbody/start)))
