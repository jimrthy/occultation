;;   Copyright (c) 2012 Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.app.window
  (:use [penumbra.opengl])
  (:require [penumbra.opengl
             [texture :as texture]
             [context :as context]]
            [penumbra.text :as text]
            [penumbra.app.event :as event]
            [penumbra.app.core :as app])
  (:import [org.lwjgl.opengl ContextAttribs DisplayMode]
           [org.newdawn.slick.opengl InternalTextureLoader TextureImpl]
           [java.awt Frame Canvas GridLayout Color]
           [java.awt.event WindowAdapter]))

;;;

(defprotocol Window
  (close? [w] "Returns true if the user has requested it be closed.")
  (destroy! [w] "Destroys the window.")
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
  (vsync! [w flag] "Toggles vertical sync."))

;;;

(defn- transform-display-mode [m]
  {:resolution [(.getWidth m) (.getHeight m)]
   :bpp (.getBitsPerPixel m)
   :fullscreen (.isFullscreenCapable m)
   :mode m})

(defn create-window
  ([app resizable]
     (create-window 800 600 resizable))
  ([app w h resizable]
     (create-window 0 0 w h resizable))
  ([app x y w h resizable]
      (let [window-size (ref [w h])
            window-position (ref [x y])]
        (reify
          Window
          (close? [this] (try
                           ;; TODO: Don't use a dynamic "global" like this
                           ;; But this seems expedient
                           (GLFW/glfwWindowShouldClose *window*)
                           (catch Exception e
                             true)))
          (destroy! [this]
            (-> (InternalTextureLoader/get) .clear)
            (context/destroy)
            (GLFW/glfwDestroyWindow *window*))
          (display-modes [_] (map transform-display-mode (Display/getAvailableDisplayModes)))
          (display-mode [_] (transform-display-mode (Display/getDisplayMode)))
          (display-mode! [this w h]
            (Display/setDisplayMode (DisplayMode. w h)))
          (fullscreen! [_ flag] (Display/setFullscreen flag))
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
            (init! this x y w h ))
          (init! [this x y w h opengl-major opengl-minor]
            (when-not (Display/isCreated)
              (Display/setResizable resizable)
              ;; Q: How well does LWJGL cooperate to include
              ;; itself as a child window these days, if I want
              ;; to embed it??
              (Display/setParent nil)
              (let [version (ContextAttribs. opengl-major opengl-minor)]
                (Display/create (PixelFormat.) version))
              ;; FIXME: Move to (x, y).
              ;; Unless full screen. Which isn't actually
              ;; implemented in any way, shape, or form yet.
              (Display/setLocation x y)
              (display-mode! this w h))
            (-> (InternalTextureLoader/get) .clear)
            (TextureImpl/bindNone)
            (let [[w h] (size this)]
              (viewport 0 0 w h)))
          (init! [this]
            (init! this w h))
          (init! [this w h]
            ;; FIXME: Honestly, this should be centered, or something.
            (init! this x y w h))
          (invalidated? [_] (Display/isDirty))
          (position [this]
            (let [x (Display/getX)
                  y  (Display/getY)]
              [x y]))
          (process! [_] (Display/processMessages))
          (resized? [this]
            (Display/wasResized))
          (size [this]
            (let [w (Display/getWidth)
                  h (Display/getHeight)]
              [w h]))
          (title! [_ title] (Display/setTitle title))
          (update! [_] (Display/update))
          (vsync! [_ flag] (Display/setVSyncEnabled flag))))))

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

(defmacro with-window [window & body]
  `(context/with-context nil
     (binding [*window* ~window]
       ~@body)))

