import { useCallback, useEffect, useState } from "react";
import {
  ReactFlow,
  Background,
  Controls,
  addEdge,
  applyNodeChanges,
  applyEdgeChanges,
  type Node,
  type Edge,
  type Connection,
  type NodeChange,
  type EdgeChange,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { supabase } from "../lib/supabase";
import type { WorkflowRow } from "../types";

const WORKFLOW_NAME = "zad-core-intelligence";
let nodeCounter = 0;

export function DesignView() {
  const [nodes, setNodes] = useState<Node[]>([]);
  const [edges, setEdges] = useState<Edge[]>([]);
  const [workflow, setWorkflow] = useState<WorkflowRow | null>(null);
  const [saving, setSaving] = useState(false);
  const [status, setStatus] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      const { data: wf } = await supabase
        .from("workflows")
        .select("*")
        .eq("name", WORKFLOW_NAME)
        .order("version", { ascending: false })
        .limit(1)
        .maybeSingle();
      if (!wf) return;
      setWorkflow(wf as WorkflowRow);

      const [{ data: nodeRows }, { data: edgeRows }] = await Promise.all([
        supabase.from("nodes").select("*").eq("workflow_id", wf.id),
        supabase.from("edges").select("*").eq("workflow_id", wf.id),
      ]);

      setNodes(
        (nodeRows || []).map((n) => ({
          id: n.id,
          position: n.position,
          data: { label: (n.data as { label?: string })?.label || n.id },
          style: { border: "1px solid #4a7dff", borderRadius: 8, padding: 8, background: "#1e2a4a", color: "#eee", fontSize: 12 },
        })),
      );
      setEdges(
        (edgeRows || []).map((e) => ({
          id: e.id,
          source: e.source,
          target: e.target,
        })),
      );
    })();
  }, []);

  const onNodesChange = useCallback((changes: NodeChange[]) => setNodes((nds) => applyNodeChanges(changes, nds)), []);
  const onEdgesChange = useCallback((changes: EdgeChange[]) => setEdges((eds) => applyEdgeChanges(changes, eds)), []);
  const onConnect = useCallback((connection: Connection) => setEdges((eds) => addEdge(connection, eds)), []);

  const addNode = useCallback(() => {
    const label = window.prompt("اسم العقدة (وكيل/أداة):");
    if (!label) return;
    nodeCounter += 1;
    const id = `${label.replace(/\s+/g, "_")}_${nodeCounter}`;
    setNodes((nds) => [
      ...nds,
      {
        id,
        position: { x: 100 + (nodeCounter % 5) * 150, y: 100 + Math.floor(nodeCounter / 5) * 100 },
        data: { label },
        style: { border: "1px solid #4a7dff", borderRadius: 8, padding: 8, background: "#1e2a4a", color: "#eee", fontSize: 12 },
      },
    ]);
  }, []);

  const save = useCallback(async () => {
    setSaving(true);
    setStatus(null);
    try {
      const nextVersion = (workflow?.version || 0) + 1;
      const { data: newWf, error: wfError } = await supabase
        .from("workflows")
        .insert({ name: WORKFLOW_NAME, version: nextVersion })
        .select()
        .single();
      if (wfError || !newWf) throw wfError;

      if (nodes.length > 0) {
        const { error: nodesError } = await supabase.from("nodes").insert(
          nodes.map((n) => ({
            id: n.id,
            workflow_id: newWf.id,
            type: "agent",
            position: n.position,
            data: n.data,
          })),
        );
        if (nodesError) throw nodesError;
      }

      if (edges.length > 0) {
        const { error: edgesError } = await supabase.from("edges").insert(
          edges.map((e) => ({
            id: e.id,
            workflow_id: newWf.id,
            source: e.source,
            target: e.target,
            data: {},
          })),
        );
        if (edgesError) throw edgesError;
      }

      setWorkflow(newWf as WorkflowRow);
      setStatus(`تم الحفظ — إصدار ${nextVersion}`);
    } catch (err) {
      setStatus(`فشل الحفظ: ${(err as Error).message}`);
    } finally {
      setSaving(false);
    }
  }, [nodes, edges, workflow]);

  return (
    <div className="design-layout">
      <div className="design-toolbar">
        <button onClick={addNode}>+ عقدة جديدة</button>
        <button onClick={save} disabled={saving}>{saving ? "جارٍ الحفظ..." : "حفظ كإصدار جديد"}</button>
        <span className="design-status">
          {workflow ? `الإصدار الحالي: ${workflow.version}` : "لا يوجد إصدار محفوظ بعد"}
          {status && ` — ${status}`}
        </span>
      </div>
      <div className="graph-pane">
        <ReactFlow nodes={nodes} edges={edges} onNodesChange={onNodesChange} onEdgesChange={onEdgesChange} onConnect={onConnect} fitView>
          <Background />
          <Controls />
        </ReactFlow>
      </div>
    </div>
  );
}
