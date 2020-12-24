(defproject juxt/crux-core "crux-git-version-beta"
  :description "An open source document database with bitemporal graph queries"
  :url "https://github.com/juxt/crux"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.logging "1.1.0"]
                 [org.slf4j/slf4j-api "1.7.30"]
                 [org.clojure/spec.alpha "0.2.176"]
                 [com.stuartsierra/dependency "0.2.0"]
                 [com.taoensso/nippy "3.1.1"]
                 [org.clojure/tools.reader "1.3.4"]
                 [org.clojure/data.json "1.0.0"]
                 [org.clojure/tools.cli "1.0.194"]
                 [org.agrona/agrona "1.7.2"]
                 [com.github.jnr/jnr-ffi "2.1.9" :scope "provided"]
                 [edn-query-language/eql "1.0.0"]
                 [junit/junit "4.12"]]
  :profiles {:dev {:dependencies [[ch.qos.logback/logback-classic "1.2.3"]]}
             :cli-e2e-test {:jvm-opts ["-Dlogback.configurationFile=../resources/logback-test.xml"]
                            :dependencies [[juxt/crux-http-server "crux-git-version-alpha"]]}
             :test {:dependencies [[juxt/crux-test "crux-git-version"]

                                   ;; dependency conflict
                                   [org.tukaani/xz "1.8"]
                                   [org.ow2.asm/asm "5.0.4"]]}}
  :middleware [leiningen.project-version/middleware]
  :java-source-paths ["src" "test"]
  :javac-options ["-source" "8" "-target" "8"
                  "-XDignore.symbol.file"
                  "-Xlint:all,-options,-path"
                  "-Werror"
                  "-proc:none"]
  :junit ["test"]
  :junit-formatter :summary
  :pedantic? :warn
  :plugins [[lein-junit "1.1.9"]])
