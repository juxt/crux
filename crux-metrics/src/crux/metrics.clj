(ns crux.metrics
  (:require [crux.metrics.indexer :as indexer-metrics]
            [crux.metrics.kv-store :as kv-metrics]
            [crux.metrics.query :as query-metrics]
            [crux.metrics.dropwizard :as dropwizard]
            [crux.metrics.dropwizard.jmx :as jmx]
            [crux.metrics.dropwizard.console :as console]
            [crux.metrics.dropwizard.csv :as csv]
            [crux.metrics.dropwizard.cloudwatch :as cloudwatch])
  (:import [java.time Duration]
           [java.util.concurrent TimeUnit]))

(def registry
  {::registry {:start-fn (fn [deps _]
                           ;; When more metrics are added we can pass a
                           ;; registry around
                           (doto (dropwizard/new-registry)
                             (indexer-metrics/assign-listeners deps)
                             (kv-metrics/assign-listeners deps)
                             (query-metrics/assign-listeners deps)))
               :deps #{:crux.node/node :crux.node/indexer :crux.node/bus :crux.node/kv-store}}})

(def jmx-reporter
  {::jmx-reporter {:start-fn (fn [{::keys [registry]} args]
                               (jmx/start-reporter registry args))
                   :args {::jmx/domain {:doc "Add custom domain"
                                        :required? false
                                        :crux.config/type :crux.config/string}
                          ::jmx/rate-unit {:doc "Set rate unit"
                                           :required? false
                                           :default TimeUnit/SECONDS
                                           :crux.config/type :crux.config/time-unit}
                          ::jmx/duration-unit {:doc "Set duration unit"
                                               :required? false
                                               :default TimeUnit/MILLISECONDS
                                               :crux.config/type :crux.config/time-unit}}
                   :deps #{::registry}}})

(def console-reporter
  {::console-reporter {:start-fn (fn [{::keys [registry]} args]
                                   (console/start-reporter registry args))
                       :deps #{::registry}
                       :args {::console/report-frequency {:doc "Frequency of reporting metrics"
                                                          :default (Duration/ofSeconds 1)
                                                          :crux.config/type :crux.config/duration}
                              ::console/rate-unit {:doc "Set rate unit"
                                                   :required? false
                                                   :default TimeUnit/SECONDS
                                                   :crux.config/type :crux.config/time-unit}
                              ::console/duration-unit {:doc "Set duration unit"
                                                       :required? false
                                                       :default TimeUnit/MILLISECONDS
                                                       :crux.config/type :crux.config/time-unit}}}})

(def csv-reporter
  {::csv-reporter {:start-fn (fn [{::keys [registry]} args]
                               (csv/start-reporter registry args))
                   :deps #{::registry}
                   :args {::csv/report-frequency {:doc "Frequency of reporting metrics"
                                                  :default (Duration/ofSeconds 1)
                                                  :crux.config/type :crux.config/duration}
                          ::csv/rate-unit {:doc "Set rate unit"
                                           :required? false
                                           :default TimeUnit/SECONDS
                                           :crux.config/type :crux.config/time-unit}
                          ::csv/duration-unit {:doc "Set duration unit"
                                               :required? false
                                               :default TimeUnit/MILLISECONDS
                                               :crux.config/type :crux.config/time-unit}}}})

(def cloudwatch-reporter
  {::cloudwatch-reporter {:start-fn (fn [{::keys [registry]} args]
                                      (cloudwatch/start-reporter registry args))
                          :deps #{::registry}
                          :args {::cloudwatch/region {:doc "Region for uploading metrics. Tries to get it using api. If this fails, you will need to specify region."
                                                      :required? false
                                                      :crux.config/type :crux.config/string}
                                 ::cloudwatch/report-frequency {:doc "Frequency of reporting metrics"
                                                                :default (Duration/ofSeconds 1)
                                                                :crux.config/type :crux.config/duration}
                                 ::cloudwatch/dry-run? {:doc "When true, the reporter prints to console instead of uploading to cw"
                                                        :required? false
                                                        :crux.config/type :crux.config/boolean}
                                 ::cloudwatch/jvm-metrics? {:doc "When true, include jvm metrics for upload"
                                                            :required? false
                                                            :crux.config/type :crux.config/boolean}
                                 ::cloudwatch/dimensions {:doc "Add global dimensions to metrics"
                                                          :required? false
                                                          :crux.config/type :crux.config/string-map}}}})

(def with-jmx (merge registry jmx-reporter))
(def with-console (merge registry console-reporter))
(def with-csv (merge registry csv-reporter))
(def with-cloudwatch (merge registry cloudwatch-reporter))
