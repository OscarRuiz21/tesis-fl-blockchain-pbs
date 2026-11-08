# Aprendizaje federado anónimo sobre Hyperledger Fabric

Prototipo de pruebas de la tesis de maestría *Aprendizaje federado descentralizado usando blockchain permisionado para garantizar privacidad y confianza*, de Oscar Manuel Ruiz Hurtado, dirigida por la Dra. Rocío Alejandra Aldeco Pérez (Maestría en Ciencia e Ingeniería de la Computación, UNAM).

En cada ronda, cada cliente obtiene del servidor de autenticación (AS) una credencial anónima mediante una firma parcialmente ciega de Abe y Okamoto sobre NIST P-256. Con ella envía su actualización a un contrato en Hyperledger Fabric 2.5, que verifica la credencial y la firma de la actualización, registra el nulificador para aceptar a lo más una contribución por ronda, agrega las actualizaciones y publica el Tally Hash del conjunto agregado.

- **Video del prototipo** (2 min, una corrida real lanzada desde el portal): [prototipo_pbs_fl.mp4](https://github.com/OscarRuiz21/tesis-fl-blockchain-pbs/releases/download/v1.0/prototipo_pbs_fl.mp4)
- **Artículo:** R. Aldeco-Pérez y O. M. Ruiz Hurtado, "PBS-FL: Anonymous Federated Learning with Verifiable Update Inclusion", en *Advances in Computational Intelligence. MICAI 2026 International Workshops*, LNCS, Springer, 2026, pp. 501-515. [doi:10.1007/978-3-032-44191-1_35](https://doi.org/10.1007/978-3-032-44191-1_35)

## Resultados

Las cifras son las del capítulo 6 de la tesis. Los CSV de cada corrida están en [`results/`](results), una carpeta por escenario, y cada escenario se reproduce con su YAML de [`experiments/`](experiments).

**Exactitud en UCI Credit Card Default** (conjunto de prueba reservado):

| Configuración | Modelo | Exactitud | Corrida |
|---|---|---:|---|
| Centralizado, mismo preprocesamiento y partición | LR | 0.806 | reportada en la tesis |
| Federado con PBS, K=5 | LR | 0.808 | `uci-B1-K5-R5-fedavg-pbs` |
| Federado con PBS, K=10, 3 semillas | MLP [16,8] | 0.820 (0.815 a 0.827) | `uci-mlp-K10-R12-fedavg-pbs*` |
| Yeh y Lien (2009) | LR y otros | 0.74 a 0.84 | literatura |

**Costo del anonimato.** El protocolo añade unos 0.3 s por cliente y ronda, cerca de tres cuartas partes en la ida y vuelta con el AS; la criptografía local y la del contrato suman menos de 90 ms. Ese costo es fijo, así que su peso relativo baja conforme el entrenamiento se encarece:

| Escenario (K=10) | Modelo | Anonimato (ms) | Entrenamiento (ms) | Fracción |
|---|---|---:|---:|---:|
| GMSC | LR | 388 | 7685 | 4.8 % |
| GMSC | MLP | 460 | 28089 | 1.6 % |
| UCI | LR | 326 | 4324 | 7.0 % |
| UCI | MLP | 297 | 8254 | 3.5 % |

En todos los escenarios ejecutados la fracción va del 7 % (rondas de 4 s de entrenamiento) al 0.5 % (29 s). La exactitud no cambia entre los modos `pbs` y `none`: 0.789 en ambos para GMSC y una diferencia máxima de 0.0002 en UCI. En UCI, con tres semillas pareadas, el sobrecosto de pared por ejecución fue de 5.3 %, 6.1 % y 5.8 %.

**Costo de la cadena** (LR, K=10, R=8, `pbs`; media por cliente y ronda):

| Componente | GMSC (ms) | UCI (ms) |
|---|---:|---:|
| Envío de la actualización (ciclo completo de la transacción) | 404 | 422 |
| ... de ello, verificación PBS en el contrato | 56 | 23 |
| ... de ello, verificación de la firma de autoría | 26 | 8 |
| Consulta del modelo global | 26 | 19 |
| Sondeo de la elección de líder | 69 | 58 |
| Transacción de agregación, con el Tally Hash | 4 | 5 |

## De la tesis al código

| Tesis | Código |
|---|---|
| Etapa 1, emisión de credenciales (§4.2.1) | AS: [`IssueCommitmentService`](as/src/main/java/org/fl/as/service/IssueCommitmentService.java), [`IssueSignatureService`](as/src/main/java/org/fl/as/service/IssueSignatureService.java), [`IssuanceLedger`](as/src/main/java/org/fl/as/store/IssuanceLedger.java). Cliente: [`PBSClientOperations`](client/src/main/java/org/fl/client/crypto/PBSClientOperations.java) |
| Etapa 2, envío anónimo (§4.2.2) | Cliente: [`AnonymityService`](client/src/main/java/org/fl/client/service/AnonymityService.java), [`ModelSigner`](client/src/main/java/org/fl/client/crypto/ModelSigner.java). Contrato: `submitUpdate` en [`FLProtocolChaincode`](chaincode/flprotocol/src/main/java/org/fl/flprotocol/FLProtocolChaincode.java), [`PBSVerifier`](chaincode/flprotocol/src/main/java/org/fl/flprotocol/verifier/PBSVerifier.java) |
| Nulificadores (P2) | [`LedgerKeys`](chaincode/flprotocol/src/main/java/org/fl/flprotocol/storage/LedgerKeys.java) |
| Etapa 3, agregación verificable y Tally Hash (§4.2.3) | `aggregateRound` en `FLProtocolChaincode`, [`AggregationEngine`](chaincode/flprotocol/src/main/java/org/fl/flprotocol/aggregator/AggregationEngine.java), [`ProtocolHash`](chaincode/flprotocol/src/main/java/org/fl/flprotocol/crypto/ProtocolHash.java) |
| Auditoría de inclusión por el cliente (P4, P5) | [`AuditService`](client/src/main/java/org/fl/client/service/AuditService.java) |
| Concurrencia y elección de líder (Cap. 5) | [`RunRoundService`](client/src/main/java/org/fl/client/service/RunRoundService.java), `getRoundLeaderInfo` en el contrato |
| FedAvg, FedMedian y FedProx (Cap. 5) | [`chaincode/.../aggregator/`](chaincode/flprotocol/src/main/java/org/fl/flprotocol/aggregator) |
| Modelos LR y MLP (Cap. 5) | [`python/models/`](python/models) |

## Cómo correrlo

Requisitos: Docker con al menos 8 GB de memoria, JDK 21, Maven 3.9, Gradle 8.6, Python 3.11, `yq` 4, OpenSSL y los binarios de Fabric 2.5 (`cryptogen`, `configtxgen`) en el `PATH` o en `FABRIC_BIN_PATH`. Se probó en macOS (Apple Silicon).

```bash
python3.11 -m venv python/.venv
python/.venv/bin/pip install -r python/requirements.txt

./scripts/download-uci.sh          # UCI id 350, CC BY 4.0; verifica el SHA-256
python/.venv/bin/python python/preprocess_uci_credit.py \
    --csv_path python/data/uci_credit/uci_credit_raw.csv \
    --output python/data/uci_credit/uci_credit_processed.csv

./scripts/build-all.sh             # jars, chaincode e imágenes (5 a 10 min la primera vez)
./scripts/run-experiment.sh experiments/uci_smoke_K2_R1.yml
```

`run-experiment.sh` levanta la red si no está arriba, despliega el contrato, arranca el AS y los K clientes, corre el experimento y deja los resultados en `results/<etiqueta>/`. Cualquier escenario de la tesis se corre igual, por ejemplo `./scripts/run-experiment.sh experiments/uci_B1_K5_R5_fedavg_pbs.yml`, cuyo entrenamiento federado tarda unos 45 s.

GMSC no se puede redistribuir: se descarga de Kaggle (competencia *Give Me Some Credit*), se coloca `cs-training.csv` en `python/data/give_me_some_credit/` (SHA-256 `1cb0645bf65b3d0584dd4a46af2fc877757966b17fb381edd100143d2f0b191c`) y se procesa con `python/preprocess_gmsc.py`.

Para detener todo conservando el ledger: `./scripts/stop-all.sh`. Para borrar la red y su material criptográfico: `./network/scripts/network-down.sh`.

**Portal (opcional).** Con la red arriba, `./scripts/start-portal.sh` y abrir http://localhost:3000. El portal se hizo con ayuda de herramientas de IA, queda fuera del alcance de la tesis y solo sirve para lanzar experimentos y revisar visualmente sus resultados; ver [`portal/README.md`](portal/README.md).

**Pruebas.** `mvn test` dentro de `as/`, `client/` y `coordinator/`; `gradle test` en `chaincode/flprotocol/`; `python/.venv/bin/python -m pytest python/tests`. Las pruebas del contrato ejercen los rechazos (credencial alterada, otra llave del AS, otra ronda, firma de autoría de otro cliente, nulificador repetido) y `ConcurrentIssuanceTest` comprueba que el AS emite a lo más una credencial por identidad y ronda bajo peticiones concurrentes.

## Estructura

```
as/            servidor de autenticación: emisión de la firma parcialmente ciega
client/        cliente FL: credencial, entrenamiento, envío, líder y auditoría
chaincode/     contrato en Fabric: verificación, nulificadores, agregación y Tally Hash
coordinator/   instrumentación: arranca el experimento, mide y evalúa el modelo global
python/        entrenamiento, evaluación, preprocesamiento y particionado
network/       configuración de la red Fabric (2 organizaciones, 1 canal)
scripts/       construcción, arranque y ejecución de experimentos
experiments/   un YAML por escenario de la tesis
results/       salidas de las corridas reportadas en la tesis
portal/        interfaz web opcional (ver nota arriba)
```
