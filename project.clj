(defproject penumbra "0.6.0"
  :description "An idiomatic wrapper for OpenGL. Clojure 1.3 vec"
  :dependencies [[slick-util "1.0.0"]
                 [kephale/cantor "0.4.0-SNAPSHOT"]
                 [org.clojure/clojure "1.3.0"] 
		 [org.clojure/math.combinatorics "0.0.2"]
		 [org.clojars.charles-stain/lwjgl "3.0"]
		 [org.lwjgl/lwjgl-util "2.7.1"]
		 [org.clojars.charles-stain/jme3-lwjgl-natives "3.0"]]
  :java-source-path "java"
  :dev-dependencies [[swank-clojure "1.3.0"]]
  :checksum-deps false)