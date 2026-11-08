"""Tests del spec_builder: ida y vuelta entre pydantic y YAML."""
from __future__ import annotations

import yaml

from app.schemas import (
    DatasetSpec,
    ExperimentSpec,
    FederationSpec,
    ModelSpec,
    OutputSpec,
    PartitionSpec,
    TimeoutsSpec,
)
from app.schemas.spec import ExperimentMetaSpec
from app.services import spec_builder
from app.store import files_io


def _sample_spec(label: str = "smoke-K2") -> ExperimentSpec:
    return ExperimentSpec(
        experiment=ExperimentMetaSpec(label=label, description="d", seed=42),
        model=ModelSpec(name="logistic_regression", num_params=26,
                        hyperparams={"epochs": 5, "lr": 0.01}),
        dataset=DatasetSpec(
            type="tabular_csv",
            base_path="/data/partitions",
            target_column="default",
            test_set_path="/opt/python/data/uci_credit/test.csv",
        ),
        partition=PartitionSpec(strategy="iid"),
        federation=FederationSpec(
            num_clients=2, num_rounds=3,
            aggregation="fedavg", anonymity="pbs",
        ),
        timeouts=TimeoutsSpec(),
        output=OutputSpec(results_dir="/results/smoke-K2"),
    )


def test_to_yaml_dict_has_expected_top_level_keys():
    d = spec_builder.to_yaml_dict(_sample_spec())
    assert set(d.keys()) == {
        "experiment", "model", "dataset", "partition",
        "federation", "timeouts", "output",
    }
    assert d["model"]["name"] == "logistic_regression"
    assert d["federation"]["num_clients"] == 2
    assert "alpha" not in d["partition"]  # iid no incluye alpha


def test_to_yaml_dict_includes_alpha_for_dirichlet():
    spec = _sample_spec()
    spec.partition = PartitionSpec(strategy="dirichlet", alpha=0.5)
    d = spec_builder.to_yaml_dict(spec)
    assert d["partition"]["strategy"] == "dirichlet"
    assert d["partition"]["alpha"] == 0.5


def test_write_persists_to_experiments_dir():
    spec = _sample_spec("write-test")
    path = spec_builder.write(spec)
    assert path.exists()
    raw = yaml.safe_load(path.read_text())
    assert raw["experiment"]["label"] == "write-test"
    assert raw["model"]["num_params"] == 26


def test_load_from_disk_roundtrip():
    original = _sample_spec("roundtrip")
    spec_builder.write(original)
    loaded = spec_builder.load_from_disk("roundtrip")
    assert loaded.experiment.label == original.experiment.label
    assert loaded.model.name == original.model.name
    assert loaded.federation.num_clients == original.federation.num_clients
    assert loaded.federation.aggregation == original.federation.aggregation


def test_to_yaml_string_is_valid_yaml():
    s = spec_builder.to_yaml_string(_sample_spec())
    parsed = yaml.safe_load(s)
    assert parsed["model"]["name"] == "logistic_regression"


def test_dirichlet_requires_alpha():
    import pytest

    with pytest.raises(ValueError, match="alpha"):
        PartitionSpec(strategy="dirichlet", alpha=None)


def test_safe_label_strips_unsafe_chars():
    assert files_io.safe_label("hello world") == "hello_world"
    assert files_io.safe_label("K=5/R=10") == "K_5_R_10"
    assert files_io.safe_label("valid-name_42") == "valid-name_42"
