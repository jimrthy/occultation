(ns penumbra.example.wiki.texture1
  (:use [penumbra.opengl])
  (:require [penumbra.app.minimal :as app]))

(defn init [state]
  (enable :texture-2d)
  (assoc state
    :texture (load-texture-from-file "/Users/hb/Downloads/sig.png")))

(defn display [_ state]
  (println (:texture state))
  (blit (:texture state)))

(defn start []
  (app/start "Texture" {:init init :display display} {}))
