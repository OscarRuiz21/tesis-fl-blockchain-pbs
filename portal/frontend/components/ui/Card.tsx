import clsx from "clsx";
import { ReactNode } from "react";

export function Card({
  title,
  children,
  className,
  action,
}: {
  title?: string;
  children: ReactNode;
  className?: string;
  action?: ReactNode;
}) {
  return (
    <section
      className={clsx(
        "bg-white rounded-md border border-slate-200 shadow-sm",
        className,
      )}
    >
      {title && (
        <header className="px-4 py-3 border-b border-slate-200 flex justify-between items-center">
          <h2 className="text-sm font-semibold text-slate-800">{title}</h2>
          {action}
        </header>
      )}
      <div className="p-4">{children}</div>
    </section>
  );
}
