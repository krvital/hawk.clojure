(ns hawk.middleware
  (:require [hawk.core :as hawk]))

(defn wrap-hawk [handler token]
  (fn [req]
    (try
      (handler req)
      (catch Exception e
        (try
          (hawk/send! token e)
          (catch Exception _))
        (throw e)))))

