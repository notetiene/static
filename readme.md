# Introduction:

Static is a Static Site Generator written in Clojure. This is a fork of the original static <http://nakkaya.com/static.html> from Nurullah Akkaya. I chose Nurullah's Static because it had lots of great IO features, but had to fork it because it differet in a couple of ways from what I wanted my blog to be:

-   Have one (or several) html files as templates that I do not need to break up into parts and that are not plastered with weird template tags. So I'm using Enlive to define selectors and the generator uses theses selectors to fill the content into the html structure. That makes it really really easy to edit the html for the blog without having to go the html -> haml/whatever -> break up -> &#x2026; route.

-   Implement a strong seperation between layout and code: The normal Static generator mixes design/layout with clojure code (within the project files, and in the generator source itself). This makes it difficult to implement a new layout easily and use it for anything but the original envisioned layout. What's more, I wanted to define my layout in HTML and not have to edit multiple Clojure files to make it work.

-   Allow different templates for the different possible views that A blog contains. I.e. a template for list views, a template for a blog post, and a template for multiple blog posts.

# Demo:

[My personal blog is being run with Static](http://appventure.me) so if you want to see what it looks like, hop over. Also, [here is the repository / configuration for this blog](https://github.com/terhechte/appventure-blog).

# Features:

-   **Template are all HTML**: Without any templating language or breaking it into pieces in between. In fact, the html templates are stored in the public/ folder with all your CSS and images and Javascript, so just fire up a 'python -m SimpleHTTPServer' and you can edit your templates as if they were an actual site. Once you're done, simply run the build process and it is finished.

-   **Org Mode Support**: This may not be interesting for most people, but I really dig Emacs Org Mode for document editing. Static has terrific Org Mode support. For an example of how to set up your environment, see below

-   **Projects**: Every document put into the site/ folder will be added to the list of projects that can be accessed in a template. This allows you to create a small document for any page you'd like to host on your blog. It can then be easily included into your navigation. Currently, this works only well for HTML, Markdown and Org Mode documents.

-   **Tags**: Every document can be tagged, and the tags can be listed on the site, and link to tag archives

-   **Archives**: An archive can automatically be generated for all content. You have the option of having a seperate archive page for each month or having just one huge archive list.

-   **Just two templates**: There're just two templates that need to be defined, a list template (for archives and tag lists) and a content template (for one or multiple posts). And they can even be the same if you wish.

-   **Footnotes in Markdown**: There's a bit of support for footnotes in Markdown, however since I'm mostly using Org Mode, which automatically inserts the markup for them, I never went beyond the basics.

-   **Compilable to jar file**: This means that you can compile it once, and whether you update your operating system, or switch to a different platform, or re-install something or change your environment, the generator will work - as long as you have Java installed. I find this much better to generators like, say, Jekyll, where it (for me) often failed when I updated Ruby or installed a new Gem (or really changed anything).

# Usage:

## Dependencies

Static requires the [leiningen](https://github.com/technomancy/leiningen) build tool. Once installed, you can
build Static with the following commands from within the project
directory structure:

    $ lein deps
    $ lein uberjar

## Running

Creating a static site usually involves the following,

-   Set up the basic structure of the site

-   Create some posts

-   Run your site locally to see how it looks

-   Deploy your site

A basic static site usually looks something like this:

    .
    |-- config.clj
    `-- resources
       |-- posts
       |   |-- 2009-04-17-back-up-and-restore-a-mysql-database.org
       |   |-- 2010-02-10-a-simple-clojure-irc-client.markdown
       |   `-- 2010-08-02-using-clojure-contrib-generic-interfaces.markdown
       |-- public
       |   `-- 404.html
       |   `-- _index.html
       |-- site
       |   `-- contact.markdown
       |   `-- fantastic_project.org
       `-- templates
           `-- default.clj
           `-- base.clj
           `-- list.clj

An overview of what each of these does:

### config.clj

Contains a vector of configuration options.

-   :site-title - Title of the site.

-   :site-title-page - The title extension for older pages

-   :site-title-tag - The title extension for tag pages

-   :site-description - Description of the site.

-   :site-url - URL of the site.

-   :site-default-keywords - The keywords that will be added to every site/page/post

-   :site-author - The name of the author

-   :copyright-year - Year the pages were created/copyrighted.

-   :in-dir - Directory containing site content by default *resources/*

-   :out-dir - Directory to save compiled files.

-   :atomic-build - When set, will build the site on a temporary
    directory first then move that directory to :out-dir.

-   :default-template - Default template to use. For Articles and Article lists. Has to be a Clojure file

-   :list-template - Template for list pages like tag or archives. Clojure file

-   :base-template - The base template contains function definitions for the list and default templates to avoid repetitions.

-   :encoding - Encoding to use for read write operations.

-   :posts-per-page - Number of posts in latest post pages.

-   :archives-title - The title for archives

-   :create-archives - Create archives?

-   :archives-title-month - The month addition for archive, if you're generating monthly archive index pages

-   :date-format-post - The output date format for posts

-   :date-format-rss - The output date format for rss entries

-   :date-format-archive - The output date format for archive links

-   :blog-as-index - If true use blog as index, meaning automatically create index.html with the recent posts

-   :emacs - path to emacs if you want to render .org files.

-   :emacs-eval - elisp code to be evaluated on the emacs process.

-   :host - remote host to deploy to.

-   :user - remote username

-   :deploy-dir - Remote directory to deploy to.

-   :rsync - path to rsync if you want to deploy with rsync.

The variables can later be reused (for example in your default template) as:

    (:site-author (static.config/config))

### posts/

Folder containing posts, the format of these files are important, as
named as *YEAR-MONTH-DAY-title.MARKDOWN*.

### public/

Folder containing static data such as images, css, javascript etc.
Folder structure will be mirrored exactly.

### site/

Folder containing pages that are not posts.

### templates/

Folder containing templates that are used to render posts and pages
with. See below

## Markup

Supported markup languages for posts / sites

-   markdown

-   org-mode (via emacs)

-   html

### Setting per page/post settings

Setting the template, title etc, for a page/post is done using a
header placed at the top of the file,

1.  org-mode

        #+title: Blogging Like a Hacker
        #+tags: clojure

2.  Markdown

        ---
        template: temp-en.clj
        title: Blogging Like a Hacker
        ---

### Page/Post Settings

-   template - If set, this specifies the template file to use. Use the
    layout file name with file extension. Layout files must be
    placed in the **templates** directory.

-   title - Override the use of default title.

-   alias - Generates redirect pages for posts with aliases
    set. (["/first-alias/index.html", "/second-alias/index.html"])
    Place the full path of the alias (place to redirect from) inside
    the destination post.

Any other setting you provide can be accessed from within your
template.

## Installation

You need to place the uberjar lein created to the folder containing
config.clj.

### Building the site

    java -jar static-app.jar --build

### Testing the site

You can test the site locally using jetty, which will launch on <http://localhost:8080>. 
The site will rebuild if you change any of the source files.

    java -jar static-app.jar --watch

In order to run just jetty,

    java -jar static-app.jar --jetty

## Templates:

Templating is done via html templates coupled with enlive clojure scripts. This allows to layout / design in html and just bind the date by adding a couple of DOM selectors. I find this much easier than having to split a template into seperate pieces like in traditional templating languages.

Templating consists out of multiple parts:

-   The html template file, usually \_index.html in public/

-   The base template file, that contains all the definitions, snippets, and more that you need in your templates

-   The default and list templates which are lists of html selectors and actions which are then performed against the results.

Since all templating is done via [Enlive](https://github.com/cgrand/enlive) it may be beneficial to have a look at it first. [Here's a fantastic tutorial](https://github.com/swannodette/enlive-tutorial/) from David Nolen. However, the example below should be simple enough to understand it even without having a look at Enlive first.

With that in mind, lets see a simple example:

**public/<sub>index</sub>.html**

``` html
    <html>
    <head><title>title</title></head>
    <body>
        <div id='content'>
            <article>
                <h1>text</h1>
                <div>content</div>
            </article>
            <div id='list'>
                <h1>title</h1>
                <div>entry</div>
            </div>
        </div>
    </body>
    </html>
```

This is straightforward html and a simple model to explain templating. When this is being used to render the **index.html** of our blog, we want the following to happen.

1.  Replace "<title>title</title>" with the site title from our metadata

2.  Remove the #list element as the index is not a list page (unlike tag list or archives)

3.  Use the <article> entry and clone it for each of our posts replacing h1 with the title and <div>content</div> with the content.

Enlive selectors are a little bit different than CSS selectors, but still easy to graps, so first lets see which selectors we need:

1.  [:head :title]: That's it, and in there we just want to replace everythign

2.  [:#list]: That's it, and we just want to remove what we find

3.  This is a bit more tricky, we first want [:div#content] to select our container div, but then we want to fill it with an instance of our <article> html structure for each item we have in our post contents.

Static always binds two variables to the template scope: "metadata" and "content". See below for an explanation of what they do.

Enlive works in a way where you define a selector and then an operation that has to be performed on the result of that selector, so now we will create a simple base template to define snippets for the article entries. A snipped is a piece of html from a template that you can clone / use multiple times, just what we need for our article.

**templates/base.clj**

``` Clojure
    ; Static offers a function that translates the name of your html template to the correct path
    ; We bind this to a var so we can access it easily
    (def base-template-file (static.core/template-path \"_index.html\"))
    
    ; This is the snipped for our article template. It will grab the releveant <article> portion for us
    ; and apply the contents to the h1 and the div
    (enlive/defsnippet article-template  ; the name of our snippet, this is later available as a function
    base-template-file ; which html template do we want to grab this from?
    [:article] ; the selector for the html that we intend to grab
      [{:keys [title url]}] ; this snippet will be called with a Post instance. Posts are Maps with keys. we just use title and url
    
        ; we tell enlive to replace the 'content' of the :h1 tag with the contents of the title var
        [:h1] (enlive/content title) 
    
        ; we tell enive to replace the 'content' of the :div tag with the contents of the url var
        [:div] (enlive/content url) 
        )
```

We're almost done. We have defined our article snippet, now we just need to maps this snippet against all the posts that we have.
This is being done in our default template.

**templates/default.clj**

``` Clojure
    ; The define-template is a macro in core.clj that helps us define simple templates
    (define-template base-template-file
      ; We replace the title contents with our site title, or, with the post title, if the author define one
      [:head :title] (enlive/content (if-let [t (:title metadata)] t (:site-title metadata)))
    
      ; We replace the contents of the #content node with the results of mapping our earlier-defined article function against our content
      [:#content] (enlive/content (map #(article %) content))
    
      ; And finally, we remove the list, as we don't need it. Returning nil for an element will get rid of it
      [:#list] nil
    )
```

That's it! Once these selectors are in place, you're done and your content will be written out. Here're some helpful tips on how to write good selectors:

-   Try to be very specific, otherwise future changes in your html require you to re-write your selectors afterwards. I.e. rather use [:#content] instead of [:body :> :div#container :> :div#content]. In the later case, once you decide to remove the div#container at some point in the future, the selector will fail.

-   The format of the selectors is always [selector] (action) so you can't just do a println for debugging. An easy way to do that, though, is by including it in a useless selector: [:head] #(when "1" (println content) %) will just replace :head with :head and print content as a side effect.

### Metadata

The Metadata is a list of template metadata which has the following attributes:

-   categories: A list of Maps where each Map is a tag that posts have been tagged with. The Map has the keys :tag - Tag Name, :url - The url to this tag list, :count - How many posts are there for this tag

-   projects: A list of dictionaries for the documents that are available under site. The Map has the keys :project - the name of the project, :link the link to the project

-   config: Directly accessing the configuration structure from config.clj. I.e. (:site-author (:config metadata))

-   pager: A Map with pager links for the index page. It can have two entries, :older and :newer. If one of them exists, then the value is the link to the respective older / newer page

-   tags: A string containing a mgerged list of post / page tags and the default site tags.

-   site-title: The default site / blog title from the config

-   title: The title of the current document / post (if there is on

-   type: Is :post for a post and :site for a document

### Content

The content structure differs based on list templates and non list templates. For the normal templates it is simple a vector of post dictionaries (see below). For list templates, it is a vector of lists, and each list has two member, the first is the title / headline for the current list group (i.e. January, February for the archive or #tag1 #tag2 for the tags) and the second is a vector of posts that belong to this list group:

-   default content: [{post1} {post2} &#x2026;]

-   list content [ [ tag1 [{post1} {post2} {post3}] ] [ tag2 [{post3} {post1} &#x2026;] ] ]

### Posts

Posts are Maps with the following attributes:

-   id: A dynamic, distinct id for each entry, you can use for javascript selectors if you want to do fancy stuff with each article

-   title: The title of the entry

-   content: The html content of the entry

-   url: The url of the entry in your blog

-   tags: The tags that were assigned to this entry

-   footnotes: If you're using Markdown and defining footnotes, they'll be made available in here.

-   date: A string with the date when this post was created

-   javadate: A java date object in case you want to perform date manipulations

# Caveats

-   This is my first take on a big Clojure project after reading two books and dabbling around with the repl the code may not be good, so if you find something atrocious, just send a pull request.

-   The way that I implemented the enlive templates with a base template and additional templates feels kinda awful. With my limited Clojure knowledge I couldn't really come up with anything else that would allow me to eval a template and allow it to import additional functions from another file to minimize repetition.

-   This is only tested for my personal blog, it may be that if you're trying to do something else, it doesn't work for that, in that case, you're welcome to fork :)

-   This started out as a proof-of-concept and turned out to be working so well that I decided to release it so maybe others can use it too. This means that the git commit history could certainly be cleaner. There's one huge commit that brings in a ton of changes.

# Emacs Org Mode

If you're interested in blogging with Emacs Org Mode, you should have a look at the configuration options in my blog configuration. There, I included a specific empty Emacs.d in order to actually get things running, otherwise it simply didn't work. This is optimized for Emacs 24.3.x.

I'm basically starting the command line Emacs with "-q" and then evaluating the following EmacsLisp in the first line: "(setq user-emacs-directory "resources/emacs/")". This will tell Emacs to not look in ~/.emacs.d but in resources/emacs so it will not load all the packages you have installed but only what you actually need it to. And that is added via seperate add-to-list calls:

``` Lisp
    '(setq user-emacs-directory "resources/emacs/")
    '(setq vc-handled-backends ())
    '(add-to-list 'load-path "~/.emacs.d/org-mode/lisp")
    '(add-to-list 'load-path "~/.emacs.d/org-mode/contrib/lisp")
    '(add-to-list 'load-path "resources/emacs/htmlize-20130207.2102/")
    '(add-to-list 'load-path "resources/emacs/soothe-theme-20130805.1700/")
    '(require 'htmlize)
    '(require 'org)
    '(require 'ob)
```

# License

Distributed under the Eclipse Public License, the same as Clojure.
