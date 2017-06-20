(ns static.config
  (:require [clojure.tools.logging :as log])
  (:import (java.io File)))

(def emacs-executable
  "Emacs executable path."
  ^:private
  (clojure.string/trim (:out (clojure.java.shell/sh "which" "emacs"))))

(let [defaults {:site-title "A Static Blog"
                ;; The extension for older pages.
                :site-title-page " - Page %s"
                ;; The extension for tags.
                :site-title-tag " - Tag %s"
                :site-description "Default blog description"
                :site-url "https://github.com/terhechte/static"
                :site-default-keywords "tag1 tag2 tag3 tag3"
                :site-author ""
                :in-dir "resources/"
                :out-dir "html/"
                :default-template "default.clj"
                :list-template "list.clj"
                :base-template "base.clj"
                :default-extension "html"
                :encoding "UTF-8"
                :posts-per-page 2
                :blog-as-index true
                :create-archives true
                :archives-title "Archives"
                ;; For the individual month.
                :archives-title-month " - %s"
                ;; the output dateformat for posts.
                :date-format-post "E, d MMM yyyy"
                ;; The date format for the rss feed.
                :date-format-rss "E, d MMM yyyy HH:mm:ss Z"
                ;; The output dateformat for archive links.
                :date-format-archive "MMMM yyyy"
                ;; If this is 0, include all content in the rss,
                ;; otherwise limtit to the given amount of chars.
                :rss-description-char-limit 120
                :emacs emacs-executable
                :emacs-config " -q "
                :org-export-command '(princ
                                      (org-no-properties
                                       (with-current-buffer
                                         (org-html-export-as-html nil nil nil t nil)
                                         (buffer-string))))}]

  (def config
    (memoize
     #(try
        (let [config (apply hash-map (read-string (slurp (File. "config.clj"))))]
          ;; If emacs key is set make sure executable exists.
          (when (:emacs config)
            (if (not (.exists (File. (:emacs config))))
              (do (log/error "Path to Emacs not valid.")
                  (System/exit 0))))
          (merge defaults config))
        (catch Exception e (do
                             (log/info "Configuration not found using defaults.")
                             defaults))))))

(defn set!-config [k v]
  (alter-var-root (find-var 'static.config/config) (fn [c] #(identity (assoc (c) k v)))))
