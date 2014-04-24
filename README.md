# clojure-firefly

A lightweight personal blog and link curation engine, implemented in (surprise) Clojure.

## Prerequisites

You will need [Leiningen][1] 1.7.0 or above installed.

As well, you need a default instance of Redis running on the local machine. The exact configuration may change as development continues.

[1]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein ring server

## Features

- Integration with redis
- Create, edit, and delete blog posts
- View a single blog post
- View a configurable range of blog posts
- Rudimentary access control for blog admin tools

## To Be Done

### Technical

- Better delineation of function purpose (e.g. get-blog is explicitly responsible for creating the request body)
- Properly implement authentication (right now username/password are hardcoded)
- Persistent sessions and better session security
- Better template definitions (e.g. no longer set 'unused' attribute as an ugly hack to simulate a no-op)
- Use 'flash' middleware to provide better user experience after performing action
- Properly handle error conditions rather than displaying placeholder error string

### Features

- Less eye-bleedingly ugly CSS and HTML
- Admin page and/or better support for admin tools
- Blog archive page (list of all blog entries by title)
- Editor preview
- About page
- Curated links feature
- Tags for blog posts and links
- Smart image support (support for uploading and deleting images in blog post editor, thumbnail previews)
- Possible comment support (or use a third-party solution)

## License

Copyright © 2014 Austin Zheng. Released under the terms of the MIT License.
