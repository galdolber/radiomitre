(defproject radiomitre "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main radiomitre.core
  :aot [radiomitre.core]
  :dependencies [[org.clojure/clojure "1.7.0-alpha2"]
                 [enlive "1.1.5"]
                 [http-kit "2.1.16"]
                 [hiccup "1.0.5"]
                 [clj-time "0.8.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [ring/ring-devel "1.3.1"]])
