(defproject juxt/crux-lmdb "crux-git-version-alpha"
  :description "Crux LMDB"
  :url "https://github.com/juxt/crux"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.logging "0.5.0"]
                 [juxt/crux-core "crux-git-version-beta"]
                 [com.github.jnr/jnr-ffi "2.1.9"]
                 [org.lmdbjava/lmdbjava "0.7.0" :exclusions [com.github.jnr/jffi]]
                 [org.lwjgl/lwjgl "3.2.3" :classifier "natives-linux" :native-prefix ""]
                 [org.lwjgl/lwjgl-lmdb "3.2.3" :classifier "natives-linux" :native-prefix ""]
                 [org.lwjgl/lwjgl "3.2.3" :classifier "natives-macos" :native-prefix ""]
                 [org.lwjgl/lwjgl-lmdb "3.2.3" :classifier "natives-macos" :native-prefix ""]
                 [org.lwjgl/lwjgl-lmdb "3.2.3"]]
  :middleware [leiningen.project-version/middleware]
  :pedantic? :warn)
