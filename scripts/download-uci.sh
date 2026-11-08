#!/usr/bin/env bash
# Descarga UCI Default of Credit Card Clients (id 350, licencia CC BY 4.0,
# doi:10.24432/C55S3H) y lo deja como lo espera preprocess_uci_credit.py:
# encabezados del XLS original y fin de línea LF. El resultado es el mismo
# archivo, byte a byte, que se usó en los experimentos de la tesis.
set -euo pipefail
cd "$(dirname "$0")/.."

URL="https://archive.ics.uci.edu/static/public/350/data.csv"
OUT="python/data/uci_credit/uci_credit_raw.csv"
SHA256="4edce6da569b2c30788a85fd98a9bafb1a983e8ca1a7aac875dd7ff853cbd0bb"
HEADER="ID,LIMIT_BAL,SEX,EDUCATION,MARRIAGE,AGE,PAY_0,PAY_2,PAY_3,PAY_4,PAY_5,PAY_6,BILL_AMT1,BILL_AMT2,BILL_AMT3,BILL_AMT4,BILL_AMT5,BILL_AMT6,PAY_AMT1,PAY_AMT2,PAY_AMT3,PAY_AMT4,PAY_AMT5,PAY_AMT6,default payment next month"

mkdir -p "$(dirname "$OUT")"
tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT

curl -fsSL "$URL" -o "$tmp"
{ echo "$HEADER"; tail -n +2 "$tmp" | tr -d '\r'; } > "$OUT"

got=$(shasum -a 256 "$OUT" | cut -d' ' -f1)
if [ "$got" != "$SHA256" ]; then
    echo "El SHA-256 no coincide con el de la tesis: $got" >&2
    exit 1
fi
echo "Listo: $OUT ($(($(wc -l < "$OUT") - 1)) filas, SHA-256 verificado)"
