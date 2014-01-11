(ns user
  (:require [clojure.java.io :as io]
            [clojure.inspector :as i]
            [clojure.string :as str]
            [clojure.pprint :refer (pprint)]
            [clojure.repl :refer :all]
            [clojure.test :as test]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [penumbra.app :as app]))

(def system nil)

(defn init
  []
  {})

(defn start
  []
  {})

(defn stop []
  {})

(defn go
  []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'user/go))
