# Contributing to FAAAST Digital Twin Gateway (Custom Edition)

First off, thank you for considering contributing to this project! It's people like you that make the open-source community such a great place to learn, inspire, and create.

Since this project is a custom extension of the original [FAAAST-Service](https://github.com/FraunhoferIOSB/FAAAST-Service), we highly welcome contributions specifically focused on our custom layers:

*   New **MQTT to OPC UA** integrations, architectures, and mappings.
*   Improvements to the **Dockerization** and deployment process.
*   Enhancements in **Historization** (MongoDB database models, analytics).
*   New **Digital Twin simulation** scenarios (such as drones, IoT devices, test environments).

## How to Contribute

If you have a suggestion for improvements or a new integration, please follow these steps:

1. **Fork the Project**
2. **Create your Feature Branch** (`git checkout -b feature/AmazingFeature`)
3. **Commit your Changes** (`git commit -m 'Add some AmazingFeature'`)
4. **Push to the Branch** (`git push origin feature/AmazingFeature`)
5. **Open a Pull Request**

### Reporting Bugs or Requesting Features

You can also simply open an issue if you don't want to code right away. When opening an issue, please include:
- A clear description of the problem or the feature request.
- Steps to reproduce the environment (if it is a bug).
- A snippet of your `config.json` and `docker-compose.yml` (⚠️ **Remember to hide your passwords and sensitive data!**).

### Core Code Formatting (Java)

If your pull request modifies the core Java codebase (FAAAST-Service files), the build process will check for code style formatting. You can automatically format your code by running:

```bash
mvn spotless:apply
```

### Third-Party Licenses

If you use additional dependencies, please be sure that their licenses are compliant with open-source standards. 

Thank you for your time and contribution!
