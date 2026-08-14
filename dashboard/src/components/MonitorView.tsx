import { useCallback, useEffect, useRef, useState } from "react";
import ForceGraph2D, { type ForceGraphMethods, type NodeObject, type LinkObject } from "react-force-graph-2d";
import { supabase } from "../lib/supabase";
import type { AgentLog } from "../types";

interface AgentAction {
  id: string;
  user_id: string;
  source: string;
  tool_name: string;
  input: unknown;
  target_table: string | null;
  target_id: string | null;
  previous_state: unknown;
  new_state: unknown;
  status: string;
  result_summary: string | null;
  created_at: string;
  undone_at: string | null;
}

type Source = "core" | "brain";

interface GNode extends NodeObject {
  id: string;
  label: string;
  kind: "agent" | "tool";
  flashUntil?: number;
  flashColor?: string;
}

interface GLink extends LinkObject {
  id: string;
  source: string;
  target: string;
}

function useContainerSize<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [size, setSize] = useState({ width: 800, height: 600 });
  useEffect(() => {
    if (!ref.current) return;
    const el = ref.current;
    const observer = new ResizeObserver(([entry]) => {
      setSize({ width: entry.contentRect.width, height: entry.contentRect.height });
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, []);
  return { ref, size };
}

const BRAIN_POLL_MS = 8000;

export function MonitorView() {
  const [source, setSource] = useState<Source>("core");
  const [logs, setLogs] = useState<AgentLog[]>([]);
  const [actions, setActions] = useState<AgentAction[]>([]);
  const [brainError, setBrainError] = useState<string | null>(null);
  const [selected, setSelected] = useState<AgentLog | AgentAction | null>(null);
  const [nodes, setNodes] = useState<GNode[]>([]);
  const [links, setLinks] = useState<GLink[]>([]);
  const fgRef = useRef<ForceGraphMethods<GNode, GLink> | undefined>(undefined);
  const { ref: containerRef, size } = useContainerSize<HTMLDivElement>();
  const seenActionIds = useRef<Set<string>>(new Set());

  const flash = useCallback((nodeId: string, status: string) => {
    const color = status === "error" || status === "failed" ? "#e55353" : "#3ddc84";
    setNodes((prev) => prev.map((n) => (n.id === nodeId ? { ...n, flashUntil: Date.now() + 1400, flashColor: color } : n)));
  }, []);

  const registerNode = useCallback(
    (agentId: string, agentLabel: string, toolId: string | null, toolLabel: string | null, status: string) => {
      setNodes((prev) => {
        const next = [...prev];
        if (!next.some((n) => n.id === agentId)) next.push({ id: agentId, label: agentLabel, kind: "agent" });
        if (toolId && toolLabel && !next.some((n) => n.id === toolId)) next.push({ id: toolId, label: toolLabel, kind: "tool" });
        return next;
      });
      if (toolId) {
        const linkId = `${agentId}=>${toolId}`;
        setLinks((prev) => (prev.some((l) => l.id === linkId) ? prev : [...prev, { id: linkId, source: agentId, target: toolId }]));
      }
      flash(agentId, status);
      if (toolId) flash(toolId, status);
    },
    [flash],
  );

  const registerLog = useCallback(
    (log: AgentLog) => {
      registerNode(`agent:${log.agent_name}`, log.agent_name, log.tool_used ? `tool:${log.tool_used}` : null, log.tool_used, log.status);
    },
    [registerNode],
  );

  const registerAction = useCallback(
    (a: AgentAction) => {
      const domain = a.target_table || "بدون جدول";
      registerNode(`tool:${a.tool_name}`, a.tool_name, `agent:${domain}`, domain, a.status);
    },
    [registerNode],
  );

  // zad-core-intelligence stream (existing behavior, realtime).
  useEffect(() => {
    if (source !== "core") return;
    setNodes([]);
    setLinks([]);
    supabase
      .from("agent_logs")
      .select("*")
      .order("timestamp", { ascending: false })
      .limit(50)
      .then(({ data }) => {
        if (!data) return;
        const rows = data as AgentLog[];
        for (const row of [...rows].reverse()) registerLog(row);
        setLogs(rows);
      });

    const channel = supabase
      .channel("agent_logs_live")
      .on("postgres_changes", { event: "INSERT", schema: "public", table: "agent_logs" }, (payload) => {
        const row = payload.new as AgentLog;
        registerLog(row);
        setLogs((prev) => [row, ...prev].slice(0, 200));
      })
      .subscribe();

    return () => {
      supabase.removeChannel(channel);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [source]);

  // Real zad-brain activity (agent_actions) — not on realtime, so polled
  // through the admin-gated edge function action instead.
  useEffect(() => {
    if (source !== "brain") return;
    setNodes([]);
    setLinks([]);
    setBrainError(null);
    let cancelled = false;

    const poll = async () => {
      const { data, error } = await supabase.functions.invoke("zad-core-intelligence", {
        body: { action: "admin_recent_activity" },
      });
      if (cancelled) return;
      if (error) {
        setBrainError(error.message);
        return;
      }
      const rows = (data?.actions || []) as AgentAction[];
      for (const row of [...rows].reverse()) {
        if (!seenActionIds.current.has(row.id)) {
          seenActionIds.current.add(row.id);
          registerAction(row);
        }
      }
      setActions(rows);
    };

    poll();
    const interval = window.setInterval(poll, BRAIN_POLL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [source]);

  const activeList = source === "core" ? logs : actions;

  return (
    <div className="monitor-layout">
      <div className="graph-pane" ref={containerRef}>
        <div className="source-toggle">
          <button className={source === "core" ? "active" : ""} onClick={() => setSource("core")}>zad-core-intelligence</button>
          <button className={source === "brain" ? "active" : ""} onClick={() => setSource("brain")}>عقل زاد الحقيقي</button>
        </div>
        <ForceGraph2D<GNode, GLink>
          ref={fgRef}
          graphData={{ nodes, links }}
          width={size.width}
          height={size.height}
          backgroundColor="#161616"
          nodeRelSize={5}
          linkColor={() => "#3a3a3a"}
          linkDirectionalParticles={1}
          linkDirectionalParticleWidth={2}
          linkDirectionalParticleColor={() => "#4a7dff"}
          cooldownTicks={100}
          nodeCanvasObject={(node, ctx, globalScale) => {
            const n = node as GNode;
            const isFlashing = (n.flashUntil ?? 0) > Date.now();
            const baseColor = n.kind === "agent" ? "#2a4a8a" : "#333";
            const color = isFlashing ? n.flashColor! : baseColor;
            const r = n.kind === "agent" ? 8 : 6;
            ctx.beginPath();
            ctx.arc(n.x!, n.y!, r, 0, 2 * Math.PI);
            ctx.fillStyle = color;
            ctx.fill();
            ctx.strokeStyle = n.kind === "agent" ? "#4a7dff" : "#666";
            ctx.lineWidth = 1;
            ctx.stroke();

            const fontSize = 11 / globalScale;
            ctx.font = `${fontSize}px sans-serif`;
            ctx.fillStyle = "#ddd";
            ctx.textAlign = "center";
            ctx.textBaseline = "top";
            ctx.fillText(n.label, n.x!, n.y! + r + 2);
          }}
          onEngineTick={() => {
            // re-render to clear expired flashes
            if (nodes.some((n) => (n.flashUntil ?? 0) < Date.now() && n.flashColor)) {
              setNodes((prev) => prev.map((n) => ((n.flashUntil ?? 0) < Date.now() ? { ...n, flashColor: undefined } : n)));
            }
          }}
        />
      </div>
      <div className="log-pane">
        <h3>{source === "core" ? `Live Agent Activity (${logs.length})` : `نشاط عقل زاد الحقيقي (${actions.length})`}</h3>
        {source === "brain" && brainError && <p className="error">فشل التحميل: {brainError}</p>}
        {source === "brain" && !brainError && (
          <p className="brain-hint">بيانات حقيقية من عملاء زاد — undo متاح لكل حركة عبر zad_agent_undo(). يتحدّث كل 8 ثواني.</p>
        )}
        <div className="log-list">
          {source === "core"
            ? logs.map((log) => (
                <button key={log.id} className={`log-row status-${log.status}`} onClick={() => setSelected(log)}>
                  <span className="log-time">{new Date(log.timestamp).toLocaleTimeString("ar-SA")}</span>
                  <span className="log-agent">{log.agent_name}</span>
                  <span className="log-tool">{log.tool_used}</span>
                  <span className="log-duration">{log.duration_ms ? `${log.duration_ms}ms` : ""}</span>
                </button>
              ))
            : actions.map((a) => (
                <button key={a.id} className={`log-row status-${a.status === "success" ? "success" : a.status === "failed" ? "error" : "warning"}`} onClick={() => setSelected(a)}>
                  <span className="log-time">{new Date(a.created_at).toLocaleTimeString("ar-SA")}</span>
                  <span className="log-agent">{a.tool_name}</span>
                  <span className="log-tool">{a.target_table}{a.undone_at ? " (متراجع عنه)" : ""}</span>
                  <span className="log-duration" />
                </button>
              ))}
          {activeList.length === 0 && !brainError && <p className="empty">لا يوجد نشاط بعد — سيظهر هنا فور استخدام التطبيق.</p>}
        </div>
      </div>
      {selected && (
        <div className="payload-drawer" onClick={() => setSelected(null)}>
          <pre onClick={(e) => e.stopPropagation()}>{JSON.stringify(selected, null, 2)}</pre>
        </div>
      )}
    </div>
  );
}
