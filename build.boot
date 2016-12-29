(set-env! :dependencies '[[boot-bundle "0.1.0-SNAPSHOT" :scope "test"]])
(require '[boot-bundle :refer [expand-keywords]])
(reset! boot-bundle/bundle-file-path "./boot.bundle.edn")

(set-env! :resource-paths #{"src"}
          :dependencies (expand-keywords [:clojure-1.8
                                          :bootlaces
                                          :aws :condensation :pathetic :raynes-fs]))

(require '[adzerk.bootlaces :refer :all])

(def +version+ "0.1.0-SNAPSHOT")

(bootlaces! +version+)

(task-options!
  pom {:project     'com.tristanstraub/clj-aws-static-website
       :version     +version+
       :description "Cloudformation config and s3 upload for static website"
       :url         "https://github.com/tristanstraub/clj-aws-static-website"
       :scm         {:url "https://github.com/tristanstraub/clj-aws-static-website"}
       :license     {"MIT" "https://opensource.org/licenses/MIT"}})

(deftask build []
  (comp (pom)
        (jar)
        (install)))

(deftask release []
  (comp (build-jar)
        (push-snapshot)))
