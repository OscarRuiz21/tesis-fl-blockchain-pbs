#!/usr/bin/env python3
"""Simula los K clientes particionando un dataset central.

No forma parte del protocolo: en un despliegue real cada cliente ya tiene sus
datos. Corre en el host (scripts/prepare-data.sh), una vez por combinación de
K, semilla y distribución, y genera:
  - <output_dir>/K{K}_seed{seed}_iid/client-{i}.csv
  - <output_dir>/K{K}_seed{seed}_dirichlet_alpha{alpha}/client-{i}.csv
  - <test_out>, el conjunto de prueba de la evaluación global
"""
import argparse
from pathlib import Path

import pandas as pd
from sklearn.model_selection import train_test_split

from python.partitioning.iid import partition_iid
from python.partitioning.dirichlet import partition_dirichlet


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--source', required=True, help='CSV original completo')
    p.add_argument('--output_dir', required=True, help='Directorio raíz de partitions')
    p.add_argument('--K', type=int, required=True)
    p.add_argument('--seed', type=int, default=42)
    p.add_argument('--distribution', choices=['iid', 'dirichlet'], required=True)
    p.add_argument('--dirichlet_alpha', type=float, default=0.5)
    p.add_argument('--target_col', required=True)
    p.add_argument('--test_ratio', type=float, default=0.2)
    p.add_argument('--test_out', required=True, help='Path absoluto al test.csv')
    args = p.parse_args()

    df = pd.read_csv(args.source)
    print(f"Source: {len(df)} rows, columns={list(df.columns)}")

    # Partición entrenamiento/prueba, estratificada si existe target_col.
    stratify = df[args.target_col] if args.target_col in df.columns else None
    train, test = train_test_split(
        df, test_size=args.test_ratio, random_state=args.seed, stratify=stratify)
    Path(args.test_out).parent.mkdir(parents=True, exist_ok=True)
    test.to_csv(args.test_out, index=False)
    print(f"Test set: {len(test)} rows -> {args.test_out}")

    if args.distribution == 'iid':
        parts = partition_iid(train, args.K, args.seed)
        suffix = f"K{args.K}_seed{args.seed}_iid"
    else:  # dirichlet
        parts = partition_dirichlet(
            train, args.K, args.dirichlet_alpha, args.seed, args.target_col)
        suffix = f"K{args.K}_seed{args.seed}_dirichlet_alpha{args.dirichlet_alpha}"

    # Numeración desde 1, como los hostnames fl-client-1..K que crea
    # docker compose --scale.
    out_dir = Path(args.output_dir) / suffix
    out_dir.mkdir(parents=True, exist_ok=True)
    for i, part in enumerate(parts, start=1):
        out_file = out_dir / f"client-{i}.csv"
        part.to_csv(out_file, index=False)
        print(f"  client-{i}: {len(part)} rows -> {out_file}")

    print(f"Done: {args.distribution} partitioning K={args.K}, seed={args.seed}")


if __name__ == '__main__':
    main()
