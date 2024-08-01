# Akka Streams Package Analyzer
This project is an implementation of a Pipe and Filter architecture using Akka Streams to process and filter NPM package metadata based on specific criteria, saving the results to an external file and printing to the terminal.

# Akka Streams Package Analyzer

## Overview
This project is an implementation of a Pipe and Filter architecture using Akka Streams and Scala 3. The system processes a stream of NPM package metadata, filtering and analyzing the data based on specific criteria such as stars count, test coverage, release count, and commit activity. The results are displayed on the terminal and saved to an external file.

## Features
- Decompresses and reads package names from a gzipped text file.
- Retrieves package metadata from the NPMS API.
- Filters packages based on:
  - Stars count: Discard packages with less than 20 stars.
  - Test coverage: Discard only the packages with tests having less than 50% (0.5) of their code.
  - Number of releases: Discard packages with less than 2 releases.
  - Commit activity of top contributors: Discard packages where the sum of commits of the top-3 contributors is less than 150.

## Architecture and Data Flow
The solution follows a Pipe and Filter architecture with the following components:
- **Decompression**: Decompresses the input file containing package names. The input file `packages.txt.gz` is decompressed, and each line is read as a package name.
- **Source**: Converts decompressed package names into a data stream.
- **Buffer**: Buffers the stream to manage flow and applies backpressure if necessary.
- **API Requests**: Fetches metadata for each package from the NPMS API. The package names are converted into a stream of data.
- **Filtering**: The data stream is filtered to retain only the packages that meet the specified criteria.
- **Sink**: Outputs filtered packages to the terminal and saves to an external file (`result.txt`).

## Languages and Tools
- Scala 3
- Akka Streams
- SBT (Scala Build Tool)
- Intellij IDEA (IDE)

## References
- [Akka Streams Documentation](https://doc.akka.io/docs/akka/current/stream/index.html)
- [Practical Tutorial](https://www.youtube.com/watch?v=L1FS2dyiwIg)
