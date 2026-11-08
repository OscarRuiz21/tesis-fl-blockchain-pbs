# `python/`: entrenamiento y preparación de datos

El cliente y el coordinator (Java) invocan estos scripts como subprocesos. Solo
este directorio conoce PyTorch; el protocolo PBS-FL es independiente del modelo.

## Scripts

| Script | Qué hace |
|---|---|
| `train.py` | Entrenamiento local de una ronda. Lee los pesos globales y escribe el delta, ambos en float32 big-endian. Lo invoca el cliente. |
| `eval.py` | Evalúa el modelo global sobre el conjunto de prueba. Lo invoca el coordinator. |
| `preprocess_gmsc.py` | Limpia el CSV crudo de Give Me Some Credit (Kaggle). |
| `preprocess_uci_credit.py` | Limpia el CSV de UCI Default of Credit Card Clients (`scripts/download-uci.sh` lo descarga). |
| `simulate_clients.py` | Parte el dataset procesado entre K clientes (IID o Dirichlet) y separa el conjunto de prueba. Corre en el host, vía `scripts/prepare-data.sh`; no forma parte del protocolo. |

Los modelos (`models/`: regresión logística y MLP) y los cargadores de datos
(`datasets/`) se registran por nombre en `MODEL_REGISTRY` y `DATASET_LOADERS`;
el YAML del experimento elige el modelo con `model.name` y `model.num_params`.

## Invocación

```bash
python train.py --model_name mlp --num_params 321 \
    --model_in in.bin --update_out out.bin \
    --data partitions/client-1.csv --client_id client-1 \
    --hyperparams '{"epochs":5,"lr":0.01,"batch_size":32,"features":[...],"target":"SeriousDlqin2yrs"}'
# stdout: accuracy=0.781 loss=0.523 num_samples=300

python eval.py --model_name mlp --num_params 321 --model_in global.bin \
    --testset test.csv --hyperparams '{"features":[...],"target":"SeriousDlqin2yrs"}'
# stdout: accuracy=0.781 loss=0.523
```

## Entorno

Python 3.11 o superior. Desde la raíz del repositorio:

```bash
python3 -m venv python/.venv
python/.venv/bin/pip install -r python/requirements.txt
python/.venv/bin/python -m pytest -q python/tests
```

`tests/compute_hash.py` y `tests/compute_tally_hash.py` son implementaciones
independientes de SHA-256 y del Tally Hash que usan los tests Java del AS para
comprobar la paridad de bytes. `tests/generate_synthetic_gmsc.py` genera un CSV
sintético con el esquema de GMSC para probar el flujo sin descargar el dataset.
