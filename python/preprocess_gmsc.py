#!/usr/bin/env python3
"""Preprocesamiento de "Give Me Some Credit" (GMSC, Kaggle, 2011).

Entrada (12 columnas, cs-training.csv):
    Id, SeriousDlqin2yrs,
    RevolvingUtilizationOfUnsecuredLines, age,
    NumberOfTime30-59DaysPastDueNotWorse, DebtRatio, MonthlyIncome,
    NumberOfOpenCreditLinesAndLoans, NumberOfTimes90DaysLate,
    NumberRealEstateLoansOrLines, NumberOfTime60-89DaysPastDueNotWorse,
    NumberOfDependents

Salida (10 features + target):
    SeriousDlqin2yrs (target binario 0|1)
    revolving_utilization (float, winsorize [0, 1.5])
    age (int, clip [18, 110])
    past_due_30_59 (int, clip [0, 20])
    debt_ratio (float, winsorize percentil 99)
    monthly_income (float, imputación por mediana + clip)
    open_credit_lines (int)
    past_due_90 (int, clip [0, 20])
    real_estate_loans (int)
    past_due_60_89 (int, clip [0, 20])
    num_dependents (int, imputación por mediana, clip [0, 10])

El target conserva su nombre original. No normaliza: la normalización la aplica datasets/tabular_csv.py en cada
cliente con sus propias estadísticas, como ocurriría en un despliegue real.
"""
import argparse
from pathlib import Path

import pandas as pd


COLUMN_RENAME = {
    'RevolvingUtilizationOfUnsecuredLines': 'revolving_utilization',
    'age': 'age',
    'NumberOfTime30-59DaysPastDueNotWorse': 'past_due_30_59',
    'DebtRatio': 'debt_ratio',
    'MonthlyIncome': 'monthly_income',
    'NumberOfOpenCreditLinesAndLoans': 'open_credit_lines',
    'NumberOfTimes90DaysLate': 'past_due_90',
    'NumberRealEstateLoansOrLines': 'real_estate_loans',
    'NumberOfTime60-89DaysPastDueNotWorse': 'past_due_60_89',
    'NumberOfDependents': 'num_dependents',
}

PAST_DUE_COLS = ['past_due_30_59', 'past_due_60_89', 'past_due_90']


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--csv_path', required=True, help='CSV crudo (cs-training.csv)')
    p.add_argument('--output',
                   default='python/data/give_me_some_credit/gmsc_processed.csv',
                   help='Destino del CSV procesado')
    p.add_argument('--debt_ratio_p99', type=float, default=None,
                   help='Override percentil para winsorize de debt_ratio')
    args = p.parse_args()

    df = pd.read_csv(args.csv_path)
    print(f"Loaded {len(df)} rows, columns={list(df.columns)}")

    if 'Id' in df.columns:
        df = df.drop(columns=['Id'])

    # Cerca del 20 % de MonthlyIncome es NaN: descartar esas filas perdería
    # demasiados datos.
    for col in ['MonthlyIncome', 'NumberOfDependents']:
        if col in df.columns:
            n_na = int(df[col].isna().sum())
            if n_na > 0:
                median = df[col].median()
                df[col] = df[col].fillna(median)
                print(f"  Imputed {n_na} NaN in '{col}' with median={median:.2f}")

    df = df.rename(columns=COLUMN_RENAME)

    # Uso de crédito: >1 indica sobreendeudamiento, pero valores como 50708
    # son errores de captura; [0, 1.5] conserva el sobreendeudamiento real.
    df['revolving_utilization'] = df['revolving_utilization'].clip(0.0, 1.5)

    # Con monthly_income en 0 o NaN el debt_ratio crudo llega a 329664; el
    # percentil 99 conserva la cola legítima y corta esos casos.
    p99_default = float(df['debt_ratio'].quantile(0.99))
    p99 = args.debt_ratio_p99 if args.debt_ratio_p99 is not None else p99_default
    df['debt_ratio'] = df['debt_ratio'].clip(0.0, p99)
    print(f"  debt_ratio clipped to [0, {p99:.4f}] (p99)")

    # El máximo crudo (3 M USD/mes) es ruido y dominaría los gradientes.
    p99_income = float(df['monthly_income'].quantile(0.99))
    df['monthly_income'] = df['monthly_income'].clip(0.0, p99_income)
    print(f"  monthly_income clipped to [0, {p99_income:.2f}] (p99)")

    # Hay edades en 0, probablemente errores de captura.
    df['age'] = df['age'].clip(18, 110)

    # 96 y 98 son códigos centinela, no conteos de moras.
    for col in PAST_DUE_COLS:
        df[col] = df[col].clip(0, 20)

    df['num_dependents'] = df['num_dependents'].clip(0, 10)

    int_cols = ['age', 'past_due_30_59', 'open_credit_lines', 'past_due_90',
                'real_estate_loans', 'past_due_60_89', 'num_dependents']
    for c in int_cols:
        df[c] = df[c].astype(int)
    df['SeriousDlqin2yrs'] = df['SeriousDlqin2yrs'].astype(int)

    # Target primero y features en el orden del YAML del experimento.
    final_order = [
        'SeriousDlqin2yrs',
        'revolving_utilization',
        'age',
        'past_due_30_59',
        'debt_ratio',
        'monthly_income',
        'open_credit_lines',
        'past_due_90',
        'real_estate_loans',
        'past_due_60_89',
        'num_dependents',
    ]
    df = df[final_order]

    print(f"Final schema: {len(df.columns)} columns, {len(df)} rows")
    print(f"Target balance: {df['SeriousDlqin2yrs'].mean()*100:.2f}% positivos")
    Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(args.output, index=False)
    print(f"Wrote {args.output}")


if __name__ == '__main__':
    main()
