#!/usr/bin/env python3
"""Preprocesamiento de "Default of Credit Card Clients" (UCI, id 350;
Yeh y Lien, 2009).

Entrada (25 columnas, CSV exportado del XLS oficial de UCI):
    ID, LIMIT_BAL, SEX, EDUCATION, MARRIAGE, AGE,
    PAY_0, PAY_2, PAY_3, PAY_4, PAY_5, PAY_6,
    BILL_AMT1..BILL_AMT6, PAY_AMT1..PAY_AMT6,
    default payment next month

Salida (23 features + target):
    default_next_month (target binario 0|1; se renombra porque el original,
        "default payment next month", tiene espacios)
    limit_bal (int, monto de crédito otorgado en NT$)
    sex (int, 1=hombre 2=mujer)
    education (int, 1=posgrado 2=universidad 3=preparatoria 4=otros;
        valores no documentados {0, 5, 6} se recodifican a 4)
    marriage (int, 1=casado 2=soltero 3=otros; el 0 no documentado -> 3)
    age (int, años)
    pay_0, pay_2..pay_6 (int ordinal, estatus de pago de sep..abr 2005;
        -2=sin consumo, -1=pago puntual, 0=revolvente, 1..8=meses de atraso)
    bill_amt1..bill_amt6 (int, NT$; puede ser negativo, saldo a favor)
    pay_amt1..pay_amt6 (int, NT$)

Las categorías no documentadas se recodifican como es habitual en la
literatura del dataset. A diferencia de GMSC no hay NaN ni códigos centinela,
así que no hace falta imputar ni recortar; el script verifica que así sea.
No normaliza: la normalización la aplica datasets/tabular_csv.py en cada
cliente con sus propias estadísticas, como ocurriría en un despliegue real.
"""
import argparse
from pathlib import Path

import pandas as pd


COLUMN_RENAME = {
    'LIMIT_BAL': 'limit_bal',
    'SEX': 'sex',
    'EDUCATION': 'education',
    'MARRIAGE': 'marriage',
    'AGE': 'age',
    'PAY_0': 'pay_0',
    'PAY_2': 'pay_2',
    'PAY_3': 'pay_3',
    'PAY_4': 'pay_4',
    'PAY_5': 'pay_5',
    'PAY_6': 'pay_6',
    'BILL_AMT1': 'bill_amt1',
    'BILL_AMT2': 'bill_amt2',
    'BILL_AMT3': 'bill_amt3',
    'BILL_AMT4': 'bill_amt4',
    'BILL_AMT5': 'bill_amt5',
    'BILL_AMT6': 'bill_amt6',
    'PAY_AMT1': 'pay_amt1',
    'PAY_AMT2': 'pay_amt2',
    'PAY_AMT3': 'pay_amt3',
    'PAY_AMT4': 'pay_amt4',
    'PAY_AMT5': 'pay_amt5',
    'PAY_AMT6': 'pay_amt6',
    'default payment next month': 'default_next_month',
}

FEATURE_ORDER = [
    'limit_bal', 'sex', 'education', 'marriage', 'age',
    'pay_0', 'pay_2', 'pay_3', 'pay_4', 'pay_5', 'pay_6',
    'bill_amt1', 'bill_amt2', 'bill_amt3', 'bill_amt4', 'bill_amt5', 'bill_amt6',
    'pay_amt1', 'pay_amt2', 'pay_amt3', 'pay_amt4', 'pay_amt5', 'pay_amt6',
]


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--csv_path', required=True,
                   help='CSV crudo (uci_credit_raw.csv, export del XLS de UCI)')
    p.add_argument('--output',
                   default='python/data/uci_credit/uci_credit_processed.csv',
                   help='Destino del CSV procesado')
    args = p.parse_args()

    df = pd.read_csv(args.csv_path)
    print(f"Loaded {len(df)} rows, columns={list(df.columns)}")

    if 'ID' in df.columns:
        df = df.drop(columns=['ID'])

    # EDUCATION documenta 1..4; {0, 5, 6} no tienen significado y pasan a 4 (otros).
    n_edu = int(df['EDUCATION'].isin([0, 5, 6]).sum())
    df['EDUCATION'] = df['EDUCATION'].replace({0: 4, 5: 4, 6: 4})
    print(f"  EDUCATION: {n_edu} valores {{0,5,6}} recodificados a 4 (otros)")

    # MARRIAGE documenta 1..3; el 0 pasa a 3 (otros).
    n_mar = int((df['MARRIAGE'] == 0).sum())
    df['MARRIAGE'] = df['MARRIAGE'].replace({0: 3})
    print(f"  MARRIAGE: {n_mar} valores 0 recodificados a 3 (otros)")

    df = df.rename(columns=COLUMN_RENAME)

    assert int(df.isna().sum().sum()) == 0, "NaN inesperados en UCI Credit"
    assert set(df['default_next_month'].unique()) == {0, 1}, "target no binario"
    df['default_next_month'] = df['default_next_month'].astype(int)

    # Target primero y features en el orden del YAML del experimento.
    final_order = ['default_next_month'] + FEATURE_ORDER
    df = df[final_order]

    print(f"Final schema: {len(df.columns)} columns, {len(df)} rows")
    print(f"Target balance: {df['default_next_month'].mean()*100:.2f}% positivos")
    Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(args.output, index=False)
    print(f"Wrote {args.output}")


if __name__ == '__main__':
    main()
