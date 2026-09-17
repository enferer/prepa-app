FROM python:3.12-slim
WORKDIR /app

RUN pip install --no-cache-dir garminconnect==0.2.25

RUN useradd --create-home --shell /bin/bash worker
USER worker

COPY --chown=worker:worker worker/ ./worker/

# Les jetons Garmin valent environ un an : les garder sur un volume evite de
# redemander le mot de passe — et une authentification par mot de passe repetee
# finit par declencher une verification en deux etapes.
ENV GARMIN_TOKENSTORE=/data/garmin-tokens

# Sans argument, l'image fait un passage complet et s'arrete — c'est ce qu'on veut d'un
# `docker compose run`. Le service qui doit rester en vie demande la boucle lui-meme,
# dans la composition : l'inverse obligeait a repeter le point d'entree pour obtenir un
# simple passage, et la commande echouait.
ENTRYPOINT ["python", "-m", "worker.sync"]
