(ns penumbra.base
  "Baseline functionality that really needs to run before everything else"
  (:require [com.stuartsierra.component :as component])
  (:import [org.lwjgl.glfw Callbacks GLFW GLFWErrorCallback]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(defrecord BaseSystem [error-callback initialized]
  component/Lifecycle
  (start
    [this]
    (when error-callback
      (let [cb (proxy [GLFWErrorCallback] []
                 (invoke
                   [error-code ^Long description]
                   (let [s (Callbacks/errorCallbackDescriptionString description)]
                     (error-callback error-code s))))]
        (GLFW/glfwSetErrorCallback cb)))
    (when-not initialized
      (GLFW/glfwInit))
    (assoc this :initialized true))
  (stop
    [this]
    ;; I'm getting a SIGSEGV here. It has something to do with a mysterious
    ;; XIO Fatal IO Error 11.
    ;; The best hint google's supplied so far is that it might have something
    ;; to do with multi-threading.
    ;; Q: What are the odds that this gets called from something besides the
    ;; main thread?
    ;; I've tried it in both emacs and reply, but they're both going over
    ;; nrepl to what I think is the same back-end.
    (comment (GLFW/glfwTerminate))
    this))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn ctor
  [overrides]
  (map->BaseSystem (select-keys overrides [:error-callback])))
