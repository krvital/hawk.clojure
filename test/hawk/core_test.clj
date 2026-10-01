(ns hawk.core-test
  (:require [hawk.core :as sut]
            [clojure.test :refer [deftest is testing] :as t]))

(defn- make-exception []
  (ex-info "Clojure example exception" {:some {:data :here}}))

(defn- make-java-exception []
  (Exception. "Java example exception"))

(deftest make-endpoint-test
  (is (= (sut/make-endpoint "eyJpbnRlZ3JhdGlvbklkIjoiZTRjYzlmYmUtNzM2MC00NjQzLTg1NTEtMDNhMDcxYmFmY2UxIiwic2VjcmV0IjoiMzRlMTNiN2ItZTgzMi00MzAyLTg0YjAtMGFlM2Q0NTc5ZjQyIn0=")
         "https://e4cc9fbe73604643855103a071bafce134e13b7be832430284b00ae3d4579f42@k1.hawk.so")))

(deftest cut-snippet-test
  (testing "base"
    (is (= (sut/cut-snippet 5 (range 20))
           '(1 2 3 4 5 6 7))))

  (testing "lower bound"
    (is (= (sut/cut-snippet 4 (range 20))
           '(0 1 2 3 4 5 6)))

    (is (= (sut/cut-snippet 1 (range 20))
           '(0 1 2 3 4 5 6))))

  (testing "higher bound"
    (is (= (sut/cut-snippet 17 (range 20))
           '(13 14 15 16 17 18 19)))

    (is (= (sut/cut-snippet 20 (range 20))
           '(13 14 15 16 17 18 19))))

  (testing "small range"
    (is (= (sut/cut-snippet 5 (range 0))
           '()))

    (is (= (sut/cut-snippet 5 (range 1))
           '(0)))

    (is (= (sut/cut-snippet 4 (range 5))
           '(0 1 2 3 4)))

    (is (= (sut/cut-snippet 0 (range 5))
           '(0 1 2 3 4)))))

(deftest payload-test
  (let [clojure-exception (make-exception)
        java-exception (make-java-exception)

        clojure-payload (sut/payload clojure-exception)
        java-payload (sut/payload java-exception)

        clojure-first-trace (some-> clojure-payload :backtrace first)
        java-first-trace (some-> java-payload :backtrace first)]

    (is (= (:type java-payload)
           "java.lang.Exception"))

    (is (= (:type clojure-payload)
           "clojure.lang.ExceptionInfo"))

    (is (= (:function clojure-first-trace)
           "hawk.core-test/make-exception"))))



