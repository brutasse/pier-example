(ns com.example.pier-example-test
  (:require [clojure.test :refer :all]
            [com.example.pier-example :as lib]))

(deftest greet-test
  (is (= "hello world" (lib/greet "world"))))
