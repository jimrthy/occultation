(ns penumbra.utils
  (:require [clojure.core.async :as async]
            [clojure.pprint :refer (pprint)]
            [schema.core :as s])
  (:import [org.lwjgl.glfw GLFW]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def promise-class (class (promise)))

(s/defschema state {s/Any s/Any})

;; This feels klunky, but it's probably the 'right' way
(def async-channel (class (async/chan)))
(s/defschema channel-map {s/Keyword async-channel})

(s/defschema input-channel-map {:char-input async-channel
                                :key-change async-channel
                                :mouse-move async-channel
                                :mouse-button async-channel})

(s/defschema Position {:left s/Int
                       :top s/Int
                       :width s/Int
                       :height s/Int})

;; This isn't strictly schema, but it at least sort-of
;; fulfills a similar role in app.window
;; TODO: Come up with a better approach
(def hint-map
  {:resizable GLFW/GLFW_RESIZABLE ; bool
   ;; Don't show until it's positioned
   ;; (i.e. Don't let callers override, though that seems
   ;; like an obnoxious choice
   ;; :visible GLFW/GLFW_VISIBLE             ; bool
   :decorated GLFW/GLFW_DECORATED ; bool
   :red GLFW/GLFW_RED_BITS
   :green GLFW/GLFW_GREEN_BITS
   :blue GLFW/GLFW_BLUE_BITS
   :alpha GLFW/GLFW_ALPHA_BITS
   :depth GLFW/GLFW_DEPTH_BITS
   :stencil GLFW/GLFW_STENCIL_BITS
   :accum-red GLFW/GLFW_ACCUM_RED_BITS ; int
   :accum-green GLFW/GLFW_ACCUM_GREEN_BITS ; int
   :accum-blue GLFW/GLFW_ACCUM_BLUE_BITS   ; int
   :accum-alpha GLFW/GLFW_ACCUM_ALPHA_BITS ; int
   :aux-buffers GLFW/GLFW_AUX_BUFFERS      ; int
   :samples GLFW/GLFW_SAMPLES              ; int
   :refresh-rate GLFW/GLFW_REFRESH_RATE    ; int
   :stereo GLFW/GLFW_STEREO                ; bool
   :srgb-capable GLFW/GLFW_SRGB_CAPABLE    ; bool
   :client-api GLFW/GLFW_OPENGL_API ; opengl or opengl-es
   :major GLFW/GLFW_CONTEXT_VERSION_MAJOR
   :minor GLFW/GLFW_CONTEXT_VERSION_MINOR
   :robust GLFW/GLFW_CONTEXT_ROBUSTNESS ; interesting enum
   :forward GLFW/GLFW_OPENGL_FORWARD_COMPAT
   :deugging GLFW/GLFW_OPENGL_DEBUG_CONTEXT
   :profile GLFW/GLFW_OPENGL_PROFILE})
;; TODO: Make the legal values explicit
(def legal-window-hints {(s/enum (keys hint-map)) s/Any})

(s/defschema window-configuration {:handle s/Int
                                   :hints legal-window-hints
                                   :monitor s/Int
                                   :position Position
                                   :share s/Int
                                   :title s/Str})
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn close-when!
  [ch]
  (when ch (async/close! ch)))

(defmacro defmacro-
  "Same as defmacro but yields a private definition"
  [name & decls]
  (list* `defmacro (with-meta name (assoc (meta name) :private true)) decls))

; defn-memo by Chouser:
(defmacro defn-memo
  "Just like defn, but memoizes the function using clojure.core/memoize"
  [fn-name & defn-stuff]
  `(do
     (defn ~fn-name ~@defn-stuff)
     (alter-var-root (var ~fn-name) memoize)
     (var ~fn-name)))

(defn indexed
  "Returns a lazy sequence of [index, item] pairs, where items come
from 's' and indexes count up from zero.

Sample: (indexed '(a b c d)) => ([0 a] [1 b] [2 c] [3 d])"
  [s]
  (map-indexed vector s))

(s/defn pretty :- s/Str
  [o :- s/Any]
  (with-out-str (pprint o)))

(s/defn random-uuid :- s/Uuid
  "Silly wrapper function to avoid needing to look up the official version"
  []
  (java.util.UUID/randomUUID))

(defn separate
  "Returns a vector:
[ (filter f s), (filter (complement f) s) ]"
  [f s]
  ((juxt filter remove) f s))

(s/defn translate-us-token-to-key-code :- s/Int
  "Really for checking whether a specific key is pressed or not.
  Revolves around the standard US keyboard layout. For inputting text,
  use the Unicode character callback instead"
  [key :- (s/either s/Keyword java.lang.Character)]
  ;; TODO: Add the rest of the shifted characters
  (let [m {:0 GLFW/GLFW_KEY_0
           \0 GLFW/GLFW_KEY_0
           \1 GLFW/GLFW_KEY_1
           :1 GLFW/GLFW_KEY_1
           :2 GLFW/GLFW_KEY_2
           \2 GLFW/GLFW_KEY_2
           :3 GLFW/GLFW_KEY_3
           \3 GLFW/GLFW_KEY_3
           :4 GLFW/GLFW_KEY_4
           \4 GLFW/GLFW_KEY_4
           :5 GLFW/GLFW_KEY_5
           \5 GLFW/GLFW_KEY_5
           :6 GLFW/GLFW_KEY_6
           \6 GLFW/GLFW_KEY_6
           :7 GLFW/GLFW_KEY_7
           \7 GLFW/GLFW_KEY_7
           :8 GLFW/GLFW_KEY_8
           \8 GLFW/GLFW_KEY_8
           :9 GLFW/GLFW_KEY_9
           \9 GLFW/GLFW_KEY_9
           :a GLFW/GLFW_KEY_A
           \a GLFW/GLFW_KEY_A
           \A GLFW/GLFW_KEY_A
           :b GLFW/GLFW_KEY_B
           \b GLFW/GLFW_KEY_B
           \B GLFW/GLFW_KEY_B
           :c GLFW/GLFW_KEY_C
           \c GLFW/GLFW_KEY_C
           \C GLFW/GLFW_KEY_C
           :d GLFW/GLFW_KEY_D
           \d GLFW/GLFW_KEY_D
           \D GLFW/GLFW_KEY_D
           :e GLFW/GLFW_KEY_E
           \e GLFW/GLFW_KEY_E
           \E GLFW/GLFW_KEY_E
           :f GLFW/GLFW_KEY_F
           \f GLFW/GLFW_KEY_F
           \F GLFW/GLFW_KEY_F
           :g GLFW/GLFW_KEY_G
           \g GLFW/GLFW_KEY_G
           \G GLFW/GLFW_KEY_G
           :h GLFW/GLFW_KEY_H
           \h GLFW/GLFW_KEY_H
           \H GLFW/GLFW_KEY_H
           :i GLFW/GLFW_KEY_I
           \i GLFW/GLFW_KEY_I
           \I GLFW/GLFW_KEY_I
           :j GLFW/GLFW_KEY_J
           \j GLFW/GLFW_KEY_J
           \J GLFW/GLFW_KEY_J
           :k GLFW/GLFW_KEY_K
           \k GLFW/GLFW_KEY_K
           \K GLFW/GLFW_KEY_K
           :l GLFW/GLFW_KEY_L
           \l GLFW/GLFW_KEY_L
           \L GLFW/GLFW_KEY_L
           :m GLFW/GLFW_KEY_M
           \m GLFW/GLFW_KEY_M
           \M GLFW/GLFW_KEY_M
           :n GLFW/GLFW_KEY_N
           \n GLFW/GLFW_KEY_N
           \N GLFW/GLFW_KEY_N
           :o GLFW/GLFW_KEY_O
           \o GLFW/GLFW_KEY_O
           \O GLFW/GLFW_KEY_O
           :p GLFW/GLFW_KEY_P
           \p GLFW/GLFW_KEY_P
           \P GLFW/GLFW_KEY_P
           :q GLFW/GLFW_KEY_Q
           \q GLFW/GLFW_KEY_Q
           \Q GLFW/GLFW_KEY_Q
           :r GLFW/GLFW_KEY_R
           \r GLFW/GLFW_KEY_R
           \R GLFW/GLFW_KEY_R
           :s GLFW/GLFW_KEY_S
           \s GLFW/GLFW_KEY_S
           \S GLFW/GLFW_KEY_S
           :t GLFW/GLFW_KEY_T
           \t GLFW/GLFW_KEY_T
           \T GLFW/GLFW_KEY_T
           :u GLFW/GLFW_KEY_U
           \u GLFW/GLFW_KEY_U
           \U GLFW/GLFW_KEY_U
           :v GLFW/GLFW_KEY_V
           \v GLFW/GLFW_KEY_V
           \V GLFW/GLFW_KEY_V
           :w GLFW/GLFW_KEY_W
           \w GLFW/GLFW_KEY_W
           \W GLFW/GLFW_KEY_W
           :x GLFW/GLFW_KEY_X
           \x GLFW/GLFW_KEY_X
           \X GLFW/GLFW_KEY_X
           :y GLFW/GLFW_KEY_Y
           \y GLFW/GLFW_KEY_Y
           \Y GLFW/GLFW_KEY_Y
           :z GLFW/GLFW_KEY_Z
           \z GLFW/GLFW_KEY_Z
           \Z GLFW/GLFW_KEY_Z
           :apostrophe GLFW/GLFW_KEY_APOSTROPHE
           \' GLFW/GLFW_KEY_APOSTROPHE
           :backslash GLFW/GLFW_KEY_BACKSLASH
           \\ GLFW/GLFW_KEY_BACKSLASH
           :backspace GLFW/GLFW_KEY_BACKSPACE
           \backspace GLFW/GLFW_KEY_BACKSPACE
           :caps-lock GLFW/GLFW_KEY_CAPS_LOCK
           :comma GLFW/GLFW_KEY_COMMA
           \, GLFW/GLFW_KEY_COMMA
           :delete GLFW/GLFW_KEY_DELETE
           :down GLFW/GLFW_KEY_DOWN
           :end GLFW/GLFW_KEY_END
           :enter GLFW/GLFW_KEY_ENTER
           \return GLFW/GLFW_KEY_ENTER
           :equal GLFW/GLFW_KEY_EQUAL
           \= GLFW/GLFW_KEY_EQUAL
           :f1 GLFW/GLFW_KEY_F1
           :f2 GLFW/GLFW_KEY_F2
           :f3 GLFW/GLFW_KEY_F3
           :f4 GLFW/GLFW_KEY_F4
           :f5 GLFW/GLFW_KEY_F5
           :f6 GLFW/GLFW_KEY_F6
           :f7 GLFW/GLFW_KEY_F7
           :f8 GLFW/GLFW_KEY_F8
           :f9 GLFW/GLFW_KEY_F9
           :f10 GLFW/GLFW_KEY_F10
           :f11 GLFW/GLFW_KEY_F11
           :f12 GLFW/GLFW_KEY_F12
           :f13 GLFW/GLFW_KEY_F13
           :f14 GLFW/GLFW_KEY_F14
           :f15 GLFW/GLFW_KEY_F15
           :f16 GLFW/GLFW_KEY_F16
           :f17 GLFW/GLFW_KEY_F17
           :f18 GLFW/GLFW_KEY_F18
           :f19 GLFW/GLFW_KEY_F19
           :f20 GLFW/GLFW_KEY_F20
           :f21 GLFW/GLFW_KEY_F21
           :f22 GLFW/GLFW_KEY_F22
           :f23 GLFW/GLFW_KEY_F23
           :f24 GLFW/GLFW_KEY_F24
           :f25 GLFW/GLFW_KEY_F25
           :grave-accent GLFW/GLFW_KEY_GRAVE_ACCENT
           :home GLFW/GLFW_KEY_HOME
           :insert GLFW/GLFW_KEY_INSERT
           :kp-0 GLFW/GLFW_KEY_KP_0
           :kp-1 GLFW/GLFW_KEY_KP_1
           :kp-2 GLFW/GLFW_KEY_KP_2
           :kp-3 GLFW/GLFW_KEY_KP_3
           :kp-4 GLFW/GLFW_KEY_KP_4
           :kp-5 GLFW/GLFW_KEY_KP_5
           :kp-6 GLFW/GLFW_KEY_KP_6
           :kp-7 GLFW/GLFW_KEY_KP_7
           :kp-8 GLFW/GLFW_KEY_KP_8
           :kp-9 GLFW/GLFW_KEY_KP_9
           :kp-add GLFW/GLFW_KEY_KP_ADD
           :kp-decimal GLFW/GLFW_KEY_KP_DECIMAL
           :kp-divide GLFW/GLFW_KEY_KP_DIVIDE
           :kp-enter GLFW/GLFW_KEY_KP_ENTER
           :kp-equal GLFW/GLFW_KEY_KP_EQUAL
           :kp-multiply GLFW/GLFW_KEY_KP_MULTIPLY
           :kp-subtract GLFW/GLFW_KEY_KP_SUBTRACT
           :last GLFW/GLFW_KEY_LAST
           :left GLFW/GLFW_KEY_LEFT
           :left-alt GLFW/GLFW_KEY_LEFT_ALT
           :left-bracket GLFW/GLFW_KEY_LEFT_BRACKET
           \[ GLFW/GLFW_KEY_LEFT_BRACKET
           :left-ctrl GLFW/GLFW_KEY_LEFT_CONTROL
           :left-shift GLFW/GLFW_KEY_LEFT_SHIFT
           :left-super GLFW/GLFW_KEY_LEFT_SUPER
           :menu GLFW/GLFW_KEY_MENU
           :minus GLFW/GLFW_KEY_MINUS
           \- GLFW/GLFW_KEY_MINUS
           :num-lock GLFW/GLFW_KEY_NUM_LOCK
           :page-down GLFW/GLFW_KEY_PAGE_DOWN
           :page-up GLFW/GLFW_KEY_PAGE_UP
           :pause GLFW/GLFW_KEY_PAUSE
           :period GLFW/GLFW_KEY_PERIOD
           \. GLFW/GLFW_KEY_PERIOD
           :print-screen GLFW/GLFW_KEY_PRINT_SCREEN
           :right GLFW/GLFW_KEY_RIGHT
           :right-alt GLFW/GLFW_KEY_RIGHT_ALT
           :right-bracket GLFW/GLFW_KEY_RIGHT_BRACKET
           \] GLFW/GLFW_KEY_RIGHT_BRACKET
           :right-ctrl GLFW/GLFW_KEY_RIGHT_CONTROL
           :right-shift GLFW/GLFW_KEY_RIGHT_SHIFT
           :right-super GLFW/GLFW_KEY_RIGHT_SUPER
           :scroll-lock GLFW/GLFW_KEY_SCROLL_LOCK
           :semicolon GLFW/GLFW_KEY_SEMICOLON
           \; GLFW/GLFW_KEY_SEMICOLON
           :slash GLFW/GLFW_KEY_SLASH
           \/ GLFW/GLFW_KEY_SLASH
           :space GLFW/GLFW_KEY_SPACE
           \space GLFW/GLFW_KEY_SPACE
           :tab GLFW/GLFW_KEY_TAB
           \tab GLFW/GLFW_KEY_TAB
           ;; not legal in the one function that actually calls this
           ;; Really just included for the sake of completeness
           :unknown GLFW/GLFW_KEY_UNKNOWN
           :up GLFW/GLFW_KEY_UP
           :world-1 GLFW/GLFW_KEY_WORLD_1
           :world-2 GLFW/GLFW_KEY_WORLD_2}]
    (get m key)))
