# `network/`: red Hyperledger Fabric

Material criptográfico, configuración del canal y scripts de la red Fabric 2.5
del prototipo de pruebas. Los servicios se declaran en el `docker-compose.yml`
de la raíz; aquí solo vive lo que esos servicios montan.

## Topología

- 3 orderers Raft (`orderer0..2.fl.network`), toleran la caída de uno.
- Org1 y Org2, con un peer cada una (`peer0.org1`, `peer0.org2`).
- Un canal, `fl-channel`, con el chaincode `flprotocol` desplegado como CCaaS.
- Política de endorsement por defecto `OR('Org2MSP.peer')`, definida en el `.env`
  de la raíz (`ENDORSEMENT_POLICY`). Es single-org porque el chaincode devuelve
  tiempos medidos en cada invocación y dos peers no producirían la misma respuesta.

## Contenido

| Ruta | Qué es |
|---|---|
| `crypto-config.yaml` | Entrada de `cryptogen`: orderers, peers y 20 usuarios de Org2 (uno por cliente FL, cota superior de K). |
| `configtx/configtx.yaml` | Entrada de `configtxgen`: organizaciones, políticas, consenso Raft y perfiles del canal. |
| `organizations/` | Identidades MSP y TLS generadas por `cryptogen` (no versionado). |
| `channel-artifacts/` | Bloque génesis, transacción del canal, anchor peers y paquete CCaaS (no versionado). |
| `as-tls/` | Certificado de servidor del AS, CN=`fl-auth-server` (no versionado). |
| `coordinator-tls/` | Certificado de cliente del coordinator, CN=`coordinator`, que el AS exige para `POST /admin/init` (no versionado). |

Los dos últimos los genera `scripts/gen-certs.sh` firmados con la CA TLS de Org2,
de modo que toda la red comparte una sola PKI.

## Scripts

Se ejecutan desde la raíz del proyecto. `scripts/start-network.sh` los encadena
en este orden y no hace nada si el chaincode ya está desplegado.

| Script | Qué hace |
|---|---|
| `scripts/generate-artifacts.sh` | Corre `cryptogen` y `configtxgen`. Se detiene si `organizations/` ya existe. |
| `scripts/network-up.sh` | Levanta orderers, peers y `cli`, crea `fl-channel` y une ambos peers. |
| `../scripts/gen-certs.sh` | Genera los certificados de `as-tls/` y `coordinator-tls/`. |
| `scripts/deploy-chaincode.sh` | Construye el chaincode, lo instala en ambos peers, levanta el contenedor CCaaS, aprueba por las dos organizaciones y hace commit. |
| `../scripts/apply-anchor-peers.sh` | Publica los anchor peers con `peer channel update`; sin ellos falla el gossip entre organizaciones. |
| `scripts/network-down.sh` | Detiene la red, borra contenedores y volúmenes (el ledger no sobrevive) y elimina `organizations/` y `channel-artifacts/`. |

## Requisitos

- Docker Compose 2.17 o posterior (por `--wait`).
- Binarios de Fabric 2.5 (`cryptogen`, `configtxgen`) en `PATH` o en `FABRIC_BIN_PATH`.
