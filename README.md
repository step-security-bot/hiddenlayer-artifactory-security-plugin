# HiddenLayer Artifactory Model Security Plugin

    A JFrog Artifactory User Plugin for scanning ML Models.

## Overview

## Getting Started
1. Start Artifactory Container
```bash
docker-compose up
```
2. Navigate to `localhost:8082` to configure Artifactory instance
   - If prompted enter a new password. (i.e. `Fr0gg3r`)
3. Click on the banner to activate Artifactory.
![activate-banner](./banner.png)
4. Enter a License and click `Save`.
5. Set up a new HuggingFace Repository
   - Click on `Welcome` in the top right corner
   - Click on `New Remote Repository`
   - Select `HuggingFace`
   - Set the repository key (something like `hf`)
   - Click `Create Remote Repository`
6. Configure the plugin
   - Open the file `./core/src/hiddenlayer.properties`
   - Add your hiddenlayer API auth client_id and client_secret
   - record the repository IDs for the repositories you want to scan
7. Reload the plugin
    ```bash
    curl -XPOST -uadmin:<password> localhost:8081/artifactory/api/plugins/reload
    ```

Now, whenever you download a model using the HuggingFace cli, the plugin will upload the model
to the HiddenLayer API for scanning.  Based on your configuration, the plugin will either block
the download or allow it to proceed.

## Resources

 - [User Plugins](https://jfrog.com/help/r/jfrog-integrations-documentation/user-plugins)
    - [Plugin Execution Points](https://jfrog.com/help/r/jfrog-integrations-documentation/plugin-execution-points)
 - [Docker Image](https://releases-docker.jfrog.io/ui/repos/tree/General/artifactory-pro/org)
   - [JFROG_HOME location in container](https://jfrog.com/help/r/jfrog-installation-setup-documentation/jfrog-home)
 - [Snyk Security Plugin](https://github.com/snyk/artifactory-snyk-security-plugin)