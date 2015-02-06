;;   Copyright (c) Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.app.input
  (:require [clojure.core.async :as async :refer (>!!)]
            [com.stuartsierra.component :as component]
            [penumbra.app.window :as window]
            [penumbra.app.event :as event]
            [penumbra.utils :refer (indexed)])
  (:import [org.lwjgl.glfw
            GLFW
            GLFWCursorEnterCallback
            GLFWCursorPosCallback
            GLFWErrorCallback   ; FIXME: This one's special
            GLFWKeyCallback
            GLFWMouseButtonCallback
            GLFWScrollCallback]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

;; Q: Does a reference to the App really make sense here?
;; It seems like the Window component would be more
;; appropriate...either way, it also seems like we're
;; risking circular dependencies.
(defrecord Input [app buttons]
  component/Lifecycle
  (start
    [this]
    ;; Keyboard used to be something imported from LWJGL
    ;; Q: Where should this functionality come from now?
    ;; A: As far as I can tell, it looks like this has just
    ;; disappeared.
    ;; Actually, according to
    ;; https://github.com/LWJGL/lwjgl3/issues/13, the entire
    ;; Keyboard class is gone.
    ;; Q: What, if anything, should replace this?
    (comment (if (Keyboard/isCreated)
               (do
                 ;; Q: Do these make sense here?
                 (Keyboard/create)
                 (Mouse/create))))
    (let [buttons (or buttons (ref {}))]
      (dosync
       ;; Signal that all keys and mouse buttons have
       ;; been released.
       ;; This seems like a questionable choice, but
       ;; it matches current functionality.
       (doseq [[b loc] @buttons]
         (event/publish! app :mouse-up loc b)
         (event/publish! app :mouse-click loc b))
       (ref-set buttons {})))
    this)

  (stop
    [this]
    (assoc this
           :buttons (ref {}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helper Functions

(defn take-every-other
  [seq]
  (->> seq
       (partition 2)
       (map first)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Macros

(defmacro callback-creators
  "FIXME: This desperately needs to be tested.
  Actually, it should go away/be updated to match the lwjgl3 event system

  Create callback wrappers that can be used to associate your callbacks with a given window.

  name - installation function names will be based around this.
  You'll wind up with low-level-~name-callback, basic-~name-callback, and ~name-callback

  klass - the class that will be overridden

  installer - the function to call to do the actual callback installation

  initial-parameters - what the lowest-level callback handler gets called with

  translation - how the basic callback handler translates those initial-parameters
  into something meaningful. This is really a let vector. The values bound here will
  be sent to the intermediate callback, in order. This part isn't smart enough to cope
  with type hints yet.

  translated - the shape of the data that should be fed into a core.async channel
  from the high-level callback handler. This is probably a map of values from translation."
  [name klass installer initial-parameters translation translated]
  ;; TODO: Doesn't the auto-gensym only work inside a back-tick?
  ;; (i.e. I think I need to use real gensyms here
  (let [name# name
        low-level-name# (symbol (str "low-level-" name# "-callback"))
        translator-name# (symbol (str "basic-" name# "-callback"))
        high-level-name# (symbol (str name# "-callback"))
        params-1# initial-parameters
        translated-values# (take-every-other params-1#)]
    `(do (defn ~'low-level-name#
           [^Long ~'window# cb#]
           ;; Note that this breaks if the superclass ctor needs parameters
           (let [handler# (proxy [~klass] []
                            (invoke
                              ~(conj [^Long ~'window#] params-1#)
                              (cb# ~@params-1#)))]
             (~installer ~'window# handler#)))
         (defn ~'translator-name#
           [^Long window# cb#]
           (let [wrapper (fn ~(conj [^Long ~'window#] params-1#)
                           (let ~translation
                             (cb# ~'window# ~@translated-values#)))]))
         (defn ~high-level-name#
           [^Long ~'window# channel#]
           (let [wrapper (fn [^Long ~'window# ~@translated-values#]
                           (>!! channel# ~'window# ~translated))])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Keyboard

;;; Lowest level events

(defn low-level-key-callback
  "Sets up cb as the keyboard event handler for window.

Returns the previous callback, if any.
Call with cb == NULL to remove the current callback.

This gets called when a key is pressed, repeated, or released"
  [^Long window
   cb]
  (let [handler (proxy
                    [GLFWKeyCallback]
                    []
                  (invoke
                    [^Long window ^Long key ^Long scan-code ^Long action ^Long mods]
                    ;; Call with the window handle, since one callback could be managing
                    ;; multiple windows.
                    ;; It doesn't seem likely, but it doesn't hurt.
                    (cb window key scan-code action mods)))]
    (GLFW/glfwSetKeyCallback window handler)))

(defn key->sym
  [key-code]
  ;; This is probably just going to be a giant case statement
  (throw (ex-info "not implemented" {})))

(defn key-action->sym
  [action]
  (condp = action
    GLFW/GLFW_RELEASE :release
    GLFW/GLFW_REPEAT :repeat
    GLFW/GLFW_PRESS :press))

(defn key-mods->sym-seq
  [^Long mod-flags]
  (let [mods [[1 :shift]
              [2 :ctrl]
              [4 :alt]
              [8 :super]]]
    (filter identity
            (map (fn [[flag mod]]
                   (when (not= (bit-and mod-flags flag) 0)
                     :mod))))))

(defn basic-key-callback
  "Translates the codes into something useful when a key is pressed, repeated, or released."
  [^Long window
   cb]
  (let [wrapper (fn [^Long window ^Long key ^Long scan-code ^Long action ^Long mods]
                  (let [key-sym (key->sym key)
                        action-sym (key-action->sym action)
                        mod-flags (key-mods->sym-seq mods)]
                    (cb window key-sym action-sym mod-flags)))]
    (low-level-key-callback window wrapper)))

(defn key-callback
  "Feeds key event data into an async channel"
  [^Long window
   channel]
  (let [wrapper (fn [^Long window key-sym action-sym mod-flags]
                  (>!! channel {window {:key key-sym
                                        :action action-sym
                                        :mods mod-flags}}))]
    (basic-key-callback window wrapper)))

;;; Individual character events

;;;; FIXME: Debug this!
(comment (callback-creators char GLFWCharCallback GLFW/glfwSetCharCallback
                            [code-point]
                            [code code-point]
                            {:code-point code}))

;;; TODO: Also need char and char-mods callbacks

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Mouse

(defn- mouse-button->sym [button-idx]
  (condp = button-idx
    GLFW/GLFW_MOUSE_BUTTON_1 :left
    GLFW/GLFW_MOUSE_BUTTON_2 :right
    GLFW/GLFW_MOUSE_BUTTON_3 :center
    (keyword (str "mouse-" (inc button-idx)))))

(comment (defn- handle-mouse [app mouse-buttons]
           (let [[w h] (window/size app)]
             (loop [mouse-buttons mouse-buttons]
               (Mouse/poll)
               (if (Mouse/next)
                 (let [dw (Mouse/getEventDWheel)
                       dx (Mouse/getEventDX), dy (- (Mouse/getEventDY))
                       x (Mouse/getEventX), y (- h (Mouse/getEventY))
                       button (Mouse/getEventButton)
                       button? (not (neg? button))
                       button-state (Mouse/getEventButtonState)]
                   (when (not (zero? dw))
                     (event/publish! app :mouse-wheel dw))
                   (cond
                     ;;mouse down/up
                     (and (zero? dx) (zero? dy) button?)
                     (do
                       (event/publish! app (if button-state :mouse-down :mouse-up) [x y] (mouse-button-name button))
                       (if button-state
                         (recur (assoc mouse-buttons (mouse-button-name button) [x y]))
                         (let [loc (mouse-buttons button)]
                           (event/publish! app :mouse-click loc (mouse-button-name button))
                           (recur (dissoc mouse-buttons (mouse-button-name button))))))
                     ;;mouse-move
                     (and
                      (empty? mouse-buttons)
                      (or (not (zero? dx)) (not (zero? dy))))
                     (do
                       (event/publish! app :mouse-move [dx dy] [x y])
                       (recur mouse-buttons))
                     ;;mouse-drag
                     :else
                     (do
                       (doseq [b (keys mouse-buttons)]
                         (event/publish! app :mouse-drag [dx dy] [x y] b))
                       (recur mouse-buttons))))
                 mouse-buttons)))))

(defn low-level-mouse-button-callback
  [^Long window
   cb]
    (proxy [GLFWMouseButtonCallback] []
      (invoke [^Long window ^Long button ^Long action ^Long mods]
        (cb window button action mods))))

(defn basic-mouse-button-callback
  [^Long window
   cb]
  (let [handler (fn [^Long window ^Long button ^Long action ^Long mods]
                  (let [button-sym (mouse-button->sym button)
                        action-sym (key-action->sym action)
                        mod-flags (key-mods->sym-seq mods)]
                    (cb window button-sym action-sym mod-flags)))]
    (low-level-mouse-button-callback window cb)))

(defn mouse-button-callback
  [^Long window
   channel]
  (let [handler (fn [^Long window button-sym action-sym mod-flags]
                  (>!! channel {window {:button button-sym
                                        :action action-sym
                                        :mods mod-flags}}))]
    (basic-mouse-button-callback window handler)))

;;; FIXME: Also need callbacks for mouse-position, mouse-scroll, and mouse-enter
;;; I think it's time to write a macro

;;;

(comment (defn create [app]
           (let [keys (ref {})
                 buttons (ref {})]
             (reify
               InputHandler
               
               (button-pressed? [_ button] (@buttons button))
               (mouse-location [_] (let [[w h] (window/size app)]
                                     [(Mouse/getX) (- h (Mouse/getY))]))
               (handle-mouse! [_] (dosync (alter buttons #(handle-mouse app %))))
               (handle-keyboard! [_] (dosync (alter keys #(handle-keyboard app %))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn ctor
  [defaults]
  (map->Input defaults))
