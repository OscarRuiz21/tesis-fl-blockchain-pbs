"use client";
import {
  ReactNode,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react";
import Link from "next/link";
import clsx from "clsx";

type ToastKind = "info" | "success" | "error" | "loading";

export interface Toast {
  id: number;
  kind: ToastKind;
  title: string;
  message?: string;
  href?: string;          // si está, muestra un enlace "Ver"
  hrefLabel?: string;     // texto del enlace (por defecto "Ver")
  durationMs?: number;    // null para sticky; default 4000
}

interface ToastApi {
  push: (t: Omit<Toast, "id">) => number;
  update: (id: number, patch: Partial<Toast>) => void;
  dismiss: (id: number) => void;
}

const Ctx = createContext<ToastApi | null>(null);

export function useToast(): ToastApi {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useToast must be inside <ToastProvider>");
  return ctx;
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const dismiss = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const push = useCallback<ToastApi["push"]>((t) => {
    const id = Date.now() + Math.floor(Math.random() * 1000);
    const toast: Toast = { id, durationMs: 4000, ...t };
    setToasts((prev) => [...prev, toast]);
    if (toast.durationMs && toast.durationMs > 0) {
      setTimeout(() => dismiss(id), toast.durationMs);
    }
    return id;
  }, [dismiss]);

  const update = useCallback<ToastApi["update"]>((id, patch) => {
    setToasts((prev) =>
      prev.map((t) => (t.id === id ? { ...t, ...patch } : t)),
    );
    if (patch.durationMs && patch.durationMs > 0) {
      setTimeout(() => dismiss(id), patch.durationMs);
    }
  }, [dismiss]);

  return (
    <Ctx.Provider value={{ push, update, dismiss }}>
      {children}
      <div className="fixed top-4 right-4 z-50 flex flex-col gap-2 w-[360px] max-w-[calc(100vw-2rem)]">
        {toasts.map((t) => (
          <ToastCard key={t.id} toast={t} onClose={() => dismiss(t.id)} />
        ))}
      </div>
    </Ctx.Provider>
  );
}

function ToastCard({ toast, onClose }: { toast: Toast; onClose: () => void }) {
  const { kind, title, message, href, hrefLabel } = toast;
  const palette: Record<ToastKind, string> = {
    info: "border-slate-300 bg-white text-slate-900",
    success: "border-emerald-400 bg-emerald-50 text-emerald-900",
    error: "border-red-400 bg-red-50 text-red-900",
    loading: "border-amber-400 bg-amber-50 text-amber-900",
  };
  const icon: Record<ToastKind, string> = {
    info: "i",
    success: "✓",
    error: "✗",
    loading: "…",
  };
  return (
    <div
      role="status"
      className={clsx(
        "rounded-md border shadow-md px-3 py-2 flex gap-3 items-start animate-fade-in",
        palette[kind],
      )}
    >
      <span
        className={clsx(
          "inline-flex items-center justify-center w-5 h-5 rounded-full text-xs font-bold mt-0.5 flex-shrink-0",
          kind === "success" && "bg-emerald-500 text-white",
          kind === "error" && "bg-red-500 text-white",
          kind === "info" && "bg-slate-700 text-white",
          kind === "loading" && "bg-amber-500 text-white animate-pulse",
        )}
      >
        {icon[kind]}
      </span>
      <div className="flex-1 min-w-0">
        <div className="text-sm font-semibold">{title}</div>
        {message && <div className="text-xs mt-0.5 opacity-90">{message}</div>}
        {href && (
          <Link
            href={href}
            className="text-xs underline font-medium mt-1 inline-block"
            onClick={onClose}
          >
            {hrefLabel ?? "Ver →"}
          </Link>
        )}
      </div>
      <button
        onClick={onClose}
        className="text-xs opacity-50 hover:opacity-100"
        aria-label="Cerrar"
      >
        ✕
      </button>
    </div>
  );
}
