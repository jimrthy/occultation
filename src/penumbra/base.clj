(ns penumbra.base
  "Baseline functionality that really needs to run before everything else")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(defrecord BaseSystem [error-callback]
  component/Lifecycle
  (start
    [this]
    (when error-callback
      (let [cb (proxy [GLFWErrorCallback] []
                 (invoke
                   [error-code ^Long description]
                   (let [s (Callbacks/errorCallbackDescriptionString description)]
                     (error-callback error-code s))))])
      (GLFW/glfwSetErrorCallback cb))
    (GLFW/glfwInit)
    this)
  (stop
    [this]
    (GLFW/glfwTerminate)
    this))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn ctor
  [overrides]
  (map->BaseSystem (select-in vals [error-callback])))