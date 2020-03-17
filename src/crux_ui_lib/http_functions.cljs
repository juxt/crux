(ns crux-ui-lib.http-functions
  (:require [promesa.core :as p :include-macros true]
            [clojure.edn :as edn]))

(defmulti fetch type)

(defmethod fetch :default [{:keys [method url] :as opts}]
  (if method (assert (#{:post :get} method) (str "Unsupported HTTP method: " (:method opts))))
  (let [opts (-> opts
                 (update :method (fnil name :get))
                 (assoc :referrerPolicy "origin"))]
    (p/alet [fp (js/fetch url (clj->js opts))
             resp (p/await fp)
             headers (.-headers resp)
             content-type (.get headers "Content-Type")
             text (p/await (.text resp))]
      {:body text
       :status (.-status resp)
       :headers {:content-type content-type}})))

(defmethod fetch js/String [url]
  (fetch {:url url :method :get}))

(defn fetch-edn [prms]
  (p/map #(update % :body edn/read-string) (fetch prms)))

