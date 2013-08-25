jenkins-reviewbot
=================

The project integrates Jenkins continuous integration server (http://jenkins-ci.org/) and Reviewboard code review tool (http://www.reviewboard.org/). Jenkins will pick a diff submitted to Reviewboard, run a build, and report build results on the review request: either blessing the request or warning that it will break mainline if committed. This essentially allows to validate commits before they reach mainline, a feature available in GitHub pull requests (CloudBees), TeamCity and Gerrit. Unlike these tools, this project supports any source control system that Reviewboard supports (Git, SVN, Perforce), any hosting, and will require only Jenkins server configuration. 

Presentation: http://www.cloudbees.com/jenkins/juc2013/juc2013-israel-abstracts.cb#YardenaMeymann
