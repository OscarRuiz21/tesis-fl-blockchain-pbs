"""Perceptrón multicapa para clasificación binaria.

Linear, ReLU y Dropout opcional por cada capa oculta de hidden_dims, y una
salida lineal que devuelve logits. Acepta los mismos hiperparámetros que
LogisticRegression (salvo fit_intercept), así que los escenarios YAML son
intercambiables entre ambos modelos.
"""
from typing import List

import torch
import torch.nn as nn

from python.models.base import EvalStats, FLModel, TrainStats
from python.training_core.federated import proximal_term
from python.training_core.metrics import compute_binary_accuracy


class MLP(FLModel):
    """num_params = sum_i (in_i * h_i + h_i) + (h_N + 1), con in_1 = n_features.
    Para [16, 8] y 10 features: 176 + 136 + 9 = 321.
    """

    def __init__(self, n_features: int, hidden_dims: List[int], dropout: float = 0.2):
        super().__init__()
        self._n_features = n_features
        self._hidden_dims = list(hidden_dims)
        self._dropout = float(dropout)

        layers: List[nn.Module] = []
        prev = n_features
        for h in hidden_dims:
            layers.append(nn.Linear(prev, h))
            layers.append(nn.ReLU())
            if dropout > 0:
                layers.append(nn.Dropout(dropout))
            prev = h
        layers.append(nn.Linear(prev, 1))
        self.net = nn.Sequential(*layers)

    def forward(self, x):
        return self.net(x)

    def num_params(self) -> int:
        return self.count_params()

    def dataset_type(self) -> str:
        return 'tabular_csv'

    def fit(
        self,
        X: torch.Tensor,
        y: torch.Tensor,
        hyperparams: dict,
    ) -> TrainStats:
        epochs = int(hyperparams['epochs'])
        batch_size = int(hyperparams['batch_size'])
        lr = float(hyperparams['lr'])
        strategy = hyperparams.get('strategy', 'fedavg')
        mu = float(hyperparams.get('mu', 0.0))
        regularization = hyperparams.get('regularization', 'none')
        reg_strength = float(hyperparams.get('reg_strength', 0.0))
        balance_classes = bool(hyperparams.get('balance_classes', False))

        if balance_classes:
            n_pos = (y == 1).sum().item()
            n_neg = (y == 0).sum().item()
            pos_weight = torch.tensor([n_neg / max(n_pos, 1)])
            criterion = nn.BCEWithLogitsLoss(pos_weight=pos_weight)
        else:
            criterion = nn.BCEWithLogitsLoss()

        # SGD y no Adam, para tratar igual a LR y al MLP en FedProx.
        weight_decay = reg_strength if regularization == 'l2' else 0.0
        optimizer = torch.optim.SGD(
            self.parameters(), lr=lr, weight_decay=weight_decay)

        global_params = None
        if strategy == 'fedprox' and mu > 0:
            global_params = [p.data.clone() for p in self.parameters()]

        self.train()
        dataset = torch.utils.data.TensorDataset(X, y)
        loader = torch.utils.data.DataLoader(
            dataset, batch_size=batch_size, shuffle=True)

        total_loss, n_batches = 0.0, 0
        for _ in range(epochs):
            for xb, yb in loader:
                optimizer.zero_grad()
                logits = self(xb)
                loss = criterion(logits, yb)

                if regularization == 'l1':
                    l1 = sum(p.abs().sum() for p in self.parameters())
                    loss = loss + reg_strength * l1

                if global_params is not None:
                    loss = loss + proximal_term(self, global_params, mu)

                loss.backward()
                optimizer.step()
                total_loss += loss.item()
                n_batches += 1

        # En modo eval para desactivar el dropout.
        self.eval()
        with torch.no_grad():
            logits = self(X)
            accuracy = compute_binary_accuracy(logits, y)

        avg_loss = total_loss / max(n_batches, 1)
        return TrainStats(
            loss=avg_loss,
            accuracy=accuracy,
            num_samples=int(X.shape[0]),
        )

    def evaluate(self, X: torch.Tensor, y: torch.Tensor) -> EvalStats:
        criterion = nn.BCEWithLogitsLoss()
        self.eval()
        with torch.no_grad():
            logits = self(X)
            loss = criterion(logits, y).item()
            accuracy = compute_binary_accuracy(logits, y)
        return EvalStats(loss=loss, accuracy=accuracy)
