;;   Copyright (c) 2012 Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.example.examples
  (:use [clojure.test])
  (:require
   [penumbra.example.demo :as demo]
   [penumbra.example.app.async :as async]
   [penumbra.example.app.switch :as switch]
   [penumbra.example.app.nested :as nested]
   [penumbra.example.opengl.accumulate :as accumulate]
   [penumbra.example.opengl.async :as gl-async]
   [penumbra.example.opengl.gears :as gears]
   [penumbra.example.opengl.marble :as marble]
   [penumbra.example.opengl.render-to-texture :as rtt]
   [penumbra.example.opengl.shadow :as shadow]
   [penumbra.example.opengl.sierpinski :as sierpinski]
   [penumbra.example.opengl.squares :as squares]
   [penumbra.example.opengl.text :as text]
   [penumbra.example.game.tetris :as tetris]
   [penumbra.example.game.asteroids :as asteroids]
   [penumbra.example.game.pong :as pong]
   [penumbra.example.gpgpu.mandelbrot :as mandelbrot]
   [penumbra.example.gpgpu.convolution :as convolution]
   [penumbra.example.gpgpu.brians-brain :as brian]
   [penumbra.example.gpgpu.fluid :as fluid]
   [penumbra.example.gpgpu.n-body :as nbody]))

(defmacro run-test
  [title ns-sym]
  ;; TODO: put ns-sym into a gensym so it doesn't
  ;; get eval'd twice
  `(testing ~title
      (let [callbacks (~ns-sym/callbacks)
            state (~ns-sym/initial-state)
            done (promise)
            test-app (-> app
                         (assoc-in :state state)
                         (assoc-in :callbacks callbacks)
                         (assoc-in :done done))]
        @done)))

(deftest run
  (let [app (demo/wrapper)]
    (run-test "Accumulate" accumulate)
    (run-test "Async" async)
    (run-test "Brian's Brains" brian)
    (run-test "Convolution" convolution)
    (run-test "Fluid" fluid)
    (run-test "Gears" gears)
    (run-test "Mandelbrot" mandelbrot)
    (run-test "Marble" marble)
    (run-test "N Body" nbody)
    (run-test "Render-to-Texture" rtt)
    (run-test "Shadow" shadow)
    (run-test "Sierpinski" sierpinski)
    (run-test "Squares" squares)
    (run-test "Text" text)
    (testing "Switch"
      (switch/start))
    (testing "Nested"
      (nested/start))
    (testing "Async"
      (gl-async/start))
    (testing "Tetris"
      (tetris/start))
    (testing "Asteroids"
      (asteroids/start))
    (testing "Pong"
      (pong/start))))
