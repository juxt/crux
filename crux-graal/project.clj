(defproject juxt/crux-graal "crux-git-version-alpha"
  :description "Crux Graal"
  :url "https://github.com/juxt/crux"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [ch.qos.logback/logback-classic "1.2.3" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/slf4j-simple "1.7.26"]
                 [juxt/crux-core "crux-git-version-alpha"]]
  :middleware [leiningen.project-version/middleware]

  :jvm-opts ["-Dclojure.compiler.direct-linking=true"
             "-Dclojure.spec.compile-asserts=false"]

  :aot [crux.kv.memdb crux.kv.rocksdb crux.main.graal]
  :main crux.main.graal

  :pedantic? :warn)
