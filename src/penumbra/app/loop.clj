;;   Copyright (c) 2012 Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.app.loop
  (:require [penumbra.app.core :as app]
            [penumbra.app.controller :as controller]
            [penumbra.app.window :as window]
            [penumbra.opengl :as opengl]
            [penumbra.time :as time]))

;;;

(defn timed-fn
  "Creates a wrapper function which prepends any arguments with [dt t] in seconds."
  [clock f]
  (when f
    (let [previous (atom @clock)]
      (fn [& args]
        (let [now @clock]
          (try
            (apply f (list* [(- now @previous) now] args))
            (finally
              (reset! previous now))))))))

(comment (defn create-thread
  "Creates a thread. 'outer-fn' is passed 'inner-fn' as its only argument."
  [app outer-fn inner-fn]
  (Thread.
   #(with-app app
      (outer-fn inner-fn)))))

(comment (defn pauseable-loop
           [app outer-fn inner-fn]
           (with-app app
             (let [cntrlr (:controller app)]
               (try
                 (outer-fn
                  (fn []
                    (loop []
                      (controller/wait! cntrlr)
                      (try
                        (inner-fn)
                        (catch Exception e
                          (println "Unhandled exception '" e "' in pauseable loop")
                          (.printStackTrace e)
                          (controller/stop! cntrlr :exception)))
                      (when-not (controller/stopped? cntrlr)
                        (recur)))))
                 (catch Exception e
                   (.printStackTrace e)
                   (controller/stop! cntrlr :exception))
                 (finally
                   (println app "\nexiting!")))))))

(comment (defn pauseable-thread
           [app outer-fn inner-fn]
           (create-thread
            app
            #(%)
            #(pauseable-loop app outer-fn inner-fn))))

(defn basic-loop
  [controller outer-fn inner-fn]
  (comment (with-app app))
  (try
    (outer-fn
     (fn []
       (loop []
         (try
           (inner-fn)
           ;; TODO: Would probably make a lot of sense to also
           ;; catch RuntimeException instances. If only to
           ;; distinguish between the two.
           ;; And ExceptionInfo, of course
           (catch Exception e
             ;; This really shouldn't be a fatal error.
             ;; At this point, it's equivalent to a BSOD when,
             ;; at worst, it might be construed as a GPF.
             (println "Unhandled exception from basic inner loop")
             (.printStackTrace e)
             (println e)
             (println "Where's my stack trace?")
             (controller/stop! controller :exception)))
         ;; Seems interesting that pausing exits the loop
         (when-not (or (controller/paused? controller) (controller/stopped? controller))
           (recur)))))
    (catch Exception e
      (println "Unhandled exception from the outer loop:\n" (class e))
      (.printStackTrace e)
      (controller/stop! controller :exception))
    (finally
      ;; Not that anything is actually happening
      (println "Cleaning up the/a messaging loop"))))
