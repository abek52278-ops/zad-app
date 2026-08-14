export interface AgentLog {
  id: string;
  timestamp: string;
  agent_name: string;
  tool_used: string | null;
  payload: unknown;
  status: "success" | "warning" | "error";
  duration_ms: number | null;
}

export interface WorkflowRow {
  id: string;
  name: string;
  version: number;
  created_at: string;
}

export interface NodeRow {
  id: string;
  workflow_id: string;
  type: string;
  position: { x: number; y: number };
  data: Record<string, unknown>;
}

export interface EdgeRow {
  id: string;
  workflow_id: string;
  source: string;
  target: string;
  data: Record<string, unknown>;
}
