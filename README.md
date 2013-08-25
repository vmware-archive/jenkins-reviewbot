Introduction
============

The project integrates Jenkins continuous integration server (http://jenkins-ci.org/) and Reviewboard code review tool (http://www.reviewboard.org/). Jenkins will pick a diff submitted to Reviewboard, run a build, and report build results on the review request: either blessing the request or warning that it will break mainline if committed. This essentially allows to validate commits before they reach mainline, a feature available in GitHub pull requests (CloudBees), TeamCity and Gerrit. Unlike these tools, this project supports any source control system that Reviewboard supports (Git, SVN, Perforce), any hosting, and will require only Jenkins server configuration. 

Presentation: http://www.cloudbees.com/jenkins/juc2013/juc2013-israel-abstracts.cb#YardenaMeymann

User Guide
==========

I plan to make a binary version of the plugin available, but for now you need to build the plugin from source. It's easy:

    mvn --settings jenkins-settings.xml clean install -DskipTests=true

* Install Patch-Parameter plugin (https://wiki.jenkins-ci.org/display/JENKINS/Patch+Parameter+Plugin), it is required for this plugin to work.
* Add this plugin to your Jenkins (Plugins, Upload, ... jenkins-reviewbot.hpi).
* Configure your reviewboard instance (URL, username, password) in Jenkins settings. It is recommended to test the connection before saving changes.
* Copy the job you want to use with this plugin, or create a new one. Add a parapeter called "review url"
* Also add a post-build action "Post build result to reviewboard", you can select "ship it" if you want Jenkins to mark successfully build diffs. 
* Now you can run the job providing it the url of the review request

My next task is to add support for automatic polling of Reviewboard, in the meantime you can use curl to trigger it:

    JENKINS=...
    JOBNAME=...
    USER=...
    PASSWORD=...
    REVIEW=... e.g. https://rb.vmware.com/r/12345/
    curl -G -u $USER:$PASSWORD -d delay=0sec --data-urlencode review.url=$REVIEW $JENKINS/job/$JOBNAME/buildWithParameters
