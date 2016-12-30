(ns clj-aws-static-website.boot
  (:require [boot.core :as boot]
            [clj-aws-static-website.stack :as stack]
            [clj-aws-static-website.contents :as contents]))

(boot/deftask create
  "Create aws stack for s3/cloudfront website"
  [n name   STR str "Name of stack"
   d domain STR str "Create stack for domain"]
  (boot/with-pre-wrap fileset
    (println (stack/create! name domain))
    fileset))

(boot/deftask push
  "Push website contents to s3"
  [d domain STR str "Create stack for domain"
   p path   STR str "Path of files to deploy"]

  (boot/with-pre-wrap fileset
    (println (contents/push! name path))
    fileset))
