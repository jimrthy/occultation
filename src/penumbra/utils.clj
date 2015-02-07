(ns penumbra.utils
  (:require [clojure.core.async :as async]
            [schema.core :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def promise-class (class (promise)))

(def state {s/Any s/Any})

;; This feels klunky, but it's probably the 'right' way
(def async-channel (class (async/chan)))
(def channel-map {s/Keyword async-channel})

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

(indexed '(a b c d)) => ([0 a] [1 b] [2 c] [3 d])"
  [s]
  (map-indexed vector s))

(s/defn random-uuid :- s/Uuid
  "Silly wrapper function to avoid needing to look up the official version"
  []
  (java.util.UUID/randomUUID))

(defn separate
  "Returns a vector:
[ (filter f s), (filter (complement f) s) ]"
  [f s]
  ((juxt filter remove) f s))
