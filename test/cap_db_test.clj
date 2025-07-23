(ns cap-db-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [cap.db :as db]))

;; ============================================================================
;; LEVEL 1: PURE FUNCTION TESTS (Query Construction)
;; Testing SQL generation functions without database
;; ============================================================================

(deftest add-capture-query-test
  (testing "basic capture insertion query"
    (let [query (db/add-capture-query "test-capture" "/home/user" "session-123" {})
          [sql & params] query]
      (is (vector? query))
      (is (= "INSERT INTO captures (name, directory, session, attributes) VALUES (?, ?, ?, ?)" sql))
      (is (= ["test-capture" "/home/user" "session-123" "{}"] params))))

  (testing "capture with complex attributes"
    (let [query (db/add-capture-query "complex" "/path" "sess" {:key "value" :num 42})
          [sql name directory session attributes] query]
      (is (= "complex" name))
      (is (= "{\"key\":\"value\",\"num\":42}" attributes)))))

(deftest add-line-query-test
  (testing "basic line insertion query"
    (let [query (db/add-line-query 123 "test log line")
          [sql & params] query]
      (is (vector? query))
      (is (= "INSERT INTO lines (capture_id, line) VALUES (?, ?)" sql))
      (is (= [123 "test log line"] params))))

  (testing "line with special characters"
    (let [query (db/add-line-query 456 "line with \"quotes\" and 'apostrophes'")
          [sql capture-id line] query]
      (is (= 456 capture-id))
      (is (= "line with \"quotes\" and 'apostrophes'" line)))))

(deftest find-captures-query-test
  (testing "query with no filters"
    (let [query (db/find-captures-query {} {})
          [sql & params] query]
      (is (vector? query))
      (is (= "SELECT * FROM captures AS c INNER JOIN (SELECT capture_id, COUNT(id) AS line_count FROM lines GROUP BY capture_id) AS l ON c.id = l.capture_id ORDER BY created_at DESC" sql))))

  (testing "query with name filter"
    (let [query (db/find-captures-query {:name "test-name"} {})
          [sql & params] query]
      (is (= "SELECT * FROM captures AS c INNER JOIN (SELECT capture_id, COUNT(id) AS line_count FROM lines GROUP BY capture_id) AS l ON c.id = l.capture_id WHERE name = ? ORDER BY created_at DESC" sql))
      (is (= ["test-name"] params))))

  (testing "query with directory filter"
    (let [query (db/find-captures-query {:directory "/home/user"} {})
          [sql & params] query]
      (is (= "SELECT * FROM captures AS c INNER JOIN (SELECT capture_id, COUNT(id) AS line_count FROM lines GROUP BY capture_id) AS l ON c.id = l.capture_id WHERE directory = ? ORDER BY created_at DESC" sql))
      (is (= ["/home/user"] params))))

  (testing "query with session filter"
    (let [query (db/find-captures-query {:session "session-123"} {})
          [sql & params] query]
      (is (= "SELECT * FROM captures AS c INNER JOIN (SELECT capture_id, COUNT(id) AS line_count FROM lines GROUP BY capture_id) AS l ON c.id = l.capture_id WHERE session = ? ORDER BY created_at DESC" sql))
      (is (= ["session-123"] params))))

  (testing "query with limit"
    (let [query (db/find-captures-query {} {:limit 10})
          [sql & params] query]
      (is (= "SELECT * FROM captures AS c INNER JOIN (SELECT capture_id, COUNT(id) AS line_count FROM lines GROUP BY capture_id) AS l ON c.id = l.capture_id ORDER BY created_at DESC LIMIT ?" sql))
      (is (= [10] params))))

  (testing "query with multiple filters and limit"
    (let [query (db/find-captures-query {:name "test" :directory "/path"} {:limit 5})
          [sql & params] query]
      (is (= "SELECT * FROM captures AS c INNER JOIN (SELECT capture_id, COUNT(id) AS line_count FROM lines GROUP BY capture_id) AS l ON c.id = l.capture_id WHERE (name = ?) AND (directory = ?) ORDER BY created_at DESC LIMIT ?" sql))
      (is (= ["test" "/path" 5] params)))))

(deftest get-capture-query-test
  (testing "basic get capture by id"
    (let [query (db/get-capture-query 42)
          [sql & params] query]
      (is (vector? query))
      (is (= "SELECT * FROM captures WHERE id = ?" sql))
      (is (= [42] params))))

  (testing "get capture with string id"
    (let [query (db/get-capture-query "123")
          [sql & params] query]
      (is (= ["123"] params)))))

(deftest set-comment-query-test
  (testing "basic comment update"
    (let [query (db/set-comment-query 42 "test comment")
          [sql & params] query]
      (is (vector? query))
      (is (= "UPDATE captures SET comment = ? WHERE id = ?" sql))
      (is (= ["test comment" 42] params))))

  (testing "comment with special characters"
    (let [query (db/set-comment-query 123 "comment with 'quotes' and newlines\n")
          [sql & params] query]
      (is (= ["comment with 'quotes' and newlines\n" 123] params)))))

(deftest find-lines-query-test
  (testing "basic lines query"
    (let [query (db/find-lines-query 42 {})
          [sql & params] query]
      (is (vector? query))
      (is (= "SELECT * FROM lines WHERE capture_id = ? ORDER BY id DESC" sql))
      (is (= [42] params))))

  (testing "lines query with limit"
    (let [query (db/find-lines-query 123 {:limit 50})
          [sql & params] query]
      (is (= "SELECT * FROM lines WHERE capture_id = ? ORDER BY id DESC LIMIT ?" sql))
      (is (= [123 50] params)))))

;; ============================================================================
;; SQL Structure Validation Tests
;; ============================================================================

(deftest query-structure-validation
  (testing "all query functions return proper structure"
    (let [queries [(db/add-capture-query "test" "/path" "sess" {})
                   (db/add-line-query 1 "line")
                   (db/find-captures-query {} {})
                   (db/get-capture-query 1)
                   (db/set-comment-query 1 "comment")
                   (db/find-lines-query 1 {})]]
      (doseq [query queries]
        (let [[sql & params] query]
          (is (vector? query) "Query should be a vector")
          (is (string? sql) "First element should be SQL string")
          (is (not-empty sql) "SQL string should not be empty"))))))

(deftest sql-injection-safety
  (testing "parameters are properly bound, not interpolated"
    ;; Test that potentially dangerous strings don't appear in SQL
    (let [dangerous-input "'; DROP TABLE captures; --"
          queries [(db/add-capture-query dangerous-input "/path" "sess" {})
                   (db/add-line-query 1 dangerous-input)
                   (db/find-captures-query {:name dangerous-input} {})
                   (db/set-comment-query 1 dangerous-input)]]
      (doseq [query queries]
        (let [[sql & params] query]
          (is (not (str/includes? sql "DROP TABLE"))
              "Dangerous SQL should not appear in generated query string"))))))
