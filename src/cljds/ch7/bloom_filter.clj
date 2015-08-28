(ns cljds.ch7.bloom-filter
  (:import [com.google.common.hash Hashing]))

(defn hash-function [m seed]
  (fn [x]
    (-> (Hashing/murmur3_32 seed)
        (.hashUnencodedChars x)
        (.asInt)
        (mod m))))

(defn hash-functions [m k]
  (map (partial hash-function m) (range k)))

(defn indices-fn [m k]
  (let [f (apply juxt (hash-functions m k))]
    (fn [x]
      (f x))))

(defn bloom-filter [m k]
  {:filter     (vec (repeat m false))
   :indices-fn (indices-fn m k)})

(defn set-bit [seq index]
  (assoc seq index true))

(defn set-bits [seq indices]
  (reduce set-bit seq indices))

(defn bloom-assoc [{:keys [indices-fn] :as bloom} element]
  (update-in bloom [:filter] set-bits (indices-fn element)))

(defn bloom-contains? [{:keys [filter indices-fn]} element]
  (->> (indices-fn element)
       (map filter)
       (every? true?)))
