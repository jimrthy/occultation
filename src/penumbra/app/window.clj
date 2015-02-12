;;   Copyright (c) 2012 Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.app.window
  (:require [com.stuartsierra.component :as component]
            [penumbra.app.core :as app]
            [penumbra.app.event :as event]
            [penumbra.app.utilities :as util]
            [penumbra.constants :as K :refer (gl-false)]
            [penumbra.opengl
             [texture :as texture]
             [context :as context]]
            ;; FIXME: Don't do this.
            [penumbra.opengl :refer :all]
            [penumbra.opengl.core]
            [penumbra.text :as text]
            [schema.core :as s])
  (:import [org.lwjgl.glfw GLFW GLFWvidmode]
           [org.lwjgl.system MemoryUtil]
           [org.newdawn.slick.opengl InternalTextureLoader TextureImpl]
           [java.awt Frame Canvas GridLayout Color]
           [java.awt.event WindowAdapter]
           [java.nio ByteBuffer IntBuffer]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def Position {:left s/Int
               :top s/Int
               :width s/Int
               :height s/Int})

(def hint-map {:resizable GLFW/GLFW_RESIZABLE         ; bool
               ;; Don't show until it's positioned
               ;; (i.e. Don't let callers override, though that seems
               ;; like an obnoxious choice
               ;; :visible GLFW/GLFW_VISIBLE             ; bool
               :decorated GLFW/GLFW_DECORATED         ; bool
               :red GLFW/GLFW_RED_BITS
               :green GLFW/GLFW_GREEN_BITS
               :blue GLFW/GLFW_BLUE_BITS
               :alpha GLFW/GLFW_ALPHA_BITS
               :depth GLFW/GLFW_DEPTH_BITS
               :stencil GLFW/GLFW_STENCIL_BITS
               :accum-red GLFW/GLFW_ACCUM_RED_BITS      ; int
               :accum-green GLFW/GLFW_ACCUM_GREEN_BITS  ; int
               :accum-blue GLFW/GLFW_ACCUM_BLUE_BITS    ; int
               :accum-alpha GLFW/GLFW_ACCUM_ALPHA_BITS  ; int
               :aux-buffers GLFW/GLFW_AUX_BUFFERS       ; int
               :samples GLFW/GLFW_SAMPLES               ; int
               :refresh-rate GLFW/GLFW_REFRESH_RATE     ; int
               :stereo GLFW/GLFW_STEREO                 ; bool
               :srgb-capable GLFW/GLFW_SRGB_CAPABLE     ; bool
               :client-api GLFW/GLFW_OPENGL_API   ; opengl or opengl-es
               :major GLFW/GLFW_CONTEXT_VERSION_MAJOR
               :minor GLFW/GLFW_CONTEXT_VERSION_MINOR
               :robust GLFW/GLFW_CONTEXT_ROBUSTNESS  ; interesting enum
               :forward GLFW/GLFW_OPENGL_FORWARD_COMPAT
               :deugging GLFW/GLFW_OPENGL_DEBUG_CONTEXT
               :profile GLFW/GLFW_OPENGL_PROFILE})
;; TODO: Make the legal values explicit
(def legal-window-hints {(s/enum (keys hint-map)) s/Any})

(declare customize-window-hints)

(s/defrecord Window [handle :- s/Int
                     position :- Position
                     hints :- legal-window-hints
                     monitor :- s/Int
                     share :- s/Int
                     title :- s/Str]
  component/Lifecycle
  (start
   [this]
   ;; TODO: Switch to supplying a default instead
   (let [^String real-title (or title
                        "You should name this yourself")]
     (GLFW/glfwDefaultWindowHints)
     ;; TODO: Default to invisible window that's convenient for moving
     (when (seq hints)
       (customize-window-hints hints))
     (let [;; TODO: Don't use magic numbers for the defaults
           x (:left position 0)
           y (:top position 0)
           ^Long w (:width position 800)
           ^Long h (:height position 600)
           ^Long mon (or monitor MemoryUtil/NULL)
           ^Long shared-handle (or share MemoryUtil/NULL)
           hwnd (or handle
                    (do
                      (println "Creating a window with: " {:position position,
                                                           :title real-title
                                                           :monitor mon
                                                           :shared shared-handle})
                      (GLFW/glfwCreateWindow w h real-title mon shared-handle)))]
       (GLFW/glfwSetWindowPos hwnd x y)
       (GLFW/glfwMakeContextCurrent hwnd)   ; This is how it all gets wired together
       ;; I suppose this next value's debatable...but why would anyone ever not
       ;; want it?
       (GLFW/glfwSwapInterval 1)
       (when (:visible hints)
         (GLFW/glfwShowWindow hwnd))
       (into this {:handle hwnd
                   :position {:left x, :top y,
                              :width w, :height h}
                   :monitor mon
                   :share shared-handle
                   :title real-title}))))
  (stop
   [this]
   (when handle
     (GLFW/glfwDestroyWindow handle))
   (assoc this :handle nil)))

;; TODO: Make this go away. Callers should
;; pick the Component out of the app
(def window-holder {:window Window
                    s/Any s/Any})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal Helpers

(defn customize-window-hints
  [hints]
  (doseq [[k v] hints]
    (GLFW/glfwWindowHint (k hint-map) v))
  (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE K/gl-false))

(defn- transform-display-mode [^ByteBuffer m]
  (let [m1 (GLFWvidmode. m)]
    {:resolution {:x (.getWidth m1) :y (.getHeight m1)}
     :red (.getRedBits m1)
     :green (.getGreenBits m1)
     :blue (.getBlueBits m1)
     :refresh-rate (.getRefreshRate m1)
     :mode m1}))

(defn query-display-modes
  "Returns a map of all display modes available on monitor n"
  [^Long handle]
  (let [count-buffer (IntBuffer/allocate 1)
        ;; Currently, this is crashing my entire JVM
        ;; TODO: Give this a shot now that I've updated
        ;; everything
        ;; Q: Could the problem be something like an old
        ;; GLFW version?
        raw-buffer (GLFW/glfwGetVideoModes handle count-buffer)]
    (throw (ex-info "Not Implemented"
                    {:possibilities raw-buffer
                     :problem "Not sure how to move forward"}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Obsolete

(comment (defn create-window
           ([app resizable]
            (create-window 800 600 resizable))
           ([app w h resizable]
            (create-window 0 0 w h resizable))
           ([app x y w h resizable]
            (let [window-size (ref [w h])
                  window-position (ref [x y])
                  hwnd (atom nil)]
              (reify
                Window
                (destroy! [this]
                  (-> (InternalTextureLoader/get) .clear)
                  (context/destroy)
                  ;; It's tempting to call glfwTerminate after this.
                  ;; That temptation should be avoided.
                  (GLFW/glfwDestroyWindow @hwnd))
                (display-mode! [this w h]
                  (GLFW/glfwSetWindowSize @hwnd w h))
                (display-mode! [this mode]
                  ;; The original version was much more clever, including maximizing
                  ;; the color bits. That doesn't really seem to be an option in
                  ;; LWJGL3.
                  ;; That may be my lack of understanding, of course -- James
                  (display-mode! this (:mode mode)))
                (display-modes [this]
                  ;; Doesn't make any sense until after glfwInit.
                  ;; Then again, neither does anything else in here.
                  (let [monitors (GLFW/glfwGetMonitors)
                        count (.capacity monitors)]
                    (if (< 0 count)
                      (let [prime (GLFW/glfwGetPrimaryMonitor)
                            queryer (fn [acc n]
                                      (let [handle (.get monitors n)]
                                        (assoc acc (if (= handle prime)
                                                     :prime
                                                     (GLFW/glfwGetMonitorName handle))
                                               (query-display-modes handle))))]
                        (reduce queryer {} (range count)))
                      (throw (ex-info "No monitors!" {})))))
                (display-mode
                  [_]
                  (-> (pick-monitor)
                      GLFW/glfwGetVideoMode
                      transform-display-mode))
                (fullscreen! [_ flag]
                  ;; This fails: have to assign the window to a monitor when we create it.
                  ;; The interesting bit would really to have two windows sharing a context.
                  ;; Then show one or the other, depending on this flag.
                  ;; Maybe we could create a second window the first time a caller tries to
                  ;; alter this?
                  (comment (Display/setFullscreen flag))
                  (throw (RuntimeException. "Not Implemented")))
                (handle-resize! [this]
                  (dosync
                   ;; This winds up calling :reshape, which seems as though
                   ;; it really should take the window position into account.
                   ;; Then again, I'm not sure why anyone would care.
                   (when (resized? this)               
                     (let [[w h] (size this)
                           [x y] (position this)]
                       (ref-set window-size [w h])
                       (viewport 0 0 w h)
                       ;; Q: Should this care about position?
                       ;; A: Maybe not. But my caller does, if only for general configuration:
                       ;; I want to restore window position on restart.
                       (event/publish! app :reshape [x y w h])))))
                (init! [this x y w h]
                  ;; TODO: What are reasonable default values that don't
                  ;; screw up backwards compatibility?
                  (init! this x y w h 3 2))
                (init! [this x y w h opengl-major opengl-minor]
                  (GLFW/glfwWindowHint GLFW/GLFW_RESIZABLE (if resizable 1 0))
                  (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MAJOR opengl-major)
                  (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MINOR opengl-minor)
                  (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE gl-false)
                  (let [handle (GLFW/glfwCreateWindow w h (:title app) MemoryUtil/NULL MemoryUtil/NULL)]
                    ;; This sort of thing was added to the road map for GLFW 3.2, but
                    ;; it really isn't something that anyone wants
                    (comment (Display/setParent nil))
                    ;; FIXME: Move to (x, y).
                    ;; Unless full screen. Which isn't actually
                    ;; implemented in any way, shape, or form yet.
                    (GLFW/glfwSetWindowPos handle x y)
                    (comment (display-mode! this w h))

                    ;; Configure the rest of it
                    ( .clear (InternalTextureLoader/get))
                    (TextureImpl/bindNone)
                    (let [[w h] (size this)]
                      (viewport 0 0 w h))
                    (GLFW/glfwShowWindow hwnd)
                    (reset! hwnd handle)))
                (init! [this]
                  (init! this w h))
                (init! [this w h]
                  ;; FIXME: Honestly, this should be centered, or something.
                  (init! this x y w h))
                (position [this]
                  (let [hwnd @hwnd
                        x-buffer (IntBuffer/allocate 1)
                        y-buffer (IntBuffer/allocate 1)]
                    (GLFW/glfwGetWindowPos hwnd x-buffer y-buffer)
                    (let [x (.get x-buffer)
                          y (.get y-buffer)]
                      ;; I'd rather do:
                      #_{:x x
                         :y y}
                      ;; But existing code is based around
                      [x y])))
                (process! [_] (GLFW/glfwPollEvents))
                (resized? [this]
                  ;; This doesn't seem like a very useful approach.
                  ;; But it's a start.
                  ;; TODO: Be more realistic about this. If nothing else,
                  ;; need to update w and h if this has changed.
                  ;; Better yet: set a flag in a resize event handler
                  (let [last-size @window-size
                        current-size (size this)]
                    (not= last-size current-size)))
                (size [this]
                  (let [w-buffer (IntBuffer/allocate 1)
                        h-buffer (IntBuffer/allocate 2)]
                    (GLFW/glfwGetWindowSize @hwnd w-buffer h-buffer)
                    [(.get w-buffer) (.get h-buffer)]))
                (vsync! [_ flag]
                  (GLFW/glfwSwapInterval (if flag 1 0)))))))

         (defn create-fixed-window 
  "One of the truly obnoxious pieces of this puzzle is that
  create-resizable-window is almost exactly the same."
  ([app] create-fixed-window app 800 600)
  ([app w h]
   (create-fixed-window app 0 0 w h))
  ([app x y w h]
   (create-window app x y w h false)))

         (defn create-resizable-window
           ([app] create-resizable-window app 800 600)
           ([app w h]
            (create-resizable-window app 0 0 w h))
           ([app x y w h]
            (create-window app x y w h true)))

         (defmacro with-window
  "This should probably be considered obsolete.
  At the same time...it's very tempting."
  [window & body]
  `(context/with-context nil
     (binding [*window* ~window]
       ~@body))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Public

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; API
;;; Part of refactoring away from a Protocol to Components

(s/defn close? :- s/Bool
  [owner :- window-holder]
  (let [^Window window (:window owner)
        ^Long handle (:handle window)]
    (try
      (GLFW/glfwWindowShouldClose handle)
      (catch RuntimeException e
        true))))

(comment (s/defn invalidated? :- s/Bool
           [owner :- window-holder]
           (let [^Window window (:window owner)]
             ;; TODO: Check whether there's been a paint
             ;; request, or whatever the GLFW equivalent
             ;; is, since the last time this was called
             ;; Deprecated may be the wrong name.
             ;; I haven't seen anything in the docs or source that looks like
             ;; this sort of thing is still available.
             ;; TODO: Looks like there's a window refresh callback that covers this
             (comment (throw (RuntimeException. "Deprecated")))
             true)))

(s/defn size :- {:width s/Int, :height s/Int}
  [window :- Window]
  (let [^IntBuffer w-buffer (IntBuffer/allocate 1)
        ^IntBuffer h-buffer (IntBuffer/allocate 2)
        ^Long handle (:handle window)]
    (GLFW/glfwGetWindowSize handle w-buffer h-buffer)
    {:width (.get w-buffer), :height (.get h-buffer)}))

(s/defn title!
  [window :- Window
   ^String title]
  (let [^Long handle (:handle window)]
    (GLFW/glfwSetWindowTitle handle title)))


(s/defn update!
  [owner :- window-holder]
  (let [^Window window (:window owner)
        ^Long handle (:handle window)]
    (GLFW/glfwSwapBuffers handle)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Management

(s/defn pick-monitor :- s/Int
  "LWJGL and GLFW support multiple monitors now.
  Which makes life significantly more complicated.
  For now, just go with the primary"
  []
  (GLFW/glfwGetPrimaryMonitor))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn close!
  ([window :- Window
    should? :- s/Bool]
   (GLFW/glfwSetWindowShouldClose (:handle window) should?))
  ([window :- Window]
   (close! window true)))

(s/defn key-pressed? :- s/Bool
  "This really seems to belong in the input namespace, but that's the wrong Component"
  [component :- Window
   key :- (s/either s/Keyword java.lang.Character)]
  ;; It's tempting to used glfwGetKey here.
  ;; That feels wrong, but it might simplify things.
  ;; More importantly...it's silly to duplicate the keymap
  ;; that glfw is already maintaining.
  (comment ((-> component :keys deref vals set) key))
  (let [current-state (GLFW/glfwGetKey (:handle component) (util/translate-us-token-to-key-code key))]
    (= current-state GLFW/GLFW_PRESS)))

(s/defn ctor :- Window
  [{:keys [handle hints position title]}]
  (let [params (cond-> {}
                 handle (assoc :handle handle)
                 hints (assoc :hints hints)
                 position (assoc :position position)
                 title (assoc :title title))]
    (map->Window params)))

(s/defn visible? :- s/Bool
  [component :- Window]
  (let [v (GLFW/glfwGetWindowAttrib (:handle component) GLFW/GLFW_VISIBLE)]
    (not= v gl-false)))
