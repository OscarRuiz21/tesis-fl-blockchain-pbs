"use client";
import { ReactNode, useEffect } from "react";

const SIZE: Record<string, string> = {
  sm: "max-w-md",
  md: "max-w-2xl",
  lg: "max-w-4xl",
  xl: "max-w-6xl",
};

export function Modal({
  open,
  onClose,
  title,
  children,
  size = "md",
}: {
  open: boolean;
  onClose: () => void;
  title?: string;
  children: ReactNode;
  size?: "sm" | "md" | "lg" | "xl";
}) {
  useEffect(() => {
    if (!open) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handler);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", handler);
      document.body.style.overflow = prev;
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-40 flex items-start justify-center pt-10 px-4 pb-4 animate-fade-in">
      <div
        className="absolute inset-0 bg-slate-900/50"
        onClick={onClose}
        aria-hidden
      />
      <div
        className={`relative z-10 bg-white rounded-lg shadow-2xl w-full ${SIZE[size]} max-h-[calc(100vh-5rem)] flex flex-col`}
        role="dialog"
        aria-modal="true"
      >
        {title && (
          <header className="px-5 py-3 border-b border-slate-200 flex justify-between items-center shrink-0">
            <h2 className="text-base font-semibold text-slate-900">{title}</h2>
            <button
              onClick={onClose}
              className="text-slate-400 hover:text-slate-900 text-2xl leading-none w-7 h-7 flex items-center justify-center rounded hover:bg-slate-100"
              aria-label="Cerrar"
            >
              ×
            </button>
          </header>
        )}
        <div className="flex-1 overflow-auto p-5">{children}</div>
      </div>
    </div>
  );
}
