(ns penumbra.example.wiki.clock2
  (:require [com.stuartsierra.component :as component]
            [penumbra.app :as app]
            [penumbra.opengl :refer :all]
            [penumbra.system :as system]))

(defn draw-clock [hour minute second]
  (push-matrix
   (scale 0.5 0.5 1)
   (rotate 180 0 0 1)
   (push-matrix
    (rotate (* -30 (rem hour 24)) 0 0 1)
    (color 1 1 1)
    (line-width 5)
    (draw-lines (vertex 0 0) (vertex 0 -0.5)))
   (push-matrix
    (rotate (* -6 (rem minute 60)) 0 0 1)
    (color 1 1 1)
    (line-width 2)
    (draw-lines (vertex 0 0) (vertex 0 -1)))
   (push-matrix
    (rotate (* -6 (rem second 60)) 0 0 1)
    (color 1 0 0)
    (line-width 1)
    (draw-lines (vertex 0 0) (vertex 0 -1)))))

(defn init [state]
  (app/vsync! true)
  state)

(defn reshape [[x y w h] state]
  (let [aspect (/ (float w) h)
        height (if (> 1 aspect) (/ 1.0 aspect) 1)
        aspect (max 1 aspect)]
    (ortho-view (- aspect) aspect (- height) height -1 1)
    state))

(defn update- [[delta time] state]
  (assoc state
    :hour (+ (:hour state) (/ delta 3600))
    :minute (+ (:minute state) (/ delta 60))
    :second (+ (:second state) delta)))

(defn display [[delta time] state]
  (draw-clock (:hour state) (:minute state) (:second state))
  (app/repaint!))

;; This is convenient for being able to just run the namespace, but it's
;; breaking basic compilation.
;; With no real hint about the actual problem.
(comment) (let [system (system/init {:callbacks
                                     {:display display, :reshape reshape, :update update-, :init init}
                                     :state {:hour 0 :minute 0 :second 0}})
                (windowed-system (update-in system [:app :window])
                                 )]
            (component/start system))
