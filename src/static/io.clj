(ns static.io
  (:require [clojure.tools.logging :as log]
            [clojure.java.shell :as sh]
            [cssgen :as cssgen]
            [hiccup.core :refer :all]
            [stringtemplate-clj.core :as string-template]
            [static.config :as config])
  (:import (org.pegdown PegDownProcessor)
           (java.io File)
           (java.io InputStreamReader OutputStreamWriter)
           (org.apache.commons.io FileUtils FilenameUtils)))

(defn- split-file [content]
  (let [idx (.indexOf content "---" 4)]
    [(.substring content 4 idx) (.substring content (+ 3 idx))]))

(defn- prepare-metadata [metadata]
  (reduce (fn [h [_ k v]]
            (let [key (keyword (.toLowerCase k))
                   ;; create a list of keyword tags
                   h (case key
                       :tags (assoc h :keyword-tags (map keyword (clojure.string/split v #" ")))
                       :keywords (assoc h :keyword-keywords (map keyword (clojure.string/split v #" ")))
                       h)]
              (if (not (h key))
                (assoc h key v)
                h)))
          {} (re-seq #"([^:#\+]+): (.+)(\n|$)" metadata)))

(defn- parse-markdown-footnotes [markdown]
  "Since pegdown has no footnotes-support, we will just do
   some replacement on the markdown itself in order to
   generate footnotes.

   This is very limited footnotes support. Right now
   the only markdown footnotes support is using [^name] in the text
   and at the bottom [^name]: text\n. Everything in one line.
   Additions welcome :)"
  (let [fo-reg #"\[\^[a-zA-Z0-9]*?\]\:.+\n"
        fos (re-seq fo-reg markdown)]
    (if (> (count fos) 0)
      ;; create the footnotes
      (let [md1 (reduce (fn [[results markdown] footnote]
                          [(conj results {:text (last (clojure.string/split footnote #":" 2))
                                          :ref (last (re-find #"\[\^([0-9a-zA-Z]+)\]" footnote))
                                          :id (str "#fn" (last (re-find #"\d" footnote)))})
                           (-> markdown
                               ;; replace the original footnote with nothing
                               (clojure.string/replace footnote "")
                               ;; replace the text-footnote with an anchor/name
                               (clojure.string/replace
                                (first (clojure.string/split footnote #":" 2))
                                (format "<sup><a name='fn%s' href='#%s'>%s</a></sup>"
                                        (last (re-find #"\d" footnote)); only the first number
                                        (last (re-find #"\[\^([0-9a-zA-Z]+)\]" footnote))
                                        (last (re-find #"\d" footnote)))))])
                        [[] markdown] fos)]
        md1)
      ;; else return no footnotes
      [[] markdown])))

(defn- read-markdown [file]
  (let [[metadata content]
        (split-file (slurp file :encoding (:encoding (config/config))))
        [footnotes content] (parse-markdown-footnotes content)]
    [(assoc (prepare-metadata metadata) :footnotes footnotes)
     ;(delay (.markdownToHtml (PegDownProcessor. org.pegdown.Extensions/TABLES) content))
     (delay (.markdownToHtml (PegDownProcessor.
                              (int
                               (bit-or org.pegdown.Extensions/TABLES
                                       org.pegdown.Extensions/SMARTYPANTS
                                       org.pegdown.Extensions/AUTOLINKS
                                       org.pegdown.Extensions/FENCED_CODE_BLOCKS)))
                             content))]))

(defn- read-html [file]
  (let [[metadata content]
        (split-file (slurp file :encoding (:encoding (config/config))))]
    [(prepare-metadata metadata) (delay content)]))

(defn- slow-read-org
  [file]
  (if (not (:emacs (config/config)))
    (do (log/error "Path to Emacs is required for org files.")
        (System/exit 0)))
  (let [metadata (prepare-metadata
                  (apply str
                         (take 500 (slurp file :encoding (:encoding (config/config))))))
        command [(:emacs (config/config))
                 "--batch" "--eval" (str "(progn "
                                         (apply str (map second (:emacs-eval (config/config))))
                                         " (find-file \""
                                         (.getAbsolutePath file)
                                         "\") "
                                         (:org-export-command (config/config))
                                         ")")
                 (:emacs-config (config/config))]
        out (println command)
        content (delay (:out (apply sh/sh command)))]
    [metadata content]))

;; We really need to parse each file once. So we memoize the results
(def read-org
  (memoize slow-read-org))

(defn- read-clj [file]
  (let [[metadata & content] (read-string
                              (str \( (slurp file :encoding (:encoding (config/config))) \)))
        evalcontent (binding [*ns* (the-ns 'static.core)]
                      (eval (last content)))]
    [metadata evalcontent]))

(defn- read-cssgen [file]
  (let [metadata {:extension "css" :template :none}
        content (read-string
                 (slurp file :encoding (:encoding (config/config))))
        to-css  #(clojure.string/join "\n" (doall (map cssgen/css %)))]
    [metadata (delay (binding [*ns* (the-ns 'static.core)] (-> content eval to-css)))]))

(defn read-doc [f]
  (let [extension (FilenameUtils/getExtension (str f))]
    (cond (or (= extension "markdown") (= extension "md"))
          (read-markdown f)
          (= extension "md") (read-markdown f)
          (= extension "org") (read-org f)
          (= extension "html") (read-html f)
          (= extension "clj") (read-clj f)
          (= extension "cssgen") (read-cssgen f)
          :default (throw (Exception. "Unknown Extension.")))))

(defn dir-path [dir]
  (cond (= dir :templates) (str (:in-dir (config/config)) "templates/")
        (= dir :public) (str (:in-dir (config/config)) "public/")
        (= dir :site) (str (:in-dir (config/config)) "site/")
        (= dir :posts) (str (:in-dir (config/config)) "posts/")
        :default (throw (Exception. "Unknown Directory."))))

(defn- active-post? [[filename [metadata content]]]
  (if (:inactive metadata)
    nil
    true))

(defn- filter-inactive-posts [posts]
  (let [meta (pmap read-doc posts)
        comb (into [] (zipmap posts meta))
        filtered (filter active-post? comb)
        posts (map first filtered)]
    posts))

(defn list-files [d]
  (let [d (File. (dir-path d))]
    (if (.isDirectory d)
      (sort
       (filter-inactive-posts
       (FileUtils/listFiles d (into-array ["markdown"
                                           "md"
                                           "clj"
                                           "cssgen"
                                           "org"
                                           "html"]) true))) [] )))

(defn template-file [template-name]
  (let [full-path (str (dir-path :templates) template-name)
        file (File. full-path)]
    (when (not (.exists file))
      (log/warn "Template does not exist: " full-path))
    file))

(def read-template
  (memoize
   (fn [template]
     (let [extension (FilenameUtils/getExtension (str template))]
       (cond (= extension "clj")
             [:clj
              (-> (template-file template)
                  (#(str \(
                         (slurp % :encoding (:encoding (config/config)))
                         \)
                         ))
                  read-string)]
             :default
             [:html
              (string-template/load-template (dir-path :templates) template)])))))

(defn write-out-dir [file str]
  (FileUtils/writeStringToFile
   (File. (:out-dir (config/config)) file) str (:encoding (config/config))))

(defn deploy-rsync [rsync out-dir host user deploy-dir]
  (let [cmd [rsync "-avz" "--delete" "--checksum" "-e" "ssh"
             out-dir (str user "@" host ":" deploy-dir)]]
    (log/info (:out (apply sh/sh cmd)))))
