(ns lib.time
  (:import
    (java.time ZoneId
               Instant)
    (java.time.format DateTimeFormatter)))

(def default-zone "America/Los_Angeles")

(defn zone-id
  ([]
   (zone-id default-zone))
  ([zone]
   (ZoneId/of zone)))

(defn now-millis
  []
  (.toEpochMilli (Instant/now)))

(defn millis->datetime
  [millis zone]
  (let [inst (Instant/ofEpochMilli millis)]
    (.atZone inst (zone-id zone))))

(defn find-format
  [spec]
  (case spec
    "instant" DateTimeFormatter/ISO_INSTANT
    "date" DateTimeFormatter/ISO_LOCAL_DATE
    "datetime" DateTimeFormatter/ISO_OFFSET_DATE_TIME
    (java.time.format.DateTimeFormatter/ofPattern spec)))

(defn format-datetime
  [datetime fmt]
  (.format datetime (find-format fmt)))


#_(-> (millis->datetime 1584806479743 "America/Los_Angeles")
      (format-datetime "instant"))
