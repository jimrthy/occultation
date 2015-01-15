(defproject phronmophobic/penumbra "0.6.7-SNAPSHOT"
  :license "Eclipse Public License, just like the original."
  :description "An idiomatic wrapper for OpenGL."
  :dependencies [[com.frereth/lwjgl "3.0.0a1-SNAPSHOT"]
                 [com.stuartsierra/component "0.2.2"]
                 [kephale/cantor "0.4.1"]  ; "high-performance floating point math" -- deprecated
                 ;; TODO: At one point, Zach mentioned that he'd managed
                 ;; to eliminate all native dependencies. Is this an
                 ;; example of what he was talking about?
                 ;; (c.f. java/penumbra/Natives.java)
                 #_[kephale/lwjgl-natives "2.9.0"]
                 #_[kephale/lwjgl-util "2.9.0"]
                 [org.clojure/clojure "1.7.0-alpha4"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                 [org.clojure/math.combinatorics "0.0.2"]
                 [prismatic/schema "0.3.3"]
                 [slick-util "1.0.0"]]   ; for things like image and font loading. Status seems questionable
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [org.clojure/java.classpath "0.2.1"]]
                   :injections [(require 'clojure.pprint)]}}
  :java-source-paths ["java"]
  ;; That work-around for native dependencies doesn't seem to
  ;; work these days. I do seem to need this
  #_(comment :jvm-opts [~(str "-Djava.library.path=native/:"
                            (System/getProperty "java.library.path"))])
  :jvm-opts [~(str  "-Djava.library.path=/usr/local/lib:"
                   (System/getProperty "java.library.path"))]
  :checksum-deps false)

