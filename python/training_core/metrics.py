"""Métricas de clasificación."""
import torch


def compute_binary_accuracy(logits: torch.Tensor, targets: torch.Tensor) -> float:
    """Sigmoide sobre los logits y umbral 0.5."""
    preds = (torch.sigmoid(logits) > 0.5).long().squeeze()
    targets = targets.squeeze().long()
    correct = (preds == targets).sum().item()
    total = targets.numel()
    return correct / total if total > 0 else 0.0


def compute_multiclass_accuracy(logits: torch.Tensor, targets: torch.Tensor) -> float:
    preds = logits.argmax(dim=1)
    correct = (preds == targets).sum().item()
    total = targets.numel()
    return correct / total if total > 0 else 0.0
