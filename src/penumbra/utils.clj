(ns penumbra.utils)

(defmacro defvar
  "Defines a var with an optional intializer and doc string"
  ([name]
     (list `def name))
  ([name init]
     (list `def name init))
  ([name init doc]
     (list `def (with-meta name (assoc (meta name) :doc doc)) init)))

(defmacro defmacro-
  "Same as defmacro but yields a private definition"
  [name & decls]
  (list* `defmacro (with-meta name (assoc (meta name) :private true)) decls))

(defmacro defvar-
  "Same as defvar but yields a private definition"
  [name & decls]
  (list* `defvar (with-meta name (assoc (meta name) :private true)) decls))

(defmacro- defnilsafe [docstring non-safe-name nil-safe-name]
  `(defmacro ~nil-safe-name ~docstring
     {:arglists '([~'x ~'form] [~'x ~'form ~'& ~'forms])}
     ([x# form#]
	`(let [~'i# ~x#] (when-not (nil? ~'i#) (~'~non-safe-name ~'i# ~form#))))
     ([x# form# & more#]
	`(~'~nil-safe-name (~'~nil-safe-name ~x# ~form#) ~@more#))))

(defnilsafe
  "Same as clojure.core/-> but returns nil as soon as the threaded value is nil itself (thus short-circuiting any pending computation).
Examples :
(-?> \"foo\" .toUpperCase (.substring 1)) returns \"OO\"
(-?> nil .toUpperCase (.substring 1)) returns nil
"
  -> -?>)

; defn-memo by Chouser:			;
(defmacro defn-memo
  "Just like defn, but memoizes the function using clojure.core/memoize"
  [fn-name & defn-stuff]
  `(do
     (defn ~fn-name ~@defn-stuff)
     (alter-var-root (var ~fn-name) memoize)
     (var ~fn-name)))

(defn separate
  "Returns a vector:
[ (filter f s), (filter (complement f) s) ]"
  [f s]
  [(filter f s) (filter (complement f) s)])

(defn indexed
  "Returns a lazy sequence of [index, item] pairs, where items come
from 's' and indexes count up from zero.

(indexed '(a b c d)) => ([0 a] [1 b] [2 c] [3 d])"
  [s]
  (map vector (iterate inc 0) s))