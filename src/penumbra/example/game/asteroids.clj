;;   Copyright (c) 2012 Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.example.game.asteroids
  (:use [penumbra opengl]
        [penumbra.utils :only (separate)]
        [cantor])
  (:require [com.stuartsierra.component :as component]
            [penumbra.app :as app]
            [penumbra.text :as text]
            [penumbra.time :as time]
            [penumbra.data :as data]
            [schema.core :as s])
  (:import [clojure.lang Atom]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def asteroid-state {:expired? (s/=> s/Bool)
                     :position s/Any
                     :radius double
                     :render (s/=> s/Any)})

(def particle-state {:expired? s/Bool
                     :position s/Any  ; actually a vec2
                     :radius double
                     :render (s/=> s/Any)})

(def ship-state  {:birth s/Any
                  :position s/Any
                  :radius double
                  :theta s/Int
                  :velocity s/Any})

(def world-state {:asteroids [asteroid-state]
                  :bullets [particle-state]
                  :keys Atom  ; really a map of which keys are pressed
                  :spaceship ship-state
                  :particles [particle-state]})

(declare initialize)
(defrecord Asteroids [dim callbacks world-state]
  component/Lifecycle
  (start [this]
    (assoc this :world-state (initialize)))
  (stop [this]
    (assoc this :world-state {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internals

(comment (def ^:dynamic *dim* (vec2 10 10)))

(s/defn wrap
  "Makes the position wrap around from right to left, bottom to top"
  [component :- Asteroids
   pos]
  (let [dim (:dim component)]
    (sub
     (map*
      mod
      (add pos dim)
      (mul dim 2))
     dim)))

(s/defn expired? :- s/Bool [x]
  ((:expired? x)))

(defn render [x]
  ((:render x)))

(s/defn radius [x] :- double
  (if (number? (:radius x))
    (:radius x)
    ((:radius x))))

(defn position [x]
  (if (cartesian? (:position x))
    (:position x)
    ((:position x))))

(defn intersects? [a b]
  (let [min-dist (+ (radius a) (radius b))
        dist (sub (position a) (position b))]
    (>= (* min-dist min-dist) (length-squared dist))))

(defn rand-color [a b]
  (map #(+ %1 (rand (- %2 %1))) a b))


;; asteroids

(defn sphere-vertices
  [lod]
  (for [theta (range 0 361 (/ 360 lod))]
    (for [phi (range -90 91 (/ 180 (/ lod 2)))]
      (cartesian (polar3 theta phi)))))

(defn rand-vector []
  (normalize (cartesian (polar3 (rand 360) (rand 360)))))

(defn offset-vertex
  "Expand if on one side of a plane, contract if on the other"
  [v vertex]
  (if (neg? (dot v (normalize vertex)))
    (mul vertex 1.01)
    (mul vertex 0.99)))

(defn offset-sphere [v vertices]
  (map (fn [arc] (map #(offset-vertex v %) arc)) vertices))

(defn gen-asteroid-vertices
  "Procedurally generate perturbed sphere"
  [lod iterations]
  (let [s (iterate #(offset-sphere (rand-vector) %) (sphere-vertices lod))]
    (nth s iterations)))

(defn draw-asteroid [vertices]
  (doseq [arcs (partition 2 1 vertices)]
    (draw-quad-strip
     (doseq [[a b] (map list (first arcs) (second arcs))]
       (vertex a) (vertex b)))))

(defn gen-asteroid-geometry [lod iterations]
  (create-display-list (draw-asteroid (gen-asteroid-vertices lod iterations))))

(defn init-asteroids []
  (def asteroid-meshes (doall (take 20 (repeatedly #(gen-asteroid-geometry 12 100))))))

(s/defn gen-asteroid :- asteroid-state
  [& args]
  (let [params (merge
                {:position (vec2 0 0)
                 :radius 1
                 :theta (rand 360)
                 :speed 1}
                (apply hash-map args))
        birth (app/now)
        elapsed #(- (app/now) birth)
        asteroid (nth asteroid-meshes (rand-int 20))
        velocity (mul (cartesian (:theta params)) (:speed params))
        position #(wrap (add (:position params) (mul velocity (elapsed))))]
    {:expired? #(< (:radius params) 0.25)
     :position position
     :radius (:radius params)
     :render #(push-matrix
                (translate (position))
                (rotate (* (:speed params) (elapsed) -50) 1 0 0)
                (rotate (:theta params) 0 1 0)
                (apply scale (->> params :radius repeat (take 3)))
                (color 0.6 0.6 0.6)
                (call-display-list asteroid))}))

;;; particles

(defn textured-quad []
  (draw-quads
   (texture 0 0) (vertex -1 -1)
   (texture 1 0) (vertex 1 -1)
   (texture 1 1) (vertex 1 1)
   (texture 0 1) (vertex -1 1)))

(defn init-particles []
  ;; TODO: This seems like it should really be
  ;; declared at the top level, since that's where
  ;; it actually happens.
  ;; It probably depends on where/when this actually gets
  ;; called.
  (def particle-tex
       (let [[w h] [32 32]
             dim (vec2 w h)
             tex (create-byte-texture w h)]
      (data/overwrite!
       tex
       (apply concat
              (for [x (range w) y (range h)]
                (let [pos (div (vec2 x y) dim)
                      i (Math/exp (* 16 (- (length-squared (sub pos (vec2 0.5 0.5))))))]
                  [1 1 1 i]))))
      tex))
  (def particle-quad
    (create-display-list (textured-quad))))

(defn draw-particle [position radius tint]
  (push-matrix
    (apply color tint)
    (translate position)
    (scale radius radius)
    (call-display-list particle-quad)))

(s/defn gen-particle :- particle-state
  [& args]
  (let [params (merge
                {:position (vec2 0 0)
                 :theta (rand 360)
                 :speed 1
                 :radius (+ 0.25 (rand 0.5))
                 :color [1 1 1]
                 :lifespan 1}
                (apply hash-map args))
        birth (app/now)
        velocity (mul (cartesian (polar2 (:theta params))) (:speed params))
        elapsed #(- (app/now) birth)
        position #(wrap (add (:position params) (mul velocity (elapsed))))]
    {:expired? #(> (- (app/now) birth) (:lifespan params))
     :position position
     :radius (:radius params)
     :render #(draw-particle
               (position)
               (:radius params)
               (concat (:color params) [(max 0 (- 1 (Math/pow (/ (elapsed) (:lifespan params)) 3)))]))}))

;;; spaceship

(defn draw-fuselage [] ;;should be hung in the Louvre
  (color 1 1 1)
  (draw-triangles
   (vertex -0.4 -0.5) (vertex 0 -0.4) (vertex 0 0.5)
   (vertex 0.4 -0.5) (vertex 0 -0.4) (vertex 0 0.5)))

(defn init-spaceship []
  ;; TODO: Here's another global to eliminate
  (def fuselage (create-display-list (draw-fuselage))))

(defn fire-bullet [state]
  (let [ship (:spaceship state)]
    (assoc state
      :bullets
      (conj
       (:bullets state)
       (gen-particle
        :position (:position ship)
        :theta (:theta ship)
        :speed 15
        :radius 0.25
        :color [0 0 1]
        :lifespan 2)))))

(s/defn key-pressed? :- s/Bool
  [key-press-state :- {s/Keyword s/Bool}
   which :- s/Keyword]
  (which key-press-state))

(s/defn emit-flame
  [state :- world-state]
  (when (key-pressed? @(:keys state) :up)
    (let [ship (:spaceship state)
          offset (- (rand 30) 15)
          theta (+ 180 (:theta ship) offset)
          particles (:particles state)
          position (add (:position ship) (cartesian (polar2 theta 0.3)))
          ;; TODO: Typehint this to get rid of the reflection warnings below
          ;; Then again, I should probably look into what polar is actually
          ;; returning
          velocity (polar (add (:velocity ship) (cartesian theta)))]
      (assoc state
        :particles (conj particles
                         (gen-particle
                          :position position
                          :theta (.theta velocity)
                          :speed (.r velocity)
                          :radius 0.25
                          :color (rand-color [1 0.5 0.7] [1 1 1])
                          :lifespan (/ (Math/cos (radians (* 3 offset))) 2.5)))))))

(s/defn update-spaceship :- ship-state
  [dt :- double
   state :- world-state]
  (let [ship (:spaceship state)
        key-state @(:keys state)
        p     (:position ship)
        v     (:velocity ship)
        theta (:theta ship)
        theta (condp (fn [x _] (key-pressed? key-state x)) nil
                :left  (rem (+ theta (* 360 dt)) 360)
                :right (rem (- theta (* 360 dt)) 360)
                theta)
        a     (if (key-pressed? key-state :up)
                  (mul (cartesian theta) 3)
                  (vec2 0 0))
        v     (add v (mul a dt))
        p     (wrap (add p (mul v dt)))]
    (assoc ship
      :theta theta
      :position p
      :velocity v)))

(s/defn draw-spaceship [ship :- ship-state]
  (push-matrix
    (translate (:position ship))
    (rotate (- (:theta ship) 90) 0 0 1)
    (call-display-list fuselage)))

(s/defn gen-spaceship :- ship-state []
  {:birth (app/now)
   :position (vec2 0 0)
   :radius 0.5
   :velocity (vec2 0 0)
   :theta 0})

;;; game state

(s/defn reset :- world-state
  "Reset to initial game state."
  [state :- world-state]
  (assoc state
    :spaceship (gen-spaceship)
    :asteroids (take 4 (repeatedly
                        #(let [theta (rand 360)
                               pos (cartesian (polar2 theta 2))]
                           (gen-asteroid :position pos :theta theta :speed (rand)))))))

(s/defn split-asteroid :- [asteroid-state]
  "Turn asteroid into four sub-asteroids."
  [asteroid :- asteroid-state]
  (let [r (radius asteroid)]
    (when (< 0.25 r)
      (take 4
            (repeatedly
             #(gen-asteroid
               :position (position asteroid)
               :radius (/ r 2)
               :speed (/ 1.5 r)))))))

(s/defn gen-explosion :- [particle-state]
  "Create particles within a given color range."
  [num :- s/Int
   object :- {:position s/Any
              s/Any s/Any}
   [lo-color hi-color]
   speed]
  (take num
    (repeatedly
     #(gen-particle
       :position (position object)
       :speed (rand speed)
       :color (rand-color lo-color hi-color)
       :lifespan 2))))

(s/defn explode-asteroids :- world-state
  "Turn asteroid into sub-asteroids and explosion particles."
  [asteroids :- [asteroid-state]
   state :- world-state]
  (assoc state
    :asteroids (concat
                 (:asteroids state)
                 (mapcat split-asteroid asteroids))
    :particles (concat
                 (:particles state)
                 (mapcat #(gen-explosion (* (radius %) 100) % [[1 0.5 0] [1 1 0.2]] 2) asteroids))))

(s/defn check-complete :- world-state
  "Are all the asteroids gone?"
  [state :- world-state]
  (if (zero? (count (:asteroids state)))
    (reset state)
    state))

(s/defn check-ship :- world-state
  "Has the ship collided with any asteroids?"
  [state :- world-state]
  (let [ship (:spaceship state)
        asteroids (:asteroids state)
        [hit missed] (separate #(intersects? ship %) asteroids)]
    (if (some #(intersects? ship %) asteroids)
      (assoc state
        :asteroids (concat missed (mapcat split-asteroid hit))
        :spaceship (gen-spaceship)
        :particles (concat (:particles state) (gen-explosion 300 ship [[0 0 0.6] [0.5 0.5 1]] 7)))
      state)))

(s/defn check-asteroids :- world-state
  "Have the asteroids collided with any bullets?"
  [state :- world-state]
  (let [bullets (:bullets state)
        asteroids (:asteroids state)
        collisions (for [a asteroids, b bullets :when (intersects? a b)] [a b])
        [hit missed] (separate (set (map first collisions)) asteroids)
        bullets (remove (set (map second collisions)) bullets)
        particles (:particles state)]
    (explode-asteroids
     hit
     (assoc state
       :particles (remove expired? particles)
       :bullets (remove expired? bullets)
       :asteroids missed))))

(s/defn update-collisions :- world-state [state :- world-state]
  (-> state check-asteroids check-ship check-complete))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; game loop

(s/defn init :- world-state
  [state :- world-state]
  (app/title! "Asteroids")
  (comment (app/vsync! true))
  (comment (app/key-repeat! false))
  (init-asteroids)
  (init-particles)
  (init-spaceship)
  (enable :blend)
  (blend-func :src-alpha :one-minus-src-alpha)
  (app/periodic-update! 25 update-collisions)
  (app/periodic-update! 50 emit-flame)
  (reset state))

(defn reshape [[x y w h] state]
  (let [dim (vec2 (* (/ w h) 10) 10)]
    (frustum-view 45 (/ w h) 0.1 100)
    (load-identity)
    (translate 0 0 (- (* 2.165 (.y dim))))
    (assoc state
      :dim dim)))

(defn key-press [key state]
  (cond
   (= key " ") (fire-bullet state)
   (= key :escape) (app/pause!)
   (= key "1") (app/speed! 0.5)
   (= key "2") (app/speed! 1)
   (= key "3") (app/speed! 2)
   :else state))

(defn update-frame [[dt time] state]
  (assoc state
         :spaceship (update-spaceship dt state)))

(defn display [[dt time] state]
  (text/write-to-screen (str (int (/ 1 dt)) " fps") 0 0)
  (with-enabled :texture-2d
    (with-texture particle-tex
      (doseq [p (concat (:particles state) (:bullets state))]
        (render p))))
  (with-disabled :texture-2d
    (with-render-mode :wireframe
      (doseq [a (:asteroids state)]
        (render a)))
    (draw-spaceship (:spaceship state)))
  (app/repaint!))

(defn start
  []
  (let [component (map->Asteroids {:callbacks {:reshape reshape,
                                               :init init,
                                               :key-press key-press,
                                               :update update-frame,
                                               :display display}
                                   :dim (vec2 10 10)})]))
