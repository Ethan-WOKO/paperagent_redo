FROM e2bdev/base:latest

USER root
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
       ca-certificates gcc g++ git maven openjdk-17-jdk-headless python3 \
    && rm -rf /var/lib/apt/lists/*
COPY yanban_runner.py /usr/local/lib/yanban/yanban_runner.py
RUN chmod 0555 /usr/local/lib/yanban/yanban_runner.py \
    && ln -s /usr/local/lib/yanban/yanban_runner.py /usr/local/bin/yanban-runner
USER user
WORKDIR /home/user/project
