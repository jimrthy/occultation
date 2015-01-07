;;   Copyright (c) Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.opengl.slate
  (:require [penumbra.utils :refer (defmacro- defn-memo)]
            [clojure.pprint :refer (pprint)]
            [penumbra.utils :refer (separate)]
            [penumbra.opengl.core :refer :all]
            [penumbra.app.core :refer :all])
  ;; Q: What happened to PixelFormat?
  (:import [org.lwjgl.opengl #_Pbuffer #_PixelFormat]))

;;;

(defstruct slate-struct
  :drawable
  :pixel-buffer)

(def ^:dynamic *slate* nil)

(defn supported?
  "Checks whether pixel buffers are supported.
Based on forum.lwjgl.org/index.php?topic=4800.210:

There's no cross-platform abstraction for pbuffers,
but you can use the OS-sepcific APIs.

It sounds like this should figure out which platform it's
is running and use that.

TODO: Until that happens, this namespace should really be
considered obsolete."
  []
  (comment (< 0 (bit-and Pbuffer/PBUFFER_SUPPORTED (Pbuffer/getCapabilities)))))

(defn create
  "Creates a slate."
  ([]
     (create nil))
  ([parent]
     (let [drawable (when-let [drawable-fn (some-> *app* :window :drawable)]
                      (drawable-fn))
           pixel-buffer (comment (Pbuffer. 1 1 (-> (PixelFormat.)) drawable))]
       (struct-map slate-struct
         :drawable (constantly pixel-buffer)
         :pixel-buffer pixel-buffer))))

(defn destroy
  "Destroys a slate."
  ([]
     (destroy *slate*))
  ([slate]
     nil))

(defmacro with-slate-
  [slate & body]
  `(do
     (.makeCurrent (:pixel-buffer ~slate))
     (binding [*slate* ~slate]
       (try
         ~@body
         (finally
           (destroy ~slate))))))

(defmacro with-slate
  [& body]
  `(let [slate# (create)]
     (with-slate- slate#
       ~@body)))

;;;




