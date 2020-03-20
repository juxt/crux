(defproject juxt/crux-console "derived-from-git"
  :dependencies
    [[org.clojure/clojure "1.10.1"]
     ; 1.10.1 doesn't work with this aleph
     [aleph "0.4.6"]
     [bidi "2.1.6"]
     [hiccup "1.0.5"]
     [page-renderer "0.4.4"]
     [ring/ring-core "1.8.0"]
     [ring/ring-jetty-adapter "1.8.0"]
     [ring/ring-codec "1.1.2"]
     [ring-cors "0.1.13"]
     [juxt/crux-core "derived-from-git"]
     [juxt/crux-rocksdb "derived-from-git"]]

  :min-lein-version "2.9.1"
  :main crux-ui-server.main
  :aot  [crux-ui-server.main]
  :uberjar-name "crux-console-skimmed.jar"
  :omit-sources true
  :resource-paths ["resources"]
  :middleware [leiningen.project-version/middleware]
  :profiles
  {:dev
   {:main dev :repl-options {:init-ns dev}
    :source-paths ["src" "test" #_"node_modules"]
    :dependencies [[nrepl/nrepl "0.6.0"]]}
   :crux-jars
   {:uberjar-name "crux-console.jar"
    :auto-clean false
    :dependencies
    [[juxt/crux-core "derived-from-git"]
     [juxt/crux-rocksdb "derived-from-git"]]}
   :shadow-cljs ; also see package.json deps
   {:dependencies
    [[org.clojure/clojurescript "1.10.520"]
     [org.clojure/tools.reader "1.3.2"]
     [reagent "0.8.1"]
     [re-frame "0.10.8"]
     [garden "1.3.9"]
     [medley "1.2.0"]
     [funcool/promesa "2.0.1"]
     [org.clojure/tools.logging "0.5.0"]
     [com.andrewmcveigh/cljs-time "0.5.2"]
     [binaryage/oops "0.6.4"]
     [day8.re-frame/test "0.1.5"]
     [day8.re-frame/re-frame-10x "0.3.3"]
     [thheller/shadow-cljs "2.8.52"]
     [com.google.javascript/closure-compiler-unshaded "v20190819"]
     [org.clojure/google-closure-library "0.0-20190213-2033d5d9"]]}}

  ; AWS is using Java 8
  :javac-options ["-source" "8" "-target" "8"
                  "-XDignore.symbol.file"
                  "-Xlint:all,-options,-path"
                  "-Werror"
                  "-proc:none"]

  :repositories [["clojars" "https://repo.clojars.org"]]
  :plugins [;[lein-shadow "0.1.5"] ; nasty guy, deletes original shadow-cljs config if you run it
            [lein-shell  "0.5.0"]] ; https://github.com/hypirion/lein-shell

  :clean-targets ^{:protect false} ["target" #_"resources/static/crux-ui/compiled"]

  :aliases
  {"yarn"
   ["do" ["shell" "yarn" "install"]]

   "shadow"
   ["shell" "node_modules/.bin/shadow-cljs"]

   "build"
   ["do"
    ["clean"]
    ["yarn"]
    ["shadow" "release" "app"]       ; compile
    #_["shadow" "release" "app-perf"]] ; compile production ready performance charts app

   "ebs"
   ["do" ["shell" "sh" "./dev/build-ebs.sh"]]

   "build-ebs"
   ["do"
    ["build"]
    ["uberjar"]
    ["ebs"]]

   "cljs-dev"
   ["do"
    ["yarn"]
    ["shadow" "watch" "app"]]

   "dep-tree"
   ["do"
    ["yarn"]
    ["shadow" "pom"]
    ["shell" "mvn" "dependency:tree"]]

   "build-report"
   [["yarn"]
    ["shadow" "run" "shadow.cljs.build-report" "app" "report.html"]]})
