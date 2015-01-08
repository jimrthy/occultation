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
  ;; These are what I needed to actually do anything useful
  ;; in a very simple test app.
  ;; May not make any sense here.
  #_(:import [java.nio IntBuffer]
           [org.lwjgl Sys]
           [org.lwjgl.glfw
            Callbacks
            GLFW
            GLFWCursorEnterCallback
            GLFWCursorPosCallback
            GLFWErrorCallback
            GLFWKeyCallback
            GLFWMouseButtonCallback
            GLFWScrollCallback
            GLFWvidmode]
           [org.lwjgl.opengl GL11 GLContext]
           [org.lwjgl.system MemoryUtil]))

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

;; TODO: This should probably just go away completely
;; Or, at the very least, be trimmed back to something that
;; works usefully with Stuart Sierra's Components.
;; Or maybe not: that library has mixed opinions, and this layer
;; shouldn't involve any more libraries than is required.
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
