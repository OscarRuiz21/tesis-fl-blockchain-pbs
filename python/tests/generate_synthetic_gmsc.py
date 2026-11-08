#!/usr/bin/env python3
"""CSV sintético con el esquema de "Give Me Some Credit" (Kaggle).

Sirve para probar el flujo completo sin descargar el dataset; los resultados
de la tesis usan el original (https://www.kaggle.com/c/GiveMeSomeCredit).

Columnas, las mismas que cs-training.csv:
    Id (int), SeriousDlqin2yrs (int 0|1),
    RevolvingUtilizationOfUnsecuredLines (float),
    age (int), NumberOfTime30-59DaysPastDueNotWorse (int),
    DebtRatio (float), MonthlyIncome (float, ~20% NaN),
    NumberOfOpenCreditLinesAndLoans (int), NumberOfTimes90DaysLate (int),
    NumberRealEstateLoansOrLines (int),
    NumberOfTime60-89DaysPastDueNotWorse (int),
    NumberOfDependents (int, ~3% NaN)

Determinista con --seed; solo usa la biblioteca estándar.

Uso:
    python3 python/tests/generate_synthetic_gmsc.py \\
        --rows 2000 --seed 42 \\
        --output python/data/give_me_some_credit/cs-training.csv
"""
import argparse
import csv
import os
import random


def generate_row(rnd: random.Random, row_id: int) -> dict:
    """Incluye NaN ocasionales para ejercitar la imputación de preprocess_gmsc.py."""
    age = rnd.randint(20, 80)

    # Ingreso lognormal, cerca de 20 % NaN.
    if rnd.random() < 0.20:
        monthly_income = ''  # NaN
    else:
        monthly_income = round(rnd.lognormvariate(mu=8.4, sigma=0.6), 0)
        monthly_income = max(500.0, min(monthly_income, 50000.0))

    # Dependientes, cerca de 3 % NaN.
    if rnd.random() < 0.03:
        num_dependents = ''
    else:
        num_dependents = rnd.choices(
            [0, 1, 2, 3, 4, 5], weights=[0.55, 0.18, 0.15, 0.08, 0.03, 0.01])[0]

    # Contadores de mora: casi siempre 0, cola corta.
    past_due_30_59 = rnd.choices(
        [0, 1, 2, 3, 4, 5], weights=[0.80, 0.10, 0.05, 0.03, 0.01, 0.01])[0]
    past_due_60_89 = rnd.choices(
        [0, 1, 2, 3], weights=[0.93, 0.05, 0.015, 0.005])[0]
    past_due_90 = rnd.choices(
        [0, 1, 2, 3, 4], weights=[0.93, 0.04, 0.02, 0.008, 0.002])[0]

    # Código centinela 98 en 0.3 % de las filas, como en el dataset real.
    if rnd.random() < 0.003:
        past_due_30_59 = 98
    if rnd.random() < 0.003:
        past_due_60_89 = 98

    revolving_util = max(0.0, rnd.gauss(0.36, 0.30))
    if rnd.random() < 0.005:
        revolving_util = rnd.uniform(50, 500)

    # Sin ingreso, o en 5 % de los casos, el debt_ratio se dispara como en el original.
    if monthly_income == '' or rnd.random() < 0.05:
        debt_ratio = round(rnd.uniform(100, 5000), 4)
    else:
        debt_ratio = round(rnd.uniform(0.0, 1.5), 4)

    open_credit = rnd.choices(
        list(range(0, 30)), weights=[0.02] + [0.05] * 5 + [0.04] * 5
        + [0.03] * 5 + [0.02] * 5 + [0.01] * 9)[0]
    real_estate = rnd.choices(
        [0, 1, 2, 3, 4, 5], weights=[0.40, 0.35, 0.15, 0.07, 0.02, 0.01])[0]

    # Probabilidad de impago creciente con las moras y el uso de crédito.
    p_default = 0.05
    p_default += min(past_due_30_59, 10) * 0.04
    p_default += min(past_due_60_89, 10) * 0.06
    p_default += min(past_due_90, 10) * 0.08
    p_default += min(revolving_util, 2.0) * 0.05
    if isinstance(monthly_income, (int, float)) and monthly_income < 1500:
        p_default += 0.05
    p_default = max(0.02, min(p_default, 0.85))
    target = 1 if rnd.random() < p_default else 0

    return {
        'Id': row_id,
        'SeriousDlqin2yrs': target,
        'RevolvingUtilizationOfUnsecuredLines': round(revolving_util, 9),
        'age': age,
        'NumberOfTime30-59DaysPastDueNotWorse': past_due_30_59,
        'DebtRatio': debt_ratio,
        'MonthlyIncome': monthly_income,
        'NumberOfOpenCreditLinesAndLoans': open_credit,
        'NumberOfTimes90DaysLate': past_due_90,
        'NumberRealEstateLoansOrLines': real_estate,
        'NumberOfTime60-89DaysPastDueNotWorse': past_due_60_89,
        'NumberOfDependents': num_dependents,
    }


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--rows', type=int, default=2000)
    p.add_argument('--seed', type=int, default=42)
    p.add_argument('--output',
                   default='python/data/give_me_some_credit/cs-training.csv')
    args = p.parse_args()

    rnd = random.Random(args.seed)
    columns = [
        'Id', 'SeriousDlqin2yrs',
        'RevolvingUtilizationOfUnsecuredLines', 'age',
        'NumberOfTime30-59DaysPastDueNotWorse', 'DebtRatio', 'MonthlyIncome',
        'NumberOfOpenCreditLinesAndLoans', 'NumberOfTimes90DaysLate',
        'NumberRealEstateLoansOrLines', 'NumberOfTime60-89DaysPastDueNotWorse',
        'NumberOfDependents',
    ]
    os.makedirs(os.path.dirname(args.output), exist_ok=True)

    n_default = 0
    with open(args.output, 'w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=columns)
        w.writeheader()
        for i in range(1, args.rows + 1):
            row = generate_row(rnd, i)
            n_default += row['SeriousDlqin2yrs']
            w.writerow(row)

    print(f"Wrote {args.rows} rows to {args.output}")
    print(f"  SeriousDlqin2yrs=1 (default): {n_default} "
          f"({100 * n_default / args.rows:.1f}%)")
    print(f"  SeriousDlqin2yrs=0 (paid):    {args.rows - n_default}")
    print(f"  seed={args.seed} (reproducible)")
    print()
    print("Próximo paso: correr preprocess_gmsc.py sobre este CSV")
    print(f"  python/.venv/bin/python python/preprocess_gmsc.py \\")
    print(f"    --csv_path {args.output} \\")
    print(f"    --output python/data/give_me_some_credit/gmsc_processed.csv")
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
