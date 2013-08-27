Introduction
============

The project integrates Jenkins continuous integration server (http://jenkins-ci.org/) and Reviewboard code review tool (http://www.reviewboard.org/). Jenkins will pick a diff submitted to Reviewboard, run a build, and report build results on the review request: either blessing the request or warning that it will break mainline if committed. This essentially allows to validate commits before they reach mainline, a feature available in GitHub pull requests (CloudBees), TeamCity and Gerrit. Unlike these tools, this project supports any source control system that Reviewboard supports (Git, SVN, Perforce), any hosting, and will require only Jenkins server configuration. 

Presentation: http://www.cloudbees.com/jenkins/juc2013/juc2013-israel-abstracts.cb#YardenaMeymann

User Guide
==========

* Install Patch-Parameter plugin (https://wiki.jenkins-ci.org/display/JENKINS/Patch+Parameter+Plugin), it is required for this plugin to work.
* Install this plugin
* Configure your reviewboard instance (URL, username, password) in Jenkins settings. It is recommended to test the connection before saving changes.
* Copy the job you want to use with this plugin, or create a new one. Add a parapeter called "review url"
* Also add a post-build action "Post build result to reviewboard", you can select "ship it" if you want Jenkins to mark successfully build diffs. 
* Now you can run the job providing it the url of the review request

For more details see https://wiki.jenkins-ci.org/display/JENKINS/Jenkins-Reviewbot

Contributions
==============
Contact me (ymeymann at vmware dot com) for contribution license agreement
