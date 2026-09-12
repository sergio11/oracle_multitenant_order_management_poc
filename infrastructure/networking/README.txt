NETWORKING - LOCAL LAB (POC)
=============================

Network topology (local lab, SRS 115):

    Host PC
       |
    Podman bridge network: poc_oracle_net
       |
       +-- backend (Spring Boot, external during Phase 1)
       |
       +-- oracle (container poc-oracle, PDB_OLTP)

The backend will communicate with Oracle over the internal Podman
network. Oracle exposes ports 1521 (listener) and 8080 (HTTP) to the
host for local development.

This is the LOCAL lab. The enterprise architecture (RAC / ASM / Data
Guard / GoldenGate) requires separate sites/nodes and is documented as
target architecture (see SRS 86-87), not simulated on a single PC.