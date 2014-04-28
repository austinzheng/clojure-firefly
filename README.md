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
- View either a single blog post or any range of posts
- Create, edit, preview, and delete blog posts
- Log in as administrator to manage blog

## To Be Done

### Technical

- Properly implement authentication (right now username/password are hardcoded)
- Persistent sessions and better session security

### Features

- Less eye-bleedingly ugly CSS and HTML
- Blog archive page (list of all blog entries by title)
- Saving draft posts
- About page
- Curated links feature
- Tags for blog posts and links
- Smart image support (support for uploading and deleting images in blog post editor, thumbnail previews)
- Possible comment support (or use a third-party solution)

## License

Copyright © 2014 Austin Zheng. Released under the terms of the MIT License.
