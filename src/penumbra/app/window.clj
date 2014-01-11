;;   Copyright (c) Zachary Tellman. All rights reserved.
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
  (:import [org.lwjgl.opengl Display PixelFormat ContextAttribs]
           [org.newdawn.slick.opengl InternalTextureLoader TextureImpl]
           [java.awt Frame Canvas GridLayout Color]
           [java.awt.event WindowAdapter]))

;;;

(defprotocol Window
  (display-modes [w] "Returns all display modes supported by the display device.")
  (display-mode [w] "Returns the current display mode.")
  (display-mode! [window w h] [w mode] "Sets the display mode.")
  (title! [w title] "Sets the title of the application.")
  (size [w] "Returns the current size of the application.")
  (position [w] "Returns the current location of the application.")
  (resizable! [w flag] "Sets whether the window is resizable or not.")
  (resized? [w] "Returns true if application was resized since handle-resize! was last called.")
  (invalidated? [w] "Returns true if the window is invalidated by the operating system.")
  (close? [w] "Returns true if the user has requested it be closed.")
  (process! [w] "Processes all messages from the operating system.")
  (update! [w] "Swaps the buffers.")
  (handle-resize! [w] "Handles any resize events.  If there wasn't a resizing, this is a no-op.")
  (init! [w]
    [wnd w h]
    [wnd x y w h]
    [wnd x y w h opengl-major opengl-minor]
    "Initializes the window.")
  (destroy! [w] "Destroys the window.")
  (vsync! [w flag] "Toggles vertical sync.")
  (fullscreen! [w flag] "Toggles fullscreen mode."))

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
          (vsync! [_ flag] (Display/setVSyncEnabled flag))
          (fullscreen! [_ flag] (Display/setFullscreen flag))
          (title! [_ title] (Display/setTitle title))
          (display-modes [_] (map transform-display-mode (Display/getAvailableDisplayModes)))
          (display-mode [_] (transform-display-mode (Display/getDisplayMode)))
          (display-mode! [_ mode] (Display/setDisplayMode (:mode mode)))
          (display-mode! [this w h]
            (let [modes (display-modes this)
                  max-bpp (apply max (map :bpp modes))]
              (->> modes
                   (filter #(= max-bpp (:bpp %)))
                   (sort-by #(Math/abs (apply * (map - [w h] (:resolution %)))))
                   first
                   (display-mode! this))))
          (size [this]
            (let [w (Display/getWidth)
                  h (Display/getHeight)]
              [w h]))
          (position [this]
            (let [x (Display/getX)
                  y  (Display/getY)]
              [x y]))
          (resized? [this]
            (Display/wasResized))
          (invalidated? [_] (Display/isDirty))
          (close? [_] (try
                        (Display/isCloseRequested)
                        (catch Exception e
                          true)))
          (update! [_] (Display/update))
          (process! [_] (Display/processMessages))
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
            (init! this 800 600))
          (init! [this w h]
            ;; FIXME: Honestly, this should be centered, or something.
            (init! this 0 0 w h))
          (destroy! [_]
            (-> (InternalTextureLoader/get) .clear)
            (context/destroy)
            (Display/destroy))))))

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

