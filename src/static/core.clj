(ns static.core
  (:require [clojure.java.browse :as browse]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [clojure.tools.logging :as log]
            [hiccup.core :as hiccup]
            [hiccup.page :refer :all]
            [hiccup.util :refer :all]
            [net.cgrand.enlive-html :as enlive]
            [ring.adapter.jetty :as jetty]
            [ring.util.response :refer :all]
            [static.config :as config]
            [static.io :as io]
            [stringtemplate-clj.core :as string-template]
            [watchtower.core :as watcher])
  (:import (java.io File)
           (java.net URL)
           (java.text SimpleDateFormat)
           (org.apache.commons.io FileUtils FilenameUtils))
  (:gen-class))

(defmacro define-template
  "Wrap a template in the necessary calls to return correctly."
  [template-file & body]
  `((fn []
      ((enlive/template ~template-file [~'metadata ~'content]
                        ~@body)
       ~'metadata ~'content))))

(defn template-path
  "Return a template file descriptor for the html template name
they're in public, so that one can just start a webserver there and
modify the html with all the css and images in place."
  [name]
  (File. (str (io/dir-path :public) name)))

(defn setup-logging []
  (let [logger (java.util.logging.Logger/getLogger "")]
    (doseq [handler (.getHandlers logger)]
      (. handler setFormatter
         (proxy [java.util.logging.Formatter] []
           (format
             [record]
             (str "[+] " (.getLevel record) ": " (.getMessage record) "\n")))))))

(defmacro log-time-elapsed
  "Evaluates expr and logs the time it took.  Returns the value of
expr."
  {:added "1.0"}
  [msg & expr]
  `(let [start# (. System (currentTimeMillis))
         ret# (do ~@expr)]
     (log/info (str ~msg " " (/ (double (- (. System (currentTimeMillis)) start#)) 1000.0) " secs"))
     ret#))

(declare tag-sidebar-list)

(defn parse-date
  "Format date from in spec to out spec."
  [in out date]
  (.format (SimpleDateFormat. out) (.parse (SimpleDateFormat. in) date)))

(defn javadate-from-file
  "Returns an actual boxed date object for time calculations."
  [file]
  (.parse (SimpleDateFormat. "yyyy-MM-dd")
          (re-find #"\d*-\d*-\d*" (FilenameUtils/getBaseName (str file)))))

(defn date-from-file
  "Parse a date from the url of the file."
  [file dateformat]
  (parse-date "yyyy-MM-dd" dateformat
              (re-find #"\d*-\d*-\d*"
                       (FilenameUtils/getBaseName (str file)))))


(defn post-url
  "Given a post file return its URL."
  [file]
  (let [name (FilenameUtils/getBaseName (str file))]
    (str (apply str (interleave (repeat \/) (.split name "-" 4))) "/")))

(defn site-url [f & [ext]]
  (-> (str f)
      (.replaceAll (io/dir-path :site) "")
      (FilenameUtils/removeExtension)
      (str "."
           (or ext
               (:default-extension (config/config))))))

(defn url-for-tag [tag]
  (str "/tags/" tag "/index.html"))

(defn project-sidebar-list
  "Give a list of all projects in the sidebar, with their respective
url projects that have the option 'published:no' set will be
ignored"
  []
  (filter #(< 0 (count %))
          (map (fn [f]
                 (let [[metadata content] (io/read-doc f)
                       url (site-url f (:extension metadata))]
                   (if (= (:options metadata) "published:no")
                     {}
                     {:project (:title metadata)
                      :link (str "/" url)})))
               (io/list-files :site))))

(defn create-site-meta
  "Create the same structure as post-meta, but for sites."
  [metadata content]
  (let [url (:url metadata)]
    {:title (:title metadata)
     :content content
     :url url
     :date ""
     :footnotes []
     :id (hash url)
     :tags (:tags metadata)}))

(defn tags-meta [metadata]
  (if (:tags metadata)
    (for [tag (str/split (:tags metadata) #",? ")]
      {:title tag :url (url-for-tag tag)})
    (do (log/warn "Nil tags... Returning empty tag list.")
        [])))

(defn create-post-meta
  "Create a dictionary with information for a content item / post."
  [f]
  (let [url (post-url f)
        [metadata content] (static.io/read-doc f)
        date (date-from-file f (:date-format-post (config/config)))]
    {:title (:title metadata)
     :content @content
     :url url
     :filename (FilenameUtils/getName (str f))
     :meta metadata
     :date date
     :javadate (javadate-from-file f)
     :footnotes (:footnotes metadata)
     :id (hash url)
     :tags (:tags metadata)
     :keywords (:keywords metadata)
     :keyword-keywords (:keyword-keywords metadata)
     :keyword-tags (:keyword-tags metadata)}))

;; These vars are included in the template metadata when specific
;; build modes are active.

;; Is being run in watch mode.
(def ^:dynamic watch-mode nil)
;; Rendering the blog index.
(def ^:dynamic blog-index nil)

(defn enhance-metadata
  "Enhance the given metadata with additional information so that we
don’t have to compute this in the template."
  [m]
  ;; This is certainly more complex than it should be. so much still
  ;; to learn.
  (let [tagfn (fn [tags]
                (filter #(> (count %) 0)
                        (if (string? tags) (str/split tags #" " ) tags)))
        page-tags (tagfn (str (:tags m) " " (:keywords m)))
        site-tags (tagfn (:site-default-keywords (config/config)))
        merge-tags (vec (sort (into #{} (if (> (count page-tags) 0) (apply conj site-tags page-tags) site-tags))))
        tagstring (str/join ", " merge-tags)
        ;; we also need the complete list of posts with their tags
        files (io/list-files :posts)
        posts (map #(-> (io/read-doc %)
                        first
                        (assoc :url (post-url %))
                        (assoc :date (date-from-file % (:date-format-post (config/config))))
                        (select-keys [:title :url :tags :keyword-tags :date :keywords :keyword-keywords]))
                   files)
        ;; posts (map (fn [f] (assoc (first (read-doc f)) :url (post-url f))) files)
        ;; posts (map #(select-keys % [:title :url :tags :date]) posts)
        ]

    ;; Update with default-values for non-existing keywords are the
    ;; combination of the site keywords and the site/post specific
    ;; keywords
    (merge {:author (:site-author (config/config))
            :site-title (:site-title (config/config))
            :categories (tag-sidebar-list)
            :watching watch-mode
            :blog-index blog-index
            :postlist posts
            :projects (project-sidebar-list)}
           m
           {:tags tagstring})))

(def ^:dynamic metadata nil)
(def ^:dynamic content nil)

(defn template [page]
  (let [[m c] page
        template (if (:template m)
                   (:template m)
                   (:default-template (config/config)))
        [type template-string] (if (= template :none)
                                 [:none c]
                                 (io/read-template template @io/memo-param))]
    (cond (or (= type :clj)
              (= type :none))
          (binding [*ns* (the-ns 'static.core)
                    metadata m content c]
            (hiccup/html (map #(eval %) template-string)))
          (= type :html)
          (let [m (->> m
                       (reduce (fn[h [k v]]
                                 (assoc h (name k) v)) {}))]
            (string-template/render-template template-string
                                             (merge m {"content" c}))))))

(defn process-site
  "Process site pages."
  []
  (dorun
   (map
    #(let [f %
           [metadata content] (io/read-doc f)]

       (if (empty? (force content))
         (log/warn (str "Empty Content: " f)))

       (io/write-out-dir
        (site-url f (:extension metadata))
        (template [(enhance-metadata (assoc metadata :type :site))
                   [(create-site-meta metadata (force content))]])))
    (io/list-files :site))))

;;
;; Create RSS Feed.
;;

(defn post-xml
  "Create RSS item node."
  [file]
  (let [[metadata content] (io/read-doc file)
        limit (:rss-description-char-limit (config/config))
        description (if (and (> limit 0) (> (count @content) limit))
                      (str (subs @content 0 limit) "… ")
                      @content)
        description (if (:summary metadata) (:summary metadata) (if (:description metadata) (:description metadata)
                                                                    (escape-html description)))
        ]
    [:item
     [:title (escape-html (:title metadata))]
     [:link  (str (URL. (URL. (:site-url (config/config))) (post-url file)))]
     [:pubDate (date-from-file file (:date-format-rss (config/config)))]
     [:description description]]))

(defn create-rss
  "Create RSS feed."
  []
  (let [in-dir (File. (io/dir-path :posts))
        posts (take 10 (reverse (io/list-files :posts)))]
    (io/write-out-dir "rss-feed"
                      (hiccup/html (xml-declaration "UTF-8")
                                   (doctype :xhtml-strict)
                                   [:rss {:version "2.0"}
                                    [:channel
                                     [:title (escape-html (:site-title (config/config)))]
                                     [:link (:site-url (config/config))]
                                     [:description
                                      (escape-html (:site-description (config/config)))]
                                     (pmap post-xml posts)]]))))

(defn create-sitemap
  "Create sitemap."
  []
  (io/write-out-dir
   "sitemap.xml"
   (let [base (:site-url (config/config))]
     (hiccup/html (xml-declaration "UTF-8")
                  [:urlset {:xmlns "http://www.sitemaps.org/schemas/sitemap/0.9"}
                   [:url [:loc base]]
                   (map #(vector :url [:loc (str base %)])
                        (map post-url (io/list-files :posts)))
                   (map #(vector :url [:loc (str base "/" %)])
                        (map site-url (io/list-files :site)))]))))

;;
;; Create Tags Page.
;;

(defn tag-map
  "Create a map of tags and posts contining them.

  {tag1 => [url1 url2..]}"
  []
  (reduce
   (fn [h v]
     (let [[metadata] (io/read-doc v)
           info [(post-url v) (:title metadata) v]
           tags (.split (:tags metadata) " ")]
       (reduce
        (fn [m p]
          (let [[tag info] p]
            (if (nil? (m tag))
              (assoc m tag [info])
              (assoc m tag (conj (m tag) info)))))
        h (partition 2 (interleave tags (repeat info))))))
   (sorted-map)
   (filter #(not (nil? (:tags (first (io/read-doc %))))) (io/list-files :posts))))

(defn tag-sidebar-list
  "Create a list with all tags that link to the individual tag
entries."
  []
  (map (fn [[tag posts]]
         {:tag tag :url (url-for-tag tag) :count (count posts)}) (tag-map)))

(defn create-tags
  "Create and write tags page."
  []
  (dorun
   (map (fn [t]
          (let [[tag posts] t
                metadata {:title (str (:site-title (config/config))
                                      (format (:site-title-tag (config/config)) tag))
                          :template (:list-template (config/config))
                          :description (:site-description (config/config))}
                enhanced-meta (enhance-metadata metadata)
                ;; The 2nd positon is the fp which we use to get all
                ;; info from this post:
                content (map #(create-post-meta (nth % 2)) (reverse posts))]
            (io/write-out-dir (url-for-tag tag)
                              (template [enhanced-meta [[tag content]]]))))
        (tag-map))))

(defn random-posts
  [amount]
  (map #(let [f %
              posturl (post-url f)
              [postmetadata _] (io/read-doc f)
              date (date-from-file f (:date-format-post (config/config)))]
          {:date date :url posturl :title (:title postmetadata)})
       (take amount (shuffle (io/list-files :posts)))))

;;
;; Create pages for latest posts.
;;

(defn pager
  "Return previous, next navigation links."
  [page max-index posts-per-page]
  (let [count-total (count (io/list-files :posts))
        older (str "/latest-posts/" (- page 1) "/")
        newer (str "/latest-posts/" (+ page 1) "/")]
    (cond
      (< count-total posts-per-page) nil
      (= page max-index) {:older older}
      (= page 0) {:newer newer}
      :default {:newer newer :older older})))

(defn create-latest-posts
  "Create and write latest post pages."
  []
  (let [posts-per-page (:posts-per-page (config/config))
        posts (partition posts-per-page
                         posts-per-page
                         []
                         (reverse (io/list-files :posts)))
        pages (partition 2 (interleave (reverse posts) (range)))
        [_ max-index] (last pages)]
    (doseq [[posts page] pages]
      (let [pager-data (pager page max-index posts-per-page)
            ;; Don’t add page extension for first page / index:
            title-extension (if (< page max-index)
                              (format (:site-title-page (config/config)) (+ page 1))
                              "")
            ;; Create metadata for page.
            metadata {:title (str (:site-title (config/config)) title-extension)
                      :description (:site-description (config/config))
                      :template (:default-template (config/config))
                      :pager pager-data}
            enhanced-meta (enhance-metadata metadata)
            content (map #(create-post-meta %) posts)]
        (io/write-out-dir
         (str "latest-posts/" page "/index.html")
         (template [enhanced-meta content]))))))

;;
;; Create Archive Pages.
;;

(defn post-count-by-mount
  "Create a map of month to post count.

{month => count}"
  []
  (->> (io/list-files :posts)
       (reduce (fn [h v]
                 (let  [date (re-find #"\d*-\d*"
                                      (FilenameUtils/getBaseName (str v)))]
                   (if (nil? (h date))
                     (assoc h date 1)
                     (assoc h date (+ 1 (h date)))))) {})
       (sort-by first)
       reverse))

(defn create-archives-one-page
  "Create and write archive pages.

Write one single page that lists everything."
  []
  (let [files (map #(create-post-meta %) (io/list-files :posts))
        sorted (reverse (sort-by :javadate files))
        annotated (map (fn [d] (let [[_ year month & rest] (str/split (:url d) #"/")]
                                 (assoc d :year year :month month))) sorted)
        grouped (reverse (vec (into (sorted-map) (group-by :year annotated))))
        meta (enhance-metadata {:title (:archives-title (config/config))
                                :template (:list-template (config/config))})]
    (io/write-out-dir (str "archives/index.html") (template [meta grouped]))))

(defn create-archives-by-month
  "Create and write archive pages.

Write a page for each month and a page listing all months."
  []
  ;; Create main archive page.
  (let [meta (enhance-metadata {:title (:archives-title (config/config)) :template (:list-template (config/config))})
        content (map (fn [[mount count]]
                       {:title (str (parse-date "yyyy-MM" (:date-format-archive (config/config)) mount) "(" count ")")
                        :url (str "/archives/" (.replace mount "-" "/") "/")
                        :date (parse-date "yyyy-MM" (:date-format-archive (config/config)) mount)
                        :content (str count)
                        :id (hash mount)
                        :keywords []
                        :footnotes []}
                       ) (post-count-by-mount))]
    (io/write-out-dir (str "archives/index.html") (template [meta content])))

  ;; Create a page for each month.
  (dorun
   (pmap
    (fn [month]
      (let [posts (->> (io/list-files :posts)
                       (filter #(.startsWith
                                 (FilenameUtils/getBaseName (str %)) month))
                       reverse)
            metadata (enhance-metadata {:title (str (:archives-title (config/config)) (format (:archives-title-month (config/config)) month))
                                        :template (:list-template (config/config))})
            content (map create-post-meta posts)]
        ;; (println "content" content)
        (io/write-out-dir
         (str "archives/" (.replace month "-" "/") "/index.html")
         (template [metadata content]))))
    (keys (post-count-by-mount)))))

(defn create-aliases
  "Create redirect pages."
  ([]
   (doseq [post (io/list-files :posts)]
     (create-aliases post))
   (doseq [site (io/list-files :site)]
     (create-aliases site)))
  ([file]
   (let [doc (io/read-doc file)]
     (when-let [aliases (-> doc first :alias)]
       (doseq [alias (read-string aliases)]
         (io/write-out-dir
          alias
          (hiccup/html [:html
                        [:head
                         [:meta {:http-equiv "content-type" :content "text/html; charset=utf-8"}]
                         [:meta {:http-equiv "refresh" :content (str "0;url=" (post-url file))}]]])))))))

(defn process-posts
  "Create and write post pages."
  []
  (dorun
   (map
    #(let [f %
           [metadata content] (io/read-doc f)
           out-file (reduce (fn[h v] (.replaceFirst h "-" "/"))
                            (FilenameUtils/getBaseName (str f)) (range 3))]
       (when (empty? @content)
         (log/warn (str "Empty Content: " f)))
       (io/write-out-dir
        (str out-file "/index.html")
        (let [meta (enhance-metadata (assoc metadata :type :post :url (post-url f)))
              cont [(create-post-meta f)]]
          (template [meta cont]))))
    (io/list-files :posts))))

(defn process-public
  "Copy public from in-dir to out-dir."
  []
  (let [in-dir (File. (io/dir-path :public))
        out-dir (File. (:out-dir (config/config)))]
    (doseq [f (map #(File. in-dir %) (.list in-dir))]
      ;; we ignore files in public that start with _ as these are html
      ;; templates
      (if (.isFile f)
        (when (not (= \_ (first (FilenameUtils/getBaseName (str f)))))
          (FileUtils/copyFileToDirectory f out-dir))
        (FileUtils/copyDirectoryToDirectory f out-dir))
      )))

(defn load-base-template
  "Load the base template, that contains template defaults."
  []
  (binding [*ns* (the-ns 'static.core)]
    (let [filepath (str (io/dir-path :templates)
                        (:base-template (config/config)))]
      (println filepath)
      (try
        (load-file filepath)
        (catch Exception e
          (log/info e "Base template not found, continuing without."))))))

(defn create
  "Build Site."
  []
  ;; During watching, we don't delete everything, as that'll make any
  ;; change detection libs come up with a 404. So, it's best to do a
  ;; final 'create' before deployment.
  (when (nil? watch-mode)
    (doto (File. (:out-dir (config/config)))
      (FileUtils/deleteDirectory)
      (.mkdir)))

  ;; Make sure the memoziation returns new values.
  (io/memo-increase)

  ;; Import the template helpers.
  (load-base-template)

  (log-time-elapsed "Processing Public " (process-public))
  (log-time-elapsed "Processing Site " (process-site))

  (when (pos? (-> (io/dir-path :posts) (File.) .list count))
    (log-time-elapsed "Processing Posts " (process-posts))
    (log-time-elapsed "Creating RSS " (create-rss))
    (log-time-elapsed "Creating Tags " (create-tags))

    (when (:create-archives (config/config))
      (log-time-elapsed "Creating Archives " (create-archives-one-page)))

    (log-time-elapsed "Creating Sitemap " (create-sitemap))
    (log-time-elapsed "Creating Aliases " (create-aliases))

    (when (:blog-as-index (config/config))
      ;; Create the latest-post archives, i.e. create a index.html
      ;; with n posts under latest-posts/ and link them together.
      (binding [blog-index true]
        (log-time-elapsed "Creating Latest Posts " (create-latest-posts)))

      ;; Copy the very last index.html over to / as the current index.
      (let [max (apply max (map read-string (-> (:out-dir (config/config))
                                                (str  "latest-posts/")
                                                (File.)
                                                .list)))]
        (FileUtils/copyFile
         (File. (str (:out-dir (config/config))
                     "latest-posts/" max "/index.html"))
         (File. (str (:out-dir (config/config)) "index.html")))))))

(defn serve-static [req]
  (let [mime-types {".clj" "text/plain"
                    ".mp4" "video/mp4"
                    ".ogv" "video/ogg"}]
    (if-let [f (file-response (:uri req) {:root (:out-dir (config/config))})]
      (if-let [mimetype (mime-types (re-find #"\..+$" (:uri req)))]
        (merge f {:headers {"Content-Type" mimetype}})
        f))))

(defn watch-and-rebuild
  "Watch for changes and rebuild site on change."
  []
  (watcher/watcher [(:in-dir (config/config))]
                   (watcher/rate 1000)
                   (watcher/on-change
                    (fn [_]
                      (log/info "Rebuilding site...")
                      (try

                        ;; Bind the 'watching' metdata.
                        (binding [watch-mode true]
                          (create))

                        (catch Exception e
                          (log/error e "Exception thrown while building site!")))))))

(defn -main [& args]
  (let [[opts _ banner]
        ;; TODO: cli/cli is deprecated.
        (cli/cli args
                 ["--build" "Build Site." :default false :flag true]
                 ["--tmp" "Use tmp location override :out-dir" :default false :flag true]
                 ["--jetty" "View Site." :default false :flag true]
                 ["--watch" "Watch Site and Rebuild on Change." :default false :flag true]
                 ["--rsync" "Deploy Site." :default false :flag true]
                 ["--help" "Show help" :default false :flag true])
        {:keys [build tmp jetty watch rsync help]} opts]

    (when help
      (println "Static")
      (println banner)
      (System/exit 0))

    (setup-logging)

    (let [out-dir (:out-dir (config/config))
          tmp-dir (str (System/getProperty "java.io.tmpdir") "/" "static/")]

      (when (or tmp
                (and (:atomic-build (config/config))
                     build))
        (let [loc (FilenameUtils/normalize tmp-dir)]
          (config/set!-config :out-dir loc)
          (log/info (str "Using tmp location: " (:out-dir (config/config))))))

      (cond build (log-time-elapsed "Build took " (create))
            watch (do (watch-and-rebuild)
                      (future (jetty/run-jetty serve-static {:port 8080}))
                      (browse/browse-url "http://127.0.0.1:8080"))
            jetty (do (future (jetty/run-jetty serve-static {:port 8080}))
                      (browse/browse-url "http://127.0.0.1:8080"))
            rsync (let [{:keys [rsync out-dir host user deploy-dir]} (config/config)]
                    (io/deploy-rsync rsync out-dir host user deploy-dir))
            :default (println "Use --help for options."))

      (when (and (:atomic-build (config/config))
                 build)
        (FileUtils/deleteDirectory (File. out-dir))
        (FileUtils/moveDirectory (File. tmp-dir) (File. out-dir))))

    (when-not watch
      (shutdown-agents))))
