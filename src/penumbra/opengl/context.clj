;;   Copyright (c) Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.opengl.context
  ;; FIXME: Don't do :refer :all
  (:require [penumbra.data :as data]
            [penumbra.opengl.geometry :refer (basic-renderer)]
            [penumbra.opengl :refer :all]
            [penumbra.opengl.core :refer :all]
            [penumbra.opengl.capabilities :as cap])
  ;; FIXME: Start Here
  ;; Switch to GLFW
  (:import [org.lwjgl.opengl Display]))

;;;

(gl-import- glActiveTexture gl-active-texture)

(defn- draw []
  (with-projection (ortho-view -1 1 1 -1 -1 1)
    (push-matrix
     (load-identity)
     (gl-active-texture :texture0)
     (color 1 1 1)
     (draw-quads
      (texture 0 1) (vertex -1 -1 0)
      (texture 1 1) (vertex 1 -1 0)
      (texture 1 0) (vertex 1 1 0)
      (texture 0 0) (vertex -1 1 0)))))

(defn draw-frame-buffer
  ([w h]
     (draw-frame-buffer 0 0 w h))
  ([x y w h]
     (with-viewport [x y w h]
       (call-display-list (force *display-list*)))))

(defn create-context []
  {:display-list (delay (create-display-list (draw)))
   :texture-pool (data/create-cache)
   :font-cache (atom {})})

(defn current []
  {:display-list *display-list*
   :texture-pool *texture-pool*
   :font-cache *font-cache*})

(defn destroy
  ([]
     (destroy (current)))
  ([context]
     (data/clear! (:texture-pool context))))

(defmacro with-context [context & body]
  `(let [context-exists?# ~context
         context# (or ~context (create-context))]
     ;; TODO: Don't use dynamic vars or binding
     (binding [*display-list* (:display-list context#)
               *texture-pool* (:texture-pool context#)
               *font-cache* (:font-cache context#)
               *read-format* cap/read-format
               *renderer* basic-renderer]
       (try
         ~@body
         (finally
           (when-not context-exists?#
             (destroy context#)))))))
