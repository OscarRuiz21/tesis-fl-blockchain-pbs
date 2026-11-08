"use client";
import { useEffect, useRef, useState } from "react";

export function LogStream({ runId }: { runId: string }) {
  const [lines, setLines] = useState<string[]>([]);
  const [done, setDone] = useState(false);
  const containerRef = useRef<HTMLPreElement | null>(null);

  useEffect(() => {
    const url = `/api/runs/${runId}/logs/stream`;
    const es = new EventSource(url);
    es.addEventListener("log", (e) => {
      setLines((prev) => {
        const next = [...prev, (e as MessageEvent).data as string];
        // Bound: si crece mucho, recortamos las primeras 200 líneas
        if (next.length > 2000) return next.slice(-1800);
        return next;
      });
    });
    es.addEventListener("done", () => {
      setDone(true);
      es.close();
    });
    es.onerror = () => {
      setDone(true);
      es.close();
    };
    return () => es.close();
  }, [runId]);

  useEffect(() => {
    if (containerRef.current) {
      containerRef.current.scrollTop = containerRef.current.scrollHeight;
    }
  }, [lines]);

  return (
    <pre
      ref={containerRef}
      className="bg-slate-900 text-slate-100 text-[11px] font-mono p-3 rounded h-[400px] overflow-auto whitespace-pre-wrap"
    >
      {lines.join("\n")}
      {done && "\n# stream closed\n"}
    </pre>
  );
}
