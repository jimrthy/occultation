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
                 ;; TODO: At one point, Zach mentioned that he'd managed
                 ;; to eliminate all native dependencies. Is this an
                 ;; example of what he was talking about?
                 ;; (c.f. java/penumbra/Natives.java)
                 [kephale/lwjgl-natives "2.9.0"]
                 [kephale/lwjgl-util "2.9.0"]
                 [slingshot "0.10.3"]]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [org.clojure/java.classpath "0.2.1"]]
                   :injections [(require 'clojure.pprint)]}}
  :java-source-paths ["java"]
  ;; That work-around for native dependencies doesn't seem to
  ;; work these days. I do seem to need this
  :jvm-opts [~(str "-Djava.library.path=native/:"
                   (System/getProperty "java.library.path"))]
  :checksum-deps false)
