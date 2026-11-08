# Portal de experimentos

> **Nota.** El portal se desarrolló con ayuda de herramientas de IA. Queda fuera del alcance de la tesis: no participa en el protocolo y solo sirve para lanzar experimentos y validar visualmente sus resultados.

Interfaz web para definir y lanzar experimentos del prototipo de pruebas, y
para revisar sus métricas de entrenamiento y de tiempos. El portal no modifica
la red ni el protocolo: valida cada spec con el coordinator, prepara las
particiones de datos y los K clientes, envía el experimento y lee los
resultados que quedan en `results/`. Cada corrida deja su log en
`portal/portal-data/logs/<run_id>.log`.

Pila: FastAPI + SQLite en el backend; Next.js 14 + Tailwind + Plotly en el
frontend.

## Arranque

Desde la raíz del repositorio:

```bash
./scripts/start-network.sh   # red HLF y chaincode
./scripts/start-portal.sh    # backend y frontend del portal
```

Después, abrir http://localhost:3000.

Los experimentos se ejecutan uno a la vez, porque todos comparten la misma red.

## Detener

```bash
./scripts/stop-portal.sh     # detiene solo el portal; la red sigue arriba
./scripts/stop-all.sh        # luego, si se quiere apagar la red y las apps (conserva el ledger)
```
