# p2p_File-Sharing

[![Language: Java](https://img.shields.io/badge/Language-Java-blue.svg)](https://www.java.com)

##  Description

**p2p_File-Sharing (PeerLink)** is a Java-based peer-to-peer (P2P) file sharing system designed for decentralized, socket-based file transfers. It leverages Java's built-in HTTP server, dynamic port allocation, and multithreaded file servers to enable fast, concurrent file uploads and downloads between peers. The application includes a Next.js frontend and is containerized using Docker with NGINX as a reverse proxy for unified access.

---

##  Key Features and Highlights

-  Peer-to-peer file sharing over dynamically assigned ports
-  Upload and download files using HTTP and socket streams
-  Java-based backend with built-in HTTP server (no external web frameworks)
-  Frontend built using Next.js, styled with Tailwind CSS
-  Dockerized setup with NGINX reverse proxy for seamless routing
-  Multipart file parsing and concurrent file server threads
-  Capable of handling 50+ concurrent uploads with efficient file streaming

---


## Description

p2p_File-Sharing is a Java-based peer-to-peer file sharing application.

## Key Features and Highlights

- Peer-to-peer file sharing
- Java-based for cross-platform compatibility

## Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/username/p2p_File-Sharing.git
   ```

2. Navigate to the project directory:
   ```bash
   cd p2p_File-Sharing
   ```

3. Compile the Java files:
   ```bash
   javac *.java
   ```

## Usage

1. Start the server:
   ```bash
   java FileServer
   ```



## Dependencies

The project has no external dependencies.

## Contributing

Contributions are welcome! Please follow these steps:
1. Fork the repository
2. Create a new branch (`git checkout -b feature`)
3. Make your changes
4. Commit your changes (`git commit -am 'Add new feature'`)
5. Push to the branch (`git push origin feature`)
6. Create a new Pull Request


