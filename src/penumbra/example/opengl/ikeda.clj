;; http://www.bestinclass.dk/index.clj/2009/09/chaos-theory-vs-clojure.html
;; from https://gist.github.com/ztellman/193829

(ns penumbra.example.opengl.ikeda
  (:use [penumbra.opengl])
  (:require [penumbra.app :as app]))

(def max-iterations 100)
(def num-paths 50)
 
(defn ikeda [x y u]
  (iterate
    (fn [[x_n y_n]]
      (let [t_n (- 0.4 (/ 6 (+ 1 (* x_n x_n) (* y_n y_n))))]
        [(inc (* u (- (* x_n (Math/cos t_n))
                      (* y_n (Math/sin t_n)))))
              (* u (+ (* x_n (Math/sin t_n))
                      (* y_n (Math/cos t_n ))))]))
  [x y]))
 
(defn gen-u [u]
  (+ u (- 0.05 (float (/ (rand-int 100) 1000)))))
 
(defn gen-paths [u num]
  (let [u (gen-u u)]
    [u (map
         (fn [[x0 y0]] (ikeda x0 y0 u))
         (partition 2 (take (* 2 num) (repeatedly #(- (* 50 (rand)) 25)))))]))
 
(defn mouse-drag [[dx dy] _ button state]
  (assoc state
    :rot-x (+ (:rot-x state) dy)
    :rot-y (+ (:rot-y state) dx)))
 
(defn reshape [[x y width height] state]
  (frustum-view 60.0 (/ (double width) height) 1.0 100.0)
  (load-identity)
  (translate 0 0 -30)
  state)
 
(defn update [_ state]
  (let [i (:iteration state)]
    (if (> i max-iterations)
      (let [[u paths] (gen-paths (:u state) num-paths)]
        (assoc state
          :iteration 0, :paths paths, :u u))
      (assoc state
        :iteration (inc i)))))
 
(defn display [_ state]
  (let [i (:iteration state)]
    (rotate (:rot-x state) 1 0 0)
    (rotate (:rot-y state) 0 1 0)
    (doseq [s (:paths state)]
      (draw-line-strip
        (doseq [[x y] (take (inc i) s)]
          (vertex x y 0)))))
  (app/repaint!))
 
(defn start []
  (app/start
          {:reshape reshape :mouse-drag mouse-drag :update update :display display}
          {:rot-x 0 :rot-y 0 :iteration 0 :initial-paths (gen-paths 0.905 num-paths) :u 0.905}))
