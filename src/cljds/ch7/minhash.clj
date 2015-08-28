(ns cljds.ch7.minhash
  (:require [clojure.set :refer [intersection union]])
  (:import [com.google.common.hash Hashing]))


;; Minhash

(defn hash-function [seed]
  (let [f (Hashing/murmur3_32 seed)]
    (fn [x]
      (-> (.hashUnencodedChars f (str x))
          (.asInt)))))

(defn hash-functions [k]
  (map hash-function (range k)))

(defn pairwise-min [a b]
  (map min a b))

(defn minhasher [k]
  (let [f (apply juxt (hash-functions k))]
    (fn [coll]
      (->> (map f coll)
           (reduce pairwise-min)))))

;; Locality Sensitive Hashing

(def lsh-hasher (hash-function 0))

(defn locality-sensitive-hash [r]
  {:r r :bands {}})

(defn buckets-for [r signature]
  (->> (partition-all r signature)
       (map lsh-hasher)
       (map-indexed vector)))

(defn lsh-assoc [{:keys [r] :as lsh} {:keys [id signature]}]
  (let [f (fn [lsh [band bucket]]
            (update-in lsh [:bands band bucket] conj id))]
    (->> (buckets-for r signature)
         (reduce f lsh))))

(defn lsh-candidates [{:keys [bands r]} signature]
  (->> (buckets-for r signature)
       (mapcat (fn [[band bucket]]
                 (get-in bands [band bucket])))
       (distinct)))
