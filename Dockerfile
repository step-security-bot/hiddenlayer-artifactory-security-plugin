ARG VARIANT=ubuntu-22.04
FROM mcr.microsoft.com/devcontainers/base:${VARIANT}

# username for conatiner, vscode default, devcontainer

# TODO Install groocy etc.
USER vscode
