NETWORKING - LOCAL LAB (POC)
============================

Network topology (local lab):

    Host PC (workstation)
       |
       |  127.0.0.1:8080  -> kind extraPortMapping -> NodePort 30080 -> backend
       |  127.0.0.1:1521  -> kind extraPortMapping -> NodePort 30152 -> oracle
       |
    kind cluster (Podman provider), namespace `poc-oracle`
       |
       +-- backend (Spring Boot Deployment)
       |      - connects to the DB via cluster DNS: oracle:1521/pdb_oltp
       |      - readiness probe includes the `db` health group
       |
       +-- oracle (StatefulSet, gvenzl/oracle-free:23-slim, 1 replica)
              - CDB FREE with PDB_OLTP (shared, VPD) and PDB_CORP
                (dedicated, ENTERPRISE physical isolation)
              - Oracle data persists on a PVC (survives pod restarts)

Both host ports bind to loopback only: the lab is never reachable from
the LAN (see `kind-config.yaml`, `listenAddress: 127.0.0.1`).

NetworkPolicies (see `network-policies.yaml`) default-deny in the
namespace with explicit allows for backend -> oracle:1521 and the
NodePort paths used by workstation-side QA (Flyway, sqlplus, smoke).

This is the LOCAL lab. The enterprise architecture (RAC / ASM) requires
separate nodes and is documented as target architecture, not simulated
on a single PC.
