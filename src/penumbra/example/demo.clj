(ns penumbra.example.demo
  (:gen-class))

(defn -main [& args]
  (use (symbol (first args))))

