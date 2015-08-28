(ns cljds.ch7.sparkling
  (:gen-class)
  (:require [clojure.string :as str]
            [sparkling.conf :as conf]
            [sparkling.core :as spark]
            [sparkling.debug :as s-dbg]
            [sparkling.destructuring :as s-de]
            [sparkling.kryo :as k]
            [sparkling.scalaInterop :as scala])
  (:import [org.apache.spark.api.java JavaRDD]
           [org.apache.spark.mllib.linalg Vector SparseVector]
           [org.apache.spark.mllib.linalg.distributed RowMatrix]
           [org.apache.spark.mllib.recommendation ALS Rating]))

(defn parse-long [i]
  (Long/parseLong i))

(defn parse-double [i]
  (Double/parseDouble i))

(defn count-ratings [sc]
  (->> (spark/text-file sc "data/ml-100k/ua.base")
       (spark/count)))

(defn parse-line [line]
  (->> (str/split line #"\t")
       (map parse-long)))

(defn parse-rating [line]
  (let [[user item rating time] (->> (str/split line #"\t")
                                     (map parse-long))]
    (spark/tuple (mod time 10)
                 (Rating. user item rating))))

(defn parse-ratings [sc]
  (->> (spark/text-file sc "data/ml-100k/ua.base")
       (spark/map-to-pair parse-rating)))

(defn training-ratings [ratings]
  (->> ratings
       (spark/filter (fn [tuple]
                       (< (s-de/key tuple) 8)))
       (spark/values)))

(defn test-ratings [ratings]
  (->> ratings
       (spark/filter (s-de/first-value-fn
                      (fn [key] (>= key 8))))
       (spark/values)))

(defn user-product [rating]
  (spark/tuple (.user rating)
               (.product rating)))

(defn user-product-rating [rating]
  (spark/tuple (user-product rating)
               (.rating rating)))

(defn parse-movie [movie]
  (let [[mid mname] (str/split movie #"::")]
    [(parse-long mid)
     mname]))

(defn to-java-rdd [rdd]
  (JavaRDD/fromRDD rdd scala/OBJECT-CLASS-TAG))

(defn to-mllib-rdd [rdd]
  (.rdd rdd))

(defn from-mllib-rdd [rdd]
  (JavaRDD/fromRDD rdd scala/OBJECT-CLASS-TAG))

(defn predict [model data]
  (->> (spark/map-to-pair user-product data)
       (to-mllib-rdd)
       (.predict model)
       (from-mllib-rdd)
       (spark/map-to-pair user-product-rating)))

(defn squared-error [y-hat y]
  (Math/pow (- y-hat y) 2))

(defn sum-squared-errors [predictions actuals]
  (->> (spark/join predictions actuals)
       (spark/values)
       (spark/map (s-de/val-val-fn squared-error))
       (spark/reduce +)))

(defn rmse [model data]
  (let [predictions  (spark/cache (predict model data))
        actuals (->> (spark/map-to-pair user-product-rating
                                        data)
                     (spark/cache))]
    (-> (sum-squared-errors predictions actuals)
        (/ (spark/count data))
        (Math/sqrt))))

(defn compute [data validation-set]
  (for [rank [50 60 70 80 90 100 110 120 130 140 150]]
    (let [model (ALS/trainImplicit (.rdd  data) rank 20 0.01 8)]
      (println (str rank "\t" (rmse model validation-set))))))

(defn alternating-least-squares [data {:keys [rank num-iter
                                              lambda]}]
  (ALS/train (to-mllib-rdd data) rank num-iter lambda 10))

(defn to-sparse-vector [cardinality]
  (fn [ratings]
    (SparseVector. cardinality
                   (int-array (map #(.product %) ratings))
                   (double-array (map #(.rating %) ratings)))))

(defn to-matrix [rdd]
  (RowMatrix. (.rdd rdd)))

(defn ratings->matrix [data]
  (let [cardinality (-> data
                        (spark/map #(.product %))
                        (spark/reduce max)
                        (inc))]
    (->> data
         (spark/group-by #(.user %))
         (spark/values)
         (spark/map (to-sparse-vector cardinality))
         (to-matrix))))

(defn pca [matrix]
  (.computePrincipalComponents matrix 10))
