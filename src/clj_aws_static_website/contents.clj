(ns clj-aws-static-website.contents
  (:require [amazonica.aws.s3 :as s3]
            [amazonica.core :as az]
            [pathetic.core :refer [relativize]]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn mime-type-of [path]
  (-> (str/split path #"[.]")
      last
      {"css"  "text/css"
       "html" "text/html"
       "js"   "text/javascript"
       "jpg"  "application/jpg"
       "png"  "application/png"
       "edn"  "text/plain"
       "cljs" "text/plain"
       "cljc" "text/plain"
       "map"  "application/json"}))

(defn traverse-website [website]
  (->> (file-seq (io/file website))
       (filter #(.isFile %))
       (map #(relativize (io/file website) %))
       (map (fn [path] {:path path
                        :type (mime-type-of path)}))))

(defn push!
  ([domain public-path]
   {:pre [domain public-path]}
   (let [www-domain (str "www." domain)]
     (->> (traverse-website public-path)
          (pmap (fn [{:keys [path type]}]
                  (println "pushing" path)
                  (let [public-file (io/file public-path path)]
                    (when (fs/exists? public-file)
                      (s3/put-object :bucket-name www-domain
                                     :key path
                                     :metadata {:content-type type
                                                :content-length (fs/size public-file)}
                                     :input-stream (io/input-stream public-file))))))
          doall))))
