(ns radiomitre.core
  (:gen-class)
  (:use ring.middleware.resource
        ring.middleware.reload
        ring.middleware.content-type)
  (:require
   [clj-time.core :as t]
   [clj-time.format :as f]
   [clj-time.coerce :as coerce]
   [clojure.pprint :as pp]
   [clojure.data.xml :as xml]
   [org.httpkit.server :as httpkit]
   [org.httpkit.client :as httpcli]
   [net.cgrand.enlive-html :as html]
   [hiccup.core :as hiccup]))

(def date-formatter (f/formatter "dd/MM/YYYY"))

(defonce data (atom {}))

(defn fetch [url]
  (let [r @(httpcli/get url {:user-agent "Chrome 37.0.2049.0"})]
    (:body r)))

(defn audio-link [permalink]
  (let [[_ a b] (re-find #"cp_load_widget\([\"](.*)[\"], [\"](.*)[\"]\)" (fetch permalink))]
    (format "http://www.cincopa.com/media-platform/runtime/widgetasync.aspx?id=%s&fid=%s" b a)))

(defn find-url [xml]
  (let [r (first (:content xml))]
    (if (string? r)
      r
      (recur r))))

(defn audio-mp3 [permalink]
  (try
    (let [link (fetch (audio-link permalink))]
      (when-let [id (second (re-find #"fid=(.*)\"" link))]        
        (find-url          
         (xml/parse-str
          (fetch
           (format "http://www.cincopa.com/media-platform/runtime/xspf.aspx?fid=%s&playlistsize=0&height=&width=&backcolor=000000&frontcolor=ffffff&lightcolor=&screencolor=&playlist=bottom&shuffle=false&repeat=always&bufferlength=2" id))))))
    (catch Exception e
      (.printStackTrace e)
      nil)))

(defn add-article [article]
  (try
    (let [{:keys [data-permalink data-title id]} (:attrs article)
          mp3 (audio-mp3 data-permalink)]
      (when (and mp3 (.endsWith mp3 ".mp3"))
        (swap! data assoc
               id
               {:permalink data-permalink
                :mp3 mp3
                :date (.parse (java.text.SimpleDateFormat. "yyyy/MM/dd")
                              (first (next (re-find #"radiomitre/([0-9]*/[0-9]*/[0-9]*)/" data-permalink))))
                :title (if (.endsWith data-title " | Radio Mitre")
                         (subs data-title 0 (- (count data-title) 14))
                         data-title)
                :id id
                :description (html/text (first (html/select article [:p])))
                :img (:src (:attrs (first (html/select article [:img]))))})))
    (catch Exception e nil)))

(defn load-last [n]
  (doseq [n (range n)]
    (let [page (html/html-resource
                (java.io.StringReader.
                 (fetch (str "http://secciones.cienradios.com.ar/radiomitre/page/" n))))]
      (doall (pmap add-article (html/select page [:article])))))
  (let [weekago (t/plus (t/today) (t/weeks -1))]
    (swap! data #(into {} (map (fn [i] [(:id i) i])
                               (remove (fn [i] (t/before? (coerce/to-local-date (:date i)) weekago)) (vals %)))))))

(defn render []
  [:html
   [:head
    [:meta {:charset "UTF-8"}]
    [:title "Radio Mitre"]
    [:script
     " (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
  (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
  m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
  })(window,document,'script','//www.google-analytics.com/analytics.js','ga');

  ga('create', 'UA-55574235-1', 'auto');
  ga('send', 'pageview');"]
    [:script
     "window.fbAsyncInit = function() {
    FB.init({
      appId      : '756550831049368',
      xfbml      : true,
      version    : 'v2.1'
    });
  };

  (function(d, s, id){
     var js, fjs = d.getElementsByTagName(s)[0];
     if (d.getElementById(id)) {return;}
     js = d.createElement(s); js.id = id;
     js.src = \"//connect.facebook.net/en_US/sdk.js\";
     fjs.parentNode.insertBefore(js, fjs);
   }(document, 'script', 'facebook-jssdk'));"]
    [:style
     ".title {-webkit-text-stroke: 1px black;text-align:center;position:absolute;top:5px;z-index:100;font-size:23px;color:white;font-weight:bold}
      .date {position:absolute;right:3px;bottom:30px;color:#DDD;font-size:13px}
      .screen {position:absolute;border-radius:7px;top:0;bottom:0;left:0;width:100%;background-color:rgba(0,0,0,0.3)}
      .article {position:relative;border:1px solid #AAA;border-radius:8px;margin:15px 0;height:300px;background-size:cover}
      .audio {position:absolute;left:0;bottom:0px;width:100%}"]
    [:link {:href "bootstrap.min.css" :rel "stylesheet"}]
    [:link {:href "material/ripples.css" :rel "stylesheet"}]
    [:link {:href "material/material.css" :rel "stylesheet"}]
    [:link {:href "material/icons/icons-material-design.css" :rel "stylesheet"}]]
   [:body.container {:style "background:url(tweed.png) repeat"}
    [:div
     [:img {:src "logo.jpg" :height "70"}]]
    [:h1 "Resumen Semanal Radio Mitre"]
    [:div.row
     (for [a (reverse (sort-by :date (vals @data)))]
       [:div.col-xs-6.col-md-4 {:id (:id a)}
        [:div.article {:style (str "background-image:url(" (:img a) ")")}
         [:div.screen]
         [:div.date (.format (java.text.SimpleDateFormat. "dd/MM/yyyy") (:date a))]
         [:div.title (:title a)]         
         [:audio.audio {:controls "" :preload "none"}
          [:source {:src (:mp3 a) :type "audio/mpeg"}]]]])]
    [:center
     [:div.fb-like {:data-share "true" :data-width "450" :data-show-faces "true"}]
     [:br]
     [:div.fb-comments
     {:data-href "http://radiomitre.nebleena.com"
      :data-numposts "10"
      :data-colorscheme "dark"}]
     [:footer {:style "padding:10px 0 20px"}
      [:i.icon-material-gmail]
      [:div "gal@dolber.com"]
      [:div "Esta pagina no esta afiliada con radio mitre o cienradios.com.ar"]
      [:div "Copyright &copy; 2014 Gal Dolber"]]]]])

(defn web-handler [req]
  {:body (hiccup/html (case (:uri req)
                        "/" (render)
                        "/refresh" (do (load-last 1)
                                       (render))))
   :status 200
   :content-type "text/html"})

(def handler
  (-> web-handler
      (wrap-resource "public")))

(def server)

(defn -main [& args]
  (load-last 10)
  (future
    (loop []
      (Thread/sleep (* 1000 60 60))
      (load-last 1)
      (recur)))
  (def server (httpkit/run-server (wrap-reload #'handler) {:port (if (pos? (count args)) 80 8080)}))
  (println "Server started"))
