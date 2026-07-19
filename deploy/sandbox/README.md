# Governed Sandbox Broker deployment

The Broker is deliberately **not** a Docker Compose service. Docker Sandboxes requires host KVM access, so production runs the Broker as a host `systemd` service under the dedicated non-root `yanban-sandbox` account. That account may belong to `kvm`, but must not have Docker-socket access, API/Project volumes, `yanban_agent` credentials, or any other application secret.

An administrator creates `yanban_sandbox` once with `initialize-yanban-sandbox.sql.example`. Compose publishes MySQL only on host loopback port `13306`; the Broker receives only that schema's least-privilege account. Its environment file supplies an absolute official `sbx` path, private workspace, token, and Broker DB credentials. The Broker itself remains on `127.0.0.1:8091`. A host TLS terminator exposes only an authenticated, firewall-restricted bridge listener such as `host.docker.internal:8443`; Compose installs the `host-gateway` route and the API uses that HTTPS URL. Never publish this listener to the public Internet or expose naked HTTP on `0.0.0.0`.

`PRODUCTION` mode requires Linux, the configured dedicated user, and readable/writable `/dev/kvm`. The example unit grants only the `kvm` supplementary group and hardens filesystem/device access. It creates persistent private HOME/XDG config, data and state beneath `/var/lib/yanban-sandbox`, allowing sbx login and daemon state to survive restart. Before enabling the service, an administrator runs the official `sbx login`, `sbx policy init` with deny-all network policy, and `sbx diagnose` as the `yanban-sandbox` user with those same HOME/XDG values. These commands are deployment prerequisites, not startup automation. Absence of the Broker affects only explicit sandbox steps; `YANBAN_SANDBOX_ENABLED=false` remains the default and performs no Broker probe or connection.

For Windows 11 acceptance only, explicitly set `YANBAN_SANDBOX_BROKER_MODE=LOCAL_ACCEPTANCE`, loopback address, `remote-access=false`, a separate test database/workspace/token, and an absolute `sbx.exe`. This mode is rejected on non-Windows or non-loopback deployments and does not relax `PRODUCTION`. No real sbx acceptance was run while sbx was absent.
### Production API-to-host transport

Production Compose uses `https://host.docker.internal:8443`; it never uses
`127.0.0.1` for the Broker because that address would name the API container.
Before enabling the feature, an administrator must provision a certificate
trusted by the API JVM for `host.docker.internal`, install the accompanying
`nginx-sandbox-broker.conf.example`, and firewall port 8443 so it is reachable
only from the local container bridge. The terminator forwards only to the
Broker's loopback `127.0.0.1:8091`; the bearer token remains mandatory.
Startup validation intentionally fails when the URL, token, TLS trust, or
Broker health check is unavailable. No certificate or private key is shipped.

For Windows `LOCAL_ACCEPTANCE`, run both API and Broker natively and explicitly
set `YANBAN_SANDBOX_BROKER_URL=http://127.0.0.1:8091`; the Broker validation
requires loopback binding and `remote-access=false`. Do not use this override
inside the production API container.
