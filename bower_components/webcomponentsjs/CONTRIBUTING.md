# Contributing

Want to contribute to webcomponents.js? Great!

We are more than happy to accept external contributions to the project in the form of [bug reports](../../issues) and pull requests.

## Contributor License Agreement

Before we can accept patches, there's a quick web form you need to fill out.

- If you're contributing as an individual (e.g. you own the intellectual property), fill out [this form](http://code.google.com/legal/individual-cla-v1.0.html).
- If you're contributing under a company, fill out [this form](http://code.google.com/legal/corporate-cla-v1.0.html) instead.

This CLA asserts that contributions are owned by you and that we can license all work under our [license](LICENSE).

Other projects require a similar agreement: jQuery, Firefox, Apache, Node, and many more.

[More about CLAs](https://www.google.com/search?q=Contributor%20License%20Agreement)

## Initial setup

1. Setup Gulp: `sudo npm install -g gulp`
1. Fork the project on github and pull down your copy.
   > replace the {{ username }} with your username and {{ repository }} with the repository name

        git clone git@github.com:{{ username }}/{{ repository }}.git

1. Test your change results in a working build.
   > in the repo you've made changes to, try generating a build:

        cd $REPO
        npm install
        gulp

The builds will be placed into the `dist/` directory if all goes well.

1. Commit your code and make a pull request.

That's it for the one time setup. Now you're ready to make a change.

## Submitting a pull request

We iterate fast! To avoid potential merge conflicts, it's a good idea to pull from the main project before making a change and submitting a pull request. The easiest way to do this is setup a remote called `upstream` and do a pull before working on a change:

    git remote add upstream git://github.com/polymer/webcomponentsjs.git

Then before making a change, do a pull from the upstream `master` branch:

    git pull upstream master

To make life easier, add a "pull upstream" alias in your `.gitconfig`:

    [alias]
      pu = !"git fetch origin -v; git fetch upstream -v; git merge upstream/master"

That will pull in changes from your forked repo, the main (upstream) repo, and merge the two. Then it's just a matter of running `git pu` before a change and pushing to your repo:

    git checkout master
    git pu
    # make change
    git commit -a -m 'Awesome things.'
    git push

Lastly, don't forget to submit the pull request.