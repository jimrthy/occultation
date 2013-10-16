(defproject jimrthy/penumbra "0.6.6-SNAPSHOT"
  :license "Eclipse Public License, just like the original."
  :description "An idiomatic wrapper for OpenGL."
  :dependencies [[slick-util "1.0.0"]
                 ;; FIXME: cantor is also deprecated. Is
                 ;; there a viable replacement yet?
                 [kephale/cantor "0.4.1"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/math.combinatorics "0.0.2"]
                 [kephale/lwjgl "2.9.0"]
                 [kephale/lwjgl-util "2.9.0"]
                 ;; TODO: At one point, Zach mentioned that he'd managed
                 ;; to eliminate all native dependencies. Is this an
                 ;; example of what he was talking about?
                 [kephale/lwjgl-natives "2.9.0"]
                 [slingshot "0.10.3"]]
  :profiles {:dev {:dependencies [[night-vision "0.1.0-SNAPSHOT"]]
             :injections [(require 'night-vision.goggles)
                          (require 'clojure.pprint)]}}
  :java-source-paths ["java"]
  :checksum-deps false)
