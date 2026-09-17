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

# En mode boucle, le worker releve les demandes de synchronisation immediates.
# Le passage nocturne complet est declenche par le planificateur de l'hote.
ENTRYPOINT ["python", "-m", "worker.sync"]
CMD ["--boucle"]
