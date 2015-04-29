An Atlassian plugin that contains Confluence macros to generate the Jenkins Plugin information summary in the Jenkins Wiki.

To develop the plugin:
* install [Atlassian Plugin SDK](https://developer.atlassian.com/docs/getting-started/set-up-the-atlassian-plugin-sdk-and-build-a-project)
* run [atlas-run](https://developer.atlassian.com/docs/developer-tools/working-with-the-sdk/command-reference/atlas-run)
 
# Deploying Plugin
Wiki administrator can build this plugin via `mvn install`,
go to [plugin installation screen](https://wiki.jenkins-ci.org/plugins/servlet/upm#install),
then click "Upload Plugin" and upload the jar file. Changes will be visible instantly.