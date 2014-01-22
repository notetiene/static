(ns static.test.core
  (:use [static.core] :reload)
  (:use [static.io] :reload)
  (:use [static.test.dummy-fs] :reload)
  (:use [clojure.test])
  (:import (java.io File)
           (org.apache.commons.io FileUtils)))

(defn delete-file-recursively [f]
  (FileUtils/deleteDirectory f))

(defn dummy-fs-fixture [f]
  (setup-logging)
  (create-dummy-fs)
  (create)
  (f)
  (delete-file-recursively (File. "resources/"))
  (delete-file-recursively (File. "html/"))
  (.delete (File. "config.clj")))

(use-fixtures :once dummy-fs-fixture)

(deftest test-markdown
  (let [[metadata content] (read-doc "resources/site/dummy.markdown")] 
    (is (= "unit test"  (:tags metadata)))
    (is (= "some dummy desc" (:description metadata)))
    (is (= "dummy content" (:title metadata)))
    (is (= "Some dummy file for unit testing."
	   (re-find #"Some dummy file for unit testing." @content)))))

; I've removed the org test, as it requires the corret setup of emacs
;(deftest test-org
;  (let [[metadata content] (read-doc (File. "resources/posts/2050-07-07-dummy-future-post-7.org"))] 
;    (is (= "org-mode org-babel"  (:tags metadata)))
;    (is (= "Dummy org-mode post" (:title metadata)))
;    (is (= "Sum 1 and 2" (re-find #"Sum 1 and 2" @content)))))

(deftest test-clj
  (let [[metadata content] (read-doc (File. "resources/site/dummy_clj.clj"))] 
    (is (= "Dummy Clj File" (:title metadata)))
    (is (= 2 (count content)))
    (is (= "dummy future post 8" (:title (first content))))))

(deftest test-io
  (is (= (count (list-files :posts)) 8))
  (is (.exists (File. "html/first-alias/index.html")))
  (is (.exists (File. "html/a/b/c/alias/index.html")))
  (is (.exists (File. "html/second-alias/index.html"))))

(deftest test-rss-feed
  (let [rss (File. "html/rss-feed")
	content (slurp rss)] 
    (is (= true (.exists rss)))
    (is (= "<title>Dummy Site</title>"
    	   (re-find #"<title>Dummy Site</title>" content)))
    (is (= "<link>http://www.dummy.com</link>"
    	   (re-find #"<link>http://www.dummy.com</link>" content)))
    (is (= "<title>dummy future post 1</title>"
    	   (re-find #"<title>dummy future post 1</title>" content)))
    (is (= "http://www.dummy.com/2050/04/04/dummy-future-post-4/"
	   (re-find #"http://www.dummy.com/2050/04/04/dummy-future-post-4/" 
		    content)))))

(deftest test-site-map
  (let [sitemap (File. "html/sitemap.xml")
	content (slurp sitemap)] 
    (is (= true (.exists sitemap)))
    (is (= "<loc>http://www.dummy.com</loc>"
    	   (re-find #"<loc>http://www.dummy.com</loc>" content)))
    (is (= "http://www.dummy.com/2050/01/01/dummy-future-post-1/"
    	   (re-find #"http://www.dummy.com/2050/01/01/dummy-future-post-1/" 
		    content)))
    (is (= "<loc>http://www.dummy.com/dummy.html</loc>"
    	   (re-find #"<loc>http://www.dummy.com/dummy.html</loc>" 
		    content)))))

(deftest test-tags
  (let [tags (File. "html/tags/25e9/index.html")
	content (slurp tags)] 
    (is (= 5 (count ((tag-map) "same"))))
    (is (= true (.exists tags)))
    (is (= "<h1>dummy future post 2</h1>" 
	   (re-find #"<h1>dummy future post 2</h1>" content)))
    (is (= "Dummy Site - Tag 25e9"
    	   (re-find #"Dummy Site - Tag 25e9" 
		    content)))))

(deftest test-latest-posts
  (let [page (File. "html/latest-posts/0/index.html")] 
    (is (= true (.exists page)))))

(deftest test-archives
  (let [index (File. "html/archives/index.html")] 
    (is (= true (.exists index)))))

(deftest test-process-posts
  (let [post1 (File. "html/2050/02/02/dummy-future-post-2/index.html")
	post2 (File. "html/2050/04/04/dummy-future-post-4/index.html")] 
    (is (= true (.exists post1)))
    (is (= true (.exists post2)))))

(deftest test-tag-sidebar
  (let [content (tag-sidebar-list)
        entry (first content)]
    (is (= "25e9" (:tag entry)))
    (is (= 1 (:count entry)))
    (is (= 13 (count content)))))

(deftest test-project-sidebar
  (let [content (project-sidebar-list)]
    (is (= 3 (count content)))
    (is (= "dummy content" (:project (first content))))))

(deftest test-enhance-meta
  (let [org-meta {:tags "tag1 tag2 tag3" :keywords "tag3 tag4 tag5" :author "Dr. No"}
        enh-meta (enhance-metadata org-meta)]
    (is (= "tag1, tag2, tag3, tag4, tag5" (:tags enh-meta)))
    (is (= "tag3 tag4 tag5" (:keywords enh-meta)))
    (is (= "Dr. No" (:author enh-meta)))
    (is (= "Dummy Site" (:site-title enh-meta)))
    (is (= 13 (count (:categories enh-meta))))
    (is (= 3 (count (:projects enh-meta))))))

(deftest test-process-site
  (let [html (File. "html/dummy.html")
	static (File. "html/dummy.static")] 
    (is (= true (.exists html)))
    (is (= true (.exists static)))
    (is (= "<h1>dummy content</h1>"
	   (re-find #"<h1>dummy content</h1>" (slurp html))))
    (is (= "Hello, World!!" (re-find #"Hello, World!!" (slurp static))))))
