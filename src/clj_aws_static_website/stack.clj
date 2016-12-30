(ns clj-aws-static-website.stack
  (:require [amazonica.aws.s3 :as s3]
            [amazonica.core :as az]
            [clojure.string :as str]
            [com.comoyo.condensation
             [stack :as cfs]
             [template :as cft]]
            [clojure.core.strint :refer [<<]]))

(defmacro let-resources [bindings & body]
  `(let [~@(apply concat
                  (for [[name definition] (partition 2 bindings)]
                    `[~name (cft/make-resource '~name (fn [] ~definition))]))]
     ~@body))

(defmacro let-outputs [bindings & body]
  `(let [~@(apply concat
                  (for [[nm definition] (partition 2 bindings)]
                    `[~nm
                      (cft/make-output '~nm
                                       ~(name nm) ;; description
                                       ~definition)]))]
     ~@body))

(defn template [domain]
  (let [www-domain (str "www." domain)]
    (let-resources [bucket-www-domain             {"Type" "AWS::S3::Bucket"
                                                   "Properties" {"BucketName"           www-domain
                                                                 "AccessControl"        "PublicRead"
                                                                 "WebsiteConfiguration" {"IndexDocument" "index.html"
                                                                                         "ErrorDocument" "404.html"}}}

                    bucket-domain                 {"Type"       "AWS::S3::Bucket"
                                                   "Properties" (merge {"BucketName" domain}
                                                                       {"WebsiteConfiguration" {"RedirectAllRequestsTo" {"HostName" (cft/ref bucket-www-domain)
                                                                                                                         "Protocol" "https"}}})}

                    bucket-logs-domain            {"Type" "AWS::S3::Bucket"
                                                   "Properties" {"BucketName" (str "logs." domain)}}

                    permissions-bucket-domain     {"Type"       "AWS::S3::BucketPolicy"
                                                   "Properties" {"Bucket"         (cft/ref bucket-domain)
                                                                 "PolicyDocument" {"Version" "2012-10-17"
                                                                                   "Statement" [{"Effect" "Allow"
                                                                                                 "Principal" "*"
                                                                                                 "Action" ["s3:GetObject"]
                                                                                                 "Resource" (<< "arn:aws:s3:::~{domain}/*")}]}}}

                    permissions-bucket-www-domain {"Type"       "AWS::S3::BucketPolicy"
                                                   "Properties" {"Bucket"         (cft/ref bucket-www-domain)
                                                                 "PolicyDocument" {"Version" "2012-10-17"
                                                                                   "Statement" [{"Effect"    "Allow"
                                                                                                 "Principal" "*"
                                                                                                 "Action"    ["s3:GetObject"]
                                                                                                 "Resource"  (<< "arn:aws:s3:::~{www-domain}/*")}]}}}

                    hosted-zone-domain            {"Type"       "AWS::Route53::HostedZone"
                                                   "Properties" {"Name" domain}}

                    cloudfront-www-domain         {"Type"       "AWS::CloudFront::Distribution"
                                                   "Properties" {"DistributionConfig" {"Origins"              [{"DomainName" {"Fn::Join" [""
                                                                                                                                          [www-domain
                                                                                                                                           ".s3-website-"
                                                                                                                                           {"Ref" "AWS::Region"}
                                                                                                                                           ".amazonaws.com"]]}
                                                                                                                "Id" "myS3Origin"
                                                                                                                "CustomOriginConfig" {"HTTPPort"             "80"
                                                                                                                                      "HTTPSPort"            "443"
                                                                                                                                      "OriginProtocolPolicy" "match-viewer"}}]
                                                                                       "Enabled"              "true"
                                                                                       "DefaultRootObject"    "index.html"
                                                                                       "Logging"              {"IncludeCookies" "false"
                                                                                                               "Bucket"         (cft/get-att bucket-logs-domain "DomainName")
                                                                                                               "Prefix"         (str (clojure.string/replace domain "." "-") "-")}
                                                                                       "Aliases"              [www-domain domain]
                                                                                       "DefaultCacheBehavior" {"AllowedMethods" [ "DELETE" "GET" "HEAD" "OPTIONS" "PATCH" "POST" "PUT" ]
                                                                                                               "TargetOriginId" "myS3Origin"
                                                                                                               "ForwardedValues" {"QueryString" "false"
                                                                                                                                  "Cookies"     {"Forward" "none"}}
                                                                                                               "ViewerProtocolPolicy" "allow-all"}
                                                                                       "PriceClass"           "PriceClass_All"}}}

                    record-set-domain             {"Type"       "AWS::Route53::RecordSetGroup"
                                                   "Properties" {"HostedZoneName" (str domain ".")
                                                                 "RecordSets"     [{"Name"        (str domain ".")
                                                                                    "Type"        "A"
                                                                                    "AliasTarget" {"DNSName"      (cft/get-att cloudfront-www-domain "DomainName")
                                                                                                   "HostedZoneId" "Z2FDTNDATAQYW2" ;; must always be this ID for cloudfront
                                                                                                   }}
                                                                                   {"Name"            (str www-domain ".")
                                                                                    "Type"            "CNAME"
                                                                                    "TTL"             "900"
                                                                                    "ResourceRecords" [{"Fn::Join" ["" [(cft/get-att cloudfront-www-domain "DomainName") "."]]}]}]}}]

      (let-outputs [output-domain                 (cft/ref bucket-domain)
                    output-www-domain             (cft/ref bucket-www-domain)
                    output-logs-domain            (cft/ref bucket-logs-domain)
                    output-url                    (cft/get-att bucket-www-domain "WebsiteURL")]

        (cft/template :description (<< "Static website for ~{domain} and ~{www-domain}")
                      :resources (cft/resources bucket-www-domain
                                                bucket-domain
                                                bucket-logs-domain
                                                permissions-bucket-domain
                                                permissions-bucket-www-domain
                                                hosted-zone-domain
                                                record-set-domain
                                                cloudfront-www-domain)
                      :outputs (cft/outputs output-domain
                                            output-www-domain
                                            output-logs-domain
                                            output-url))))))

(defn create! [name domain & {:keys [region]}]
  {:pre [name domain]}
  (when region
    (az/defcredential {:endpoint region}))
  (cfs/create-or-update-stack :stack-name name :template (template domain)))

(defn destroy! [name & {:keys [region]}]
  {:pre [name]}
  (when region
    (az/defcredential {:endpoint region}))
  (cfs/delete-stack :stack-name name))
