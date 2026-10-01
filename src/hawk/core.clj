(ns hawk.core
  (:import java.util.Base64)
  (:require
   [cheshire.core :as json]
   [clojure.string :as str]
   [org.httpkit.client :as http-client]
   [clojure.java.io :as io]))

;;
;; Schemas
;;

(def Throwable?
  [:fn {:error/fn (fn [{:keys [value]} _]
                    (str "should be a Throwable, got " (type value)))}
   #(instance? Throwable %)])

(def Token :string)

(def HawkClient
  [:map
   [:token Token]
   [:url :string]])

(def Payload
  [:map
   [:title :string]
   [:type {:optional true} :string]
   [:description {:optional true} :string]
   [:level {:optional true} :int]
   [:backtrace
    {:optional true}
    [:vector
     [:map
      [:file :string]
      [:line :int]
      [:column {:optional true} [:maybe :int]]
      [:function {:optional true} [:maybe :string]]
      [:arguments {:optional true} [:maybe :any]]
      [:sourceCode
       {:optional true}
       [:vector
        [:map
         [:line :int]
         [:content :string]]]]]]]
   [:addons {:optional true} [:map-of :keyword :any]]
   [:release {:optional true} :string]
   [:user
    {:optional true}
    [:map
     [:id :string]
     [:name {:optional true} :string]
     [:url {:optional true} :string]
     [:photo {:optional true} :string]]]
   [:context {:optional true} [:map-of :keyword :any]]])

(def CatcherEvent
  [:map
   [:token :string]
   [:catcherType :string]
   [:payload Payload]])

;;
;; Client
;; 

(defn decode-token [token]
  (try
    (json/decode (String. (.decode (Base64/getUrlDecoder) token)))
    (catch Exception _
      (throw (ex-info "hawk: Can not parse provided token" {:token token})))))

(defn make-endpoint [token]
  (let [decoded (decode-token token)
        integration-id (get decoded "integrationId")
        secret (get decoded "secret")
        prefix (str/replace (str integration-id secret) #"-" "")]
    (str "https://"  prefix "@k1.hawk.so")))

;;
;; Send
;;

(defn clojure-file? [^StackTraceElement el]
  (when-let [file-name (.getFileName el)]
    (let [ext (last (str/split file-name #"\."))]
      (or (= ext "clj")
          (= ext "cljc")))))

#_(defn clojure-invoke? [^StackTraceElement el]
    (#{"invokeStatic" "doInvoke"} (.getMethodName el)))

(defn cut-snippet
  "Takes source code context around a line."
  ([line-number lines]
   (let [default-context-size 3]
     (cut-snippet line-number lines default-context-size)))
  ([line-number lines context]
   (let [window (inc (* context 2))
         drop-count (if (< (- (count lines) line-number) context)
                      (- (count lines) window)
                      (- line-number (inc context)))]
     (->> lines (drop drop-count) (take window)))))

(defn get-ns-name [class-name]
  (-> class-name
      (str/split #"\$")
      (first)
      (str/replace #"_" "-")))

(defn source-file-path
  "Returns source file path for clojure and java files."
  [^StackTraceElement el]
  (if (clojure-file? el)
    (let [ns-name (get-ns-name (.getClassName el))
          dir-name (str/join "/" (butlast (str/split ns-name #"\.")))
          file-name (.getFileName el)]
      (str dir-name "/" file-name))
    (.getFileName el)))

(defn fetch-snippet
  "Fetches code snippet from file."
  [file line]
  (when file
    (when-let [res (io/resource file)]
      (with-open [rdr (io/reader res)]
        (some->> rdr
                 line-seq
                 (map-indexed (fn [idx string] {:line (inc idx) :content string}))
                 (cut-snippet line)
                 (doall))))))

(defn function-name [^StackTraceElement el]
  (if (clojure-file? el)
    (-> (.getClassName el)
        ;; Using Regex below instead of `(str/replace "$" "/")`
        ;; for such cases when function name might start with $
        (str/replace #"\$+" (fn [m] (str "/" (subs m 1))))
        (str/replace "_" "-"))
    (str (.getClassName el) "/" (.getMethodName el))))

(defn make-frame [^StackTraceElement el]
  (let [file-path (source-file-path el)
        line-number (.getLineNumber el)
        snippet (fetch-snippet file-path line-number)]
    (cond-> {:file (.getFileName el)
             :line line-number
             :function (function-name el)}
      snippet (assoc :sourceCode snippet))))

(defn make-stack-trace [^Throwable e]
  (map make-frame (.getStackTrace e)))

(defn payload [^Throwable e]
  (let [message (ex-message e)
        stack-trace (make-stack-trace e)]
    (cond->
     {:title message
      :type (-> e type .getName)
      ;; TODO add :release
      :backtrace stack-trace})))

(defn format-body
  {:malli/schema
   [:=> [:cat Token Throwable? [:maybe :map] [:maybe :map]] CatcherEvent]}
  [token e context user]
  {:token token
   :catcherType "error/clojure"
   :payload (cond-> (payload e)
              context (assoc :context context)
              user (assoc :user user))})

;;
;; Public API
;;

(defn make-client
  {:malli/schema
   [:=> [:cat :string] HawkClient]}
  [token]
  {:token token
   :url (make-endpoint token)})

(defn send!
  {:malli/schema
   [:=> [:cat HawkClient Throwable? [:? [:maybe :map]] [:? [:maybe :map]]] :any]}
  ([client e]
   (send! client e nil nil))
  ([client e context]
   (send! client e context nil))
  ([client e context user]
   (let [url (:url client)
         token (:token client)
         body (format-body token e context user)]
     (http-client/post url {:body (json/generate-string body)}))))


