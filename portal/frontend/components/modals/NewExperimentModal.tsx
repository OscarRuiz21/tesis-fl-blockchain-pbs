"use client";
import { Modal } from "@/components/Modal";
import { SpecWizard } from "@/components/wizard/SpecWizard";
import type { ExperimentSpec } from "@/lib/types";

export function NewExperimentModal({
  open,
  onClose,
  onSaved,
  onLaunched,
  initial,
  title,
}: {
  open: boolean;
  onClose: () => void;
  onSaved?: (label: string) => void;
  onLaunched?: (runId: string) => void;
  initial?: ExperimentSpec;
  title?: string;
}) {
  return (
    <Modal
      open={open}
      onClose={onClose}
      title={title ?? "Nuevo experimento"}
      size="lg"
    >
      <SpecWizard
        initial={initial}
        onSaved={(label) => {
          onSaved?.(label);
          onClose();
        }}
        onLaunched={(runId) => {
          onLaunched?.(runId);
          onClose();
        }}
      />
    </Modal>
  );
}
