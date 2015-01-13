;;   Copyright (c) Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.opengl.core
  (:use [penumbra.utils :only (defn-memo defmacro-)])
  (:import (org.lwjgl.opengl GL11 GL12 GL13 GL14 GL15 GL20 GL30 GL31 GL32 GL42
                             ARBDrawBuffers
                             ARBFramebufferObject
                             ARBTextureFloat
                             ARBHalfFloatPixel
                             #_APPLEFloatPixels
                             #_ATITextureFloat
                             #_NVFloatBuffer
                             EXTTransformFeedback
                             ARBTextureRectangle
                             #_EXTTextureRectangle
                             EXTFramebufferObject
                             #_EXTGeometryShader4))
  #_(:import (org.lwjgl.util.glu GLUy))
  (:import (java.lang.reflect Field Method))
  (:import (org.lwjgl BufferUtils))
  #_(:import (penumbra PenumbraSystem Natives)))

;; This seems like an odd approach. My first guess is that it's for
;; working around ickiness that has hopefully been reduced over the
;; years.
;; My second guess is that it would be really painful to avoid
#_(Natives/extractNativeLibs (PenumbraSystem/getPlatform) "LWGL")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Dynamic Globals
;;; Really, these should go away

(def ^:dynamic *primitive-type* "What type of primitive is being rendered?" nil)


(def ^:dynamic *check-errors*
  "Causes errors in glGetError to throw an exception.  This creates minimal CPU overhead (~3%), and is almost always worth having enabled."
  true)

(def ^:dynamic *view*
  "Pixel boundaries of render window.  Parameters represent [x y width height]."
  (atom [0 0 0 0]))

;;;

(def ^:dynamic *program*
  "The current program bound by with-program"
  nil)

(def ^:dynamic *uniforms*
  "Cached integer locations for uniforms (bound on a per-program basis)"
  nil)

(def ^:dynamic *attributes*
  "Cached integer locations for attributes (bound on a per-program basis)"
  nil)

;;;

(def ^:dynamic *texture-pool*
  "A list of all allocated textures.  Unused textures can be overwritten, thus avoiding allocation."
  nil)

;;;

(def ^:dynamic *renderer* nil)

(def ^:dynamic *display-list* "Display list for framebuffer/blit rendering." nil)

(def ^:dynamic *frame-buffer*
  "The currently bound frame buffer"
  nil)

(def ^:dynamic *read-format*
  "A function which returns the proper read format for a sequence type and tuple."
  nil)

(def ^:dynamic *render-to-screen?*
  "Whether the current renderer only targets the screen."
  false)

(def ^:dynamic *render-target*
  "The texture which is the main render target (GL_COLOR_ATTACHMENT0)"
  nil)

(def ^:dynamic *layered-target?*
  "Is the render target a layered texture?"
  false)

(def ^:dynamic *z-offset*
  "2-D slice of 3-D texture to render into."
  nil)

;;;

(def ^:dynamic *font-cache* "Where all the fonts are kept" nil)


(def ^:dynamic *font* "Current font" nil)

;;;

(def ^:dynamic containers [#_APPLEFloatPixels
                           ARBDrawBuffers
                           ARBTextureFloat
                           ARBHalfFloatPixel
                           ARBFramebufferObject
                           EXTFramebufferObject
                           #_NVFloatBuffer
                           #_ATITextureFloat
                           #_EXTTextureRectangle
                           ARBTextureRectangle
                           EXTTransformFeedback
                           #_GeometryShader4  ; Became standard in GL32
                           GL11 GL12 GL13 GL14 GL15 GL20 GL30 GL31 GL32 GL42
                           #_GLU])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Named Constants
;;; Mainly for stylistic consistency
;;; To avoid magic numbers.
;;; These would probably make more sense in their own namespace

(def gl-false GL11/GL_FALSE)

(defn- get-fields [^Class static-class]
  (. static-class getFields))

(defn- get-methods [^Class static-class]
  (. static-class getMethods))

(defn- contains-field? [^Class static-class field]
  (first
   (filter
    #{ (name field) }
    (map #(.getName ^Field %) (get-fields static-class)))))

(defn- contains-method? [static-class method]
  (first
   (filter
    #{ (name method) }
    (map #(.getName ^Method %) (get-methods static-class)))))

(defn- field-container [field]
  (first (filter #(contains-field? % field) containers)))

(defn- method-container [method]
  (first (filter #(contains-method? % method) containers)))

(defn- get-gl-method [method]
  (let [method-name (name method)]
    (first (filter #(= method-name (.getName ^Method %)) (mapcat get-methods containers)))))

(defn-memo enum-name
  "Takes the numeric value of a gl constant (i.e. GL_LINEAR), and gives the name"
  [enum-value]
  (if (= 0 enum-value)
    "NONE"
    (.getName
     ^Field (some
             #(if (= enum-value (.get ^Field % nil)) % nil)
             (mapcat get-fields containers)))))

(defn check-error
  ([]
     (check-error ""))
  ([name]
     (let [error (GL11/glGetError)]
       (if (not (zero? error))
         (throw (Exception. (str "OpenGL error: " name " " (enum-name error))))))))

(defn-memo enum [k]
  (when (keyword? k)
    (let [gl (str "GL_" (.. (name k) (replace \- \_) (toUpperCase)))
          sym (symbol gl)
          container (field-container sym)]
      (when (nil? container)
        (throw (Exception. (str "Cannot locate enumeration " k))))
      (eval `(. ~(field-container sym) ~sym)))))

(defn- get-parameters [method]
  (map
   #(keyword (.getCanonicalName ^Class %))
   (.getParameterTypes ^Method (get-gl-method method))))

(defn- get-doc-string [method]
  (str "Wrapper for " method "."))

(defmacro gl-import
  [import-from import-as]
  (let [method-name (str import-from)
        container (method-container import-from)]
    (when (nil? container)
      (throw (Exception. (str "Cannot locate method " import-from))))
    (let [doc-string (get-doc-string import-from)
          arg-list (vec (get-parameters import-from))
          doc-skip (if (contains? (meta import-as) :skip-wiki)
                     (:skip-wiki (meta import-as))
                     true)]
      ;; Q: Why is this creating macros?
      `(defmacro ~import-as
         ~doc-string
         {:skip-wiki ~doc-skip
          :arglists (list ~arg-list)}
         [& args#]
         `(do
            (let [~'value# (. ~'~container ~'~import-from ~@(map (fn [x#] (or (enum x#) x#)) args#))]
              (when (and *check-errors* (not *primitive-type*))
                (check-error ~'~method-name))
              ~'value#))))))

(defmacro gl-import-
  "Private version of gl-import"
  [import-from import-as]
  (list `gl-import import-from (with-meta import-as (assoc (meta import-as) :private true))))

(defmacro gl-import+
  "Documented version of gl-import"
  [import-from import-as]
  (list `gl-import import-from (with-meta import-as (assoc (meta import-as) :skip-wiki nil))))

;;;

(gl-import- glGetInteger gl-get-integer)

(defn get-integer
  "Calls glGetInteger."
  [param]
  (let [buf (BufferUtils/createIntBuffer 16)]
    ;; FIXME: Eliminate reflection warning
    (gl-get-integer (enum param) buf)
    (.get buf 0)))
