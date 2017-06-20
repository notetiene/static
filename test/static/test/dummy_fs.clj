(ns static.test.dummy-fs
  (:import (java.io File)))


(def emacs-executable
  "Emacs executable path."
  ^:private
  (clojure.string/trim (:out (clojure.java.shell/sh "which" "emacs"))))

(defn- create-resources []
  (.mkdir (File. "resources/"))
  (.mkdir (File. "resources/site/"))
  (.mkdir (File. "resources/public/"))
  (.mkdir (File. "resources/posts/"))
  (.mkdir (File. "resources/templates/")))

(defn- create-site []
  (spit (File. "resources/site/dummy.markdown")
	"---
title: dummy content
description: some dummy desc
tags: unit test
---

Some dummy file for unit testing.")

  (spit (File. "resources/site/style.cssgen")
        "[[:body :font-size :1em]]")
  
  (spit (File. "resources/site/dummy_clj.clj")
	"{:title \"Dummy Clj File\"}
(map #(static.core/create-post-meta %) (take 2 (reverse (static.io/list-files :posts))))")
)

(defn- create-dummy-posts []
  (spit 
   (File. "resources/posts/2050-01-01-dummy-future-post-1.markdown")
   "---
TITLE: dummy future post 1
TAGS: 4673 9c0e same
TEMPLATE: temp.clj
---

text dummy post 1")

  (spit 
   (File. "resources/posts/2050-02-02-dummy-future-post-2.markdown")
   "---
title: dummy future post 2
tags: e8edaab7 25e9 same
template: temp.clj
---

text dummy post 2")

  (spit 
   (File. "resources/posts/2050-03-03-dummy-future-post-3.markdown")
   "---
title: dummy future post 3
tags: 45f5 8a0c same
---

text dummy post 3")

  (spit 
   (File. "resources/posts/2050-04-04-dummy-future-post-4.markdown")
   "---
title: dummy future post 4
tags: 4784d643 e4e8 same
template: temp.clj
alias: [\"/first-alias/index.html\", \"/second-alias/index.html\"]
---

text dummy post 4")

  (spit 
   (File. "resources/posts/2050-05-05-dummy-future-post-5.markdown")
   "---
title: dummy future post 5
tags: 6662
published: false
---

Should be skipped...")

  (spit 
   (File. "resources/posts/2050-06-06-dummy-future-post-6.html")
   "---
title: org-jekyll entry
on: <2050-06-06 Sat>
template: temp.clj
extra: first
CATEGORY: test
---

<div id=\"outline-container-1\" class=\"outline-2\">
<h2 id=\"sec-1\"><a href=\"test.html\">First blog entry </a></h2>
<div class=\"outline-text-2\" id=\"text-1\">

<p>With some content in the first entry. 
</p></div>
</div>")

  (spit 
   (File. "resources/posts/2050-07-07-dummy-future-post-7.org")
   "#+title: Dummy org-mode post
#+tags: org-mode org-babel
#+template: temp.clj

Sum 1 and 2

#+BEGIN_SRC clojure

(+ 1 2)

#+END_SRC

")

(spit 
 (File. "resources/posts/2050-08-08-dummy-future-post-8.org")
 "
#+title: dummy future post 8
#+tags: 45f5 8a06 same
#+alias: [\"/a/b/c/alias/index.html\"]

org alias test"))

(defn- create-html-template []
  (spit (File. "resources/public/_index.html") "
<html>
<head><title>title</title></head>
<body>
<div id='content'>
<article><h1>text</h1><div>content</div></article>
<div id='list'>
<h1>title</h1>
<div>entry</div>
</div>
</div>
</body>
</html>
"))

(defn- create-base-template []
  (spit (File. "resources/templates/base.clj") "(def base-template-file (static.core/template-path \"_index.html\"))

(enlive/defsnippet article-template base-template-file [:article]
  [{:keys [title url]}]
    [:h1] (enlive/content title)
    [:div] (enlive/content url)
    )

(enlive/defsnippet list-template base-template-file [:#list]
  [[headline content]]
  [:h1] (enlive/content headline)
  [:div] (enlive/content (map #(article-template %) content))
  )
"))

(defn- create-template []
  ; Create the default template, that creates the selectors (for a better example have a look at my blog repo)
  (spit (File. "resources/templates/temp.clj") "
(define-template base-template-file
     [:head :title] (enlive/content (if-let [t (:title metadata)] t (:site-title metadata)))
     [:#content] (enlive/content (map #(article-template %) content))
     [:#list] nil
  ) "))

(defn- create-list-template []
  (spit (File. "resources/templates/list.clj") "(define-template base-template-file
     [:head :title] (enlive/content (if-let [t (:title metadata)] t (:site-title metadata)))
     [:#content] (enlive/content (map #(list-template %) content))
     ;[:#list] nil
  )"))

(defn- create-static-file []
  (spit (File. "resources/public/dummy.static") "Hello, World!!"))

(defn- create-config []
  (spit (File. "config.clj")
        (format "[:site-title \"Dummy Site\"
 :site-description \"Dummy Description\"
 :site-url \"http://www.dummy.com\"
 :in-dir \"resources/\"
 :out-dir \"html/\"
 :default-template \"temp.clj\"
 :list-template \"list.clj\"
 :encoding \"UTF-8\"
 :posts-per-page 2
 :blog-as-index true
 :emacs \"%s\"]" emacs-executable)))

(defn create-dummy-fs []
  (create-resources)
  (create-site)
  (create-static-file)
  (create-dummy-posts)
  (create-base-template)
  (create-html-template)
  (create-template)
  (create-list-template)
  (create-config))
