(ns penumbra.constants
  "Convert named java constants into dash-syntax"
  (:import [org.lwjgl.opengl GL11]))

;; TODO: Do something magical with reflection instead
;; Or maybe just use values that have apparently been
;; defined in penumbra.opengl.core
(def gl-true GL11/GL_TRUE)
(def gl-false GL11/GL_FALSE)


