;;   Copyright (c) 2012 Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.app.window
  (:require [com.stuartsierra.component :as component]
            [penumbra.constants :as K]
            [penumbra.opengl
             [texture :as texture]
             [context :as context]]
            ;; FIXME: Don't do this.
            [penumbra.opengl :refer :all]
            [penumbra.opengl.core :refer (gl-false)]
            [penumbra.text :as text]
            [penumbra.app.event :as event]
            [penumbra.app.core :as app])
  (:import [org.lwjgl.glfw GLFW GLFWvidmode]
           [org.lwjgl.system MemoryUtil]
           [org.newdawn.slick.opengl InternalTextureLoader TextureImpl]
           [java.awt Frame Canvas GridLayout Color]
           [java.awt.event WindowAdapter]
           [java.nio ByteBuffer IntBuffer]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(comment (defprotocol Window
           (close? [w] "Returns true if the user has requested it be closed.")
           (destroy! [w] "Destroys the window.")
           (display-mode! [window w h] [w mode] "Sets the window size")
           (display-modes [w] "Returns all display modes supported by the display device.")
           (display-mode [w] "Returns the current display mode.")
           (fullscreen! [w flag] "Toggles fullscreen mode.")
           (handle-resize! [w] "Handles any resize events.  If there wasn't a resizing, this is a no-op.")
           (init! [w]
             [wnd w h]
             [wnd x y w h]
             [wnd x y w h opengl-major opengl-minor]
             "Initializes the window.")
           (invalidated? [w] "Returns true if the window is invalidated by the operating system.")
           (position [w] "Returns the current location of the application.")
           (process! [w] "Processes all messages from the operating system.")
           (resizable! [w flag] "Sets whether the window is resizable or not.")
           (resized? [w] "Returns true if application was resized since handle-resize! was last called.")
           (size [w] "Returns the current size of the application.")
           (title! [w title] "Sets the title of the application.")
           (update! [w] "Swaps the buffers.")
           (vsync! [w flag] "Toggles vertical sync.")))

(declare customize-window-hints)
(defrecord Window [^Long handle
                   position
                   hints
                   ^Long monitor
                   ^Long share
                   title]
  component/Lifecycle
  (start
    [this]
    (GLFW/glfwDefaultWindowHints)
    ;; TODO: Default to invisible window that's convenient for moving
    (when (seq hints)
      (customize-window-hints hints))
    (let [;; TODO: Don't use magic numbers for the defaults
          x (:left position 0)
          y (:top position 0)
          w (:width position 800)
          h (:height position 600)
          mon (or monitor MemoryUtil/NULL)
          shared-handle (or share MemoryUtil/NULL)
          hwnd (or handle
                   (GLFW/glfwCreateWindow w h title mon shared-handle))]
      (GLFW/glfwSetWindowPos handle x y)
      (GLFW/glfwMakeContextCurrent handle)   ; This is how it all gets wired together
      ;; I suppose this next value's debatable...but why would anyone ever not
      ;; want it?
      (GLFW/glfwSwapInterval 1)
      (when (:visible hints)
        (GLFW/glfwShowWindow handle))
      (into this {:handle hwnd
                  :position {:left x, :top y,
                             :width w, :height h}
                  :monitor mon
                  :share shared-handle})))
  (stop
    [this]
    (GLFW/glfwDestroyWindow handle)
    (assoc this :handle nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal Helpers

(defn customize-window-hints
  [hints]
  (let [hint-map {:resizable GLFW/GLFW_RESIZABLE         ; bool
                  ;; Don't show until it's positioned
                  ; :visible GLFW/GLFW_VISIBLE             ; bool
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
                  :profile GLFW/GLFW_OPENGL_PROFILE}]
    (doseq [[k v] hints]
      (GLFW/glfwWindowHint (k hint-map) v))
    (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE K/gl-false)))

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
                (close? [this] (try
                                 (GLFW/glfwWindowShouldClose @hwnd)
                                 (catch RuntimeException e
                                   true)))
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
                (invalidated? [_]
                  ;; Deprecated may be the wrong name.
                  ;; I haven't seen anything in the docs or source that looks like
                  ;; this sort of thing is still available.
                  ;; TODO: Looks like there's a window refresh callback that covers this
                  (throw (RuntimeException. "Deprecated")))
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
                (title! [this title]
                  (GLFW/glfwSetWindowTitle @hwnd title))
                (update! [this]
                  (process! this)
                  (GLFW/glfwSwapBuffers @hwnd))
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
;;; Public

(defn pick-monitor
  "LWJGL and GLFW support multiple monitors now.
  Which makes life significantly more complicated.
  For now, just go with the primary"
  []
  (GLFW/glfwGetPrimaryMonitor))

(defn ctor
  [{:keys [handle hints position]}]
  (let [params (cond-> {}
                 handle (assoc :handle handle)
                 hints (assoc :hints hints)
                 position (assoc :position position))]
    (map->Window params)))
