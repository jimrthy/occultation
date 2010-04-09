;;   Copyright (c) Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.opengl.capabilities
  (:require [penumbra.slate :as slate]
            [penumbra.opengl :as gl]
            [penumbra.opengl.texture :as tex]
            [penumbra.opengl.frame-buffer :as fb]
            [penumbra.data :as data])
  (:use [penumbra.opengl.core :only (enum enum-name gl-import-)]
        [clojure.contrib.def :only (defn-memo)]
        [clojure.contrib.combinatorics :only (cartesian-product)])
  (:import [java.nio IntBuffer]))

(gl-import- glGetInteger gl-get-integer)

(defn get-integer
  [param]
  (let [ary (int-array 16)]
    (gl-get-integer (enum param) (IntBuffer/wrap ary))
    (first ary)))


(defn- get-frame-buffer []
  (get-integer :framebuffer))

(defn- valid-read-format? [type tuple format]
  (try
   (let [tex (tex/create-texture
              :target :texture-rectangle
              :dim [16 16]
              :internal-format format
              :pixel-format (tex/tuple->pixel-format tuple)
              :internal-type type)]
     ;;TODO: test that if we write to it, we can read back the same thing
      (data/destroy! tex)
      [format (tex/tuple->pixel-format tuple) type])
   (catch Exception e
     false)))

(defn-memo read-format [type tuple]
  (some
    #(apply valid-read-format? %)
    (filter
     #(and (= type (first %)) (= tuple (second %)))
     tex/internal-formats)))

(defn- valid-write-format? [type tuple format]
  (let [curr (get-frame-buffer)
        fb (fb/gen-frame-buffer)]
    (fb/bind-frame-buffer fb)
    (try
     (let [tex (tex/create-texture
                :target :texture-rectangle
                :dim [16 16]
                :internal-format format
                :pixel-format (tex/tuple->pixel-format tuple)
                :internal-type type)]
        (fb/attach-textures [] [tex])
        (if (fb/frame-buffer-ok?)
          (do
            (when tex
              (data/destroy! tex))
            [format (tex/tuple->pixel-format tuple) type])
          (do
            (when tex
              (data/destroy! tex))
            false)))
      (catch Exception e
        false)
      (finally
        (fb/destroy-frame-buffer fb)
        (fb/bind-frame-buffer curr)))))

(defn-memo write-format [type tuple]
  (some
    #(apply valid-write-format? %)
    (filter
      #(and (= type (first %)) (= tuple (second %)))
      tex/internal-formats)))

(defn print-compatible-types []
  (let [permutations (cartesian-product [:float :int :unsigned-byte] (range 1 5))]
    (slate/with-slate
      (doseq [[type tuple] permutations]
        (println
          (name type) tuple
          "\n  R " (if-let [format (read-format type tuple)]
                   (first format)
                   "NONE")
          "\n  W " (if-let [format (write-format type tuple)]
                   (first format)
                   "NONE"))))))
