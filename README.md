# FAAAST Digital Twin Gateway (Custom Edition)

*Read this in other languages: [English](#english-version) | [Português](#versão-em-português)*

---

## Versão em Português

Este projeto é uma versão customizada e estendida do [FAAAST-Service](https://github.com/FraunhoferIOSB/FAAAST-Service) (Fraunhofer IOSB), projetada para oferecer uma solução completa de Gêmeos Digitais (Digital Twins) com foco em interoperabilidade industrial, comunicação em tempo real e persistência de dados.

A arquitetura foi adaptada para atuar como um gateway inteligente, integrando **MQTT** para mensageria assíncrona, **OPC UA** para automação e controle padronizado, e **MongoDB** para historização e armazenamento de séries temporais das simulações e telemetria.

## Principais Diferenciais e Funcionalidades

Diferente do repositório original, esta versão introduz as seguintes melhorias:

*   **Gateway MQTT ↔ OPC UA:** Comunicação bidirecional configurada. O sistema é capaz de assinar tópicos MQTT e mapear essas mensagens diretamente para nós (nodes) do servidor OPC UA.
*   **Historização de Dados (MongoDB):** Integração com banco de dados MongoDB para salvar o histórico de eventos, telemetria e estados dos Gêmeos Digitais, permitindo análises futuras e auditorias.
*   **Servidor OPC UA Customizado:** O servidor OPC UA padrão foi substituído/otimizado para suportar conexões mais complexas e garantir maior flexibilidade na exposição dos dados do *Asset Administration Shell (AAS)*.
*   **Simulação Distribuída:** Estrutura base preparada para orquestração de simulações de múltiplos ativos (como drones ou ambientes de teste) gerenciados via MQTT e OPC UA simultaneamente.
*   **Ambiente Totalmente Dockerizado:** Orquestração completa do ambiente (Broker, Banco de Dados e FAAAST Service) com um único comando via `docker-compose`.

## Arquitetura do Sistema

O ambiente sobe os seguintes serviços de forma interconectada:

1.  **Mosquitto (Broker MQTT):** Gerencia o tráfego de mensagens assíncronas (ex: telemetria, waypoints de missão). Operando na porta `1883`.
2.  **MongoDB:** Banco de dados NoSQL utilizado para a historização persistente das informações do AAS. Operando na porta `27017`.
3.  **FAAAST Service:** O núcleo do *Asset Administration Shell*. Expõe a API REST e o endpoint do servidor OPC UA atualizado. Operando nas portas `8080` (HTTP) e `4840` (OPC UA).

## Como Executar o Projeto

### Pré-requisitos
*   [Docker](https://docs.docker.com/get-docker/)
*   [Docker Compose](https://docs.docker.com/compose/install/)

### Passo a Passo

1. Clone este repositório:
   ```bash
   git clone https://github.com/DevMiguelPinheiro/faaast-opcua-historian.git
   cd faaast-opcua-historian
   ```

2. Certifique-se de que os seus arquivos de recurso (`config.json` e arquivos `.aasx`) estão na pasta `./my-resources`.

3. Inicie a stack utilizando o Docker Compose:
   ```bash
   docker-compose up -d --build
   ```

4. Verifique os logs para garantir que o gateway e o OPC UA inicializaram corretamente:
   ```bash
   docker-compose logs -f faaast
   ```

### Endpoints Disponíveis

Após a inicialização, você poderá acessar os seguintes serviços:

*   **API REST (AAS):** `http://localhost:8080`
*   **Servidor OPC UA:** `opc.tcp://localhost:4840` (Conecte com clientes como *UaExpert*)
*   **Broker MQTT:** `localhost:1883`
*   **Banco de Dados (Mongo):** `localhost:27017` (Usuário/Senha: devem ser configurados no `.env` ou `docker-compose.yml`)

## Exemplo Genérico de Configuração (`config.json`)

Para que a integração do OPC UA, historização no MongoDB e mapeamento MQTT funcione, você deve configurar o `config.json` (geralmente localizado em `./my-resources/config.json`). Abaixo há um exemplo genérico da configuração do endpoint:

```json
{
    "core": {
        "requestHandlerThreadPoolSize": 2
    },
    "endpoints": [
        {
            "@class": "de.fraunhofer.iosb.ilt.faaast.service.endpoint.http.HttpEndpoint",
            "port": 8080
        },
        {
            "@class": "de.fraunhofer.iosb.ilt.faaast.service.endpoint.opcuamilo.MiloOpcUaEndpoint",
            "port": 4840,
            "serverName": "faaast",
            "historizingEnabled": true,
            "historyMongoConnectionString": "mongodb://[USER]:[PASSWORD]@mongodb:27017/?authSource=admin",
            "historyMongoDatabase": "faaast_history",
            "historyMongoCollection": "opcua_history",
            "historyMaxEntries": 10000,
            "historyMaxAgeDays": 30,
            "mqttEnabled": true,
            "mqttBrokerUrl": "tcp://mosquitto:1883",
            "mqttMappings": [
                { 
                    "topic": "seu-topico/exemplo/Dado1", 
                    "submodelId": "NomeDoSubmodelo", 
                    "idShort": "Dado1" 
                },
                { 
                    "topic": "seu-topico/exemplo/Dado2", 
                    "submodelId": "NomeDoSubmodelo", 
                    "idShort": "Dado2" 
                }
            ]
        }
    ],
    "persistence": {
        "@class": "de.fraunhofer.iosb.ilt.faaast.service.persistence.memory.PersistenceInMemory",
        "initialModelFile": "/app/resources/SeuModeloAAS.aasx"
    },
    "messageBus": {
        "@class": "de.fraunhofer.iosb.ilt.faaast.service.messagebus.internal.MessageBusInternal"
    }
}
```

## Tecnologias Utilizadas
*   [Java / Spring Boot] - Core do FAAAST-Service
*   [Eclipse Mosquitto] - Broker MQTT
*   [MongoDB] - Banco de Dados para historização
*   [OPC UA] - Protocolo M2M de automação industrial
*   [Docker] - Containerização

---

## English Version

This project is a customized and extended version of [FAAAST-Service](https://github.com/FraunhoferIOSB/FAAAST-Service) (Fraunhofer IOSB), designed to provide a complete Digital Twins solution focusing on industrial interoperability, real-time communication, and data persistence.

The architecture was adapted to act as a smart gateway, integrating **MQTT** for asynchronous messaging, **OPC UA** for standardized automation and control, and **MongoDB** for historization and time-series storage of simulations and telemetry.

## Key Features and Differentiators

Unlike the original repository, this version introduces the following improvements:

*   **MQTT ↔ OPC UA Gateway:** Configured bi-directional communication. The system can subscribe to MQTT topics and map these messages directly to nodes in the OPC UA server.
*   **Data Historization (MongoDB):** Integration with a MongoDB database to save the history of events, telemetry, and Digital Twin states, allowing for future analysis and auditing.
*   **Custom OPC UA Server:** The standard OPC UA server was replaced/optimized to support more complex connections and ensure greater flexibility in exposing *Asset Administration Shell (AAS)* data.
*   **Distributed Simulation:** Base structure prepared to orchestrate simulations of multiple assets (such as drones or test environments) managed via MQTT and OPC UA simultaneously.
*   **Fully Dockerized Environment:** Complete orchestration of the environment (Broker, Database, and FAAAST Service) using a single command via `docker-compose`.

## System Architecture

The environment spins up the following interconnected services:

1.  **Mosquitto (MQTT Broker):** Manages asynchronous message traffic (e.g., telemetry, mission waypoints). Operating on port `1883`.
2.  **MongoDB:** NoSQL database used for persistent historization of AAS information. Operating on port `27017`.
3.  **FAAAST Service:** The core of the *Asset Administration Shell*. Exposes the REST API and the updated OPC UA server endpoint. Operating on ports `8080` (HTTP) e `4840` (OPC UA).

## How to Run the Project

### Prerequisites
*   [Docker](https://docs.docker.com/get-docker/)
*   [Docker Compose](https://docs.docker.com/compose/install/)

### Step by Step

1. Clone this repository:
   ```bash
   git clone https://github.com/DevMiguelPinheiro/faaast-opcua-historian.git
   cd faaast-opcua-historian
   ```

2. Make sure your resource files (`config.json` and `.aasx` files) are placed in the `./my-resources` folder.

3. Start the stack using Docker Compose:
   ```bash
   docker-compose up -d --build
   ```

4. Check the logs to ensure the gateway and OPC UA server started correctly:
   ```bash
   docker-compose logs -f faaast
   ```

### Available Endpoints

After initialization, you can access the following services:

*   **REST API (AAS):** `http://localhost:8080`
*   **OPC UA Server:** `opc.tcp://localhost:4840` (Connect with clients like *UaExpert*)
*   **MQTT Broker:** `localhost:1883`
*   **Database (Mongo):** `localhost:27017` (User/Password: must be set in `.env` or `docker-compose.yml`)

## Generic Configuration Example (`config.json`)

For the OPC UA integration, MongoDB historization, and MQTT mapping to work, you must set up the `config.json` (usually located in `./my-resources/config.json`). Below is a generic endpoint configuration example:

```json
{
    "core": {
        "requestHandlerThreadPoolSize": 2
    },
    "endpoints": [
        {
            "@class": "de.fraunhofer.iosb.ilt.faaast.service.endpoint.http.HttpEndpoint",
            "port": 8080
        },
        {
            "@class": "de.fraunhofer.iosb.ilt.faaast.service.endpoint.opcuamilo.MiloOpcUaEndpoint",
            "port": 4840,
            "serverName": "faaast",
            "historizingEnabled": true,
            "historyMongoConnectionString": "mongodb://[USER]:[PASSWORD]@mongodb:27017/?authSource=admin",
            "historyMongoDatabase": "faaast_history",
            "historyMongoCollection": "opcua_history",
            "historyMaxEntries": 10000,
            "historyMaxAgeDays": 30,
            "mqttEnabled": true,
            "mqttBrokerUrl": "tcp://mosquitto:1883",
            "mqttMappings": [
                { 
                    "topic": "your-topic/example/Data1", 
                    "submodelId": "SubmodelName", 
                    "idShort": "Data1" 
                },
                { 
                    "topic": "your-topic/example/Data2", 
                    "submodelId": "SubmodelName", 
                    "idShort": "Data2" 
                }
            ]
        }
    ],
    "persistence": {
        "@class": "de.fraunhofer.iosb.ilt.faaast.service.persistence.memory.PersistenceInMemory",
        "initialModelFile": "/app/resources/YourAASModel.aasx"
    },
    "messageBus": {
        "@class": "de.fraunhofer.iosb.ilt.faaast.service.messagebus.internal.MessageBusInternal"
    }
}
```

## Technologies Used
*   [Java / Spring Boot] - FAAAST-Service Core
*   [Eclipse Mosquitto] - MQTT Broker
*   [MongoDB] - Database for historization
*   [OPC UA] - M2M Protocol for industrial automation
*   [Docker] - Containerization
