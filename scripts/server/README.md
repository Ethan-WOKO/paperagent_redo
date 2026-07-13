# Server management scripts

These scripts are intended for a Docker deployment located at `/opt/paperagent` and can be run by BaoTa scheduled tasks.

Run them with `bash`; making the files executable is optional.

```bash
bash /opt/paperagent/scripts/server/start.sh
bash /opt/paperagent/scripts/server/stop.sh
bash /opt/paperagent/scripts/server/update.sh
bash /opt/paperagent/scripts/server/status.sh
TAIL=300 bash /opt/paperagent/scripts/server/logs.sh api
FOLLOW=1 bash /opt/paperagent/scripts/server/logs.sh api
```

`update.sh` pulls the `main` branch using Git HTTP/1.1, rebuilds the Docker images, and starts the updated services. It deliberately does not run `docker compose down -v`, so persistent data volumes are retained.
