(ns cljds.ch7.data
  (:require [clojure.java.io :as io]
            [clojure.string :as s]))

(defn to-long [s]
  (Long/parseLong s))

(defn line->rating [line]
  (->> (s/split line #"\t")
       (map to-long)
       (zipmap [:user :item :rating])))

(defn load-ratings [file]
  (with-open [rdr (io/reader (io/resource file))]
    (->> (line-seq rdr)
         (map line->rating)
         (into []))))


(defn line->item-tuple [line]
  (let [[id name] (s/split line #"\|")]
    (vector (to-long id) name)))

(defn load-items [path]
  (with-open [rdr (io/reader (io/resource path))]
    (->> (line-seq rdr)
         (map line->item-tuple)
         (into {}))))

(defn item->name [file]
  (let [items (load-items file)]
    (fn [{:keys [id]}]
      (get items id))))
