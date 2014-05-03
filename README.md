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
- View a compact list of all posts ('archive')
- Create, edit, preview, and delete blog posts
- Add tags to blog posts; users can see all posts for a tag
- Log in as administrator to manage blog

## To Be Done

### Technical

- Figure out why save "fails" occasionally first time after inactivity (may have to do with "1 changes in 3600 seconds" autosave on Redis' end...)
- Persistent sessions and better session security
- Add a system for logging error or warning data to a file

### Features

- Less eye-bleedingly ugly CSS and HTML
- Saving draft posts
- About page
- Curated links feature
- Smart image support (support for uploading and deleting images in blog post editor, thumbnail previews)
- Possible comment support (or use a third-party solution)

## License

Copyright © 2014 Austin Zheng. Released under the terms of the MIT License.
